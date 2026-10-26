package com.umamit.app

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.catalog.AppCatalogRefreshRunner
import com.umamit.core.data.catalog.CatalogRefreshHost
import com.umamit.core.data.catalog.CatalogRefreshState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The refresh service, created by the system, reaches the one refresh the screens start (#65,
 * 06 §5.3) through the app. Nothing in the compiler guards this hand-over: without it the service
 * would leave at once and the refresh would end with the app's process.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CatalogRefreshHostTest {
    private val app: UmamitApplication = ApplicationProvider.getApplicationContext()

    @Test
    fun theAppHandsTheServiceTheOneRefreshRunner() {
        val host: CatalogRefreshHost = app

        assertThat(host.catalogRefresh).isInstanceOf(AppCatalogRefreshRunner::class.java)
        assertThat(host.catalogRefresh).isSameInstanceAs(host.catalogRefresh)
        assertThat(host.catalogRefresh.state.value).isEqualTo(CatalogRefreshState.Idle)
    }
}
