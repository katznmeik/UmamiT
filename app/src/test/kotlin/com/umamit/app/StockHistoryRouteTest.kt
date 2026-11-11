package com.umamit.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.SessionStart
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.StepKind
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.testing.FakeCatalogRepository
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeSessionPhotos
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
import com.umamit.feature.collection.detail.teaDetailHistoryRowTag
import com.umamit.feature.journal.session.SESSION_BREW_AGAIN_TAG
import com.umamit.feature.journal.session.SessionHeroViewModel
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
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * VORRAT's stock history as a route of the root (SCR-09, SCR-16, SCR-29, D-104; spec #171 seam 3): in Sammlung, the
 * tea detail's brew row opens that session's page in the same stack, and back returns to the detail. The real root,
 * the real detail and session page view models on fakes; an invented tea (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class StockHistoryRouteTest {

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
        collection.stock.addEvent(InventoryEvent("initial", TEA, InventoryEventType.INITIAL, 100.0, 1_000L))
        brewedYesterday()
    }

    /** Yesterday's finished session of the tea; its end deducts 4,5 g as a BREW event of [PAST]. */
    private suspend fun brewedYesterday() {
        val snapshot = SessionSnapshot(
            TEA, "Sencha Morgentau", null, "Sencha", "sencha", TeaCategory.GREEN, null, null,
            BrewMethod.KYUSU, null, null, 4.5, 180, 65,
        )
        val berlin = ZoneId.of("Europe/Berlin")
        val started = ZonedDateTime.now(berlin).minusDays(1).toInstant().toEpochMilli()
        sessions.sessions.startSession(
            SessionStart(PAST, snapshot, listOf(PlannedStep(StepKind.INFUSION, 60, null)), 10, started, berlin.id),
        )
        sessions.sessions.recordStep(PAST, 0, StepRecord(60, 60, 0, 0, started, started + 60_000, false, false))
        sessions.sessions.completeSession(PAST, autoFinished = false)
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
                    collection.teas,
                    archive,
                    sessions.tasting,
                )
            },
            heroViewModel = viewModel {
                SessionHeroViewModel(
                    createSavedStateHandle(),
                    sessions.journal,
                    FakeSessionPhotos(sessions),
                    PhotoFiles(File("no-photos")),
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

    @Test
    fun `the history's brew row opens its session page, back returns to the detail`() {
        show()
        openDetail()

        compose.onNodeWithTag(teaDetailHistoryRowTag("brew-$PAST")).performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(SESSION_BREW_AGAIN_TAG).assertExists()
        compose.onNodeWithTag(TEA_DETAIL_TAG).assertDoesNotExist()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithTag(TEA_DETAIL_TAG).assertIsDisplayed()
    }

    private companion object {
        const val TEA = "morgentau"
        const val PAST = "past"
        const val ROW = "Sencha Morgentau (Liste)"
        const val SETUP = "SETUP"
        const val TIMER = "TIMER"
    }
}
