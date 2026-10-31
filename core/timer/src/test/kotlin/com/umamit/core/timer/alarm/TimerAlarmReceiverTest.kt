package com.umamit.core.timer.alarm

import android.Manifest
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.TestTimerHost
import com.umamit.core.timer.controller.TimerAlarm
import com.umamit.core.timer.controller.TimerAlarmKind
import com.umamit.core.timer.service.ForegroundServiceState
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/** The alarm receiver hands every alarm to the app's controller as its slot and kind (04 §6.2, D-057, D-098). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestTimerHost::class)
class TimerAlarmReceiverTest {
    private val app: TestTimerHost = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ForegroundServiceState.reset()
    }

    @After
    fun tearDown() = ForegroundServiceState.reset()

    @Test
    fun everyAlarmBroadcastReachesTheControllerAsItsSlotAndKind() {
        TimerSlot.entries.forEach { slot ->
            TimerAlarmKind.entries.forEach { kind ->
                app.sendBroadcast(TimerAlarmReceiver.intent(app, slot, kind))
                shadowOf(Looper.getMainLooper()).idle()

                assertThat(app.alarms.poll(TIMEOUT_S, TimeUnit.SECONDS)).isEqualTo(TimerAlarm(slot, kind))
            }
        }
        assertThat(app.alarms).isEmpty()
    }

    /** An alarm v0.2.2 set, for a brew or a quick timer, arrives after the update as the brew slot's (04 §6.2). */
    @Test
    fun anAlarmBroadcastOfV022ReachesTheControllerAsTheBrewSlotsKind() {
        TimerAlarmKind.entries.forEach { kind ->
            val v022 = Intent(app, TimerAlarmReceiver::class.java)
                .setAction("com.umamit.core.timer.action.TIMER_ALARM.${kind.name}")
            app.sendBroadcast(v022)
            shadowOf(Looper.getMainLooper()).idle()

            assertThat(app.alarms.poll(TIMEOUT_S, TimeUnit.SECONDS)).isEqualTo(TimerAlarm(TimerSlot.BREW, kind))
        }
    }

    private companion object {
        const val TIMEOUT_S = 5L
    }
}
