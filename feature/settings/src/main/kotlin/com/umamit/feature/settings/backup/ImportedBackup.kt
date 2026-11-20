package com.umamit.feature.settings.backup

/**
 * An import just done (BAK-02 AC6): Start shows „Sicherung eingespielt · 142 Sessions, 23 Tees" with *Rückgängig
 * machen* (03 SCR-26, D-130 item 22).
 */
data class ImportedBackup(val sessions: Int, val teas: Int)
