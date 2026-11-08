package com.umamit.feature.collection.detail

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.PhotoFiles
import com.umamit.core.model.Photo
import com.umamit.core.model.PhotoOrigin
import com.umamit.core.model.PhotoOwner
import com.umamit.core.testing.FakeCatalogRepository
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.testing.FakeTeaArchive
import com.umamit.core.testing.FakeTeaPhotos
import com.umamit.feature.collection.detail.TeaDetailFixture.TEA_ID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The detail's photos (TEA-05, SCR-28, #183, #186): the pager's photos, the cover first, *+ Foto* and its limit,
 * *Als Titelbild* and *Löschen*.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TeaDetailPhotosViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val collection = FakeCollection()
    private val settings = FakeSettingsRepository()
    private val sessions = FakeSessions(collection, settings)
    private val files by lazy { PhotoFiles(folder.newFolder("photos")) }
    private val photos by lazy { FakeTeaPhotos(collection, files) }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = TeaDetailViewModel(
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
        photos,
    )

    private val TeaDetailViewModel.tea get() = (state.value as TeaDetailUiState.Ready).tea

    private suspend fun shopPhoto() {
        collection.teas.savePhoto(Photo("shop", PhotoOwner.OfTea(TEA_ID), PhotoOrigin.CATALOG_COPY, 10, 10, 4))
        files.write("shop", byteArrayOf(1, 2, 3, 4))
        collection.teas.setCoverPhoto(TEA_ID, "shop")
    }

    @Test
    fun `a tea without photos has an empty pager and can add one`() = runTest {
        TeaDetailFixture.seed(collection)

        val tea = viewModel().tea

        assertThat(tea.photos).isEmpty()
        assertThat(tea.canAddPhoto).isTrue()
    }

    @Test
    fun `the pager shows the cover first, then the others in their order`() = runTest {
        TeaDetailFixture.seed(collection)
        shopPhoto()
        photos.add(TEA_ID, "content://picked/1")
        photos.add(TEA_ID, "content://picked/2")
        collection.teas.setCoverPhoto(TEA_ID, "photo-2")

        val pager = viewModel().tea.photos

        assertThat(pager.map { it.id }).containsExactly("photo-2", "shop", "photo-1").inOrder()
        assertThat(pager.map { it.isCover }).containsExactly(true, false, false).inOrder()
        assertThat(pager.first().path).isEqualTo(files.file("photo-2").path)
    }

    @Test
    fun `Foto adds the picked photo at once, the first becomes the cover`() = runTest {
        TeaDetailFixture.seed(collection)
        val vm = viewModel()

        vm.addPhoto("content://picked/1")

        assertThat(photos.added).containsExactly("content://picked/1")
        assertThat(vm.tea.photos.map { it.id }).containsExactly("photo-1")
        assertThat(vm.tea.photos.single().isCover).isTrue()
        assertThat(vm.tea.photoPath).isEqualTo(files.file("photo-1").path)
    }

    @Test
    fun `at 10 photos Foto is greyed out`() = runTest {
        TeaDetailFixture.seed(collection)
        val vm = viewModel()

        repeat(Photo.MAX_PER_TEA - 1) { vm.addPhoto("content://picked/$it") }
        assertThat(vm.tea.canAddPhoto).isTrue()
        vm.addPhoto("content://picked/last")

        assertThat(vm.tea.photos).hasSize(10)
        assertThat(vm.tea.canAddPhoto).isFalse()
    }

    @Test
    fun `an unreadable photo says so and adds nothing`() = runTest {
        TeaDetailFixture.seed(collection)
        photos.unreadable += "content://picked/broken"
        val vm = viewModel()

        vm.addPhoto("content://picked/broken")

        assertThat(withTimeout(1_000) { vm.navigation.first() }).isEqualTo(TeaDetailEvent.PhotoUnreadable)
        assertThat(vm.tea.photos).isEmpty()
    }

    @Test
    fun `a refused 11th photo says Hoechstens 10 Fotos`() = runTest {
        TeaDetailFixture.seed(collection)
        repeat(Photo.MAX_PER_TEA) { photos.add(TEA_ID, "content://picked/$it") }
        val vm = viewModel()

        vm.addPhoto("content://picked/eleven")

        assertThat(withTimeout(1_000) { vm.navigation.first() }).isEqualTo(TeaDetailEvent.PhotoLimitReached)
    }

    @Test
    fun `Als Titelbild shows the new cover first at once`() = runTest {
        TeaDetailFixture.seed(collection)
        shopPhoto()
        photos.add(TEA_ID, "content://picked/1")
        val vm = viewModel()

        vm.setCoverPhoto("photo-1")

        assertThat(vm.tea.photos.map { it.id }).containsExactly("photo-1", "shop").inOrder()
        assertThat(vm.tea.photos.map { it.isCover }).containsExactly(true, false).inOrder()
        assertThat(vm.tea.photoPath).isEqualTo(files.file("photo-1").path)
    }

    @Test
    fun `Loeschen of the cover makes the next photo the cover`() = runTest {
        TeaDetailFixture.seed(collection)
        shopPhoto()
        photos.add(TEA_ID, "content://picked/1")
        photos.add(TEA_ID, "content://picked/2")
        val vm = viewModel()

        vm.deletePhoto("shop")

        assertThat(vm.tea.photos.map { it.id }).containsExactly("photo-1", "photo-2").inOrder()
        assertThat(vm.tea.photos.first().isCover).isTrue()
        assertThat(vm.tea.photoPath).isEqualTo(files.file("photo-1").path)
        assertThat(files.file("shop").exists()).isFalse()
    }

    @Test
    fun `Loeschen of the last photo brings back the leaf`() = runTest {
        TeaDetailFixture.seed(collection)
        photos.add(TEA_ID, "content://picked/1")
        val vm = viewModel()

        vm.deletePhoto("photo-1")

        assertThat(vm.tea.photos).isEmpty()
        assertThat(vm.tea.photoPath).isNull()
        assertThat(vm.tea.canAddPhoto).isTrue()
        assertThat(files.file("photo-1").exists()).isFalse()
    }
}
