package com.umamit.feature.journal.session

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.SessionStart
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.StepKind
import com.umamit.core.model.TasteAxis
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.testing.FakeCatalogRepository
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeRecipeOffers
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeTeaArchive
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.StepRecord
import com.umamit.core.ui.INFUSION_NOTE_TAG
import com.umamit.core.ui.INFUSION_TASTE_RESET_TAG
import com.umamit.core.ui.TASTE_RADAR_TAG
import com.umamit.core.ui.tasteSliderTag
import com.umamit.feature.journal.JournalSessions
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/**
 * GESCHMACK per infusion on the session page (JRN-03, JRN-05, SCR-16 v0.6.1, D-114) with the real view model on the
 * fakes of core:testing: a rinse (no chip), the 1st infusion, a skipped 2nd (no chip) and the 3rd. An invented tea
 * (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class SessionTasteSectionTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val collection = FakeCollection()
    private val sessions = FakeSessions(collection)
    private val onScreen = mutableStateOf(true)

    @Before
    fun seed() = runBlocking {
        collection.teas.saveTea(Tea(TEA, "Sencha Morgentau", category = TeaCategory.GREEN, form = TeaForm.LOOSE_LEAF))
        val snapshot = SessionSnapshot(
            teaId = TEA,
            teaName = "Sencha Morgentau",
            teaSecondaryName = null,
            teaType = "Sencha",
            styleKey = "sencha",
            category = TeaCategory.GREEN,
            profileId = null,
            profileName = null,
            method = BrewMethod.KYUSU,
            vesselId = null,
            vesselName = null,
            leafGrams = 4.5,
            waterMl = 180,
            baseTempC = 65,
        )
        val steps = listOf(
            PlannedStep(StepKind.RINSE, 10, 80),
            PlannedStep(StepKind.INFUSION, 60, null),
            PlannedStep(StepKind.INFUSION, 20, 70),
            PlannedStep(StepKind.INFUSION, 30, 75),
        )
        sessions.sessions.startSession(SessionStart(PAST, snapshot, steps, 10, STARTED, "Europe/Berlin"))
        sessions.sessions.recordStep(PAST, 0, record(10, 10))
        sessions.sessions.recordStep(PAST, 1, record(60, 60))
        sessions.sessions.recordStep(PAST, 2, record(20, 0, skipped = true))
        sessions.sessions.recordStep(PAST, 3, record(30, 35))
        sessions.sessions.completeSession(PAST, autoFinished = false)
    }

    private fun record(planned: Int, actual: Int, skipped: Boolean = false) =
        StepRecord(planned, actual, 0, 0, STARTED, STARTED + 1_000, skipped = skipped, overtimeAutoAck = false)

    private fun show() {
        val viewModel = SessionPageViewModel(
            SavedStateHandle(mapOf("sessionId" to PAST)),
            sessions.journal,
            FakeRecipeOffers(),
            collection.teas,
            FakeTeaArchive(sessions),
            sessions.tasting,
            FakeCatalogRepository(),
        )
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                if (onScreen.value) {
                    SessionPageRoute(
                        host = SessionPageHost(onBack = {}, onOpenTea = {}, onBrewAgain = {}, onDeleted = {}),
                        viewModel = viewModel,
                        heroViewModel = heroViewModel(sessions, PAST),
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun session() = runBlocking { sessions.sessions.getSession(PAST)!! }

    private fun tasting(position: Int) = session().infusions.single { it.position == position }

    private fun node(tag: String) = compose.onNodeWithTag(tag).performScrollTo()

    private fun slider(axis: TasteAxis) = node(tasteSliderTag(axis))

    private fun release(axis: TasteAxis, value: Float) {
        slider(axis).performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
        compose.waitForIdle()
    }

    private fun tap(tag: String) {
        node(tag).performClick()
        compose.waitForIdle()
    }

    /** A chip: the page is scrolled to GESCHMACK first (the chip row's own scroll is sideways only). */
    private fun tapChip(tag: String) {
        compose.onNodeWithTag(SESSION_TASTE_ADJUST_TAG).performScrollTo()
        tap(tag)
    }

    private fun seedTaste(position: Int, vararg values: Pair<TasteAxis, Int>) = runBlocking {
        values.forEach { (axis, value) -> sessions.tasting.setInfusionTaste(PAST, position, axis, value) }
    }

    @Test
    fun `without values one row, a tap shows the chips with the 1st infusion selected and unfolded`() {
        show()
        compose.onNodeWithText("Geschmack hinzufügen").performScrollTo().assertExists()
        compose.onNodeWithTag(SESSION_TASTE_ALL_TAG).assertDoesNotExist()

        tap(SESSION_TASTE_ADD_TAG)

        node(sessionTasteChipTag(FIRST)).assertIsSelected()
        slider(TasteAxis.UMAMI).assert(hasStateDescription("nicht gesetzt"))
        node(SESSION_TASTE_ADJUST_TAG).assert(hasStateDescription("ausgeklappt"))
        assertThat(session().infusions.all { it.taste.setCount == 0 && it.note == null }).isTrue()
    }

    @Test
    fun `a rinse and a skipped step have no chip, the others are numbered as the step table`() {
        seedTaste(FIRST, TasteAxis.UMAMI to 4)
        show()

        node(SESSION_TASTE_ALL_TAG).assertExists()
        node(sessionTasteChipTag(FIRST)).assert(hasContentDescription("1. Aufguss"))
        compose.onNodeWithText("1. Aufguss", useUnmergedTree = true).assertExists()
        node(sessionTasteChipTag(THIRD)).assert(hasContentDescription("3. Aufguss"))
        compose.onNodeWithText("3.", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(sessionTasteChipTag(0)).assertDoesNotExist()
        compose.onNodeWithTag(sessionTasteChipTag(2)).assertDoesNotExist()
    }

    @Test
    fun `with values it opens folded on the 1st infusion, and Anpassen unfolds and folds`() {
        seedTaste(FIRST, TasteAxis.UMAMI to 4, TasteAxis.SWEETNESS to 3)
        show()

        node(sessionTasteChipTag(FIRST))
            .assertIsSelected()
            .assert(hasStateDescription("ausgewählt, Geschmack eingetragen"))
        node(SESSION_TASTE_SUMMARY_TAG).assertExists()
        compose.onNodeWithText("Umami 4 · Süße 3").assertExists()
        compose.onNodeWithTag(tasteSliderTag(TasteAxis.UMAMI)).assertDoesNotExist()
        node(SESSION_TASTE_ADJUST_TAG)
            .assert(hasContentDescription("Geschmack anpassen"))
            .assert(hasStateDescription("eingeklappt"))

        tap(SESSION_TASTE_ADJUST_TAG)
        slider(TasteAxis.UMAMI).assert(hasStateDescription("4 von 5"))
        node(INFUSION_NOTE_TAG).assertExists()
        node(SESSION_TASTE_ADJUST_TAG).assert(hasStateDescription("ausgeklappt"))

        tap(SESSION_TASTE_ADJUST_TAG)
        compose.onNodeWithTag(tasteSliderTag(TasteAxis.UMAMI)).assertDoesNotExist()
    }

    @Test
    fun `folded with three axes shows the radar and the note in one line`() {
        seedTaste(FIRST, TasteAxis.UMAMI to 5, TasteAxis.BODY to 3, TasteAxis.AROMA to 1)
        runBlocking { sessions.tasting.setInfusionNote(PAST, FIRST, "Weich, nach Nori") }
        show()

        node(TASTE_RADAR_TAG).assertExists()
        compose.onNodeWithTag(SESSION_TASTE_SUMMARY_TAG).assertDoesNotExist()
        node(SESSION_TASTE_NOTE_LINE_TAG).assertExists()
        compose.onNodeWithText("Weich, nach Nori").assertExists()
    }

    @Test
    fun `a chip switch shows that infusion's values and keeps the fold state`() {
        seedTaste(FIRST, TasteAxis.UMAMI to 4)
        seedTaste(THIRD, TasteAxis.UMAMI to 2)
        show()
        tap(SESSION_TASTE_ADJUST_TAG)
        slider(TasteAxis.UMAMI).assert(hasStateDescription("4 von 5"))

        tapChip(sessionTasteChipTag(THIRD))

        node(sessionTasteChipTag(THIRD)).assertIsSelected()
        slider(TasteAxis.UMAMI).assert(hasStateDescription("2 von 5"))
    }

    @Test
    fun `Alle shows the averaged summary and has no sliders and no Anpassen`() {
        seedTaste(FIRST, TasteAxis.UMAMI to 4)
        seedTaste(THIRD, TasteAxis.UMAMI to 3, TasteAxis.SWEETNESS to 2)
        show()
        tap(SESSION_TASTE_ADJUST_TAG)

        tapChip(SESSION_TASTE_ALL_TAG)

        compose.onNodeWithText("Umami 3,5 · Süße 2").assertExists()
        compose.onNodeWithTag(tasteSliderTag(TasteAxis.UMAMI)).assertDoesNotExist()
        compose.onNodeWithTag(SESSION_TASTE_ADJUST_TAG).assertDoesNotExist()
    }

    @Test
    fun `a slider release saves on the selected infusion only`() {
        seedTaste(FIRST, TasteAxis.UMAMI to 4)
        show()
        tapChip(sessionTasteChipTag(THIRD))
        node(sessionTasteChipTag(THIRD)).assertIsSelected()
        tap(SESSION_TASTE_ADJUST_TAG)
        slider(TasteAxis.UMAMI).assert(hasStateDescription("nicht gesetzt"))

        release(TasteAxis.BODY, 3f)
        slider(TasteAxis.BODY).assert(hasStateDescription("3 von 5"))

        assertThat(tasting(THIRD).taste.body).isEqualTo(3)
        assertThat(tasting(FIRST).taste.body).isNull()
        assertThat(tasting(FIRST).taste.umami).isEqualTo(4)
    }

    @Test
    fun `the radar appears with the third axis and not before`() {
        show()
        tap(SESSION_TASTE_ADD_TAG)

        release(TasteAxis.UMAMI, 4f)
        release(TasteAxis.AROMA, 2f)
        compose.onNodeWithTag(TASTE_RADAR_TAG).assertDoesNotExist()

        release(TasteAxis.BODY, 1f)
        node(TASTE_RADAR_TAG)
            .assert(hasContentDescription("Geschmacksprofil: Umami 4 von 5, Körper 1 von 5, Aroma 2 von 5"))
    }

    @Test
    fun `the note saves 1 s after the last key`() {
        show()
        tap(SESSION_TASTE_ADD_TAG)

        node(INFUSION_NOTE_TAG).performTextInput("  Süß und weich  ")
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()

        assertThat(tasting(FIRST).note).isEqualTo("Süß und weich")
    }

    @Test
    fun `the note saves when the page is left, on its own infusion`() {
        seedTaste(THIRD, TasteAxis.UMAMI to 2)
        show()
        tapChip(sessionTasteChipTag(THIRD))
        tap(SESSION_TASTE_ADJUST_TAG)

        node(INFUSION_NOTE_TAG).performTextInput("Grasig")
        compose.runOnUiThread { onScreen.value = false }
        compose.waitForIdle()

        assertThat(tasting(THIRD).note).isEqualTo("Grasig")
        assertThat(tasting(FIRST).note).isNull()
    }

    @Test
    fun `the note counter shows from 180 characters and the field stops at 200`() {
        show()
        tap(SESSION_TASTE_ADD_TAG)

        node(INFUSION_NOTE_TAG).performTextInput("a".repeat(179))
        compose.onNodeWithText("179 / 200").assertDoesNotExist()
        node(INFUSION_NOTE_TAG).performTextInput("b")
        compose.onNodeWithText("180 / 200").assertExists()
        node(INFUSION_NOTE_TAG).performTextInput("c".repeat(50))
        compose.onNodeWithText("200 / 200").assertExists()
    }

    @Test
    fun `Zuruecksetzen clears only the selected infusion's six values and keeps its note`() {
        seedTaste(FIRST, TasteAxis.UMAMI to 5, TasteAxis.BODY to 3)
        seedTaste(THIRD, TasteAxis.UMAMI to 2)
        runBlocking { sessions.tasting.setInfusionNote(PAST, FIRST, "Weich") }
        show()
        tap(SESSION_TASTE_ADJUST_TAG)

        tap(INFUSION_TASTE_RESET_TAG)

        assertThat(tasting(FIRST).taste.setCount).isEqualTo(0)
        assertThat(tasting(FIRST).note).isEqualTo("Weich")
        assertThat(tasting(THIRD).taste.umami).isEqualTo(2)
        slider(TasteAxis.UMAMI).assert(hasStateDescription("nicht gesetzt"))
    }

    private companion object {
        const val TEA = "morgentau"
        const val PAST = "past"
        const val FIRST = 1
        const val THIRD = 3
        val STARTED = JournalSessions.ms(LocalDateTime.of(2026, 10, 22, 9, 12))
    }
}
