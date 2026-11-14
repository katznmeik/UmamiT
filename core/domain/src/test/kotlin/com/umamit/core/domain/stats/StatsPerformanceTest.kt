package com.umamit.core.domain.stats

import com.google.common.truth.Truth.assertThat
import com.umamit.core.domain.stats.StatsFixtures.TODAY
import com.umamit.core.domain.stats.StatsFixtures.calculator
import com.umamit.core.domain.stats.StatsFixtures.infusion
import com.umamit.core.domain.stats.StatsFixtures.purchase
import com.umamit.core.domain.stats.StatsFixtures.session
import com.umamit.core.domain.stats.StatsFixtures.tea
import com.umamit.core.model.MonthSpan
import com.umamit.core.model.StatsRange
import com.umamit.core.model.StepKind
import com.umamit.core.model.StockLevel
import org.junit.Test
import java.time.LocalTime
import java.time.YearMonth

/**
 * NFR-PERF-06: the statistics compute within 500 ms for 10 000 sessions. Dating the sessions and all three tabs for
 * one range are timed together, over warmed-up runs (wall-clock, so the median, with slack for a loaded runner).
 */
class StatsPerformanceTest {
    private val sessions = List(SESSION_COUNT) { index ->
        val tea = index % TEA_COUNT
        session(
            day = TODAY.minusDays((index / SESSIONS_PER_DAY).toLong()),
            time = LocalTime.of(index % HOURS_PER_DAY, 0),
            teaId = "t$tea",
            teaName = "Tee $tea",
            teaType = SORTEN[index % SORTEN.size],
            profileId = "r${index % 3}",
            profileName = "Rezept ${index % 3}",
            rating = if (index % 2 == 0) 1.0 + index % 9 * 0.5 else null,
            leafGrams = 5.0,
            costCents = if (index % 3 == 0) 120L else null,
            steps = listOf(
                StatsStep(StepKind.RINSE, ended = true),
                infusion(),
                infusion(),
                StatsStep(StepKind.INFUSION, ended = false),
            ),
        )
    }
    private val teas = List(TEA_COUNT) { tea("t$it", grams = it.toDouble(), level = StockLevel.LOW) }
    private val events = List(TEA_COUNT * 4) { purchase(TODAY.minusDays(it.toLong() * 3), 990) }

    @Test
    fun `all three tabs for 10 000 sessions within 500 ms`() {
        val span = MonthSpan(YearMonth.of(2026, 1), YearMonth.of(2026, 9))
        fun all(range: StatsRange) = calculator(sessions, teas, events).run {
            Triple(overview(range, span), ratings(range, span), consumption(range, span))
        }
        repeat(WARM_UP) { all(StatsRange.ALL) }

        val millis = List(RUNS) { run ->
            val range = StatsRange.entries[run % StatsRange.entries.size]
            val start = System.nanoTime()
            all(range)
            (System.nanoTime() - start) / NANOS_PER_MILLI
        }.sorted()

        assertThat(all(StatsRange.ALL).first.sessionCount).isEqualTo(SESSION_COUNT)
        println("StatsPerformanceTest: median ${millis[RUNS / 2]} ms, max ${millis.last()} ms")
        assertThat(millis[RUNS / 2]).isLessThan(BUDGET_MS)
    }

    private companion object {
        const val SESSION_COUNT = 10_000
        const val SESSIONS_PER_DAY = 3
        const val HOURS_PER_DAY = 24
        const val TEA_COUNT = 60
        const val WARM_UP = 5
        const val RUNS = 12
        const val NANOS_PER_MILLI = 1_000_000L
        const val BUDGET_MS = 500L
        val SORTEN = listOf("Sencha", "Gyokuro", "Matcha", "Bancha", "Kabusecha", "Genmaicha", "Hojicha")
    }
}
