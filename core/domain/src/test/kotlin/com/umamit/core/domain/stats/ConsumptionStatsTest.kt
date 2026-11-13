package com.umamit.core.domain.stats

import com.google.common.truth.Truth.assertThat
import com.umamit.core.domain.collection.CupAverage
import com.umamit.core.domain.stats.StatsFixtures.TODAY
import com.umamit.core.domain.stats.StatsFixtures.calculator
import com.umamit.core.domain.stats.StatsFixtures.infusion
import com.umamit.core.domain.stats.StatsFixtures.purchase
import com.umamit.core.domain.stats.StatsFixtures.session
import com.umamit.core.domain.stats.StatsFixtures.tea
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.StepKind
import com.umamit.core.model.StockLevel
import org.junit.Test

/** *Verbrauch* (INS-05) and the cups rule shared with INV-08's `CostRule`. */
class ConsumptionStatsTest {
    private val rinse = StatsStep(StepKind.RINSE, ended = true)
    private val whisk = StatsStep(StepKind.WHISK, ended = true)

    @Test
    fun `cups - one per poured non-rinse step, whisk-only, all-skipped and no rows are one, never-started rows none`() {
        assertThat(session(steps = listOf(rinse, infusion(), infusion())).cups).isEqualTo(2)
        assertThat(session(steps = listOf(whisk)).cups).isEqualTo(1)
        assertThat(session(steps = listOf(whisk, whisk)).cups).isEqualTo(1)
        val skipped = StatsStep(StepKind.INFUSION, ended = false, skipped = true)
        assertThat(session(steps = listOf(skipped, skipped)).cups).isEqualTo(1)
        assertThat(session(steps = listOf(rinse)).cups).isEqualTo(1)
        assertThat(session(steps = emptyList()).cups).isEqualTo(1)
        val neverStarted = StatsStep(StepKind.INFUSION, ended = false)
        assertThat(session(steps = listOf(infusion(), infusion(), neverStarted)).cups).isEqualTo(2)
        assertThat(session(steps = listOf(StatsStep(StepKind.INFUSION, ended = true, skipped = true))).cups)
            .isEqualTo(1)
    }

    @Test
    fun `grams and cups in total and per period, a session without leaf adds nothing`() {
        val sessions = listOf(
            session(leafGrams = 5.0, steps = listOf(infusion(), infusion())),
            session(leafGrams = null),
            session(TODAY.minusDays(28), leafGrams = 4.5),
            session(TODAY.minusDays(40), leafGrams = 100.0),
        )

        val use = calculator(sessions).consumption(StatsRange.DAYS_30)

        assertThat(use.grams).isWithin(1e-9).of(9.5)
        assertThat(use.cups).isEqualTo(4)
        assertThat(use.gramsPerPeriod.map { it.value }).containsExactly(4.5, 0.0, 0.0, 0.0, 5.0).inOrder()
        assertThat(use.cupsPerPeriod.map { it.value }).containsExactly(1, 0, 0, 0, 3).inOrder()
    }

    @Test
    fun `money is the priced INITIAL and PURCHASE events dated in the range`() {
        val events = listOf(
            purchase(TODAY, 1240, InventoryEventType.INITIAL),
            purchase(TODAY.minusDays(28), 990),
            purchase(TODAY.minusDays(3), null),
            purchase(TODAY.minusDays(40), 5000),
            purchase(TODAY, 777, InventoryEventType.ADJUSTMENT),
            purchase(TODAY, 888, InventoryEventType.BREW),
        )

        val use = calculator(events = events).consumption(StatsRange.DAYS_30)

        assertThat(use.moneyCents).isEqualTo(2230)
        assertThat(use.hasPurchases).isTrue()
        assertThat(use.moneyPerPeriod.map { it.value }).containsExactly(990L, 0L, 0L, 0L, 1240L).inOrder()
        assertThat(calculator(events = events).consumption(StatsRange.ALL).moneyCents).isEqualTo(7230)
        val none = calculator(events = listOf(purchase(TODAY, null))).consumption(StatsRange.DAYS_30)
        assertThat(none.hasPurchases).isFalse()
        assertThat(none.moneyCents).isEqualTo(0)
    }

    @Test
    fun `cost per cup only over the sessions with a cost`() {
        val sessions = listOf(
            session(costCents = 120, steps = listOf(rinse, infusion(), infusion(), infusion())),
            session(costCents = 60, steps = listOf(whisk)),
            session(costCents = null, steps = listOf(infusion(), infusion())),
            session(TODAY.minusDays(40), costCents = 9999),
        )

        val use = calculator(sessions).consumption(StatsRange.DAYS_30)

        // 180 ct over 4 cups.
        assertThat(use.costPerCup).isEqualTo(CupAverage(cupCents = 45, sessionCount = 2))
        assertThat(calculator(listOf(session())).consumption(StatsRange.DAYS_30).costPerCup).isNull()
    }

    @Test
    fun `running out lists Leer first, then by grams, only non-archived teas with the warning, ignoring the range`() {
        val teas = listOf(
            tea("a", name = "Knapp 12", grams = 12.0, level = StockLevel.LOW),
            tea("b", name = "Leer", grams = 0.0, level = StockLevel.EMPTY),
            tea("c", name = "Knapp 4", grams = 4.0, level = StockLevel.LOW),
            tea("d", name = "Voll", grams = 80.0, level = StockLevel.AVAILABLE),
            tea("e", name = "Ohne Warnung", grams = 1.0, level = StockLevel.LOW, warning = false),
            tea("f", name = "Archiviert", grams = 0.0, level = StockLevel.EMPTY, archived = true),
        )

        val out = calculator(teas = teas).consumption(StatsRange.DAYS_30).runningOut

        assertThat(out.map { it.teaName }).containsExactly("Leer", "Knapp 4", "Knapp 12").inOrder()
        assertThat(out.first().level).isEqualTo(StockLevel.EMPTY)
    }
}
