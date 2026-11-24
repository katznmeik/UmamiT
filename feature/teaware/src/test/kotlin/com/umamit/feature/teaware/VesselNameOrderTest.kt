package com.umamit.feature.teaware

import com.google.common.truth.Truth.assertThat
import com.umamit.testlocale.inLocale
import org.junit.Test
import java.util.Locale

/** Vessels A–Z in the app's language (GEN-13): case and accents second, a fresh collator per sort. */
class VesselNameOrderTest {
    private val names = listOf("oolong-Kanne", "Öko-Kyusu", "Houhin", "Äsche-Schale")

    @Test
    fun `German order`() {
        assertThat(names.sortedWith(TeawareViewModel.byName))
            .containsExactly("Äsche-Schale", "Houhin", "Öko-Kyusu", "oolong-Kanne").inOrder()
    }

    @Test
    fun `English order`() = inLocale(Locale.US) {
        assertThat(names.sortedWith(TeawareViewModel.byName))
            .containsExactly("Äsche-Schale", "Houhin", "Öko-Kyusu", "oolong-Kanne").inOrder()
    }
}
