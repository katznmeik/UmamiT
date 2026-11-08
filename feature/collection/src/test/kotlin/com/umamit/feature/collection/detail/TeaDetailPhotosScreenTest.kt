package com.umamit.feature.collection.detail

import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
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
 * The detail's photo pager (SCR-28, TEA-05, #183): the photos with their TalkBack labels, *+ Foto*, the limit, and,
 * with the view model and the faked picker, a picked photo shown at once or a short error.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TeaDetailPhotosScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val folder = TemporaryFolder()

    private var adds = 0

    private fun show(tea: TeaDetail) {
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                TeaDetailScreen(
                    TeaDetailUiState.Ready(tea),
                    TeaDetailActions(addPhoto = { adds++ }),
                    photo = { ColorPainter(Color.Gray) },
                )
            }
        }
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
