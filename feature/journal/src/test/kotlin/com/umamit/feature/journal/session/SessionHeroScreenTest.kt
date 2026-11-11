package com.umamit.feature.journal.session

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.SessionStart
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.StepKind
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakePhotoPicker
import com.umamit.core.testing.FakeSessionPhotos
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeTeaArchive
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.StepRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.time.LocalDateTime

/**
 * The session page's hero (SCR-16, JRN-07, D-112, #229): its three states (the session's photos, the tea's cover,
 * the type leaf), *+ Foto* greyed at 6, *+ Foto* and *Löschen* through the route on the fakes of core:testing, and the
 * photo's menu and question giving way to an alert (rule 8). Invented tea, placeholder images only (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class SessionHeroScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val folder = TemporaryFolder()

    private val collection = FakeCollection()
    private val sessions = FakeSessions(collection)
    private val alert = mutableStateOf<String?>(null)
    private val deleted = mutableListOf<String>()

    @Before
    fun seed() = runBlocking {
        collection.teas.saveTea(Tea(TEA, "Sencha Morgentau", category = TeaCategory.GREEN, form = TeaForm.LOOSE_LEAF))
        val snapshot = SessionSnapshot(
            teaId = TEA,
            teaName = "Sencha Morgentau",
            teaSecondaryName = null,
            teaType = "Sencha",
            styleKey = "sencha",
            category = TeaCategory.GREEN,
            profileId = null,
            profileName = "Kräftig",
            method = BrewMethod.KYUSU,
            vesselId = null,
            vesselName = "Kyusu 250 ml",
            leafGrams = 4.5,
            waterMl = 180,
            baseTempC = 65,
        )
        sessions.sessions.startSession(
            SessionStart(PAST, snapshot, listOf(PlannedStep(StepKind.INFUSION, 60, null)), 10, STARTED, "UTC"),
        )
        sessions.sessions.recordStep(PAST, 0, StepRecord(60, 62, 0, 2, STARTED, STARTED + 62_000, false, false))
        sessions.sessions.completeSession(PAST, autoFinished = false)
    }

    private fun showScreen(hero: SessionHero) {
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                SessionPageScreen(
                    state = SessionPageUiState.Ready(page),
                    alertKey = alert.value,
                    hero = hero,
                    heroActions = SessionHeroActions(deletePhoto = { deleted += it }),
                    photo = { PLACEHOLDER },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun showRoute(picker: FakePhotoPicker = FakePhotoPicker(), configure: (FakeSessionPhotos) -> Unit = {}) =
        FakeSessionPhotos(sessions, files).also(configure).also { photos ->
            compose.setContent {
                UmamiTheme(reduceMotion = true) {
                    SessionPageRoute(
                        host = SessionPageHost(onBack = {}, onOpenTea = {}, onBrewAgain = {}),
                        viewModel = SessionPageViewModel(
                            SavedStateHandle(mapOf("sessionId" to PAST)),
                            sessions.journal,
                            collection.teas,
                            FakeTeaArchive(sessions),
                            sessions.tasting,
                        ),
                        heroViewModel = heroViewModel(sessions, PAST, photos, files),
                        photoPicker = picker,
                    )
                }
            }
            compose.waitForIdle()
        }

    private val files by lazy { PhotoFiles(folder.newFolder("photos")) }

    private fun photos(count: Int) = List(count) { SessionHeroPhoto("p$it", "/photos/p$it.jpg") }

    private fun openPhotoMenu() {
        compose.onNodeWithContentDescription("Foto-Optionen").performClick()
        compose.waitForIdle()
    }

    private fun confirmDelete() {
        compose.onNodeWithText("Löschen").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Löschen").filterToOne(hasClickAction()).performClick()
        compose.waitForIdle()
    }

    @Test
    fun `with session photos the hero swipes over them in the order added`() {
        showScreen(SessionHero(photos = photos(3), coverPath = "/photos/cover.jpg"))

        compose.onNodeWithContentDescription("Fotos der Sitzung").assertExists()
        compose.onNodeWithContentDescription("Foto 1 von 3").assertIsDisplayed()
        compose.onNodeWithTag(SESSION_PHOTOS_TAG).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Foto 2 von 3").assertIsDisplayed()
        compose.onNodeWithTag(SESSION_HERO_COVER_TAG).assertDoesNotExist()
        compose.onNodeWithContentDescription("Foto-Optionen").assertExists()
    }

    @Test
    fun `without session photos the hero shows the tea's cover and no photo menu`() {
        showScreen(SessionHero(coverPath = "/photos/cover.jpg"))

        compose.onNodeWithTag(SESSION_HERO_COVER_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SESSION_PHOTOS_TAG).assertDoesNotExist()
        compose.onNodeWithTag(SESSION_HERO_LEAF_TAG).assertDoesNotExist()
        compose.onNodeWithContentDescription("Foto-Optionen").assertDoesNotExist()
        compose.onNodeWithTag(SESSION_ADD_PHOTO_TAG).assertIsEnabled()
    }

    @Test
    fun `without session photos and cover the hero shows the type leaf`() {
        showScreen(SessionHero())

        compose.onNodeWithTag(SESSION_HERO_LEAF_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SESSION_HERO_COVER_TAG).assertDoesNotExist()
        compose.onNodeWithText("Höchstens 6 Fotos").assertDoesNotExist()
    }

    @Test
    fun `at 6 photos Foto is greyed out with Hoechstens 6 Fotos`() {
        showScreen(SessionHero(photos = photos(6)))

        compose.onNodeWithTag(SESSION_ADD_PHOTO_TAG).assertIsNotEnabled()
        compose.onNodeWithText("Höchstens 6 Fotos").assertIsDisplayed()
    }

    @Test
    fun `an alert closes the photo menu and the delete question for good`() {
        showScreen(SessionHero(photos = photos(2)))

        openPhotoMenu()
        compose.onNodeWithText("Löschen").assertIsDisplayed()
        alert.value = "alert-1"
        compose.waitForIdle()
        compose.onNodeWithText("Löschen").assertDoesNotExist()

        alert.value = null
        compose.waitForIdle()
        compose.onNodeWithText("Löschen").assertDoesNotExist()
        openPhotoMenu()
        compose.onNodeWithText("Löschen").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Foto löschen?").assertIsDisplayed()
        alert.value = "alert-2"
        compose.waitForIdle()
        compose.onNodeWithText("Foto löschen?").assertDoesNotExist()

        alert.value = null
        compose.waitForIdle()
        compose.onNodeWithText("Foto löschen?").assertDoesNotExist()
        assertThat(deleted).isEmpty()
    }

    @Test
    fun `Foto adds the picked photo and shows it at once`() {
        val picker = FakePhotoPicker(Uri.parse("content://picked/1"))
        val photos = showRoute(picker)

        compose.onNodeWithTag(SESSION_ADD_PHOTO_TAG).performClick()
        compose.waitForIdle()

        assertThat(picker.launches).isEqualTo(1)
        assertThat(photos.added).containsExactly("content://picked/1")
        compose.onNodeWithContentDescription("Fotos der Sitzung").assertExists()
        compose.onNodeWithContentDescription("Foto 1 von 1").assertExists()
    }

    @Test
    fun `Loeschen shows the neighbouring photo`() {
        val photos = showRoute()
        runBlocking {
            photos.add(PAST, "content://picked/1")
            photos.add(PAST, "content://picked/2")
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Foto 1 von 2").assertExists()

        openPhotoMenu()
        confirmDelete()

        val left = runBlocking { photos.observePhotos(PAST).first() }
        assertThat(left.map { it.id }).containsExactly("session-photo-2")
        compose.onNodeWithContentDescription("Foto 1 von 1").assertExists()
    }

    @Test
    fun `Loeschen of the last photo brings back the tea's cover`() {
        val cover = folder.newFile("cover.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        sessions.journal.coverPhotos.value = mapOf(TEA to cover)
        val photos = showRoute()
        runBlocking { photos.add(PAST, "content://picked/1") }
        compose.waitForIdle()
        compose.onNodeWithTag(SESSION_HERO_COVER_TAG).assertDoesNotExist()

        openPhotoMenu()
        confirmDelete()

        compose.onNodeWithContentDescription("Fotos der Sitzung").assertDoesNotExist()
        compose.onNodeWithContentDescription("Foto-Optionen").assertDoesNotExist()
        compose.onNodeWithTag(SESSION_HERO_COVER_TAG).assertExists()
    }

    @Test
    fun `an unreadable photo shows a short error and adds nothing`() {
        val photos = showRoute(FakePhotoPicker(Uri.parse("content://picked/broken"))) {
            it.unreadable += "content://picked/broken"
        }

        compose.onNodeWithTag(SESSION_ADD_PHOTO_TAG).performClick()
        compose.waitForIdle()

        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("Das Foto konnte nicht gelesen werden.")
        assertThat(runBlocking { photos.observePhotos(PAST).first() }).isEmpty()
        compose.onNodeWithTag(SESSION_HERO_LEAF_TAG).assertExists()
    }

    @Test
    fun `a failed delete says so and keeps the photo`() {
        val photos = showRoute { it.failDeletes = true }
        runBlocking { photos.add(PAST, "content://picked/1") }
        compose.waitForIdle()

        openPhotoMenu()
        confirmDelete()

        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("Das Foto konnte nicht geändert werden.")
        compose.onNodeWithContentDescription("Foto 1 von 1").assertExists()
    }

    private companion object {
        const val TEA = "morgentau"
        const val PAST = "past"
        const val STARTED = 1_761_117_120_000L
        val PLACEHOLDER = ColorPainter(Color(0xFF8A9A7B))

        val page = SessionPage(
            sessionId = PAST,
            teaId = TEA,
            teaName = "Sencha Morgentau",
            teaSecondaryName = null,
            styleKey = "sencha",
            tea = SessionTea.AVAILABLE,
            startedAt = LocalDateTime.of(2026, 10, 22, 9, 12),
            method = BrewMethod.KYUSU,
            vesselName = "Kyusu 250 ml",
            leafGrams = 4.5,
            waterMl = 180,
            steps = listOf(SessionPageStep(StepKind.INFUSION, 1, 60, 62, 65, skipped = false)),
        )
    }
}
