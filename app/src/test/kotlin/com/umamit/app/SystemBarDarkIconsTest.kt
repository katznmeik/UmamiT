package com.umamit.app

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * The rules of [systemBarDarkIcons] (D-069) in both app themes, for every surface on top. From v0.8.0 the timer and
 * the quick timer follow the app's theme (SET-05, D-128 Q20): light icons on the dark timer, dark on the light one.
 */
class SystemBarDarkIconsTest {

    @Test
    fun timerOnTopFollowsTheAppThemeAndIsDarkOnTheAlert() {
        assertWithMessage("dark timer")
            .that(systemBarDarkIcons(timerOnTop = true, alerting = false, darkTheme = true))
            .isFalse()
        assertWithMessage("light timer")
            .that(systemBarDarkIcons(timerOnTop = true, alerting = false, darkTheme = false))
            .isTrue()
        for (darkTheme in listOf(false, true)) {
            assertWithMessage("alerting, dark theme $darkTheme")
                .that(systemBarDarkIcons(timerOnTop = true, alerting = true, darkTheme = darkTheme))
                .isTrue()
        }
    }

    @Test
    fun everythingElseFollowsTheAppTheme() {
        // The quick timer before its start (SCR-07) is such a route: it follows the app's theme too.
        assertWithMessage("shell, light theme")
            .that(systemBarDarkIcons(timerOnTop = false, alerting = false, darkTheme = false))
            .isTrue()
        assertWithMessage("shell, dark theme")
            .that(systemBarDarkIcons(timerOnTop = false, alerting = true, darkTheme = true))
            .isFalse()
    }

    @Test
    fun photoViewerOnTopIsAlwaysLight() {
        for (darkTheme in listOf(false, true)) {
            assertWithMessage("photo viewer, dark theme $darkTheme")
                .that(
                    systemBarDarkIcons(
                        timerOnTop = false,
                        alerting = false,
                        darkTheme = darkTheme,
                        photoViewerOnTop = true,
                    ),
                )
                .isFalse()
        }
    }
}
