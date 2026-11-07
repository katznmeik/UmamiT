package com.umamit.core.data

import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the completion of session [sessionId] did to its tea's *Geöffnet am* (INV-06, SCR-06): it set
 * [openedDate] on tea [teaId], whose value before was [previous] (none: the tea was unopened).
 */
data class OpenedDateChange(
    val sessionId: String,
    val teaId: String,
    val previous: LocalDate?,
    val openedDate: LocalDate,
)

/**
 * The opening dates the latest completions set, in memory only (SCR-06, D-104): the „Gespeichert"
 * sheet reads one within its 10 s, so nothing needs to survive the process. One per session, at
 * most [CAPACITY]; the oldest goes first. Recording is a map write that never blocks or fails, as
 * it runs inside the controller's transition (rule 8).
 */
@Singleton
class OpenedDateChanges @Inject constructor() {
    private val changes = object : LinkedHashMap<String, OpenedDateChange>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, OpenedDateChange>?) = size > CAPACITY
    }

    @Synchronized
    fun record(change: OpenedDateChange) {
        changes[change.sessionId] = change
    }

    @Synchronized
    fun of(sessionId: String): OpenedDateChange? = changes[sessionId]

    @Synchronized
    fun forget(sessionId: String) {
        changes.remove(sessionId)
    }

    private companion object {
        /** Two slots can finish within a sheet's 10 s; a few more cost nothing. */
        const val CAPACITY = 8
    }
}
