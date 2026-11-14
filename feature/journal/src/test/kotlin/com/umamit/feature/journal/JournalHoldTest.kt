package com.umamit.feature.journal

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.data.JournalSession
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
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Hold, then show fresh (03 §3.6, A-09) at the route's level: each time the Journal tab comes back (a new composition)
 * the hold starts again, so a changed list ready within [JOURNAL_HOLD_MS] takes the old one's place in place; one
 * that changes later, while the tab stays, cross-fades once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class JournalHoldTest {
    @get:Rule
    val compose = createComposeRule()

    private val clock = FakeClock(wall = JournalSessions.ms(LocalDateTime.of(2026, 10, 22, 12, 0)))
    private val data = FakeSessions()
    private val sources = JournalSources(
        data.journal,
        data.sessions,
        SavedSheetRequests(clock),
        clock,
        FakeCollection().teas,
        data.tasting,
        FakeRecipeOffers(),
        EmptyCoroutineContext,
    )

    private var sessions by mutableStateOf<List<JournalSession>?>(null)
    private var present by mutableStateOf(true)
    private var shown: ShownJournal? = null

    private fun brewed(id: String, hour: Int) = JournalSessions.session(id, LocalDateTime.of(2026, 10, 22, hour, 0))

    private fun listFor(vararg read: JournalSession) = journalList(read.toList(), JournalSessions.TODAY)

    @Test
    fun `a tab that comes back holds again, a change while it stays cross-fades once`() {
        val a = brewed("a", 8)
        val b = brewed("b", 9)
        val c = brewed("c", 10)
        sessions = listOf(a)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            if (present) {
                val now = rememberShownJournal(sources, sessions, JournalSessions.TODAY, "de-DE", emptyMap())
                SideEffect {
                    shown = now
                    sources.lastList = now.list
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        assertShown("the first list", listFor(a), changes = 0)

        // Past the hold, the tab still shown: a new session cross-fades the list once.
        compose.mainClock.advanceTimeBy(JOURNAL_HOLD_MS + FRAME_MS)
        sessions = listOf(a, b)
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        assertShown("a change after the hold", listFor(a, b), changes = 1)

        // The tab leaves; a session is added meanwhile; the tab comes back with its last list and the fresh read.
        present = false
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeByFrame()
        sessions = listOf(a, b, c)
        present = true
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        assertShown("the fresh list within the new hold", listFor(a, b, c), changes = 0)
    }

    private fun assertShown(where: String, list: JournalList, changes: Int) {
        assertWithMessage("$where: the list").that(shown?.list).isEqualTo(list)
        assertWithMessage("$where: cross-fades").that(shown?.changes).isEqualTo(changes)
    }

    private companion object {
        const val FRAME_MS = 16L
    }
}
