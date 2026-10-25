package com.umamit.core.data.catalog

import android.content.Context
import com.umamit.core.common.WallClock
import com.umamit.core.data.diagnostics.DiagnosticsLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Keeps a running refresh alive with the phone locked and shows it in the notification shade
 * (CAT-07 AC1, 06 §5.3): the runner's side of [CatalogRefreshService].
 */
interface CatalogRefreshForeground {
    /** A refresh started: the service comes up and follows it until it ends. */
    fun start()

    /** The result was seen in the app, or a new refresh starts: the result notification goes. */
    fun clearResult()
}

/**
 * [CatalogRefreshForeground] by [CatalogRefreshService]: started with `startForegroundService`
 * from the user's tap while the app is in the foreground (06 §5.3). If Android refuses the start,
 * the refresh still runs while the app's process lives (as before #65), and the refusal is logged.
 */
class ServiceCatalogRefreshForeground @Inject constructor(
    @ApplicationContext private val context: Context,
    private val diagnostics: DiagnosticsLog,
    private val clock: WallClock,
) : CatalogRefreshForeground {
    override fun start() {
        val refusal = try {
            context.startForegroundService(CatalogRefreshService.runIntent(context))
            null
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException and the other ServiceStartNotAllowedExceptions.
            e
        } catch (e: SecurityException) {
            e
        }
        if (refusal != null) {
            diagnostics.append(
                clock.nowMs(),
                clock.zone(),
                "catalog refresh: service not started (${refusal.javaClass.simpleName}), runs while the app lives",
            )
        }
    }

    override fun clearResult() = CatalogNotifications.cancelResult(context)
}
