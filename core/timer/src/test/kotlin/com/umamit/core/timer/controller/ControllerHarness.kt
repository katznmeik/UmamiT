package com.umamit.core.timer.controller

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.umamit.core.data.ActiveBrewStore
import com.umamit.core.data.RoomActiveBrewStore
import com.umamit.core.data.RoomDatabaseTransactions
import com.umamit.core.data.RoomSessionRepository
import com.umamit.core.data.RoomStockRepository
import com.umamit.core.data.RoomTeaRepository
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.StepKind
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.model.TimerPhase
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeSavedSheet
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.testing.FakeTimerAlarms
import com.umamit.core.testing.FakeTimerAlertPlayer
import com.umamit.core.testing.FakeTimerDiagnostics
import com.umamit.core.testing.FakeTimerNotifier
import com.umamit.core.testing.FakeTimerService
import com.umamit.core.testing.FakeTimerWakeUp
import com.umamit.core.testing.PortCall
import com.umamit.core.testing.TimerPortLog
import com.umamit.core.timer.engine.BrewTimerEngine
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.TimerSnapshot
import kotlinx.coroutines.CompletableDeferred
import javax.inject.Provider

/**
 * The controller as spec #40 seam 2 tests it: the real engine and the real
 * repositories on an in-memory Room database, a fake clock with a boot count,
 * and fake ports that share one [log], so the order of the controller's calls
 * across ports (and the store's persist) is observable. A test can put the
 * production alert port in place of the fake one.
 */
class ControllerHarness(
    /** The alert port; the default is [alert]. A real one reaches the controller through [controllerProvider]. */
    alertPort: (ControllerHarness.() -> TimerAlertPort)? = null,
    /** The diagnostics port; the default is [diagnostics]. */
    diagnosticsPort: TimerDiagnosticsPort? = null,
    /** The service and notifier ports; the default is [service] and [notifier]. The Android adapters for M1-11. */
    private val notificationPorts: (ControllerHarness.() -> Pair<TimerServicePort, TimerNotifierPort>)? = null,
    val clock: FakeClock = FakeClock(),
    val settings: FakeSettingsRepository = FakeSettingsRepository(),
    val db: UmamiDatabase = Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), UmamiDatabase::class.java)
        .allowMainThreadQueries()
        .build(),
) {
    val log = TimerPortLog()
    val sessions = RoomSessionRepository(db, settings, clock)
    val teas = RoomTeaRepository(db.teaDao(), clock)
    val stock = RoomStockRepository(db.inventoryDao(), clock)

    /** The real store, through a decorator that logs each persist and can fail or hold it. */
    val store = ObservedStore(RoomActiveBrewStore(db, clock), log)

    val alarms = FakeTimerAlarms(log)
    val service = FakeTimerService(log)
    val notifier = FakeTimerNotifier(log)
    val alert = FakeTimerAlertPlayer(log)
    val diagnostics = FakeTimerDiagnostics()
    val savedSheet = FakeSavedSheet(log)
    val wakeUp = FakeTimerWakeUp(clock)

    private val shownBy = notificationPorts?.invoke(this)

    /** The controller for a port that reports back to it (Hilt gives the alert port a provider, too). */
    val controllerProvider: Provider<BrewSessionController> = Provider { controller }

    val controller = BrewSessionController(
        clocks = TimerClocks(monotonic = clock, wall = clock),
        sessions = sessions,
        activeBrew = store,
        transactions = RoomDatabaseTransactions(db),
        settings = settings,
        ports = TimerPorts(
            alarms = alarms,
            service = shownBy?.first ?: service,
            notifier = shownBy?.second ?: notifier,
            alert = alertPort?.invoke(this) ?: alert,
            diagnostics = diagnosticsPort ?: diagnostics,
            savedSheet = savedSheet,
            wakeUp = wakeUp,
        ),
    )

    val ui: TimerUiState get() = controller.state.value

    /** The active state; fails when the controller is idle. */
    val active: TimerUiState.Active get() = ui as TimerUiState.Active

    val sessionId: String get() = active.sessionId!!

    /** Moves time forward by [seconds], letting the in-process wake-up fire whenever it comes due. */
    suspend fun advanceSeconds(seconds: Long) = wakeUp.advanceBy(seconds * MS_PER_SECOND)

    /** Tea „t1" with 100 g bought for 8,50 € a day before the session (8.5 ct/g). */
    suspend fun teaWithStock() {
        teas.saveTea(Tea(id = TEA_ID, name = TEA_NAME, category = TeaCategory.GREEN, form = TeaForm.LOOSE_LEAF))
        stock.addEvent(
            InventoryEvent(
                id = "buy",
                teaId = TEA_ID,
                type = InventoryEventType.INITIAL,
                deltaGrams = 100.0,
                occurredAtMillis = clock.wall - DAY_MS,
                priceCents = 850,
            ),
        )
    }

    /**
     * The process died and a new one starts (E3, 04 §7.2): a new controller with fresh ports
     * on the same database, settings and clocks. Close only one of the two harnesses.
     */
    fun processRestarted(): ControllerHarness =
        ControllerHarness(clock = clock, settings = settings, db = db, notificationPorts = notificationPorts)

    /**
     * The database came to a new phone (or a reset one) in a device backup (E11, 05 §8): restore
     * detection cleared `active_brew` when it opened, so only the session rows are left; then
     * the app's process starts.
     */
    suspend fun restoredOnAnotherPhone(): ControllerHarness {
        db.activeBrewDao().clear()
        return processRestarted()
    }

    fun close() = db.close()

    companion object {
        const val TEA_ID = "t1"
        const val TEA_NAME = "Bio Sencha Hisui"
        const val MS_PER_SECOND = 1_000L
        const val DAY_MS = 24 * 3_600_000L
    }
}

/** The request of *Brühen starten* for tea „t1" with 4 g and 200 ml in the „Kyusu" recipe. */
fun brewStart(
    vararg steps: PlannedStep,
    recipePreAlertSeconds: Int? = null,
    method: BrewMethod = BrewMethod.KYUSU,
    leafGrams: Double? = 4.0,
    waterMl: Int? = 200,
) = BrewStart(
    snapshot = SessionSnapshot(
        teaId = ControllerHarness.TEA_ID,
        teaName = ControllerHarness.TEA_NAME,
        teaSecondaryName = "Hisui",
        teaType = "Sencha",
        styleKey = "sencha",
        category = TeaCategory.GREEN,
        profileId = null,
        profileName = "Kyusu",
        method = method,
        vesselId = null,
        vesselName = "Kyusu",
        leafGrams = leafGrams,
        waterMl = waterMl,
        baseTempC = 60,
    ),
    steps = steps.toList(),
    recipePreAlertSeconds = recipePreAlertSeconds,
)

/** An infusion with its own [preAlert]: null *Automatisch*, 0 *Aus*, else the lead in seconds (TMR-23, D-093). */
fun infusion(seconds: Int, tempC: Int = 60, preAlert: Int? = null) =
    PlannedStep(StepKind.INFUSION, seconds, tempC, preAlert)

fun rinse(seconds: Int) = PlannedStep(StepKind.RINSE, seconds, tempC = 70)

/** The persist of [phase] (null: the row was cleared) in the shared port log. */
data class Persisted(val phase: TimerPhase?) : PortCall

/**
 * The real [ActiveBrewStore] with a test handle: each persist is logged as [Persisted];
 * [failNext] makes the next persists throw like a full disk; [hold] keeps the next persist
 * waiting until the returned gate is opened. The slot methods (umamit.db v4) go to the real store as they are.
 */
class ObservedStore(private val real: ActiveBrewStore, private val log: TimerPortLog) : ActiveBrewStore by real {
    private var failures = 0
    private var readFailures = 0
    private var gate: Gate? = null

    /** Changes what the next reads return, e.g. into a snapshot `restore` refuses; null reads unchanged. */
    var readAs: ((TimerSnapshot?) -> TimerSnapshot?)? = null

    fun failNext(times: Int) {
        failures = times
    }

    /** Makes the next [times] reads throw like a database that cannot be opened. */
    fun failNextReads(times: Int) {
        readFailures = times
    }

    fun hold(): Gate = Gate().also { gate = it }

    override suspend fun read(): TimerSnapshot? {
        if (readFailures > 0) {
            readFailures--
            throw android.database.sqlite.SQLiteCantOpenDatabaseException("unable to open database file (test)")
        }
        val snapshot = real.read()
        return readAs?.invoke(snapshot) ?: snapshot
    }

    override suspend fun persist(snapshot: TimerSnapshot?) {
        gate?.let {
            gate = null
            it.reached.complete(Unit)
            it.open.await()
        }
        if (failures > 0) {
            failures--
            throw android.database.sqlite.SQLiteFullException("database or disk is full (test)")
        }
        real.persist(snapshot)
        log.record(Persisted(snapshot?.activeBrew?.phase))
    }

    override suspend fun clear() = real.clear()

    /** The phase of the stored `active_brew` row, read back through the store. */
    suspend fun storedPhase(): TimerPhase? = real.read()?.activeBrew?.phase

    /** The state the stored row restores to. */
    suspend fun stored() = BrewTimerEngine.restore(real.read())

    class Gate {
        val reached = CompletableDeferred<Unit>()
        val open = CompletableDeferred<Unit>()
    }
}
