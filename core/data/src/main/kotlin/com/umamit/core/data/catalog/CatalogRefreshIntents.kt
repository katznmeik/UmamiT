package com.umamit.core.data.catalog

import android.content.Context
import android.content.Intent

/**
 * The intent that opens Sammlung › Katalog with the refresh sheet (SCR-22) from the refresh's
 * progress and result notifications (03 §2.4, D-097; until v0.2.1 they opened the app where it
 * was, D-079). The sheet shows whatever the refresh holds: its progress while it runs, its
 * summary after it, the last one from the refresh log when the app was closed since.
 *
 * Only this app's own intents open it (#113, #121, 06 §5.7): the notifications' tap goes to the
 * app's trampoline [TAP_ACTIVITY], which is not exported and has no intent filter, so no other
 * app can start it. It lets the tap through to the activity that hosts the shell, in the same
 * process; that activity is exported as the launcher and takes the action only when let through.
 * `core:data` does not know the app's classes, so the trampoline is named by its class name
 * (the app's tests pin it). The app's root decides when the sheet shows: never over an alert,
 * never before the first-start setup is done (rule 8, ONB-03).
 */
object CatalogRefreshIntents {
    /** The refresh sheet: the notifications' tap, and the trampoline's hand-over to the shell's activity. */
    const val ACTION_OPEN_REFRESH = "com.umamit.action.OPEN_CATALOG_REFRESH"

    /** The app's non-exported trampoline for the notifications' tap. */
    const val TAP_ACTIVITY = "com.umamit.app.CatalogRefreshTapActivity"

    /** The notifications' tap: the app's trampoline, in a task of its own that it leaves at once. */
    fun openRefresh(context: Context): Intent = Intent(ACTION_OPEN_REFRESH)
        .setPackage(context.packageName)
        .setClassName(context.packageName, TAP_ACTIVITY)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Whether [intent] asks for the refresh sheet. */
    fun isOpenRefresh(intent: Intent?): Boolean = intent?.action == ACTION_OPEN_REFRESH
}
