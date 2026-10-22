package com.umamit.core.designsystem.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GermanUppercaseTest {

    @Test
    fun `overline keeps umlauts when uppercased`() {
        assertThat("Zuletzt gebrüht".germanUppercase()).isEqualTo("ZULETZT GEBRÜHT")
    }

    @Test
    fun `sharp s becomes capital sharp s instead of SS`() {
        assertThat("Große Blätter".germanUppercase()).isEqualTo("GROẞE BLÄTTER")
    }

    @Test
    fun `macrons survive uppercasing`() {
        assertThat("Hōjicha".germanUppercase()).isEqualTo("HŌJICHA")
    }
}
