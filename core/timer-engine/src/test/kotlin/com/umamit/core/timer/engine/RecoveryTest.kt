package com.umamit.core.timer.engine

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.timer.engine.TimerEffect.Persist
import com.umamit.core.timer.engine.TimerEffect.RecordStep
import com.umamit.core.timer.engine.TimerEffect.ScheduleAlarms
import com.umamit.core.timer.engine.TimerEffect.ShowSavedSheet
import com.umamit.core.timer.engine.TimerEffect.StartAlert
import com.umamit.core.timer.engine.TimerEffect.StartForeground
import com.umamit.core.timer.engine.TimerEffect.StopAlert
import com.umamit.core.timer.engine.TimerEffect.StopForeground
import com.umamit.core.timer.engine.TimerEffect.UpdateNotification
import org.junit.Test

/**
 * The recovery table of 04 §7.2 with fake clocks and boot counts (04 §12
 * "Unit"): the controller sends [TimerEvent.Recover] with the persisted state
 * on every process start, after a reboot as well as on the same boot.
 */
class RecoveryTest {

    private val tenMinutes = 10 * 60_000L

    // --- Same boot: the monotonic values still hold.

    @Test
    fun `a waiting session is restored as it was and the service is tried again`() {
        val waiting = listOf(
            sessionWith(infusion(60), infusion(20)),
            sessionWith(infusion(60), infusion(20)).apply {
                send(TimerEvent.Start)
                clocks.advanceSeconds(10)
                send(TimerEvent.Pause)
            },
            sessionWith(infusion(60)).apply { send(TimerEvent.SkipStep) },
        )
        waiting.forEach { driver ->
            val before = driver.state
            driver.clocks.advanceSeconds(300)

            val effects = driver.send(TimerEvent.Recover)

            val label = before::class.simpleName
            assertWithMessage("$label").that(driver.outcome).isEqualTo(TransitionOutcome.Accepted)
            assertWithMessage("$label").that(driver.state).isEqualTo(before)
            assertWithMessage("$label").that(effects).containsExactly(
                Persist,
                ScheduleAlarms(BrewTimerEngine.dueTimes(before)),
                StartForeground,
                UpdateNotification,
            ).inOrder()
        }
    }

    @Test
    fun `a running step with its deadline ahead is restored and its alarms rescheduled`() {
        val driver = sessionWith(infusion(120)).apply { send(TimerEvent.Start) }
        val before = driver.state as TimerState.Running
        driver.clocks.advanceSeconds(30)

        val effects = driver.send(TimerEvent.Recover)

        assertThat(driver.state).isEqualTo(before)
        assertThat(effects).containsExactly(
            Persist,
            runningAlarms(before.deadlineElapsedMs),
            StartForeground,
            UpdateNotification,
        ).inOrder()
        assertThat(driver.display()).isEqualTo(TimerDisplay(90, 0))
    }

    @Test
    fun `a running step whose deadline passed less than 10 min ago alerts with overtime`() {
        val driver = sessionWith(infusion(60)).apply { send(TimerEvent.Start) }
        val deadline = driver.deadline
        driver.clocks.advance(60_000 + tenMinutes - 1)

        val effects = driver.send(TimerEvent.Recover)

        val alerting = driver.state as TimerState.Alerting
        assertThat(alerting.deadlineElapsedMs).isEqualTo(deadline)
        assertThat(alerting.alertPlayed).isFalse()
        assertThat(effects)
            .containsExactly(
                Persist,
                alertingAlarms(deadline),
                StartForeground,
                StartAlert,
                UpdateNotification,
            ).inOrder()
        assertThat(driver.display()!!.overtimeSeconds).isEqualTo(599)
    }

    @Test
    fun `an alerting step whose alert did not finish plays it again (at least once)`() {
        val driver = alerting(infusion(60))
        driver.clocks.advanceSeconds(120)

        val effects = driver.send(TimerEvent.Recover)

        assertThat(driver.state).isInstanceOf(TimerState.Alerting::class.java)
        assertThat(effects)
            .containsExactly(
                Persist,
                alertingAlarms(START_ELAPSED_MS + 60_000),
                StartForeground,
                StartAlert,
                UpdateNotification,
            ).inOrder()
    }

    @Test
    fun `an alerting step whose alert already played does not play it again`() {
        val driver = alerting(infusion(60)).apply { send(TimerEvent.AlertCompleted) }
        driver.clocks.advanceSeconds(120)

        val effects = driver.send(TimerEvent.Recover)

        assertThat((driver.state as TimerState.Alerting).alertPlayed).isTrue()
        assertThat(
            effects,
        ).containsExactly(
            Persist,
            alertingAlarms(START_ELAPSED_MS + 60_000),
            StartForeground,
            UpdateNotification,
        ).inOrder()
    }

    @Test
    fun `a running step whose deadline passed 10 min ago or more is poured on time, silently`() {
        val driver = sessionWith(infusion(60), infusion(20)).apply { send(TimerEvent.Start) }
        driver.clocks.advanceSeconds(20)
        driver.send(TimerEvent.Pause)
        driver.clocks.advanceSeconds(5)
        driver.send(TimerEvent.Resume)
        val running = driver.state as TimerState.Running
        driver.advanceTo(running.deadlineElapsedMs + tenMinutes)

        val effects = driver.send(TimerEvent.Recover)

        val ready = driver.state as TimerState.Ready
        val record = driver.recordOf(0)
        assertThat(ready.stepIndex).isEqualTo(1)
        assertThat(record).isEqualTo(
            StepRecord(
                plannedSeconds = 60,
                actualSeconds = 60,
                pausedSeconds = 5,
                overtimeSeconds = 0,
                startedAtWallMs = START_WALL_MS,
                endedAtWallMs = running.deadlineWallMs,
                skipped = false,
                overtimeAutoAck = true,
            ),
        )
        assertThat(effects).containsExactly(
            RecordStep(SESSION_ID, 0, record),
            Persist,
            waitingAlarms(driver.clocks.wall),
            StartForeground,
            UpdateNotification,
        ).inOrder()
    }

    @Test
    fun `an alerting last step left for more than 10 min is poured on time and the plan finishes, silently`() {
        val driver = alerting(infusion(60))
        driver.clocks.advance(tenMinutes + 1)

        val effects = driver.send(TimerEvent.Recover)

        assertThat(driver.state).isInstanceOf(TimerState.Finished::class.java)
        assertThat(driver.recordOf(0).overtimeAutoAck).isTrue()
        assertThat(effects).containsExactly(
            RecordStep(SESSION_ID, 0, driver.recordOf(0)),
            Persist,
            waitingAlarms(driver.clocks.wall),
            StopAlert,
            StartForeground,
            UpdateNotification,
        ).inOrder()
        assertThat(effects.none { it == StartAlert || it is ShowSavedSheet }).isTrue()
    }

    @Test
    fun `a quick timer left alerting for more than 10 min ends silently`() {
        val driver = EngineDriver().apply {
            send(TimerEvent.StartQuickTimer(60, globalPreAlertSeconds = 10))
            send(TimerEvent.Start)
            clocks.advanceSeconds(60)
            send(TimerEvent.DeadlineReached)
        }
        driver.clocks.advance(tenMinutes)

        val effects = driver.send(TimerEvent.Recover)

        assertThat(driver.state).isEqualTo(TimerState.Idle)
        assertThat(effects).containsExactly(Persist, NO_ALARMS, StopAlert, StopForeground).inOrder()
    }

    @Test
    fun `recovery is not an interaction`() {
        val driver = sessionWith(infusion(60))
        driver.clocks.advanceSeconds(600)

        driver.send(TimerEvent.Recover)

        assertThat((driver.state as TimerState.Ready).session.lastInteractionWallMs).isEqualTo(START_WALL_MS)
    }

    @Test
    fun `there is nothing to recover in Idle`() {
        val driver = EngineDriver()

        assertThat(driver.send(TimerEvent.Recover)).isEmpty()
        assertThat(driver.outcome).isEqualTo(TransitionOutcome.Ignored)
    }

    // --- After a reboot: the elapsed clock restarted, the wall values hold.

    @Test
    fun `after a reboot a running step keeps its wall-clock deadline on the new elapsed clock`() {
        val driver = sessionWith(infusion(3_600)).apply { send(TimerEvent.Start) }
        val before = driver.state as TimerState.Running
        driver.clocks.reboot(offMs = 60_000, upMs = 20_000) // 80 s after the start

        val effects = driver.send(TimerEvent.Recover)

        val running = driver.state as TimerState.Running
        assertThat(running.session.bootCount).isEqualTo(START_BOOT_COUNT + 1)
        assertThat(running.deadlineWallMs).isEqualTo(before.deadlineWallMs)
        assertThat(running.deadlineElapsedMs).isEqualTo(20_000 + 3_600_000 - 80_000)
        assertThat(driver.display()!!.remainingSeconds).isEqualTo(3_600 - 80)
        assertThat(effects).contains(deadlineAlarm(running.deadlineElapsedMs))
    }

    @Test
    fun `after a reboot a pre-alert keeps its lead time before the rebased deadline`() {
        val driver = sessionWith(infusion(300)).apply { send(TimerEvent.Start) }
        driver.clocks.reboot(offMs = 60_000, upMs = 20_000)

        driver.send(TimerEvent.Recover)

        val running = driver.state as TimerState.Running
        assertThat(running.deadlineElapsedMs).isEqualTo(20_000 + 300_000 - 80_000)
        assertThat(running.preAlertAtElapsedMs).isEqualTo(running.deadlineElapsedMs - 10_000)
    }

    @Test
    fun `after a reboot past the deadline the step alerts with the overtime measured on the wall clock`() {
        val driver = sessionWith(infusion(60)).apply { send(TimerEvent.Start) }
        driver.clocks.reboot(offMs = 100_000, upMs = 20_000) // deadline passed 60 s ago

        driver.send(TimerEvent.Recover)

        val alerting = driver.state as TimerState.Alerting
        assertThat(alerting.deadlineElapsedMs).isEqualTo(20_000 - 60_000)
        assertThat(driver.display()!!.overtimeSeconds).isEqualTo(60)
        assertThat(driver.effects).contains(StartAlert)
    }

    @Test
    fun `after a long reboot an alerting step is poured on time, silently`() {
        val driver = alerting(infusion(60), infusion(20))
        driver.clocks.reboot(offMs = 2 * tenMinutes, upMs = 30_000)

        driver.send(TimerEvent.Recover)

        assertThat(driver.state).isInstanceOf(TimerState.Ready::class.java)
        assertThat(driver.recordOf(0).overtimeAutoAck).isTrue()
        assertThat(driver.effects).doesNotContain(StartAlert)
    }

    @Test
    fun `after a reboot a paused step counts the time off as paused`() {
        val driver = sessionWith(infusion(60)).apply {
            send(TimerEvent.Start)
            clocks.advanceSeconds(20)
            send(TimerEvent.Pause)
        }
        driver.clocks.reboot(offMs = 100_000, upMs = 20_000)

        driver.send(TimerEvent.Recover)
        driver.send(TimerEvent.Resume)

        val running = driver.state as TimerState.Running
        assertThat(running.pausedTotalMs).isEqualTo(120_000)
        assertThat(running.deadlineElapsedMs).isEqualTo(20_000 + 40_000)
    }

    @Test
    fun `after a reboot a waiting session only moves to the new boot`() {
        val driver = sessionWith(infusion(60))
        val before = driver.state as TimerState.Ready
        driver.clocks.reboot(offMs = 100_000, upMs = 20_000)

        driver.send(TimerEvent.Recover)

        assertThat(driver.state).isEqualTo(before.copy(session = before.session.copy(bootCount = START_BOOT_COUNT + 1)))
    }

    @Test
    fun `an event from another boot is rejected until the state was recovered`() {
        val driver = sessionWith(infusion(60)).apply { send(TimerEvent.Start) }
        val before = driver.state
        driver.clocks.reboot(offMs = 10_000, upMs = 10_000)

        listOf(TimerEvent.DeadlineReached, TimerEvent.Pause, TimerEvent.EndStepNow, TimerEvent.AddStep)
            .forEach { event ->
                assertWithMessage("$event").that(driver.send(event)).isEmpty()
                assertWithMessage("$event").that(driver.state).isSameInstanceAs(before)
                assertWithMessage("$event").that(driver.outcome)
                    .isEqualTo(TransitionOutcome.Rejected(RejectionReason.BootChanged))
            }

        driver.send(TimerEvent.Recover)
        driver.send(TimerEvent.Pause)
        assertThat(driver.state).isInstanceOf(TimerState.Paused::class.java)
    }

    // --- Wall-clock error across a reboot: the rebase never produces an impossible state (D-048).

    @Test
    fun `after a reboot with the wall clock set back an alerting deadline stays in the past`() {
        val driver = alerting(infusion(60))
        driver.clocks.reboot(offMs = 0, upMs = 20_000)
        driver.clocks.wall -= 3_600_000

        val effects = driver.send(TimerEvent.Recover)

        val alerting = driver.state as TimerState.Alerting
        assertThat(alerting.deadlineElapsedMs).isEqualTo(0)
        assertThat(effects).contains(StartAlert)
        driver.send(TimerEvent.Poured)
        val record = driver.recordOf(0)
        assertThat(record.overtimeSeconds).isEqualTo(20)
        assertThat(record.actualSeconds).isEqualTo(80)
    }

    @Test
    fun `after a reboot with the wall clock set back a running step never has more than its planned time left`() {
        val driver = sessionWith(infusion(600)).apply { send(TimerEvent.Start) }
        driver.clocks.reboot(offMs = 0, upMs = 30_000)
        driver.clocks.wall -= 3_600_000

        driver.send(TimerEvent.Recover)

        val running = driver.state as TimerState.Running
        assertThat(running.deadlineElapsedMs).isEqualTo(600_000)
        assertThat(driver.display()!!.remainingSeconds).isEqualTo(570)
    }

    @Test
    fun `after a reboot with the wall clock set back a pause started before the boot`() {
        val driver = sessionWith(infusion(60)).apply {
            send(TimerEvent.Start)
            clocks.advanceSeconds(20)
            send(TimerEvent.Pause)
        }
        driver.clocks.reboot(offMs = 0, upMs = 20_000)
        driver.clocks.wall -= 3_600_000

        driver.send(TimerEvent.Recover)
        driver.send(TimerEvent.Resume)

        val running = driver.state as TimerState.Running
        assertThat(running.pausedTotalMs).isEqualTo(20_000)
        assertThat(running.deadlineElapsedMs).isEqualTo(20_000 + 40_000)
    }

    @Test
    fun `a running step that is impossible on the same boot count is rebased from the wall clock`() {
        val driver = sessionWith(infusion(60)).apply { send(TimerEvent.Start) }
        driver.clocks.rebootUnnoticed(offMs = 100_000, upMs = 20_000) // deadline passed 60 s ago

        val effects = driver.send(TimerEvent.Recover)

        val alerting = driver.state as TimerState.Alerting
        assertThat(alerting.deadlineElapsedMs).isEqualTo(20_000 - 60_000)
        assertThat(driver.display()!!.overtimeSeconds).isEqualTo(60)
        assertThat(effects).contains(StartAlert)
    }

    @Test
    fun `alerting and paused states that are impossible on the same boot count are rebased`() {
        val alerting = alerting(infusion(60)).apply {
            clocks.rebootUnnoticed(offMs = 60_000, upMs = 20_000)
            send(TimerEvent.Recover)
        }
        assertThat((alerting.state as TimerState.Alerting).deadlineElapsedMs).isEqualTo(20_000 - 80_000)

        val paused = sessionWith(infusion(60)).apply {
            send(TimerEvent.Start)
            clocks.advanceSeconds(20)
            send(TimerEvent.Pause)
            clocks.rebootUnnoticed(offMs = 100_000, upMs = 20_000)
            send(TimerEvent.Recover)
            send(TimerEvent.Resume)
        }
        assertThat((paused.state as TimerState.Running).pausedTotalMs).isEqualTo(120_000)
    }

    @Test
    fun `a possible state on the same boot is kept as it is`() {
        val driver = sessionWith(infusion(60)).apply {
            send(TimerEvent.Start)
            clocks.advanceSeconds(20)
            send(TimerEvent.Pause)
        }
        val before = driver.state
        driver.clocks.wall -= 3_600_000 // a wall-clock change on the same boot changes nothing

        driver.send(TimerEvent.Recover)

        assertThat(driver.state).isEqualTo(before)
    }

    // --- Boundaries and orders.

    @Test
    fun `recovery 1 ms before the overtime timeout still alerts`() {
        val driver = alerting(infusion(60))
        driver.clocks.advance(tenMinutes - 1)

        driver.send(TimerEvent.Recover)

        assertThat(driver.state).isInstanceOf(TimerState.Alerting::class.java)
        assertThat(driver.effects).contains(StartAlert)
    }

    @Test
    fun `the deadline before recover and a second recover each ask for the alert, which StartAlert ensures once`() {
        // The controller sends recover exactly once per process, first (04 §4). If it does not,
        // the engine asks for the alert again; StartAlert is idempotent for the alerting step.
        val driver = sessionWith(infusion(60)).apply { send(TimerEvent.Start) }
        driver.clocks.advanceSeconds(61)

        assertThat(driver.send(TimerEvent.DeadlineReached)).contains(StartAlert)
        assertThat(driver.send(TimerEvent.Recover))
            .containsExactly(
                Persist,
                alertingAlarms(START_ELAPSED_MS + 60_000),
                StartForeground,
                StartAlert,
                UpdateNotification,
            ).inOrder()
        assertThat(driver.send(TimerEvent.Recover))
            .containsExactly(
                Persist,
                alertingAlarms(START_ELAPSED_MS + 60_000),
                StartForeground,
                StartAlert,
                UpdateNotification,
            ).inOrder()
        driver.send(TimerEvent.AlertCompleted)
        assertThat(driver.send(TimerEvent.Recover)).doesNotContain(StartAlert)
    }

    @Test
    fun `a quick timer whose inactivity cancel passed while the process was dead ends silently on recovery`() {
        val driver = EngineDriver().apply { send(TimerEvent.StartQuickTimer(60, globalPreAlertSeconds = 10)) }
        driver.clocks.advance(30 * 60_000L)

        val effects = driver.send(TimerEvent.Recover)

        assertThat(driver.state).isEqualTo(TimerState.Idle)
        assertThat(effects).containsExactly(Persist, NO_ALARMS, StopForeground).inOrder()
    }

    @Test
    fun `a start before recovery is refused as a second timer, so the UI can still ask (TMR-21)`() {
        val driver = sessionWith(infusion(60))
        driver.clocks.reboot(offMs = 10_000, upMs = 10_000)

        driver.send(startSession(infusion(30)))

        assertThat(driver.outcome).isEqualTo(TransitionOutcome.Rejected(RejectionReason.ActiveTimerExists(BREW)))
    }

    @Test
    fun `a session records the boot it started on`() {
        val driver = sessionWith(infusion(60))

        assertThat((driver.state as TimerState.Ready).session.bootCount).isEqualTo(START_BOOT_COUNT)
    }

    private fun alerting(vararg steps: PlannedStep): EngineDriver = sessionWith(*steps).apply {
        send(TimerEvent.Start)
        clocks.advanceSeconds(steps.first().plannedSeconds.toLong())
        send(TimerEvent.DeadlineReached)
    }
}
