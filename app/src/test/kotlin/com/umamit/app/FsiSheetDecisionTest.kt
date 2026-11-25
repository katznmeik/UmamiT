package com.umamit.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The Vollbild sheet's decision (TMR-31, SCR-34, D-136 item 13). */
class FsiSheetDecisionTest {
    private fun show(
        prompted: Int? = 0,
        current: Int = 1_000,
        onboardingCompleted: Boolean = true,
        allowed: Boolean = false,
        timerOnTop: Boolean = false,
        alerting: Boolean = false,
    ) = shouldShowFsiSheet(prompted, current, onboardingCompleted, allowed, timerOnTop, alerting)

    @Test
    fun `a new versionCode with the permission missing shows`() {
        assertThat(show()).isTrue()
        assertThat(show(prompted = 900)).isTrue()
    }

    @Test
    fun `the same versionCode does not show`() {
        assertThat(show(prompted = 1_000)).isFalse()
    }

    @Test
    fun `a granted permission does not show`() {
        assertThat(show(allowed = true)).isFalse()
    }

    @Test
    fun `the first start does not show`() {
        assertThat(show(onboardingCompleted = false)).isFalse()
    }

    @Test
    fun `a timer screen on top does not show`() {
        assertThat(show(timerOnTop = true)).isFalse()
    }

    @Test
    fun `an alert does not show`() {
        assertThat(show(alerting = true)).isFalse()
    }

    @Test
    fun `an unread stored code does not show`() {
        assertThat(show(prompted = null)).isFalse()
    }
}
