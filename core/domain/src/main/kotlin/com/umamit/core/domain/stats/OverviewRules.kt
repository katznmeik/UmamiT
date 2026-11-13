package com.umamit.core.domain.stats

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

// The rules of *Überblick* (INS-01, INS-03, INS-06, D-115).

private const val TOP = 5
private val MITTAG_FROM: LocalTime = LocalTime.of(11, 0)
private val NACHMITTAG_FROM: LocalTime = LocalTime.of(14, 0)
private val ABEND_FROM: LocalTime = LocalTime.of(18, 0)
private val MORGEN_FROM: LocalTime = LocalTime.of(5, 0)

/**
 * The streak over [days] (INS-01): the current run includes [today] when it has a session, else it ends
 * yesterday; the longest run of consecutive days up to today.
 */
internal fun streakOf(days: Set<LocalDate>, today: LocalDate): Streak {
    var day = if (today in days) today else today.minusDays(1)
    var current = 0
    while (day in days) {
        current++
        day = day.minusDays(1)
    }
    var longest = 0
    var run = 0
    var previous: LocalDate? = null
    days.filter { it <= today }.sorted().forEach { next ->
        run = if (previous?.plusDays(1) == next) run + 1 else 1
        longest = maxOf(longest, run)
        previous = next
    }
    return Streak(current = current, longest = longest)
}

/** The key a tea's sessions are grouped by: its id, else (a deleted tea) its snapshot name. */
internal fun StatsSession.teaKey(): String = teaId ?: "name:$teaName"

/** A tea group's name: the tea's current name, else its latest session's snapshot. */
internal fun List<DatedSession>.teaName(names: Map<String, String>): String {
    val latest = maxBy { it.session.startedAtMs }.session
    return latest.teaId?.let(names::get) ?: latest.teaName
}

internal fun mostBrewed(
    sessions: List<DatedSession>,
    names: Map<String, String>,
    teas: Map<String, StatsTea> = emptyMap(),
): List<TeaSessions> = sessions
    .groupBy { it.session.teaKey() }
    .values
    .map { group ->
        val teaId = group.first().session.teaId
        val tea = teaId?.let(teas::get)
        TeaSessions(
            teaId = teaId,
            teaName = group.teaName(names),
            sessions = group.size,
            coverPhoto = tea?.coverPhoto,
            styleKey = tea?.styleKey ?: group.maxBy { it.session.startedAtMs }.session.styleKey,
        )
    }
    .sortedWith(compareByDescending<TeaSessions> { it.sessions }.thenBy { it.teaName })
    .take(TOP)

/** The donut (INS-03): the five largest Sorten, the rest and the sessions without a Sorte as „Andere". */
internal fun sorten(sessions: List<DatedSession>): List<SorteShare> {
    val bySorte = sessions.groupingBy { it.session.teaType?.trim()?.takeIf(String::isNotEmpty) }.eachCount()
    val named = bySorte.filterKeys { it != null }.entries
        .sortedWith(compareByDescending<Map.Entry<String?, Int>> { it.value }.thenBy { it.key })
    val shown = named.take(TOP).map { it.key to it.value }
    val andere = (bySorte[null] ?: 0) + named.drop(TOP).sumOf { it.value }
    val slices = if (andere > 0) shown + (null to andere) else shown
    val percents = wholePercentages(slices.map { it.second })
    return slices.mapIndexed { index, (sorte, count) -> SorteShare(sorte, count, percents[index]) }
}

internal fun dayPartOf(time: LocalTime): DayPart = when {
    time < MORGEN_FROM -> DayPart.ABEND
    time < MITTAG_FROM -> DayPart.MORGEN
    time < NACHMITTAG_FROM -> DayPart.MITTAG
    time < ABEND_FROM -> DayPart.NACHMITTAG
    else -> DayPart.ABEND
}

internal fun dayParts(sessions: List<DatedSession>): List<Share<DayPart>> =
    shares(DayPart.entries, sessions.groupingBy { dayPartOf(it.time) }.eachCount())

internal fun weekdays(sessions: List<DatedSession>): List<Share<DayOfWeek>> =
    shares(DayOfWeek.entries, sessions.groupingBy { it.day.dayOfWeek }.eachCount())

private fun <K> shares(keys: List<K>, counts: Map<K, Int>): List<Share<K>> {
    val values = keys.map { counts[it] ?: 0 }
    val percents = wholePercentages(values)
    return keys.mapIndexed { index, key -> Share(key, values[index], percents[index]) }
}
