package com.umamit.core.timer.diagnostics

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.umamit.core.timer.controller.TimerClocks
import com.umamit.core.timer.controller.TimerDiagnostic
import com.umamit.core.timer.controller.TimerDiagnosticsPort
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * The states the timer's reliability depends on (06 §5.6, TMR-31): the
 * notification permission, Live Update promotion (null: the platform has no
 * such API), the full-screen alert, exact alarms and the exemption from
 * battery optimisation.
 */
data class PermissionStates(
    val notifications: Boolean,
    val promotedNotifications: Boolean?,
    val fullScreenIntent: Boolean,
    val exactAlarms: Boolean,
    val batteryUnrestricted: Boolean,
)

/** Reads the [PermissionStates] of the app now; a binder call each, so off the main thread. */
fun interface PermissionStatesReader {
    fun read(): PermissionStates
}

/** The platform queries of [PermissionStatesReader] (verified on the Galaxy, #14). */
class AndroidPermissionStatesReader @Inject constructor(@ApplicationContext context: Context) :
    PermissionStatesReader {
    private val appContext = context.applicationContext

    override fun read(): PermissionStates {
        val notifications = checkNotNull(appContext.getSystemService(NotificationManager::class.java))
        return PermissionStates(
            notifications = ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED &&
                NotificationManagerCompat.from(appContext).areNotificationsEnabled(),
            promotedNotifications = promoted(notifications),
            fullScreenIntent = notifications.canUseFullScreenIntent(),
            exactAlarms = checkNotNull(appContext.getSystemService(AlarmManager::class.java)).canScheduleExactAlarms(),
            batteryUnrestricted = checkNotNull(appContext.getSystemService(PowerManager::class.java))
                .isIgnoringBatteryOptimizations(appContext.packageName),
        )
    }

    private fun promoted(notifications: NotificationManager): Boolean? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            try {
                notifications.canPostPromotedNotifications()
            } catch (_: NoSuchMethodError) {
                // A flagged API (FLAG_API_RICH_ONGOING) that a build may lack: unknown.
                null
            }
        } else {
            null
        }
}

/**
 * Logs the [PermissionStates] once per process start (06 §5.6), so a missed
 * alert in the log can be matched to a missing permission. The app calls
 * [record] off the main thread right after it starts; a check that fails is
 * not fatal and logs nothing.
 */
class AppStartDiagnostics @Inject constructor(
    private val reader: PermissionStatesReader,
    private val clocks: TimerClocks,
    private val diagnostics: TimerDiagnosticsPort,
) {
    @Suppress("TooGenericExceptionCaught") // diagnostics never crash the app
    fun record() {
        try {
            diagnostics.record(TimerDiagnostic.PermissionsAtStart(clocks.read(), reader.read()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("UmamiTDiagnostics", "Permission states at start not logged", e)
        }
    }
}
