package com.umamit.core.data

import androidx.datastore.core.DataStoreFactory
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.datastore.SettingsDataSource
import com.umamit.core.datastore.SettingsSerializer
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.SessionStepRow
import com.umamit.core.model.StepKind
import com.umamit.core.model.TimerSlot
import com.umamit.core.model.UserSettings
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.timer.engine.StepRecord
import com.umamit.core.timer.engine.TimerEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The session lifecycle of 05 §3.7 through the repositories, driven by the
 * timer engine's data effects: start, edit/add, record, complete (TMR-12,
 * SET-15, INV-06, INV-08) and discard (TMR-13).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SessionLifecycleTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val data = TestDatabase()

    @After
    fun tearDown() = data.close()

    /** Tea „t1" with 100 g bought for 8,50 € (8.5 ct/g), dated before the session. */
    private suspend fun teaWithStock() {
        data.teas.saveTea(tea("t1"))
        data.stock.addEvent(initial("buy", "t1", grams = 100.0, priceCents = 850, at = START_WALL_MS - DAY_MS))
    }

    private suspend fun session() = data.sessions.getSession(SESSION_ID)!!

    @Test
    fun `a started session keeps its snapshots and one planned row per step`() = runTest {
        teaWithStock()
        val driver = TimerDriver(data)

        driver.send(startEvent(rinse(10), infusion(60), infusion(20, tempC = 62), preAlert = 15))

        val session = session()
        assertThat(session.status).isEqualTo(SessionStatus.IN_PROGRESS)
        assertThat(session.snapshot).isEqualTo(snapshotOf())
        assertThat(session.preAlertSeconds).isEqualTo(15)
        assertThat(session.startedAtMs).isEqualTo(START_WALL_MS)
        assertThat(session.endedAtMs).isNull()
        assertThat(session.zoneId).isEqualTo("Europe/Berlin")
        assertThat(session.costCents).isNull()
        assertThat(session.steps).containsExactly(
            planned(0, StepKind.RINSE, 10, 70),
            planned(1, StepKind.INFUSION, 60, 60),
            // Temperatures are stored in 5 °C steps (GEN-12).
            planned(2, StepKind.INFUSION, 20, 60),
        ).inOrder()
    }

    @Test
    fun `editing and adding steps replaces only the unstarted rows and keeps the session's B`() = runTest {
        teaWithStock()
        val driver = TimerDriver(data)
        driver.send(startEvent(infusion(60), infusion(20), infusion(30)))
        driver.send(TimerEvent.Start)
        driver.clocks.advanceSeconds(10)

        driver.send(TimerEvent.EditSteps(listOf(infusion(60), infusion(25, tempC = 70))))
        driver.send(TimerEvent.AddStep)

        val session = session()
        // From umamit.db v2 the session's value is B as frozen at the start (D-093).
        assertThat(session.preAlertSeconds).isEqualTo(10)
        assertThat(session.steps).containsExactly(
            planned(0, StepKind.INFUSION, 60, 60),
            planned(1, StepKind.INFUSION, 25, 70),
            // TMR-10: 1.5 × 25 s = 37.5 s → 40 s at the last temperature.
            planned(2, StepKind.INFUSION, 40, 70),
        ).inOrder()
    }

    @Test
    fun `each step's pre-alert is stored with its row at the start, by an edit and for an added step`() = runTest {
        teaWithStock()
        val driver = TimerDriver(data)
        driver.send(startEvent(infusion(60, preAlert = 20), infusion(20), infusion(30, preAlert = 0)))
        assertThat(session().steps.map { it.preAlertSeconds }).containsExactly(20, null, 0).inOrder()

        driver.send(TimerEvent.Start)
        driver.clocks.advanceSeconds(10)
        // The running step changes only its pre-alert; the pending ones are replaced (SCR-31, D-093).
        driver.send(TimerEvent.EditSteps(listOf(infusion(60, preAlert = 15), infusion(20, preAlert = 5))))
        driver.send(TimerEvent.AddStep)

        val session = session()
        assertThat(session.steps.map { it.preAlertSeconds }).containsExactly(15, 5, null).inOrder()
        assertThat(session.preAlertSeconds).isEqualTo(10)
    }

    @Test
    fun `a finished step stores the engine's recorded data`() = runTest {
        teaWithStock()
        val driver = TimerDriver(data)
        driver.send(startEvent(infusion(60), infusion(20)))
        driver.send(TimerEvent.Start)
        val startedAt = driver.clocks.wall
        driver.clocks.advanceSeconds(20)
        driver.send(TimerEvent.Pause)
        driver.clocks.advanceSeconds(15)
        driver.send(TimerEvent.Resume)
        driver.clocks.advanceSeconds(40)
        driver.send(TimerEvent.DeadlineReached)
        driver.clocks.advanceSeconds(7)

        driver.send(TimerEvent.Poured)

        assertThat(session().steps.first()).isEqualTo(
            SessionStepRow(
                position = 0,
                kind = StepKind.INFUSION,
                plannedSeconds = 60,
                tempC = 60,
                actualSeconds = 67,
                pausedSeconds = 15,
                overtimeSeconds = 7,
                startedAtWallMs = startedAt,
                endedAtWallMs = driver.clocks.wall,
                skipped = false,
                overtimeAutoAck = false,
            ),
        )
    }

    @Test
    fun `completing after two of three steps keeps the plan count, cost, stock deduction and opened date`() = runTest {
        teaWithStock()
        val driver = TimerDriver(data)
        driver.send(startEvent(rinse(10), infusion(60), infusion(20)))
        driver.send(TimerEvent.SkipStep)
        driver.send(TimerEvent.Start)
        driver.clocks.advanceSeconds(60)
        driver.send(TimerEvent.DeadlineReached)
        driver.clocks.advanceSeconds(2)
        driver.send(TimerEvent.Poured)
        val pouredAt = driver.clocks.wall
        driver.clocks.advanceSeconds(300)

        driver.send(TimerEvent.FinishSession)

        val session = session()
        assertThat(session.status).isEqualTo(SessionStatus.COMPLETED)
        // „2 von 3 Aufgüssen": the never-started third row is gone, the plan count stays.
        assertThat(session.plannedStepCount).isEqualTo(3)
        assertThat(session.steps.map { it.position to it.skipped }).containsExactly(0 to true, 1 to false).inOrder()
        assertThat(session.endedAtMs).isEqualTo(pouredAt)
        // 4 g × 8.5 ct/g = 34 ct (INV-08).
        assertThat(session.costCents).isEqualTo(34)
        val brew = data.stock.observeEvents("t1").first().single { it.type == InventoryEventType.BREW }
        assertThat(brew.sessionId).isEqualTo(SESSION_ID)
        assertThat(brew.deltaGrams).isEqualTo(-4.0)
        assertThat(brew.occurredAtMillis).isEqualTo(pouredAt)
        assertThat(data.stock.observeStock("t1").first().remainingGrams).isWithin(1e-9).of(96.0)
        assertThat(data.teas.getTea("t1")!!.openedDate).isEqualTo(LocalDate.of(2026, 10, 22))
    }

    @Test
    fun `finishing a running step records it as ended early before completing`() = runTest {
        teaWithStock()
        val driver = TimerDriver(data)
        driver.send(startEvent(infusion(60), infusion(20)))
        driver.send(TimerEvent.Start)
        driver.clocks.advanceSeconds(30)

        driver.send(TimerEvent.FinishSession)

        val session = session()
        assertThat(session.plannedStepCount).isEqualTo(2)
        assertThat(session.steps.single().actualSeconds).isEqualTo(30)
        assertThat(session.endedAtMs).isEqualTo(driver.clocks.wall)
    }

    @Test
    fun `with Bestand abziehen off a completed session deducts nothing`() = runTest {
        teaWithStock()
        data.settings.update { it.copy(deductStock = false) }

        completeOneStep(TimerDriver(data))

        assertThat(session().status).isEqualTo(SessionStatus.COMPLETED)
        assertThat(data.stock.observeEvents("t1").first().map { it.type }).containsExactly(InventoryEventType.INITIAL)
        assertThat(session().costCents).isEqualTo(34)
    }

    @Test
    fun `a tea without priced stock gets no cost, and a session without a tea deducts nothing`() = runTest {
        data.teas.saveTea(tea("t1"))
        completeOneStep(TimerDriver(data))
        assertThat(session().costCents).isNull()

        val noTea = TimerDriver(data) { id, steps, preAlert ->
            sessionStart(id, steps, preAlert, TestClocks(), snapshot = snapshotOf(teaId = null))
        }
        completeOneStep(noTea, id = "s2")

        assertThat(data.sessions.getSession("s2")!!.costCents).isNull()
        assertThat(data.stock.observeEvents("t1").first().filter { it.sessionId == "s2" }).isEmpty()
    }

    @Test
    fun `the midnight timeout stores the session as auto-finished, Sitzung beenden does not`() = runTest {
        teaWithStock()
        completeOneStep(TimerDriver(data))
        assertThat(session().autoFinished).isFalse()

        val driver = TimerDriver(data)
        driver.send(startEvent(infusion(60), id = "s2"))
        driver.send(TimerEvent.Start)
        driver.clocks.advanceSeconds(60)
        driver.send(TimerEvent.DeadlineReached)
        driver.send(TimerEvent.Poured)
        // Idle from 10:01 on: the first midnight at least 6 h later is 23 Oct, 00:00 in Berlin (TMR-14).
        driver.clocks.wall = LocalDate.of(2026, 10, 23).atStartOfDay(BERLIN).toInstant().toEpochMilli()

        driver.send(TimerEvent.MidnightTimeout)

        val auto = data.sessions.getSession("s2")!!
        assertThat(auto.status).isEqualTo(SessionStatus.COMPLETED)
        assertThat(auto.autoFinished).isTrue()
    }

    /**
     * The cost is leaf × Σ price ÷ Σ grams in exact decimals, rounded half up once (INV-08,
     * O1 of the #27 slice review): a double product lands below the exact half.
     */
    @Test
    fun `the session cost rounds exact halves up`() = runTest {
        // (price in ct, package grams, leaf grams, expected cost in ct)
        val cases = listOf(
            Cost(850, 100.0, 5.0, 43), // Tanabata Kyusu: 42.5 ct
            Cost(1790, 100.0, 5.0, 90), // Hisui Kyusu: 89.5 ct
            Cost(1790, 100.0, 25.0, 448), // 447.5 ct
            Cost(100, 4.0, 2.3, 58), // 57.5 ct
        )
        cases.forEachIndexed { i, case ->
            data.teas.saveTea(tea("t$i"))
            data.stock.addEvent(initial("buy$i", "t$i", case.grams, case.priceCents, at = START_WALL_MS - DAY_MS))
            val driver = TimerDriver(data) { id, steps, preAlert ->
                sessionStart(id, steps, preAlert, TestClocks(), snapshotOf(teaId = "t$i", leafGrams = case.leaf))
            }

            completeOneStep(driver, id = "s$i")

            assertWithMessage("$case").that(data.sessions.getSession("s$i")!!.costCents).isEqualTo(case.expected)
        }
    }

    private data class Cost(val priceCents: Long, val grams: Double, val leaf: Double, val expected: Long)

    /**
     * S4 of the #27 slice review: the data effects run inside the controller's transition
     * transaction, so predictable bad input must not throw and roll back its `Persist`.
     */
    @Test
    fun `recording a step whose row is missing changes nothing and does not throw`() = runTest {
        val record = StepRecord(60, 60, 0, 0, START_WALL_MS, START_WALL_MS + 60_000, false, false)

        data.transactions.inTransaction { data.sessions.recordStep("missing", 0, record) }

        assertThat(data.sessions.getSession("missing")).isNull()
    }

    @Test
    fun `unreadable settings complete the session with the default Bestand abziehen`() = runTest {
        val broken = TestDatabase(settings = UnreadableSettings())
        try {
            broken.teas.saveTea(tea("t1"))
            broken.stock.addEvent(initial("buy", "t1", grams = 100.0, priceCents = 850, at = START_WALL_MS - DAY_MS))

            completeOneStep(TimerDriver(broken))

            assertThat(broken.sessions.getSession(SESSION_ID)!!.status).isEqualTo(SessionStatus.COMPLETED)
            assertThat(broken.activeBrew.read(TimerSlot.BREW)).isNull()
            assertThat(broken.stock.observeStock("t1").first().remainingGrams).isWithin(1e-9).of(96.0)
        } finally {
            broken.close()
        }
    }

    @Test
    fun `a session zone this device does not know completes in the device zone`() = runTest {
        teaWithStock()
        val driver = TimerDriver(data) { id, steps, preAlert ->
            sessionStart(id, steps, preAlert, TestClocks()).copy(zoneId = "Mars/Olympus")
        }

        completeOneStep(driver)

        assertThat(session().status).isEqualTo(SessionStatus.COMPLETED)
        assertThat(data.activeBrew.read(TimerSlot.BREW)).isNull()
        val deviceDate = Instant.ofEpochMilli(START_WALL_MS).atZone(ZoneId.systemDefault()).toLocalDate()
        assertThat(data.teas.getTea("t1")!!.openedDate).isEqualTo(deviceDate)
    }

    /** S7 of the #27 slice review: the production settings (a DataStore file) read inside the transaction. */
    @Test
    fun `the real settings store is read inside the transition transaction`() = runTest {
        val store = DataStoreSettingsRepository(
            SettingsDataSource(
                DataStoreFactory.create(SettingsSerializer, scope = backgroundScope) { File(folder.root, "s.json") },
            ),
        )
        store.update { it.copy(deductStock = false) }
        val real = TestDatabase(settings = store)
        try {
            real.teas.saveTea(tea("t1"))
            real.stock.addEvent(initial("buy", "t1", grams = 100.0, priceCents = 850, at = START_WALL_MS - DAY_MS))

            completeOneStep(TimerDriver(real))

            assertThat(real.sessions.getSession(SESSION_ID)!!.status).isEqualTo(SessionStatus.COMPLETED)
            assertThat(
                real.stock.observeEvents("t1").first().map {
                    it.type
                },
            ).containsExactly(InventoryEventType.INITIAL)
        } finally {
            real.close()
        }
    }

    @Test
    fun `completing twice changes nothing the second time`() = runTest {
        teaWithStock()
        completeOneStep(TimerDriver(data))
        val first = session()

        data.sessions.completeSession(SESSION_ID, autoFinished = false)

        assertThat(session()).isEqualTo(first)
        assertThat(data.stock.observeEvents("t1").first().count { it.type == InventoryEventType.BREW }).isEqualTo(1)
    }

    @Test
    fun `discarding removes the session, its steps, its stock deduction and the active brew`() = runTest {
        teaWithStock()
        completeOneStep(TimerDriver(data))
        val driver = TimerDriver(data)
        driver.send(startEvent(infusion(60), id = "s2"))
        data.stock.addEvent(InventoryEvent("brew-s2", "t1", InventoryEventType.BREW, -4.0, 1L, sessionId = "s2"))

        driver.send(TimerEvent.Discard)

        assertThat(data.sessions.getSession("s2")).isNull()
        assertThat(data.activeBrew.read(TimerSlot.BREW)).isNull()
        assertThat(data.stock.observeEvents("t1").first().map { it.id }).doesNotContain("brew-s2")
        // The other session and its deduction stay.
        assertThat(session().status).isEqualTo(SessionStatus.COMPLETED)

        data.sessions.discardSession(SESSION_ID)

        assertThat(data.sessions.getSession(SESSION_ID)).isNull()
        assertThat(data.stock.observeStock("t1").first().remainingGrams).isWithin(1e-9).of(100.0)
    }

    @Test
    fun `the opened date is set once and not moved by later sessions`() = runTest {
        teaWithStock()
        completeOneStep(TimerDriver(data))
        val later = TestClocks(wall = START_WALL_MS + 3 * DAY_MS)

        completeOneStep(TimerDriver(data, later), id = "s2")

        assertThat(data.teas.getTea("t1")!!.openedDate).isEqualTo(LocalDate.of(2026, 10, 22))
    }

    @Test
    fun `a new package bought after the stock ran out sets a new opened date`() = runTest {
        data.teas.saveTea(tea("t1").copy(openedDate = LocalDate.of(2026, 9, 1)))
        data.stock.addEvent(initial("old", "t1", grams = 4.0, priceCents = 100, at = START_WALL_MS - 40 * DAY_MS))
        data.stock.addEvent(InventoryEvent("used", "t1", InventoryEventType.BREW, -4.0, START_WALL_MS - 30 * DAY_MS))
        data.stock.addEvent(purchaseTwoDaysAgo())

        completeOneStep(TimerDriver(data))

        assertThat(data.teas.getTea("t1")!!.openedDate).isEqualTo(LocalDate.of(2026, 10, 22))
    }

    @Test
    fun `a package bought while stock remained keeps the opened date`() = runTest {
        data.teas.saveTea(tea("t1").copy(openedDate = LocalDate.of(2026, 9, 1)))
        data.stock.addEvent(initial("old", "t1", grams = 50.0, priceCents = 400, at = START_WALL_MS - 40 * DAY_MS))
        data.stock.addEvent(purchaseTwoDaysAgo())

        completeOneStep(TimerDriver(data))

        assertThat(data.teas.getTea("t1")!!.openedDate).isEqualTo(LocalDate.of(2026, 9, 1))
    }

    /** Starts session [id] with one 60 s infusion, pours it on time and finishes. */
    private suspend fun completeOneStep(driver: TimerDriver, id: String = SESSION_ID) {
        driver.send(startEvent(infusion(60), id = id))
        driver.send(TimerEvent.Start)
        driver.clocks.advanceSeconds(60)
        driver.send(TimerEvent.DeadlineReached)
        driver.send(TimerEvent.Poured)
        driver.send(TimerEvent.FinishSession)
    }

    private fun planned(position: Int, kind: StepKind, seconds: Int, tempC: Int?) = SessionStepRow(
        position,
        kind,
        seconds,
        tempC,
        actualSeconds = null,
        pausedSeconds = null,
        overtimeSeconds = null,
        startedAtWallMs = null,
        endedAtWallMs = null,
        skipped = false,
        overtimeAutoAck = false,
    )

    /** 50 g bought for 9 € on 2026-10-20. */
    private fun purchaseTwoDaysAgo() =
        InventoryEvent("new", "t1", InventoryEventType.PURCHASE, 50.0, START_WALL_MS - 2 * DAY_MS, priceCents = 900)

    /** Settings whose file can't be read: DataStore's IOException on every read. */
    private class UnreadableSettings : SettingsRepository by FakeSettingsRepository() {
        override val settings: Flow<UserSettings> = flow { throw IOException("unreadable settings") }
    }

    private companion object {
        const val DAY_MS = 86_400_000L
    }
}
