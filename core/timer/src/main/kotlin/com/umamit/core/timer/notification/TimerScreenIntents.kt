package com.umamit.core.timer.notification

import android.content.Context
import android.content.Intent

/**
 * The intent that opens the timer screen (SCR-05) from a notification: the content intent of
 * every timer notification and the alert's full-screen intent (03 §2.4, 04 §8.2; D-NNN). The
 * app's single activity handles [ACTION_OPEN_TIMER]: it opens the timer route and, while a
 * session is active, shows it over the lock screen (and turns the screen on for the alert)
 * before its first frame. `core:timer` does not know the activity, so the intent names the
 * action within this package; it resolves to the activity that declares it.
 */
object TimerScreenIntents {
    /** Declared by the app's activity that hosts the timer route. */
    const val ACTION_OPEN_TIMER = "com.umamit.action.OPEN_TIMER"

    /** Opens the timer screen of this app, in its task (or a new one) without a second copy on top. */
    fun openTimer(context: Context): Intent {
        val intent = Intent(ACTION_OPEN_TIMER)
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        // Explicit where the activity is known, so nothing else can ever receive it.
        context.packageManager.queryIntentActivities(intent, 0).firstOrNull()?.activityInfo?.let {
            intent.setClassName(it.packageName, it.name)
        }
        return intent
    }

    /** Whether [intent] asks for the timer screen. */
    fun isOpenTimer(intent: Intent?): Boolean = intent?.action == ACTION_OPEN_TIMER
}
