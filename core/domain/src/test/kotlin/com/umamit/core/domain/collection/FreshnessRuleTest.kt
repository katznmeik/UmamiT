package com.umamit.core.domain.collection

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.Freshness
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import org.junit.Test
import java.time.LocalDate

/** The freshness label (INV-06, D-104): thirds of the style's window from the opening date, or a passed best-before. */
class FreshnessRuleTest {
    private val today = LocalDate.of(2026, 11, 8)

    private fun gyokuroOpened(daysAgo: Long) = FreshnessRule.of(
        today.minusDays(daysAgo),
        bestBefore = null,
        windowDays = 60,
        remainingGrams = 30.0,
        today = today,
    )

    @Test
    fun `a Gyokuro of 60 days is Frisch on days 0 to 39, Bald geniessen on 40 to 59, past its peak from 60`() {
        assertThat(gyokuroOpened(0)).isEqualTo(Freshness.FRESH)
        assertThat(gyokuroOpened(39)).isEqualTo(Freshness.FRESH)
        assertThat(gyokuroOpened(40)).isEqualTo(Freshness.DRINK_SOON)
        assertThat(gyokuroOpened(59)).isEqualTo(Freshness.DRINK_SOON)
        assertThat(gyokuroOpened(60)).isEqualTo(Freshness.PAST_PEAK)
        assertThat(gyokuroOpened(400)).isEqualTo(Freshness.PAST_PEAK)
    }

    @Test
    fun `the thirds are whole days rounded down`() {
        // 45 days (Sencha): two thirds are 30 days. 50 days: 33,3 → 33.
        fun opened(daysAgo: Long, window: Int) =
            FreshnessRule.of(today.minusDays(daysAgo), null, window, remainingGrams = null, today = today)
        assertThat(opened(29, 45)).isEqualTo(Freshness.FRESH)
        assertThat(opened(30, 45)).isEqualTo(Freshness.DRINK_SOON)
        assertThat(opened(44, 45)).isEqualTo(Freshness.DRINK_SOON)
        assertThat(opened(45, 45)).isEqualTo(Freshness.PAST_PEAK)
        assertThat(opened(32, 50)).isEqualTo(Freshness.FRESH)
        assertThat(opened(33, 50)).isEqualTo(Freshness.DRINK_SOON)
    }

    @Test
    fun `a passed best-before date is past its peak, opened or not`() {
        val yesterday = today.minusDays(1)
        assertThat(FreshnessRule.of(null, yesterday, 60, 50.0, today)).isEqualTo(Freshness.PAST_PEAK)
        assertThat(FreshnessRule.of(today.minusDays(3), yesterday, 60, 50.0, today)).isEqualTo(Freshness.PAST_PEAK)
        // On the best-before date itself the tea is still good.
        assertThat(FreshnessRule.of(null, today, 60, 50.0, today)).isNull()
        assertThat(FreshnessRule.of(today.minusDays(3), today, 60, 50.0, today)).isEqualTo(Freshness.FRESH)
    }

    @Test
    fun `an unopened tea has no label`() {
        assertThat(FreshnessRule.of(null, null, 60, 50.0, today)).isNull()
        assertThat(FreshnessRule.of(null, today.plusDays(90), 60, 50.0, today)).isNull()
    }

    @Test
    fun `an empty tea has no label, even past its window or best-before`() {
        assertThat(FreshnessRule.of(today.minusDays(5), null, 60, 0.0, today)).isNull()
        assertThat(FreshnessRule.of(today.minusDays(90), today.minusDays(1), 60, 0.0, today)).isNull()
        // Below 0,05 g reads „Leer" (INV-05).
        assertThat(FreshnessRule.of(today.minusDays(5), null, 60, 0.04, today)).isNull()
        assertThat(FreshnessRule.of(today.minusDays(5), null, 60, 0.05, today)).isEqualTo(Freshness.FRESH)
    }

    @Test
    fun `a tea without stock events is not empty`() {
        assertThat(FreshnessRule.of(today.minusDays(5), null, 60, remainingGrams = null, today = today))
            .isEqualTo(Freshness.FRESH)
    }

    @Test
    fun `a style without a window has no label from the opening date`() {
        assertThat(FreshnessRule.of(today.minusDays(500), null, windowDays = null, 50.0, today)).isNull()
    }

    @Test
    fun `the tea's style gives the window, and its own freshness_days is ignored`() {
        fun tea(style: String?) = Tea(
            id = "t",
            name = "Mondschatten",
            category = TeaCategory.GREEN,
            form = TeaForm.LOOSE_LEAF,
            styleKey = style,
            openedDate = today.minusDays(40),
            freshnessDays = 365,
        )
        // Gyokuro 60 days → day 40 is Bald genießen; Shincha 30 days → past; Andere / unbekannt has no window.
        assertThat(FreshnessRule.of(tea("gyokuro"), 20.0, today)).isEqualTo(Freshness.DRINK_SOON)
        assertThat(FreshnessRule.of(tea("shincha"), 20.0, today)).isEqualTo(Freshness.PAST_PEAK)
        assertThat(FreshnessRule.of(tea("other"), 20.0, today)).isNull()
        assertThat(FreshnessRule.of(tea(null), 20.0, today)).isNull()
    }
}
