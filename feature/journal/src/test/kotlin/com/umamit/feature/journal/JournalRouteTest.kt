package com.umamit.feature.journal

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeRecipeOffers
import com.umamit.core.testing.FakeSessions
import com.umamit.core.timer.controller.SavedSheetRequests
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/** The journal tab keeps „Heute" true while it stays open: at midnight today's sessions move to „Gestern" (D-061). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class JournalRouteTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `at midnight today's sessions move to yesterday`() {
        val data = FakeSessions()
        val clock = FakeClock(wall = JournalSessions.ms(LocalDateTime.of(2026, 10, 22, 23, 59, 50)))
        val requests = SavedSheetRequests(clock)
        // A session brewed at 09:12 today.
        FakeBrewController(data.sessions, requests).brew("s1", 60)
        requests.dismiss("s1")
        val sources =
            JournalSources(
                data.journal,
                data.sessions,
                requests,
                clock,
                FakeCollection().teas,
                data.tasting,
                FakeRecipeOffers(),
            )
        compose.mainClock.autoAdvance = false
        compose.setContent {
            UmamiTheme(darkTheme = false, reduceMotion = true) {
                JournalRoute(sources = sources, onOpenSettings = {}, onOpenTimer = {})
            }
        }
        compose.mainClock.advanceTimeBy(100)
        // The list is built off the main thread: wait for it.
        awaitText("HEUTE")
        compose.onNodeWithText("HEUTE").assertIsDisplayed()

        // Ten seconds later the phone's date is the 23rd.
        clock.advanceBy(10_000)
        compose.mainClock.advanceTimeBy(10_000)
        awaitText("GESTERN")

        compose.onNodeWithText("GESTERN").assertIsDisplayed()
        compose.onNodeWithText("HEUTE").assertDoesNotExist()
    }

    /** Steps the paused clock until [text] shows: the list is built on another thread, which the clock cannot see. */
    private fun awaitText(text: String) {
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(STEP_MS)
            compose.mainClock.advanceTimeBy(FRAME_MS)
        }
        check(compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()) {
            "\"$text\" did not show within $WAIT_MS ms"
        }
    }

    private companion object {
        const val WAIT_MS = 5_000L
        const val STEP_MS = 10L
        const val FRAME_MS = 16L
    }
}
