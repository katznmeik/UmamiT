package com.umamit.core.backup.recipe

import com.umamit.core.model.BrewMethod
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.StepKind
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.VesselType
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** Synthetic recipes and files (D-016): made-up teas and article numbers, no shop text. */
internal object RecipeFixtures {
    const val SHOP_URL = "https://www.tee-kontor-kiel.de/nebelhang-sencha/XY0170"
    const val CATALOG_KEY = "XY0170"

    val CREATED_AT: OffsetDateTime = OffsetDateTime.of(2026, 11, 18, 8, 30, 0, 0, ZoneOffset.ofHours(1))

    /** A Kyusu recipe of four steps with a rinse, for a catalog tea (the example of 03 SCR-12, synthetic tea). */
    val kyusu = SharedRecipe(
        tea = SharedTea(
            name = "Nebelhang Sencha",
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
            RecipeStep(StepKind.INFUSION, 20, tempC = 65, preAlertSeconds = 5),
            RecipeStep(StepKind.INFUSION, 45, tempC = 70, note = "kurz schwenken", preAlertSeconds = 0),
        ),
        vesselType = VesselType.KYUSU,
        preAlertSeconds = 10,
    )

    /** A tea without catalog, no optional fields. */
    val plain = SharedRecipe(
        tea = SharedTea(name = "Eigener Bancha", category = TeaCategory.GREEN),
        name = "Becher",
        method = BrewMethod.WESTERN,
        leafGrams = 2.5,
        waterMl = 250,
        baseTempC = 80,
        steps = listOf(RecipeStep(StepKind.INFUSION, 120)),
    )

    fun write(recipe: SharedRecipe = kyusu): String = RecipeFile.write(recipe, CREATED_AT).decodeToString()

    fun read(text: String): RecipeFileResult = RecipeFile.read(text.toByteArray(Charsets.UTF_8))

    /** The written [kyusu] file with [old] replaced by [new]; fails when [old] is not in it exactly once. */
    fun edited(old: String, new: String): String {
        val text = write()
        check(text.split(old).size == 2) { "'$old' is not in the file exactly once" }
        return text.replace(old, new)
    }
}
