package com.umamit.feature.journal

import com.umamit.core.data.JournalSession
import com.umamit.core.model.BrewSession
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.SessionStepRow
import com.umamit.core.model.StepKind
import java.io.File
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * One journal row (JRN-08, mockup v2): tea, time of day and the meta line
 * „55 °C · 1:00 · 3 Aufgüsse" (base temperature · first infusion · count), or
 * „2 von 3 Aufgüssen" when fewer infusions were poured than planned (TMR-12).
 *
 * @property infusions poured infusions: ended steps that are neither a rinse nor skipped.
 * @property plannedInfusions the non-rinse steps of the plan, never-started ones included.
 */
data class JournalEntry(
    val sessionId: String,
    val teaName: String,
    val styleKey: String?,
    val photo: File?,
    val startedAt: LocalDateTime,
    val temperatureC: Int?,
    val firstInfusionSeconds: Int?,
    val infusions: Int,
    val plannedInfusions: Int,
    val inProgress: Boolean,
) {
    val timeOfDay: LocalTime get() = startedAt.toLocalTime().withSecond(0).withNano(0)
    val finishedEarly: Boolean get() = infusions < plannedInfusions
}

/** A day group's header: „Heute", „Gestern", or the date („Montag, 12. Oktober"). */
sealed interface JournalDay {
    data object Today : JournalDay

    data object Yesterday : JournalDay

    /** [thisYear]: the date is in today's year, so the header leaves the year out. */
    data class On(val date: LocalDate, val thisYear: Boolean = true) : JournalDay
}

data class JournalDayGroup(val day: JournalDay, val entries: List<JournalEntry>)

/** The journal: the in-progress session(s) pinned on top, then the finished sessions by day, newest first. */
data class JournalList(val inProgress: List<JournalEntry>, val days: List<JournalDayGroup>) {
    val isEmpty: Boolean get() = inProgress.isEmpty() && days.isEmpty()

    companion object {
        val EMPTY = JournalList(emptyList(), emptyList())
    }
}

/**
 * Builds the journal from the repository's sessions. A session belongs to the
 * local date of its start in its own time zone, also when it ran past midnight
 * (E13), and shows its time of day in that zone; [today] is the phone's date now.
 */
fun journalList(sessions: List<JournalSession>, today: LocalDate): JournalList {
    val entries = sessions.map { it.toEntry() }
    val (running, finished) = entries.partition { it.inProgress }
    val days = finished
        .groupBy { it.startedAt.toLocalDate() }
        .toSortedMap(compareByDescending { it })
        .map { (date, rows) -> JournalDayGroup(dayOf(date, today), rows.sortedByDescending { it.startedAt }) }
    return JournalList(running.sortedByDescending { it.startedAt }, days)
}

private fun dayOf(date: LocalDate, today: LocalDate): JournalDay = when (date) {
    today -> JournalDay.Today
    today.minusDays(1) -> JournalDay.Yesterday
    else -> JournalDay.On(date, thisYear = date.year == today.year)
}

private fun JournalSession.toEntry(): JournalEntry {
    val infusionRows = session.steps.filter { it.kind != StepKind.RINSE }
    val poured = infusionRows.count { it.poured }
    val inProgress = session.status == SessionStatus.IN_PROGRESS
    // A finished session keeps only its ended rows; its plan lives on in the planned count (TMR-12).
    val planned = if (inProgress) {
        infusionRows.size
    } else {
        session.plannedStepCount - session.steps.count { it.kind == StepKind.RINSE }
    }
    return JournalEntry(
        sessionId = session.id,
        teaName = session.snapshot.teaName,
        styleKey = session.snapshot.styleKey,
        photo = coverPhoto,
        startedAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(session.startedAtMs), zoneOf(session.zoneId)),
        temperatureC = session.snapshot.baseTempC ?: infusionRows.firstNotNullOfOrNull { it.tempC },
        firstInfusionSeconds = infusionRows.firstOrNull()?.plannedSeconds,
        infusions = poured,
        plannedInfusions = maxOf(planned, poured),
        inProgress = inProgress,
    )
}

/** The „Gespeichert" sheet's summary (SCR-06): poured infusions and their steeping time together. */
data class SavedSummary(val infusions: Int, val steepSeconds: Int)

fun savedSummary(session: BrewSession): SavedSummary {
    val poured = session.steps.filter { it.kind != StepKind.RINSE && it.poured }
    return SavedSummary(poured.size, poured.sumOf { it.actualSeconds ?: 0 })
}

private val SessionStepRow.poured: Boolean get() = actualSeconds != null && !skipped

/** The session's zone; one this device doesn't know counts as the device's (as in the repository, D-054). */
private fun zoneOf(id: String): ZoneId = try {
    ZoneId.of(id)
} catch (_: DateTimeException) {
    ZoneId.systemDefault()
}
