package com.umamit.feature.journal

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeRecipeOffers
import com.umamit.core.testing.FakeSessions
import com.umamit.core.timer.controller.SavedSheetRequests
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

/**
 * Where the journal list is built (JRN-09, #233). The app builds it on `Dispatchers.Default`; a Compose UI test runs
 * the route's effects on an unconfined test dispatcher, which resumed after that hop on the worker thread and sent
 * the snapshot's apply notifications from there, so the view could be touched off its thread
 * (`CalledFromWrongThreadException`, once in a while). The route builds the list in [JournalSources.listContext].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class JournalListThreadTest {
    @get:Rule
    val compose = createComposeRule()

    /** Counts the builds sent to it and runs them at once on the calling thread. */
    private class InPlace : CoroutineDispatcher() {
        val dispatches = AtomicInteger()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatches.incrementAndGet()
            block.run()
        }
    }

    @Test
    fun `the app builds the journal list off the main thread`() {
        val data = FakeSessions()
        val clock = FakeClock()
        val sources = JournalSources(
            data.journal,
            data.sessions,
            SavedSheetRequests(clock),
            clock,
            FakeCollection().teas,
            data.tasting,
            FakeRecipeOffers(),
        )

        assertSame(Dispatchers.Default, sources.listContext)
    }

    @Test
    fun `the route builds its list in the sources' context and applies its state on the test thread`() {
        val data = FakeSessions()
        val clock = FakeClock(wall = JournalSessions.ms(LocalDateTime.of(2026, 10, 22, 12, 0)))
        val requests = SavedSheetRequests(clock)
        FakeBrewController(data.sessions, requests).brew("s1", 60)
        requests.dismiss("s1")
        val collection = FakeCollection()
        runBlocking {
            collection.teas.saveTea(
                Tea(id = "t-neu", name = "Neuer Sencha", category = TeaCategory.GREEN, form = TeaForm.LOOSE_LEAF),
            )
        }
        val inPlace = InPlace()
        val sources = JournalSources(
            data.journal,
            data.sessions,
            requests,
            clock,
            collection.teas,
            data.tasting,
            FakeRecipeOffers(),
            inPlace,
        )
        val testThread = Thread.currentThread()
        val elsewhere = CopyOnWriteArrayList<String>()
        val observer = Snapshot.registerApplyObserver { _, _ ->
            Thread.currentThread().takeIf { it !== testThread }?.let { elsewhere += it.name }
        }
        try {
            compose.setContent {
                UmamiTheme(darkTheme = false, reduceMotion = true) {
                    JournalRoute(
                        sources = sources,
                        onOpenSettings = {},
                        onOpenTimer = {},
                        teaRequest = JournalTeaRequest("t-neu", 1),
                    )
                }
            }
            compose.waitUntil(WAIT_MS) {
                compose.onAllNodesWithContentDescription("Filter entfernen: Neuer Sencha")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.waitForIdle()
        } finally {
            observer.dispose()
        }

        assertTrue("the list was not built in JournalSources.listContext", inPlace.dispatches.get() > 0)
        assertEquals("apply notifications off the test thread", emptyList<String>(), elsewhere.toList())
    }

    private companion object {
        const val WAIT_MS = 5_000L
    }
}
