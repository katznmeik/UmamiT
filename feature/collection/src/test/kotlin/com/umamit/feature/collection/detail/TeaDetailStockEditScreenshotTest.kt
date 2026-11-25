package com.umamit.feature.collection.detail

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.theme.UmamiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * „Kauf bearbeiten" and „Korrektur bearbeiten" (INV-03, SCR-29, D-136 item 11) in light and dark at font scale 1.0
 * and 2.0 (03 §6): each sheet filled in from its history row, with *Löschen* and *Speichern*. Invented tea, no photo
 * (D-016). Goldens: `src/test/screenshots/`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TeaDetailStockEditScreenshotTest(private val dark: Boolean, private val fontScale: Float) {

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

    private fun open(eventId: String) {
        RuntimeEnvironment.setFontScale(fontScale)
        val tea = TeaDetailScreenTest.sample().copy(
            history = TeaDetailHistoryScreenTest.HISTORY,
            today = LocalDate.of(2026, 11, 8),
        )
        compose.setContent {
            UmamiTheme(darkTheme = dark, reduceMotion = true) {
                TeaDetailScreen(TeaDetailUiState.Ready(tea), TeaDetailActions(openSession = {}), photo = { null })
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag(teaDetailHistoryRowTag(eventId)).performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    @Test
    fun purchase() {
        open("p1")
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/TeaDetail_editPurchase_$variant.png")
    }

    @Test
    fun correction() {
        open("a1")
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/TeaDetail_editCorrection_$variant.png")
    }
}
