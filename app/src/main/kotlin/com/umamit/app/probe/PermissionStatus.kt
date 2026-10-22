package com.umamit.app.probe

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * The five timer-reliability checks of the probe's status list (M1-03a, #14;
 * 09 §4), in display order.
 */
enum class PermissionStatusItem {
    /** `POST_NOTIFICATIONS` granted and notifications enabled (04 §8). */
    Notifications,

    /** Live Update promotion, `canPostPromotedNotifications()` (04 §8.3; API 36+). */
    PromotedNotifications,

    /** Full-screen alert, `canUseFullScreenIntent()` (04 §8.2). */
    FullScreenIntent,

    /** Exact alarms, `canScheduleExactAlarms()` (04 §6.2). */
    ExactAlarms,

    /** Exemption from battery optimisation, `isIgnoringBatteryOptimizations()` (04 §6.4). */
    BatteryOptimization,
}

/** What a status row shows: ✓, ✗, or „nicht verfügbar" when the platform lacks the API. */
enum class PermissionState { Granted, Denied, Unavailable }

data class PermissionStatusRow(val item: PermissionStatusItem, val state: PermissionState)

/** The platform queries behind the rows; a seam so the state logic is tested with fakes. */
interface PermissionStatusChecks {
    fun areNotificationsEnabled(): Boolean

    /** `null` when the platform has no promotion API (before Android 16, API 36). */
    fun canPostPromotedNotifications(): Boolean?

    fun canUseFullScreenIntent(): Boolean

    fun canScheduleExactAlarms(): Boolean

    fun isIgnoringBatteryOptimizations(): Boolean
}

/** Maps the checks to one row per [PermissionStatusItem], in display order. */
class PermissionStatusReader(private val checks: PermissionStatusChecks) {

    fun read(): List<PermissionStatusRow> = PermissionStatusItem.entries.map { PermissionStatusRow(it, stateOf(it)) }

    private fun stateOf(item: PermissionStatusItem): PermissionState = when (item) {
        PermissionStatusItem.Notifications -> checks.areNotificationsEnabled().toState()

        PermissionStatusItem.PromotedNotifications ->
            checks.canPostPromotedNotifications()?.toState() ?: PermissionState.Unavailable

        PermissionStatusItem.FullScreenIntent -> checks.canUseFullScreenIntent().toState()

        PermissionStatusItem.ExactAlarms -> checks.canScheduleExactAlarms().toState()

        PermissionStatusItem.BatteryOptimization -> checks.isIgnoringBatteryOptimizations().toState()
    }

    private fun Boolean.toState() = if (this) PermissionState.Granted else PermissionState.Denied
}

/** The real platform queries for this app. */
class AndroidPermissionStatusChecks(context: Context) : PermissionStatusChecks {
    private val appContext = context.applicationContext
    private val notificationManager = appContext.getSystemService(NotificationManager::class.java)
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)
    private val powerManager = appContext.getSystemService(PowerManager::class.java)

    override fun areNotificationsEnabled(): Boolean = isNotificationPermissionGranted(appContext) &&
        NotificationManagerCompat.from(appContext).areNotificationsEnabled()

    override fun canPostPromotedNotifications(): Boolean? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
        try {
            notificationManager.canPostPromotedNotifications()
        } catch (_: NoSuchMethodError) {
            // Flagged API (android.app.Flags.FLAG_API_RICH_ONGOING): treat a build without it as unavailable.
            null
        }
    } else {
        null
    }

    override fun canUseFullScreenIntent(): Boolean = notificationManager.canUseFullScreenIntent()

    override fun canScheduleExactAlarms(): Boolean = alarmManager.canScheduleExactAlarms()

    override fun isIgnoringBatteryOptimizations(): Boolean =
        powerManager.isIgnoringBatteryOptimizations(appContext.packageName)
}

/** Whether the `POST_NOTIFICATIONS` runtime permission is granted. */
internal fun isNotificationPermissionGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
