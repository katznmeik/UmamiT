package com.umamit.core.backup.recipe

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.backup.recipe.RecipeFixtures.SHOP_URL
import com.umamit.core.backup.recipe.RecipeFixtures.kyusu
import com.umamit.core.backup.recipe.RecipeFixtures.plain
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.StepKind
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The share text (PRF-08 AC1, the example in 03 SCR-12) against the module's own strings.xml (#340). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecipeShareTextTest {
    private val labels = ResourceRecipeShareLabels(ApplicationProvider.getApplicationContext<Context>().resources)

    /** A no-break space between a number and its unit (05 §7). */
    private val nbsp = "\u00A0"

    @Test
    fun `a Kyusu recipe of four steps with a rinse reads as the golden`() {
        val golden = listOf(
            "Nebelhang Sencha (Testtal) – Rezept „Kyusu\"",
            "Kyusu · 4,0${nbsp}g auf 120${nbsp}ml · 60$nbsp°C · Gefäß: Kyusu",
            "1. Spülen · 60$nbsp°C · 0:10",
            "2. Aufguss · 60$nbsp°C · 1:30",
            "3. Aufguss · 65$nbsp°C · 0:20",
            "4. Aufguss · 70$nbsp°C · 0:45",
            SHOP_URL,
            "Geteilt aus UmamiT – öffne die angehängte Datei in UmamiT, um das Rezept zu übernehmen.",
        ).joinToString("\n")
        assertThat(RecipeShareText.build(kyusu, labels)).isEqualTo(golden)
    }

    @Test
    fun `a tea without catalog, secondary name or vessel type has no such parts`() {
        val text = RecipeShareText.build(plain.copy(leafGrams = 12.5), labels)
        assertThat(text).isEqualTo(
            listOf(
                "Eigener Bancha – Rezept „Becher\"",
                "Westlich · 12,5${nbsp}g auf 250${nbsp}ml · 80$nbsp°C",
                "1. Aufguss · 80$nbsp°C · 2:00",
                "Geteilt aus UmamiT – öffne die angehängte Datei in UmamiT, um das Rezept zu übernehmen.",
            ).joinToString("\n"),
        )
    }

    @Test
    fun `long steps read with hours and the whisk has its own word`() {
        val steps = listOf(RecipeStep(StepKind.WHISK, 4 * 3_600, tempC = 5), RecipeStep(StepKind.INFUSION, 600))
        val lines = RecipeShareText.build(plain.copy(steps = steps), labels).lines()
        assertThat(lines[2]).isEqualTo("1. Aufschlagen · 5$nbsp°C · 4:00:00")
        assertThat(lines[3]).isEqualTo("2. Aufguss · 80$nbsp°C · 10:00")
    }
}
