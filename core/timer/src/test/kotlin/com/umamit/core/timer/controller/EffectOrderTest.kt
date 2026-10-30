package com.umamit.core.timer.controller

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.TimerPhase
import com.umamit.core.model.TimerSlot
import com.umamit.core.testing.AlarmsReconciled
import com.umamit.core.testing.AlertStarted
import com.umamit.core.testing.AlertStopped
import com.umamit.core.testing.AlertWatchdogSet
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.NotificationShown
import com.umamit.core.testing.PreAlertPlayed
import com.umamit.core.testing.SavedSheetShown
import com.umamit.core.testing.ServiceStarted
import com.umamit.core.testing.ServiceStopped
import com.umamit.core.timer.engine.DueTimes
import com.umamit.core.timer.engine.SessionKind
import com.umamit.core.timer.engine.TimerEvent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZonedDateTime

/**
 * The order of the controller's calls across the ports (04 §4, D-047): the
 * transition is persisted first, then the alarms are reconciled with the full
 * due set of the new state, then the side effects run in the engine's order.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EffectOrderTest {
    private val h = ControllerHarness()

    @Before
    fun setUp() = runTest { h.teaWithStock() }

    @After
    fun tearDown() = h.close()

    /** Started at the harness clock's 5 000 000 ms elapsed, 2026-10-22 10:00 Berlin. */
    private val startElapsed = h.clock.elapsed

    @Test
    fun `starting a step persists it, then arms the pre-alert and the deadline, then shows the Live Update`() =
        runTest {
            h.controller.startSession(brewStart(infusion(60)))
            h.log.clear()

            h.controller.send(TimerEvent.Start)

            assertThat(h.log.calls).containsExactly(
                Persisted(TimerPhase.RUNNING),
                AlarmsReconciled(
                    TimerSlot.BREW,
                    DueTimes(
                        preAlertAtElapsedMs = startElapsed + 50_000,
                        deadlineElapsedMs =
                        startElapsed + 60_000,
                    ),
                ),
                ServiceStarted(TimerPhase.RUNNING),
                NotificationShown(TimerPhase.RUNNING, fullScreen = false),
                // After the turn: the deadline guard, 90 s after the deadline (D-068).
                AlertWatchdogSet(TimerSlot.BREW, startElapsed + 60_000 + 90_000),
            ).inOrder()
        }

    @Test
    fun `the deadline persists ALERTING before it ensures the service, starts the alert and posts it full screen`() =
        runTest {
            h.controller.startSession(brewStart(infusion(60)))
            h.controller.send(TimerEvent.Start)
            h.advanceSeconds(50)
            h.log.clear()

            h.advanceSeconds(10)

            val alert = StepAlert(SessionKind.Brew(h.sessionId), stepIndex = 0, deadlineWallMs = h.clock.wall)
            assertThat(h.log.calls).containsExactly(
                Persisted(TimerPhase.ALERTING),
                AlarmsReconciled(
                    TimerSlot.BREW,
                    DueTimes(overtimeTimeoutAtElapsedMs = startElapsed + 60_000 + 600_000),
                ),
                ServiceStarted(TimerPhase.ALERTING),
                AlertStarted(alert),
                NotificationShown(TimerPhase.ALERTING, fullScreen = true),
                // After the turn: the alert is watched while it plays (D-063).
                AlertWatchdogSet(TimerSlot.BREW, h.clock.elapsed + 30_000),
            ).inOrder()
        }

    @Test
    fun `the pre-alert persists its played flag before the tick`() = runTest {
        h.controller.startSession(brewStart(infusion(60)))
        h.controller.send(TimerEvent.Start)
        h.log.clear()

        h.advanceSeconds(50)

        assertThat(h.log.calls).containsExactly(
            Persisted(TimerPhase.RUNNING),
            AlarmsReconciled(TimerSlot.BREW, DueTimes(deadlineElapsedMs = startElapsed + 60_000)),
            ServiceStarted(TimerPhase.RUNNING),
            PreAlertPlayed,
            NotificationShown(TimerPhase.RUNNING, fullScreen = false),
        ).inOrder()
    }

    @Test
    fun `a pause arms the idle demote and the midnight timeout of the waiting session`() = runTest {
        h.controller.startSession(brewStart(infusion(60)))
        h.controller.send(TimerEvent.Start)
        h.advanceSeconds(20)
        h.log.clear()

        h.controller.send(TimerEvent.Pause)

        // Paused at 10:00:20; idle demote 30 min later, midnight is the first one at least 6 h later.
        val midnight = ZonedDateTime.of(2026, 10, 23, 0, 0, 0, 0, FakeClock.BERLIN).toInstant().toEpochMilli()
        assertThat(h.log.calls).containsExactly(
            Persisted(TimerPhase.PAUSED),
            AlarmsReconciled(
                TimerSlot.BREW,
                DueTimes(idleDemoteAtWallMs = h.clock.wall + 30 * 60_000, midnightAtWallMs = midnight),
            ),
            NotificationShown(TimerPhase.PAUSED, fullScreen = false),
            // No step runs: the deadline guard goes (D-068).
            AlertWatchdogSet(TimerSlot.BREW, null),
        ).inOrder()
    }

    @Test
    fun `Abgegossen records the step and stops the alert before the next step is shown`() = runTest {
        h.controller.startSession(brewStart(infusion(10), infusion(20)))
        h.controller.send(TimerEvent.Start)
        h.advanceSeconds(12)
        h.log.clear()

        h.controller.send(TimerEvent.Poured)

        assertThat(h.log.calls.first()).isEqualTo(Persisted(TimerPhase.READY))
        assertThat(h.log.calls.drop(2)).containsExactly(
            AlertStopped,
            NotificationShown(TimerPhase.READY, fullScreen = false),
            AlertWatchdogSet(TimerSlot.BREW, null),
        ).inOrder()
        assertThat(h.sessions.getSession(h.sessionId)!!.steps[0].overtimeSeconds).isEqualTo(2)
    }

    @Test
    fun `Sitzung beenden clears the timer, cancels every alarm and stops the service before the saved sheet`() =
        runTest {
            h.controller.startSession(brewStart(infusion(10)))
            h.controller.send(TimerEvent.Start)
            h.advanceSeconds(10)
            h.controller.send(TimerEvent.Poured)
            h.log.clear()

            h.controller.send(TimerEvent.FinishSession)

            assertThat(h.log.calls).containsExactly(
                Persisted(null),
                AlarmsReconciled(TimerSlot.BREW, DueTimes.NONE),
                ServiceStopped,
                SavedSheetShown(h.savedSheet.shown.single()),
            ).inOrder()
        }
}
