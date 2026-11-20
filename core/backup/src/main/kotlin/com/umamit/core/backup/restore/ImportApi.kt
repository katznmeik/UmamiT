package com.umamit.core.backup.restore

import com.umamit.core.backup.R
import com.umamit.core.backup.archive.BackupPreview

/** Why an import or its undo is refused (BAK-02, 03 §5); [messageRes] is its German text. Nothing was changed. */
enum class RestoreRefusal(val messageRes: Int) {
    /** A brew or the quick timer is active (05 §8 „Replace" step 1): „Während ein Timer läuft, …". */
    TIMER_ACTIVE(R.string.backup_refused_timer_active),

    /** A catalog refresh runs (D-130 build note of #336, the PO's answer): „Während der Katalog aktualisiert …". */
    CATALOG_REFRESH(R.string.backup_refused_catalog_refresh),

    /** The safety copy could not be written or did not read back (step 2): „Die Sicherheitskopie konnte nicht …". */
    SAFETY_COPY_FAILED(R.string.backup_refused_safety_copy),

    /** The replace failed before its commit, or an unfinished earlier one could not be finished: „Das Einspielen …". */
    REPLACE_FAILED(R.string.backup_refused_replace_failed),
}

/** How [RestoreBackup.restore] and [UndoImport.undo] end. */
sealed interface RestoreResult {
    /**
     * The data is replaced: „Sicherung eingespielt · 142 Sessions, 23 Tees" ([preview]'s counts) or „Import rückgängig
     * gemacht". With [finishedAtNextStart] a step after the commit failed (e.g. the settings write on a full disk): the
     * screen still says it is done, and the next app start finishes the rest; the undo is offered at once, counted
     * from the import, and refused while the rest still fails (D-130 build note, D-133 Q3).
     */
    data class Done(val preview: BackupPreview, val finishedAtNextStart: Boolean) : RestoreResult

    /** Not done; [refusal]'s text is shown and nothing changed. */
    data class Refused(val refusal: RestoreRefusal) : RestoreResult

    /** Undo only: there is no valid safety copy (none, or its 7 × 24 h are over); nothing changed. */
    data object NoSafetyCopy : RestoreResult
}

/**
 * The undo dialog's counts (D-130 Q6, item 21): [sessions] completed and manual sessions saved after the import
 * (`created_at`, D-133 Q9),
 * [teas] teas created or changed after it; both 0 is „Seit dem Import gibt es keine neuen Sessions und keine
 * geänderten Tees.". [availableUntil] (epoch ms) is „Möglich bis …": the import's time + 7 × 24 h.
 */
data class UndoPreview(val sessions: Int, val teas: Int, val availableUntil: Long)

/**
 * Whether a timer runs in memory, which `active_brew` may not show (a timer whose persist failed twice, D-054).
 * SCR-26's view model supplies it from the timer controller's slot states (#338); the import also checks `active_brew`
 * itself.
 */
fun interface TimerActivity {
    fun isActive(): Boolean
}
