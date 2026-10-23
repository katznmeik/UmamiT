package com.umamit.app.timer

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.app.UmamitApplication
import com.umamit.core.data.diagnostics.DiagnosticsLog
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The app's process start recovers the timer (04 §7.2, D-063; #46 review S2): after a kill or a
 * force stop, opening the app shows the running step at once, without waiting for another
 * input. Nothing in the compiler guards this call, so this test does: the controller logs the
 * request (06 §5.6).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppStartRecoveryTest {
    private val app: UmamitApplication = ApplicationProvider.getApplicationContext()

    @Test
    fun theProcessStartAsksTheControllerToRecoverTheTimer() {
        val log = File(File(app.noBackupFilesDir, DiagnosticsLog.DIRECTORY), "diagnostics.log")
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline && !log.recoveryRequested()) Thread.sleep(POLL_MS)

        assertWithMessage("diagnostics log after the app's onCreate:\n${log.takeIf { it.exists() }?.readText()}")
            .that(log.recoveryRequested())
            .isTrue()
    }

    private fun File.recoveryRequested() = exists() && readText().contains("recovery requested by APP_START")

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val POLL_MS = 50L
    }
}
