package com.umamit.core.data

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.common.WallClock
import com.umamit.core.database.entity.BrewSessionEntity
import com.umamit.core.database.entity.PhotoEntity
import com.umamit.core.database.entity.SessionDescriptorEntity
import com.umamit.core.database.entity.SessionStepEntity
import com.umamit.core.domain.stats.StatsInput
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.PhotoOrigin
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.StatsRange
import com.umamit.core.model.StepKind
import com.umamit.core.model.StockLevel
import com.umamit.core.model.TasteProfile
import com.umamit.core.model.UserSettings
import com.umamit.core.testing.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The statistics' data source (INS-01–INS-07, D-115, #257) on the database in memory, with invented teas: which
 * rows reach [StatsInput], and that the flow follows the writes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StatsRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val settings = FakeSettingsRepository(UserSettings(lowStockGrams = 20.0))
    private val data = TestDatabase(settings = settings)
    private val stats = RoomStatsRepository(data.db.statsDao(), settings, PhotoFiles(java.io.File("photos")))

    @After
    fun tearDown() = data.close()

    private suspend fun input() = stats.observeInput().first()

    private suspend fun session(
        id: String,
        teaId: String? = "t1",
        status: SessionStatus = SessionStatus.COMPLETED,
        steps: List<SessionStepEntity> = emptyList(),
        sessionTaste: TasteProfile = TasteProfile.EMPTY,
        descriptors: List<String> = emptyList(),
    ) {
        data.db.sessionDao().insert(
            BrewSessionEntity(
                id = id,
                status = status,
                teaId = teaId,
                teaName = "Snapshot-Sencha",
                teaSecondaryName = null,
                teaType = "Sencha",
                styleKey = null,
                category = null,
                profileId = null,
                profileName = "Standard",
                method = BrewMethod.KYUSU,
                vesselId = null,
                vesselName = null,
                leafGrams = 4.0,
                waterMl = 100,
                baseTempC = 70,
                preAlertSeconds = null,
                plannedStepCount = steps.size,
                costCents = 120L,
                startedAt = 1_000L,
                endedAt = if (status == SessionStatus.IN_PROGRESS) null else 2_000L,
                zoneId = "Europe/Berlin",
                rating = 4.5,
                tasteUmami = sessionTaste.umami,
                tasteSweetness = sessionTaste.sweetness,
                tasteBitterness = sessionTaste.bitterness,
                tasteAstringency = sessionTaste.astringency,
                tasteBody = sessionTaste.body,
                tasteAroma = sessionTaste.aroma,
                notes = null,
                autoFinished = false,
                timesOffered = false,
                timesApplied = false,
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )
        data.db.sessionDao().insertSteps(steps.map { it.copy(sessionId = id) })
        descriptors.forEachIndexed { i, key ->
            data.db.tastingDao().insertDescriptor(SessionDescriptorEntity(id, key, createdAt = i.toLong()))
        }
    }

    private fun step(position: Int, umami: Int? = null, ended: Boolean = true) = SessionStepEntity(
        id = "step-$position-${umami ?: "x"}",
        sessionId = "",
        position = position,
        kind = StepKind.INFUSION,
        plannedSeconds = 60,
        actualSeconds = if (ended) 60 else null,
        pausedSeconds = null,
        overtimeSeconds = null,
        tempC = 70,
        startedAt = null,
        endedAt = null,
        skipped = false,
        overtimeAutoAck = false,
        note = null,
        createdAt = 1L,
        updatedAt = 1L,
        preAlertSeconds = null,
        tasteUmami = umami,
    )

    private suspend fun event(id: String, type: InventoryEventType, grams: Double, price: Long?, teaId: String = "t1") =
        data.stock.addEvent(
            InventoryEvent(
                id = id,
                teaId = teaId,
                type = type,
                deltaGrams = grams,
                occurredAtMillis = 5_000L,
                priceCents = price,
            ),
        )

    @Test
    fun `a running session is excluded, finished and manual ones count`() = runTest {
        data.teas.saveTea(tea("t1"))
        session("done")
        session("hand", status = SessionStatus.MANUAL)
        session("running", status = SessionStatus.IN_PROGRESS)

        assertThat(input().sessions.map { it.id }).containsExactly("done", "hand")
    }

    @Test
    fun `a deleted tea's session counts with its snapshot and no tea id, an archived tea counts`() = runTest {
        data.teas.saveTea(tea("t1", "Hisui"))
        data.teas.saveTea(tea("t2", "Gyokuro Alt").copy(isArchived = true))
        session("s1", teaId = "t1")
        session("s2", teaId = "t2")
        data.teas.deleteTea("t1")

        val input = input()

        val orphan = input.sessions.single { it.id == "s1" }
        assertThat(orphan.teaId).isNull()
        assertThat(orphan.teaName).isEqualTo("Snapshot-Sencha")
        assertThat(orphan.teaType).isEqualTo("Sencha")
        assertThat(input.sessions.single { it.id == "s2" }.teaId).isEqualTo("t2")
        assertThat(input.teas.map { it.id to it.archived }).containsExactly("t2" to true)
    }

    @Test
    fun `only priced initial and purchase events are passed`() = runTest {
        data.teas.saveTea(tea("t1"))
        event("e1", InventoryEventType.INITIAL, 50.0, 1_200L)
        event("e2", InventoryEventType.PURCHASE, 50.0, 1_500L)
        event("e3", InventoryEventType.PURCHASE, 50.0, null)
        event("e4", InventoryEventType.BREW, -4.0, 300L)
        event("e5", InventoryEventType.ADJUSTMENT, -1.0, 100L)

        assertThat(input().inventoryEvents.map { it.id }).containsExactly("e1", "e2")
    }

    @Test
    fun `an unknown descriptor key is dropped and the known ones keep their order`() = runTest {
        data.teas.saveTea(tea("t1"))
        session("s1", descriptors = listOf("marine.nori", "erfunden.zeug", "vegetal.spinat"))

        assertThat(input().sessions.single().descriptorKeys).containsExactly("marine.nori", "vegetal.spinat").inOrder()
    }

    @Test
    fun `per-step taste and the session-level taste of a v0_6_0 session are delivered`() = runTest {
        data.teas.saveTea(tea("t1"))
        session("v7", steps = listOf(step(0, umami = 4), step(1, umami = 2, ended = false)))
        session("v6", sessionTaste = TasteProfile(umami = 3, aroma = 5))

        val sessions = input().sessions.associateBy { it.id }

        assertThat(sessions.getValue("v7").steps.map { it.taste.umami }).containsExactly(4, 2).inOrder()
        assertThat(sessions.getValue("v7").steps.map { it.ended }).containsExactly(true, false).inOrder()
        assertThat(sessions.getValue("v7").sessionTaste).isEqualTo(TasteProfile.EMPTY)
        assertThat(sessions.getValue("v6").sessionTaste).isEqualTo(TasteProfile(umami = 3, aroma = 5))
    }

    @Test
    fun `a tea carries its cover photo file and style key`() = runTest {
        data.teas.saveTea(tea("a", "Asamushi").copy(styleKey = "sencha"))
        data.teas.saveTea(tea("b", "Bancha"))
        data.db.sessionPhotoDao().insert(
            PhotoEntity("p1", "a", null, null, 0, 10, 10, 10, PhotoOrigin.OWN, 1, 1),
        )
        data.db.openHelper.writableDatabase.execSQL("UPDATE tea SET cover_photo_id = 'p1' WHERE id = 'a'")

        val teas = input().teas.associateBy { it.id }

        assertThat(teas.getValue("a").coverPhoto?.name).isEqualTo("p1.jpg")
        assertThat(teas.getValue("a").styleKey).isEqualTo("sencha")
        assertThat(teas.getValue("b").coverPhoto).isNull()
    }

    @Test
    fun `teas carry their stock level by the own or the general threshold`() = runTest {
        data.teas.saveTea(tea("a", "Asamushi"))
        data.teas.saveTea(tea("b", "Bancha").copy(lowStockGrams = 5.0, lowStockWarning = false))
        data.teas.saveTea(tea("c", "Cha-no-ha"))
        data.teas.saveTea(tea("d", "Dashi-Cha"))
        event("e1", InventoryEventType.PURCHASE, 15.0, null, teaId = "a")
        event("e2", InventoryEventType.PURCHASE, 15.0, null, teaId = "b")
        event("e3", InventoryEventType.PURCHASE, 0.0, null, teaId = "c")
        event("e4", InventoryEventType.PURCHASE, 80.0, null, teaId = "d")

        val teas = input().teas.associateBy { it.id }

        assertThat(teas.getValue("a").stockLevel).isEqualTo(StockLevel.LOW)
        assertThat(teas.getValue("b").stockLevel).isEqualTo(StockLevel.AVAILABLE)
        assertThat(teas.getValue("b").lowStockWarning).isFalse()
        assertThat(teas.getValue("c").stockLevel).isEqualTo(StockLevel.EMPTY)
        assertThat(teas.getValue("d").stockLevel).isEqualTo(StockLevel.AVAILABLE)
        assertThat(teas.getValue("d").remainingGrams).isEqualTo(80.0)
    }

    @Test
    fun `the flow re-emits after a session finishes, a session is deleted and a purchase is added`() = runTest {
        data.teas.saveTea(tea("t1"))
        val emissions = mutableListOf<StatsInput>()
        backgroundScope.launch { stats.observeInput().toList(emissions) }

        suspend fun awaitLatest(what: String, condition: (StatsInput) -> Boolean) = withContext(Dispatchers.Default) {
            withTimeout(WAIT_MS) {
                while (emissions.lastOrNull()?.let(condition) != true) delay(POLL_MS)
            }
        }.also { assertWithMessage(what).that(emissions.last().let(condition)).isTrue() }

        awaitLatest("first emission") { it.sessions.isEmpty() }

        session("s1", status = SessionStatus.IN_PROGRESS)
        val running = data.db.sessionDao().get("s1")!!
        data.db.sessionDao().update(running.copy(status = SessionStatus.COMPLETED))
        awaitLatest("finished") { input -> input.sessions.map { it.id } == listOf("s1") }

        RoomJournalRepository(data.db, PhotoFiles(java.io.File("photos"))).deleteSession("s1")
        awaitLatest("deleted") { it.sessions.isEmpty() }

        event("e1", InventoryEventType.PURCHASE, 50.0, 1_500L)
        awaitLatest("purchase") { input -> input.inventoryEvents.map { it.id } == listOf("e1") }
    }

    @Test
    fun `the use case computes the tabs from the input with the clock's day`() = runTest {
        data.teas.saveTea(tea("t1"))
        session("s1")
        val clock = object : WallClock {
            override fun nowMs(): Long = 1_000L

            override fun zone(): ZoneId = ZoneOffset.UTC
        }
        val observe = ObserveStats(stats, clock)

        assertThat(observe.overview(StatsRange.ALL).first().sessionCount).isEqualTo(1)
        assertThat(observe.ratings(StatsRange.ALL).first().ratedCount).isEqualTo(1)
        assertThat(observe.consumption(StatsRange.ALL).first().grams).isEqualTo(4.0)
        assertThat(observe.calculator().first().hasSessions).isTrue()
    }

    private companion object {
        const val WAIT_MS = 10_000L
        const val POLL_MS = 20L
    }
}
