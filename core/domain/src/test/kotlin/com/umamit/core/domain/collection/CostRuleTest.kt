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

    private fun recipe(leaf: Double, vararg kinds: StepKind) = Recipe(
        id = "r",
        teaId = TEA,
        name = "Rezept",
        method = BrewMethod.KYUSU,
        leafGrams = leaf,
        waterMl = 250,
        baseTempC = 70,
        steps = kinds.map { RecipeStep(it, 60) },
        isDefault = true,
    )

    @Test
    fun `the estimate is the default recipe's leaf times the price per gram over its cups, a rinse no cup`() {
        // 5 g × 24,8 ct = 124 ct over three infusions (the rinse pours nothing) → 41,33 → 41 ct.
        val recipe = recipe(5.0, StepKind.RINSE, StepKind.INFUSION, StepKind.INFUSION, StepKind.INFUSION)

        assertThat(CostRule.of(priced, recipe, emptyList())?.recipeCupCents).isEqualTo(41)
    }

    @Test
    fun `a whisk-only recipe is one cup`() {
        // 2 g × 24,8 ct = 49,6 → 50 ct, one bowl.
        assertThat(CostRule.of(priced, recipe(2.0, StepKind.WHISK), emptyList())?.recipeCupCents).isEqualTo(50)
    }

    @Test
    fun `without a default recipe there is no estimate but still the price`() {
        val cost = CostRule.of(priced, defaultRecipe = null, sessions = emptyList())

        assertThat(cost?.pricePer100gCents).isEqualTo(2480)
        assertThat(cost?.recipeCupCents).isNull()
    }

    /** A step row as the session stores it: [poured] ended with an actual time, else skipped (zeros) or never started. */
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
    ) = BrewSession(
        id = id,
        status = status,
        snapshot = SessionSnapshot(
            TEA, "Tee", null, null, null, null, null, null, BrewMethod.KYUSU, null, null, 5.0, 250, 70,
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
    fun `the average is the sessions' cost over their poured infusions, a rinse, a skip and an unstarted step none`() {
        val sessions = listOf(
            // Rinse, two poured, one skipped, one never started: 2 cups for 120 ct.
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
            // Three poured: 3 cups for 125 ct.
            session("b", 125, List(3) { row(it, StepKind.INFUSION) }),
        )

        // 245 ct over 5 cups = 49 ct, from 2 sessions.
        assertThat(CostRule.of(priced, null, sessions)?.average).isEqualTo(CupAverage(cupCents = 49, sessionCount = 2))
    }

    @Test
    fun `a whisk-only session is one cup`() {
        val sessions = listOf(
            session("m", 60, listOf(row(0, StepKind.WHISK))),
            session("k", 90, listOf(row(0, StepKind.INFUSION), row(1, StepKind.INFUSION))),
        )

        // 150 ct over 1 + 2 cups = 50 ct.
        assertThat(CostRule.of(priced, null, sessions)?.average).isEqualTo(CupAverage(50, 2))
    }

    @Test
    fun `sessions without a cost and the running one are left out of the average`() {
        val sessions = listOf(
            session("unpriced", null, List(4) { row(it, StepKind.INFUSION) }),
            session("running", 500, listOf(row(0, StepKind.INFUSION)), status = SessionStatus.IN_PROGRESS),
            session("priced", 81, List(3) { row(it, StepKind.INFUSION) }),
        )

        assertThat(CostRule.of(priced, null, sessions)?.average).isEqualTo(CupAverage(27, 1))
    }

    @Test
    fun `a priced session with every step skipped used its leaf and counts as one cup`() {
        val sessions = listOf(
            session("skipped", 40, listOf(row(0, StepKind.INFUSION, poured = false))),
            session("poured", 80, List(2) { row(it, StepKind.INFUSION) }),
        )

        // 120 ct over 1 + 2 cups = 40 ct.
        assertThat(CostRule.of(priced, null, sessions)?.average).isEqualTo(CupAverage(40, 2))
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
}
