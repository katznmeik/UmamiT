package com.umamit.feature.brew.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.KettleWater
import com.umamit.core.model.RecipeCheckReason
import com.umamit.core.model.StepKind
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.feature.brew.picker.PickerTea
import com.umamit.feature.brew.picker.TeaPickerSheet
import com.umamit.feature.brew.picker.TeaPickerUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The tea picker (SCR-03) and the setup (SCR-04), in light and dark at font
 * scale 1.0 and 2.0 (03 §6): the seed teas of 08 §2.5a with placeholder
 * images only (D-016). Goldens: `src/test/screenshots/`.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class BrewSetupScreenshotTest(private val dark: Boolean, private val fontScale: Float) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}, fontScale={1}")
        fun parameters(): List<Array<Any>> = listOf(false, true).flatMap { dark ->
            listOf(1f, 2f).map { scale -> arrayOf<Any>(dark, scale) }
        }

        private val hisui = PickerTea("h", "Bio Sencha Hisui", "Sencha", null)
        private const val LONG_NAME = "Bio Gyokuro Asahi Premium aus Yame, Frühjahrsernte"
        private val longTea = PickerTea("l", LONG_NAME, "Gyokuro", null)
        private val tanabata = PickerTea("t", "Bio Sencha Tanabata", "Sencha", null)

        val kyusu = BrewSetupUiState.Ready(
            teaName = "Bio Sencha Hisui",
            secondaryName = null,
            stockGrams = 100.0,
            recipeCheckReason = null,
            recipes = listOf(RecipeChip("k", "Kyusu"), RecipeChip("w", "Westlich"), RecipeChip("c", "Kaltaufguss")),
            selectedRecipeId = "k",
            vessels = listOf(VesselOption("ky", "Kyusu", 250), VesselOption("mug", "Becher mit Sieb", 400)),
            selectedVesselId = "ky",
            method = BrewMethod.KYUSU,
            waterMl = 250,
            leafGrams = 5.0,
            ratioLocked = true,
            ratioPer100Ml = 2.0,
            baseTempC = 65,
            kettle = Kettle(65, KettleWater.HOT),
            steps = listOf(
                PlannedStep(StepKind.INFUSION, 60, 65),
                PlannedStep(StepKind.INFUSION, 10, 65),
                PlannedStep(StepKind.INFUSION, 10, 65),
            ),
            preAlertBaseSeconds = 10,
            canSaveToRecipe = false,
            question = null,
            alertKey = null,
        )
    }

    @get:Rule
    val compose = createComposeRule()

    private val variant get() = "${if (dark) "dark" else "light"}_font${fontScale.toString().replace('.', '_')}"

    private fun capture(name: String, act: () -> Unit = {}, content: @androidx.compose.runtime.Composable () -> Unit) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent { UmamiTheme(darkTheme = dark, reduceMotion = true) { content() } }
        compose.waitForIdle()
        act()
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/${name}_$variant.png")
    }

    @Test
    fun setup() = capture("BrewSetup_kyusu") { BrewSetupScreen(kyusu) }

    @Test
    fun setupChangedInTheBecher() = capture("BrewSetup_changed") {
        BrewSetupScreen(
            kyusu.copy(
                selectedVesselId = "mug",
                waterMl = 400,
                leafGrams = 8.0,
                canSaveToRecipe = true,
                // Changed per step in the Schritte-Sheet: the row reads „20 · 5 · –" (SCR-04, TMR-23).
                steps = listOf(
                    PlannedStep(StepKind.INFUSION, 60, 65, preAlertSeconds = 20),
                    PlannedStep(StepKind.INFUSION, 20, 65),
                    PlannedStep(StepKind.INFUSION, 10, 65),
                ),
            ),
        )
    }

    @Test
    fun setupColdBrew() = capture("BrewSetup_coldBrew") {
        BrewSetupScreen(
            kyusu.copy(
                selectedRecipeId = "c",
                method = BrewMethod.COLD_BREW,
                selectedVesselId = null,
                waterMl = 1000,
                leafGrams = 10.0,
                ratioPer100Ml = 1.0,
                baseTempC = 5,
                kettle = Kettle(5, KettleWater.COLD),
                steps = listOf(PlannedStep(StepKind.INFUSION, 14_400, 5)),
            ),
        )
    }

    /** A tea flagged „Rezept prüfen": the flag and its reason above the steps (CAT-05, SCR-04). */
    @Test
    fun setupRecipeCheck() = capture("BrewSetup_recipeCheck") {
        BrewSetupScreen(kyusu.copy(recipeCheckReason = RecipeCheckReason.FROM_STYLE_DEFAULT))
    }

    /** „38,0 g übrig" tapped: „Wie viel ist noch da?" on the current stock (BRW-02, INV-03; SCR-04). */
    @Test
    fun setupStockSheet() = capture("BrewSetup_stock", act = { tapStock() }) {
        BrewSetupScreen(kyusu.copy(stockGrams = 38.0))
    }

    /** A total that isn't one: the field says why and *Speichern* is off. */
    @Test
    fun setupStockSheetInvalid() = capture(
        "BrewSetup_stockInvalid",
        act = {
            tapStock()
            compose.onNode(hasSetTextAction()).performTextReplacement("23,45")
        },
    ) {
        BrewSetupScreen(kyusu.copy(stockGrams = 38.0))
    }

    /** At 0 g the header reads „Leer" (INV-05). */
    @Test
    fun setupEmpty() = capture("BrewSetup_empty") { BrewSetupScreen(kyusu.copy(stockGrams = 0.0)) }

    private fun tapStock() {
        compose.onNodeWithTag(SETUP_STOCK_TAG).performClick()
    }

    @Test
    fun setupSecondSessionQuestion() = capture("BrewSetup_question") {
        BrewSetupScreen(kyusu.copy(question = ActiveSessionQuestion("Bio Sencha Tanabata")))
    }

    @Test
    fun picker() = capture("TeaPicker") {
        PickerHost(
            TeaPickerUiState(
                recent = listOf(tanabata),
                favorites = listOf(hisui),
                all = listOf(hisui, tanabata),
                loading = false,
            ),
        )
    }

    @Test
    fun pickerLongName() = capture("TeaPicker_longName") {
        PickerHost(
            TeaPickerUiState(
                recent = listOf(longTea),
                favorites = listOf(longTea),
                all = listOf(longTea, hisui),
                loading = false,
            ),
        )
    }

    @Test
    fun setupLongName() = capture("BrewSetup_longName") {
        BrewSetupScreen(kyusu.copy(teaName = LONG_NAME, secondaryName = "Gyokuro Asahi, Frühjahrsernte aus Yame"))
    }

    @Test
    fun pickerEmpty() = capture("TeaPicker_empty") { PickerHost(TeaPickerUiState(loading = false)) }

    /** A long press on a tea: its menu with *Aus Sammlung entfernen* (SCR-03, TEA-04). */
    @Test
    fun pickerMenu() = capture("TeaPicker_menu", act = { longPress("Bio Sencha Tanabata") }) {
        PickerHost(TeaPickerUiState(favorites = listOf(hisui), all = listOf(hisui, tanabata), loading = false))
    }

    /** The tea of the active brew session: greyed out with „Erst die Sitzung beenden" (E15). */
    @Test
    fun pickerMenuBlocked() = capture("TeaPicker_menuBlocked", act = { longPress("Bio Sencha Tanabata") }) {
        PickerHost(
            TeaPickerUiState(
                favorites = listOf(hisui),
                all = listOf(hisui, tanabata.copy(removable = false)),
                loading = false,
            ),
        )
    }

    /** Removed: the snackbar with *Rückgängig* (D-093 Q21). */
    @Test
    fun pickerRemoved() = capture("TeaPicker_removed") {
        PickerHost(TeaPickerUiState(all = listOf(tanabata), loading = false, removed = hisui))
    }

    private fun longPress(name: String) {
        compose.onAllNodesWithText(name).onLast().performTouchInput { longClick() }
    }

    @androidx.compose.runtime.Composable
    private fun PickerHost(state: TeaPickerUiState) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            // A stand-in for Start under the scrim.
            Text(
                text = "UmamiT",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(UmamiSpacing.SideMargin),
            )
            TeaPickerSheet(state = state, onQueryChange = {}, onPickTea = {}, onQuickTimer = {}, onDismissRequest = {})
        }
    }
}
