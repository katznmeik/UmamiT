package com.umamit.feature.journal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.SessionTasting
import com.umamit.core.data.TastingWrite
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.RecipeCheckReason
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.StepKind
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeRecipeOffers
import com.umamit.core.testing.FakeSessions
import com.umamit.core.timer.controller.SavedSheetRequests
import com.umamit.core.ui.ratingStarTag
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

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
    private val opened = mutableListOf<String>()

    /** The tasting writes the sheet gets; a test swaps in a failing or a gated one. */
    private var tasting: SessionTasting = data.tasting

    /** The recipe offers (PRF-07): a test puts a session's proposal in when the offer applies. */
    private val offers = FakeRecipeOffers()

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
                        tasting = tasting,
                        offers = offers,
                        onOpenNotes = { opened += it },
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

    private fun saveTea(id: String, reason: RecipeCheckReason?, openedDate: LocalDate? = null) = runBlocking {
        collection.teas.saveTea(
            Tea(
                id,
                "Tee $id",
                category = TeaCategory.GREEN,
                form = TeaForm.LOOSE_LEAF,
                openedDate = openedDate,
                recipeCheckReason = reason,
            ),
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
    fun `the brew that opens the package says so, and Rueckgaengig makes the tea unopened again`() {
        saveTea("new", null)
        show()

        controller.brew("s1", 60, teaId = "new")
        assertThat(runBlocking { collection.teas.getTea("new") }?.openedDate).isNotNull()
        compose.onNodeWithText("Packung als geöffnet markiert").assertIsDisplayed()
        compose.onNodeWithText("Rückgängig").assertIsDisplayed().performClick()
        compose.waitForIdle()

        assertThat(runBlocking { collection.teas.getTea("new") }?.openedDate).isNull()
        compose.onNodeWithText("Rückgängig").assertDoesNotExist()
        compose.onNodeWithText("Öffnungsdatum ist zurückgesetzt.").assertIsDisplayed()
    }

    @Test
    fun `a brew that leaves the opened date shows no line`() {
        saveTea("open", null, openedDate = LocalDate.of(2026, 10, 1))
        show()

        controller.brew("s1", 60, teaId = "open")

        compose.onNodeWithText("Gespeichert im Journal").assertIsDisplayed()
        compose.onNodeWithText("Packung als geöffnet markiert").assertDoesNotExist()
        compose.onNodeWithText("Rückgängig").assertDoesNotExist()
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

    private fun star(n: Int) = compose.onNodeWithTag(ratingStarTag(n), useUnmergedTree = true)

    private fun rating(id: String) = runBlocking { data.sessions.getSession(id) }?.rating

    @Test
    fun `a star tap rates the session at once and restarts the 10 s`() {
        show()
        compose.mainClock.autoAdvance = false
        controller.brew("s1", 60)
        compose.mainClock.advanceTimeBy(1_000)

        // 8 s in, a tap: the 10 s start again.
        compose.mainClock.advanceTimeBy(7_000)
        star(4).performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertThat(rating("s1")).isEqualTo(4.0)

        compose.mainClock.advanceTimeBy(8_000)
        compose.onNodeWithText("Gespeichert im Journal").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(3_000)
        compose.onNodeWithText("Gespeichert im Journal").assertDoesNotExist()
        assertThat(rating("s1")).isEqualTo(4.0)
    }

    @Test
    fun `a second and a third tap on the same star take half off and clear`() {
        show()
        controller.brew("s1", 60)

        star(3).performClick()
        compose.waitForIdle()
        assertThat(rating("s1")).isEqualTo(3.0)
        star(3).performClick()
        compose.waitForIdle()
        assertThat(rating("s1")).isEqualTo(2.5)
        star(3).performClick()
        compose.waitForIdle()
        assertThat(rating("s1")).isNull()
    }

    @Test
    fun `the stars stay one adjustable node of their own for TalkBack`() {
        show()
        controller.brew("s1", 60)

        // Exactly one node adjusts, and it is not the sheet itself.
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
            .assert(hasContentDescription("Bewertung"))
    }

    @Test
    fun `ignoring the sheet leaves the session unrated and it dismisses after 10 s`() {
        show()
        compose.mainClock.autoAdvance = false
        controller.brew("s1", 60)
        compose.mainClock.advanceTimeBy(11_000)

        compose.onNodeWithText("Gespeichert im Journal").assertDoesNotExist()
        assertThat(rating("s1")).isNull()
        assertThat(opened).isEmpty()
    }

    @Test
    fun `an alert removes the sheet and the started rating write still lands`() {
        show()
        compose.mainClock.autoAdvance = false
        controller.brew("s1", 60)
        compose.mainClock.advanceTimeBy(1_000)

        star(5).performClick()
        alerting = true
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeByFrame()

        compose.onNodeWithText("Gespeichert im Journal").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertThat(rating("s1")).isEqualTo(5.0)
    }

    /** Delegates to [inner]; [gate] holds back the first write, [fail] makes every write fail. */
    private class ScriptedTasting(
        private val inner: SessionTasting,
        private val gate: CompletableDeferred<Unit>? = null,
        private val fail: (() -> TastingWrite)? = null,
    ) : SessionTasting by inner {
        val ratings = mutableListOf<Double?>()
        private var first = true

        override suspend fun setRating(sessionId: String, rating: Double?): TastingWrite {
            ratings += rating
            if (first) {
                first = false
                gate?.await()
            }
            return fail?.invoke() ?: inner.setRating(sessionId, rating)
        }
    }

    @Test
    fun `a second tap queued behind a slow write lands after it, also after an alert`() {
        val gate = CompletableDeferred<Unit>()
        tasting = ScriptedTasting(data.tasting, gate)
        show()
        compose.mainClock.autoAdvance = false
        controller.brew("s1", 60)
        compose.mainClock.advanceTimeBy(1_000)

        star(2).performClick()
        star(5).performClick()
        alerting = true
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Gespeichert im Journal").assertDoesNotExist()

        gate.complete(Unit)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertThat(rating("s1")).isEqualTo(5.0)
        assertThat((tasting as ScriptedTasting).ratings).containsExactly(2.0, 5.0).inOrder()
    }

    @Test
    fun `a rating write that is refused leaves the sheet usable (D-090)`() {
        tasting = ScriptedTasting(data.tasting, fail = { TastingWrite.NOT_COMPLETED })
        show()
        controller.brew("s1", 60)

        star(3).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Gespeichert im Journal").assertIsDisplayed()
        compose.onNodeWithText("Notizen hinzufügen").assertIsDisplayed()
        assertThat(rating("s1")).isNull()
        // Another tap is still written.
        star(4).performClick()
        compose.waitForIdle()
        assertThat((tasting as ScriptedTasting).ratings).containsExactly(3.0, 4.0).inOrder()
    }

    @Test
    fun `a rating write that throws is logged, never crashes, and leaves the sheet usable (D-090)`() {
        tasting = ScriptedTasting(data.tasting, fail = { error("disk full") })
        show()
        controller.brew("s1", 60)

        star(3).performClick()
        compose.waitForIdle()
        star(4).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Gespeichert im Journal").assertIsDisplayed()
        assertThat((tasting as ScriptedTasting).ratings).containsExactly(3.0, 4.0).inOrder()
        assertThat(rating("s1")).isNull()
    }

    @Test
    fun `Notizen hinzufuegen closes the sheet and opens the session page`() {
        show()
        controller.brew("s1", 60)

        compose.onNodeWithText("Notizen hinzufügen").assertIsDisplayed().performClick()
        compose.waitForIdle()

        assertThat(opened).containsExactly("s1")
        compose.onNodeWithText("Gespeichert im Journal").assertDoesNotExist()
        assertThat(requests.pending.value).isNull()
        assertThat(rating("s1")).isNull()
    }

    @Test
    fun `the stars come before Notizen hinzufuegen, before the opened line and Rezept passt`() {
        saveTea("flagged", RecipeCheckReason.FROM_TEXT)
        show()

        controller.brew("s1", 60, teaId = "flagged")

        val stars = star(1).fetchSemanticsNode().boundsInRoot.top
        val notes = compose.onNodeWithText("Notizen hinzufügen").fetchSemanticsNode().boundsInRoot.top
        val ok = compose.onNodeWithText("Rezept passt").fetchSemanticsNode().boundsInRoot.top
        assertThat(stars).isLessThan(notes)
        assertThat(notes).isLessThan(ok)
    }

    @Test
    fun `without an offer the sheet shows no Zeiten ins Rezept uebernehmen`() {
        show()

        controller.brew("s1", 60)

        compose.onNodeWithText("Gespeichert im Journal").assertIsDisplayed()
        compose.onNodeWithText("Zeiten ins Rezept übernehmen").assertDoesNotExist()
    }

    @Test
    fun `when the offer applies, Zeiten ins Rezept uebernehmen sits below Notizen and above Rezept passt`() {
        saveTea("flagged", RecipeCheckReason.FROM_TEXT)
        offers.proposals["s1"] = PROPOSAL
        show()

        controller.brew("s1", 60, teaId = "flagged")

        val notes = compose.onNodeWithText("Notizen hinzufügen").fetchSemanticsNode().boundsInRoot.top
        val offer = compose.onNodeWithText("Zeiten ins Rezept übernehmen").fetchSemanticsNode().boundsInRoot.top
        val ok = compose.onNodeWithText("Rezept passt").fetchSemanticsNode().boundsInRoot.top
        assertThat(notes).isLessThan(offer)
        assertThat(offer).isLessThan(ok)
    }

    @Test
    fun `a tap accepts the offer and the line reads Rezept aktualisiert in the same height`() {
        offers.proposals["s1"] = PROPOSAL
        show()
        controller.brew("s1", 60)
        val line = compose.onNodeWithText("Zeiten ins Rezept übernehmen").fetchSemanticsNode().boundsInRoot

        compose.onNodeWithText("Zeiten ins Rezept übernehmen").performClick()
        compose.waitForIdle()

        assertThat(offers.accepted).containsExactly("s1")
        compose.onNodeWithText("Zeiten ins Rezept übernehmen").assertDoesNotExist()
        // The text's own node: the sheet merges its plain lines into one.
        val done = compose.onNodeWithText("Rezept aktualisiert.", useUnmergedTree = true)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        assertThat(done.top).isAtLeast(line.top)
        assertThat(done.bottom).isAtMost(line.bottom)
        // Nothing else happens: the sheet stays and asks nothing.
        compose.onNodeWithText("Gespeichert im Journal").assertIsDisplayed()
    }

    @Test
    fun `a refused accept removes the line`() {
        offers.proposals["s1"] = PROPOSAL
        show()
        controller.brew("s1", 60)
        compose.onNodeWithText("Zeiten ins Rezept übernehmen").assertIsDisplayed()
        // The offer stops applying before the tap (the recipe changed meanwhile).
        offers.proposals.clear()

        compose.onNodeWithText("Zeiten ins Rezept übernehmen").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Zeiten ins Rezept übernehmen").assertDoesNotExist()
        compose.onNodeWithText("Rezept aktualisiert.").assertDoesNotExist()
        assertThat(offers.accepted).isEmpty()
    }

    @Test
    fun `a failing offer read shows the sheet without the line (D-090)`() {
        offers.proposals["s1"] = PROPOSAL
        offers.failure = IllegalStateException("disk full")
        show()

        controller.brew("s1", 60)

        compose.onNodeWithText("Gespeichert im Journal").assertIsDisplayed()
        compose.onNodeWithText("Zeiten ins Rezept übernehmen").assertDoesNotExist()
    }

    @Test
    fun `an alert removes the sheet and the started accept still lands`() {
        offers.proposals["s1"] = PROPOSAL
        show()
        compose.mainClock.autoAdvance = false
        controller.brew("s1", 60)
        compose.mainClock.advanceTimeBy(1_000)

        compose.onNodeWithText("Zeiten ins Rezept übernehmen").performClick()
        alerting = true
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeByFrame()

        compose.onNodeWithText("Gespeichert im Journal").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertThat(offers.accepted).containsExactly("s1")
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

    private companion object {
        val PROPOSAL = listOf(RecipeStep(StepKind.INFUSION, 50))
    }
}
