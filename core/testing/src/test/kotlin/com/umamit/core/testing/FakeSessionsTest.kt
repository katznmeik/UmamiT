package com.umamit.core.testing

import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.SessionStart
import com.umamit.core.model.ActiveBrewSnapshot
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.StepKind
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.model.TimerPhase
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.engine.BrewTimerEngine
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.StepRecord
import com.umamit.core.timer.engine.TimerSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate

/** The session fakes follow the Room lifecycle (05 §3.7) where tests rely on it. */
class FakeSessionsTest {
    private val fakes = FakeSessions()

    private val start = SessionStart(
        id = "s1",
        snapshot = SessionSnapshot(
            "t1", "Hisui", null, null, null, TeaCategory.GREEN, null, null, BrewMethod.KYUSU,
            null, null, leafGrams = 4.0, waterMl = 200, baseTempC = 62,
        ),
        steps = listOf(PlannedStep(StepKind.INFUSION, 60, 62), PlannedStep(StepKind.INFUSION, 20, 60)),
        preAlertSeconds = 10,
        startedAtMs = START,
        zoneId = "Europe/Berlin",
    )

    @Test
    fun `complete keeps the plan count, deducts stock, sets cost and opened date, and discard removes it all`() =
        runTest {
            fakes.collection.teas.saveTea(Tea("t1", "Hisui", TeaCategory.GREEN, TeaForm.LOOSE_LEAF))
            fakes.collection.stock.addEvent(
                InventoryEvent("e1", "t1", InventoryEventType.INITIAL, 100.0, 1L, priceCents = 850),
            )
            fakes.sessions.startSession(start)
            fakes.sessions.recordStep(
                "s1",
                0,
                StepRecord(60, 62, 0, 2, START, START + 62_000, skipped = false, overtimeAutoAck = false),
            )

            fakes.sessions.completeSession("s1", autoFinished = false)

            val session = fakes.sessions.getSession("s1")!!
            assertThat(session.status).isEqualTo(SessionStatus.COMPLETED)
            assertThat(session.plannedStepCount).isEqualTo(2)
            assertThat(session.steps.map { it.tempC }).containsExactly(60)
            assertThat(session.costCents).isEqualTo(34)
            assertThat(session.endedAtMs).isEqualTo(START + 62_000)
            assertThat(fakes.collection.stock.observeStock("t1").first().remainingGrams).isWithin(1e-9).of(96.0)
            assertThat(fakes.collection.teas.getTea("t1")!!.openedDate).isEqualTo(LocalDate.of(1970, 1, 1))

            fakes.sessions.discardSession("s1")

            assertThat(fakes.sessions.getSession("s1")).isNull()
            assertThat(fakes.collection.stock.observeStock("t1").first().remainingGrams).isWithin(1e-9).of(100.0)
        }

    @Test
    fun `the store reads the session's current step rows and clears with the session`() = runTest {
        fakes.sessions.startSession(start)
        val ready = TimerSnapshot(readyRow(), fakes.sessions.getSession("s1")!!.steps, longStepsOnly = false)
        // A snapshot the engine accepts.
        BrewTimerEngine.restore(ready)
        fakes.activeBrew.persist(ready)

        assertThat(fakes.activeBrew.read()).isEqualTo(ready)

        fakes.sessions.discardSession("s1")

        assertThat(fakes.activeBrew.read()).isNull()
    }

    @Test
    fun `the store keeps one timer per slot as on Room (umamit_db v4)`() = runTest {
        fakes.sessions.startSession(start)
        val brew = TimerSnapshot(readyRow(), fakes.sessions.getSession("s1")!!.steps, longStepsOnly = false)
        val quick = TimerSnapshot(
            readyRow().copy(sessionId = null, isQuickTimer = true, quickDurationSeconds = 90),
            emptyList(),
            longStepsOnly = false,
        )
        // Snapshots the engine accepts.
        BrewTimerEngine.restore(brew)
        BrewTimerEngine.restore(quick)

        fakes.activeBrew.persist(TimerSlot.BREW, brew)
        fakes.activeBrew.persist(TimerSlot.QUICK, quick)

        assertThat(fakes.activeBrew.read(TimerSlot.BREW)).isEqualTo(brew)
        assertThat(fakes.activeBrew.read(TimerSlot.QUICK)).isEqualTo(quick)
        assertThat(fakes.activeBrew.read()).isEqualTo(brew)
        assertThat(runCatching { fakes.activeBrew.persist(TimerSlot.QUICK, brew) }.exceptionOrNull())
            .isInstanceOf(IllegalArgumentException::class.java)

        fakes.activeBrew.clear(TimerSlot.BREW)

        assertThat(fakes.activeBrew.read(TimerSlot.BREW)).isNull()
        assertThat(fakes.activeBrew.read()).isEqualTo(quick)

        // The single-timer persist leaves only its own slot.
        fakes.activeBrew.persist(TimerSlot.QUICK, quick)
        fakes.activeBrew.persist(brew)

        assertThat(fakes.activeBrew.read(TimerSlot.QUICK)).isNull()
        assertThat(fakes.activeBrew.read(TimerSlot.BREW)).isEqualTo(brew)
    }

    private fun readyRow() = ActiveBrewSnapshot(
        sessionId = "s1",
        isQuickTimer = false,
        quickDurationSeconds = null,
        stepIndex = 0,
        phase = TimerPhase.READY,
        deadlineElapsedMs = null,
        deadlineWallMs = null,
        preAlertElapsedMs = null,
        preAlertPlayed = false,
        remainingMs = null,
        pausedTotalMs = 0,
        pausedSinceElapsedMs = null,
        pausedSinceWallMs = null,
        readySinceWallMs = START,
        stepStartedWallMs = null,
        alertPlayed = false,
        bootCount = 1,
        lastInteractionWallMs = START,
        demoted = false,
        preAlertInheritedSeconds = 10,
        zoneId = "Europe/Berlin",
    )

    private companion object {
        const val START = 1_000L
    }
}
