package com.umamit.core.data

import com.umamit.core.model.BrewMethod
import com.umamit.core.model.HarvestSeason
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.Recipe
import com.umamit.core.model.RecipeSource
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.StepKind
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.model.UmamiLevel
import com.umamit.core.model.Vessel
import com.umamit.core.model.VesselType

/**
 * The first-start seed data of 08 §2.5a. Only factual values: no shop description texts
 * and no photos (D-016, CLAUDE.md rule 3); the photos are downloaded at runtime (ONB-06).
 */
object SeedData {
    const val TANABATA_TEA_ID = "6f1c2a9e-3b4d-4e8a-9c71-2d5b8e0f4a13"
    const val HISUI_TEA_ID = "b8e47d02-9a1f-4c36-8e25-7f0d3c6a9b41"

    const val KYUSU_VESSEL_ID = "2c9a5e71-0d48-4b6f-a3e2-8f1b7c4d6e05"
    const val MUG_VESSEL_ID = "e4d1f7b3-6c2a-4f90-b85e-1a3c9d7e2f68"

    /** The product photo paths on the shop host (08 §2.5a), downloaded once at runtime (ONB-06). */
    const val TANABATA_PHOTO_PATH = "/media/ab/15/63/1727307323/gtbiosenchatanabataaiya.jpg"
    const val HISUI_PHOTO_PATH = "/media/7c/c3/7c/1727308250/gtbiosenchahisui.jpg"

    /** Tea id → photo path, in download order. */
    val photoPaths: List<Pair<String, String>> = listOf(
        TANABATA_TEA_ID to TANABATA_PHOTO_PATH,
        HISUI_TEA_ID to HISUI_PHOTO_PATH,
    )

    private const val VENDOR = "Tee-Kontor Kiel"
    private const val SHOP = "https://www.tee-kontor-kiel.de"
    private const val ASAMUSHI = "Asamushi (leicht gedämpft)"

    val tanabata = Tea(
        id = TANABATA_TEA_ID,
        name = "Bio Sencha Tanabata",
        category = TeaCategory.GREEN,
        form = TeaForm.LOOSE_LEAF,
        catalogKey = "TG0213",
        styleKey = "sencha",
        teaType = "Sencha",
        // No umami scale in the shop: the style default (08 §2.5a).
        umamiLevel = UmamiLevel.MEDIUM,
        isOrganic = true,
        originCountry = "JP",
        originRegion = "Kagoshima (Kyushu)",
        cultivar = "Blend, hauptsächlich Yabukita",
        harvestSeason = HarvestSeason.SUMMER,
        processingNotes = ASAMUSHI,
        vendor = VENDOR,
        productUrl = "$SHOP/bio-sencha-tanabata/TG0213",
    )

    val hisui = Tea(
        id = HISUI_TEA_ID,
        name = "Bio Sencha Hisui",
        category = TeaCategory.GREEN,
        form = TeaForm.LOOSE_LEAF,
        catalogKey = "TG0557",
        styleKey = "sencha",
        teaType = "Sencha",
        // Shop umami scale 1 (08 §2.5a).
        umamiLevel = UmamiLevel.LOW,
        isOrganic = true,
        originCountry = "JP",
        originRegion = "Kirishima, Kagoshima (Kyushu)",
        cultivar = "Okumidori (100 %)",
        harvestSeason = HarvestSeason.FIRST_FLUSH,
        shadingDays = 6,
        processingNotes = ASAMUSHI,
        vendor = VENDOR,
        productUrl = "$SHOP/bio-sencha-hisui/TG0557",
    )

    val teas: List<Tea> = listOf(tanabata, hisui)

    /** The PO's teaware (WARE-06). */
    val vessels: List<Vessel> = listOf(
        Vessel(id = KYUSU_VESSEL_ID, name = "Kyusu", type = VesselType.KYUSU, capacityMl = 300, usableMl = 250),
        Vessel(
            id = MUG_VESSEL_ID,
            name = "Becher mit Sieb",
            type = VesselType.MUG_INFUSER,
            capacityMl = 400,
            usableMl = 400,
        ),
    )

    /** The recipes of 08 §2.5a, scaled from the shop's ratios to the seed vessels. */
    val recipes: List<Recipe> = listOf(
        kyusu("0a7e3c94-5b1d-4f28-9e6a-3d8c2b1f7a50", TANABATA_TEA_ID, firstInfusionSeconds = 45),
        westlich("7d2b9f16-8e4c-4a35-b1d7-6c0e5a9f3b82", TANABATA_TEA_ID, baseTempC = 70),
        kyusu("c3f85a27-1e9b-4d60-8a4c-5b7d2e0f9c16", HISUI_TEA_ID, firstInfusionSeconds = 60),
        westlich("5e1a7c38-2f6d-4b94-9c03-8d4b1e6a7f29", HISUI_TEA_ID, baseTempC = 65),
        Recipe(
            id = "9b4d6e02-7a3c-4e18-a5f9-2c8e1d0b6a74",
            teaId = HISUI_TEA_ID,
            name = "Kaltaufguss",
            method = BrewMethod.COLD_BREW,
            leafGrams = 10.0,
            waterMl = 1000,
            baseTempC = 5,
            steps = listOf(RecipeStep(StepKind.INFUSION, COLD_BREW_SECONDS)),
            position = 2,
            // Style default for cold brew, from the shop's „Coldbrew geeignet" badge.
            source = RecipeSource.STYLE_DEFAULT,
        ),
    )

    /**
     * The PO's stock: INITIAL 100 g of each tea at the exact price paid, dated to
     * [firstStartMs] (08 §2.5a).
     */
    fun initialStock(firstStartMs: Long): List<InventoryEvent> = listOf(
        initial("4a8c1e63-9d2b-4f75-8e10-6b3d7a2c5f94", TANABATA_TEA_ID, TANABATA_PRICE_CENTS, firstStartMs),
        initial("d6f29b40-3c7e-4a81-b5d2-0e9a4c8f1b37", HISUI_TEA_ID, HISUI_PRICE_CENTS, firstStartMs),
    )

    private const val SEED_STOCK_GRAMS = 100.0
    private const val TANABATA_PRICE_CENTS = 850L
    private const val HISUI_PRICE_CENTS = 1790L

    private fun initial(id: String, teaId: String, priceCents: Long, at: Long) = InventoryEvent(
        id = id,
        teaId = teaId,
        type = InventoryEventType.INITIAL,
        deltaGrams = SEED_STOCK_GRAMS,
        occurredAtMillis = at,
        priceCents = priceCents,
        packageLabel = "100 g",
    )

    private const val COLD_BREW_SECONDS = 4 * 60 * 60
    private const val SECOND_INFUSION_SECONDS = 10
    private const val WESTERN_FIRST_SECONDS = 60
    private const val WESTERN_SECOND_SECONDS = 15
    private const val WESTERN_SECOND_TEMP_RISE = 5

    /** „Kyusu" (default): 5,0 g / 250 ml / 65 °C, three infusions. */
    private fun kyusu(id: String, teaId: String, firstInfusionSeconds: Int) = Recipe(
        id = id,
        teaId = teaId,
        name = "Kyusu",
        method = BrewMethod.KYUSU,
        leafGrams = 5.0,
        waterMl = 250,
        baseTempC = 65,
        steps = listOf(
            RecipeStep(StepKind.INFUSION, firstInfusionSeconds),
            RecipeStep(StepKind.INFUSION, SECOND_INFUSION_SECONDS),
            RecipeStep(StepKind.INFUSION, SECOND_INFUSION_SECONDS),
        ),
        vesselId = KYUSU_VESSEL_ID,
        isDefault = true,
        position = 0,
        source = RecipeSource.CATALOG_TABLE,
    )

    /** „Westlich": 4,0 g / 400 ml, 1:00 and 0:15 at 5 °C more. */
    private fun westlich(id: String, teaId: String, baseTempC: Int) = Recipe(
        id = id,
        teaId = teaId,
        name = "Westlich",
        method = BrewMethod.WESTERN,
        leafGrams = 4.0,
        waterMl = 400,
        baseTempC = baseTempC,
        steps = listOf(
            RecipeStep(StepKind.INFUSION, WESTERN_FIRST_SECONDS),
            RecipeStep(StepKind.INFUSION, WESTERN_SECOND_SECONDS, tempC = baseTempC + WESTERN_SECOND_TEMP_RISE),
        ),
        vesselId = MUG_VESSEL_ID,
        position = 1,
        source = RecipeSource.CATALOG_TABLE,
    )
}
