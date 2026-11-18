package com.umamit.app

import android.os.SystemClock
import com.umamit.core.data.reminder.OpenBackupIntents

/**
 * The taps on the backup reminder (BAK-06, 03 §2.4, D-130 Q11), as [MainActivity] keeps them: a new token per tap,
 * pending until the root has shown *Daten & Sicherung* (SCR-26) for it (or let it go, before the setup is done). A
 * pending tap survives the activity's recreation; a task reopened from the recent apps brings its first intent
 * again, which was handled then.
 *
 * No gate, as for [StartRequests]: the action only opens the app at a screen any user can reach anyway.
 */
class BackupRequests(newToken: () -> Long = SystemClock::elapsedRealtimeNanos) :
    IntentRequests(KEY_PENDING, OpenBackupIntents::isOpenBackup, newToken)

private const val KEY_PENDING = "com.umamit.app.PENDING_BACKUP_REQUEST"
