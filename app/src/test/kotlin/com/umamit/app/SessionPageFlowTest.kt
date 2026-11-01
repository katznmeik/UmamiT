package com.umamit.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.SessionStart
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.Recipe
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.StepKind
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.model.Vessel
import com.umamit.core.model.VesselType
import com.umamit.core.testing.FakeBrewStarter
import com.umamit.core.testing.FakeCatalogRepository
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.testing.FakeTeaArchive
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.StepRecord
import com.umamit.feature.brew.setup.BrewSetupViewModel
import com.umamit.feature.brew.setup.SETUP_SAVE_TAG
import com.umamit.feature.brew.setup.SETUP_START_TAG
import com.umamit.feature.journal.session.SESSION_BREW_AGAIN_TAG
import com.umamit.feature.journal.session.SESSION_TEA_TAG
import com.umamit.feature.journal.session.SessionPageHost
import com.umamit.feature.journal.session.SessionPageRoute
import com.umamit.feature.journal.session.SessionPageViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime
import com.umamit.feature.brew.setup.BrewSetupRoute as BrewSetupScreenRoute

/**
 * The session page and *So nochmal brühen* as routes of the root (SCR-15, SCR-16, BRW-06, D-100; seam 3): a past
 * journal entry opens its page, whose *So nochmal brühen* opens the setup above the shell with the session as brewed:
 * each step at its set time (a step changed in the Schritte-Sheet and run into overtime prefills its set time), and
 * *Ohne Rezept* for a recipe that is gone. *Brühen starten* there is the only way into the timer. The real root, the
 * real page and setup view models on fakes; an invented tea (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class SessionPageFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val collection = FakeCollection()
    private val settings = FakeSettingsRepository()
    private val sessions = FakeSessions(collection, settings)
    private val archive = FakeTeaArchive(sessions)
    private val starter = FakeBrewStarter()
    private val alertKey = MutableStateFlow<String?>(null)

    @Before
    fun seed(): Unit = runBlocking {
        collection.teas.saveTea(Tea(TEA, "Sencha Morgentau", category = TeaCategory.GREEN, form = TeaForm.LOOSE_LEAF))
        collection.vessels.saveVessel(Vessel("kyusu", "Kyusu 250 ml", VesselType.KYUSU, 300, 250))
        collection.recipes.saveRecipe(
            Recipe(
                id = "standard",
                teaId = TEA,
                name = "Standard",
                method = BrewMethod.KYUSU,
                leafGrams = 5.0,
                waterMl = 250,
                baseTempC = 70,
                steps = listOf(RecipeStep(StepKind.INFUSION, 60)),
                vesselId = "kyusu",
                isDefault = true,
            ),
        )
        brewedYesterday()
    }

    /**
     * Yesterday's session of the recipe „Kräftig", deleted since: infusion 1 was set to 1:15 in the Schritte-Sheet and
     * poured after 1:35 (overtime); infusion 2 was never started (kept from v0.4.0, Q-19).
     */
    private suspend fun brewedYesterday() {
        val snapshot = SessionSnapshot(
            teaId = TEA,
            teaName = "Sencha Morgentau",
            teaSecondaryName = null,
            teaType = "Sencha",
            styleKey = "sencha",
            category = TeaCategory.GREEN,
            profileId = "deleted-recipe",
            profileName = "Kräftig",
            method = BrewMethod.KYUSU,
            vesselId = "kyusu",
            vesselName = "Kyusu 250 ml",
            leafGrams = 4.5,
            waterMl = 180,
            baseTempC = 65,
        )
        val berlin = ZoneId.of("Europe/Berlin")
        val started = ZonedDateTime.now(berlin).minusDays(1).toInstant().toEpochMilli()
        val planned = listOf(PlannedStep(StepKind.INFUSION, 60, null), PlannedStep(StepKind.INFUSION, 30, 75))
        sessions.sessions.startSession(SessionStart(PAST, snapshot, planned, 10, started, berlin.id))
        sessions.sessions.replacePendingSteps(
            PAST,
            0,
            listOf(PlannedStep(StepKind.INFUSION, 75, null), PlannedStep(StepKind.INFUSION, 30, 75)),
        )
        sessions.sessions.recordStep(PAST, 0, StepRecord(75, 95, 0, 20, started, started + 95_000, false, false))
        sessions.sessions.completeSession(PAST, autoFinished = false)
    }

    private fun show() {
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                UmamitRoot(
                    activeBrewSource = NoActiveBrewSource,
                    journalSources = testJournalSources(sessions, collection.teas),
                    alertKey = alertKey,
                    journalScreens = JournalStackScreens(session = { host -> Page(host) }),
                    collectionScreens = CollectionStackScreens(tea = { Text(TEA_DETAIL) }),
                    setupScreen = { host -> Setup(host) },
                    settings = testSettingsScreens(collection),
                    timerScreen = { _, _, _, _ -> Text(TIMER) },
                )
            }
        }
        compose.waitForIdle()
    }

    @Composable
    private fun Page(host: SessionPageHost) {
        SessionPageRoute(
            host = host,
            viewModel = viewModel {
                SessionPageViewModel(createSavedStateHandle(), sessions.journal, collection.teas, archive)
            },
        )
    }

    @Composable
    private fun Setup(host: SetupHost) {
        BrewSetupScreenRoute(
            onBack = host.onBack,
            onOpenTimer = host.onOpenTimer,
            viewModel = viewModel {
                BrewSetupViewModel(
                    createSavedStateHandle(),
                    collection.teas,
                    collection.recipes,
                    collection.vessels,
                    collection.stock,
                    sessions.sessions,
                    settings,
                    starter,
                    FakeCatalogRepository(),
                )
            },
        )
    }

    private fun openSessionPage() {
        compose.onNodeWithTag(tabTag(Tab.Journal)).performClick()
        compose.waitForIdle()
        compose.onNode(hasText("Sencha Morgentau") and hasClickAction()).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Sitzung").assertIsDisplayed()
    }

    private fun brewAgain() {
        compose.onNodeWithTag(SESSION_BREW_AGAIN_TAG).performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Test
    fun `an entry opens its page, whose So nochmal bruehen opens the setup with set times and Ohne Rezept`() {
        show()
        openSessionPage()
        compose.onNodeWithText("nicht gebrüht", useUnmergedTree = true).assertExists()

        brewAgain()

        // The setup, not the timer: nothing has started yet (BRW-01).
        compose.onNodeWithTag(SETUP_START_TAG).assertExists()
        assertThat(starter.starts).isEmpty()
        compose.onNodeWithText("Ohne Rezept").assertIsSelected()
        compose.onNodeWithTag(SETUP_SAVE_TAG).performScrollTo().assertIsDisplayed()

        compose.onNodeWithTag(SETUP_START_TAG).performScrollTo().performClick()
        compose.waitForIdle()

        val start = starter.starts.single()
        // The set time 1:15, never the measured 1:35; the never-started step is brewed again.
        assertThat(start.steps).containsExactly(
            PlannedStep(StepKind.INFUSION, 75, 65),
            PlannedStep(StepKind.INFUSION, 30, 75),
        ).inOrder()
        assertThat(start.snapshot.profileId).isNull()
        assertThat(start.snapshot.waterMl).isEqualTo(180)
        compose.onNodeWithText(TIMER).assertIsDisplayed()
    }

    @Test
    fun `back from the setup returns to the session page`() {
        show()
        openSessionPage()
        brewAgain()

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()

        compose.onNodeWithTag(SESSION_BREW_AGAIN_TAG).assertExists()
    }

    @Test
    fun `the tea's name opens its detail in the Journal stack`() {
        show()
        openSessionPage()

        compose.onNodeWithTag(SESSION_TEA_TAG).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(TEA_DETAIL).assertIsDisplayed()
    }

    @Test
    fun `during an alert So nochmal bruehen opens the alerting timer, never the setup (rule 8)`() {
        show()
        openSessionPage()
        alertKey.value = "brew-1"
        compose.waitForIdle()

        brewAgain()

        compose.onNodeWithTag(SETUP_START_TAG).assertDoesNotExist()
        compose.onNodeWithText(TIMER).assertIsDisplayed()
        assertThat(starter.starts).isEmpty()
    }

    private companion object {
        const val TEA = "morgentau"
        const val PAST = "past"
        const val TIMER = "TIMER"
        const val TEA_DETAIL = "TEA_DETAIL"
    }
}
