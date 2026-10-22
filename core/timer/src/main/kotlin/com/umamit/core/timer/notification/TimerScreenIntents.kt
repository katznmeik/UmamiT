package com.umamit.core.timer.notification

import android.content.Context
import android.content.Intent

/**
 * The intent that opens the timer screen (SCR-05) from a notification: the content intent of
 * every timer notification and the alert's full-screen intent (03 §2.4, 04 §8.2; D-064). The
 * app's single activity handles [ACTION_OPEN_TIMER]: it opens the timer route and, while a
 * session is active, shows it over the lock screen (and turns the screen on for the alert)
 * before its first frame. `core:timer` does not know the activity: the intent names the app's
 * launcher activity explicitly, with this action, so no intent filter is needed (D-064). Only
 * without a launcher activity (the timer's test APK) it falls back to the action within this
 * package.
 */
object TimerScreenIntents {
    /** Handled by the app's activity that hosts the timer route. */
    const val ACTION_OPEN_TIMER = "com.umamit.action.OPEN_TIMER"

    /** Opens the timer screen of this app, in its task (or a new one) without a second copy on top. */
    fun openTimer(context: Context): Intent {
        val intent = Intent(ACTION_OPEN_TIMER)
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.component?.let(intent::setComponent)
        return intent
    }

    /** Whether [intent] asks for the timer screen. */
    fun isOpenTimer(intent: Intent?): Boolean = intent?.action == ACTION_OPEN_TIMER
}
