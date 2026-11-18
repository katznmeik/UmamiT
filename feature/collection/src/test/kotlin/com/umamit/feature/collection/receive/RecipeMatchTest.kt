package com.umamit.feature.collection.receive

import com.google.common.truth.Truth.assertThat
import com.umamit.core.backup.recipe.SharedTea
import com.umamit.core.model.TeaCategory
import com.umamit.feature.collection.receive.ReceivedRecipes.tea
import org.junit.Test

/** The same tea for a received recipe (PRF-08 AC6, D-130 Q13) and the name it takes over (AC7). */
class RecipeMatchTest {
    private fun file(name: String, key: String? = null) = SharedTea(name, TeaCategory.GREEN, catalogKey = key)

    @Test
    fun `the catalog key wins over the name`() {
        val teas = listOf(tea("byName", "Gyokuro Asahi"), tea("byKey", "Ganz anders", catalogKey = "XY0170"))

        assertThat(RecipeMatch.find(file("Gyokuro Asahi", "XY0170"), teas))
            .isEqualTo(TeaMatch("byKey", MatchKind.CATALOG))
    }

    @Test
    fun `the name matches ignoring case`() {
        val teas = listOf(tea("t1", "gyokuro asahi"))

        assertThat(RecipeMatch.find(file("Gyokuro Asahi"), teas)).isEqualTo(TeaMatch("t1", MatchKind.NAME))
    }

    @Test
    fun `the name matches ignoring accents and outer spaces`() {
        val teas = listOf(tea("t1", "Hojicha Kuki"))

        assertThat(RecipeMatch.find(file(" Hōjicha  Kuki "), teas)).isEqualTo(TeaMatch("t1", MatchKind.NAME))
    }

    @Test
    fun `an unknown key falls back to the name, and nothing else matches`() {
        val teas = listOf(tea("t1", "Gyokuro Asahi", catalogKey = "XY0999"))

        assertThat(RecipeMatch.find(file("Gyokuro Asahi", "XY0170"), teas)).isEqualTo(TeaMatch("t1", MatchKind.NAME))
        assertThat(RecipeMatch.find(file("Sencha"), teas)).isNull()
    }

    @Test
    fun `an archived tea matches, a tea in the collection first`() {
        val archived = tea("old", "Gyokuro Asahi", archived = true)

        assertThat(RecipeMatch.find(file("Gyokuro Asahi"), listOf(archived))).isEqualTo(TeaMatch("old", MatchKind.NAME))
        assertThat(RecipeMatch.find(file("Gyokuro Asahi"), listOf(archived, tea("new", "Gyokuro Asahi"))))
            .isEqualTo(TeaMatch("new", MatchKind.NAME))
    }

    @Test
    fun `a taken name gets the next free number`() {
        assertThat(RecipeMatch.recipeName("Kyusu", listOf("Westlich"))).isEqualTo("Kyusu")
        assertThat(RecipeMatch.recipeName("Kyusu", listOf("kyusu"))).isEqualTo("Kyusu (2)")
        assertThat(RecipeMatch.recipeName("Kyusu", listOf("Kyusu", "Kyusu (2)"))).isEqualTo("Kyusu (3)")
    }

    @Test
    fun `a long taken name is cut so the number fits 40 characters`() {
        val long = "A".repeat(40)

        val name = RecipeMatch.recipeName(long, listOf(long))

        assertThat(name).isEqualTo("A".repeat(36) + " (2)")
    }
}
