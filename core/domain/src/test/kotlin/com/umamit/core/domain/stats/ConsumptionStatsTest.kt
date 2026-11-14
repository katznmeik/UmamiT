package com.umamit.core.domain.stats

import com.google.common.truth.Truth.assertThat
import com.umamit.core.domain.collection.LiterAverage
import com.umamit.core.domain.stats.StatsFixtures.TODAY
import com.umamit.core.domain.stats.StatsFixtures.calculator
import com.umamit.core.domain.stats.StatsFixtures.infusion
import com.umamit.core.domain.stats.StatsFixtures.purchase
import com.umamit.core.domain.stats.StatsFixtures.session
import com.umamit.core.domain.stats.StatsFixtures.tea
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.StatsRange
import com.umamit.core.model.StepKind
import com.umamit.core.model.StockLevel
import org.junit.Test

/** *Verbrauch* (INS-05) and the volume rule shared with INV-08's `CostRule`. */
class ConsumptionStatsTest {
    private val rinse = StatsStep(StepKind.RINSE, ended = true)
    private val whisk = StatsStep(StepKind.WHISK, ended = true)

    @Test
    fun `volume - water per poured non-rinse step, whisk-only once, nothing without water or poured steps`() {
        fun volume(steps: List<StatsStep>, water: Int? = 100) = session(steps = steps, waterMl = water).volumeMl
        assertThat(volume(listOf(rinse, infusion(), infusion()))).isEqualTo(200)
        assertThat(volume(listOf(whisk))).isEqualTo(100)
        assertThat(volume(listOf(whisk, whisk))).isEqualTo(100)
        val skipped = StatsStep(StepKind.INFUSION, ended = false, skipped = true)
        assertThat(volume(listOf(skipped, skipped))).isEqualTo(0)
        assertThat(volume(listOf(rinse))).isEqualTo(0)
        assertThat(volume(emptyList())).isEqualTo(0)
        val neverStarted = StatsStep(StepKind.INFUSION, ended = false)
        assertThat(volume(listOf(infusion(), infusion(), neverStarted))).isEqualTo(200)
        assertThat(volume(listOf(StatsStep(StepKind.INFUSION, ended = true, skipped = true)))).isEqualTo(0)
        assertThat(volume(listOf(infusion(), infusion()), water = null)).isEqualTo(0)
    }

    @Test
    fun `grams and volume in total and per period, a session without leaf adds nothing`() {
        val sessions = listOf(
            session(leafGrams = 5.0, waterMl = 120, steps = listOf(infusion(), infusion())),
            session(leafGrams = null),
            session(TODAY.minusDays(28), leafGrams = 4.5, waterMl = 300),
            session(TODAY.minusDays(40), leafGrams = 100.0, waterMl = 500),
        )

        val use = calculator(sessions).consumption(StatsRange.DAYS_30)

        assertThat(use.grams).isWithin(1e-9).of(9.5)
        assertThat(use.volumeMl).isEqualTo(540L)
        assertThat(use.gramsPerPeriod.map { it.value }).containsExactly(4.5, 0.0, 0.0, 0.0, 5.0).inOrder()
        assertThat(use.volumePerPeriod.map { it.value }).containsExactly(300L, 0L, 0L, 0L, 240L).inOrder()
        assertThat(calculator(sessions).overview(StatsRange.DAYS_30).volumeMl).isEqualTo(540L)
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
    fun `cost per litre only over the sessions with a cost and a volume, cents half up`() {
        val sessions = listOf(
            session(costCents = 120, waterMl = 100, steps = listOf(rinse, infusion(), infusion(), infusion())),
            session(costCents = 60, waterMl = 200, steps = listOf(whisk)),
            session(costCents = 500, steps = listOf(infusion(), infusion())),
            session(costCents = null, waterMl = 100, steps = listOf(infusion(), infusion())),
            session(TODAY.minusDays(40), costCents = 9999, waterMl = 100),
        )

        val use = calculator(sessions).consumption(StatsRange.DAYS_30)

        // 180 ct over 500 ml = 360 ct per litre; the 500 ct session has no volume and stays out of average and N.
        assertThat(use.costPerLiter).isEqualTo(LiterAverage(literCents = 360, sessionCount = 2))
        assertThat(use.drunkCents).isEqualTo(680)
        assertThat(use.drunkSessionCount).isEqualTo(3)
        assertThat(calculator(listOf(session())).consumption(StatsRange.DAYS_30).costPerLiter).isNull()
        assertThat(calculator(listOf(session(costCents = 5))).consumption(StatsRange.DAYS_30).costPerLiter).isNull()
    }

    @Test
    fun `cost per litre rounds half up`() {
        val sessions = listOf(session(costCents = 5, waterMl = 400))

        // 5 ct over 0,4 l = 12,5 ct per litre.
        assertThat(calculator(sessions).consumption(StatsRange.DAYS_30).costPerLiter?.literCents).isEqualTo(13)
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

    @Test
    fun `a running out tea carries its cover photo and style`() {
        val photo = java.io.File("cover.jpg")
        val teas = listOf(tea("a", grams = 4.0, level = StockLevel.LOW, coverPhoto = photo, styleKey = "sencha"))

        val out = calculator(teas = teas).consumption(StatsRange.DAYS_30).runningOut.single()

        assertThat(out.coverPhoto).isEqualTo(photo)
        assertThat(out.styleKey).isEqualTo("sencha")
    }

    @Test
    fun `volume sums stay exact - a 1 l Kanne session of two infusions is 2 l`() {
        val kanne = session(waterMl = 1_000, steps = listOf(infusion(), infusion()))
        val kyusu = session(waterMl = 120, steps = listOf(infusion()))
        val noWater = session(steps = listOf(infusion(), infusion()))

        val use = calculator(listOf(kanne, kyusu, noWater)).consumption(StatsRange.DAYS_30)

        assertThat(use.volumeMl).isEqualTo(2_120L)
    }
}
