package com.umamit.feature.collection.detail

import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.PhotoFiles
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.testing.FakeCatalogRepository
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakePhotoPicker
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.testing.FakeTeaArchive
import com.umamit.core.testing.FakeTeaPhotos
import com.umamit.feature.collection.detail.TeaDetailFixture.TEA_ID
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * The detail's photo pager (SCR-28, TEA-05, #183, #186): the photos with their TalkBack labels, *+ Foto*, the limit,
 * the photo's ⋮ with *Als Titelbild* and *Löschen* after a confirmation, and, with the view model and the fakes, a
 * picked photo shown at once or a short error, a new cover and a deleted photo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TeaDetailPhotosScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val folder = TemporaryFolder()

    private var adds = 0
    private val covered = mutableListOf<String>()
    private val deleted = mutableListOf<String>()
    private val shown = mutableStateOf<TeaDetail?>(null)

    private fun show(tea: TeaDetail) {
        shown.value = tea
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                TeaDetailScreen(
                    TeaDetailUiState.Ready(shown.value!!),
                    TeaDetailActions(addPhoto = { adds++ }, setCoverPhoto = { covered += it }, deletePhoto = {
                        deleted +=
                            it
                    }),
                    photo = { ColorPainter(Color.Gray) },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun swipeTo(page: Int) {
        repeat(page) {
            compose.onNodeWithTag(TEA_DETAIL_PHOTOS_TAG).performTouchInput { swipeLeft() }
            compose.waitForIdle()
        }
    }

    private fun openPhotoMenu() {
        compose.onNodeWithContentDescription("Foto-Optionen").performClick()
        compose.waitForIdle()
    }

    private fun photos(count: Int) = List(count) { DetailPhoto("p$it", "/photos/p$it.jpg", isCover = it == 0) }

    @Test
    fun `the pager reads the tea's photos, the cover first`() {
        show(TeaDetailScreenTest.sample().copy(photos = photos(3), photoCount = 3))

        compose.onNodeWithContentDescription("Fotos von Morgennebel").assertExists()
        compose.onNodeWithContentDescription("Foto 1 von 3, Titelbild").assertIsDisplayed()

        compose.onNodeWithTag(TEA_DETAIL_PHOTOS_TAG).performTouchInput { swipeLeft() }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Foto 2 von 3").assertIsDisplayed()
    }

    @Test
    fun `the shown photo's menu offers Als Titelbild and Loeschen`() {
        show(TeaDetailScreenTest.sample().copy(photos = photos(3), photoCount = 3))
        swipeTo(1)

        openPhotoMenu()
        compose.onNodeWithText("Als Titelbild").assertIsDisplayed().performClick()

        assertThat(covered).containsExactly("p1")
    }

    @Test
    fun `the cover's menu has no Als Titelbild`() {
        show(TeaDetailScreenTest.sample().copy(photos = photos(2), photoCount = 2))

        openPhotoMenu()

        compose.onNodeWithText("Als Titelbild").assertDoesNotExist()
        compose.onNodeWithText("Löschen").assertIsDisplayed()
    }

    @Test
    fun `without photos there is no photo menu`() {
        show(TeaDetailScreenTest.sample())

        compose.onNodeWithContentDescription("Foto-Optionen").assertDoesNotExist()
    }

    @Test
    fun `Loeschen asks first and Abbrechen keeps the photo`() {
        show(TeaDetailScreenTest.sample().copy(photos = photos(3), photoCount = 3))
        swipeTo(2)

        openPhotoMenu()
        compose.onNodeWithText("Löschen").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Foto löschen?").assertIsDisplayed()
        compose.onNodeWithText("Abbrechen").performClick()
        compose.waitForIdle()

        assertThat(deleted).isEmpty()
        compose.onNodeWithText("Foto löschen?").assertDoesNotExist()
    }

    @Test
    fun `Loeschen after the confirmation deletes the shown photo`() {
        show(TeaDetailScreenTest.sample().copy(photos = photos(3), photoCount = 3))
        swipeTo(2)

        openPhotoMenu()
        compose.onNodeWithText("Löschen").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Das Foto wird aus der App entfernt.").assertIsDisplayed()
        compose.onAllNodesWithText("Löschen").filterToOne(hasClickAction()).performClick()
        compose.waitForIdle()

        assertThat(deleted).containsExactly("p2")
    }

    @Test
    fun `deleting the cover says the next becomes the cover`() {
        show(TeaDetailScreenTest.sample().copy(photos = photos(2), photoCount = 2))

        openPhotoMenu()
        compose.onNodeWithText("Löschen").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Das Foto wird aus der App entfernt. Das nächste Foto wird zum Titelbild.")
            .assertIsDisplayed()
    }

    @Test
    fun `after Als Titelbild the pager stays on the same photo, now first`() {
        val before = photos(3)
        show(TeaDetailScreenTest.sample().copy(photos = before, photoCount = 3))
        swipeTo(2)
        compose.onNodeWithContentDescription("Foto 3 von 3").assertIsDisplayed()

        // The view model's answer to *Als Titelbild* on p2: the cover first, the others in their order.
        shown.value = shown.value!!.copy(
            photos = listOf(before[2].copy(isCover = true), before[0].copy(isCover = false), before[1]),
        )
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Foto 1 von 3, Titelbild").assertIsDisplayed()
    }

    @Test
    fun `Foto opens the picker`() {
        show(TeaDetailScreenTest.sample())

        compose.onNodeWithText("+ Foto").assertIsEnabled().performClick()

        assertThat(adds).isEqualTo(1)
    }

    @Test
    fun `at 10 photos Foto is greyed out with Hoechstens 10 Fotos`() {
        show(TeaDetailScreenTest.sample().copy(photos = photos(10), photoCount = 10))

        compose.onNodeWithTag(TEA_DETAIL_ADD_PHOTO_TAG).assertIsNotEnabled()
        compose.onNodeWithText("Höchstens 10 Fotos").assertIsDisplayed()
    }

    @Test
    fun `below 10 there is no limit line`() {
        show(TeaDetailScreenTest.sample().copy(photos = photos(9), photoCount = 9))

        compose.onNodeWithTag(TEA_DETAIL_ADD_PHOTO_TAG).assertIsEnabled()
        compose.onNodeWithText("Höchstens 10 Fotos").assertDoesNotExist()
    }

    private fun showRoute(picker: FakePhotoPicker, configure: (FakeTeaPhotos) -> Unit = {}): FakeTeaPhotos {
        val collection = FakeCollection()
        val settings = FakeSettingsRepository()
        val sessions = FakeSessions(collection, settings)
        val files = PhotoFiles(folder.newFolder("photos"))
        val teaPhotos = FakeTeaPhotos(collection, files).also(configure)
        runBlocking { TeaDetailFixture.seed(collection) }
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                TeaDetailRoute(
                    host = TeaDetailHost(onBack = {}, onBrew = {}, onArchived = {}, onDeleted = {}),
                    photoPicker = picker,
                    viewModel = viewModel {
                        TeaDetailViewModel(
                            SavedStateHandle(mapOf("teaId" to TEA_ID)),
                            collection.teas,
                            collection.recipes,
                            collection.vessels,
                            collection.stock,
                            sessions.journal,
                            settings,
                            FakeCatalogRepository(details = listOf(TeaDetailFixture.catalogTea)),
                            FakeTeaArchive(sessions),
                            files,
                            teaPhotos,
                        )
                    },
                )
            }
        }
        compose.waitForIdle()
        return teaPhotos
    }

    @Test
    fun `a picked photo shows at once as the cover`() {
        val picker = FakePhotoPicker(Uri.parse("content://picked/1"))
        val teaPhotos = showRoute(picker)

        compose.onNodeWithText("+ Foto").performClick()
        compose.waitForIdle()

        assertThat(picker.launches).isEqualTo(1)
        assertThat(teaPhotos.added).containsExactly("content://picked/1")
        compose.onNodeWithContentDescription("Foto 1 von 1, Titelbild").assertExists()
    }

    @Test
    fun `Als Titelbild shows the new cover on the detail at once`() {
        val teaPhotos = showRoute(FakePhotoPicker(Uri.parse("content://picked/1")))
        runBlocking {
            teaPhotos.add(TEA_ID, "content://picked/1")
            teaPhotos.add(TEA_ID, "content://picked/2")
        }
        compose.waitForIdle()
        swipeTo(1)

        openPhotoMenu()
        compose.onNodeWithText("Als Titelbild").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Foto 1 von 2, Titelbild").assertIsDisplayed()
    }

    @Test
    fun `deleting the last photo brings back the leaf`() {
        val teaPhotos = showRoute(FakePhotoPicker(Uri.parse("content://picked/1")))
        runBlocking { teaPhotos.add(TEA_ID, "content://picked/1") }
        compose.waitForIdle()

        openPhotoMenu()
        compose.onNodeWithText("Löschen").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Löschen").filterToOne(hasClickAction()).performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Fotos von Morgennebel").assertDoesNotExist()
        compose.onNodeWithContentDescription("Foto-Optionen").assertDoesNotExist()
    }

    @Test
    fun `backing out of the picker adds nothing`() {
        val teaPhotos = showRoute(FakePhotoPicker(next = null))

        compose.onNodeWithText("+ Foto").performClick()
        compose.waitForIdle()

        assertThat(teaPhotos.added).isEmpty()
    }

    @Test
    fun `an unreadable photo shows a short error and adds nothing`() {
        val showRoute = showRoute(FakePhotoPicker(Uri.parse("content://picked/broken"))) {
            it.unreadable += "content://picked/broken"
        }

        compose.onNodeWithText("+ Foto").performClick()
        compose.waitForIdle()

        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("Das Foto konnte nicht gelesen werden.")
        assertThat(showRoute.added).hasSize(1)
        compose.onNodeWithContentDescription("Fotos von Morgennebel").assertDoesNotExist()
    }
}
