package com.umamit.feature.journal.session

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.SessionStart
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.StepKind
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.model.UserSettings
import com.umamit.core.model.Vessel
import com.umamit.core.model.VesselType
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeSessionEditor
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.StepRecord
import com.umamit.feature.journal.JournalSessions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/**
 * The session editor (SCR-17, JRN-11, D-112, #230) through its view model on the fakes of core:testing: *Speichern*
 * only when changed and valid, the discard question, the stock line, the future refused, a failed write, the greyed
 * steps, and the alert path that keeps the unsaved values (rule 8). An invented tea (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SessionEditorScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val collection = FakeCollection()
    private var closed = 0
    private var saved = 0
    private val alertKey = mutableStateOf<String?>(null)

    private fun setUp(deductStock: Boolean = true): Pair<FakeSessions, FakeSessionEditor> = runBlocking {
        val sessions = FakeSessions(
            collection,
            FakeSettingsRepository(UserSettings(deductStock = deductStock)),
            nowMs = { NOW },
        )
        collection.teas.saveTea(Tea(TEA, "Sencha Morgentau", category = TeaCategory.GREEN, form = TeaForm.LOOSE_LEAF))
        collection.stock.addEvent(
            InventoryEvent("buy", TEA, InventoryEventType.INITIAL, 100.0, STARTED - DAY_MS, priceCents = 850),
        )
        collection.vessels.saveVessel(Vessel("v1", "Kyusu 250 ml", VesselType.KYUSU, 250))
        val steps = listOf(
            PlannedStep(StepKind.RINSE, 10, 80),
            PlannedStep(StepKind.INFUSION, 75, null),
            PlannedStep(StepKind.INFUSION, 20, 70),
            PlannedStep(StepKind.INFUSION, 30, 75),
        )
        sessions.sessions.startSession(SessionStart(PAST, snapshot(), steps, 10, STARTED, "Europe/Berlin"))
        sessions.sessions.recordStep(PAST, 0, StepRecord(10, 10, 0, 0, STARTED, STARTED + 10_000, false, false))
        sessions.sessions.recordStep(
            PAST,
            1,
            StepRecord(75, 95, 0, 20, STARTED + 20_000, STARTED + 115_000, false, false),
        )
        sessions.sessions.recordStep(PAST, 2, StepRecord(20, 0, 0, 0, null, null, true, false))
        sessions.sessions.completeSession(PAST, autoFinished = false)
        sessions to FakeSessionEditor(sessions)
    }

    private fun snapshot() = SessionSnapshot(
        teaId = TEA,
        teaName = "Sencha Morgentau",
        teaSecondaryName = null,
        teaType = "Sencha",
        styleKey = "sencha",
        category = TeaCategory.GREEN,
        profileId = null,
        profileName = "Kyusu",
        method = BrewMethod.KYUSU,
        vesselId = null,
        vesselName = null,
        leafGrams = 4.0,
        waterMl = 180,
        baseTempC = 65,
    )

    private fun show(editor: FakeSessionEditor): SessionEditorViewModel {
        val viewModel = SessionEditorViewModel(
            SavedStateHandle(mapOf("sessionId" to PAST)),
            editor,
            collection.vessels,
            FakeClock(wall = NOW),
        )
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                SessionEditorRoute(
                    host = SessionEditorHost(
                        onClose = { closed++ },
                        onSaved = { saved++ },
                        alertKey = alertKey.value,
                    ),
                    viewModel = viewModel,
                )
            }
        }
        compose.waitForIdle()
        return viewModel
    }

    private fun save() = compose.onNodeWithTag(SessionEditorTags.SAVE)

    private fun leaf() = compose.onNodeWithTag(SessionEditorTags.LEAF)

    @Test
    fun `Speichern is enabled only once something changed and every field is valid`() {
        val (_, editor) = setUp()
        show(editor)
        save().assertIsNotEnabled()

        leaf().performTextReplacement("5")
        save().assertIsEnabled()

        leaf().performTextReplacement("4,0")
        save().assertIsNotEnabled()

        leaf().performTextReplacement("")
        compose.onNodeWithText("0,1 bis 100 g, z. B. 4,5.").assertExists()
        save().assertIsNotEnabled()

        leaf().performTextReplacement("4,5")
        compose.onNodeWithTag(SessionEditorTags.WATER).performTextReplacement("183")
        compose.onNodeWithText("10 bis 3000 ml, in 5-ml-Schritten.").assertExists()
        save().assertIsNotEnabled()

        compose.onNodeWithTag(SessionEditorTags.WATER).performTextReplacement("180")
        compose.onNodeWithTag(SessionEditorTags.STEP_TIME + 1).performScrollTo().performTextReplacement("1:")
        compose.onNodeWithText("Als m:ss, z. B. 1:35.").assertExists()
        save().assertIsNotEnabled()

        compose.onNodeWithTag(SessionEditorTags.STEP_TIME + 1).performTextReplacement("0:00")
        compose.onNodeWithText("Zwischen 0:01 und 24:00:00.").assertExists()
        compose.onNodeWithText("Als m:ss, z. B. 1:35.").assertDoesNotExist()
        save().assertIsNotEnabled()

        compose.onNodeWithTag(SessionEditorTags.STEP_TIME + 1).performTextReplacement("1:40")
        save().assertIsEnabled()
        assertThat(editor.writes).isEqualTo(0)
    }

    @Test
    fun `Speichern writes leaf, water, vessel and a step and hands back to the page`() {
        val (sessions, editor) = setUp()
        show(editor)

        leaf().performTextReplacement("5,5")
        compose.onNodeWithTag(SessionEditorTags.WATER).performTextReplacement("200")
        compose.onNodeWithTag(SessionEditorTags.VESSEL).performClick()
        compose.onNodeWithText("Kyusu 250 ml").performClick()
        compose.onNodeWithTag(SessionEditorTags.STEP_TIME + 1).performScrollTo().performTextReplacement("1:40")
        compose.onNodeWithContentDescription("Temperatur erhöhen, Schritt 2").performClick()
        save().performClick()
        compose.waitForIdle()

        assertThat(saved).isEqualTo(1)
        val session = runBlocking { checkNotNull(sessions.sessions.getSession(PAST)) }
        assertThat(session.snapshot.leafGrams).isEqualTo(5.5)
        assertThat(session.snapshot.waterMl).isEqualTo(200)
        assertThat(session.snapshot.vesselId).isEqualTo("v1")
        assertThat(session.snapshot.vesselName).isEqualTo("Kyusu 250 ml")
        assertThat(session.steps[1].actualSeconds).isEqualTo(100)
        assertThat(session.steps[1].plannedSeconds).isEqualTo(75)
        assertThat(session.steps[1].tempC).isEqualTo(70)
        val stock = runBlocking { collection.stock.observeStock(TEA).first() }
        assertThat(stock.remainingGrams).isWithin(1e-9).of(94.5)
    }

    @Test
    fun `leaving with changes asks first, without changes it closes at once`() {
        val (_, editor) = setUp()
        show(editor)
        compose.onNodeWithContentDescription("Schließen").performClick()
        assertThat(closed).isEqualTo(1)

        leaf().performTextReplacement("6")
        compose.onNodeWithContentDescription("Schließen").performClick()
        compose.onNodeWithText("Änderungen verwerfen?").assertExists()
        compose.onNodeWithText("Weiter bearbeiten").performClick()
        assertThat(closed).isEqualTo(1)
        leaf().assertTextContains("6")

        compose.onNodeWithContentDescription("Schließen").performClick()
        compose.onNodeWithText("Verwerfen").performClick()
        assertThat(closed).isEqualTo(2)
        assertThat(editor.writes).isEqualTo(0)
    }

    @Test
    fun `the stock line shows while the leaf differs`() {
        val (_, editor) = setUp()
        show(editor)
        compose.onNodeWithTag(SessionEditorTags.STOCK_LINE).assertDoesNotExist()

        leaf().performTextReplacement("6")
        compose.onNodeWithText("Dein Vorrat wird um 2 g angepasst.").assertExists()

        leaf().performTextReplacement("3,5")
        compose.onNodeWithText("Dein Vorrat wird um 0,5 g angepasst.").assertExists()

        leaf().performTextReplacement("4")
        compose.onNodeWithTag(SessionEditorTags.STOCK_LINE).assertDoesNotExist()
    }

    @Test
    fun `a session without a stock deduction shows no stock line`() {
        val (_, editor) = setUp(deductStock = false)
        show(editor)

        leaf().performTextReplacement("6")

        save().assertIsEnabled()
        compose.onNodeWithTag(SessionEditorTags.STOCK_LINE).assertDoesNotExist()
    }

    @Test
    fun `a start in the future is refused at the field`() {
        val (_, editor) = setUp()
        val viewModel = show(editor)

        // The session started at 9:12 and ended about two minutes later; now is 11:12.
        viewModel.setTime(hour = 11, minute = 30)
        compose.waitForIdle()

        compose.onNodeWithText("Die Sitzung kann nicht in der Zukunft liegen.").assertExists()
        save().assertIsNotEnabled()

        viewModel.setTime(hour = 8, minute = 0)
        compose.waitForIdle()
        compose.onNodeWithTag(SessionEditorTags.FUTURE).assertDoesNotExist()
        compose.onNodeWithText("08:00").assertExists()
        save().assertIsEnabled()
    }

    @Test
    fun `a failed write keeps the editor open with its values`() {
        val (_, editor) = setUp()
        show(editor)
        editor.failNext = true

        leaf().performTextReplacement("6")
        save().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Die Änderungen konnten nicht gespeichert werden.").assertExists()
        assertThat(saved).isEqualTo(0)
        leaf().assertTextContains("6")
        save().assertIsEnabled()
    }

    @Test
    fun `never started and skipped steps show greyed, without fields`() {
        val (_, editor) = setUp()
        show(editor)

        compose.onNodeWithText("3 · Aufguss · übersprungen").performScrollTo().assertExists()
        compose.onNodeWithText("4 · Aufguss · nicht gebrüht").performScrollTo().assertExists()
        compose.onNodeWithTag(SessionEditorTags.STEP_TIME + 0).assertExists()
        compose.onNodeWithTag(SessionEditorTags.STEP_TIME + 2).assertDoesNotExist()
        compose.onNodeWithTag(SessionEditorTags.STEP_TIME + 3).assertDoesNotExist()
    }

    @Test
    fun `an alert closes the question in its first frame and the editor keeps its unsaved values`() {
        val (_, editor) = setUp()
        show(editor)
        leaf().performTextReplacement("6")
        compose.onNodeWithContentDescription("Schließen").performClick()
        compose.onNodeWithText("Änderungen verwerfen?").assertExists()

        alertKey.value = "alert-1"
        compose.waitForIdle()
        compose.onNodeWithText("Änderungen verwerfen?").assertDoesNotExist()

        alertKey.value = null
        compose.waitForIdle()
        leaf().assertTextContains("6")
        compose.onNodeWithText("Dein Vorrat wird um 2 g angepasst.").assertExists()
        save().assertIsEnabled()
        assertThat(closed).isEqualTo(0)
        assertThat(editor.writes).isEqualTo(0)
    }

    @Test
    fun `the session page offers Bearbeiten above Loeschen in its menu`() {
        val edited = mutableListOf<String>()
        val page = SessionPageScreenshotTest.page
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                SessionPageScreen(
                    state = SessionPageUiState.Ready(page),
                    actions = SessionPageActions(edit = { edited += it }),
                )
            }
        }
        compose.onNodeWithTag(SESSION_MENU_TAG).performClick()
        compose.onNodeWithText("Löschen").assertExists()
        compose.onNodeWithTag(SESSION_EDIT_TAG).assertTextContains("Bearbeiten").performClick()

        assertThat(edited).containsExactly(page.sessionId)
    }

    private companion object {
        const val PAST = "past"
        const val TEA = "morgentau"
        const val DAY_MS = 86_400_000L
        val STARTED = JournalSessions.ms(LocalDateTime.of(2026, 10, 22, 9, 12))
        val NOW = JournalSessions.ms(LocalDateTime.of(2026, 10, 22, 11, 12))
    }
}
