package com.umamit.core.data.reminder

import android.content.Context
import android.content.Intent

/**
 * The intent that opens *Daten & Sicherung* (SCR-26) from the backup reminder (BAK-06, 03 §2.4, D-130 Q11). Like
 * [OpenStartIntents], it names the app's launcher activity explicitly with the app's own action, so no intent filter
 * is needed; opening the app is what the launcher does anyway, and the root decides when SCR-26 shows (after a
 * running alert, never before the first-start setup is done: rule 8, ONB-03).
 */
object OpenBackupIntents {
    /** SCR-26: the reminder's tap. */
    const val ACTION_OPEN_BACKUP = "com.umamit.action.OPEN_BACKUP"

    /** The reminder's tap: the app's task (or a new one), without a second copy of the activity on top. */
    fun openBackup(context: Context): Intent = Intent(ACTION_OPEN_BACKUP)
        .setClassName(context.packageName, OpenStartIntents.LAUNCHER_ACTIVITY)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /** Whether [intent] asks for SCR-26. */
    fun isOpenBackup(intent: Intent?): Boolean = intent?.action == ACTION_OPEN_BACKUP
}
