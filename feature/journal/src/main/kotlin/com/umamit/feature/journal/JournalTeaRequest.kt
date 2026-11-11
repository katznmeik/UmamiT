package com.umamit.feature.journal

/**
 * A request to show the journal's list for one tea (TEA-13's *Alle anzeigen*, JRN-09): [teaKey] is the tea's id (as
 * in [JournalEntry.teaKey]). Each request carries a new, increasing [token] (from 1), so asking again for the same tea
 * after its chip was cleared is a new request and applies again.
 */
data class JournalTeaRequest(val teaKey: String, val token: Int)
