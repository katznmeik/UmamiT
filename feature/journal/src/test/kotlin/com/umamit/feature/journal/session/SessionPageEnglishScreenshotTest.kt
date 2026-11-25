package com.umamit.feature.journal.session

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.StepKind
import com.umamit.core.model.TasteProfile
import com.umamit.core.ui.INFUSION_TASTE_RESET_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The session page (SCR-16) in English (GEN-13, #359), light and dark at font scale 1.0: the page of
 * [SessionPageScreenshotTest] (synthetic data, D-016), rated with a note, and its taste editor unfolded. Goldens:
 * `src/test/screenshots/`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w411dp-h891dp-xhdpi")
class SessionPageEnglishScreenshotTest(private val dark: Boolean) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun parameters(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }

    @get:Rule
    val compose = createComposeRule()

    private val variant get() = if (dark) "dark" else "light"

    private val page = SessionPageScreenshotTest.page

    private fun capture(
        name: String,
        state: SessionPageUiState,
        scrollToTag: String? = null,
        click: String? = null,
        thenScrollTo: String? = null,
    ) {
        compose.setContent { UmamiTheme(darkTheme = dark, reduceMotion = true) { SessionPageScreen(state) } }
        compose.waitForIdle()
        if (scrollToTag != null) compose.onNodeWithTag(scrollToTag).performScrollTo()
        if (click != null) compose.onNodeWithTag(click).performClick()
        compose.waitForIdle()
        if (thenScrollTo != null) compose.onNodeWithTag(thenScrollTo).performScrollTo()
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/SessionPageEn_${name}_$variant.png")
    }

    @Test
    fun available() = capture("available", SessionPageUiState.Ready(page))

    @Test
    fun ratedWithNote() = capture(
        "ratedWithNote",
        SessionPageUiState.Ready(
            page.copy(
                rating = 3.5,
                brewingRating = 4.0,
                notes = "Mild and creamy, hardly any bitterness. The second infusion adds a light sweetness.",
            ),
        ),
    )

    @Test
    fun tasteUnfolded() {
        val steps = page.steps.map { if (it.infusionNumber == 3) it.copy(actualSeconds = 32) else it }
        val infusions = listOf(
            SessionInfusion(
                position = 1,
                number = 1,
                kind = StepKind.INFUSION,
                taste = TasteProfile(umami = 5, sweetness = 3, body = 4),
                note = "Soft",
            ),
            SessionInfusion(position = 3, number = 3, kind = StepKind.INFUSION, taste = TasteProfile.EMPTY),
        )
        capture(
            "tasteUnfolded",
            SessionPageUiState.Ready(page.copy(steps = steps, infusions = infusions)),
            scrollToTag = SESSION_TASTE_ADJUST_TAG,
            click = SESSION_TASTE_ADJUST_TAG,
            thenScrollTo = INFUSION_TASTE_RESET_TAG,
        )
    }
}
