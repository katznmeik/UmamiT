package com.umamit.feature.journal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.umamit.core.data.JournalRepository
import com.umamit.core.data.JournalSession
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.TeaTasting
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeRecipeOffers
import com.umamit.core.testing.FakeSessions
import com.umamit.core.timer.controller.SavedSheetRequests
import com.umamit.feature.journal.JournalSessions.TODAY
import com.umamit.feature.journal.JournalSessions.session
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.coroutines.EmptyCoroutineContext

/**
 * The tea detail's *Alle anzeigen* shows the journal's list for one tea (TEA-13, JRN-09, #232): a request sets *Tee*
 * and clears every other filter and the search; each request applies once, so the same tea asked for again after the
 * user cleared the chip shows again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class JournalTeaRequestTest {
    @get:Rule
    val compose = createComposeRule()

    private val sessions = listOf(
        session("hisui", LocalDateTime.of(TODAY, LocalTime.of(9, 0)), teaName = "Bio Sencha Hisui", rating = 4.5),
        session("gyokuro", LocalDateTime.of(TODAY.minusDays(1), LocalTime.of(18, 0)), teaName = "Gyokuro Asahi"),
    )

    private var request by mutableStateOf<JournalTeaRequest?>(null)

    private fun show() {
        val clock = FakeClock(wall = JournalSessions.ms(LocalDateTime.of(TODAY, LocalTime.NOON)))
        val journal = object : JournalRepository {
            override fun observeSessions(): Flow<List<JournalSession>> = flowOf(sessions)

            override suspend fun deleteSession(sessionId: String) = Unit

            override fun observeSession(sessionId: String): Flow<JournalSession?> =
                flowOf(sessions.firstOrNull { it.session.id == sessionId })

            override fun observeTeaTasting(teaId: String): Flow<TeaTasting> = flowOf(TeaTasting.EMPTY)
        }
        val sources = JournalSources(
            journal,
            FakeSessions().sessions,
            SavedSheetRequests(clock),
            clock,
            FakeCollection().teas,
            FakeSessions().tasting,
            FakeRecipeOffers(),
            EmptyCoroutineContext,
        )
        compose.setContent {
            UmamiTheme(darkTheme = false, reduceMotion = true) {
                JournalRoute(sources = sources, onOpenSettings = {}, onOpenTimer = {}, teaRequest = request)
            }
        }
        compose.waitForIdle()
        // The list is built off the main thread: wait for it.
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Gyokuro Asahi").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun chip() = compose.onNodeWithContentDescription(GYOKURO_CHIP)

    @Test
    fun `a request shows the list for the tea`() {
        request = JournalTeaRequest(GYOKURO, 1)
        show()

        chip().assertExists()
        compose.onAllNodesWithText("Gyokuro Asahi").onLast().assertIsDisplayed()
        compose.onNodeWithText("Bio Sencha Hisui").assertDoesNotExist()
    }

    @Test
    fun `the same tea asked for again after the chip was cleared shows again`() {
        request = JournalTeaRequest(GYOKURO, 1)
        show()
        chip().performClick()
        compose.waitForIdle()
        chip().assertDoesNotExist()
        compose.onNodeWithText("Bio Sencha Hisui").assertIsDisplayed()

        // Nothing changes while the same request stays: the user's own change stands.
        compose.waitForIdle()
        chip().assertDoesNotExist()

        request = JournalTeaRequest(GYOKURO, 2)
        compose.waitForIdle()

        chip().assertExists()
        compose.onNodeWithText("Bio Sencha Hisui").assertDoesNotExist()
    }

    @Test
    fun `a request clears the search text and the other filters`() {
        show()
        compose.onNodeWithTag(JOURNAL_SEARCH_TAG).performTextInput("hisui")
        compose.waitForIdle()
        compose.onNodeWithText("Gyokuro Asahi").assertDoesNotExist()
        compose.onNodeWithText("Bio Sencha Hisui").assertIsDisplayed()

        request = JournalTeaRequest(GYOKURO, 1)
        compose.waitForIdle()

        // The search for „hisui" would leave no match with the tea filter; the request cleared it.
        compose.onNodeWithText("Keine Sitzung passt zu deiner Suche.").assertDoesNotExist()
        compose.onAllNodesWithText("Gyokuro Asahi").onLast().assertIsDisplayed()
        compose.onNodeWithText("Bio Sencha Hisui").assertDoesNotExist()
    }

    @Test
    fun `without a request the list is plain`() {
        show()

        chip().assertDoesNotExist()
        compose.onNodeWithText("Gyokuro Asahi").assertIsDisplayed()
        compose.onNodeWithText("Bio Sencha Hisui").assertIsDisplayed()
    }

    private companion object {
        const val GYOKURO = "t-gyokuro"
        const val GYOKURO_CHIP = "Filter entfernen: Gyokuro Asahi"
    }
}
