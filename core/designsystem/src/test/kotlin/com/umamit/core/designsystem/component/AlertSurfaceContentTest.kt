package com.umamit.core.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.designsystem.theme.DefaultExtendedColors
import com.umamit.core.designsystem.theme.SteepingTheme
import com.umamit.core.designsystem.theme.TEXT_CONTRAST
import com.umamit.core.designsystem.theme.UI_CONTRAST
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.designsystem.theme.contrast
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The design-system components on the [AlertSurface] are alert-safe with their
 * default parameters (03 §3.6, rule 8): a host that forgets the alert colours
 * still gets an ink ring and an ink pill, readable at both ends of the pulse.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class AlertSurfaceContentTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val alert = DefaultExtendedColors.alert
    private val phases = mapOf("washi" to alert.washi, "matcha" to alert.matcha)

    private class Resolved(val ring: TimerRingColors, val pill: ButtonColors, val contentColor: Color)

    @Test
    fun `default ring, pill and text colours keep their contrast in both pulse phases and both themes`() {
        val byTheme = mutableMapOf<Boolean, Resolved>()
        compose.setContent {
            Column {
                listOf(false, true).forEach { dark ->
                    UmamiTheme(darkTheme = dark, reduceMotion = true) {
                        AlertSurface {
                            byTheme[dark] =
                                Resolved(
                                    TimerRingDefaults.colors(),
                                    PillButtonDefaults.colors(),
                                    LocalContentColor.current,
                                )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        byTheme.forEach { (dark, resolved) ->
            phases.forEach { (phase, surface) ->
                val where = "${if (dark) "dark" else "light"} theme, $phase phase"
                assertWithMessage(
                    "text, $where",
                ).that(contrast(resolved.contentColor, surface)).isAtLeast(TEXT_CONTRAST)
                assertWithMessage(
                    "digits, $where",
                ).that(contrast(resolved.ring.content, surface)).isAtLeast(TEXT_CONTRAST)
                assertWithMessage(
                    "caption, $where",
                ).that(contrast(resolved.ring.caption, surface)).isAtLeast(TEXT_CONTRAST)
                assertWithMessage("ring, $where").that(contrast(resolved.ring.progress, surface)).isAtLeast(UI_CONTRAST)
                assertWithMessage("marker, $where").that(contrast(resolved.ring.marker, surface)).isAtLeast(UI_CONTRAST)
                assertWithMessage(
                    "pill, $where",
                ).that(contrast(resolved.pill.containerColor, surface)).isAtLeast(UI_CONTRAST)
            }
            assertWithMessage("pill label").that(contrast(resolved.pill.contentColor, resolved.pill.containerColor))
                .isAtLeast(TEXT_CONTRAST)
            assertWithMessage("no liquor inside the alert").that(resolved.ring.liquorAlpha).isEqualTo(0f)
        }
        assertThat(byTheme.keys).containsExactly(false, true)
    }

    @Test
    fun `a default ring and pill render in ink at the washi phase`() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SteepingTheme(reduceMotion = false) {
                AlertSurface {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        TimerRing(
                            progress = { 1f },
                            remainingSeconds = 0,
                            caption = "Jetzt abgießen!",
                            liquorColor = DefaultExtendedColors.liquor.green,
                            modifier = Modifier.testTag(RING),
                        )
                        PillButton(text = "Abgegossen", onClick = {}, modifier = Modifier.testTag(PILL))
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(HALF_CYCLE_MILLIS)

        val bitmap = compose.windowBitmap()
        val ring = compose.boundsOf(RING)
        val pill = compose.boundsOf(PILL)
        // The washi phase: the surface beside the ring is washi.
        assertThat(bitmap.colorAt(ring.left + 2f, ring.top + 2f).distanceTo(alert.washi)).isLessThan(SAME)
        // The pill's container, left of its label.
        assertThat(bitmap.colorAt(pill.left + pill.height / 2, pill.center.y).distanceTo(alert.content))
            .isLessThan(SAME)
        // The full ring at its top: the progress arc crosses the centre column 14 dp below the top.
        val ringTop = ring.top + compose.density.run { RING_INSET.toPx() }
        assertThat(bitmap.colorAt(ring.center.x, ringTop).distanceTo(alert.content)).isLessThan(SAME)
        // The digits: the darkest pixel in the ring's centre is ink, not the Steeping cream.
        var darkest = alert.washi
        val y0 = ring.center.y.toInt() - DIGITS_HALF_HEIGHT_PX
        val y1 = ring.center.y.toInt() + DIGITS_HALF_HEIGHT_PX
        for (y in y0..y1) {
            for (x in (ring.center.x - ring.width / 4).toInt()..(ring.center.x + ring.width / 4).toInt()) {
                val c = bitmap.colorAt(x.toFloat(), y.toFloat())
                if (c.red + c.green + c.blue < darkest.red + darkest.green + darkest.blue) darkest = c
            }
        }
        assertThat(darkest.distanceTo(alert.content)).isLessThan(SAME)
    }

    private companion object {
        const val RING = "ring"
        const val PILL = "pill"
        const val HALF_CYCLE_MILLIS = 750L
        const val SAME = 0.06f
        const val DIGITS_HALF_HEIGHT_PX = 40
        val RING_INSET = 14.dp
    }
}
