package com.umamit.feature.journal

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeSessions
import com.umamit.core.timer.controller.SavedSheetRequests
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/**
 * JRN-01 through the journal's seams: a session brewed and finished through a
 * fake controller appears in the journal at once, first pinned while it runs,
 * then under „Heute", with the „Gespeichert" sheet (SCR-06).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class JournalIntegrationTest {
    @get:Rule
    val compose = createComposeRule()

    private val data = FakeSessions()
    private val clock = FakeClock(wall = JournalSessions.ms(LocalDateTime.of(2026, 10, 22, 9, 30)))
    private val requests = SavedSheetRequests(clock)
    private val sources = JournalSources(data.journal, data.sessions, requests, clock)
    private val controller = FakeBrewController(data.sessions, requests)

    @Test
    fun `a session appears pinned while it runs and under today once finished, with the sheet`() {
        compose.setContent {
            UmamiTheme(darkTheme = false, reduceMotion = true) {
                Box {
                    JournalRoute(sources = sources, onOpenSettings = {}, onOpenTimer = {})
                    SavedSheetHost(requests = requests, sessions = data.sessions, alerting = false)
                }
            }
        }
        compose.onNodeWithText("Deine erste Tasse wartet.").assertIsDisplayed()

        controller.start("s1", 60, 20, 60)
        controller.pour("s1", 0, 60)
        compose.onNodeWithText("LÄUFT GERADE").assertIsDisplayed()
        compose.onNodeWithText("55 °C · 1:00 · 1 von 3 Aufgüssen").assertIsDisplayed()

        controller.pour("s1", 1, 20)
        controller.finish("s1")

        compose.onNodeWithText("LÄUFT GERADE").assertDoesNotExist()
        compose.onNodeWithText("HEUTE").assertIsDisplayed()
        compose.onNodeWithText("09:12").assertIsDisplayed()
        compose.onNodeWithText("55 °C · 1:00 · 2 von 3 Aufgüssen").assertIsDisplayed()
        compose.onNodeWithText("Gespeichert im Journal").assertIsDisplayed()
        compose.onNodeWithText("2 Aufgüsse · 1:20").assertIsDisplayed()
    }
}
