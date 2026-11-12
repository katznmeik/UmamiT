package com.umamit.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The app theme for the stored *Design* choice (SET-05, D-114) against the phone's own theme. */
@RunWith(RobolectricTestRunner::class)
class UmamiAppThemeTest {
    @get:Rule
    val compose = createComposeRule()

    private fun isDark(mode: ThemeMode, systemDark: Boolean): Boolean {
        var dark: Boolean? = null
        var background: androidx.compose.ui.graphics.Color? = null
        compose.setContent {
            UmamiAppTheme(mode = mode, systemDark = systemDark, reduceMotion = true) {
                Probe { d, b ->
                    dark = d
                    background = b
                }
            }
        }
        compose.waitForIdle()
        val expected = if (dark == true) SteepingColorScheme.background else WashiColorScheme.background
        assertThat(background).isEqualTo(expected)
        return dark!!
    }

    @Composable
    private fun Probe(report: (Boolean, androidx.compose.ui.graphics.Color) -> Unit) {
        report(UmamiTheme.isDark, MaterialTheme.colorScheme.background)
    }

    @Test
    fun `Dunkel is dark although the phone is light`() {
        assertThat(isDark(ThemeMode.DARK, systemDark = false)).isTrue()
    }

    @Test
    fun `Hell is light although the phone is dark`() {
        assertThat(isDark(ThemeMode.LIGHT, systemDark = true)).isFalse()
    }

    @Test
    fun `System follows the phone`() {
        assertThat(isDark(ThemeMode.SYSTEM, systemDark = false)).isFalse()
    }

    @Test
    fun `System follows a dark phone`() {
        assertThat(isDark(ThemeMode.SYSTEM, systemDark = true)).isTrue()
    }

    @Test
    fun `isDark resolves every choice`() {
        for (system in listOf(false, true)) {
            assertThat(ThemeMode.SYSTEM.isDark(system)).isEqualTo(system)
            assertThat(ThemeMode.LIGHT.isDark(system)).isFalse()
            assertThat(ThemeMode.DARK.isDark(system)).isTrue()
        }
    }
}
