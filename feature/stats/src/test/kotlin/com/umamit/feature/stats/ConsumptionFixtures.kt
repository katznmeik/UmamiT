package com.umamit.feature.stats

import com.umamit.core.domain.collection.CupAverage
import com.umamit.core.domain.stats.ConsumptionStats
import com.umamit.core.domain.stats.PeriodValue
import com.umamit.core.domain.stats.RunningOutTea
import com.umamit.core.model.StockLevel

/** Invented *Verbrauch* results for the tab's tests and goldens (no shop texts, D-016). */
internal object ConsumptionFixtures {
    private val RUNNING_OUT = listOf(
        RunningOutTea("t1", "Hisui Gyokuro", 0.0, StockLevel.EMPTY, styleKey = "gyokuro"),
        RunningOutTea("t2", "Morgentau Sencha", 4.5, StockLevel.LOW, styleKey = "sencha"),
        RunningOutTea("t3", "Röstduft Hojicha", 12.0, StockLevel.LOW, styleKey = "hojicha"),
    )

    private fun grams(periods: List<PeriodValue<Int>>) = periods.map { PeriodValue(it.period, it.value * 1.5) }

    private fun cents(periods: List<PeriodValue<Int>>) =
        periods.map { PeriodValue(it.period, if (it.value % 3 == 0) 0L else it.value * 120L) }

    fun data(
        periods: List<PeriodValue<Int>> = OverviewFixtures.weekly(),
        grams: Double = 38.0,
        hasPurchases: Boolean = true,
        costPerCup: CupAverage? = CupAverage(cupCents = 27, sessionCount = 20),
        drunkSessions: Int = 20,
        runningOut: List<RunningOutTea> = RUNNING_OUT,
    ) = ConsumptionStats(
        grams = grams,
        gramsPerPeriod = grams(periods),
        cups = 96,
        cupsPerPeriod = periods,
        moneyCents = if (hasPurchases) 6480 else 0,
        hasPurchases = hasPurchases,
        moneyPerPeriod = if (hasPurchases) cents(periods) else periods.map { PeriodValue(it.period, 0L) },
        costPerCup = costPerCup,
        drunkCents = if (drunkSessions > 0) 2340L else 0L,
        drunkSessionCount = drunkSessions,
        drunkPerPeriod = periods.map { PeriodValue(it.period, if (drunkSessions > 0) it.value * 45L else 0L) },
        runningOut = runningOut,
    )

    fun emptyRange() = data(
        periods = OverviewFixtures.weekly().map { PeriodValue(it.period, 0) },
        grams = 0.0,
        hasPurchases = false,
        costPerCup = null,
        drunkSessions = 0,
    ).copy(cups = 0)
}
