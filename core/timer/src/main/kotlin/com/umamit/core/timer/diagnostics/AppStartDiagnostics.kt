package com.umamit.core.timer.diagnostics

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
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
            fullScreenIntent = fullScreenIntent(notifications),
            exactAlarms = checkNotNull(appContext.getSystemService(AlarmManager::class.java)).canScheduleExactAlarms(),
            batteryUnrestricted = checkNotNull(appContext.getSystemService(PowerManager::class.java))
                .isIgnoringBatteryOptimizations(appContext.packageName),
        )
    }

    /**
     * The full-screen alert (04 §8.2, TMR-31): `canUseFullScreenIntent()` and the app op the user
     * switches in *Vollbildbenachrichtigungen* (D-068). On API 35 and 36 the permission check alone
     * still answered true after the op was denied, so a revoked full-screen alert never reached
     * the reliability card; the op's own mode reflects it at once. A mode of „default" leaves the
     * answer to the permission check.
     */
    private fun fullScreenIntent(notifications: NotificationManager): Boolean =
        fullScreenAlertAllowed(notifications.canUseFullScreenIntent(), fullScreenOpMode())

    /** The mode of the full-screen app op for this app; `MODE_DEFAULT` where the platform cannot say. */
    internal fun fullScreenOpMode(): Int {
        val appOps = appContext.getSystemService(AppOpsManager::class.java) ?: return AppOpsManager.MODE_DEFAULT
        return try {
            appOps.unsafeCheckOpNoThrow(
                OP_USE_FULL_SCREEN_INTENT,
                appContext.applicationInfo.uid,
                appContext.packageName,
            )
        } catch (_: IllegalArgumentException) {
            // A platform without this op name: the permission check is all there is.
            AppOpsManager.MODE_DEFAULT
        }
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

    private companion object {
        /** `AppOpsManager.OPSTR_USE_FULL_SCREEN_INTENT`, which the SDK hides. */
        const val OP_USE_FULL_SCREEN_INTENT = "android:use_full_screen_intent"
    }
}

/**
 * The full-screen alert is allowed (D-068): the permission check says so ([permission]) and the
 * app op is not denied ([opMode]; `MODE_DEFAULT` leaves the answer to the permission check).
 */
internal fun fullScreenAlertAllowed(permission: Boolean, opMode: Int): Boolean =
    permission && opMode != AppOpsManager.MODE_ERRORED && opMode != AppOpsManager.MODE_IGNORED

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
