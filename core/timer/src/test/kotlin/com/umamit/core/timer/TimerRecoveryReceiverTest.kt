package com.umamit.core.timer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.timer.controller.RecoveryTrigger
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * After a reboot (`BOOT_COMPLETED`, after the first unlock, E2) and an update of the app
 * (`MY_PACKAGE_REPLACED`), the receiver asks the app's controller to recover the timer
 * (04 §7.3, §7.5; #46).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestTimerHost::class)
class TimerRecoveryReceiverTest {
    private val app: TestTimerHost = ApplicationProvider.getApplicationContext()

    @Test
    fun bootCompletedRecoversTheTimer() {
        app.sendBroadcast(Intent(Intent.ACTION_BOOT_COMPLETED).setPackage(app.packageName))
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(app.recoveries.poll(TIMEOUT_S, TimeUnit.SECONDS)).isEqualTo(RecoveryTrigger.BOOT_COMPLETED)
    }

    @Test
    fun anUpdateOfTheAppRecoversTheTimer() {
        app.sendBroadcast(Intent(Intent.ACTION_MY_PACKAGE_REPLACED).setPackage(app.packageName))
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(app.recoveries.poll(TIMEOUT_S, TimeUnit.SECONDS)).isEqualTo(RecoveryTrigger.PACKAGE_REPLACED)
    }

    @Test
    fun anyOtherBroadcastIsIgnored() {
        TimerRecoveryReceiver().onReceive(app, Intent(Intent.ACTION_TIME_CHANGED))

        assertThat(app.recoveries.poll(1, TimeUnit.SECONDS)).isNull()
    }

    @Test
    fun theReceiverIsDeclaredNotExportedForBothBroadcasts() {
        listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED).forEach { action ->
            val receivers = app.packageManager.queryBroadcastReceivers(Intent(action).setPackage(app.packageName), 0)
            val receiver = receivers.single().activityInfo
            assertThat(receiver.name).isEqualTo(TimerRecoveryReceiver::class.java.name)
            // The system sends both broadcasts, which reach a receiver that is not exported (04 §13).
            assertThat(receiver.exported).isFalse()
        }
        val permissions = app.packageManager
            .getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            .orEmpty()
        assertThat(permissions).asList().contains(Manifest.permission.RECEIVE_BOOT_COMPLETED)
    }

    private companion object {
        const val TIMEOUT_S = 5L
    }
}
