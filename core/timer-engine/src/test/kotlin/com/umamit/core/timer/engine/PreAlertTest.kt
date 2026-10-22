package com.umamit.core.timer.engine

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.timer.engine.TimerEffect.Persist
import com.umamit.core.timer.engine.TimerEffect.PlayPreAlert
import com.umamit.core.timer.engine.TimerEffect.ScheduleAlarms
import com.umamit.core.timer.engine.TimerEffect.StartForeground
import com.umamit.core.timer.engine.TimerEffect.UpdateNotification
import org.junit.Test

/**
 * The pre-alert tick (TMR-23, 04 §5, E6, E7, D-048 consume rule): when it is
 * due, when a step has none, and what `preAlertReached` does on time and late.
 */
class PreAlertTest {

    /** The pre-alert time of the step started in a fresh session with [settings]; null for none. */
    private fun preAlertAfterStart(
        step: PlannedStep,
        settings: PreAlertSettings,
        longStepsOnly: Boolean = false,
    ): Long? {
        val driver = EngineDriver()
        driver.send(TimerEvent.StartSession(SESSION_ID, listOf(step), settings, longStepsOnly))
        driver.send(TimerEvent.Start)
        return BrewTimerEngine.dueTimes(driver.state).preAlertAtElapsedMs
    }

    @Test
    fun `N is the session override, else the recipe value, else the global setting`() {
        // (settings, expected lead in seconds or null for none); a 60 s infusion starts at START_ELAPSED_MS.
        val cases = listOf(
            PreAlertSettings.inherited(recipeSeconds = null, globalSeconds = 10) to 10,
            PreAlertSettings.inherited(recipeSeconds = null, globalSeconds = 25) to 25,
            PreAlertSettings.inherited(recipeSeconds = 20, globalSeconds = 10) to 20,
            PreAlertSettings.inherited(recipeSeconds = 0, globalSeconds = 10) to null,
            PreAlertSettings.inherited(recipeSeconds = null, globalSeconds = 0) to null,
            PreAlertSettings(sessionOverrideSeconds = 15, inheritedSeconds = 20) to 15,
            PreAlertSettings(sessionOverrideSeconds = 5, inheritedSeconds = 0) to 5,
            PreAlertSettings(sessionOverrideSeconds = 0, inheritedSeconds = 20) to null,
        )
        cases.forEach { (settings, lead) ->
            assertWithMessage("$settings").that(preAlertAfterStart(infusion(60), settings))
                .isEqualTo(lead?.let { START_ELAPSED_MS + 60_000 - it * 1000L })
        }
    }

    @Test
    fun `a step has no pre-alert when it is too short, a short rinse or a long step (E7)`() {
        val ten = PreAlertSettings.inherited(recipeSeconds = null, globalSeconds = 10)
        val thirty = PreAlertSettings.inherited(recipeSeconds = 30, globalSeconds = 10)
        val five = PreAlertSettings.inherited(recipeSeconds = 5, globalSeconds = 10)
        // (step, settings, has a pre-alert)
        val cases = listOf(
            Triple(infusion(15), ten, true),
            Triple(infusion(14), ten, false),
            Triple(whisk(15), ten, true),
            Triple(whisk(14), ten, false),
            Triple(infusion(35), thirty, true),
            Triple(infusion(34), thirty, false),
            Triple(rinse(15), five, true),
            Triple(rinse(14), five, false),
            Triple(rinse(10), five, false),
            Triple(infusion(599), ten, true),
            Triple(infusion(600), ten, false),
            Triple(infusion(4 * 3600), ten, false),
        )
        cases.forEach { (step, settings, expected) ->
            assertWithMessage("$step with N = ${settings.effectiveSeconds}")
                .that(preAlertAfterStart(step, settings) != null).isEqualTo(expected)
        }
    }

    @Test
    fun `a cold or ice brew session has no pre-alert, however short its steps`() {
        assertThat(preAlertAfterStart(infusion(60), PRE_ALERT, longStepsOnly = true)).isNull()
    }

    @Test
    fun `start schedules the pre-alert with the deadline`() {
        val driver = sessionWith(infusion(60))

        val effects = driver.send(TimerEvent.Start)

        val running = driver.state as TimerState.Running
        assertThat(running.preAlertAtElapsedMs).isEqualTo(START_ELAPSED_MS + 50_000)
        assertThat(running.preAlertPlayed).isFalse()
        val due =
            DueTimes(preAlertAtElapsedMs = START_ELAPSED_MS + 50_000, deadlineElapsedMs = running.deadlineElapsedMs)
        assertThat(effects).contains(ScheduleAlarms(due))
    }

    @Test
    fun `preAlertReached plays the tick once when it is due`() {
        val driver = sessionWith(infusion(60)).apply { send(TimerEvent.Start) }
        driver.clocks.advance(49_999)

        assertThat(driver.send(TimerEvent.PreAlertReached)).isEmpty()
        assertThat(driver.outcome).isEqualTo(TransitionOutcome.Ignored)

        driver.clocks.advance(1)
        val effects = driver.send(TimerEvent.PreAlertReached)

        val running = driver.state as TimerState.Running
        assertThat(driver.outcome).isEqualTo(TransitionOutcome.Accepted)
        assertThat(running.preAlertPlayed).isTrue()
        // The tick plays in the service (04 §9), so it is ensured first, as before the alert.
        assertThat(effects).containsExactly(
            Persist,
            deadlineAlarm(running.deadlineElapsedMs),
            StartForeground,
            PlayPreAlert,
            UpdateNotification,
        ).inOrder()

        assertThat(driver.send(TimerEvent.PreAlertReached)).isEmpty()
        assertThat(driver.outcome).isEqualTo(TransitionOutcome.Ignored)
    }

    @Test
    fun `a late pre-alert plays while at least 1 s is left, else it is consumed without a tick`() {
        // (time after the pre-alert time in ms, plays); the deadline is 10 s after the pre-alert.
        val cases = listOf(
            0L to true,
            8_999L to true,
            9_000L to true,
            9_001L to false,
            10_000L to false,
            30_000L to false,
        )
        cases.forEach { (lateMs, plays) ->
            val driver = sessionWith(infusion(60)).apply { send(TimerEvent.Start) }
            driver.clocks.advance(50_000 + lateMs)

            val effects = driver.send(TimerEvent.PreAlertReached)

            val label = "$lateMs ms late"
            assertWithMessage(label).that(driver.outcome).isEqualTo(TransitionOutcome.Accepted)
            assertWithMessage(label).that(PlayPreAlert in effects).isEqualTo(plays)
            assertWithMessage(label).that(StartForeground in effects).isEqualTo(plays)
            assertWithMessage(label).that((driver.state as TimerState.Running).preAlertPlayed).isTrue()
            assertWithMessage(label).that(BrewTimerEngine.dueTimes(driver.state).preAlertAtElapsedMs).isNull()
        }
    }

    @Test
    fun `after a pause the played pre-alert is not repeated (E6)`() {
        val driver = sessionWith(infusion(60)).apply { send(TimerEvent.Start) }
        driver.clocks.advanceSeconds(50)
        driver.send(TimerEvent.PreAlertReached)
        driver.clocks.advanceSeconds(3)
        driver.send(TimerEvent.Pause)
        driver.clocks.advanceSeconds(30)

        driver.send(TimerEvent.Resume)

        val running = driver.state as TimerState.Running
        assertThat(running.preAlertPlayed).isTrue()
        assertThat(BrewTimerEngine.dueTimes(running).preAlertAtElapsedMs).isNull()
    }

    @Test
    fun `after a pause an unplayed pre-alert is rescheduled from the new deadline (E6)`() {
        val driver = sessionWith(infusion(60)).apply { send(TimerEvent.Start) }
        driver.clocks.advanceSeconds(20)
        driver.send(TimerEvent.Pause)
        driver.clocks.advanceSeconds(100)

        driver.send(TimerEvent.Resume)

        val running = driver.state as TimerState.Running
        assertThat(running.deadlineElapsedMs).isEqualTo(START_ELAPSED_MS + 160_000)
        assertThat(running.preAlertAtElapsedMs).isEqualTo(START_ELAPSED_MS + 150_000)
        assertThat(running.preAlertPlayed).isFalse()
    }

    @Test
    fun `after a pause inside the window an unplayed pre-alert is skipped (E6)`() {
        // (remaining at the pause in s, pre-alert after resume): exactly N left is already past.
        val cases = listOf(11L to true, 10L to false, 8L to false)
        cases.forEach { (remaining, rescheduled) ->
            val driver = sessionWith(infusion(60)).apply { send(TimerEvent.Start) }
            driver.clocks.advanceSeconds(60 - remaining)
            driver.send(TimerEvent.Pause)
            driver.clocks.advanceSeconds(5)

            driver.send(TimerEvent.Resume)

            val running = driver.state as TimerState.Running
            assertWithMessage("$remaining s left").that(running.preAlertAtElapsedMs != null).isEqualTo(rescheduled)
            assertWithMessage("$remaining s left").that(running.preAlertPlayed).isFalse()
        }
    }

    @Test
    fun `editSteps moves the unplayed pre-alert of the running step to the new N`() {
        // (choice, expected lead in seconds or null); the inherited value is 10 s, the step 60 s, edited after 5 s.
        val cases = listOf(
            PreAlertChoice.Seconds(20) to 20,
            PreAlertChoice.Off to null,
            PreAlertChoice.Inherit to 10,
            PreAlertChoice.Seconds(60) to null,
        )
        cases.forEach { (choice, lead) ->
            val driver = sessionWith(infusion(60)).apply {
                send(TimerEvent.EditSteps(listOf(infusion(60)), PreAlertChoice.Seconds(30)))
                send(TimerEvent.Start)
            }
            val deadline = driver.deadline
            driver.clocks.advanceSeconds(5)

            val effects = driver.send(TimerEvent.EditSteps(listOf(infusion(60)), choice))

            val expected = lead?.let { deadline - it * 1000L }
            assertWithMessage("$choice").that((driver.state as TimerState.Running).preAlertAtElapsedMs)
                .isEqualTo(expected)
            assertWithMessage("$choice").that(effects)
                .contains(ScheduleAlarms(DueTimes(preAlertAtElapsedMs = expected, deadlineElapsedMs = deadline)))
        }
    }

    @Test
    fun `editSteps never brings back a played pre-alert`() {
        val driver = sessionWith(infusion(60)).apply { send(TimerEvent.Start) }
        driver.clocks.advanceSeconds(50)
        driver.send(TimerEvent.PreAlertReached)
        driver.clocks.advanceSeconds(1)

        driver.send(TimerEvent.EditSteps(listOf(infusion(60)), PreAlertChoice.Seconds(5)))

        assertThat((driver.state as TimerState.Running).preAlertAtElapsedMs).isNull()
        assertThat(BrewTimerEngine.dueTimes(driver.state).preAlertAtElapsedMs).isNull()
    }

    /**
     * B1 of the #27 slice review: the pre-alert came due a few ms before the
     * edit, but its `preAlertReached` is still queued behind it. The edit must
     * not consume it; only *Aus* (or a step without a pre-alert) removes it.
     */
    @Test
    fun `an edit right after the pre-alert came due keeps it due, so the queued tick still plays`() {
        // (edit, the queued tick plays)
        val cases = listOf(
            TimerEvent.AddStep to true,
            TimerEvent.EditSteps(listOf(infusion(60)), PreAlertChoice.Inherit) to true,
            TimerEvent.EditSteps(listOf(infusion(60), infusion(30)), PreAlertChoice.Seconds(10)) to true,
            TimerEvent.EditSteps(listOf(infusion(60)), PreAlertChoice.Seconds(20)) to true,
            TimerEvent.EditSteps(listOf(infusion(60)), PreAlertChoice.Off) to false,
            // N = 60 leaves the 60 s step without a pre-alert (E7).
            TimerEvent.EditSteps(listOf(infusion(60)), PreAlertChoice.Seconds(60)) to false,
        )
        cases.forEach { (edit, plays) ->
            val driver = sessionWith(infusion(60)).apply { send(TimerEvent.Start) }
            val preAlertAt = driver.deadline - 10_000
            driver.advanceTo(preAlertAt + 5)

            driver.send(edit)

            val label = "$edit"
            assertWithMessage("$label: accepted").that(driver.outcome).isEqualTo(TransitionOutcome.Accepted)
            assertWithMessage("$label: still due").that(BrewTimerEngine.dueTimes(driver.state).preAlertAtElapsedMs)
                .isEqualTo(preAlertAt.takeIf { plays })
            driver.clocks.advance(5)
            val effects = driver.send(TimerEvent.PreAlertReached)
            assertWithMessage("$label: the queued tick").that(PlayPreAlert in effects).isEqualTo(plays)
        }
    }

    @Test
    fun `preAlertLeadSeconds is the current step's lead time, played or not, for the ring and the progress point`() {
        val driver = sessionWith(infusion(60), infusion(14), infusion(600))
        assertThat(BrewTimerEngine.preAlertLeadSeconds(TimerState.Idle)).isNull()
        assertThat(BrewTimerEngine.preAlertLeadSeconds(driver.state)).isEqualTo(10)

        driver.send(TimerEvent.Start)
        driver.clocks.advanceSeconds(50)
        driver.send(TimerEvent.PreAlertReached)
        assertThat(BrewTimerEngine.preAlertLeadSeconds(driver.state)).isEqualTo(10)

        driver.send(TimerEvent.EditSteps(listOf(infusion(60), infusion(14), infusion(600)), PreAlertChoice.Seconds(20)))
        assertThat(BrewTimerEngine.preAlertLeadSeconds(driver.state)).isEqualTo(20)

        // A step shorter than N + 5 s and a long step have none (E7, TMR-18); Finished has no step.
        driver.send(TimerEvent.EndStepNow)
        assertThat(BrewTimerEngine.preAlertLeadSeconds(driver.state)).isNull()
        driver.send(TimerEvent.SkipStep)
        assertThat(BrewTimerEngine.preAlertLeadSeconds(driver.state)).isNull()
        driver.send(TimerEvent.SkipStep)
        assertThat(driver.state).isInstanceOf(TimerState.Finished::class.java)
        assertThat(BrewTimerEngine.preAlertLeadSeconds(driver.state)).isNull()
    }

    @Test
    fun `the quick timer uses the global pre-alert`() {
        val driver = EngineDriver().apply {
            send(TimerEvent.StartQuickTimer(durationSeconds = 120, globalPreAlertSeconds = 15))
            send(TimerEvent.Start)
        }

        assertThat(BrewTimerEngine.dueTimes(driver.state).preAlertAtElapsedMs).isEqualTo(START_ELAPSED_MS + 105_000)
    }
}
