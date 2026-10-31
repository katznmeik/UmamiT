package com.umamit.core.timer.alarm

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.TimerSlot
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeTimerDiagnostics
import com.umamit.core.timer.controller.TimerAlarm
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
 * request code, replaced when it moves and cancelled when it clears. Each slot has its own
 * request codes, so the slots never touch each other's alarms (D-098).
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
        alarms.reconcile(TimerSlot.BREW, DueTimes(preAlertAtElapsedMs = 50_000L))

        val alarm = scheduled().single()
        assertThat(alarm.type).isEqualTo(AlarmManager.ELAPSED_REALTIME_WAKEUP)
        assertThat(alarm.triggerAtMs).isEqualTo(50_000L)
        assertThat(alarm.isAllowWhileIdle).isTrue()
        assertThat(alarm.windowLengthMs).isEqualTo(ShadowAlarmManager.WINDOW_EXACT)
        assertThat(shadowOf(alarm.operation).requestCode).isEqualTo(101)
    }

    @Test
    fun everyDueFieldIsScheduledAsTheAlarmOfItsKind() {
        alarms.reconcile(TimerSlot.BREW, ALL_DUE)

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
        alarms.reconcile(TimerSlot.BREW, ALL_DUE)

        val delivered = scheduled().associate { alarm ->
            val intent = shadowOf(alarm.operation).savedIntent
            assertThat(intent.component?.className).isEqualTo(TimerAlarmReceiver::class.java.name)
            shadowOf(alarm.operation).requestCode to TimerAlarmReceiver.alarmOf(intent)
        }
        assertThat(delivered).containsExactly(
            101, TimerAlarm(TimerSlot.BREW, TimerAlarmKind.PRE_ALERT),
            102, TimerAlarm(TimerSlot.BREW, TimerAlarmKind.DEADLINE),
            103, TimerAlarm(TimerSlot.BREW, TimerAlarmKind.OVERTIME_TIMEOUT),
            104, TimerAlarm(TimerSlot.BREW, TimerAlarmKind.IDLE_DEMOTE),
            105, TimerAlarm(TimerSlot.BREW, TimerAlarmKind.QUICK_CANCEL),
            106, TimerAlarm(TimerSlot.BREW, TimerAlarmKind.MIDNIGHT),
        )
    }

    @Test
    fun theQuickSlotSchedulesEveryDueFieldUnderItsOwnRequestCodes() {
        alarms.reconcile(TimerSlot.QUICK, ALL_DUE)
        alarms.watchAlert(TimerSlot.QUICK, 90_000L)

        val delivered = scheduled().associate { alarm ->
            shadowOf(alarm.operation).requestCode to
                (TimerAlarmReceiver.alarmOf(shadowOf(alarm.operation).savedIntent) to alarm.triggerAtMs)
        }
        assertThat(delivered).containsExactly(
            201, TimerAlarm(TimerSlot.QUICK, TimerAlarmKind.PRE_ALERT) to 50_000L,
            202, TimerAlarm(TimerSlot.QUICK, TimerAlarmKind.DEADLINE) to 60_000L,
            203, TimerAlarm(TimerSlot.QUICK, TimerAlarmKind.OVERTIME_TIMEOUT) to 660_000L,
            204, TimerAlarm(TimerSlot.QUICK, TimerAlarmKind.IDLE_DEMOTE) to 1_800_000L,
            205, TimerAlarm(TimerSlot.QUICK, TimerAlarmKind.QUICK_CANCEL) to 1_900_000L,
            206, TimerAlarm(TimerSlot.QUICK, TimerAlarmKind.MIDNIGHT) to 2_000_000L,
            207, TimerAlarm(TimerSlot.QUICK, TimerAlarmKind.ALERT_WATCHDOG) to 90_000L,
        )
        // Delivered like the brew slot's: the same clock and exactness per kind (04 §6.2).
        val deadline = scheduled().single { shadowOf(it.operation).requestCode == 202 }
        assertThat(deadline.type).isEqualTo(AlarmManager.ELAPSED_REALTIME_WAKEUP)
        assertThat(deadline.windowLengthMs).isEqualTo(ShadowAlarmManager.WINDOW_EXACT)
        val midnight = scheduled().single { shadowOf(it.operation).requestCode == 206 }
        assertThat(midnight.type).isEqualTo(AlarmManager.RTC)
    }

    @Test
    fun theTwoSlotsNeverReplaceOrCancelEachOthersAlarms() {
        alarms.reconcile(TimerSlot.BREW, ALL_DUE)
        alarms.watchAlert(TimerSlot.BREW, 90_000L)
        alarms.reconcile(TimerSlot.QUICK, DueTimes(preAlertAtElapsedMs = 7_000L, deadlineElapsedMs = 8_000L))
        alarms.watchAlert(TimerSlot.QUICK, 98_000L)

        assertThat(codesAndTimes()).isEqualTo(BREW_ALARMS + mapOf(201 to 7_000L, 202 to 8_000L, 207 to 98_000L))

        // The quick slot's deadline is set again, then everything of the quick slot goes.
        alarms.retry(TimerSlot.QUICK, TimerAlarmKind.DEADLINE, 13_000L)
        assertThat(codesAndTimes()).isEqualTo(BREW_ALARMS + mapOf(201 to 7_000L, 202 to 13_000L, 207 to 98_000L))
        alarms.reconcile(TimerSlot.QUICK, DueTimes.NONE)
        alarms.watchAlert(TimerSlot.QUICK, null)

        assertThat(codesAndTimes()).isEqualTo(BREW_ALARMS)

        // And the brew slot's go without touching a quick timer's.
        alarms.reconcile(TimerSlot.QUICK, DueTimes(deadlineElapsedMs = 8_000L))
        alarms.reconcile(TimerSlot.BREW, DueTimes.NONE)
        alarms.watchAlert(TimerSlot.BREW, null)

        assertThat(codesAndTimes()).containsExactly(202, 8_000L)
    }

    /**
     * Up to v0.2.2 every timer set its alarms as the brew slot does now (04 §6.2, §7.5): the same
     * request code and the same broadcast. So the brew slot replaces and cancels an alarm the old
     * version left behind, and the receiver reads it as the brew slot's.
     */
    @Test
    fun anAlarmLeftByV022IsTheBrewSlotsAlarmOfItsKind() {
        V022Alarms.set(context, TimerAlarmKind.DEADLINE, 60_000L)
        V022Alarms.set(context, TimerAlarmKind.PRE_ALERT, 50_000L)
        val left = scheduled().single { shadowOf(it.operation).requestCode == 102 }
        assertThat(TimerAlarmReceiver.alarmOf(shadowOf(left.operation).savedIntent))
            .isEqualTo(TimerAlarm(TimerSlot.BREW, TimerAlarmKind.DEADLINE))

        alarms.reconcile(TimerSlot.BREW, DueTimes(deadlineElapsedMs = 75_000L))

        assertThat(codesAndTimes()).containsExactly(102, 75_000L)

        alarms.reconcile(TimerSlot.BREW, DueTimes.NONE)

        assertThat(scheduled()).isEmpty()
    }

    @Test
    fun theAlertWatchdogIsAnExactElapsedWakeUpAlarmWithRequestCode107() {
        alarms.watchAlert(TimerSlot.BREW, 90_000L)

        val alarm = scheduled().single()
        assertThat(alarm.type).isEqualTo(AlarmManager.ELAPSED_REALTIME_WAKEUP)
        assertThat(alarm.triggerAtMs).isEqualTo(90_000L)
        assertThat(alarm.isAllowWhileIdle).isTrue()
        assertThat(alarm.windowLengthMs).isEqualTo(ShadowAlarmManager.WINDOW_EXACT)
        assertThat(shadowOf(alarm.operation).requestCode).isEqualTo(107)
        assertThat(TimerAlarmReceiver.alarmOf(shadowOf(alarm.operation).savedIntent))
            .isEqualTo(TimerAlarm(TimerSlot.BREW, TimerAlarmKind.ALERT_WATCHDOG))
    }

    @Test
    fun theDueSetLeavesTheWatchdogAloneAndOnlyItsOwnCallCancelsIt() {
        alarms.watchAlert(TimerSlot.BREW, 90_000L)

        alarms.reconcile(TimerSlot.BREW, DueTimes.NONE)
        assertThat(scheduled().map { shadowOf(it.operation).requestCode }).containsExactly(107)

        alarms.watchAlert(TimerSlot.BREW, null)
        assertThat(scheduled()).isEmpty()
    }

    @Test
    fun aMovedDueTimeReplacesTheAlarmOfItsKind() {
        alarms.reconcile(TimerSlot.BREW, DueTimes(deadlineElapsedMs = 60_000L))
        alarms.reconcile(TimerSlot.BREW, DueTimes(deadlineElapsedMs = 75_000L))

        val alarm = scheduled().single()
        assertThat(shadowOf(alarm.operation).requestCode).isEqualTo(102)
        assertThat(alarm.triggerAtMs).isEqualTo(75_000L)
    }

    @Test
    fun aClearedDueTimeCancelsOnlyTheAlarmOfItsKind() {
        alarms.reconcile(TimerSlot.BREW, DueTimes(preAlertAtElapsedMs = 50_000L, deadlineElapsedMs = 60_000L))

        // The pre-alert played: the next state's due set keeps only the deadline.
        alarms.reconcile(TimerSlot.BREW, DueTimes(deadlineElapsedMs = 60_000L))

        val alarm = scheduled().single()
        assertThat(shadowOf(alarm.operation).requestCode).isEqualTo(102)
        assertThat(alarm.triggerAtMs).isEqualTo(60_000L)
    }

    @Test
    fun anAlarmSetAgainMovesOnlyItsKind() {
        alarms.reconcile(TimerSlot.BREW, DueTimes(preAlertAtElapsedMs = 50_000L, deadlineElapsedMs = 60_000L))

        // A turn that could not read the timer has its alarm come again (D-057).
        alarms.retry(TimerSlot.BREW, TimerAlarmKind.DEADLINE, 65_000L)

        val byCode = scheduled().associate { shadowOf(it.operation).requestCode to it.triggerAtMs }
        assertThat(byCode).containsExactly(101, 50_000L, 102, 65_000L)
        val deadline = scheduled().single { shadowOf(it.operation).requestCode == 102 }
        assertThat(deadline.type).isEqualTo(AlarmManager.ELAPSED_REALTIME_WAKEUP)
        assertThat(deadline.windowLengthMs).isEqualTo(ShadowAlarmManager.WINDOW_EXACT)
    }

    @Test
    fun anEmptyDueSetCancelsEveryAlarm() {
        alarms.reconcile(TimerSlot.BREW, ALL_DUE)

        alarms.reconcile(TimerSlot.BREW, DueTimes.NONE)

        assertThat(scheduled()).isEmpty()
    }

    @Test
    fun withoutExactAlarmsTheExactKindsFallBackToInexactAlarmsAndAreLogged() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        alarms.reconcile(TimerSlot.BREW, DueTimes(deadlineElapsedMs = 60_000L))

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

    private fun codesAndTimes(): Map<Int, Long> =
        scheduled().associate { shadowOf(it.operation).requestCode to it.triggerAtMs }

    private companion object {
        val ALL_DUE = DueTimes(
            preAlertAtElapsedMs = 50_000L,
            deadlineElapsedMs = 60_000L,
            overtimeTimeoutAtElapsedMs = 660_000L,
            idleDemoteAtWallMs = 1_800_000L,
            quickCancelAtWallMs = 1_900_000L,
            midnightAtWallMs = 2_000_000L,
        )

        /** The brew slot's alarms of [ALL_DUE] and its watchdog at 90 s, by request code. */
        val BREW_ALARMS = mapOf(
            101 to 50_000L,
            102 to 60_000L,
            103 to 660_000L,
            104 to 1_800_000L,
            105 to 1_900_000L,
            106 to 2_000_000L,
            107 to 90_000L,
        )
    }
}
