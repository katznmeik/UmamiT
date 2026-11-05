package com.umamit.feature.collection.editor

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

/** The editor reads „Kauf" numbers the German way, like the add sheet (INV-01, D-090, F14). */
class TeaEditorFormNumbersTest {
    private val today = LocalDate.of(2026, 11, 5)
    private fun form(grams: String = "", price: String = "") =
        TeaEditorForm(name = "Testtee", purchaseDate = today, packageGrams = grams, price = price)

    @Test
    fun `thousands groups are accepted like in the add sheet`() {
        val form = form(grams = "1.000", price = "1.234,50 €")
        assertThat(form.errors(today)).isEmpty()
        assertThat(form.purchase()?.grams).isEqualTo(1000.0)
        assertThat(form.purchase()?.priceCents).isEqualTo(123_450L)
    }

    @Test
    fun `garbage and a price without a package are still refused`() {
        assertThat(form(grams = "abc").errors(today)).containsExactly(TeaFieldError.PackageGrams)
        assertThat(form(grams = "10.001").errors(today)).containsExactly(TeaFieldError.PackageGrams)
        assertThat(form(grams = "100", price = "-1").errors(today)).containsExactly(TeaFieldError.Price)
        assertThat(form(price = "4,50").errors(today)).containsExactly(TeaFieldError.PriceWithoutPackage)
    }
}
