package com.umamit.feature.journal.session

import com.google.common.truth.Truth.assertThat
import com.umamit.testlocale.inLocale
import org.junit.Test
import java.util.Locale

/** A count with thousands separators in the app's language (rule 4, GEN-10). */
class GroupedCountTest {
    @Test
    fun `German groups with a dot`() {
        assertThat(grouped(4501)).isEqualTo("4.501")
        assertThat(grouped(12)).isEqualTo("12")
    }

    @Test
    fun `English groups with a comma`() = inLocale(Locale.US) {
        assertThat(grouped(4501)).isEqualTo("4,501")
    }
}
