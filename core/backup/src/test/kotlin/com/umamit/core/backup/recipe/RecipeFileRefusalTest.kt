package com.umamit.core.backup.recipe

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.backup.recipe.RecipeFixtures.SHOP_URL
import com.umamit.core.backup.recipe.RecipeFixtures.edited
import com.umamit.core.backup.recipe.RecipeFixtures.read
import com.umamit.core.backup.recipe.RecipeFixtures.write
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

/** The reader's refusals and the shop link check (05 §8a, PRF-08 AC5, #340). Every file is untrusted input. */
class RecipeFileRefusalTest {
    private fun assertRefused(text: String, refusal: RecipeRefusal) {
        assertWithMessage(text).that(read(text)).isEqualTo(RecipeFileResult.Refused(refusal))
    }

    private fun assertDamaged(old: String, new: String) = assertRefused(edited(old, new), RecipeRefusal.DAMAGED)

    @Test
    fun `another format id or none is not a recipe file`() {
        assertRefused(edited("\"umamit-recipe\"", "\"umamit-backup\""), RecipeRefusal.NOT_A_RECIPE_FILE)
        assertRefused(edited("\"format\": \"umamit-recipe\",", ""), RecipeRefusal.NOT_A_RECIPE_FILE)
        assertRefused("""{"name":"Einkaufsliste","items":[]}""", RecipeRefusal.NOT_A_RECIPE_FILE)
        assertRefused(edited("\"format\": \"umamit-recipe\"", "\"format\": 1"), RecipeRefusal.NOT_A_RECIPE_FILE)
    }

    @Test
    fun `a newer format version is refused before its fields are looked at`() {
        // A v2 file may have new fields and enum names: still „newer", never „damaged".
        val v2 = """
            {"format":"umamit-recipe","formatVersion":2,"createdAt":"bald",
             "tea":{"name":"X","category":"PURPLE","flavour":"neu"},"recipe":{"method":"SIPHON"},"photos":[]}
        """.trimIndent()
        assertRefused(v2, RecipeRefusal.NEWER_VERSION)
        assertRefused(
            edited("\"formatVersion\": 1", "\"formatVersion\": 99999999999999999999"),
            RecipeRefusal.NEWER_VERSION,
        )
    }

    @Test
    fun `a format version that is not a whole number from 1 is damaged`() {
        listOf("0", "-1", "1.5", "\"1\"", "null", "true").forEach { version ->
            assertDamaged("\"formatVersion\": 1", "\"formatVersion\": $version")
        }
        assertDamaged("\"formatVersion\": 1,", "")
    }

    @Test
    fun `broken JSON, UTF-8 or a top level that is not an object is damaged`() {
        assertRefused(write().dropLast(2), RecipeRefusal.DAMAGED)
        assertRefused("[" + write() + "]", RecipeRefusal.DAMAGED)
        assertRefused("\"umamit-recipe\"", RecipeRefusal.DAMAGED)
        assertRefused("", RecipeRefusal.DAMAGED)
        assertRefused(write() + "{}", RecipeRefusal.DAMAGED)
        val badUtf8 = write().toByteArray().also { it[it.indexOf('N'.code.toByte())] = 0xC3.toByte() }
        assertThat(RecipeFile.read(badUtf8)).isEqualTo(RecipeFileResult.Refused(RecipeRefusal.DAMAGED))
        val binary = ByteArray(4_000) { (it * 31).toByte() }
        assertThat(RecipeFile.read(binary)).isEqualTo(RecipeFileResult.Refused(RecipeRefusal.DAMAGED))
    }

    @Test
    fun `deeply nested JSON is refused, not a crash`() {
        assertRefused("[".repeat(60_000), RecipeRefusal.DAMAGED)
        assertRefused("{\"a\":".repeat(10_000) + "1" + "}".repeat(10_000), RecipeRefusal.DAMAGED)
        // Counted before parsing: 16 levels are parsed (no format id), 17 are not; brackets in strings don't count.
        assertRefused("{\"a\":".repeat(16) + "1" + "}".repeat(16), RecipeRefusal.NOT_A_RECIPE_FILE)
        assertRefused("{\"a\":".repeat(17) + "1" + "}".repeat(17), RecipeRefusal.DAMAGED)
        val brackets = "[".repeat(18) + "\\\"" + "{".repeat(18)
        val inName = read(edited("\"Kyusu\"", "\"$brackets\""))
        assertThat(inName).isInstanceOf(RecipeFileResult.Valid::class.java)
    }

    @Test
    fun `a 65 KB file is refused unread`() {
        val text = write()
        val padded = text + " ".repeat(65 * 1024 - text.toByteArray().size)
        assertRefused(padded, RecipeRefusal.NOT_A_RECIPE_FILE)
        // One byte over the limit, and only the size can refuse it.
        assertRefused(
            text + " ".repeat(RecipeFile.MAX_BYTES + 1 - text.toByteArray().size),
            RecipeRefusal.NOT_A_RECIPE_FILE,
        )
    }

    @Test
    fun `a stream is read no further than one byte past the limit`() {
        var served = 0
        val endless = object : InputStream() {
            override fun read(): Int = ' '.code.also { served++ }
        }
        assertThat(RecipeFile.read(endless)).isEqualTo(RecipeFileResult.Refused(RecipeRefusal.NOT_A_RECIPE_FILE))
        assertThat(served).isAtMost(RecipeFile.MAX_BYTES + 1)
        val small = RecipeFile.read(ByteArrayInputStream(write().toByteArray()))
        assertThat(small).isInstanceOf(RecipeFileResult.Valid::class.java)
    }

    @Test
    fun `a missing required field, an unknown field or a wrong JSON type is damaged`() {
        assertDamaged("\"createdAt\": \"2026-11-18T08:30:00+01:00\",", "")
        assertDamaged("\"createdAt\": \"2026-11-18T08:30:00+01:00\"", "\"createdAt\": \"18.11.2026\"")
        assertDamaged("\"method\": \"KYUSU\",", "")
        assertDamaged("\"name\": \"Nebelhang Sencha\",", "")
        assertDamaged("\"name\": \"Kyusu\",", "\"name\": null,")
        assertDamaged("\"formatVersion\": 1,", "\"formatVersion\": 1, \"photos\": [],")
        assertDamaged("\"category\": \"GREEN\",", "\"category\": \"GREEN\", \"notes\": \"privat\",")
        assertDamaged("\"method\": \"KYUSU\",", "\"method\": \"KYUSU\", \"notes\": \"privat\",")
        assertDamaged("\"waterMl\": 120", "\"waterMl\": \"120\"")
        assertDamaged("\"waterMl\": 120", "\"waterMl\": 120.0")
        assertDamaged("\"waterMl\": 120", "\"waterMl\": 1.2e2")
        assertDamaged("\"leafGrams\": 4.0", "\"leafGrams\": \"4.0\"")
        assertDamaged("\"name\": \"Kyusu\"", "\"name\": 7")
        assertDamaged("\"name\": \"Kyusu\"", "\"name\": [\"Kyusu\"]")
        assertRefused(edited("\"tea\": {", "\"tea\": [], \"x\": {"), RecipeRefusal.DAMAGED)
        assertDamaged("\"steps\": [", "\"steps\": {}, \"y\": [")
        assertDamaged("\"kind\": \"RINSE\",", "\"kind\": \"RINSE\", \"photo\": \"a.jpg\",")
    }

    @Test
    fun `an unknown enum name is damaged`() {
        assertDamaged("\"category\": \"GREEN\"", "\"category\": \"PURPLE\"")
        assertDamaged("\"category\": \"GREEN\"", "\"category\": \"green\"")
        assertDamaged("\"method\": \"KYUSU\"", "\"method\": \"SIPHON\"")
        assertDamaged("\"vesselType\": \"KYUSU\"", "\"vesselType\": \"SAMOVAR\"")
        assertDamaged("\"kind\": \"RINSE\"", "\"kind\": \"STEAM\"")
    }

    @Test
    fun `a value outside PRF-03 and PRF-04 is damaged`() {
        listOf("0.05", "100.1", "0", "-4.0", "1e3", "NaN", "true").forEach {
            assertDamaged("\"leafGrams\": 4.0", "\"leafGrams\": $it")
        }
        listOf("5", "3005", "-120").forEach { assertDamaged("\"waterMl\": 120", "\"waterMl\": $it") }
        listOf("0", "62", "105").forEach { assertDamaged("\"baseTempC\": 60", "\"baseTempC\": $it") }
        listOf("62", "105", "0").forEach { assertDamaged("\"tempC\": 65", "\"tempC\": $it") }
        listOf("0", "3", "86401").forEach { assertDamaged("\"durationSeconds\": 90", "\"durationSeconds\": $it") }
        listOf("3", "35", "-5", "12").forEach {
            assertDamaged("\"preAlertSeconds\": 10", "\"preAlertSeconds\": $it")
            assertDamaged("\"preAlertSeconds\": 5", "\"preAlertSeconds\": $it")
        }
    }

    @Test
    fun `steps outside 1 to 30 or only rinses are damaged`() {
        val rinse = """{"kind": "RINSE", "durationSeconds": 10}"""
        val infusion = """{"kind": "INFUSION", "durationSeconds": 60}"""
        val full = write()
        val steps = full.substring(full.indexOf("\"steps\": ["), full.lastIndexOf(']') + 1)
        fun withSteps(list: List<String>) = full.replace(steps, "\"steps\": [${list.joinToString()}]")
        assertRefused(withSteps(emptyList()), RecipeRefusal.DAMAGED)
        assertRefused(withSteps(List(31) { infusion }), RecipeRefusal.DAMAGED)
        assertRefused(withSteps(listOf(rinse, rinse)), RecipeRefusal.DAMAGED)
        assertRefused(withSteps(listOf(rinse, "7")), RecipeRefusal.DAMAGED)
        assertThat(read(withSteps(List(30) { infusion }))).isInstanceOf(RecipeFileResult.Valid::class.java)
        assertThat(read(withSteps(listOf(rinse, infusion)))).isInstanceOf(RecipeFileResult.Valid::class.java)
    }

    @Test
    fun `a text over its limit is refused, not cut`() {
        assertDamaged("\"name\": \"Nebelhang Sencha\"", "\"name\": \"${"T".repeat(81)}\"")
        assertDamaged("\"secondaryName\": \"Testtal\"", "\"secondaryName\": \"${"S".repeat(81)}\"")
        assertDamaged("\"name\": \"Kyusu\"", "\"name\": \"${"R".repeat(41)}\"")
        assertDamaged("\"note\": \"kurz schwenken\"", "\"note\": \"${"n".repeat(121)}\"")
        assertDamaged("\"name\": \"Kyusu\"", "\"name\": \"   \"")
        assertDamaged("\"name\": \"Kyusu\"", "\"name\": \"Ky\\nusu\"")
    }

    @Test
    fun `a foreign host or http link is dropped with the catalog key, and the recipe stays valid`() {
        listOf(
            "http://www.tee-kontor-kiel.de/nebelhang-sencha/XY0170",
            "https://www.tee-kontor-kiel.de.example.com/nebelhang-sencha/XY0170",
            "https://example.com/nebelhang-sencha/XY0170",
            "https://www.tee-kontor-kiel.de@example.com/nebelhang-sencha/XY0170",
            "https://www.tee-kontor-kiel.de/nebelhang-sencha/XY0171",
            "https://www.tee-kontor-kiel.de/tee-info/XY0170",
            "javascript:alert(1)",
        ).forEach { link ->
            val result = read(edited("\"shopUrl\": \"$SHOP_URL\"", "\"shopUrl\": \"$link\""))
            assertWithMessage(link).that(result).isInstanceOf(RecipeFileResult.Valid::class.java)
            val tea = (result as RecipeFileResult.Valid).recipe.tea
            assertWithMessage(link).that(tea.shopUrl).isNull()
            assertWithMessage(link).that(tea.catalogKey).isNull()
            assertThat(result.recipe.steps).hasSize(4)
        }
    }

    @Test
    fun `an accepted link is kept in its checked form, a size variant of the same article too`() {
        val upper = read(edited(SHOP_URL, "HTTPS://WWW.Tee-Kontor-Kiel.de/nebelhang-sencha/XY0170-50"))
        val tea = (upper as RecipeFileResult.Valid).recipe.tea
        assertThat(tea.shopUrl).isEqualTo("https://www.tee-kontor-kiel.de/nebelhang-sencha/XY0170-50")
        assertThat(tea.catalogKey).isEqualTo("XY0170")
    }

    @Test
    fun `a catalog key without a link is kept, a malformed one is dropped`() {
        val noLink = read(edited("\"shopUrl\": \"$SHOP_URL\"", "\"shopUrl\": null"))
        assertThat((noLink as RecipeFileResult.Valid).recipe.tea.catalogKey).isEqualTo("XY0170")
        val variantKey = read(edited("\"catalogKey\": \"XY0170\"", "\"catalogKey\": \"XY0170-50\""))
        val tea = (variantKey as RecipeFileResult.Valid).recipe.tea
        assertThat(tea.catalogKey).isNull()
        assertThat(tea.shopUrl).isNull()
        val noKey = read(edited("\"catalogKey\": \"XY0170\",", ""))
        assertThat((noKey as RecipeFileResult.Valid).recipe.tea.shopUrl).isNull()
    }
}
