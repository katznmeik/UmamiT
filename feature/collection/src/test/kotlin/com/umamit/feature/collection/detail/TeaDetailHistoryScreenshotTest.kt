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
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * VORRAT's stock history (INV-03, SCR-29, D-104) in light and dark at font scale 1.0 and 2.0 (03 §6): seven rows of
 * every kind, the newest five and *Alle 7 Einträge anzeigen*, and the delete question of a purchase. Invented tea,
 * no photo (D-016). Goldens: `src/test/screenshots/`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TeaDetailHistoryScreenshotTest(private val dark: Boolean, private val fontScale: Float) {

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

    private fun show() {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            UmamiTheme(darkTheme = dark, reduceMotion = true) {
                TeaDetailScreen(
                    TeaDetailUiState.Ready(historySample()),
                    TeaDetailActions(openSession = {}),
                    photo = { null },
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun history() {
        show()
        compose.onNodeWithTag(TEA_DETAIL_HISTORY_MORE_TAG).performScrollTo()
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/TeaDetail_history_$variant.png")
    }

    @Test
    fun deleteQuestion() {
        show()
        compose.onNodeWithTag(teaDetailHistoryDeleteTag("p1")).performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/TeaDetail_history_delete_$variant.png")
    }
}

/** The history on a short screen (384 × 832 dp) at font scale 2.0: rows, amounts and actions stay legible. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w384dp-h832dp-xhdpi")
class TeaDetailHistoryShortScreenScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun history() {
        RuntimeEnvironment.setFontScale(2f)
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                TeaDetailScreen(
                    TeaDetailUiState.Ready(historySample()),
                    TeaDetailActions(openSession = {}),
                    photo = { null },
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag(teaDetailHistoryRowTag("p1")).performScrollTo()
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/TeaDetail_short_history_font2_0.png")
    }
}

/** Seven synthetic events, newest first: a correction, brews, purchases with and without a price. */
private fun historySample() = TeaDetailScreenTest.sample().copy(
    today = LocalDate.of(2026, 11, 8),
    history = listOf(
        StockEntry("a1", StockEntryKind.CORRECTION, -12.5, null, LocalDate.of(2026, 11, 7)),
        StockEntry("b2", StockEntryKind.BREW, -5.0, null, LocalDate.of(2026, 11, 6), sessionId = "s2"),
        StockEntry("p2", StockEntryKind.PURCHASE, 50.0, null, LocalDate.of(2026, 11, 2)),
        StockEntry("b1", StockEntryKind.BREW, -4.5, null, LocalDate.of(2026, 10, 30), sessionId = "s1"),
        StockEntry("p1", StockEntryKind.PURCHASE, 100.0, 1890, LocalDate.of(2026, 10, 20)),
        StockEntry("a0", StockEntryKind.CORRECTION, 3.0, null, LocalDate.of(2026, 10, 12)),
        StockEntry("initial", StockEntryKind.PURCHASE, 50.0, 990, LocalDate.of(2026, 10, 1)),
    ),
)
