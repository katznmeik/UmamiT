package com.umamit.feature.brew.timer

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.component.TimerRingColors
import com.umamit.core.designsystem.component.TimerRingDefaults
import com.umamit.core.designsystem.theme.SteepingTheme
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.TimerPhase
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.engine.PreAlertChoice
import com.umamit.feature.brew.quick.QUICK_RING_TAG
import com.umamit.feature.brew.quick.QuickTimerActions
import com.umamit.feature.brew.quick.QuickTimerScreen
import com.umamit.feature.brew.quick.QuickTimerUiState
import com.umamit.feature.brew.timer.TimerStates.NOW
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * SCR-05's layout and colours: the pill stays where the finger is (spatial
 * consistency, TMR-03), the ink-wash band and the tea seal (D-095) leave the
 * alert surface without moving anything, on a short screen the band gives way
 * so *Abgegossen* lies on the screen (D-096), overlays survive a configuration
 * change (GEN-07), and the paused ring keeps its text readable (03 §6).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TimerScreenLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    private var state by mutableStateOf<TimerUiState.Active>(TimerStates.ready())

    /** A synthetic white-backed tea photo (never a Tee-Kontor photo, D-016). */
    private val whitePhoto = ColorPainter(Color.White)

    private var photo by mutableStateOf<Painter?>(whitePhoto)

    private fun show(initial: TimerUiState.Active) {
        state = initial
        val start = compose.mainClock.currentTime
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                TimerScreen(
                    state = state,
                    nowElapsedMs = { NOW + compose.mainClock.currentTime - start },
                    onEvent = {},
                    onBack = {},
                    onAdjustBrew = {},
                    onOpenTimerSettings = {},
                    reliabilityProblem = true,
                    teaPhoto = photo,
                    teaStyleKey = "gyokuro",
                )
            }
        }
    }

    private fun top(text: String): Dp = compose.onNodeWithText(text).getUnclippedBoundsInRoot().top

    private fun tagTop(tag: String): Dp =
        compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot().top

    private fun band() = compose.onNodeWithTag(TIMER_BAND_TAG, useUnmergedTree = true)

    private fun seal() = compose.onNodeWithTag(TIMER_SEAL_TAG, useUnmergedTree = true)

    private fun sealShows(content: SealContent) = SemanticsMatcher.expectValue(SealContentKey, content)

    @Test
    fun `band and seal leave the alert surface, and the title, ring and pill do not move (D-095, D-058)`() {
        show(TimerStates.ready())
        band().assertExists()
        seal().assertExists()
        val ready = listOf(top("Bio Uji Gyokuro Gokō"), tagTop(TIMER_RING_TAG), top("Start"))

        state = TimerStates.running()
        compose.waitForIdle()
        band().assertExists()
        seal().assertExists()
        val running = listOf(top("Bio Uji Gyokuro Gokō"), tagTop(TIMER_RING_TAG), top("Pause"))

        state = TimerStates.alerting(overtimeMs = 7_000, stepIndex = 0)
        compose.waitForIdle()
        band().assertDoesNotExist()
        seal().assertDoesNotExist()
        val alert = listOf(top("Bio Uji Gyokuro Gokō"), tagTop(TIMER_RING_TAG), top("Abgegossen"))

        assertThat(running).isEqualTo(ready)
        assertThat(alert).isEqualTo(ready)
    }

    @Test
    fun `the seal is 88 dp, centred, and half over the band's lower edge`() {
        show(TimerStates.running())
        val band = band().getUnclippedBoundsInRoot()
        val seal = seal().getUnclippedBoundsInRoot()

        assertThat(seal.width).isEqualTo(88.dp)
        assertThat(seal.height).isEqualTo(88.dp)
        assertThat(band.height).isEqualTo(220.dp)
        assertThat((seal.top + seal.bottom) / 2).isEqualTo(band.bottom)
        assertThat((seal.left + seal.right) / 2).isEqualTo((band.left + band.right) / 2)
    }

    @Test
    fun `the seal shows the tea's photo, and the type-coloured leaf without one`() {
        show(TimerStates.running())
        seal().assert(sealShows(SealContent.Photo))

        photo = null
        compose.waitForIdle()
        seal().assert(sealShows(SealContent.Leaf))
    }

    @Test
    fun `the band and the seal are decorative, the title names the tea`() {
        show(TimerStates.running())
        val noDescription = SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription)
        band().assert(noDescription)
        seal().assert(noDescription)
    }

    @Test
    fun `the quick timer shows the band without a seal (SCR-07)`() {
        photo = null
        show(TimerStates.quick(TimerPhase.RUNNING))
        band().assertExists()
        seal().assertDoesNotExist()
    }

    @Test
    fun `the quick timer's ring stays where it was before Start (SCR-07)`() {
        var started by mutableStateOf(false)
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                if (started) {
                    TimerScreen(TimerStates.quick(TimerPhase.RUNNING), { NOW }, {}, {}, {}, {})
                } else {
                    QuickTimerScreen(state = QuickTimerUiState(durationSeconds = 90), actions = QuickTimerActions())
                }
            }
        }
        band().assertExists()
        val beforeStart = tagTop(QUICK_RING_TAG)

        started = true
        compose.waitForIdle()
        band().assertExists()
        assertThat(tagTop(TIMER_RING_TAG)).isEqualTo(beforeStart)
    }

    @Test
    fun `the pill does not move at Start, at the alert, nor when the overtime turns large`() = pillStaysPut()

    /**
     * Font scale 2.0 from the configuration, before the activity starts: only that applies Android's
     * non-linear font scaling, under which two lines of text are not twice one line. Real text metrics
     * (`GraphicsMode.NATIVE`) are needed for the heights to differ at all.
     */
    @Test
    @Config(fontScale = 2.0f)
    fun `the pill does not move at font scale 2_0 either (non-linear font scaling)`() = pillStaysPut()

    private fun pillStaysPut() {
        show(TimerStates.ready())
        val ready = top("Start")

        state = TimerStates.running()
        compose.waitForIdle()
        val running = top("Pause")

        state = TimerStates.alerting(overtimeMs = 7_000)
        compose.waitForIdle()
        val alert = top("Abgegossen")

        compose.mainClock.advanceTimeBy(8_000)
        compose.onNodeWithText("+0:15 überzogen", useUnmergedTree = true).assertIsDisplayed()
        val prominent = top("Abgegossen")

        assertThat(listOf(running, alert, prominent)).containsExactly(ready, ready, ready)
    }

    /**
     * Rule 8: on a short screen at the largest font, the band and the seal give way so that the
     * alert's *Abgegossen* lies whole on the screen without scrolling, and still nothing moves when
     * the alert starts (D-058, D-096).
     */
    @Test
    @Config(qualifiers = "w411dp-h800dp-xhdpi", fontScale = 2.0f)
    fun `on a 411 x 800 dp screen at font scale 2_0 the alert's pill is on the screen`() =
        alertPillOnScreen(teaName = "Bio Uji Gyokuro Gokō", titleLines = 1)

    @Test
    @Config(qualifiers = "w384dp-h832dp-xhdpi", fontScale = 2.0f)
    fun `on a 384 x 832 dp screen at font scale 2_0 with a two-line tea name the alert's pill is on the screen`() =
        alertPillOnScreen(teaName = TWO_LINE_NAME, titleLines = 2)

    @Test
    @Config(qualifiers = "w411dp-h800dp-xhdpi")
    fun `on a 411 x 800 dp screen with a two-line tea name the alert's pill is on the screen`() =
        alertPillOnScreen(teaName = TWO_LINE_NAME, titleLines = 2)

    private fun alertPillOnScreen(teaName: String, titleLines: Int) {
        show(TimerStates.ready().copy(teaName = teaName))
        assertThat(titleLineCount(teaName)).isEqualTo(titleLines)
        val ready = listOf(top(teaName), tagTop(TIMER_RING_TAG), top("Start"))

        state = TimerStates.running().copy(teaName = teaName)
        compose.waitForIdle()
        val running = listOf(top(teaName), tagTop(TIMER_RING_TAG), top("Pause"))
        // The band gave way, and the seal still sits on its lower edge (D-095).
        val band = band().getUnclippedBoundsInRoot()
        val seal = seal().getUnclippedBoundsInRoot()
        assertThat((seal.top + seal.bottom) / 2).isEqualTo(band.bottom)

        state = TimerStates.alerting(overtimeMs = 7_000).copy(teaName = teaName)
        compose.waitForIdle()
        val alert = listOf(top(teaName), tagTop(TIMER_RING_TAG), top("Abgegossen"))
        val screen = compose.onRoot().getUnclippedBoundsInRoot()
        val pill = compose.onNodeWithText("Abgegossen").getUnclippedBoundsInRoot()
        assertThat(pill.top).isAtLeast(screen.top)
        assertThat(pill.bottom).isAtMost(screen.bottom)

        compose.mainClock.advanceTimeBy(8_000)
        compose.onNodeWithText("+0:15 überzogen", useUnmergedTree = true).assertIsDisplayed()
        assertThat(compose.onNodeWithText("Abgegossen").getUnclippedBoundsInRoot()).isEqualTo(pill)

        assertThat(listOf(running, alert)).containsExactly(ready, ready)
    }

    /** The quick timer has no seal and a one-line title: its *Fertig* fits as it is (SCR-07). */
    @Test
    @Config(qualifiers = "w411dp-h800dp-xhdpi", fontScale = 2.0f)
    fun `on a 411 x 800 dp screen at font scale 2_0 the quick timer's Fertig is on the screen`() {
        photo = null
        show(TimerStates.quick(TimerPhase.ALERTING))
        val screen = compose.onRoot().getUnclippedBoundsInRoot()
        val pill = compose.onNodeWithText("Fertig").getUnclippedBoundsInRoot()
        assertThat(pill.bottom).isAtMost(screen.bottom)
    }

    /**
     * Rule 8 before *Start* too: with the *Vorwarnung* row (D-097) SCR-07's *Start* lies whole on a short screen at
     * the largest font, for every kind of label, without scrolling.
     */
    @Test
    @Config(qualifiers = "w411dp-h800dp-xhdpi", fontScale = 2.0f)
    fun `on a 411 x 800 dp screen at font scale 2_0 the quick timer's Start is on the screen`() {
        var quick by mutableStateOf(QuickTimerUiState(durationSeconds = 120))
        compose.setContent {
            UmamiTheme(reduceMotion = true) { QuickTimerScreen(state = quick, actions = QuickTimerActions()) }
        }
        val states = listOf(
            QuickTimerUiState(durationSeconds = 120),
            QuickTimerUiState(durationSeconds = 20, preAlertChoice = PreAlertChoice.Seconds(20)),
            QuickTimerUiState(durationSeconds = 4_500, preAlertChoice = PreAlertChoice.Seconds(20)),
        )
        states.forEach { shown ->
            quick = shown
            compose.waitForIdle()
            val screen = compose.onRoot().getUnclippedBoundsInRoot()
            val pill = compose.onNodeWithText("Start").getUnclippedBoundsInRoot()
            assertThat(pill.top).isAtLeast(screen.top)
            assertThat(pill.bottom).isAtMost(screen.bottom)
        }
    }

    private fun titleLineCount(text: String): Int {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single().lineCount
    }

    @Test
    fun `the discard question and the Schritte-Sheet survive a configuration change`() {
        val restoration = StateRestorationTester(compose)
        state = TimerStates.running(stepIndex = 1)
        restoration.setContent {
            UmamiTheme(reduceMotion = true) {
                TimerScreen(
                    state = state,
                    nowElapsedMs = { NOW },
                    onEvent = {},
                    onBack = {},
                    onAdjustBrew = {},
                    onOpenTimerSettings = {},
                )
            }
        }
        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.onNodeWithText("Sitzung verwerfen").performClick()
        compose.onNodeWithText("Nichts wird gespeichert.").assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithText("Nichts wird gespeichert.").assertIsDisplayed()
    }

    private fun contrast(a: Color, b: Color): Double {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    @Test
    fun `the paused ring dims its stroke and liquor, while digits and caption keep at least 4,5 to 1`() {
        lateinit var colors: TimerRingColors
        var background = Color.Unspecified
        var liquor = Color.Unspecified
        compose.setContent {
            SteepingTheme(reduceMotion = true) {
                colors = TimerRingDefaults.colors()
                background = MaterialTheme.colorScheme.background
                liquor = UmamiTheme.extendedColors.liquor.green
            }
        }
        compose.waitForIdle()

        val paused = colors.dimmed(0.6f)
        val underText = liquor.copy(alpha = paused.liquorAlpha).compositeOver(background)

        assertThat(paused.liquorAlpha).isLessThan(colors.liquorAlpha)
        assertThat(paused.track.alpha).isLessThan(colors.track.alpha)
        assertThat(paused.content).isEqualTo(colors.content)
        assertThat(paused.caption).isEqualTo(colors.caption)
        assertThat(contrast(paused.caption, underText)).isAtLeast(4.5)
        assertThat(contrast(paused.content, underText)).isAtLeast(4.5)
    }

    @Test
    fun `dimming never makes a darker phone brighter (TMR-17)`() {
        assertThat(dimmedBrightness(systemLevel = 0.8f)).isEqualTo(DIMMED_BRIGHTNESS)
        assertThat(dimmedBrightness(systemLevel = 0.05f)).isEqualTo(0.05f)
        assertThat(dimmedBrightness(systemLevel = null)).isEqualTo(DIMMED_BRIGHTNESS)
    }

    private companion object {
        /** A synthetic tea name (D-016) that takes two lines of the title. */
        const val TWO_LINE_NAME = "Bio Japan Sencha Kagoshima Asatsuyu"
    }
}
