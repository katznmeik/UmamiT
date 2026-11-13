package com.umamit.core.domain.stats

import com.umamit.core.domain.collection.CupAverage
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.StockLevel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToLong

/**
 * The figures of the Statistik screen (02 §12, INS-01–INS-07, D-115), one function per tab. Pure: today and the
 * zone come from [clock], never read elsewhere. Each session belongs to the day of its `started_at` in its own
 * `zone_id`; teas' `created_at` and purchases' `occurred_at` are dated in the clock's zone. Sessions dated after
 * today are not counted.
 *
 * Build one per [StatsInput] (the sessions are dated once), then ask it for any range and tab.
 */
class StatsCalculator(private val input: StatsInput, clock: Clock) {
    private val today: LocalDate = LocalDate.now(clock)
    private val zone: ZoneId = clock.zone

    private val sessions: List<DatedSession> = input.sessions.mapNotNull { session ->
        val local = Instant.ofEpochMilli(session.startedAtMs).atZone(ZoneId.of(session.zoneId))
        val day = local.toLocalDate()
        if (day > today) null else DatedSession(session, day, local.toLocalTime(), session.cups)
    }

    private val firstSessionDay: LocalDate? = sessions.minOfOrNull { it.day }

    /** Whether there is any finished session at all; without one the screen shows A9 and no tabs (INS-08). */
    val hasSessions: Boolean get() = sessions.isNotEmpty()

    /** [range] resolved against today: its days and chart periods (INS-07). */
    fun window(range: StatsRange): StatsWindow = StatsWindow.of(range, today, firstSessionDay)

    /** The streak over all time (INS-01); independent of any range. */
    fun streak(): Streak = streakOf(sessions.mapTo(HashSet()) { it.day }, today)

    /** *Überblick* (INS-01, INS-03, INS-06). */
    fun overview(range: StatsRange): OverviewStats {
        val window = window(range)
        val inRange = sessions.filter { it.day in window }
        val activeTeas = input.teas.filter { !it.archived }
        return OverviewStats(
            sessionCount = inRange.size,
            cupCount = inRange.sumOf { it.cups },
            teaCount = activeTeas.size,
            teasAddedInRange = activeTeas.count { dayOf(it.createdAtMs) in window },
            sessionsPerPeriod = perPeriod(window, inRange, 0) { sum, _ -> sum + 1 },
            streak = streak(),
            mostBrewed = mostBrewed(inRange, teaNames()),
            sorten = sorten(inRange),
            dayParts = dayParts(inRange),
            weekdays = weekdays(inRange),
        )
    }

    /** *Bewertungen* (INS-02, INS-04). */
    fun ratings(range: StatsRange): RatingsStats {
        val window = window(range)
        val inRange = sessions.filter { it.day in window }
        val rated = inRange.filter { it.session.rating != null }
        val names = teaNames()
        return RatingsStats(
            average = rated.map { it.rating }.takeIf { it.isNotEmpty() }?.average(),
            ratedCount = rated.size,
            sessionCount = inRange.size,
            distribution = starBuckets(rated),
            topRated = topRated(rated, names),
            ratingPerPeriod = ratingPerPeriod(window, rated),
            bestRecipes = bestRecipes(rated, names),
            taste = taste(inRange),
            topDescriptors = topDescriptors(inRange, input.knownDescriptorKeys),
        )
    }

    /** *Verbrauch* (INS-05). */
    fun consumption(range: StatsRange): ConsumptionStats {
        val window = window(range)
        val inRange = sessions.filter { it.day in window }
        val purchases = input.inventoryEvents
            .filter { it.type in PRICED_TYPES && it.priceCents != null }
            .map { DatedPrice(dayOf(it.occurredAtMillis), it.priceCents ?: 0L) }
            .filter { it.day in window }
        return ConsumptionStats(
            grams = inRange.sumOf { it.session.leafGrams ?: 0.0 },
            gramsPerPeriod = perPeriod(window, inRange, 0.0) { sum, s -> sum + (s.session.leafGrams ?: 0.0) },
            cups = inRange.sumOf { it.cups },
            cupsPerPeriod = perPeriod(window, inRange, 0) { sum, s -> sum + s.cups },
            moneyCents = purchases.sumOf { it.cents },
            hasPurchases = purchases.isNotEmpty(),
            moneyPerPeriod = window.periods.mapIndexed { index, period ->
                PeriodValue(period, purchases.filter { window.periodIndex(it.day) == index }.sumOf { it.cents })
            },
            costPerCup = costPerCup(inRange),
            runningOut = runningOut(),
        )
    }

    private fun dayOf(epochMs: Long): LocalDate = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()

    /** The current names of the existing teas, by id. */
    private fun teaNames(): Map<String, String> = input.teas.associate { it.id to it.name }

    private fun costPerCup(inRange: List<DatedSession>): CupAverage? {
        val priced = inRange.filter { it.session.costCents != null }
        if (priced.isEmpty()) return null
        val cents = priced.sumOf { it.session.costCents ?: 0L }
        val cups = priced.sumOf { it.cups }
        return CupAverage(cupCents = (cents.toDouble() / cups).roundToLong(), sessionCount = priced.size)
    }

    private fun runningOut(): List<RunningOutTea> = input.teas
        .filter { !it.archived && it.lowStockWarning && it.stockLevel != StockLevel.AVAILABLE }
        .sortedWith(
            compareBy<StatsTea> { it.stockLevel != StockLevel.EMPTY }
                .thenBy { it.remainingGrams }
                .thenBy { it.name },
        )
        .map { RunningOutTea(it.id, it.name, it.remainingGrams, it.stockLevel) }

    private class DatedPrice(val day: LocalDate, val cents: Long)

    private companion object {
        val PRICED_TYPES = setOf(InventoryEventType.INITIAL, InventoryEventType.PURCHASE)
    }
}

/** A session with its day and local start time in its own zone, and its cups. */
internal class DatedSession(val session: StatsSession, val day: LocalDate, val time: LocalTime, val cups: Int) {
    /** The rating; only read on rated sessions. */
    val rating: Double get() = session.rating ?: 0.0
}

/** One value per period of [window], folded from the sessions in it. */
internal fun <T> perPeriod(
    window: StatsWindow,
    sessions: List<DatedSession>,
    zero: T,
    add: (T, DatedSession) -> T,
): List<PeriodValue<T>> {
    val values = MutableList(window.periods.size) { zero }
    sessions.forEach { session ->
        val index = window.periodIndex(session.day)
        if (index >= 0) values[index] = add(values[index], session)
    }
    return window.periods.mapIndexed { index, period -> PeriodValue(period, values[index]) }
}
