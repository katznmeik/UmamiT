package com.umamit.feature.journal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.RecipeCheckReason
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeSessions
import com.umamit.core.timer.controller.SavedSheetRequests
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The „Gespeichert" sheet (SCR-06, TMR-12): shown when the controller reports
 * a finished session, gone after 10 s or a swipe, and never over an alert (rule 8).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class SavedSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val collection = FakeCollection()
    private val data = FakeSessions(collection)
    private val clock = FakeClock()
    private val requests = SavedSheetRequests(clock)
    private val controller = FakeBrewController(data.sessions, requests)
    private var alerting by mutableStateOf(false)

    /** Whether the shell, and with it the sheet's host, is composed (the timer route covers it otherwise). */
    private var hostComposed by mutableStateOf(true)

    private fun show(reduceMotion: Boolean = false) {
        compose.setContent {
            UmamiTheme(darkTheme = false, reduceMotion = reduceMotion) {
                if (hostComposed) {
                    SavedSheetHost(
                        requests = requests,
                        sessions = data.sessions,
                        teas = collection.teas,
                        alerting = alerting,
                    )
                }
            }
        }
    }

    private fun composeHost(composed: Boolean) {
        hostComposed = composed
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeByFrame()
    }

    @Test
    fun `a finished session shows its infusions and steeping time`() {
        show()

        controller.brew("s1", 80, 20, 40)

        compose.onNodeWithText("Gespeichert im Journal").assertIsDisplayed()
        compose.onNodeWithText("3 Aufgüsse · 2:20").assertIsDisplayed()
    }

    @Test
    fun `the sheet dismisses itself after 10 s`() {
        show()
        compose.mainClock.autoAdvance = false
        controller.brew("s1", 60)
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("1 Aufguss · 1:00").assertIsDisplayed()

        compose.mainClock.advanceTimeBy(8_500)
        compose.onNodeWithText("1 Aufguss · 1:00").assertIsDisplayed()

        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithText("Gespeichert im Journal").assertDoesNotExist()
        assertThat(requests.pending.value).isNull()
    }

    private fun saveTea(id: String, reason: RecipeCheckReason?) = runBlocking {
        collection.teas.saveTea(
            Tea(id, "Tee $id", category = TeaCategory.GREEN, form = TeaForm.LOOSE_LEAF, recipeCheckReason = reason),
        )
    }

    @Test
    fun `Rezept passt clears the flag of the session's tea only`() {
        saveTea("flagged", RecipeCheckReason.FROM_TEXT)
        saveTea("other", RecipeCheckReason.FROM_STYLE_DEFAULT)
        show()

        controller.brew("s1", 60, teaId = "flagged")
        compose.onNodeWithText("Rezept passt").assertIsDisplayed().performClick()
        compose.waitForIdle()

        assertThat(runBlocking { collection.teas.getTea("flagged") }?.recipeCheckReason).isNull()
        assertThat(runBlocking { collection.teas.getTea("other") }?.recipeCheckReason)
            .isEqualTo(RecipeCheckReason.FROM_STYLE_DEFAULT)
        // The sheet says so instead of offering it again.
        compose.onNodeWithText("Rezept passt").assertDoesNotExist()
        compose.onNodeWithText("„Rezept prüfen“ ist entfernt.").assertIsDisplayed()
    }

    @Test
    fun `a session of a tea not flagged offers no Rezept passt`() {
        saveTea("fine", null)
        show()

        controller.brew("s1", 60, teaId = "fine")

        compose.onNodeWithText("Gespeichert im Journal").assertIsDisplayed()
        compose.onNodeWithText("Rezept passt").assertDoesNotExist()
    }

    @Test
    fun `an alert removes the sheet with Rezept passt in the same frame`() {
        saveTea("flagged", RecipeCheckReason.FROM_TEXT)
        show()
        compose.mainClock.autoAdvance = false
        controller.brew("s1", 60, teaId = "flagged")
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Rezept passt").assertIsDisplayed()

        alerting = true
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeByFrame()

        compose.onNodeWithText("Rezept passt").assertDoesNotExist()
        assertThat(runBlocking { collection.teas.getTea("flagged") }?.recipeCheckReason)
            .isEqualTo(RecipeCheckReason.FROM_TEXT)
    }

    @Test
    fun `a swipe down dismisses it`() {
        show()
        controller.brew("s1", 60)

        compose.onNodeWithText("Gespeichert im Journal").performTouchInput { swipeDown() }
        compose.waitForIdle()

        compose.onNodeWithText("Gespeichert im Journal").assertDoesNotExist()
        assertThat(requests.pending.value).isNull()
    }

    @Test
    fun `a finger resting on the sheet holds it past 10 s, and the 10 s start again on release`() {
        show()
        compose.mainClock.autoAdvance = false
        controller.brew("s1", 60)
        compose.mainClock.advanceTimeBy(1_000)
        val sheet = compose.onNodeWithText("Gespeichert im Journal")

        // A still finger: no movement, so no drag ever starts.
        sheet.performTouchInput { down(center) }
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(12_000)
        sheet.assertIsDisplayed()

        sheet.performTouchInput { up() }
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(9_000)
        sheet.assertIsDisplayed()
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithText("Gespeichert im Journal").assertDoesNotExist()
    }

    @Test
    fun `a dragging finger holds it too`() {
        show()
        compose.mainClock.autoAdvance = false
        controller.brew("s1", 60)
        compose.mainClock.advanceTimeBy(1_000)
        val sheet = compose.onNodeWithText("Gespeichert im Journal")

        sheet.performTouchInput {
            down(center)
            moveBy(Offset(0f, 30f))
            moveBy(Offset(0f, 30f))
        }
        compose.mainClock.advanceTimeBy(12_000)

        sheet.assertIsDisplayed()
    }

    @Test
    fun `a sheet whose 10 s ran out while the shell was covered does not come back`() {
        show()
        compose.mainClock.autoAdvance = false
        controller.brew("s1", 60)
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Gespeichert im Journal").assertIsDisplayed()

        // The next brew's timer covers the shell for a minute, then the user returns.
        composeHost(false)
        clock.advanceBy(60_000)
        composeHost(true)
        compose.mainClock.advanceTimeBy(1_000)

        compose.onNodeWithText("Gespeichert im Journal").assertDoesNotExist()
        assertThat(requests.pending.value).isNull()
    }

    @Test
    fun `a sheet covered briefly comes back only for the rest of its 10 s`() {
        show()
        compose.mainClock.autoAdvance = false
        controller.brew("s1", 60)
        compose.mainClock.advanceTimeBy(1_000)

        composeHost(false)
        clock.advanceBy(4_000)
        composeHost(true)
        compose.mainClock.advanceTimeBy(1_000)
        clock.advanceBy(1_000)
        compose.onNodeWithText("Gespeichert im Journal").assertIsDisplayed()

        // 4 s covered and 1 s shown: 5 s are left, not a fresh 10.
        compose.mainClock.advanceTimeBy(6_000)
        compose.onNodeWithText("Gespeichert im Journal").assertDoesNotExist()
    }

    @Test
    fun `an alert removes the sheet in the same frame and it does not come back`() {
        show()
        compose.mainClock.autoAdvance = false
        controller.brew("s1", 60)
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Gespeichert im Journal").assertIsDisplayed()

        alerting = true
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeByFrame()

        compose.onNodeWithText("Gespeichert im Journal").assertDoesNotExist()
        alerting = false
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Gespeichert im Journal").assertDoesNotExist()
        assertThat(requests.pending.value).isNull()
    }

    @Test
    fun `a sheet due while an alert plays is never shown`() {
        alerting = true
        show()

        controller.brew("s1", 60)
        compose.waitForIdle()

        compose.onNodeWithText("Gespeichert im Journal").assertDoesNotExist()
        assertThat(requests.pending.value).isNull()
    }

    @Test
    fun `a newer session replaces the sheet`() {
        show()
        controller.brew("s1", 60)
        controller.brew("s2", 60, 30)

        compose.onNodeWithText("2 Aufgüsse · 1:30").assertIsDisplayed()
        compose.onNodeWithText("1 Aufguss · 1:00").assertDoesNotExist()
    }

    @Test
    fun `under reduce motion it still shows and dismisses`() {
        show(reduceMotion = true)
        compose.mainClock.autoAdvance = false
        controller.brew("s1", 60)
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("Gespeichert im Journal").assertIsDisplayed()

        // 10 s, then the short reduce-motion fade (100 ms).
        compose.mainClock.advanceTimeBy(10_300)

        compose.onNodeWithText("Gespeichert im Journal").assertDoesNotExist()
    }

    @Test
    fun `the summary counts poured infusions only`() {
        val summary = savedSummary(
            JournalSessions.session(
                "s",
                java.time.LocalDateTime.of(2026, 10, 22, 9, 0),
                steps = listOf(
                    JournalSessions.step(0, 10, kind = com.umamit.core.model.StepKind.RINSE),
                    JournalSessions.step(1, 80),
                    JournalSessions.step(2, 20, skipped = true),
                    JournalSessions.step(3, 60, actualSeconds = 65),
                ),
            ).session,
        )

        assertThat(summary).isEqualTo(SavedSummary(infusions = 2, steepSeconds = 145))
    }
}
