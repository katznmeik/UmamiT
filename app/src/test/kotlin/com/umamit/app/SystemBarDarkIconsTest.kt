package com.umamit.app

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/** The rules of [systemBarDarkIcons] (D-069) in both phone themes, for every surface on top. */
class SystemBarDarkIconsTest {

    @Test
    fun timerOnTopFollowsTheAlertInBothThemes() {
        for (darkTheme in listOf(false, true)) {
            assertWithMessage("steeping, dark theme $darkTheme")
                .that(
                    systemBarDarkIcons(
                        timerOnTop = true,
                        quickTimerOnTop = false,
                        alerting = false,
                        darkTheme = darkTheme,
                    ),
                )
                .isFalse()
            assertWithMessage("alerting, dark theme $darkTheme")
                .that(
                    systemBarDarkIcons(
                        timerOnTop = true,
                        quickTimerOnTop = false,
                        alerting = true,
                        darkTheme = darkTheme,
                    ),
                )
                .isTrue()
        }
    }

    @Test
    fun quickTimerOnTopIsAlwaysLight() {
        for (darkTheme in listOf(false, true)) {
            assertWithMessage("quick timer, dark theme $darkTheme")
                .that(
                    systemBarDarkIcons(
                        timerOnTop = false,
                        quickTimerOnTop = true,
                        alerting = false,
                        darkTheme = darkTheme,
                    ),
                )
                .isFalse()
        }
    }

    @Test
    fun everythingElseFollowsTheAppTheme() {
        assertWithMessage("shell, light theme")
            .that(systemBarDarkIcons(timerOnTop = false, quickTimerOnTop = false, alerting = false, darkTheme = false))
            .isTrue()
        assertWithMessage("shell, dark theme")
            .that(systemBarDarkIcons(timerOnTop = false, quickTimerOnTop = false, alerting = true, darkTheme = true))
            .isFalse()
    }
}
