package com.umamit.core.timer.alarm

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.timer.controller.TimerAlarmKind
import com.umamit.core.timer.controller.TimerAlarmSink
import com.umamit.core.timer.controller.TimerAlarmSinkHost
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** The alarm receiver hands every alarm to the app's controller as its kind (04 §6.2, D-057). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TimerAlarmReceiverTest.HostApp::class)
class TimerAlarmReceiverTest {
    private val app: HostApp = ApplicationProvider.getApplicationContext()

    @Test
    fun everyAlarmBroadcastReachesTheControllerAsItsKind() {
        TimerAlarmKind.entries.forEach { kind ->
            app.sendBroadcast(TimerAlarmReceiver.intent(app, kind))
            shadowOf(Looper.getMainLooper()).idle()

            assertThat(app.received.poll(TIMEOUT_S, TimeUnit.SECONDS)).isEqualTo(kind)
        }
        assertThat(app.received).isEmpty()
    }

    /** An app that runs the controller: here, one that records the alarms it is handed. */
    class HostApp :
        Application(),
        TimerAlarmSinkHost {
        val received = LinkedBlockingQueue<TimerAlarmKind>()

        override val timerAlarmSink = TimerAlarmSink { kind -> received.put(kind) }
    }

    private companion object {
        const val TIMEOUT_S = 5L
    }
}
