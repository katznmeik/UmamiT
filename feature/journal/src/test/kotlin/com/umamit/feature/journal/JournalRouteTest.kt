package com.umamit.feature.journal

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeCollection
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
        val sources = JournalSources(data.journal, data.sessions, requests, clock, FakeCollection().teas)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            UmamiTheme(darkTheme = false, reduceMotion = true) {
                JournalRoute(sources = sources, onOpenSettings = {}, onOpenTimer = {})
            }
        }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("HEUTE").assertIsDisplayed()

        // Ten seconds later the phone's date is the 23rd.
        clock.advanceBy(10_000)
        compose.mainClock.advanceTimeBy(10_000)

        compose.onNodeWithText("GESTERN").assertIsDisplayed()
        compose.onNodeWithText("HEUTE").assertDoesNotExist()
    }
}
