package com.umamit.feature.settings.backup

import androidx.compose.runtime.saveable.Saver

/**
 * An import just done (BAK-02 AC6): Start shows „Sicherung eingespielt · 142 Sessions, 23 Tees" with *Rückgängig
 * machen* (03 SCR-26, D-130 item 22).
 */
data class ImportedBackup(val sessions: Int, val teas: Int) {
    companion object {
        /** For `rememberSaveable`: the snackbar survives the activity's re-creation. */
        val Saver: Saver<ImportedBackup?, List<Int>> = Saver(
            save = { it?.let { imported -> listOf(imported.sessions, imported.teas) } },
            restore = { ImportedBackup(it[0], it[1]) },
        )
    }
}
