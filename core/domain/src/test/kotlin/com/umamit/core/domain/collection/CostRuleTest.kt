package com.umamit.core.domain.collection

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.BrewSession
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.Recipe
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.SessionStepRow
import com.umamit.core.model.StepKind
import com.umamit.core.model.StockSummary
import org.junit.Test

/** A tea's cost on its detail (INV-08, D-104; spec #171 seam 1): price per 100 g, the recipe estimate, the average. */
class CostRuleTest {
    private fun bought(
        id: String,
        grams: Double,
        cents: Long?,
        type: InventoryEventType = InventoryEventType.PURCHASE,
    ) = InventoryEvent(id, TEA, type, grams, 1_000, priceCents = cents)

    @Test
    fun `the price per 100 g is weighted by the grams of two purchases at different prices`() {
        // 50 g for 9,90 € and 100 g for 24,00 €: 33,90 € for 150 g → 22,60 € / 100 g (not the mean 21,90 €).
        val stock = StockSummary.of(
            listOf(
                bought("i", 50.0, 990, InventoryEventType.INITIAL),
                bought("p", 100.0, 2400),
                InventoryEvent("b", TEA, InventoryEventType.BREW, -5.0, 2_000),
            ),
        )

        assertThat(CostRule.of(stock, defaultRecipe = null, sessions = emptyList())?.pricePer100gCents)
            .isEqualTo(2260)
    }

    /** 50 g for 12,40 €: 24,8 ct per gram. */
    private val priced = StockSummary.of(listOf(bought("i", 50.0, 1240, InventoryEventType.INITIAL)))

    private fun recipe(leaf: Double, vararg kinds: StepKind, waterMl: Int = 150) = Recipe(
        id = "r",
        teaId = TEA,
        name = "Rezept",
        method = BrewMethod.KYUSU,
        leafGrams = leaf,
        waterMl = waterMl,
        baseTempC = 70,
        steps = kinds.map { RecipeStep(it, 60) },
        isDefault = true,
    )

    @Test
    fun `the estimate is the leaf times the price per gram over the recipe's litres, a rinse pours none`() {
        // 5 g × 24,8 ct = 124 ct over 3 infusions × 150 ml = 450 ml → 275,56 → 276 ct per litre.
        val recipe = recipe(5.0, StepKind.RINSE, StepKind.INFUSION, StepKind.INFUSION, StepKind.INFUSION)

        assertThat(CostRule.of(priced, recipe, emptyList())?.recipeLiterCents).isEqualTo(276)
    }

    @Test
    fun `a whisk-only recipe pours its water once`() {
        // 2 g × 24,8 ct = 49,6 ct over 150 ml → 330,67 → 331 ct per litre.
        assertThat(CostRule.of(priced, recipe(2.0, StepKind.WHISK), emptyList())?.recipeLiterCents).isEqualTo(331)
    }

    @Test
    fun `a recipe without water or with only rinses has no estimate`() {
        assertThat(CostRule.of(priced, recipe(5.0, StepKind.INFUSION, waterMl = 0), emptyList())?.recipeLiterCents)
            .isNull()
        assertThat(CostRule.of(priced, recipe(5.0, StepKind.RINSE, StepKind.RINSE), emptyList())?.recipeLiterCents)
            .isNull()
    }

    @Test
    fun `without a default recipe there is no estimate but still the price`() {
        val cost = CostRule.of(priced, defaultRecipe = null, sessions = emptyList())

        assertThat(cost?.pricePer100gCents).isEqualTo(2480)
        assertThat(cost?.recipeLiterCents).isNull()
    }

    /** A step row as stored: [poured] ended with an actual time, else skipped (zeros), or never [started]. */
    private fun row(position: Int, kind: StepKind, poured: Boolean = true, started: Boolean = true) = SessionStepRow(
        position = position,
        kind = kind,
        plannedSeconds = 60,
        tempC = null,
        actualSeconds = if (started) (if (poured) 60 else 0) else null,
        pausedSeconds = null,
        overtimeSeconds = null,
        startedAtWallMs = null,
        endedAtWallMs = null,
        skipped = started && !poured,
        overtimeAutoAck = false,
    )

    private fun session(
        id: String,
        costCents: Long?,
        steps: List<SessionStepRow>,
        status: SessionStatus = SessionStatus.COMPLETED,
        waterMl: Int? = 150,
    ) = BrewSession(
        id = id,
        status = status,
        snapshot = SessionSnapshot(
            TEA, "Tee", null, null, null, null, null, null, BrewMethod.KYUSU, null, null, 5.0, waterMl, 70,
        ),
        preAlertSeconds = null,
        plannedStepCount = steps.size,
        costCents = costCents,
        startedAtMs = 1_000,
        endedAtMs = 2_000,
        zoneId = "UTC",
        autoFinished = false,
        steps = steps,
    )

    @Test
    fun `the average is the cost over the poured litres, a rinse, a skip and an unstarted step pour none`() {
        val sessions = listOf(
            // Rinse, two poured, one skipped, one never started: 2 × 150 ml for 120 ct.
            session(
                "a",
                120,
                listOf(
                    row(0, StepKind.RINSE),
                    row(1, StepKind.INFUSION),
                    row(2, StepKind.INFUSION),
                    row(3, StepKind.INFUSION, poured = false),
                    row(4, StepKind.INFUSION, started = false),
                ),
            ),
            // Three poured: 450 ml for 125 ct.
            session("b", 125, List(3) { row(it, StepKind.INFUSION) }),
        )

        // 245 ct over 750 ml = 326,67 → 327 ct per litre, from 2 sessions.
        assertThat(
            CostRule.of(priced, null, sessions)?.average,
        ).isEqualTo(LiterAverage(literCents = 327, sessionCount = 2))
    }

    @Test
    fun `a whisk-only session pours its water once`() {
        val sessions = listOf(
            session("m", 60, listOf(row(0, StepKind.WHISK))),
            session("k", 90, listOf(row(0, StepKind.INFUSION), row(1, StepKind.INFUSION))),
        )

        // 150 ct over 150 + 300 ml = 333,33 → 333 ct per litre.
        assertThat(CostRule.of(priced, null, sessions)?.average).isEqualTo(LiterAverage(333, 2))
    }

    @Test
    fun `sessions without a cost and the running one are left out of the average`() {
        val sessions = listOf(
            session("unpriced", null, List(4) { row(it, StepKind.INFUSION) }),
            session("running", 500, listOf(row(0, StepKind.INFUSION)), status = SessionStatus.IN_PROGRESS),
            session("priced", 81, List(3) { row(it, StepKind.INFUSION) }),
        )

        // 81 ct over 450 ml = 180 ct per litre.
        assertThat(CostRule.of(priced, null, sessions)?.average).isEqualTo(LiterAverage(180, 1))
    }

    @Test
    fun `a session with every step skipped or without water_ml has no volume and is left out of the average`() {
        val sessions = listOf(
            session("skipped", 40, listOf(row(0, StepKind.INFUSION, poured = false))),
            session("no water", 50, List(2) { row(it, StepKind.INFUSION) }, waterMl = null),
            session("poured", 80, List(2) { row(it, StepKind.INFUSION) }),
        )

        // 80 ct over 300 ml = 266,67 → 267 ct per litre, from 1 session.
        assertThat(CostRule.of(priced, null, sessions)?.average).isEqualTo(LiterAverage(267, 1))
        assertThat(CostRule.of(priced, null, sessions.take(2))?.average).isNull()
    }

    @Test
    fun `without a priced session there is no average`() {
        val sessions = listOf(session("unpriced", null, List(2) { row(it, StepKind.INFUSION) }))

        assertThat(CostRule.of(priced, null, sessions)?.average).isNull()
    }

    @Test
    fun `no price gives no cost at all, whatever the recipe and sessions`() {
        val unpriced = StockSummary.of(listOf(bought("i", 50.0, null, InventoryEventType.INITIAL)))
        val sessions = listOf(session("old", 100, listOf(row(0, StepKind.INFUSION))))

        assertThat(CostRule.of(unpriced, recipe(5.0, StepKind.INFUSION), sessions)).isNull()
        assertThat(CostRule.of(StockSummary.of(emptyList()), recipe(5.0, StepKind.INFUSION), sessions)).isNull()
    }

    private companion object {
        const val TEA = "tea"
    }

    @Test
    fun `a Kanne of 1000 ml with two infusions is 2000 ml, a Kyusu of 120 ml with three is 360 ml`() {
        assertThat(CostRule.sessionVolumeMl(List(2) { StepKind.INFUSION }, 1_000)).isEqualTo(2_000)
        assertThat(CostRule.sessionVolumeMl(List(3) { StepKind.INFUSION }, 120)).isEqualTo(360)
        // 400 ct over 2 l = 200 ct per litre.
        val sessions = listOf(session("k", 400, List(2) { row(it, StepKind.INFUSION) }, waterMl = 1_000))
        assertThat(CostRule.of(priced, null, sessions)?.average).isEqualTo(LiterAverage(200, 1))
    }

    @Test
    fun `whisk-only pours once, a rinse none, without water_ml or nothing poured the volume is 0 ml`() {
        assertThat(CostRule.sessionVolumeMl(listOf(StepKind.WHISK), 300)).isEqualTo(300)
        assertThat(CostRule.sessionVolumeMl(listOf(StepKind.WHISK, StepKind.WHISK), 300)).isEqualTo(300)
        assertThat(CostRule.sessionVolumeMl(listOf(StepKind.RINSE, StepKind.INFUSION), 300)).isEqualTo(300)
        assertThat(CostRule.sessionVolumeMl(listOf(StepKind.RINSE), 300)).isEqualTo(0)
        assertThat(CostRule.sessionVolumeMl(emptyList(), 300)).isEqualTo(0)
        assertThat(CostRule.sessionVolumeMl(List(3) { StepKind.INFUSION }, null)).isEqualTo(0)
    }

    @Test
    fun `the cost per litre rounds half up`() {
        // 3 ct over 2 000 ml = 1,5 → 2 ct per litre.
        val sessions = listOf(session("k", 3, List(2) { row(it, StepKind.INFUSION) }, waterMl = 1_000))

        assertThat(CostRule.of(priced, null, sessions)?.average?.literCents).isEqualTo(2)
    }
}
