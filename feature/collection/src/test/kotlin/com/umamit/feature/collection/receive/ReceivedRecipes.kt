package com.umamit.feature.collection.receive

import com.umamit.core.backup.recipe.RecipeFile
import com.umamit.core.backup.recipe.SharedRecipe
import com.umamit.core.backup.recipe.SharedTea
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.Recipe
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.StepKind
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.model.VesselType
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** Synthetic received recipes (D-016): made-up teas, article numbers and links, no shop text. */
internal object ReceivedRecipes {
    const val CATALOG_KEY = "XY0170"
    const val SHOP_URL = "https://www.tee-kontor-kiel.de/nebelhang-sencha/XY0170"

    private val CREATED_AT: OffsetDateTime = OffsetDateTime.of(2026, 11, 18, 8, 30, 0, 0, ZoneOffset.ofHours(1))

    /** A Kyusu recipe of a catalog tea with its shop link. */
    val kyusu = SharedRecipe(
        tea = SharedTea(
            name = "Gyokuro Asahi",
            category = TeaCategory.GREEN,
            secondaryName = "Testtal",
            styleKey = "gyokuro",
            catalogKey = CATALOG_KEY,
            shopUrl = SHOP_URL,
        ),
        name = "Kyusu",
        method = BrewMethod.KYUSU,
        leafGrams = 4.0,
        waterMl = 120,
        baseTempC = 60,
        steps = listOf(
            RecipeStep(StepKind.RINSE, 10),
            RecipeStep(StepKind.INFUSION, 90, tempC = 60),
            RecipeStep(StepKind.INFUSION, 20, tempC = 65),
        ),
        vesselType = VesselType.KYUSU,
    )

    /** A tea without catalog. */
    val plain = SharedRecipe(
        tea = SharedTea(name = "Eigener Bancha", category = TeaCategory.GREEN, styleKey = "bancha"),
        name = "Becher",
        method = BrewMethod.WESTERN,
        leafGrams = 2.5,
        waterMl = 250,
        baseTempC = 80,
        steps = listOf(RecipeStep(StepKind.INFUSION, 120)),
    )

    fun bytes(recipe: SharedRecipe = kyusu): ByteArray = RecipeFile.write(recipe, CREATED_AT)

    fun tea(id: String, name: String, catalogKey: String? = null, archived: Boolean = false) = Tea(
        id = id,
        name = name,
        category = TeaCategory.GREEN,
        form = TeaForm.LOOSE_LEAF,
        catalogKey = catalogKey,
        isArchived = archived,
    )

    fun recipe(teaId: String, index: Int, name: String = "Rezept $index") = Recipe(
        id = "$teaId-r$index",
        teaId = teaId,
        name = name,
        method = BrewMethod.WESTERN,
        leafGrams = 3.0,
        waterMl = 250,
        baseTempC = 80,
        steps = listOf(RecipeStep(StepKind.INFUSION, 120)),
        isDefault = index == 0,
        position = index,
    )
}
