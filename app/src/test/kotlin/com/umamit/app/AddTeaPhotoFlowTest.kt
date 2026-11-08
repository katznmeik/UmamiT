package com.umamit.app

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.PhotoFiles
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.testing.FakeCatalogRepository
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeCollectionList
import com.umamit.core.testing.FakePhotoPicker
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.testing.FakeTeaArchive
import com.umamit.core.testing.FakeTeaPhotos
import com.umamit.feature.collection.MyTeasActions
import com.umamit.feature.collection.MyTeasContent
import com.umamit.feature.collection.MyTeasViewModel
import com.umamit.feature.collection.detail.TEA_DETAIL_ADD_PHOTO_TAG
import com.umamit.feature.collection.detail.TEA_DETAIL_TAG
import com.umamit.feature.collection.detail.TeaDetailRoute
import com.umamit.feature.collection.detail.TeaDetailViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * *+ Foto* (TEA-05, SCR-28, #183) in the Sammlung tab: a tea without photos, its detail, the faked photo picker
 * returns an image, and the photo shows on the detail at once and, back in *Meine Tees*, as the row's cover.
 * The real root and shell navigation, the real view models on fakes; an invented tea (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class AddTeaPhotoFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val folder = TemporaryFolder()

    private val collection = FakeCollection()
    private val settings = FakeSettingsRepository()
    private val sessions = FakeSessions(collection, settings)
    private val archive = FakeTeaArchive(sessions)
    private val picker = FakePhotoPicker(Uri.parse("content://media/picker/0/42"))

    /** The photo file each list row's thumbnail was asked for, by row. */
    private val listThumbnails = mutableListOf<String?>()

    @Before
    fun seed(): Unit = runBlocking {
        collection.teas.saveTea(Tea(id = ID, name = NAME, category = TeaCategory.GREEN, form = TeaForm.LOOSE_LEAF))
    }

    @Test
    fun `a picked photo shows on the detail and as the cover in the list`() {
        val files = PhotoFiles(folder.newFolder("photos"))
        val photos = FakeTeaPhotos(collection, files)
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                UmamitRoot(
                    activeBrewSource = NoActiveBrewSource,
                    journalSources = testJournalSources(sessions, collection.teas),
                    alertKey = MutableStateFlow(null),
                    collectionScreens = CollectionStackScreens(
                        myTeas = { tab -> MyTeas(tab, files) },
                        tea = { host ->
                            TeaDetailRoute(
                                host = host,
                                photoPicker = picker,
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
                                        files,
                                        photos,
                                    )
                                },
                            )
                        },
                    ),
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag(tabTag(Tab.Collection)).performClick()
        compose.waitForIdle()
        assertThat(listThumbnails.last()).isNull()

        compose.onNodeWithText(NAME).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(TEA_DETAIL_TAG).assertIsDisplayed()
        compose.onNodeWithTag(TEA_DETAIL_ADD_PHOTO_TAG).performClick()
        compose.waitForIdle()

        assertThat(picker.launches).isEqualTo(1)
        assertThat(photos.added).containsExactly("content://media/picker/0/42")
        compose.onNodeWithContentDescription("Foto 1 von 1, Titelbild").assertIsDisplayed()

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()

        compose.onNodeWithText(NAME).assertIsDisplayed()
        val photoId = runBlocking { collection.teas.getTea(ID)!!.coverPhotoId }
        assertThat(listThumbnails.last()).isEqualTo(files.file(checkNotNull(photoId)).path)
    }

    /** The real *Meine Tees* list; its thumbnails are recorded and drawn as placeholders. */
    @Composable
    private fun MyTeas(
        tab: MyTeasTab,
        files: PhotoFiles,
        vm: MyTeasViewModel = viewModel { MyTeasViewModel(FakeCollectionList(sessions), settings, archive, files) },
    ) {
        val state by vm.state.collectAsStateWithLifecycle()
        MyTeasContent(
            state = state,
            actions = MyTeasActions(onOpenTea = tab.openTea),
            modifier = tab.modifier,
            thumbnail = { path ->
                listThumbnails += path
                ColorPainter(Color.Gray)
            },
        )
    }

    private companion object {
        const val ID = "abendrot"
        const val NAME = "Abendrot"
    }
}
