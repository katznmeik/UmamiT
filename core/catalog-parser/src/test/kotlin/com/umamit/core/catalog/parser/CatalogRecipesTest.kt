package com.umamit.core.catalog.parser

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.RecipeCheckReason
import com.umamit.core.model.TeaForm
import com.umamit.core.model.UmamiLevel
import org.junit.Test

/** A parsed product page to its style and recipes (CAT-04, CAT-06), as the refresh stores them. */
class CatalogRecipesTest {
    private fun parse(html: String) =
        (TeeKontorParser.parseProduct(html.toByteArray(), "https://shop.example/x/XY0100") as ProductParse.Parsed).page

    @Test
    fun aPageGivesItsStyleUmamiLevelAndRecipes() {
        val page = parse(
            SyntheticShop.productPage(
                badges = listOf("Bio", "Coldbrew geeignet"),
                scales = listOf("Umami" to 3),
                brewRows = listOf(
                    "westlich" to listOf("~10g/L", "~70°C", "~1 Min", "-", "-"),
                    "japan" to listOf("~4g/200ml", "~60°C", "60 Sek", "10 Sek", "10 Sek"),
                ),
            ),
        )

        val result = CatalogRecipes.derive(page)

        assertThat(result.styleKey).isEqualTo("sencha")
        assertThat(result.form).isEqualTo(TeaForm.LOOSE_LEAF)
        assertThat(result.umamiLevel).isEqualTo(UmamiLevel.HIGH)
        assertThat(result.recipes.map { it.method })
            .containsExactly(BrewMethod.KYUSU, BrewMethod.WESTERN, BrewMethod.COLD_BREW).inOrder()
        assertThat(result.checkReason).isNull()
    }

    @Test
    fun aTeaFromJapanInAGenericSectionDefaultsToItsKyusuRow() {
        val page = parse(
            SyntheticShop.productPage(
                title = "Wolkenspitze – 50g",
                breadcrumb = listOf("Tee" to "/tee/", "Grüner Tee" to "/tee/gruener-tee/"),
                properties = listOf("Anbaugebiete" to listOf("Japan")),
                brewRows = listOf(
                    "westlich" to listOf("~10g/L", "~80°C", "~2 Min", "-", "-"),
                    "japan" to listOf("~4g/200ml", "~70°C", "60 Sek", "10 Sek", "10 Sek"),
                ),
            ),
        )

        val result = CatalogRecipes.derive(page)

        assertThat(result.styleKey).isEqualTo("green_generic")
        assertThat(result.recipes.first().method).isEqualTo(BrewMethod.KYUSU)
        assertThat(result.checkReason).isEqualTo(RecipeCheckReason.UNMAPPED_STYLE)
    }
}
