package com.umamit.feature.journal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.testing.FakeSessions
import com.umamit.core.timer.controller.SavedSheetRequests
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

    private val data = FakeSessions()
    private val requests = SavedSheetRequests()
    private val controller = FakeBrewController(data.sessions, requests)
    private var alerting by mutableStateOf(false)

    private fun show(reduceMotion: Boolean = false) {
        compose.setContent {
            UmamiTheme(darkTheme = false, reduceMotion = reduceMotion) {
                SavedSheetHost(requests = requests, sessions = data.sessions, alerting = alerting)
            }
        }
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
    fun `a finger on the sheet holds it past 10 s, and the 10 s start again on release`() {
        show()
        compose.mainClock.autoAdvance = false
        controller.brew("s1", 60)
        compose.mainClock.advanceTimeBy(1_000)
        val sheet = compose.onNodeWithText("Gespeichert im Journal")

        sheet.performTouchInput {
            down(center)
            // Past the touch slop, so the drag has started.
            moveBy(Offset(0f, 30f))
            moveBy(Offset(0f, 30f))
        }
        compose.mainClock.advanceTimeBy(12_000)
        sheet.assertIsDisplayed()

        sheet.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(9_000)
        sheet.assertIsDisplayed()
        compose.mainClock.advanceTimeBy(2_000)
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
