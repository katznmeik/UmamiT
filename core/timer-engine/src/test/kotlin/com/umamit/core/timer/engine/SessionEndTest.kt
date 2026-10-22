package com.umamit.core.timer.engine

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.timer.engine.TimerEffect.CancelAlarms
import com.umamit.core.timer.engine.TimerEffect.CompleteSession
import com.umamit.core.timer.engine.TimerEffect.DiscardSession
import com.umamit.core.timer.engine.TimerEffect.Persist
import com.umamit.core.timer.engine.TimerEffect.RecordStep
import com.umamit.core.timer.engine.TimerEffect.ShowSavedSheet
import com.umamit.core.timer.engine.TimerEffect.StopAlert
import com.umamit.core.timer.engine.TimerEffect.StopForeground
import org.junit.Test

/** *Sitzung beenden* (TMR-12) and discard (TMR-13), 04 §3.2. */
class SessionEndTest {

    /** Rinse skipped and the first infusion poured: Ready for the second infusion. */
    private fun afterFirstInfusion(): EngineDriver = sessionWith(rinse(10), infusion(60), infusion(20)).apply {
        send(TimerEvent.SkipStep)
        send(TimerEvent.Start)
        clocks.advanceSeconds(60)
        send(TimerEvent.DeadlineReached)
        send(TimerEvent.Poured)
    }

    @Test
    fun `finishing a session with a completed infusion completes it and shows the saved sheet`() {
        val driver = afterFirstInfusion()

        val effects = driver.send(TimerEvent.FinishSession)

        assertThat(driver.state).isEqualTo(TimerState.Idle)
        assertThat(effects).containsExactly(
            CompleteSession(SESSION_ID),
            Persist,
            CancelAlarms,
            StopForeground,
            ShowSavedSheet(SESSION_ID),
        ).inOrder()
    }

    @Test
    fun `finishing from Finished completes the session`() {
        val driver = afterFirstInfusion().apply { send(TimerEvent.SkipStep) }
        assertThat(driver.state).isInstanceOf(TimerState.Finished::class.java)

        val effects = driver.send(TimerEvent.FinishSession)

        assertThat(driver.state).isEqualTo(TimerState.Idle)
        assertThat(effects).contains(CompleteSession(SESSION_ID))
    }

    @Test
    fun `finishing during a running step ends it early first and records it`() {
        val driver = sessionWith(infusion(60), infusion(20))
        driver.send(TimerEvent.Start)
        driver.clocks.advanceSeconds(42)

        val effects = driver.send(TimerEvent.FinishSession)

        assertThat(driver.state).isEqualTo(TimerState.Idle)
        val expectedRecord = StepRecord(
            plannedSeconds = 60,
            actualSeconds = 42,
            pausedSeconds = 0,
            overtimeSeconds = 0,
            startedAtWallMs = START_WALL_MS,
            endedAtWallMs = START_WALL_MS + 42_000,
            skipped = false,
        )
        assertThat(effects).containsExactly(
            RecordStep(SESSION_ID, 0, expectedRecord),
            CompleteSession(SESSION_ID),
            Persist,
            CancelAlarms,
            StopForeground,
            ShowSavedSheet(SESSION_ID),
        ).inOrder()
    }

    @Test
    fun `finishing during a paused step ends it early first and records it`() {
        val driver = sessionWith(infusion(60))
        driver.send(TimerEvent.Start)
        driver.clocks.advanceSeconds(20)
        driver.send(TimerEvent.Pause)
        driver.clocks.advanceSeconds(30)

        val effects = driver.send(TimerEvent.FinishSession)

        val record = (effects.first() as RecordStep).record
        assertThat(record.actualSeconds).isEqualTo(20)
        assertThat(record.pausedSeconds).isEqualTo(30)
        assertThat(effects).contains(CompleteSession(SESSION_ID))
    }

    @Test
    fun `finishing during the alert counts the step as poured and stops the alert`() {
        val driver = sessionWith(infusion(60))
        driver.send(TimerEvent.Start)
        driver.clocks.advanceSeconds(60)
        driver.send(TimerEvent.DeadlineReached)
        driver.clocks.advanceSeconds(7)

        val effects = driver.send(TimerEvent.FinishSession)

        val record = (effects.first() as RecordStep).record
        assertThat(record.actualSeconds).isEqualTo(67)
        assertThat(record.overtimeSeconds).isEqualTo(7)
        assertThat(effects).containsExactly(
            RecordStep(SESSION_ID, 0, record),
            CompleteSession(SESSION_ID),
            Persist,
            CancelAlarms,
            StopAlert,
            StopForeground,
            ShowSavedSheet(SESSION_ID),
        ).inOrder()
    }

    @Test
    fun `finishing without a completed non-rinse step discards the session`() {
        val cases = mapOf(
            "nothing brewed" to sessionWith(infusion(60)),
            "only a rinse" to sessionWith(rinse(10), infusion(60)).apply {
                send(TimerEvent.Start)
                clocks.advanceSeconds(10)
                send(TimerEvent.DeadlineReached)
                send(TimerEvent.Poured)
            },
            "infusion skipped" to sessionWith(infusion(60), infusion(20)).apply { send(TimerEvent.SkipStep) },
            "rinse running" to sessionWith(rinse(10)).apply { send(TimerEvent.Start) },
        )
        cases.forEach { (case, driver) ->
            val effects = driver.send(TimerEvent.FinishSession)

            assertWithMessage(case).that(driver.state).isEqualTo(TimerState.Idle)
            assertWithMessage(case).that(effects)
                .containsExactly(DiscardSession(SESSION_ID), Persist, CancelAlarms, StopForeground).inOrder()
        }
    }

    @Test
    fun `discard deletes the session from every active state`() {
        val cases = mapOf(
            "Ready" to sessionWith(infusion(60)),
            "Running" to sessionWith(infusion(60)).apply { send(TimerEvent.Start) },
            "Paused" to sessionWith(infusion(60)).apply {
                send(TimerEvent.Start)
                send(TimerEvent.Pause)
            },
            "Finished" to afterFirstInfusion().apply { send(TimerEvent.SkipStep) },
        )
        cases.forEach { (case, driver) ->
            val effects = driver.send(TimerEvent.Discard)

            assertWithMessage(case).that(driver.state).isEqualTo(TimerState.Idle)
            assertWithMessage(case).that(effects)
                .containsExactly(DiscardSession(SESSION_ID), Persist, CancelAlarms, StopForeground).inOrder()
        }
    }

    @Test
    fun `discard during the alert also stops the alert`() {
        val driver = sessionWith(infusion(60))
        driver.send(TimerEvent.Start)
        driver.clocks.advanceSeconds(60)
        driver.send(TimerEvent.DeadlineReached)

        val effects = driver.send(TimerEvent.Discard)

        assertThat(driver.state).isEqualTo(TimerState.Idle)
        assertThat(effects)
            .containsExactly(DiscardSession(SESSION_ID), Persist, CancelAlarms, StopAlert, StopForeground).inOrder()
    }
}
