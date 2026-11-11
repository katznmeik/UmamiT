package com.umamit.feature.journal.session

import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.StepKind
import com.umamit.core.model.Vessel
import com.umamit.core.model.VesselType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Sitzung bearbeiten (SCR-17, JRN-11, D-112) in light and dark at font scale 1.0 and 2.0 (03 §6): untouched; with a
 * leaf change and its stock line; with the future refused. A synthetic session (D-016): a rinse, an infusion run into
 * overtime, a skipped one and one never started. Goldens: `src/test/screenshots/`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class SessionEditorScreenshotTest(private val dark: Boolean, private val fontScale: Float) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}, fontScale={1}")
        fun parameters(): List<Array<Any>> = listOf(false, true).flatMap { dark ->
            listOf(1f, 2f).map { scale -> arrayOf<Any>(dark, scale) }
        }

        private val zone: ZoneId = ZoneId.of("Europe/Berlin")
        private val start = LocalDateTime.of(2026, 10, 22, 9, 12).atZone(zone).toInstant().toEpochMilli()

        private val form = SessionEditorForm(
            startedAtMs = start,
            vesselId = "v1",
            leaf = "4,5",
            water = "180",
            steps = listOf(
                SessionEditorStepForm(0, "0:10", 80),
                SessionEditorStepForm(1, "1:35", 65),
            ),
        )

        private val untouched = SessionEditorUiState.Ready(
            form = form,
            zone = zone,
            today = LocalDate.of(2026, 10, 22),
            vessels = listOf(Vessel("v1", "Kyusu 250 ml", VesselType.KYUSU, 250)),
            vesselName = "Kyusu 250 ml",
            steps = listOf(
                SessionEditorStepRow(0, StepKind.RINSE, form.steps[0], skipped = false),
                SessionEditorStepRow(1, StepKind.INFUSION, form.steps[1], skipped = false),
                SessionEditorStepRow(2, StepKind.INFUSION, null, skipped = true),
                SessionEditorStepRow(3, StepKind.INFUSION, null, skipped = false),
            ),
            errors = emptySet(),
            stockChangeGrams = null,
            hasChanges = false,
            canSave = false,
        )
    }

    @get:Rule
    val compose = createComposeRule()

    private val variant get() = "${if (dark) "dark" else "light"}_font${fontScale.toString().replace('.', '_')}"

    private fun capture(name: String, state: SessionEditorUiState) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent { UmamiTheme(darkTheme = dark, reduceMotion = true) { SessionEditorScreen(state) } }
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/SessionEditor_${name}_$variant.png")
    }

    @Test
    fun untouched() = capture("untouched", untouched)

    /** The leaf changed from 4,5 g to 6,5 g: „Dein Vorrat wird um 2 g angepasst.". */
    @Test
    fun stockLine() = capture(
        "stock_line",
        untouched.copy(
            form = form.copy(leaf = "6,5"),
            stockChangeGrams = 2.0,
            hasChanges = true,
            canSave = true,
        ),
    )

    /** A start after now: „Die Sitzung kann nicht in der Zukunft liegen.", *Speichern* greyed out. */
    @Test
    fun error() = capture(
        "error",
        untouched.copy(
            form = form.copy(startedAtMs = start + 6 * 3_600_000L),
            errors = setOf(SessionEditorError.InFuture),
            hasChanges = true,
        ),
    )
}
