package com.umamit.feature.journal

import com.umamit.core.data.JournalSession
import com.umamit.core.domain.search.SearchQuery
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
 * „55 °C · 1:00 · 3 Aufgüsse" (the first infusion's temperature and time · count), or
 * „2 von 3 Aufgüssen" when fewer infusions were poured than planned (TMR-12).
 *
 * @property temperatureC the first infusion's temperature as brewed (its own, else the session's base; D-102).
 * @property infusions poured infusions: ended steps that are neither a rinse nor skipped.
 * @property plannedInfusions the non-rinse steps of the plan, never-started ones included.
 * @property rating 0,5–5,0 in half steps; null when unrated (JRN-02, JRN-08).
 * @property hasNotes the session has note text, its own or an infusion's (JRN-09's *Mit Notizen*, D-114).
 * @property teaKey the tea filter's key (JRN-09): the tea's id, or `name:` and the recorded name for a deleted tea.
 * @property searchWords the normalized words of the tea name, the notes, the infusion notes (D-114) and the chosen
 *   descriptors' labels (GEN-10), built once with the list so a keystroke only compares them.
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
    val rating: Double? = null,
    val hasNotes: Boolean = false,
    val teaKey: String = teaKeyOf(null, teaName),
    val searchWords: List<String> = emptyList(),
) {
    val timeOfDay: LocalTime get() = startedAt.toLocalTime().withSecond(0).withNano(0)
    val finishedEarly: Boolean get() = infusions < plannedInfusions
}

internal const val DELETED_TEA_PREFIX = "name:"

/** The tea filter's key of a session's tea: its id, or its recorded name once the tea is deleted (JRN-09). */
fun teaKeyOf(teaId: String?, teaName: String): String = teaId ?: "$DELETED_TEA_PREFIX$teaName"

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
 * (E13), and shows its time of day in that zone; [today] is the phone's date now. [descriptorLabel] is a
 * descriptor key's German label (null for a key the app does not know), for the search (JRN-09).
 */
fun journalList(
    sessions: List<JournalSession>,
    today: LocalDate,
    descriptorLabel: (key: String) -> String? = { null },
): JournalList {
    val entries = sessions.map { it.toEntry(descriptorLabel) }
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

private fun JournalSession.toEntry(descriptorLabel: (String) -> String?): JournalEntry {
    val inProgress = session.status == SessionStatus.IN_PROGRESS
    // A finished session counts only its ended rows, also when it keeps its never-started ones (from
    // v0.4.0, Q-19); its plan lives on in the planned count (TMR-12).
    val rows = if (inProgress) session.steps else session.endedSteps
    val infusionRows = rows.filter { it.kind != StepKind.RINSE }
    val poured = infusionRows.count { it.poured }
    val planned = if (inProgress) {
        infusionRows.size
    } else {
        session.plannedStepCount - rows.count { it.kind == StepKind.RINSE }
    }
    return JournalEntry(
        sessionId = session.id,
        teaName = session.snapshot.teaName,
        styleKey = session.snapshot.styleKey,
        photo = coverPhoto,
        startedAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(session.startedAtMs), zoneOf(session.zoneId)),
        // The first infusion as brewed: a step warmer or cooler than the base (Schritte-Sheet) shows its own
        // temperature, not the recipe's base (D-102).
        temperatureC = infusionRows.firstOrNull()?.let { it.tempC ?: session.snapshot.baseTempC }
            ?: session.snapshot.baseTempC,
        firstInfusionSeconds = infusionRows.firstOrNull()?.plannedSeconds,
        infusions = poured,
        plannedInfusions = maxOf(planned, poured),
        inProgress = inProgress,
        rating = session.rating,
        hasNotes = !session.notes.isNullOrBlank() || session.hasInfusionNotes,
        teaKey = teaKeyOf(session.snapshot.teaId, session.snapshot.teaName),
        searchWords = SearchQuery.words(
            buildString {
                append(session.snapshot.teaName)
                session.notes?.let { append(' ').append(it) }
                session.infusions.forEach { infusion -> infusion.note?.let { append(' ').append(it) } }
                session.descriptorKeys.forEach { key -> descriptorLabel(key)?.let { append(' ').append(it) } }
            },
        ),
    )
}

/** The „Gespeichert" sheet's summary (SCR-06): poured infusions and their steeping time together. */
data class SavedSummary(val infusions: Int, val steepSeconds: Int)

/** [SavedSummary] of [session]: poured rows are ended, so never-started ones (kept from v0.4.0, Q-19) don't count. */
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
