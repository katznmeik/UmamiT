package com.umamit.core.timer.engine

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.model.StepKind
import com.umamit.core.timer.engine.TimerEffect.Persist
import com.umamit.core.timer.engine.TimerEffect.StartForeground
import com.umamit.core.timer.engine.TimerEffect.StopAlert
import com.umamit.core.timer.engine.TimerEffect.StopForeground
import com.umamit.core.timer.engine.TimerEffect.UpdateNotification
import org.junit.Test

/** The quick timer of 04 §10 (TMR-22): one infusion, the global pre-alert, nothing recorded. */
class QuickTimerTest {

    private val thirtyMinutes = 30 * 60_000L

    @Test
    fun `the quick timer waits in Ready with one infusion and the global pre-alert`() {
        val driver = EngineDriver()

        val effects = driver.send(TimerEvent.StartQuickTimer(durationSeconds = 90, globalPreAlertSeconds = 15))

        val ready = driver.state as TimerState.Ready
        assertThat(driver.outcome).isEqualTo(TransitionOutcome.Accepted)
        assertThat(ready.session.kind).isEqualTo(SessionKind.Quick)
        assertThat(ready.session.steps)
            .containsExactly(SessionStep(StepKind.INFUSION, 90, tempC = null, StepStatus.PENDING, record = null))
        assertThat(
            ready.session.preAlert,
        ).isEqualTo(PreAlertSettings(sessionOverrideSeconds = null, inheritedSeconds = 15))
        assertThat(ready.session.zone).isEqualTo(BERLIN)
        assertThat(effects).containsExactly(
            Persist,
            TimerEffect.ScheduleAlarms(DueTimes(quickCancelAtWallMs = START_WALL_MS + thirtyMinutes)),
            StartForeground,
            UpdateNotification,
        ).inOrder()
    }

    @Test
    fun `start, pause, resume and the deadline work as for a session`() {
        val driver = quick(60)

        driver.send(TimerEvent.Start)
        assertThat(driver.state).isInstanceOf(TimerState.Running::class.java)
        driver.clocks.advanceSeconds(20)
        driver.send(TimerEvent.Pause)
        assertThat((driver.state as TimerState.Paused).remainingMs).isEqualTo(40_000)
        driver.clocks.advanceSeconds(5)
        driver.send(TimerEvent.Resume)
        driver.clocks.advanceSeconds(40)
        val effects = driver.send(TimerEvent.DeadlineReached)

        assertThat(driver.state).isInstanceOf(TimerState.Alerting::class.java)
        assertThat(effects).contains(TimerEffect.StartAlert)
        assertThat(BrewTimerEngine.display(driver.state, driver.clocks.now())).isEqualTo(TimerDisplay(0, 0))
    }

    @Test
    fun `Fertig stops the alert and ends the quick timer without recording anything`() {
        val driver = quick(60).apply {
            send(TimerEvent.Start)
            clocks.advanceSeconds(65)
            send(TimerEvent.DeadlineReached)
        }

        val effects = driver.send(TimerEvent.Poured)

        assertThat(driver.state).isEqualTo(TimerState.Idle)
        assertThat(effects).containsExactly(Persist, NO_ALARMS, StopAlert, StopForeground).inOrder()
    }

    @Test
    fun `Abbrechen in Ready, Running and Paused ends the quick timer without recording anything`() {
        val cases = mapOf(
            "Ready" to quick(60),
            "Running" to quick(60).apply { send(TimerEvent.Start) },
            "Paused" to quick(60).apply {
                send(TimerEvent.Start)
                send(TimerEvent.Pause)
            },
        )
        cases.forEach { (name, driver) ->
            val effects = driver.send(TimerEvent.Cancel)

            assertWithMessage(name).that(driver.state).isEqualTo(TimerState.Idle)
            assertWithMessage(name).that(effects).containsExactly(Persist, NO_ALARMS, StopForeground).inOrder()
        }
    }

    @Test
    fun `Abbrechen is ignored while alerting, Fertig ends it`() {
        val driver = quick(60).apply {
            send(TimerEvent.Start)
            clocks.advanceSeconds(60)
            send(TimerEvent.DeadlineReached)
        }

        assertThat(driver.send(TimerEvent.Cancel)).isEmpty()
        assertThat(driver.outcome).isEqualTo(TransitionOutcome.Ignored)
    }

    @Test
    fun `the session-only events are ignored by the quick timer`() {
        val sessionEvents = listOf(
            TimerEvent.EndStepNow,
            TimerEvent.SkipStep,
            TimerEvent.FinishSession,
            TimerEvent.Discard,
            TimerEvent.AddStep,
            TimerEvent.EditSteps(listOf(infusion(60)), PreAlertChoice.Off),
        )
        val drivers = listOf(
            quick(60),
            quick(60).apply { send(TimerEvent.Start) },
            quick(60).apply {
                send(TimerEvent.Start)
                send(TimerEvent.Pause)
            },
            quick(60).apply {
                send(TimerEvent.Start)
                clocks.advanceSeconds(60)
                send(TimerEvent.DeadlineReached)
            },
        )
        drivers.forEach { driver ->
            sessionEvents.forEach { event ->
                val before = driver.state
                assertWithMessage("$event in $before").that(driver.send(event)).isEmpty()
                assertWithMessage("$event in $before").that(driver.state).isSameInstanceAs(before)
                assertWithMessage("$event in $before").that(driver.outcome).isEqualTo(TransitionOutcome.Ignored)
            }
        }
    }

    @Test
    fun `the quick timer never asks to confirm a discard, it has no Sitzung beenden`() {
        assertThat(BrewTimerEngine.finishWouldDiscard(quick(60).state)).isFalse()
    }

    @Test
    fun `an unstarted quick timer is cancelled 30 min after the last interaction`() {
        val driver = quick(60)
        driver.clocks.advance(thirtyMinutes - 1)
        assertThat(driver.send(TimerEvent.QuickTimerTimeout)).isEmpty()
        assertThat(driver.outcome).isEqualTo(TransitionOutcome.Ignored)

        driver.clocks.advance(1)
        val effects = driver.send(TimerEvent.QuickTimerTimeout)

        assertThat(driver.state).isEqualTo(TimerState.Idle)
        assertThat(effects).containsExactly(Persist, NO_ALARMS, StopForeground).inOrder()
    }

    @Test
    fun `a paused quick timer is cancelled 30 min after the pause, and every interaction moves the timeout`() {
        val driver = quick(600).apply { send(TimerEvent.Start) }
        driver.clocks.advanceSeconds(60)
        driver.send(TimerEvent.Pause)
        val pausedAt = driver.clocks.wall
        assertThat(BrewTimerEngine.dueTimes(driver.state).quickCancelAtWallMs).isEqualTo(pausedAt + thirtyMinutes)

        driver.clocks.advance(thirtyMinutes)
        driver.send(TimerEvent.QuickTimerTimeout)

        assertThat(driver.state).isEqualTo(TimerState.Idle)
    }

    @Test
    fun `a running or alerting quick timer is never cancelled for inactivity`() {
        val running = quick(3_600).apply { send(TimerEvent.Start) }
        assertThat(BrewTimerEngine.dueTimes(running.state).quickCancelAtWallMs).isNull()
        running.clocks.advance(thirtyMinutes)
        assertThat(running.send(TimerEvent.QuickTimerTimeout)).isEmpty()

        val alerting = quick(60).apply {
            send(TimerEvent.Start)
            clocks.advanceSeconds(60)
            send(TimerEvent.DeadlineReached)
        }
        alerting.clocks.advance(thirtyMinutes)
        assertThat(alerting.send(TimerEvent.QuickTimerTimeout)).isEmpty()
    }

    @Test
    fun `a brew session is never cancelled by the quick-timer timeout`() {
        val driver = sessionWith(infusion(60))
        driver.clocks.advance(2 * thirtyMinutes)

        assertThat(BrewTimerEngine.dueTimes(driver.state).quickCancelAtWallMs).isNull()
        assertThat(driver.send(TimerEvent.QuickTimerTimeout)).isEmpty()
    }

    @Test
    fun `starting a quick timer while a session is active is rejected (GEN-03, TMR-21)`() {
        val driver = sessionWith(infusion(60)).apply { send(TimerEvent.Start) }
        val before = driver.state

        val effects = driver.send(TimerEvent.StartQuickTimer(60, globalPreAlertSeconds = 10))

        assertThat(effects).isEmpty()
        assertThat(driver.state).isSameInstanceAs(before)
        assertThat(driver.outcome).isEqualTo(TransitionOutcome.Rejected(RejectionReason.ActiveTimerExists(BREW)))
    }

    @Test
    fun `starting a session or a second quick timer while a quick timer is active is rejected`() {
        val driver = quick(60)
        val before = driver.state

        assertThat(driver.send(startSession(infusion(60)))).isEmpty()
        assertThat(driver.outcome)
            .isEqualTo(TransitionOutcome.Rejected(RejectionReason.ActiveTimerExists(SessionKind.Quick)))
        assertThat(driver.send(TimerEvent.StartQuickTimer(30, globalPreAlertSeconds = 10))).isEmpty()
        assertThat(driver.outcome)
            .isEqualTo(TransitionOutcome.Rejected(RejectionReason.ActiveTimerExists(SessionKind.Quick)))
        assertThat(driver.state).isSameInstanceAs(before)
    }

    private fun quick(seconds: Int) =
        EngineDriver().apply { send(TimerEvent.StartQuickTimer(seconds, globalPreAlertSeconds = 10)) }
}
