package com.umamit.core.timer.alarm

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeTimerDiagnostics
import com.umamit.core.timer.controller.TimerAlarmKind
import com.umamit.core.timer.controller.TimerClocks
import com.umamit.core.timer.controller.TimerDiagnostic
import com.umamit.core.timer.engine.DueTimes
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/**
 * The alarm adapter of the controller's alarm port (04 §6.2, §12 Robolectric; spec #40 seam 3):
 * every due-time field is scheduled as its kind with its clock, exactness, trigger time and
 * request code, replaced when it moves and cancelled when it clears.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidTimerAlarmsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val clock = FakeClock()
    private val diagnostics = FakeTimerDiagnostics()
    private val alarms = AndroidTimerAlarms(context, TimerClocks(clock, clock), diagnostics)

    @Before
    fun setUp() {
        // USE_EXACT_ALARM is granted at install on a device; Robolectric defaults to false.
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @Test
    fun thePreAlertIsAnExactElapsedWakeUpAlarmWithRequestCode101() {
        alarms.reconcile(DueTimes(preAlertAtElapsedMs = 50_000L))

        val alarm = scheduled().single()
        assertThat(alarm.type).isEqualTo(AlarmManager.ELAPSED_REALTIME_WAKEUP)
        assertThat(alarm.triggerAtMs).isEqualTo(50_000L)
        assertThat(alarm.isAllowWhileIdle).isTrue()
        assertThat(alarm.windowLengthMs).isEqualTo(ShadowAlarmManager.WINDOW_EXACT)
        assertThat(shadowOf(alarm.operation).requestCode).isEqualTo(101)
    }

    @Test
    fun everyDueFieldIsScheduledAsTheAlarmOfItsKind() {
        alarms.reconcile(ALL_DUE)

        val byCode = scheduled().associateBy { shadowOf(it.operation).requestCode }
        assertThat(byCode.keys).containsExactly(101, 102, 103, 104, 105, 106)
        // 04 §6.2: elapsed fields on ELAPSED_REALTIME_WAKEUP, idle demote and the quick-timer
        // cancel on RTC_WAKEUP, all exact and allowed while idle.
        val exact = mapOf(
            101 to (AlarmManager.ELAPSED_REALTIME_WAKEUP to 50_000L),
            102 to (AlarmManager.ELAPSED_REALTIME_WAKEUP to 60_000L),
            103 to (AlarmManager.ELAPSED_REALTIME_WAKEUP to 660_000L),
            104 to (AlarmManager.RTC_WAKEUP to 1_800_000L),
            105 to (AlarmManager.RTC_WAKEUP to 1_900_000L),
        )
        exact.forEach { (code, expected) ->
            val alarm = checkNotNull(byCode[code])
            assertThat(alarm.type to alarm.triggerAtMs).isEqualTo(expected)
            assertThat(alarm.isAllowWhileIdle).isTrue()
            assertThat(alarm.windowLengthMs).isEqualTo(ShadowAlarmManager.WINDOW_EXACT)
        }
        // The midnight timeout is inexact `set(RTC)` and does not wake the phone.
        val midnight = checkNotNull(byCode[106])
        assertThat(midnight.type).isEqualTo(AlarmManager.RTC)
        assertThat(midnight.triggerAtMs).isEqualTo(2_000_000L)
        assertThat(midnight.isAllowWhileIdle).isFalse()
        assertThat(midnight.windowLengthMs).isNotEqualTo(ShadowAlarmManager.WINDOW_EXACT)
    }

    @Test
    fun everyAlarmIsDeliveredToTheReceiverAsItsKind() {
        alarms.reconcile(ALL_DUE)

        val delivered = scheduled().associate { alarm ->
            val intent = shadowOf(alarm.operation).savedIntent
            assertThat(intent.component?.className).isEqualTo(TimerAlarmReceiver::class.java.name)
            shadowOf(alarm.operation).requestCode to TimerAlarmReceiver.kindOf(intent)
        }
        assertThat(delivered).isEqualTo(TimerAlarmKind.entries.associateBy { it.requestCode })
    }

    @Test
    fun aMovedDueTimeReplacesTheAlarmOfItsKind() {
        alarms.reconcile(DueTimes(deadlineElapsedMs = 60_000L))
        alarms.reconcile(DueTimes(deadlineElapsedMs = 75_000L))

        val alarm = scheduled().single()
        assertThat(shadowOf(alarm.operation).requestCode).isEqualTo(102)
        assertThat(alarm.triggerAtMs).isEqualTo(75_000L)
    }

    @Test
    fun aClearedDueTimeCancelsOnlyTheAlarmOfItsKind() {
        alarms.reconcile(DueTimes(preAlertAtElapsedMs = 50_000L, deadlineElapsedMs = 60_000L))

        // The pre-alert played: the next state's due set keeps only the deadline.
        alarms.reconcile(DueTimes(deadlineElapsedMs = 60_000L))

        val alarm = scheduled().single()
        assertThat(shadowOf(alarm.operation).requestCode).isEqualTo(102)
        assertThat(alarm.triggerAtMs).isEqualTo(60_000L)
    }

    @Test
    fun anAlarmSetAgainMovesOnlyItsKind() {
        alarms.reconcile(DueTimes(preAlertAtElapsedMs = 50_000L, deadlineElapsedMs = 60_000L))

        // A turn that could not read the timer has its alarm come again (D-057).
        alarms.retry(TimerAlarmKind.DEADLINE, 65_000L)

        val byCode = scheduled().associate { shadowOf(it.operation).requestCode to it.triggerAtMs }
        assertThat(byCode).containsExactly(101, 50_000L, 102, 65_000L)
        val deadline = scheduled().single { shadowOf(it.operation).requestCode == 102 }
        assertThat(deadline.type).isEqualTo(AlarmManager.ELAPSED_REALTIME_WAKEUP)
        assertThat(deadline.windowLengthMs).isEqualTo(ShadowAlarmManager.WINDOW_EXACT)
    }

    @Test
    fun anEmptyDueSetCancelsEveryAlarm() {
        alarms.reconcile(ALL_DUE)

        alarms.reconcile(DueTimes.NONE)

        assertThat(scheduled()).isEmpty()
    }

    @Test
    fun withoutExactAlarmsTheExactKindsFallBackToInexactAlarmsAndAreLogged() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        alarms.reconcile(DueTimes(deadlineElapsedMs = 60_000L))

        val alarm = scheduled().single()
        assertThat(alarm.type).isEqualTo(AlarmManager.ELAPSED_REALTIME_WAKEUP)
        assertThat(alarm.triggerAtMs).isEqualTo(60_000L)
        assertThat(alarm.isAllowWhileIdle).isTrue()
        assertThat(alarm.windowLengthMs).isNotEqualTo(ShadowAlarmManager.WINDOW_EXACT)
        // The reliability banner (TMR-31) reads canScheduleExactAlarms(); the log says which alarm.
        assertThat(diagnostics.entriesOf<TimerDiagnostic.AlarmNotExact>())
            .containsExactly(TimerDiagnostic.AlarmNotExact(clock.reading(), TimerAlarmKind.DEADLINE))
    }

    private fun scheduled() = shadowOf(alarmManager).scheduledAlarms

    private companion object {
        val ALL_DUE = DueTimes(
            preAlertAtElapsedMs = 50_000L,
            deadlineElapsedMs = 60_000L,
            overtimeTimeoutAtElapsedMs = 660_000L,
            idleDemoteAtWallMs = 1_800_000L,
            quickCancelAtWallMs = 1_900_000L,
            midnightAtWallMs = 2_000_000L,
        )
    }
}
