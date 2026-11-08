package com.umamit.core.domain.collection

import com.umamit.core.domain.style.BuiltInStyles
import com.umamit.core.model.Freshness
import com.umamit.core.model.StockLevel
import com.umamit.core.model.Tea
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The freshness label of a tea (INV-06, D-104, 08 §3): counted from the opening date over the style's window,
 * *Frisch* for the first two thirds (whole days, rounded down: Gyokuro's 60 days give *Frisch* on days 0–39),
 * *Bald genießen* for the last third, *Über dem Höhepunkt* from the window's end. A passed best-before date makes
 * a tea *Über dem Höhepunkt*, opened or not. No label for an empty tea, an unopened one (best-before not passed),
 * or a style without a window. There is no per-tea period (`freshness_days` keeps no UI, D-100).
 */
object FreshnessRule {
    private const val THIRDS = 3
    private const val FRESH_THIRDS = 2

    /**
     * The label, or null for none.
     *
     * @param windowDays the style's freshness window (`TeaStyle.freshnessDays`); null for a style without one.
     * @param remainingGrams the stock (Σ of the stock events); null without any, which is not empty (D-100).
     */
    fun of(
        openedDate: LocalDate?,
        bestBefore: LocalDate?,
        windowDays: Int?,
        remainingGrams: Double?,
        today: LocalDate,
    ): Freshness? {
        val day = openedDate?.let { ChronoUnit.DAYS.between(it, today) }
        return when {
            remainingGrams != null && remainingGrams < StockLevel.EMPTY_BELOW_GRAMS -> null
            bestBefore != null && today.isAfter(bestBefore) -> Freshness.PAST_PEAK
            day == null || windowDays == null || windowDays <= 0 -> null
            day < windowDays * FRESH_THIRDS / THIRDS -> Freshness.FRESH
            day < windowDays -> Freshness.DRINK_SOON
            else -> Freshness.PAST_PEAK
        }
    }

    /** The label of [tea] by its style's window (`BuiltInStyles`). */
    fun of(tea: Tea, remainingGrams: Double?, today: LocalDate): Freshness? = of(
        openedDate = tea.openedDate,
        bestBefore = tea.bestBefore,
        windowDays = tea.styleKey?.let(BuiltInStyles::byKey)?.freshnessDays,
        remainingGrams = remainingGrams,
        today = today,
    )
}
