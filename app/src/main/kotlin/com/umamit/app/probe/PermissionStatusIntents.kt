package com.umamit.app.probe

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * The settings pages a status row opens, most specific first (04 §6.2, §6.4,
 * §8.2, §8.3). The last entry is always the app's details page, so a tap opens
 * something even where a specific page does not exist (One UI, emulators).
 *
 * - Notifications: the app's notification settings.
 * - Live-Benachrichtigungen: the promotion page (API 36+), else the app's
 *   notification settings.
 * - Vollbild-Alarm: the full-screen intent page for this app.
 * - Exakte Alarme: the exact-alarm page for this app.
 * - Akku: while optimised, the system request to exempt this app; once exempt,
 *   the app's details page (One UI's *Akku › Uneingeschränkt* lives there).
 *   The battery-optimisation list is the fallback for the request.
 */
// BatteryLife: sideloaded, never reviewed by Play (D-013, 04 §6.4). InlinedApi: the promotion
// action (API 36) is only used when [sdkInt] is at least 36; it is a plain string constant.
@SuppressLint("BatteryLife", "InlinedApi")
fun permissionStatusSettingsIntents(
    item: PermissionStatusItem,
    state: PermissionState,
    packageName: String,
    sdkInt: Int = Build.VERSION.SDK_INT,
): List<Intent> {
    val packageUri = Uri.fromParts("package", packageName, null)
    val appNotificationSettings = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)

    val specific = when (item) {
        PermissionStatusItem.Notifications -> listOf(appNotificationSettings)

        PermissionStatusItem.PromotedNotifications -> if (sdkInt >= Build.VERSION_CODES.BAKLAVA) {
            listOf(
                Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
                appNotificationSettings,
            )
        } else {
            listOf(appNotificationSettings)
        }

        PermissionStatusItem.FullScreenIntent ->
            listOf(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri))

        PermissionStatusItem.ExactAlarms ->
            listOf(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri))

        PermissionStatusItem.BatteryOptimization -> if (state == PermissionState.Granted) {
            emptyList()
        } else {
            listOf(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            )
        }
    }
    return specific + appDetails
}

/**
 * Starts the first of [intents] that an activity handles; returns whether one
 * started. Trying them in turn (instead of resolving first) also works without
 * package-visibility queries for the Settings app.
 */
fun Context.startFirstPermissionStatusIntent(intents: List<Intent>): Boolean = intents.any { intent ->
    if (this !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
