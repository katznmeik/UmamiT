package com.umamit.core.timer.controller

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.model.TimerPhase
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.alarm.AndroidTimerAlarms
import com.umamit.core.timer.alarm.TimerAlarmReceiver
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
 * The update from v0.4.1 with a brew and a quick timer running (04 §7.5; D-108, rule 8): v0.4.1 stored them as
 * the `active_brew` rows `BREW` and `QUICK` of umamit.db v4 and set their alarms under 101–107 and 201–207, as this
 * version does for that pair. The new process opens the database through `MIGRATION_4_5` ([V041Database]), keeps
 * both in their slots and codes, both alert on time, and a second brew session then takes `BREW_2` with the codes
 * 301–307; an update during the brew's alert with the quick alert held replays the brew's and holds the quick one
 * until the brew's sound has ended (D-109). The alarms are the real alarm manager's (Robolectric).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UpdateFromV041Test {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** v0.4.1's process, with its alarms on the alarm manager. */
    private val before = ControllerHarness(alarmPort = { androidAlarms() })

    /** The database file v0.4.1 leaves at the update, and the one the new process opens through the migration. */
    private val v041 = V041Database(context)
    private var migrated: UmamiDatabase? = null

    @Before
    fun setUp() = runTest {
        // USE_EXACT_ALARM is granted at install on a device; Robolectric defaults to false.
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        before.teaWithStock()
        before.teaWithStock(ControllerHarness.TEA_2_ID, ControllerHarness.TEA_2_NAME)
    }

    @After
    fun tearDown() {
        migrated?.close()
        before.close()
    }

    @Test
    fun `a running brew and quick timer keep their slots and alarms, and a second brew takes BREW_2`() = runTest {
        before.controller.startSession(brewStart(infusion(60, preAlert = 0)))
        before.controller.send(TimerSlot.BREW, TimerEvent.Start)
        before.controller.startQuickTimer(90, preAlertSeconds = 0, run = true)
        val brew = SessionKind.Brew(checkNotNull(before.controller.activeIn(TimerSlot.BREW).sessionId))
        val brewDeadline = checkNotNull(before.controller.activeIn(TimerSlot.BREW).deadlineElapsedMs)
        val quickDeadline = checkNotNull(before.controller.activeIn(TimerSlot.QUICK).deadlineElapsedMs)
        before.advanceSeconds(20)

        val after = updated()
        after.controller.recover(RecoveryTrigger.PACKAGE_REPLACED)

        assertThat(after.controller.activeIn(TimerSlot.BREW).kind).isEqualTo(brew)
        assertThat(after.controller.activeIn(TimerSlot.QUICK).phase).isEqualTo(TimerPhase.RUNNING)
        assertThat(alarms()).containsExactly(
            102,
            brewDeadline,
            107,
            brewDeadline + AlertWatchdog.GUARD_MS,
            202,
            quickDeadline,
            207,
            quickDeadline + AlertWatchdog.GUARD_MS,
        )

        after.clock.advanceBy(brewDeadline - after.clock.elapsed)
        after.deliver(102)
        assertThat(after.alert.playing?.kind).isEqualTo(brew)
        after.controller.send(TimerSlot.BREW, TimerEvent.Poured)
        after.clock.advanceBy(quickDeadline - after.clock.elapsed)
        after.deliver(202)
        assertThat(after.alert.playing?.kind).isEqualTo(SessionKind.Quick)
        after.controller.send(TimerSlot.QUICK, TimerEvent.Poured)

        // The brew still waits for its next infusion; a second tea's session takes the second brew slot.
        after.controller.startSession(
            brewStart(
                infusion(45, preAlert = 0),
                teaId = ControllerHarness.TEA_2_ID,
                teaName = ControllerHarness.TEA_2_NAME,
            ),
        )
        after.controller.send(TimerSlot.BREW_2, TimerEvent.Start)
        val secondDeadline = checkNotNull(after.controller.activeIn(TimerSlot.BREW_2).deadlineElapsedMs)

        assertThat(alarms()).containsEntry(302, secondDeadline)
        assertThat(alarms()).containsEntry(307, secondDeadline + AlertWatchdog.GUARD_MS)
        after.clock.advanceBy(secondDeadline - after.clock.elapsed)
        after.deliver(302)
        assertThat(after.controller.activeIn(TimerSlot.BREW_2).ringing).isTrue()
        assertThat(alarms().keys).containsAtLeast(303, 307)
    }

    @Test
    fun `an update during the brew's alert replays it and the held quick alert rings once its sound has ended`() =
        runTest {
            before.controller.startSession(brewStart(infusion(60, preAlert = 0)))
            before.controller.send(TimerSlot.BREW, TimerEvent.Start)
            before.advanceSeconds(1)
            before.controller.startQuickTimer(60, preAlertSeconds = 0, run = true)
            val brew = SessionKind.Brew(checkNotNull(before.controller.activeIn(TimerSlot.BREW).sessionId))
            val brewDeadline = checkNotNull(before.controller.activeIn(TimerSlot.BREW).deadlineElapsedMs)
            before.advanceSeconds(62)
            assertThat(before.controller.activeIn(TimerSlot.QUICK).alertHeld).isTrue()

            // The update kills v0.4.1 while the brew's alert sounds; the new process starts 5 s later.
            val after = updated()
            after.clock.advanceBy(5_000)
            after.controller.recover(RecoveryTrigger.PACKAGE_REPLACED)

            assertThat(after.alert.started.map { it.kind }).containsExactly(brew)
            assertThat(after.controller.activeIn(TimerSlot.BREW).ringing).isTrue()
            assertThat(after.controller.activeIn(TimerSlot.QUICK).alertHeld).isTrue()
            assertThat(alarms()).containsEntry(103, brewDeadline + 600_000)
            assertThat(alarms().keys).containsNoneOf(202, 203)

            after.controller.alertCompleted(checkNotNull(after.alert.playing))

            assertThat(after.alert.playing?.kind).isEqualTo(SessionKind.Quick)
            assertThat(after.controller.activeIn(TimerSlot.QUICK).ringing).isTrue()
            assertThat(alarms()).containsEntry(203, after.clock.elapsed + 600_000)
            assertThat(after.controller.activeIn(TimerSlot.BREW).phase).isEqualTo(TimerPhase.ALERTING)
        }

    /** The new process after the update: v0.4.1's rows through `MIGRATION_4_5`, its alarms on the alarm manager. */
    private fun updated(): ControllerHarness {
        v041.writeFrom(before.db)
        val db = v041.migrated().also { migrated = it }
        return before.processRestarted(alarmPort = { androidAlarms() }, db = db)
    }

    private fun ControllerHarness.androidAlarms(): TimerAlarmPort =
        AndroidTimerAlarms(context, TimerClocks(clock, clock), diagnostics)

    private fun BrewSessionController.activeIn(slot: TimerSlot) = state(slot).value as TimerUiState.Active

    /** The alarm manager's alarms, trigger time by request code. */
    private fun alarms(): Map<Int, Long> = shadowOf(alarmManager).scheduledAlarms
        .associate { shadowOf(it.operation).requestCode to it.triggerAtMs }

    /** The alarm with [requestCode] fires now, at its trigger time: its broadcast reaches the controller. */
    private suspend fun ControllerHarness.deliver(requestCode: Int) {
        val alarm = shadowOf(alarmManager).scheduledAlarms.single { shadowOf(it.operation).requestCode == requestCode }
        assertThat(alarm.triggerAtMs).isEqualTo(clock.elapsed)
        val delivered = checkNotNull(TimerAlarmReceiver.alarmOf(shadowOf(alarm.operation).savedIntent))
        alarmManager.cancel(checkNotNull(alarm.operation))
        controller.onAlarm(delivered.slot, delivered.kind)
    }
}
