package com.umamit.core.timer

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every boot reaches the receiver, also while the app does not hand over its controller yet
 * (D-057): no timer can run then, so there is nothing to recover, and even a debuggable build
 * must not crash at boot.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UnwiredRecoveryReceiverTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun aBootWithoutTheControllerDoesNotCrashADebuggableApp() {
        app.applicationInfo.flags = app.applicationInfo.flags or ApplicationInfo.FLAG_DEBUGGABLE

        TimerRecoveryReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        TimerRecoveryReceiver().onReceive(app, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
    }
}
