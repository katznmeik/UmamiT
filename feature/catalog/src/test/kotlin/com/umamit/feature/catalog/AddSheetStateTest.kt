package com.umamit.feature.catalog

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.CatalogPackage
import org.junit.Test
import java.time.LocalDate

/**
 * The add sheet's price and amount fields (SCR-23, CAT-12) with large values (D-090): a price of
 * 1.000 € or more prefills a field that is valid as it stands, and German thousands separators
 * typed by the user are read.
 */
class AddSheetStateTest {
    private fun sheet(priceCents: Long) = AddSheetState.open(
        teaName = "Morgennebel Sencha",
        packages = listOf(
            CatalogPackage(
                articleNumber = "XY0100-1000",
                label = "1000 g",
                grams = 1000.0,
                priceCents = priceCents,
                priceIsEstimate = false,
                savingsPercent = null,
                variantUrl = null,
                available = true,
            ),
        ),
        repurchase = false,
        lastBoughtGrams = null,
        today = LocalDate.of(2026, 10, 22),
    )

    @Test
    fun aPriceOfAThousandEurosOrMorePrefillsAValidField() {
        val sheet = sheet(priceCents = 123_450)

        assertThat(sheet.price).isEqualTo("1234,50")
        assertThat(sheet.priceValid).isTrue()
        assertThat(sheet.priceCents).isEqualTo(123_450)
        assertThat(sheet.canSave).isTrue()
    }

    @Test
    fun anExactPriceOfAThousandEurosOrMoreReplacesTheFieldValidly() {
        val sheet = sheet(priceCents = 99_000).fetching(0).fetched(0, cents = 101_000)

        assertThat(sheet.price).isEqualTo("1010,00")
        assertThat(sheet.priceValid).isTrue()
    }
}
