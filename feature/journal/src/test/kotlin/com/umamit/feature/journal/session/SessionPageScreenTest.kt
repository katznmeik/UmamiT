package com.umamit.feature.journal.session

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
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
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeRecipeOffers
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeTeaArchive
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.StepRecord
import com.umamit.core.ui.RATING_STARS_TAG
import com.umamit.core.ui.TASTE_RADAR_TAG
import com.umamit.core.ui.ratingStarTag
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
 * The session page (SCR-16 read-only, BRW-06, D-100) with its real view model on the fakes of core:testing (seam 2):
 * the content and the step table, and the four states: a tea in the collection, a session whose recipe is gone (the
 * page is the same; the setup shows *Ohne Rezept*), an archived tea and a deleted one. An invented tea (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class SessionPageScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val collection = FakeCollection()
    private val sessions = FakeSessions(collection)
    private val archive = FakeTeaArchive(sessions)
    private val openedTeas = mutableListOf<String>()
    private val brewedAgain = mutableListOf<String>()
    private var deleted = 0

    /** Whether the page is composed; set to false, it is left as when back or an alert takes the screen. */
    private val onScreen = mutableStateOf(true)

    private fun leavePage() {
        compose.runOnUiThread { onScreen.value = false }
        compose.waitForIdle()
    }

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
            profileId = "deleted-recipe",
            profileName = "Kräftig",
            method = BrewMethod.KYUSU,
            vesselId = "kyusu",
            vesselName = "Kyusu 250 ml",
            leafGrams = 4.5,
            waterMl = 180,
            baseTempC = 65,
        )
        val steps = listOf(
            PlannedStep(StepKind.RINSE, 10, 80),
            PlannedStep(StepKind.INFUSION, 75, null),
            PlannedStep(StepKind.INFUSION, 30, 75),
        )
        sessions.sessions.startSession(SessionStart(PAST, snapshot, steps, 10, STARTED, "Europe/Berlin"))
        sessions.sessions.recordStep(PAST, 0, record(10, 10))
        sessions.sessions.recordStep(PAST, 1, record(75, 95))
        sessions.sessions.completeSession(PAST, autoFinished = false)
    }

    private fun record(planned: Int, actual: Int) =
        StepRecord(planned, actual, 0, actual - planned, STARTED, STARTED + 1_000, false, false)

    private fun show() {
        val viewModel =
            SessionPageViewModel(
                SavedStateHandle(mapOf("sessionId" to PAST)),
                sessions.journal,
                FakeRecipeOffers(),
                collection.teas,
                archive,
                sessions.tasting,
            )
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                if (onScreen.value) {
                    SessionPageRoute(
                        host = SessionPageHost(
                            onBack = {},
                            onOpenTea = { openedTeas += it },
                            onBrewAgain = { brewedAgain += it },
                            onDeleted = { deleted++ },
                        ),
                        viewModel = viewModel,
                        heroViewModel = heroViewModel(sessions, PAST),
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun session() = runBlocking { sessions.sessions.getSession(PAST)!! }

    private fun star(n: Int) = compose.onNodeWithTag(ratingStarTag(n), useUnmergedTree = true)

    private fun stars() = compose.onNodeWithTag(RATING_STARS_TAG)

    private fun stepRow(description: String) = compose.onNode(hasContentDescription(description))

    @Test
    fun `the page shows the tea, the start, method, vessel, leaf, water, ratio and the step table`() {
        show()

        compose.onNodeWithText("Sencha Morgentau").assertExists()
        compose.onNodeWithText("22. Okt. 2026 · 09:12").assertExists()
        compose.onNodeWithText("Kyusu").assertExists()
        compose.onNodeWithText("Kyusu 250 ml").assertExists()
        compose.onNodeWithText("4,5 g").assertExists()
        compose.onNodeWithText("180 ml").assertExists()
        compose.onNodeWithText("2,5 g / 100 ml").assertExists()
        stepRow("Spülen: geplant\u00A010 Sekunden, tatsächlich\u00A010 Sekunden, 80 °C").assertExists()
        // The set time from the Schritte-Sheet and the measured time with its overtime (11 „Set time").
        stepRow(
            "Aufguss 1: geplant\u00A01 Minute 15 Sekunden, tatsächlich\u00A01 Minute 35 Sekunden, 65 °C",
        ).assertExists()
        // Kept from v0.4.0 without a measured time (Q-19).
        stepRow("Aufguss 2: geplant\u00A030 Sekunden, nicht gebrüht, 75 °C").assertExists()
    }

    @Test
    fun `the tea opens its detail and So nochmal bruehen hands the session to the host, also without its recipe`() {
        show()

        compose.onNodeWithTag(SESSION_TEA_TAG).performClick()
        compose.onNodeWithTag(SESSION_BREW_AGAIN_TAG).performScrollTo().assertIsEnabled().performClick()

        assertThat(openedTeas).containsExactly(TEA)
        assertThat(brewedAgain).containsExactly(PAST)
    }

    @Test
    fun `an archived tea greys out So nochmal bruehen, and Wiederherstellen enables it`() {
        runBlocking { archive.archive(TEA) }
        show()

        compose.onNodeWithTag(SESSION_BREW_AGAIN_TAG).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Tee ist archiviert").assertExists()
        compose.onNodeWithTag(SESSION_RESTORE_TAG).performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(SESSION_BREW_AGAIN_TAG).assertIsEnabled()
        compose.onNodeWithText("Tee ist archiviert").assertDoesNotExist()
        assertThat(archive.archived).containsExactly(TEA)
    }

    @Test
    fun `a deleted tea leaves the page readable, without a tea link and without the button`() {
        runBlocking { archive.delete(TEA) }
        show()

        compose.onNodeWithText("Sencha Morgentau").assertExists()
        compose.onNodeWithTag(SESSION_TEA_TAG).assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        compose.onNodeWithTag(SESSION_BREW_AGAIN_TAG).assertDoesNotExist()
        stepRow("Aufguss 2: geplant\u00A030 Sekunden, nicht gebrüht, 75 °C").assertExists()
    }

    /** ⋮ *Löschen* asks „Sitzung löschen?" first; confirmed, the session is gone and the host is told (D-102). */
    @Test
    fun `Loeschen asks first, then removes the session and tells the host`() {
        show()

        compose.onNodeWithTag(SESSION_MENU_TAG).performClick()
        compose.onNodeWithText("Löschen").performClick()
        compose.onNodeWithText("Sitzung löschen?").assertExists()
        compose.onNodeWithText("Abbrechen").performClick()
        compose.waitForIdle()
        assertThat(runBlocking { sessions.sessions.getSession(PAST) }).isNotNull()
        assertThat(deleted).isEqualTo(0)

        compose.onNodeWithTag(SESSION_MENU_TAG).performClick()
        compose.onNodeWithText("Löschen").performClick()
        compose.onNodeWithText("Sitzung löschen?").assertExists()
        compose.onNodeWithText("Löschen").performClick()
        compose.waitForIdle()

        assertThat(runBlocking { sessions.sessions.getSession(PAST) }).isNull()
        assertThat(deleted).isEqualTo(1)
    }

    @Test
    fun `a session that is gone shows the not-found state`() {
        runBlocking { sessions.sessions.discardSession(PAST) }
        show()

        compose.onNodeWithText("Diese Sitzung ist nicht mehr da").assertExists()
    }

    @Test
    fun `a tap on a star rates the session and persists it, the cycle takes half off and clears`() {
        show()

        star(4).performClick()
        compose.waitForIdle()
        assertThat(session().rating).isEqualTo(4.0)
        star(4).performClick()
        compose.waitForIdle()
        assertThat(session().rating).isEqualTo(3.5)
        star(4).performClick()
        compose.waitForIdle()
        assertThat(session().rating).isNull()
    }

    @Test
    fun `TalkBack reads the rating, adjusts in half steps and removes it`() {
        show()
        stars().assert(hasStateDescription("keine"))

        stars().performSemanticsAction(SemanticsActions.SetProgress) { it(3.5f) }
        compose.waitForIdle()
        assertThat(session().rating).isEqualTo(3.5)
        stars().assert(hasStateDescription("3,5 von 5 Sternen"))
        stars().assert(hasContentDescription("Bewertung"))

        stars().performSemanticsAction(SemanticsActions.SetProgress) { it(4f) }
        compose.waitForIdle()
        assertThat(session().rating).isEqualTo(4.0)

        val remove = stars().fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .single { it.label == "Bewertung entfernen" }
        compose.runOnUiThread { remove.action() }
        compose.waitForIdle()
        assertThat(session().rating).isNull()
        stars().assert(hasStateDescription("keine"))
    }

    @Test
    fun `a note is saved when the page is left, and shown again when the page opens`() {
        show()

        compose.onNodeWithTag(SESSION_NOTES_TAG).performTextInput("  Mild und cremig  ")
        leavePage()

        assertThat(session().notes).isEqualTo("Mild und cremig")
    }

    @Test
    fun `a note typed is saved at once when the app stops, and the field shows the stored note`() {
        runBlocking { sessions.tasting.setNotes(PAST, "Vom Vortag") }
        show()
        compose.onNodeWithText("Vom Vortag").assertExists()

        compose.onNodeWithTag(SESSION_NOTES_TAG).performTextReplacement("Vom Vortag, nochmal")
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        compose.waitForIdle()

        assertThat(session().notes).isEqualTo("Vom Vortag, nochmal")
    }

    @Test
    fun `the counter shows from 4500 characters and the field stops at 5000`() {
        show()

        compose.onNodeWithTag(SESSION_NOTES_TAG).performTextInput("a".repeat(4_499))
        compose.onNodeWithText("4.499 / 5.000").assertDoesNotExist()
        compose.onNodeWithTag(SESSION_NOTES_TAG).performTextInput("bb")
        compose.onNodeWithText("4.501 / 5.000").assertExists()
        compose.onNodeWithTag(SESSION_NOTES_TAG).performTextInput("c".repeat(600))
        compose.onNodeWithText("5.000 / 5.000").assertExists()
    }

    private fun slider(axis: TasteAxis) = compose.onNodeWithTag(tasteSliderTag(axis)).performScrollTo()

    private fun release(axis: TasteAxis, value: Float) {
        slider(axis).performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
        compose.waitForIdle()
    }

    private fun openTaste() {
        compose.onNodeWithTag(SESSION_TASTE_ADD_TAG).performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Test
    fun `GESCHMACK starts as one row and opening it writes nothing, untouched sliders stay unset`() {
        show()
        compose.onNodeWithText("Geschmack hinzufügen").performScrollTo().assertExists()
        compose.onNodeWithTag(tasteSliderTag(TasteAxis.UMAMI)).assertDoesNotExist()

        openTaste()
        release(TasteAxis.UMAMI, 4f)

        slider(TasteAxis.SWEETNESS).assert(hasStateDescription("nicht gesetzt"))
        slider(TasteAxis.UMAMI).assert(hasStateDescription("4 von 5"))
        assertThat(session().infusions.first().taste.umami).isEqualTo(4)
        assertThat(session().infusions.first().taste.sweetness).isNull()
        assertThat(session().infusions.first().taste.setCount).isEqualTo(1)
    }

    @Test
    fun `a taste value persists and the page shows it again when it opens, already unfolded`() {
        runBlocking { sessions.tasting.setInfusionTaste(PAST, 1, TasteAxis.BODY, 3) }
        show()

        slider(TasteAxis.BODY).assert(hasStateDescription("3 von 5"))
        compose.onNodeWithTag(SESSION_TASTE_ADD_TAG).assertDoesNotExist()
    }

    @Test
    fun `the radar appears with the third axis and not before`() {
        show()
        openTaste()

        release(TasteAxis.UMAMI, 4f)
        release(TasteAxis.AROMA, 2f)
        compose.onNodeWithTag(TASTE_RADAR_TAG).assertDoesNotExist()

        release(TasteAxis.BODY, 1f)
        compose.onNodeWithTag(TASTE_RADAR_TAG).performScrollTo().assertExists()
        compose.onNodeWithTag(TASTE_RADAR_TAG)
            .assert(hasContentDescription("Geschmacksprofil: Umami 4 von 5, Körper 1 von 5, Aroma 2 von 5"))
    }

    @Test
    fun `a release followed at once by Zuruecksetzen ends folded with nothing set`() {
        show()
        openTaste()
        slider(TasteAxis.UMAMI).performSemanticsAction(SemanticsActions.SetProgress) { it(3f) }
        compose.onNodeWithTag(SESSION_TASTE_RESET_TAG).performScrollTo().performClick()
        compose.waitForIdle()

        assertThat(session().infusions.first().taste.setCount).isEqualTo(0)
        compose.onNodeWithTag(tasteSliderTag(TasteAxis.UMAMI)).assertDoesNotExist()
        compose.onNodeWithTag(SESSION_TASTE_ADD_TAG).performScrollTo().assertExists()
    }

    @Test
    fun `Zuruecksetzen clears all six and folds back to the one row`() {
        runBlocking {
            sessions.tasting.setInfusionTaste(PAST, 1, TasteAxis.UMAMI, 5)
            sessions.tasting.setInfusionTaste(PAST, 1, TasteAxis.BODY, 3)
            sessions.tasting.setInfusionTaste(PAST, 1, TasteAxis.AROMA, 1)
        }
        show()
        compose.onNodeWithTag(TASTE_RADAR_TAG).performScrollTo().assertExists()

        compose.onNodeWithTag(SESSION_TASTE_RESET_TAG).performScrollTo().performClick()
        compose.waitForIdle()

        assertThat(session().infusions.first().taste.setCount).isEqualTo(0)
        compose.onNodeWithTag(TASTE_RADAR_TAG).assertDoesNotExist()
        compose.onNodeWithTag(tasteSliderTag(TasteAxis.UMAMI)).assertDoesNotExist()
        compose.onNodeWithTag(SESSION_TASTE_ADD_TAG).performScrollTo().assertExists()
    }

    @Test
    fun `an archived and a deleted tea keep both inputs working`() {
        runBlocking { archive.delete(TEA) }
        show()

        star(2).performClick()
        compose.onNodeWithTag(SESSION_NOTES_TAG).performTextInput("Ohne Tee")
        leavePage()

        assertThat(session().rating).isEqualTo(2.0)
        assertThat(session().notes).isEqualTo("Ohne Tee")
    }

    private companion object {
        const val TEA = "morgentau"
        const val PAST = "past"
        val STARTED = JournalSessions.ms(LocalDateTime.of(2026, 10, 22, 9, 12))
    }
}
