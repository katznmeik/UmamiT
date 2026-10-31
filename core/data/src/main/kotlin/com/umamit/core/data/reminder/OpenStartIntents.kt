package com.umamit.core.data.reminder

import android.content.Context
import android.content.Intent

/**
 * The intent that opens Start (SCR-02) at its root from the daily low-stock reminder (INV-11, 03 §2.4, D-098).
 * Like the timer's `OPEN_TIMER`, it names the app's launcher activity explicitly with the app's own action, so
 * no intent filter is needed; `core:data` does not know the app's classes, so the activity is named by its
 * class name (the app's tests pin it). Opening Start is what the launcher does anyway, so the action needs no
 * gate against other apps. The app's root decides when Start shows: after a running alert, never before the
 * first-start setup is done (rule 8, ONB-03).
 */
object OpenStartIntents {
    /** Start at its root: the reminder's tap. */
    const val ACTION_OPEN_START = "com.umamit.action.OPEN_START"

    /** The app's launcher activity, which hosts the shell. */
    const val LAUNCHER_ACTIVITY = "com.umamit.app.MainActivity"

    /** The reminder's tap: the app's task (or a new one), without a second copy of the activity on top. */
    fun openStart(context: Context): Intent = Intent(ACTION_OPEN_START)
        .setClassName(context.packageName, LAUNCHER_ACTIVITY)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /** Whether [intent] asks for Start. */
    fun isOpenStart(intent: Intent?): Boolean = intent?.action == ACTION_OPEN_START
}
