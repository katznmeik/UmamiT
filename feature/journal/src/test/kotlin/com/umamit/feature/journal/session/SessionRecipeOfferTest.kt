package com.umamit.feature.journal.session

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.SessionStart
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.StepKind
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeRecipeOffers
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeTeaArchive
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.StepRecord
import com.umamit.feature.journal.JournalSessions
import com.umamit.feature.journal.RECIPE_OFFER_TAG
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/**
 * *Zeiten ins Rezept übernehmen* on the session page (SCR-16, PRF-07 AC3, D-112, #228) with its real view model on
 * the fakes: the text button under the step table only while the offer applies, „Rezept aktualisiert." after the tap,
 * and gone from the page opened again. An invented tea (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class SessionRecipeOfferTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val collection = FakeCollection()
    private val sessions = FakeSessions(collection)
    private val archive = FakeTeaArchive(sessions)
    private val offers = FakeRecipeOffers()

    /** The page's view model now; a new one is the page opened again. Null: the page is not composed. */
    private val page = mutableStateOf<SessionPageViewModel?>(null)

    @Before
    fun seed() = runBlocking {
        collection.teas.saveTea(Tea(TEA, "Sencha Morgentau", category = TeaCategory.GREEN, form = TeaForm.LOOSE_LEAF))
        val snapshot = SessionSnapshot(
            teaId = TEA, teaName = "Sencha Morgentau", teaSecondaryName = null, teaType = "Sencha",
            styleKey = "sencha", category = TeaCategory.GREEN, profileId = "kraeftig", profileName = "Kräftig",
            method = BrewMethod.KYUSU, vesselId = null, vesselName = "Kyusu 250 ml", leafGrams = 4.5, waterMl = 180,
            baseTempC = 65,
        )
        val steps = listOf(PlannedStep(StepKind.INFUSION, 60, null), PlannedStep(StepKind.INFUSION, 30, null))
        sessions.sessions.startSession(SessionStart(PAST, snapshot, steps, 10, STARTED, "Europe/Berlin"))
        sessions.sessions.recordStep(PAST, 0, StepRecord(60, 48, 0, 0, STARTED, STARTED + 48_000, false, false))
        sessions.sessions.recordStep(PAST, 1, StepRecord(30, 30, 0, 0, STARTED, STARTED + 90_000, false, false))
        sessions.sessions.completeSession(PAST, autoFinished = false)
    }

    private fun viewModel() = SessionPageViewModel(
        SavedStateHandle(mapOf("sessionId" to PAST)),
        sessions.journal,
        offers,
        collection.teas,
        archive,
        sessions.tasting,
    )

    private fun show() {
        page.value = viewModel()
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                page.value?.let { viewModel ->
                    SessionPageRoute(
                        host = SessionPageHost(onBack = {
                        }, onOpenTea = {}, onBrewAgain = {}),
                        viewModel = viewModel,
                        heroViewModel = remember(viewModel) { heroViewModel(sessions, PAST) },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    /** Back, and the page opened again from the journal: a new view model. */
    private fun reopen() {
        compose.runOnUiThread { page.value = null }
        compose.waitForIdle()
        compose.runOnUiThread { page.value = viewModel() }
        compose.waitForIdle()
    }

    private fun offerButton() = compose.onNodeWithTag(RECIPE_OFFER_TAG)

    @Test
    fun `without an offer the page shows no button`() {
        show()

        compose.onNode(hasContentDescription("Aufgüsse")).assertIsDisplayed()
        offerButton().assertDoesNotExist()
        compose.onNodeWithText("Zeiten ins Rezept übernehmen").assertDoesNotExist()
    }

    @Test
    fun `while the offer applies the button sits under the step table, above GESCHMACK`() {
        offers.proposals[PAST] = PROPOSAL
        show()

        // Unclipped: with the hero above, GESCHMACK can sit below the viewport once the button is scrolled to.
        val button = offerButton().performScrollTo().assertIsDisplayed().getUnclippedBoundsInRoot()
        val steps = compose.onNode(hasContentDescription("Aufgüsse")).getUnclippedBoundsInRoot()
        val taste = compose.onNode(hasContentDescription("Geschmack")).getUnclippedBoundsInRoot()
        assertThat(button.top).isGreaterThan(steps.bottom)
        assertThat(button.bottom).isAtMost(taste.top)
    }

    @Test
    fun `a tap reads Rezept aktualisiert and the reopened page has no line`() {
        offers.proposals[PAST] = PROPOSAL
        show()

        offerButton().performScrollTo().performClick()
        compose.waitForIdle()

        assertThat(offers.accepted).containsExactly(PAST)
        offerButton().assertDoesNotExist()
        compose.onNodeWithText("Rezept aktualisiert.").assertIsDisplayed()

        reopen()

        compose.onNode(hasContentDescription("Aufgüsse")).assertExists()
        offerButton().assertDoesNotExist()
        compose.onNodeWithText("Rezept aktualisiert.").assertDoesNotExist()
    }

    @Test
    fun `a refused accept removes the line`() {
        offers.proposals[PAST] = PROPOSAL
        show()
        // The recipe changed elsewhere meanwhile: the offer no longer applies.
        offers.proposals.clear()

        offerButton().performScrollTo().performClick()
        compose.waitForIdle()

        offerButton().assertDoesNotExist()
        compose.onNodeWithText("Rezept aktualisiert.").assertDoesNotExist()
        assertThat(offers.accepted).isEmpty()
    }

    @Test
    fun `a second tap writes nothing more`() {
        offers.proposals[PAST] = PROPOSAL
        show()
        val viewModel = checkNotNull(page.value)

        offerButton().performScrollTo().performClick()
        compose.waitForIdle()
        offers.proposals[PAST] = PROPOSAL
        compose.runOnUiThread { viewModel.recipeOffer.accept() }
        compose.waitForIdle()

        assertThat(offers.accepted).containsExactly(PAST)
    }

    @Test
    fun `a recipe changed on another page drops the line when the page resumes`() {
        offers.proposals[PAST] = PROPOSAL
        show()
        offerButton().performScrollTo().assertIsDisplayed()

        // Tee, then Rezept: the recipe is edited there, and back on the page from the back stack.
        offers.proposals.clear()
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()

        offerButton().assertDoesNotExist()
        assertThat(offers.accepted).isEmpty()
    }

    @Test
    fun `restoring the archived tea on the page brings the line`() {
        runBlocking { archive.archive(TEA) }
        show()
        offerButton().assertDoesNotExist()

        offers.proposals[PAST] = PROPOSAL
        val viewModel = checkNotNull(page.value)
        compose.runOnUiThread { viewModel.restore() }
        compose.waitForIdle()

        offerButton().performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a failed read shows the page without the line`() {
        offers.failure = IllegalStateException("database closed")
        show()

        compose.onNode(hasContentDescription("Aufgüsse")).assertIsDisplayed()
        offerButton().assertDoesNotExist()
    }

    @Test
    fun `a failed accept removes the line and never reads Rezept aktualisiert`() {
        offers.proposals[PAST] = PROPOSAL
        show()
        offers.failure = IllegalStateException("database closed")

        offerButton().performScrollTo().performClick()
        compose.waitForIdle()

        offerButton().assertDoesNotExist()
        compose.onNodeWithText("Rezept aktualisiert.").assertDoesNotExist()
        compose.onNode(hasContentDescription("Aufgüsse")).assertExists()
    }

    private companion object {
        const val TEA = "morgentau"
        const val PAST = "past"
        val STARTED = JournalSessions.ms(LocalDateTime.of(2026, 10, 22, 9, 12))
        val PROPOSAL = listOf(RecipeStep(StepKind.INFUSION, 50), RecipeStep(StepKind.INFUSION, 30))
    }
}
