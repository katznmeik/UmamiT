package com.umamit.feature.brew.steps

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.StepKind
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.PreAlertChoice
import com.umamit.core.timer.engine.StepStatus
import com.umamit.core.timer.engine.TimerEvent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The editing rules of the Schritte-Sheet (SCR-31, PRF-04, GEN-12, TMR-10, TMR-11, BRW-02). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class StepsSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val doneRequests = mutableListOf<TimerEvent.EditSteps>()
    private val saveRequests = mutableListOf<SaveToRecipeRequest>()

    private fun show(
        steps: List<StepsSheetStep>,
        preAlert: PreAlertChoice = PreAlertChoice.Inherit,
        hasRecipe: Boolean = true,
        baseTempC: Int? = 60,
    ) {
        val state = StepsSheetState(StepsSheetInput(steps, preAlert, hasRecipe, baseTempC))
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                StepsSheetContent(state = state, onDone = {
                    doneRequests += it
                }, onSaveToRecipe = { saveRequests += it })
            }
        }
    }

    private fun click(description: String, times: Int = 1) = repeat(times) {
        compose.onNodeWithContentDescription(description).performScrollTo().performClick()
    }

    private fun clickText(text: String) = compose.onNodeWithText(text).performScrollTo().performClick()

    /** Opens the pre-alert menu the way TalkBack does: through the click action, not a touch. */
    private fun openPreAlert(spoken: String) = compose.onNodeWithContentDescription("Vorwarnung: $spoken. Ändern")
        .performScrollTo()
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        .performSemanticsAction(SemanticsActions.OnClick)

    /** The action buttons stay below the scrolling plan. */
    private fun clickButton(text: String) = compose.onNodeWithText(text).performClick()

    private fun done(): TimerEvent.EditSteps {
        clickButton("Fertig")
        return doneRequests.single()
    }

    @Test
    fun `the duration moves in 5 s steps below 10 min and in 1 min steps from 10 min on`() {
        show(listOf(pending(seconds = 590)))

        click("Ziehzeit verlängern, Schritt 1", times = 2)
        compose.onNodeWithText("10:00").assertIsDisplayed()
        click("Ziehzeit verlängern, Schritt 1")
        compose.onNodeWithText("11:00").assertIsDisplayed()
        click("Ziehzeit verkürzen, Schritt 1")
        compose.onNodeWithText("10:00").assertIsDisplayed()
        click("Ziehzeit verkürzen, Schritt 1")
        compose.onNodeWithText("9:55").assertIsDisplayed()
    }

    @Test
    fun `the duration stops at 5 s`() {
        show(listOf(pending(seconds = 10)))

        click("Ziehzeit verkürzen, Schritt 1")

        compose.onNodeWithText("0:05").assertIsDisplayed()
        compose.onNodeWithContentDescription("Ziehzeit verkürzen, Schritt 1").assertIsNotEnabled()
        assertThat(done().steps.single().plannedSeconds).isEqualTo(5)
    }

    @Test
    fun `the duration stops at 24 h`() {
        show(listOf(pending(seconds = 86_340)))

        click("Ziehzeit verlängern, Schritt 1")

        compose.onNodeWithText("24:00:00").assertIsDisplayed()
        compose.onNodeWithContentDescription("Ziehzeit verlängern, Schritt 1").assertIsNotEnabled()
        assertThat(done().steps.single().plannedSeconds).isEqualTo(86_400)
    }

    @Test
    fun `the temperature moves in 5 degree steps`() {
        show(listOf(pending(tempC = 60)))

        click("Temperatur erhöhen, Schritt 1")
        compose.onNodeWithText("65 °C").assertIsDisplayed()
        click("Temperatur senken, Schritt 1", times = 2)
        compose.onNodeWithText("55 °C").assertIsDisplayed()

        assertThat(done().steps.single().tempC).isEqualTo(55)
    }

    @Test
    fun `the temperature stays within 5 and 100 degrees`() {
        show(listOf(pending(tempC = 95), pending(tempC = 10)))

        click("Temperatur erhöhen, Schritt 1")
        click("Temperatur senken, Schritt 2")

        compose.onNodeWithContentDescription("Temperatur erhöhen, Schritt 1").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Temperatur senken, Schritt 2").assertIsNotEnabled()
        assertThat(done().steps.map { it.tempC }).containsExactly(100, 5).inOrder()
    }

    @Test
    fun `a step without its own temperature shows the recipe's and starts from it`() {
        show(listOf(pending(tempC = null)), baseTempC = 70)

        compose.onNodeWithText("70 °C").assertIsDisplayed()
        click("Temperatur erhöhen, Schritt 1")

        assertThat(done().steps.single().tempC).isEqualTo(75)
    }

    @Test
    fun `an untouched step keeps no temperature of its own`() {
        show(listOf(pending(tempC = null)), baseTempC = 70)

        assertThat(done().steps.single().tempC).isNull()
    }

    @Test
    fun `delete is disabled for the last non-rinse step`() {
        show(listOf(pending(StepKind.RINSE, seconds = 10), pending(seconds = 60)))

        compose.onNodeWithContentDescription("Löschen, Schritt 2").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Löschen, Schritt 1").assertIsEnabled()
    }

    @Test
    fun `a done infusion counts as the non-rinse step that must remain`() {
        show(listOf(doneStep(), pending(seconds = 45)))

        click("Löschen, Schritt 2")

        assertThat(done().steps).containsExactly(PlannedStep(StepKind.INFUSION, 60, 60))
    }

    @Test
    fun `deleting a step removes it and renumbers the rest`() {
        show(listOf(pending(seconds = 30), pending(seconds = 45), pending(seconds = 60)))

        click("Löschen, Schritt 2")

        compose.onAllNodesWithText("0:45").assertCountEquals(0)
        compose.onNodeWithContentDescription("Löschen, Schritt 3", useUnmergedTree = true).assertDoesNotExist()
        assertThat(done().steps.map { it.plannedSeconds }).containsExactly(30, 60).inOrder()
    }

    @Test
    fun `add uses the Weiterer Aufguss defaults of the last non-rinse step`() {
        show(listOf(doneStep(seconds = 60, tempC = 70), pending(StepKind.RINSE, seconds = 10, tempC = 80)))

        clickText("Aufguss hinzufügen")

        compose.onNodeWithText("1:30").assertIsDisplayed()
        assertThat(done().steps.last()).isEqualTo(PlannedStep(StepKind.INFUSION, 90, 70))
    }

    @Test
    fun `add rounds to whole minutes from 10 min on`() {
        show(listOf(pending(seconds = 420)))

        clickText("Aufguss hinzufügen")

        assertThat(done().steps.last().plannedSeconds).isEqualTo(660)
    }

    @Test
    fun `done, running and skipped steps are read-only`() {
        show(
            listOf(
                doneStep(seconds = 90, tempC = 55),
                StepsSheetStep(StepKind.INFUSION, 30, 60, StepStatus.SKIPPED),
                StepsSheetStep(StepKind.INFUSION, 20, 60, StepStatus.STARTED),
                pending(),
            ),
        )

        listOf(1, 2, 3).forEach { number ->
            listOf(
                "Ziehzeit verlängern, Schritt $number",
                "Ziehzeit verkürzen, Schritt $number",
                "Temperatur erhöhen, Schritt $number",
                "Temperatur senken, Schritt $number",
                "Löschen, Schritt $number",
            ).forEach { compose.onAllNodesWithContentDescription(it).assertCountEquals(0) }
        }
        compose.onNodeWithContentDescription("Schritt 1, Aufguss, erledigt, 1 Minute 30 Sekunden, 55 Grad")
            .assertExists()
        compose.onNodeWithContentDescription("Schritt 2, Aufguss, übersprungen, 30 Sekunden, 60 Grad").assertExists()
        compose.onNodeWithContentDescription("Schritt 3, Aufguss, läuft, 20 Sekunden, 60 Grad").assertExists()
        compose.onNodeWithContentDescription("Ziehzeit verlängern, Schritt 4").assertIsEnabled()
    }

    @Test
    fun `the pre-alert selector offers Wie Einstellung, Aus and 5 to 30 s`() {
        show(listOf(pending()))

        openPreAlert("Wie Einstellung")

        val labels = listOf("Aus", "5 s", "10 s", "15 s", "20 s", "25 s", "30 s")
        labels.forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onNodeWithText("Wie Einstellung").assertIsDisplayed()
        assertThat(PreAlertOptions).containsExactly(
            PreAlertChoice.Inherit,
            PreAlertChoice.Off,
            PreAlertChoice.Seconds(5),
            PreAlertChoice.Seconds(10),
            PreAlertChoice.Seconds(15),
            PreAlertChoice.Seconds(20),
            PreAlertChoice.Seconds(25),
            PreAlertChoice.Seconds(30),
        ).inOrder()
    }

    @Test
    fun `a new step scrolls into view`() {
        show((1..12).map { pending(seconds = 30 + it * 5) })

        clickText("Aufguss hinzufügen")

        compose.onNodeWithContentDescription("Ziehzeit verlängern, Schritt 13").assertIsDisplayed()
        compose.onNodeWithText("Vorwarnung").assertIsDisplayed()
    }

    @Test
    fun `the selected pre-alert goes into the request`() {
        show(listOf(pending()), preAlert = PreAlertChoice.Seconds(10))

        openPreAlert("10 Sekunden vorher")
        compose.onNodeWithText("Aus").performClick()

        compose.onNodeWithContentDescription("Vorwarnung: Aus. Ändern").assertExists()
        assertThat(done().steps.single().preAlertSeconds).isEqualTo(0)
    }

    @Test
    fun `the selector's pre-alert goes into the running and pending steps, an ended step keeps its own`() {
        // The single selector until #92 shows the first step that has not ended (D-093).
        show(
            listOf(
                doneStep(seconds = 90, preAlert = 20),
                StepsSheetStep(StepKind.INFUSION, 30, 60, StepStatus.SKIPPED, preAlertSeconds = 25),
                StepsSheetStep(StepKind.INFUSION, 60, 60, StepStatus.STARTED, preAlertSeconds = 15),
                pending(seconds = 45),
            ),
            preAlert = PreAlertChoice.Seconds(15),
        )

        openPreAlert("15 Sekunden vorher")
        compose.onNodeWithText("Aus").performClick()

        assertThat(done().steps.map { it.preAlertSeconds }).containsExactly(20, 25, 0, 0).inOrder()
    }

    @Test
    fun `Fertig emits exactly the edited plan and pre-alert`() {
        show(
            listOf(doneStep(seconds = 60, tempC = 60), pending(seconds = 30, tempC = 65), pending(seconds = 45)),
            preAlert = PreAlertChoice.Inherit,
        )

        click("Ziehzeit verlängern, Schritt 2")
        click("Temperatur senken, Schritt 2")
        click("Löschen, Schritt 3")
        clickText("Aufguss hinzufügen")
        openPreAlert("Wie Einstellung")
        compose.onNodeWithText("15 s").performClick()

        assertThat(done()).isEqualTo(
            TimerEvent.EditSteps(
                steps = listOf(
                    // The done step keeps its own pre-alert; the others take the selector's (D-093).
                    PlannedStep(StepKind.INFUSION, 60, 60),
                    PlannedStep(StepKind.INFUSION, 35, 60, preAlertSeconds = 15),
                    PlannedStep(StepKind.INFUSION, 55, 60, preAlertSeconds = 15),
                ),
            ),
        )
        assertThat(saveRequests).isEmpty()
    }

    @Test
    fun `without a recipe there is no Im Rezept speichern`() {
        show(listOf(pending()), hasRecipe = false)

        compose.onAllNodesWithText("Im Rezept speichern").assertCountEquals(0)
        compose.onNodeWithText("Fertig").assertExists()
    }

    @Test
    fun `Im Rezept speichern emits the plan and confirms with a snackbar`() {
        show(listOf(doneStep(), pending(seconds = 30)), preAlert = PreAlertChoice.Off)

        click("Ziehzeit verlängern, Schritt 2")
        clickButton("Im Rezept speichern")

        compose.onNodeWithText("Im Rezept gespeichert").assertIsDisplayed()
        assertThat(saveRequests.single()).isEqualTo(
            SaveToRecipeRequest(
                steps = listOf(
                    PlannedStep(StepKind.INFUSION, 60, 60),
                    PlannedStep(StepKind.INFUSION, 35, 60, preAlertSeconds = 0),
                ),
            ),
        )
        assertThat(saveRequests.single().recipeSteps.map { it.preAlertSeconds }).containsExactly(null, 0).inOrder()
        assertThat(doneRequests).isEmpty()
    }

    @Test
    fun `a step without any temperature has no temperature stepper`() {
        show(listOf(pending(tempC = null)), hasRecipe = false, baseTempC = null)

        compose.onAllNodesWithContentDescription("Temperatur erhöhen, Schritt 1").assertCountEquals(0)
        assertThat(done().steps.single().tempC).isNull()
    }

    @Test
    fun `the stepper values have spoken TalkBack descriptions`() {
        show(listOf(pending(seconds = 90, tempC = 60)))

        compose.onNodeWithContentDescription("Ziehzeit 1 Minute 30 Sekunden").assertExists()
        compose.onNodeWithContentDescription("Temperatur 60 Grad").assertExists()
    }

    private fun pending(kind: StepKind = StepKind.INFUSION, seconds: Int = 30, tempC: Int? = 60) =
        StepsSheetStep(kind, seconds, tempC, StepStatus.PENDING)

    private fun doneStep(seconds: Int = 60, tempC: Int? = 60, preAlert: Int? = null) =
        StepsSheetStep(StepKind.INFUSION, seconds, tempC, StepStatus.DONE, preAlertSeconds = preAlert)
}
