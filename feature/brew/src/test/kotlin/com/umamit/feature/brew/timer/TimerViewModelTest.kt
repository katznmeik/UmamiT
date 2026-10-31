package com.umamit.feature.brew.timer

import com.google.common.truth.Truth.assertThat
import com.umamit.core.common.MonotonicClock
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.SeedData
import com.umamit.core.data.SessionStart
import com.umamit.core.data.StockRepository
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.Photo
import com.umamit.core.model.PhotoOrigin
import com.umamit.core.model.PhotoOwner
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.StepKind
import com.umamit.core.model.StockLevel
import com.umamit.core.model.StockSummary
import com.umamit.core.model.TimerPhase
import com.umamit.core.model.TimerSlot
import com.umamit.core.model.UserSettings
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.testing.FakeTimerReliability
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.SessionKind
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.engine.TransitionOutcome
import com.umamit.core.timer.reliability.CheckState
import com.umamit.core.timer.reliability.ReliabilityCheck
import com.umamit.core.timer.reliability.ReliabilityProblem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * The timer's view model over a fake [BrewTimer]: it forwards events and reads the settings, the checks, the
 * seal and the stock. On Robolectric for the log of a failed stock read.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TimerViewModelTest {

    private val clock = object : MonotonicClock {
        override fun elapsedMs() = 42L

        override fun bootCount() = 1
    }
    private val timer = FakeBrewTimer(TimerStates.running())
    private val reliability = FakeTimerReliability()
    private val settings = FakeSettingsRepository(UserSettings(keepScreenOn = false, dimWhileSteeping = false))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @get:Rule
    val folder = TemporaryFolder()

    private val collection = FakeCollection()
    private val sessions = FakeSessions(collection)
    private val photos by lazy { PhotoFiles(folder.newFolder("photos")) }

    private fun viewModel() =
        TimerViewModel(timer, clock, settings, reliability, sessions.sessions, collection.teas, photos, stock)

    /** The stock the view model reads; a test swaps in a failing one (rule 8). */
    private var stock: StockRepository = collection.stock

    private suspend fun buy(grams: Double, id: String = "e$grams") = collection.stock.addEvent(
        InventoryEvent(id, SeedData.HISUI_TEA_ID, InventoryEventType.PURCHASE, grams, occurredAtMillis = 1),
    )

    private suspend fun startHisuiSession(id: String, teaId: String? = SeedData.HISUI_TEA_ID) {
        val snapshot = SessionSnapshot(
            teaId = teaId,
            teaName = "Bio Sencha Hisui",
            teaSecondaryName = null,
            teaType = "Sencha",
            styleKey = "sencha",
            category = null,
            profileId = null,
            profileName = "Kyusu",
            method = BrewMethod.KYUSU,
            vesselId = SeedData.KYUSU_VESSEL_ID,
            vesselName = "Kyusu",
            leafGrams = 5.0,
            waterMl = 250,
            baseTempC = 65,
        )
        sessions.sessions.startSession(
            SessionStart(id, snapshot, listOf(PlannedStep(StepKind.INFUSION, 60, 65)), 15, 1_000, "UTC"),
        )
    }

    @Test
    fun `the brew's seal gets the tea's style and cover photo, no seal for the quick timer (D-095)`() = runTest {
        collection.teas.saveTea(SeedData.hisui)
        startHisuiSession("s1")
        val vm = viewModel()
        // No cover photo yet: the seal shows the leaf in the style's colour.
        assertThat(vm.teaSeal.value).isEqualTo(TimerTeaSeal(styleKey = "sencha", photoPath = null))

        collection.teas.savePhoto(Photo("p1", PhotoOwner.OfTea(SeedData.HISUI_TEA_ID), PhotoOrigin.OWN, 10, 10, 3))
        photos.write("p1", byteArrayOf(1, 2, 3))
        collection.teas.setCoverPhoto(SeedData.HISUI_TEA_ID, "p1")
        assertThat(vm.teaSeal.value).isEqualTo(TimerTeaSeal(styleKey = "sencha", photoPath = photos.file("p1").path))

        timer.show(TimerStates.quick(TimerPhase.RUNNING))
        assertThat(vm.teaSeal.value).isNull()
    }

    @Test
    fun `the seal and the stock follow the brew while the quick timer is in front of the controller (D-098)`() =
        runTest {
            collection.teas.saveTea(SeedData.hisui)
            buy(38.0)
            startHisuiSession("s1")
            val quick = TimerStates.quick(TimerPhase.RUNNING)
            timer.show(brew = TimerStates.ready(), quick = quick, front = quick)
            val vm = viewModel()

            assertThat(vm.teaSeal.value).isEqualTo(TimerTeaSeal(styleKey = "sencha", photoPath = null))
            assertThat(vm.teaStock.value).isEqualTo(TimerStock(grams = 38.0, level = StockLevel.AVAILABLE))
            assertThat(vm.state(TimerSlot.QUICK).value).isEqualTo(quick)
        }

    @Test
    fun `READY's stock follows the tea's stock live, Knapp at the threshold and Leer at 0 g (BRW-11, D-098)`() =
        runTest {
            collection.teas.saveTea(SeedData.hisui)
            buy(38.0)
            startHisuiSession("s1")
            timer.show(TimerStates.ready())
            val vm = viewModel()
            assertThat(vm.teaStock.value).isEqualTo(TimerStock(grams = 38.0, level = StockLevel.AVAILABLE))

            // SET-16's „Knapp ab" is 15 g, inclusive.
            collection.stock.correctStock(SeedData.HISUI_TEA_ID, 15.0)
            assertThat(vm.teaStock.value).isEqualTo(TimerStock(grams = 15.0, level = StockLevel.LOW))
            collection.stock.correctStock(SeedData.HISUI_TEA_ID, 14.0)
            assertThat(vm.teaStock.value).isEqualTo(TimerStock(grams = 14.0, level = StockLevel.LOW))
            collection.stock.correctStock(SeedData.HISUI_TEA_ID, 0.0)
            assertThat(vm.teaStock.value).isEqualTo(TimerStock(grams = 0.0, level = StockLevel.EMPTY))
        }

    @Test
    fun `the tea's own Knapp threshold wins over SET-16's (INV-05)`() = runTest {
        collection.teas.saveTea(SeedData.hisui.copy(lowStockGrams = 20.0))
        buy(18.0)
        startHisuiSession("s1")
        timer.show(TimerStates.ready())

        assertThat(viewModel().teaStock.value).isEqualTo(TimerStock(grams = 18.0, level = StockLevel.LOW))
    }

    @Test
    fun `a tea without packages reads Leer, as in the setup (INV-05)`() = runTest {
        collection.teas.saveTea(SeedData.hisui)
        startHisuiSession("s1")
        timer.show(TimerStates.ready())

        assertThat(viewModel().teaStock.value).isEqualTo(TimerStock(grams = 0.0, level = StockLevel.EMPTY))
    }

    @Test
    fun `no stock for the quick timer or a tea not in the collection (BRW-11, D-098)`() = runTest {
        // A session without a tea, and one whose tea was deleted or removed (archived).
        startHisuiSession("s1", teaId = null)
        timer.show(TimerStates.ready())
        val vm = viewModel()
        assertThat(vm.teaStock.value).isNull()

        startHisuiSession("s2")
        timer.show(TimerStates.ready().copy(kind = SessionKind.Brew("s2")))
        assertThat(vm.teaStock.value).isNull()
        collection.teas.saveTea(SeedData.hisui.copy(isArchived = true))
        buy(38.0)
        assertThat(vm.teaStock.value).isNull()
        collection.teas.saveTea(SeedData.hisui)
        assertThat(vm.teaStock.value).isEqualTo(TimerStock(grams = 38.0, level = StockLevel.AVAILABLE))

        timer.show(TimerStates.quick(TimerPhase.READY))
        assertThat(vm.teaStock.value).isNull()
    }

    @Test
    fun `a failed stock read shows no line and leaves the timer alone (rule 8)`() = runTest {
        collection.teas.saveTea(SeedData.hisui)
        startHisuiSession("s1")
        stock = object : StockRepository by collection.stock {
            override fun observeStock(teaId: String): Flow<StockSummary> = flow { throw IOException("disk") }
        }
        timer.show(TimerStates.ready())
        val vm = viewModel()

        assertThat(vm.teaStock.value).isNull()
        assertThat(vm.state.value).isEqualTo(TimerStates.ready())
        vm.send(TimerSlot.BREW, TimerEvent.Start)
        timer.show(TimerStates.running())
        assertThat(timer.sent).containsExactly(TimerSlot.BREW to TimerEvent.Start)
        assertThat(vm.state.value).isEqualTo(TimerStates.running())
    }

    @Test
    fun `the state is the controller's state and events go to the controller`() {
        val vm = viewModel()

        vm.send(TimerSlot.QUICK, TimerEvent.Pause)
        timer.show(TimerUiState.Idle)

        assertThat(timer.sent).containsExactly(TimerSlot.QUICK to TimerEvent.Pause)
        assertThat(vm.state.value).isEqualTo(TimerUiState.Idle)
        assertThat(vm.nowElapsedMs()).isEqualTo(42L)
    }

    @Test
    fun `the screen settings follow SET-17 and SET-25`() {
        val vm = viewModel()
        val collected: StateFlow<TimerScreenSettings> = vm.screenSettings

        assertThat(collected.value).isEqualTo(TimerScreenSettings(keepScreenOn = false, dimWhileSteeping = false))
    }

    @Test
    fun `the reliability checks are read again on resume, and Beheben opens the failing setting (TMR-31)`() {
        val vm = viewModel()
        assertThat(vm.reliabilityProblem.value).isNull()

        reliability.set(ReliabilityCheck.FullScreenAlert, CheckState.Missing)
        vm.refreshReliability()
        vm.fixReliability()

        assertThat(vm.reliabilityProblem.value).isEqualTo(ReliabilityProblem.Unreliable)
        assertThat(reliability.opened).containsExactly(ReliabilityCheck.FullScreenAlert)

        // D-098: the alarm volume at 0 has its own message; raising it clears the banner.
        reliability.grantAll()
        reliability.set(ReliabilityCheck.AlarmVolume, CheckState.Missing)
        vm.refreshReliability()
        assertThat(vm.reliabilityProblem.value).isEqualTo(ReliabilityProblem.AlarmVolumeOff)
        reliability.set(ReliabilityCheck.AlarmVolume, CheckState.Ok)
        vm.refreshReliability()
        assertThat(vm.reliabilityProblem.value).isNull()
    }
}
