package com.umamit.feature.journal.session

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.SessionStart
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.StepKind
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeTeaArchive
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.StepRecord
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
                collection.teas,
                archive,
            )
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                SessionPageRoute(
                    host = SessionPageHost(
                        onBack = {},
                        onOpenTea = { openedTeas += it },
                        onBrewAgain = { brewedAgain += it },
                    ),
                    viewModel = viewModel,
                )
            }
        }
        compose.waitForIdle()
    }

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
        compose.onNodeWithTag(SESSION_RESTORE_TAG).performClick()
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

    @Test
    fun `a session that is gone shows the not-found state`() {
        runBlocking { sessions.sessions.discardSession(PAST) }
        show()

        compose.onNodeWithText("Diese Sitzung ist nicht mehr da").assertExists()
    }

    private companion object {
        const val TEA = "morgentau"
        const val PAST = "past"
        val STARTED = JournalSessions.ms(LocalDateTime.of(2026, 10, 22, 9, 12))
    }
}
