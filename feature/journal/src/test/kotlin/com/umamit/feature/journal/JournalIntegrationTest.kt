package com.umamit.feature.journal

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.umamit.core.data.SessionEdit
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeRecipeOffers
import com.umamit.core.testing.FakeSessionEditor
import com.umamit.core.testing.FakeSessions
import com.umamit.core.timer.controller.SavedSheetRequests
import kotlinx.coroutines.runBlocking
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

    private val clock = FakeClock(wall = JournalSessions.ms(LocalDateTime.of(2026, 10, 22, 9, 30)))
    private val data = FakeSessions(nowMs = { clock.wall })
    private val requests = SavedSheetRequests(clock)
    private val sources =
        JournalSources(
            data.journal,
            data.sessions,
            requests,
            clock,
            FakeCollection().teas,
            data.tasting,
            FakeRecipeOffers(),
        )
    private val controller = FakeBrewController(data.sessions, requests)

    @Test
    fun `a session appears pinned while it runs and under today once finished, with the sheet`() {
        compose.setContent {
            UmamiTheme(darkTheme = false, reduceMotion = true) {
                Box {
                    JournalRoute(sources = sources, onOpenSettings = {}, onOpenTimer = {})
                    SavedSheetHost(
                        requests = requests,
                        sessions = data.sessions,
                        teas = sources.teas,
                        tasting = sources.tasting,
                        offers = sources.recipeOffers,
                        onOpenNotes = {},
                        alerting = false,
                    )
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

    @Test
    fun `an edited session moves to its new day in the journal`() {
        controller.brew("s1", 60, 20)
        compose.setContent {
            UmamiTheme(darkTheme = false, reduceMotion = true) {
                JournalRoute(sources = sources, onOpenSettings = {}, onOpenTimer = {})
            }
        }
        compose.onNodeWithText("HEUTE").assertIsDisplayed()
        compose.onNodeWithText("GESTERN").assertDoesNotExist()

        // JRN-11: the session editor moves the start one day back, to 08:12 the day before.
        val start = JournalSessions.ms(LocalDateTime.of(2026, 10, 21, 8, 12))
        runBlocking { FakeSessionEditor(data).editSession("s1", SessionEdit(startedAtMs = start)) }

        compose.onNodeWithText("GESTERN").assertIsDisplayed()
        compose.onNodeWithText("HEUTE").assertDoesNotExist()
        compose.onNodeWithText("08:12").assertIsDisplayed()
    }
}
