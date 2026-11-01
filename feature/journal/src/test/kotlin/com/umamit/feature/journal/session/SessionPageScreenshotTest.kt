package com.umamit.feature.journal.session

import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.StepKind
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime

/**
 * The session page (SCR-16 read-only, D-100) in light and dark at font scale 1.0 and 2.0 (03 §6): a tea of the
 * collection with a rinse, an infusion run into overtime, a skipped one and one never started; an archived tea (the
 * button greyed out with *Wiederherstellen*); a deleted tea (no link, no button). Goldens: `src/test/screenshots/`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class SessionPageScreenshotTest(private val dark: Boolean, private val fontScale: Float) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}, fontScale={1}")
        fun parameters(): List<Array<Any>> = listOf(false, true).flatMap { dark ->
            listOf(1f, 2f).map { scale -> arrayOf<Any>(dark, scale) }
        }

        /** A synthetic session (D-016): a rinse, an overtime infusion, a skipped one and one never started. */
        val page = SessionPage(
            sessionId = "past",
            teaId = "morgentau",
            teaName = "Sencha Morgentau",
            teaSecondaryName = "Frühlingsernte",
            styleKey = "sencha",
            tea = SessionTea.AVAILABLE,
            startedAt = LocalDateTime.of(2026, 10, 22, 9, 12),
            method = BrewMethod.KYUSU,
            vesselName = "Kyusu 250 ml",
            leafGrams = 4.5,
            waterMl = 180,
            steps = listOf(
                SessionPageStep(StepKind.RINSE, null, 10, 10, 80, skipped = false),
                SessionPageStep(StepKind.INFUSION, 1, 75, 95, 65, skipped = false),
                SessionPageStep(StepKind.INFUSION, 2, 20, 0, 70, skipped = true),
                SessionPageStep(StepKind.INFUSION, 3, 30, null, 75, skipped = false),
            ),
        )
    }

    @get:Rule
    val compose = createComposeRule()

    private val variant get() = "${if (dark) "dark" else "light"}_font${fontScale.toString().replace('.', '_')}"

    private fun capture(name: String, state: SessionPageUiState) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent { UmamiTheme(darkTheme = dark, reduceMotion = true) { SessionPageScreen(state) } }
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/SessionPage_${name}_$variant.png")
    }

    @Test
    fun available() = capture("available", SessionPageUiState.Ready(page))

    @Test
    fun archivedTea() = capture("archivedTea", SessionPageUiState.Ready(page.copy(tea = SessionTea.ARCHIVED)))

    @Test
    fun deletedTea() =
        capture("deletedTea", SessionPageUiState.Ready(page.copy(teaId = null, tea = SessionTea.DELETED)))
}
