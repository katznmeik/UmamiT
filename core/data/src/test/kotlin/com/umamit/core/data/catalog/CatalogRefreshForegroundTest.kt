package com.umamit.core.data.catalog

import android.app.Application
import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationManager
import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.diagnostics.DiagnosticsLog
import com.umamit.core.testing.FakeClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The runner's side of the refresh service (#65, 06 §5.3): a start sends `startForegroundService`
 * to [CatalogRefreshService]; a refused start is logged and never thrown into the tap that
 * started the refresh; a seen result takes its notification away.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CatalogRefreshForegroundTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val diagnostics by lazy {
        DiagnosticsLog(folder.newFolder(), maxBytes = 100_000, dispatcher = Dispatchers.IO)
    }

    @Test
    fun aStartSendsTheRunToTheRefreshService() {
        ServiceCatalogRefreshForeground(app, diagnostics, FakeClock()).start()

        val started = checkNotNull(shadowOf(app).nextStartedService)
        assertThat(started.component).isEqualTo(ComponentName(app, CatalogRefreshService::class.java))
        assertThat(started.action).isEqualTo(CatalogRefreshService.runIntent(app).action)
    }

    @Test
    fun aRefusedStartIsLoggedAndNotThrown() = runBlocking {
        val refusing = object : ContextWrapper(app) {
            override fun startForegroundService(service: Intent?): ComponentName? =
                throw ForegroundServiceStartNotAllowedException("refused")
        }

        ServiceCatalogRefreshForeground(refusing, diagnostics, FakeClock()).start()

        assertThat(diagnostics.entries().single().message).isEqualTo(
            "catalog refresh: service not started (ForegroundServiceStartNotAllowedException), " +
                "runs while the app lives",
        )
    }

    @Test
    fun clearingTheResultRemovesItsNotification() {
        val notifications = app.getSystemService(NotificationManager::class.java)
        CatalogNotifications.ensureChannel(app)
        val result =
            checkNotNull(CatalogNotifications.result(app, CatalogRefreshOutcome.Success(teas = 1, newTeas = 1)))
        notifications.notify(CatalogNotifications.ID_RESULT, result)

        ServiceCatalogRefreshForeground(app, diagnostics, FakeClock()).clearResult()

        assertThat(shadowOf(notifications).getNotification(CatalogNotifications.ID_RESULT)).isNull()
    }
}
