package com.umamit.core.timer.controller

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.TimerPhase
import com.umamit.core.timer.alarm.AndroidTimerAlarms
import com.umamit.core.timer.alarm.TimerAlarmReceiver
import com.umamit.core.timer.alarm.V022Alarms
import com.umamit.core.timer.engine.SessionKind
import com.umamit.core.timer.engine.TimerEvent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/**
 * The update from v0.2.2 with a timer running (04 §6.2, §7.5; D-098, #125): v0.2.2 set the
 * alarms of every timer under the request codes 101–107. The new process restores a brew into
 * the brew slot, which keeps those codes, and a quick timer into the quick slot, whose alarms
 * move to 201–207. Either way the timer alerts on time, also when an old alarm starts the new
 * process. The alarms are the real alarm manager's (Robolectric), delivered at their trigger time.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UpdateFromV022Test {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** v0.2.2's process; its alarms are set below as v0.2.2 set them. */
    private val before = ControllerHarness()

    @Before
    fun setUp() = runTest {
        // USE_EXACT_ALARM is granted at install on a device; Robolectric defaults to false.
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        before.teaWithStock()
    }

    @After
    fun tearDown() = before.close()

    @Test
    fun `a running quick timer moves to the quick slot's alarms and alerts at its deadline`() = runTest {
        before.controller.startQuickTimer(60, run = true)
        val (preAlert, deadline) = leaveV022Alarms()
        val deadlineWall = checkNotNull(before.active.deadlineWallMs)

        val after = updated()
        after.clock.advanceBy(20_000)
        after.controller.recover(RecoveryTrigger.PACKAGE_REPLACED)

        assertThat(alarms()).containsExactly(201, preAlert, 202, deadline, 207, deadline + AlertWatchdog.GUARD_MS)

        after.clock.advanceBy(preAlert - after.clock.elapsed)
        after.deliver(201)
        assertThat(after.alert.ticks).isEqualTo(1)

        after.clock.advanceBy(deadline - after.clock.elapsed)
        after.deliver(202)

        assertThat(after.active.kind).isEqualTo(SessionKind.Quick)
        assertThat(after.active.phase).isEqualTo(TimerPhase.ALERTING)
        assertThat(after.alert.started).containsExactly(StepAlert(SessionKind.Quick, 0, deadlineWall))
        assertThat(alarms().keys).containsExactly(203, 207)
    }

    @Test
    fun `a running quick timer whose old deadline alarm starts the new process alerts on time`() = runTest {
        before.controller.startQuickTimer(60, preAlertSeconds = 0, run = true)
        val (_, deadline) = leaveV022Alarms()
        val deadlineWall = checkNotNull(before.active.deadlineWallMs)

        val after = updated()
        after.clock.advanceBy(deadline - after.clock.elapsed)
        // The alarm v0.2.2 set at 102 reads as the brew slot's deadline.
        after.deliver(102)

        assertThat(after.active.kind).isEqualTo(SessionKind.Quick)
        assertThat(after.active.phase).isEqualTo(TimerPhase.ALERTING)
        assertThat(after.alert.started).containsExactly(StepAlert(SessionKind.Quick, 0, deadlineWall))
        assertThat(after.service.fullScreen).isTrue()
        // The quick timer's alarms are the quick slot's now; none of v0.2.2's is left.
        assertThat(alarms())
            .containsExactly(203, deadline + 600_000, 207, after.clock.elapsed + AlertWatchdog.INTERVAL_MS)
    }

    @Test
    fun `a running brew keeps its alarms and alerts at its deadline`() = runTest {
        before.controller.startSession(brewStart(infusion(60)))
        val id = before.sessionId
        before.controller.send(TimerEvent.Start)
        val (preAlert, deadline) = leaveV022Alarms()
        val deadlineWall = checkNotNull(before.active.deadlineWallMs)

        val after = updated()
        after.clock.advanceBy(20_000)
        after.controller.recover(RecoveryTrigger.PACKAGE_REPLACED)

        assertThat(alarms()).containsExactly(101, preAlert, 102, deadline, 107, deadline + AlertWatchdog.GUARD_MS)

        after.clock.advanceBy(preAlert - after.clock.elapsed)
        after.deliver(101)
        assertThat(after.alert.ticks).isEqualTo(1)

        after.clock.advanceBy(deadline - after.clock.elapsed)
        after.deliver(102)

        assertThat(after.active.phase).isEqualTo(TimerPhase.ALERTING)
        assertThat(after.alert.started).containsExactly(StepAlert(SessionKind.Brew(id), 0, deadlineWall))
        assertThat(alarms().keys).containsExactly(103, 107)
    }

    /**
     * Sets the alarms v0.2.2 left for the timer running in [before]: its pre-alert (when it has one) and
     * its deadline, and the deadline guard. Returns the pre-alert (0 without one) and the deadline.
     */
    private fun leaveV022Alarms(): Pair<Long, Long> {
        val running = before.active
        val deadline = checkNotNull(running.deadlineElapsedMs)
        val preAlert = running.preAlertAtElapsedMs
        preAlert?.let { V022Alarms.set(context, TimerAlarmKind.PRE_ALERT, it) }
        V022Alarms.set(context, TimerAlarmKind.DEADLINE, deadline)
        V022Alarms.set(context, TimerAlarmKind.ALERT_WATCHDOG, deadline + AlertWatchdog.GUARD_MS)
        return (preAlert ?: 0L) to deadline
    }

    /** The process of v0.3.0 after the update: its alarms on the alarm manager. */
    private fun updated(): ControllerHarness = before.processRestarted(
        alarmPort = { AndroidTimerAlarms(context, TimerClocks(clock, clock), diagnostics) },
    )

    /** The alarm manager's alarms, trigger time by request code. */
    private fun alarms(): Map<Int, Long> = shadowOf(alarmManager).scheduledAlarms
        .associate { shadowOf(it.operation).requestCode to it.triggerAtMs }

    /** The alarm with [requestCode] fires now, at its trigger time: its broadcast reaches the controller. */
    private suspend fun ControllerHarness.deliver(requestCode: Int) {
        val alarm = shadowOf(alarmManager).scheduledAlarms.single { shadowOf(it.operation).requestCode == requestCode }
        assertThat(alarm.triggerAtMs).isEqualTo(clock.elapsed)
        val delivered = checkNotNull(TimerAlarmReceiver.alarmOf(shadowOf(alarm.operation).savedIntent))
        // An exact alarm fires once.
        alarmManager.cancel(checkNotNull(alarm.operation))
        controller.onAlarm(delivered.slot, delivered.kind)
    }
}
