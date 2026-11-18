package com.umamit.core.network

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The shop client's User-Agent version (GEN-01). A package the package manager does not know (as in a Robolectric
 * test whose app-start launch builds the graph after the test's package manager was reset) gives „unknown" instead
 * of a throw that escaped that launch into the next test's `TestScope` (PR #345).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NetworkModuleTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `an unknown package gives the version unknown instead of a throw`() {
        val unknown = object : ContextWrapper(context) {
            override fun getPackageName() = "com.umamit.not.installed"
        }

        assertThat(NetworkModule.shopClient(unknown).userAgent).isEqualTo("UmamiT/unknown (private app)")
    }

    @Test
    fun `the installed package's versionName is in the User-Agent`() {
        shadowOf(context.packageManager).getInternalMutablePackageInfo(context.packageName).versionName = "9.9.9-test"

        assertThat(NetworkModule.shopClient(context).userAgent).isEqualTo("UmamiT/9.9.9-test (private app)")
    }
}
