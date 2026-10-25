package com.umamit.core.data.diagnostics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.DataModule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The app's diagnostics log lives in the no-backup storage, which no device
 * backup or transfer ever includes (06 §5.6, 05 §8), and keeps the 200 KB cap.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DiagnosticsLogLocationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `the app's log is in the no-backup storage`() {
        val log = DataModule.diagnosticsLog(context)

        assertThat(log.directory).isEqualTo(File(context.noBackupFilesDir, "diagnostics"))
    }
}
