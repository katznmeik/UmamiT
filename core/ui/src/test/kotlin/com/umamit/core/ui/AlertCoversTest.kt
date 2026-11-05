package com.umamit.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Which alert an overlay gives way to (rule 8, D-058): only the one it was opened in may play under it. */
class AlertCoversTest {

    @Test
    fun `no alert covers nothing`() {
        assertThat(alertCovers(alertKey = null, openedIn = null)).isFalse()
        assertThat(alertCovers(alertKey = null, openedIn = "s1:0:1")).isFalse()
    }

    @Test
    fun `an alert covers what was opened before it`() {
        assertThat(alertCovers(alertKey = "s1:0:1", openedIn = null)).isTrue()
    }

    @Test
    fun `the alert it was opened in does not cover it`() {
        assertThat(alertCovers(alertKey = "s1:0:1", openedIn = "s1:0:1")).isFalse()
    }

    @Test
    fun `a later alert covers what was opened during an earlier one`() {
        assertThat(alertCovers(alertKey = "s1:1:2", openedIn = "s1:0:1")).isTrue()
    }
}
