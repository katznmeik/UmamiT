package com.umamit.feature.brew.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.StepKind
import com.umamit.core.timer.engine.StepStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Schritte-Sheet (SCR-31) open over a screen, in light and dark at font
 * scale 1.0 and 2.0 (03 §6): with done steps, without a recipe (quick timer),
 * and with many steps. Goldens: `src/test/screenshots/`.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class StepsSheetScreenshotTest(private val dark: Boolean, private val fontScale: Float) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}, fontScale={1}")
        fun parameters(): List<Array<Any>> = listOf(false, true).flatMap { dark ->
            listOf(1f, 2f).map { scale -> arrayOf<Any>(dark, scale) }
        }
    }

    @get:Rule
    val compose = createComposeRule()

    private val variant get() = "${if (dark) "dark" else "light"}_font${fontScale.toString().replace('.', '_')}"

    private fun capture(name: String, input: StepsSheetInput, act: () -> Unit = {}) {
        // The sheet's window takes the font scale from the configuration, not from a LocalDensity override.
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            UmamiTheme(darkTheme = dark, reduceMotion = true) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    // A stand-in for the host screen (SCR-04 / SCR-05) under the scrim.
                    Text(
                        text = "Bio Sencha Hisui",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(UmamiSpacing.SideMargin),
                    )
                    StepsSheet(
                        visible = true,
                        state = StepsSheetState(input),
                        onDone = {},
                        onSaveToRecipe = {},
                        onDismissRequest = {},
                    )
                }
            }
        }
        // The sheet has its own window: capture the whole screen, host and sheet.
        compose.waitForIdle()
        act()
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/StepsSheet_${name}_$variant.png")
    }

    @Test
    fun withDoneSteps() = capture(
        "withDoneSteps",
        StepsSheetInput(
            // The running step's selector stays; 0:20 gives „Auto · 5 s", 10 s on 0:10 is „zu kurz" (Q17, E7).
            steps = listOf(
                step(StepKind.INFUSION, 90, 55, StepStatus.DONE),
                step(StepKind.INFUSION, 30, 60, StepStatus.STARTED),
                step(StepKind.INFUSION, 30, 60, preAlert = 0),
                step(StepKind.INFUSION, 20, null),
                step(StepKind.INFUSION, 10, 65, preAlert = 10),
            ),
            hasRecipe = true,
            baseTempC = 60,
            preAlertBaseSeconds = 10,
        ),
    )

    @Test
    fun withoutRecipe() = capture(
        "withoutRecipe",
        StepsSheetInput(
            steps = listOf(step(StepKind.INFUSION, 180, null, preAlert = 15)),
            hasRecipe = false,
            baseTempC = null,
            preAlertBaseSeconds = 10,
        ),
    )

    @Test
    fun manySteps() = capture(
        "manySteps",
        StepsSheetInput(
            steps = listOf(step(StepKind.RINSE, 10, 80, StepStatus.DONE)) +
                (1..8).map { step(StepKind.INFUSION, 30 + it * 15, 60 + it / 2 * 5) } +
                step(StepKind.INFUSION, 14_400, 5),
            hasRecipe = true,
            baseTempC = 60,
            preAlertBaseSeconds = 10,
        ),
    )

    @Test
    fun hotStepsOfMinutes() = capture(
        "hotStepsOfMinutes",
        StepsSheetInput(
            // From v0.3.0 a hot step of 10–59 min has its Vorwarnung like any short step („Auto · 10 s"); from
            // 60 min it is a long step without one („Auto · keine", „10 s · keine"; TMR-18, D-098).
            steps = listOf(
                step(StepKind.INFUSION, 15 * 60, 95, StepStatus.STARTED),
                step(StepKind.INFUSION, 30 * 60, 95, preAlert = 20),
                step(StepKind.INFUSION, 60 * 60, 95),
                step(StepKind.INFUSION, 60 * 60, 95, preAlert = 10),
            ),
            hasRecipe = true,
            baseTempC = 95,
            preAlertBaseSeconds = 10,
        ),
    )

    @Test
    fun coldBrew() = capture(
        "coldBrew",
        StepsSheetInput(
            // Every step of a cold or ice brew is a long step, however short (TMR-18).
            steps = listOf(
                step(StepKind.INFUSION, 15 * 60, 5, StepStatus.STARTED),
                step(StepKind.INFUSION, 4 * 3_600, 5, preAlert = 10),
            ),
            hasRecipe = true,
            baseTempC = 5,
            preAlertBaseSeconds = 10,
            longStepsOnly = true,
        ),
    )

    /** A tap on a pending step's time opens „Dauer wählen" over the sheet, with its value (#141). */
    @Test
    fun durationPicker() = capture(
        "durationPicker",
        StepsSheetInput(
            steps = listOf(
                step(StepKind.INFUSION, 90, 55, StepStatus.DONE),
                step(StepKind.INFUSION, 300, null),
                step(StepKind.INFUSION, 30, 60),
            ),
            hasRecipe = true,
            baseTempC = 60,
            preAlertBaseSeconds = 10,
        ),
        act = {
            compose.onNodeWithContentDescription("Ziehzeit, Schritt 2: 5 Minuten. Ändern")
                .performScrollTo()
                .performClick()
        },
    )

    private fun step(
        kind: StepKind,
        seconds: Int,
        tempC: Int?,
        status: StepStatus = StepStatus.PENDING,
        preAlert: Int? = null,
    ) = StepsSheetStep(kind, seconds, tempC, status, preAlert)
}
