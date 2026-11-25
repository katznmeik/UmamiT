package com.umamit.core.backup.recipe

import com.google.common.truth.Truth.assertThat
import com.umamit.core.backup.recipe.RecipeFixtures.CREATED_AT
import com.umamit.core.backup.recipe.RecipeFixtures.edited
import com.umamit.core.backup.recipe.RecipeFixtures.kyusu
import com.umamit.core.backup.recipe.RecipeFixtures.plain
import com.umamit.core.backup.recipe.RecipeFixtures.read
import com.umamit.core.backup.recipe.RecipeFixtures.write
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.Recipe
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.StepKind
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.model.VesselType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.Locale

/** The recipe file's writer and reader (PRF-08, 05 §8a, #340): round trips and the shop link. */
class RecipeFileTest {
    private fun valid(text: String): SharedRecipe {
        val result = read(text)
        assertThat(result).isInstanceOf(RecipeFileResult.Valid::class.java)
        return (result as RecipeFileResult.Valid).recipe
    }

    private fun roundTrip(recipe: SharedRecipe): SharedRecipe {
        val result = RecipeFile.read(RecipeFile.write(recipe, CREATED_AT))
        assertThat(result).isEqualTo(RecipeFileResult.Valid(recipe, CREATED_AT))
        return (result as RecipeFileResult.Valid).recipe
    }

    /** GEN-13 (#359): only the share text follows the app language; the file is the same byte for byte. */
    @Test
    fun `the file is the same in English as in German`() {
        val before = Locale.getDefault()
        val (german, english) = try {
            Locale.setDefault(Locale.GERMANY)
            val german = RecipeFile.write(kyusu, CREATED_AT)
            Locale.setDefault(Locale.US)
            german to RecipeFile.write(kyusu, CREATED_AT)
        } finally {
            Locale.setDefault(before)
        }
        assertThat(english).isEqualTo(german)
    }

    @Test
    fun `every method survives the round trip`() {
        BrewMethod.entries.forEach { method -> roundTrip(kyusu.copy(method = method)) }
    }

    @Test
    fun `every step kind survives the round trip`() {
        StepKind.entries.forEach { kind ->
            val steps = listOf(RecipeStep(kind, 30, tempC = 75), RecipeStep(StepKind.INFUSION, 60))
            roundTrip(kyusu.copy(steps = steps))
        }
    }

    @Test
    fun `every vessel type, category and no vessel survive the round trip`() {
        VesselType.entries.forEach { roundTrip(kyusu.copy(vesselType = it)) }
        TeaCategory.entries.forEach { roundTrip(plain.copy(tea = plain.tea.copy(category = it))) }
        roundTrip(plain)
    }

    @Test
    fun `the limits themselves survive the round trip`() {
        val longest = List(RecipeFields.MAX_STEPS) { RecipeStep(StepKind.INFUSION, 86_400, 100, "n".repeat(120), 30) }
        roundTrip(
            kyusu.copy(
                tea = kyusu.tea.copy(name = "T".repeat(80), secondaryName = "S".repeat(80)),
                name = "R".repeat(40),
                leafGrams = 100.0,
                waterMl = 3000,
                baseTempC = 5,
                steps = longest,
                preAlertSeconds = 0,
            ),
        )
        roundTrip(kyusu.copy(leafGrams = 0.1, waterMl = 10, steps = listOf(RecipeStep(StepKind.WHISK, 5, 5))))
    }

    @Test
    fun `values off the editor's increments but within the data model's ranges are read back`() {
        // 05 §8a checks §3.2 and §3.3's ranges: a stored 2,45 g, 333 ml or 10:07 is never refused.
        roundTrip(kyusu.copy(leafGrams = 2.45, waterMl = 333, steps = listOf(RecipeStep(StepKind.INFUSION, 607))))
    }

    @Test
    fun `the file is the JSON of 05 8a`() {
        val root = Json.parseToJsonElement(write()).jsonObject
        assertThat(root.keys).containsExactly("format", "formatVersion", "createdAt", "tea", "recipe").inOrder()
        assertThat(root.getValue("format").jsonPrimitive.content).isEqualTo("umamit-recipe")
        assertThat(root.getValue("formatVersion").jsonPrimitive.content).isEqualTo("1")
        assertThat(root.getValue("createdAt").jsonPrimitive.content).isEqualTo("2026-11-18T08:30:00+01:00")
        val recipe = root.getValue("recipe").jsonObject
        assertThat(recipe.keys).containsExactly(
            "name",
            "method",
            "leafGrams",
            "waterMl",
            "baseTempC",
            "vesselType",
            "preAlertSeconds",
            "steps",
        ).inOrder()
        assertThat(recipe.getValue("leafGrams").jsonPrimitive.content).isEqualTo("4.0")
        assertThat(write(plain)).doesNotContain("catalogKey")
        assertThat(write(plain)).doesNotContain("shopUrl")
    }

    @Test
    fun `a recipe and tea from the collection become the shared recipe without notes or vessel`() {
        val tea = Tea(
            id = "tea-1",
            name = "Nebelhang Sencha",
            category = TeaCategory.GREEN,
            form = TeaForm.LOOSE_LEAF,
            secondaryName = "Testtal",
            catalogKey = RecipeFixtures.CATALOG_KEY,
            styleKey = "gyokuro",
            productUrl = RecipeFixtures.SHOP_URL,
            notes = "privat",
        )
        val recipe = Recipe(
            id = "r-1",
            teaId = "tea-1",
            name = "Kyusu",
            method = BrewMethod.KYUSU,
            leafGrams = 4.0,
            waterMl = 120,
            baseTempC = 60,
            steps = kyusu.steps,
            vesselId = "vessel-1",
            preAlertSeconds = 10,
            notes = "meine Notiz",
        )
        val shared = SharedRecipe.of(tea, recipe, VesselType.KYUSU)
        assertThat(shared).isEqualTo(kyusu)
        assertThat(write(shared)).doesNotContain("meine Notiz")
        assertThat(write(shared)).doesNotContain("vessel-1")
        assertThat(write(shared)).doesNotContain("privat")

        val badLink = SharedRecipe.of(tea.copy(productUrl = "http://example.com/x/XY0170"), recipe, null)
        assertThat(badLink.tea.catalogKey).isEqualTo(RecipeFixtures.CATALOG_KEY)
        assertThat(badLink.tea.shopUrl).isNull()
    }

    @Test
    fun `absent and null optional fields read as none, a blank note as none`() {
        val text = """
            {"format":"umamit-recipe","formatVersion":1,"createdAt":"2026-11-18T08:30:00Z",
             "tea":{"name":"Eigener Bancha","category":"GREEN","secondaryName":null,"styleKey":null},
             "recipe":{"name":"Becher","method":"WESTERN","leafGrams":2.5,"waterMl":250,"baseTempC":80,
              "steps":[{"kind":"INFUSION","durationSeconds":120,"note":"  "}]}}
        """.trimIndent()
        assertThat(valid(text)).isEqualTo(plain)
    }

    @Test
    fun `texts are trimmed of surrounding blanks, never cut`() {
        val recipe = valid(edited("\"name\": \"Kyusu\"", "\"name\": \"  Kyusu \""))
        assertThat(recipe.name).isEqualTo("Kyusu")
    }

    @Test
    fun `a leading byte order mark is accepted`() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + write().toByteArray()
        assertThat(RecipeFile.read(bytes)).isInstanceOf(RecipeFileResult.Valid::class.java)
    }

    @Test
    fun `an unknown style is dropped and the recipe stays valid`() {
        val recipe = valid(edited("\"styleKey\": \"gyokuro\"", "\"styleKey\": \"no_such_style\""))
        assertThat(recipe.tea.styleKey).isNull()
    }

    @Test
    fun `a file of exactly 64 KB is read, a stream too`() {
        val text = write()
        val padded = text + " ".repeat(RecipeFile.MAX_BYTES - text.toByteArray().size)
        assertThat(padded.toByteArray().size).isEqualTo(RecipeFile.MAX_BYTES)
        assertThat(valid(padded)).isEqualTo(kyusu)
        val stream = RecipeFile.read(ByteArrayInputStream(padded.toByteArray()))
        assertThat(stream).isEqualTo(RecipeFileResult.Valid(kyusu, CREATED_AT))
    }

    @Test
    fun `the file name drops the forbidden characters and keeps 60 characters`() {
        assertThat(RecipeFile.fileName("Gyokuro Asahi")).isEqualTo("Gyokuro Asahi.umamit-rezept.json")
        assertThat(RecipeFile.fileName("A/B\\C:D*E?F\"G<H>I|J")).isEqualTo("ABCDEFGHIJ.umamit-rezept.json")
        assertThat(RecipeFile.fileName("x".repeat(80))).isEqualTo("x".repeat(60) + ".umamit-rezept.json")
        assertThat(RecipeFile.fileName("/?*")).isEqualTo("Rezept.umamit-rezept.json")
    }
}
