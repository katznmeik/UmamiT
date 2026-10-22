package com.umamit.feature.brew.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.StepKind
import com.umamit.core.timer.engine.PreAlertChoice
import com.umamit.core.timer.engine.StepStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Schritte-Sheet (SCR-31) open over a screen, in light and dark at font
 * scale 1.0 and 2.0 (03 §6): with done steps, without a recipe (quick timer),
 * and with many steps. Goldens: `src/test/screenshots/`.
 */
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

        private const val TAG = "screen"
    }

    @get:Rule
    val compose = createComposeRule()

    private val variant get() = "${if (dark) "dark" else "light"}_font${fontScale.toString().replace('.', '_')}"

    private fun capture(name: String, input: StepsSheetInput) {
        compose.setContent {
            UmamiTheme(darkTheme = dark, reduceMotion = true) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    Box(
                        modifier = Modifier
                            .testTag(TAG)
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
        }
        compose.onNodeWithTag(TAG)
            .captureRoboImage("${roborazziSystemPropertyOutputDirectory()}/StepsSheet_${name}_$variant.png")
    }

    @Test
    fun withDoneSteps() = capture(
        "withDoneSteps",
        StepsSheetInput(
            steps = listOf(
                step(StepKind.INFUSION, 90, 55, StepStatus.DONE),
                step(StepKind.INFUSION, 30, 60, StepStatus.STARTED),
                step(StepKind.INFUSION, 30, 60),
                step(StepKind.INFUSION, 20, null),
            ),
            preAlert = PreAlertChoice.Inherit,
            hasRecipe = true,
            baseTempC = 60,
        ),
    )

    @Test
    fun withoutRecipe() = capture(
        "withoutRecipe",
        StepsSheetInput(
            steps = listOf(step(StepKind.INFUSION, 180, null)),
            preAlert = PreAlertChoice.Seconds(15),
            hasRecipe = false,
            baseTempC = null,
        ),
    )

    @Test
    fun manySteps() = capture(
        "manySteps",
        StepsSheetInput(
            steps = listOf(step(StepKind.RINSE, 10, 80, StepStatus.DONE)) +
                (1..8).map { step(StepKind.INFUSION, 30 + it * 15, 60 + it / 2 * 5) } +
                step(StepKind.INFUSION, 14_400, 5),
            preAlert = PreAlertChoice.Off,
            hasRecipe = true,
            baseTempC = 60,
        ),
    )

    private fun step(kind: StepKind, seconds: Int, tempC: Int?, status: StepStatus = StepStatus.PENDING) =
        StepsSheetStep(kind, seconds, tempC, status)
}
