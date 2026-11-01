package com.umamit.feature.collection.detail

import com.umamit.core.common.ClockReading
import com.umamit.core.data.SessionStart
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.CatalogPackage
import com.umamit.core.model.CatalogTeaDetail
import com.umamit.core.model.HarvestSeason
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.Recipe
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.StepKind
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.model.TimerSlot
import com.umamit.core.model.UmamiLevel
import com.umamit.core.model.Vessel
import com.umamit.core.model.VesselType
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeSessions
import com.umamit.core.timer.engine.BrewTimerEngine
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.PreAlertSettings
import com.umamit.core.timer.engine.StepRecord
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.engine.TimerState
import java.time.ZoneOffset

/** Synthetic teas, recipes and sessions for the tea detail's tests (D-016): invented names and texts. */
internal object TeaDetailFixture {
    const val TEA_ID = "morgennebel"
    const val CATALOG_KEY = "XY0100"
    const val PRODUCT_URL = "https://shop.example/morgennebel/XY0100"
    const val KYUSU_ID = "kyusu"
    const val STANDARD_ID = "standard"
    const val COLD_ID = "kalt"

    /** 21 Oct 2026, 08:00 UTC. */
    const val LAST_BREW_MS = 1_792_569_600_000L

    val tea = Tea(
        id = TEA_ID,
        name = "Morgennebel",
        secondaryName = "Asagiri",
        category = TeaCategory.GREEN,
        form = TeaForm.LOOSE_LEAF,
        catalogKey = CATALOG_KEY,
        styleKey = "sencha",
        teaType = "Sencha",
        umamiLevel = UmamiLevel.HIGH,
        description = "Ein erfundener Sencha mit grasiger Frische und weichem Nachklang.",
        notes = "Zweiter Aufguss etwas kürzer.",
        originCountry = "Japan",
        originRegion = "Shizuoka",
        cultivar = "Yabukita",
        harvestYear = 2026,
        harvestSeason = HarvestSeason.FIRST_FLUSH,
        vendor = "Teehaus Beispiel",
        productUrl = PRODUCT_URL,
    )

    val vessel = Vessel(id = KYUSU_ID, name = "Kyusu 250 ml", type = VesselType.KYUSU, capacityMl = 300, usableMl = 250)

    val recipes = listOf(
        Recipe(
            id = STANDARD_ID,
            teaId = TEA_ID,
            name = "Kyusu",
            method = BrewMethod.KYUSU,
            leafGrams = 5.0,
            waterMl = 250,
            baseTempC = 70,
            steps = listOf(
                RecipeStep(StepKind.INFUSION, 60),
                RecipeStep(StepKind.INFUSION, 15, tempC = 75),
                RecipeStep(StepKind.INFUSION, 30, tempC = 80),
            ),
            vesselId = KYUSU_ID,
            isDefault = true,
            position = 0,
        ),
        Recipe(
            id = COLD_ID,
            teaId = TEA_ID,
            name = "Kaltaufguss",
            method = BrewMethod.COLD_BREW,
            leafGrams = 10.0,
            waterMl = 1000,
            baseTempC = 5,
            steps = listOf(RecipeStep(StepKind.RINSE, 10, tempC = 70), RecipeStep(StepKind.INFUSION, 10_800)),
            position = 1,
        ),
    )

    val packages = listOf(
        CatalogPackage("XY0100-50", "50 g", 50.0, 990, false, null, "$PRODUCT_URL-50", true),
        CatalogPackage("XY0100-100", "100 g", 100.0, 1890, false, null, PRODUCT_URL, true),
    )

    val catalogTea =
        CatalogTeaDetail(key = CATALOG_KEY, name = "Morgennebel", productUrl = PRODUCT_URL, packages = packages)

    /** The tea with its recipes, its vessel and 50 g bought (INITIAL). */
    suspend fun seed(collection: FakeCollection, tea: Tea = TeaDetailFixture.tea, grams: Double = 50.0) {
        collection.teas.saveTea(tea)
        collection.vessels.saveVessel(vessel)
        recipes.forEach { collection.recipes.saveRecipe(it) }
        if (grams > 0.0) {
            collection.stock.addEvent(
                InventoryEvent("initial", tea.id, InventoryEventType.INITIAL, grams, 1_000, priceCents = 990),
            )
        }
    }

    private fun snapshot(teaId: String = TEA_ID) = SessionSnapshot(
        teaId, "Morgennebel", null, "Sencha", "sencha", TeaCategory.GREEN, STANDARD_ID, "Kyusu",
        BrewMethod.KYUSU, KYUSU_ID, "Kyusu 250 ml", 5.0, 250, 70,
    )

    /** A finished session of the tea, started at [at]. */
    suspend fun brewed(sessions: FakeSessions, id: String, at: Long) {
        sessions.sessions.startSession(
            SessionStart(id, snapshot(), listOf(PlannedStep(StepKind.INFUSION, 60, 70)), 10, at, "Europe/Berlin"),
        )
        sessions.sessions.recordStep(id, 0, StepRecord(60, 60, 0, 0, at, at + 60_000, false, false))
        sessions.sessions.completeSession(id, autoFinished = false)
    }

    /** A brew session of the tea waits in READY: the active session (E15). */
    suspend fun activeSession(sessions: FakeSessions, teaId: String = TEA_ID) {
        val steps = listOf(PlannedStep(StepKind.INFUSION, 60, 70))
        val start = TimerEvent.StartSession(
            "active",
            steps,
            PreAlertSettings.inherited(null, 10),
            longStepsOnly = false,
        )
        val ready = BrewTimerEngine.reduce(TimerState.Idle, start, ClockReading(1_000, 1_000, ZoneOffset.UTC, 1)).state
        sessions.sessions.startSession(SessionStart("active", snapshot(teaId), steps, 10, 1_000, "UTC"))
        sessions.activeBrew.persist(TimerSlot.BREW, BrewTimerEngine.snapshot(ready))
    }
}
