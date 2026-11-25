package com.umamit.feature.collection

import com.google.common.truth.Truth.assertThat
import com.umamit.testlocale.inLocale
import org.junit.Test
import java.util.Locale

/** A tea's country by its code in the app's language (GEN-13); anything that is no code stays as typed. */
class CountryNamesTest {

    @Test
    fun `German names by default`() {
        assertThat(countryName("JP")).isEqualTo("Japan")
        assertThat(countryName("KR")).isEqualTo("Südkorea")
        assertThat(countryName("Uji")).isEqualTo("Uji")
    }

    @Test
    fun `English names in English`() = inLocale(Locale.US) {
        assertThat(countryName("JP")).isEqualTo("Japan")
        assertThat(countryName("KR")).isEqualTo("South Korea")
        assertThat(countryName("Uji")).isEqualTo("Uji")
    }
}
