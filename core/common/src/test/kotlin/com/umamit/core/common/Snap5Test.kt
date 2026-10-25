package com.umamit.core.common

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/** GEN-12, 05 §7: temperatures snap to the nearest multiple of 5 °C, half up. */
class Snap5Test {

    @Test
    fun `values snap to the nearest multiple of five, half up`() {
        val cases = listOf(
            50.0 to 50,
            52.4 to 50,
            52.5 to 55,
            53.0 to 55,
            57.49 to 55,
            57.5 to 60,
            60.0 to 60,
            2.5 to 5,
            2.4 to 0,
            0.0 to 0,
            97.5 to 100,
            100.0 to 100,
        )
        cases.forEach { (value, expected) ->
            assertWithMessage("snap5($value)").that(snap5(value)).isEqualTo(expected)
        }
    }

    @Test
    fun `whole degrees snap half up too`() {
        val cases = listOf(50 to 50, 52 to 50, 53 to 55, 57 to 55, 58 to 60, 85 to 85, 99 to 100)
        cases.forEach { (value, expected) ->
            assertWithMessage("snap5($value)").that(snap5(value)).isEqualTo(expected)
        }
    }
}
