package com.umamit.core.timer.alarm

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import com.umamit.core.timer.controller.TimerAlarmKind
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * An app that schedules timer alarms but does not hand the receiver its controller would
 * miss every alert after a kill (D-057): a debuggable build fails at once, a release build
 * logs an error and never crashes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UnwiredReceiverTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun aDebuggableAppWithoutTheControllerFailsLoudly() {
        app.applicationInfo.flags = app.applicationInfo.flags or ApplicationInfo.FLAG_DEBUGGABLE

        assertThrows(IllegalStateException::class.java) {
            TimerAlarmReceiver().onReceive(app, TimerAlarmReceiver.intent(app, TimerAlarmKind.DEADLINE))
        }
    }

    @Test
    fun aReleaseAppWithoutTheControllerDropsTheAlarmWithoutCrashing() {
        app.applicationInfo.flags = app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()

        TimerAlarmReceiver().onReceive(app, TimerAlarmReceiver.intent(app, TimerAlarmKind.DEADLINE))
    }
}
