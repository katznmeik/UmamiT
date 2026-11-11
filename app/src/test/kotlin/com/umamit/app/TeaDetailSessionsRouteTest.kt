package com.umamit.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.SessionStart
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.StepKind
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.testing.FakeCatalogRepository
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeRecipeOffers
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.testing.FakeTeaArchive
import com.umamit.core.testing.FakeTeaPhotos
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.StepRecord
import com.umamit.feature.collection.detail.TEA_DETAIL_TAG
import com.umamit.feature.collection.detail.TeaDetailHost
import com.umamit.feature.collection.detail.TeaDetailRoute
import com.umamit.feature.collection.detail.TeaDetailViewModel
import com.umamit.feature.collection.detail.teaDetailSessionTag
import com.umamit.feature.journal.session.SESSION_BREW_AGAIN_TAG
import com.umamit.feature.journal.session.SessionPageHost
import com.umamit.feature.journal.session.SessionPageRoute
import com.umamit.feature.journal.session.SessionPageViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The tea detail's LETZTE SITZUNGEN as routes of the root (TEA-13, SCR-09, SCR-15, SCR-16, D-112, #232): a row opens
 * the session's page in the Sammlung stack, back returns to the detail; *Alle anzeigen* switches to the Journal tab
 * at its root, filtered to the tea, and does so again for the same tea after the chip was cleared; the stack of
 * Sammlung is left as it was. The real root, detail, session page and journal on fakes; an invented tea (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TeaDetailSessionsRouteTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val folder = TemporaryFolder()

    private val collection = FakeCollection()
    private val settings = FakeSettingsRepository()
    private val sessions = FakeSessions(collection, settings)
    private val archive = FakeTeaArchive(sessions)
    private val alertKey = MutableStateFlow<String?>(null)

    @Before
    fun seed(): Unit = runBlocking {
        collection.teas.saveTea(Tea(TEA, "Sencha Morgentau", category = TeaCategory.GREEN, form = TeaForm.LOOSE_LEAF))
        collection.teas.saveTea(Tea(OTHER, "Gyokuro Abendrot", category = TeaCategory.GREEN, form = TeaForm.LOOSE_LEAF))
        // Four sessions of the tea (the detail lists three, so *Alle anzeigen* shows) and one of another tea.
        listOf("s1", "s2", "s3", "s4").forEachIndexed { index, id -> brewed(id, TEA, "Sencha Morgentau", 5 - index) }
        brewed("other", OTHER, "Gyokuro Abendrot", 1)
    }

    private suspend fun brewed(id: String, teaId: String, name: String, daysAgo: Int) {
        val snapshot = SessionSnapshot(
            teaId, name, null, "Sencha", "sencha", TeaCategory.GREEN, null, null,
            BrewMethod.KYUSU, null, null, 4.5, 180, 65,
        )
        val berlin = ZoneId.of("Europe/Berlin")
        val started = ZonedDateTime.now(berlin).minusDays(daysAgo.toLong()).toInstant().toEpochMilli()
        sessions.sessions.startSession(
            SessionStart(id, snapshot, listOf(PlannedStep(StepKind.INFUSION, 60, null)), 10, started, berlin.id),
        )
        sessions.sessions.recordStep(id, 0, StepRecord(60, 60, 0, 0, started, started + 60_000, false, false))
        sessions.sessions.completeSession(id, autoFinished = false)
    }

    private fun show() {
        val photos = PhotoFiles(folder.newFolder("photos"))
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                UmamitRoot(
                    activeBrewSource = NoActiveBrewSource,
                    journalSources = testJournalSources(sessions, collection.teas),
                    alertKey = alertKey,
                    journalScreens = JournalStackScreens(session = { host -> Page(host) }),
                    collectionScreens = CollectionStackScreens(
                        myTeas = { tab -> MyTeas(tab) },
                        tea = { host -> Detail(host, photos) },
                    ),
                    setupScreen = { _ -> Text(SETUP) },
                    timerScreen = { _, _, _, _ -> Text(TIMER) },
                )
            }
        }
        compose.waitForIdle()
    }

    /** A stand-in for the *Meine Tees* list: one row that opens the tea. */
    @Composable
    private fun MyTeas(tab: MyTeasTab) {
        Box(tab.modifier) {
            TextButton(onClick = { tab.openTea(TEA) }) { Text(ROW) }
        }
    }

    @Composable
    private fun Detail(host: TeaDetailHost, photos: PhotoFiles) {
        TeaDetailRoute(
            host = host,
            viewModel = viewModel {
                TeaDetailViewModel(
                    createSavedStateHandle(),
                    collection.teas,
                    collection.recipes,
                    collection.vessels,
                    collection.stock,
                    sessions.journal,
                    settings,
                    FakeCatalogRepository(),
                    archive,
                    photos,
                    FakeTeaPhotos(collection, photos),
                )
            },
        )
    }

    @Composable
    private fun Page(host: SessionPageHost) {
        SessionPageRoute(
            host = host,
            viewModel = viewModel {
                SessionPageViewModel(
                    createSavedStateHandle(),
                    sessions.journal,
                    FakeRecipeOffers(),
                    collection.teas,
                    archive,
                    sessions.tasting,
                )
            },
        )
    }

    private fun openDetail() {
        compose.onNodeWithTag(tabTag(Tab.Collection)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(ROW).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(TEA_DETAIL_TAG).assertIsDisplayed()
    }

    private fun showAll() {
        // By its action: scrolled to the viewport's lower edge, the pencil FAB covers the button's centre.
        compose.onNodeWithContentDescription("Alle anzeigen: Letzte Sitzungen").performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun `a session row opens its page in the same stack, back returns to the detail`() {
        show()
        openDetail()

        compose.onNodeWithTag(teaDetailSessionTag("s4")).performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(SESSION_BREW_AGAIN_TAG).assertExists()
        compose.onNodeWithTag(TEA_DETAIL_TAG).assertDoesNotExist()
        back()
        compose.onNodeWithTag(TEA_DETAIL_TAG).assertIsDisplayed()
    }

    @Test
    fun `Alle anzeigen switches to the journal filtered to the tea`() {
        show()
        openDetail()

        showAll()

        compose.onNodeWithTag(tabTag(Tab.Journal)).assertIsSelected()
        compose.onNodeWithContentDescription("Filter entfernen: Sencha Morgentau").assertExists()
        compose.onNodeWithText("Gyokuro Abendrot").assertDoesNotExist()
    }

    @Test
    fun `the Sammlung stack keeps the detail and the journal has one list`() {
        show()
        openDetail()
        showAll()

        // Back from the journal's root leaves the tab, never to a second list.
        compose.onNodeWithTag(tabTag(Tab.Collection)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(TEA_DETAIL_TAG).assertIsDisplayed()
    }

    @Test
    fun `Alle anzeigen works again for the same tea after the chip was cleared`() {
        show()
        openDetail()
        showAll()
        compose.onNodeWithContentDescription("Filter entfernen: Sencha Morgentau").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Gyokuro Abendrot").assertIsDisplayed()

        compose.onNodeWithTag(tabTag(Tab.Collection)).performClick()
        compose.waitForIdle()
        showAll()

        compose.onNodeWithTag(tabTag(Tab.Journal)).assertIsSelected()
        compose.onNodeWithContentDescription("Filter entfernen: Sencha Morgentau").assertExists()
        compose.onNodeWithText("Gyokuro Abendrot").assertDoesNotExist()
    }

    @Test
    fun `a session page open in the journal is popped, not stacked on`() {
        show()
        // The journal tab has a session page open when the request arrives.
        compose.onNodeWithTag(tabTag(Tab.Journal)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Gyokuro Abendrot").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(SESSION_BREW_AGAIN_TAG).assertExists()
        openDetail()

        showAll()

        compose.onNodeWithTag(tabTag(Tab.Journal)).assertIsSelected()
        compose.onNodeWithContentDescription("Filter entfernen: Sencha Morgentau").assertExists()
        compose.onNodeWithTag(SESSION_BREW_AGAIN_TAG).assertDoesNotExist()
    }

    private companion object {
        const val TEA = "morgentau"
        const val OTHER = "abendrot"
        const val ROW = "Sencha Morgentau (Liste)"
        const val SETUP = "SETUP"
        const val TIMER = "TIMER"
    }
}
