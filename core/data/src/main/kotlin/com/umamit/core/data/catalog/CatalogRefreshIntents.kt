package com.umamit.core.data.catalog

import android.content.Context
import android.content.Intent

/**
 * The intent that opens Sammlung › Katalog with the refresh sheet (SCR-22) from the refresh's
 * progress and result notifications (03 §2.4, D-097; until v0.2.1 they opened the app where it
 * was, D-079). The sheet shows whatever the refresh holds: its progress while it runs, its
 * summary after it, the last one from the refresh log when the app was closed since.
 *
 * As the timer's `TimerScreenIntents` (D-064): `core:data` does not know the activity, so the
 * intent names the app's launcher activity explicitly with this action, and no intent filter
 * is needed. The app's root decides when it shows: never over an alert, never before the
 * first-start setup is done (rule 8, ONB-03).
 */
object CatalogRefreshIntents {
    /** Handled by the app's activity that hosts the shell. */
    const val ACTION_OPEN_REFRESH = "com.umamit.action.OPEN_CATALOG_REFRESH"

    /** Opens the refresh sheet in this app's task (or a new one) without a second copy on top. */
    fun openRefresh(context: Context): Intent {
        val intent = Intent(ACTION_OPEN_REFRESH)
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.component?.let(intent::setComponent)
        return intent
    }

    /** Whether [intent] asks for the refresh sheet. */
    fun isOpenRefresh(intent: Intent?): Boolean = intent?.action == ACTION_OPEN_REFRESH
}
