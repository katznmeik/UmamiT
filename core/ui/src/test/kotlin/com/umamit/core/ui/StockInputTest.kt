package com.umamit.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The total typed into „Wie viel ist noch da?" (BRW-02, INV-03): German input, 0,1 g steps, 0 to 9.999,9 g. */
class StockInputTest {

    @Test
    fun `a comma or a dot, at most one decimal, is a total`() {
        assertThat(StockInput.parse("23,5")).isEqualTo(23.5)
        assertThat(StockInput.parse("23.5")).isEqualTo(23.5)
        assertThat(StockInput.parse("0")).isEqualTo(0.0)
        assertThat(StockInput.parse(" 7 ")).isEqualTo(7.0)
    }

    @Test
    fun `negative, non-numeric, finer than 0,1 g or too large input is refused`() {
        assertThat(StockInput.parse("-1")).isNull()
        assertThat(StockInput.parse("abc")).isNull()
        assertThat(StockInput.parse("2,35")).isNull()
        assertThat(StockInput.parse("10000")).isNull()
        assertThat(StockInput.parse("")).isNull()
    }

    @Test
    fun `it opens on the stock to 0,1 g, never below 0`() {
        assertThat(StockInput.format(38.04)).isEqualTo("38,0")
        assertThat(StockInput.format(-2.0)).isEqualTo("0,0")
    }

    @Test
    fun `the steppers move by 0,1 g within 0 and the maximum`() {
        assertThat(StockInput.step("23,5", up = true)).isEqualTo("23,6")
        assertThat(StockInput.step("23,5", up = false)).isEqualTo("23,4")
        assertThat(StockInput.step("0", up = false)).isNull()
        assertThat(StockInput.step("abc", up = true)).isEqualTo("0,1")
        assertThat(StockInput.step("9999,9", up = true)).isNull()
    }
}
