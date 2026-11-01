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
import androidx.compose.ui.test.hasContentDescription
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
        hasRecipe: Boolean = true,
        baseTempC: Int? = 60,
        preAlertBase: Int = 10,
        longStepsOnly: Boolean = false,
    ) {
        val state = StepsSheetState(StepsSheetInput(steps, hasRecipe, baseTempC, preAlertBase, longStepsOnly))
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

    /** The *Vorwarnung* selector of step [number] as TalkBack reads it, with its [spoken] value. */
    private fun preAlertSelector(number: Int, spoken: String) =
        compose.onNodeWithContentDescription("Vorwarnung, Schritt $number: $spoken. Ändern")

    /** Every *Vorwarnung* selector, or those of step [number]. */
    private fun preAlertSelectors(number: Int? = null) = compose.onAllNodes(
        hasContentDescription("Vorwarnung, Schritt ${number?.let { "$it:" }.orEmpty()}", substring = true),
    )

    /** Opens step [number]'s pre-alert menu the way TalkBack does: through the click action, not a touch. */
    private fun openPreAlert(number: Int, spoken: String) = preAlertSelector(number, spoken)
        .performScrollTo()
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        .performSemanticsAction(SemanticsActions.OnClick)

    /** Picks [option] in step [number]'s open pre-alert menu. */
    private fun choosePreAlert(number: Int, spoken: String, option: String) {
        openPreAlert(number, spoken)
        compose.onNodeWithText(option).performClick()
    }

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
    fun `a rinse reads Spuelen, not Spuelaufguss (D-100 G3)`() {
        show(listOf(pending(StepKind.RINSE, seconds = 10), pending(seconds = 60)))

        compose.onNodeWithText("1 Spülen").assertIsDisplayed()
        compose.onAllNodesWithText("Spülaufguss", substring = true).assertCountEquals(0)
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
    fun `every pending step and the running step have a Vorwarnung selector, done and skipped steps none`() {
        // SCR-31, D-093: per step; the session-wide selector of v0.2.0 is gone.
        show(
            listOf(
                doneStep(seconds = 90),
                StepsSheetStep(StepKind.INFUSION, 30, 60, StepStatus.SKIPPED),
                StepsSheetStep(StepKind.INFUSION, 60, 60, StepStatus.STARTED),
                pending(seconds = 20),
                pending(seconds = 10),
            ),
        )

        preAlertSelectors(1).assertCountEquals(0)
        preAlertSelectors(2).assertCountEquals(0)
        preAlertSelector(3, "Automatisch, 10 Sekunden vorher").assertExists()
        preAlertSelector(4, "Automatisch, 5 Sekunden vorher").assertExists()
        preAlertSelector(5, "Automatisch, keine").assertExists()
        preAlertSelectors().assertCountEquals(3)
    }

    @Test
    fun `Automatisch shows the lead it will use, Auto N s or Auto keine`() {
        // Q17: N = min(B, 5 · ⌊d / 15⌋); none under 15 s and for long steps (TMR-23, TMR-18).
        show(listOf(pending(seconds = 60), pending(seconds = 20), pending(seconds = 10), pending(seconds = 3_600)))

        compose.onNodeWithText("Auto · 10 s", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Auto · 5 s", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Auto · keine", useUnmergedTree = true).assertCountEquals(2)
    }

    @Test
    fun `Automatisch takes the session's base B`() {
        show(listOf(pending(seconds = 45), pending(seconds = 120)), preAlertBase = 20)

        compose.onNodeWithText("Auto · 15 s", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Auto · 20 s", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `in a cold or ice brew Automatisch gives none`() {
        show(listOf(pending(seconds = 60)), longStepsOnly = true)

        compose.onNodeWithText("Auto · keine", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `the Automatisch label follows the step's duration as it is edited`() {
        show(listOf(pending(seconds = 20)))
        compose.onNodeWithText("Auto · 5 s", useUnmergedTree = true).assertIsDisplayed()

        click("Ziehzeit verlängern, Schritt 1", times = 2)

        compose.onNodeWithText("Auto · 10 s", useUnmergedTree = true).assertIsDisplayed()
        preAlertSelector(1, "Automatisch, 10 Sekunden vorher").assertExists()
    }

    @Test
    fun `a hot step of 10 to 59 min has its Vorwarnung, from 60 min none (TMR-18, D-098)`() {
        show(listOf(pending(seconds = 15 * 60), pending(seconds = 60 * 60)))

        preAlertSelector(1, "Automatisch, 10 Sekunden vorher").assertExists()
        preAlertSelector(2, "Automatisch, keine").assertExists()

        // Below 60 min the step is short again; the rounding stays 1 min from 10 min on (PRF-04).
        click("Ziehzeit verkürzen, Schritt 2")
        compose.onNodeWithText("59:00").assertIsDisplayed()
        preAlertSelector(2, "Automatisch, 10 Sekunden vorher").assertExists()
        click("Ziehzeit verlängern, Schritt 2")
        preAlertSelector(2, "Automatisch, keine").assertExists()
    }

    @Test
    fun `the selector offers Automatisch with its lead, Aus and 5 to 30 s`() {
        show(listOf(pending(seconds = 60)))

        openPreAlert(1, "Automatisch, 10 Sekunden vorher")

        val labels = listOf("Automatisch · 10 s", "Aus", "5 s", "10 s", "15 s", "20 s", "25 s", "30 s")
        labels.forEach { compose.onNodeWithText(it).assertIsDisplayed() }
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
    fun `an explicit value the step is too short for shows zu kurz, on a long step keine`() {
        // E7: none when d < N + 5 s; a long step never has one (09 §4 v0.2.1: 0:10 with 10 s → „zu kurz").
        show(listOf(pending(seconds = 10), pending(seconds = 3_600)))

        choosePreAlert(1, "Automatisch, keine", "10 s · zu kurz")
        compose.onNodeWithText("10 s · zu kurz", useUnmergedTree = true).assertIsDisplayed()
        preAlertSelector(1, "10 Sekunden vorher, zu kurz").assertExists()
        choosePreAlert(1, "10 Sekunden vorher, zu kurz", "5 s")
        preAlertSelector(1, "5 Sekunden vorher").assertExists()
        choosePreAlert(2, "Automatisch, keine", "10 s · keine")
        preAlertSelector(2, "10 Sekunden vorher, keine").assertExists()

        assertThat(done().steps.map { it.preAlertSeconds }).containsExactly(5, 10).inOrder()
    }

    @Test
    fun `a new step scrolls into view`() {
        show((1..12).map { pending(seconds = 30 + it * 5) })

        clickText("Aufguss hinzufügen")

        compose.onNodeWithContentDescription("Ziehzeit verlängern, Schritt 13").assertIsDisplayed()
        // The added step starts as Automatisch (04 §3.2): 2:15 gives min(10, 45) = 10 s.
        preAlertSelector(13, "Automatisch, 10 Sekunden vorher").assertIsDisplayed()
    }

    @Test
    fun `each step's choice goes into its own step of the request`() {
        show(listOf(pending(seconds = 60), pending(seconds = 30), pending(seconds = 20)))

        choosePreAlert(1, "Automatisch, 10 Sekunden vorher", "15 s")
        choosePreAlert(2, "Automatisch, 10 Sekunden vorher", "Aus")

        preAlertSelector(1, "15 Sekunden vorher").assertExists()
        preAlertSelector(2, "Aus").assertExists()
        preAlertSelector(3, "Automatisch, 5 Sekunden vorher").assertExists()
        assertThat(done().steps.map { it.preAlertSeconds }).containsExactly(15, 0, null).inOrder()
    }

    @Test
    fun `a step set back to Automatisch keeps no value of its own`() {
        show(listOf(StepsSheetStep(StepKind.INFUSION, 60, 60, StepStatus.PENDING, preAlertSeconds = 20)))

        choosePreAlert(1, "20 Sekunden vorher", "Automatisch · 10 s")

        assertThat(done().steps.single().preAlertSeconds).isNull()
    }

    @Test
    fun `the running step's Vorwarnung is its only editable field, ended steps keep their own`() {
        show(
            listOf(
                doneStep(seconds = 90, preAlert = 20),
                StepsSheetStep(StepKind.INFUSION, 30, 60, StepStatus.SKIPPED, preAlertSeconds = 25),
                StepsSheetStep(StepKind.INFUSION, 60, 60, StepStatus.STARTED, preAlertSeconds = 15),
                pending(seconds = 45),
            ),
        )

        listOf("Ziehzeit verlängern", "Ziehzeit verkürzen", "Temperatur erhöhen", "Temperatur senken", "Löschen")
            .forEach { compose.onAllNodesWithContentDescription("$it, Schritt 3").assertCountEquals(0) }
        choosePreAlert(3, "15 Sekunden vorher", "Aus")

        assertThat(done()).isEqualTo(
            TimerEvent.EditSteps(
                listOf(
                    PlannedStep(StepKind.INFUSION, 90, 60, preAlertSeconds = 20),
                    PlannedStep(StepKind.INFUSION, 30, 60, preAlertSeconds = 25),
                    PlannedStep(StepKind.INFUSION, 60, 60, preAlertSeconds = 0),
                    PlannedStep(StepKind.INFUSION, 45, 60),
                ),
            ),
        )
    }

    @Test
    fun `Fertig emits exactly the edited plan with each step's pre-alert`() {
        show(listOf(doneStep(seconds = 60, tempC = 60), pending(seconds = 30, tempC = 65), pending(seconds = 45)))

        click("Ziehzeit verlängern, Schritt 2")
        click("Temperatur senken, Schritt 2")
        click("Löschen, Schritt 3")
        clickText("Aufguss hinzufügen")
        choosePreAlert(2, "Automatisch, 10 Sekunden vorher", "15 s")

        assertThat(done()).isEqualTo(
            TimerEvent.EditSteps(
                steps = listOf(
                    PlannedStep(StepKind.INFUSION, 60, 60),
                    PlannedStep(StepKind.INFUSION, 35, 60, preAlertSeconds = 15),
                    // The added step starts as Automatisch (04 §3.2).
                    PlannedStep(StepKind.INFUSION, 55, 60),
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
    fun `Im Rezept speichern emits the plan with each step's pre-alert and confirms with a snackbar`() {
        show(listOf(doneStep(), pending(seconds = 30), pending(seconds = 20), pending(seconds = 45)))

        click("Ziehzeit verlängern, Schritt 2")
        choosePreAlert(2, "Automatisch, 10 Sekunden vorher", "Aus")
        choosePreAlert(4, "Automatisch, 10 Sekunden vorher", "20 s")
        clickButton("Im Rezept speichern")

        compose.onNodeWithText("Im Rezept gespeichert").assertIsDisplayed()
        assertThat(saveRequests.single()).isEqualTo(
            SaveToRecipeRequest(
                steps = listOf(
                    PlannedStep(StepKind.INFUSION, 60, 60),
                    PlannedStep(StepKind.INFUSION, 35, 60, preAlertSeconds = 0),
                    PlannedStep(StepKind.INFUSION, 20, 60),
                    PlannedStep(StepKind.INFUSION, 45, 60, preAlertSeconds = 20),
                ),
            ),
        )
        assertThat(saveRequests.single().recipeSteps.map { it.preAlertSeconds })
            .containsExactly(null, 0, null, 20).inOrder()
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

        compose.onNodeWithContentDescription("Ziehzeit, Schritt 1: 1 Minute 30 Sekunden. Ändern").assertExists()
        compose.onNodeWithContentDescription("Temperatur 60 Grad").assertExists()
    }

    private fun pending(kind: StepKind = StepKind.INFUSION, seconds: Int = 30, tempC: Int? = 60) =
        StepsSheetStep(kind, seconds, tempC, StepStatus.PENDING)

    private fun doneStep(seconds: Int = 60, tempC: Int? = 60, preAlert: Int? = null) =
        StepsSheetStep(StepKind.INFUSION, seconds, tempC, StepStatus.DONE, preAlertSeconds = preAlert)
}
