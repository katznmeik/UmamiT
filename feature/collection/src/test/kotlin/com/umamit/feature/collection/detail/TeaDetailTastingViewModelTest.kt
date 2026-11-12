package com.umamit.feature.collection.detail

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.PhotoFiles
import com.umamit.core.model.DescriptorCount
import com.umamit.core.model.TasteAxis
import com.umamit.core.model.TasteDescriptor
import com.umamit.core.model.TeaRating
import com.umamit.core.model.UserSettings
import com.umamit.core.testing.FakeCatalogRepository
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.testing.FakeTeaArchive
import com.umamit.core.testing.FakeTeaPhotos
import com.umamit.feature.collection.detail.TeaDetailFixture.LAST_BREW_MS
import com.umamit.feature.collection.detail.TeaDetailFixture.TEA_ID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate

/**
 * The tea detail's rating, GESCHMACK and LETZTE SITZUNGEN (TEA-13, JRN-02, JRN-03, JRN-04, D-112, #232): the figures
 * come from the sessions' tasting inputs and follow them at once; every one is absent without data.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TeaDetailTastingViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val collection = FakeCollection()
    private val settings = FakeSettingsRepository(UserSettings(lowStockGrams = 15.0))
    private val sessions = FakeSessions(collection, settings)
    private val catalog = FakeCatalogRepository(details = listOf(TeaDetailFixture.catalogTea))
    private val clock = FakeClock()
    private val photos by lazy { PhotoFiles(folder.newFolder("photos")) }

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
        catalog,
        FakeTeaArchive(sessions),
        photos,
        FakeTeaPhotos(collection, photos),
        clock,
    )

    private val TeaDetailViewModel.tea get() = (state.value as TeaDetailUiState.Ready).tea

    private suspend fun brewed(id: String, daysAgo: Int = 0) =
        TeaDetailFixture.brewed(sessions, id, at = LAST_BREW_MS - 86_400_000L * daysAgo)

    @Test
    fun `without a rated session the header has no rating and no section shows`() = runTest {
        TeaDetailFixture.seed(collection)

        val tea = viewModel().tea

        assertThat(tea.rating).isNull()
        assertThat(tea.taste.hasData).isFalse()
        assertThat(tea.recentSessions).isEmpty()
        assertThat(tea.hasMoreSessions).isFalse()
    }

    @Test
    fun `an unrated finished session adds no rating but is listed`() = runTest {
        TeaDetailFixture.seed(collection)
        brewed("s1")

        val tea = viewModel().tea

        assertThat(tea.rating).isNull()
        assertThat(tea.recentSessions.map { it.id }).containsExactly("s1")
        assertThat(tea.recentSessions.single().rating).isNull()
    }

    @Test
    fun `the header holds the average of the rated sessions and their count`() = runTest {
        TeaDetailFixture.seed(collection)
        brewed("s1", daysAgo = 3)
        brewed("s2", daysAgo = 2)
        brewed("s3", daysAgo = 1)
        sessions.tasting.setRating("s1", 4.0)
        sessions.tasting.setRating("s2", 5.0)

        assertThat(viewModel().tea.rating).isEqualTo(TeaRating(average = 4.5, count = 2))
    }

    @Test
    fun `the detail follows a rating that changes at once`() = runTest {
        TeaDetailFixture.seed(collection)
        brewed("s1")
        val viewModel = viewModel()
        assertThat(viewModel.tea.rating).isNull()

        sessions.tasting.setRating("s1", 3.5)
        assertThat(viewModel.tea.rating).isEqualTo(TeaRating(3.5, 1))
        assertThat(viewModel.tea.recentSessions.single().rating).isEqualTo(3.5)

        sessions.tasting.setRating("s1", null)
        assertThat(viewModel.tea.rating).isNull()
        assertThat(viewModel.tea.recentSessions.single().rating).isNull()
    }

    @Test
    fun `the last three sessions are listed newest first and the rest is counted`() = runTest {
        TeaDetailFixture.seed(collection)
        brewed("old", daysAgo = 9)
        brewed("c", daysAgo = 3)
        brewed("a", daysAgo = 1)
        brewed("b", daysAgo = 2)

        val tea = viewModel().tea

        assertThat(tea.recentSessions.map { it.id }).containsExactly("a", "b", "c").inOrder()
        assertThat(tea.sessionCount).isEqualTo(4)
        assertThat(tea.hasMoreSessions).isTrue()
    }

    @Test
    fun `exactly three sessions have no further ones to show`() = runTest {
        TeaDetailFixture.seed(collection)
        brewed("a", daysAgo = 1)
        brewed("b", daysAgo = 2)
        brewed("c", daysAgo = 3)

        assertThat(viewModel().tea.hasMoreSessions).isFalse()
    }

    @Test
    fun `a session row holds its day, first infusion and counts`() = runTest {
        TeaDetailFixture.seed(collection)
        brewed("s1")

        val row = viewModel().tea.recentSessions.single()

        assertThat(row.day).isEqualTo(LocalDate.of(2026, 10, 21))
        assertThat(row.temperatureC).isEqualTo(70)
        assertThat(row.firstInfusionSeconds).isEqualTo(60)
        assertThat(row.infusions).isEqualTo(1)
        assertThat(row.finishedEarly).isFalse()
    }

    @Test
    fun `a session in progress is not listed`() = runTest {
        TeaDetailFixture.seed(collection)
        TeaDetailFixture.activeSession(sessions)

        val tea = viewModel().tea

        assertThat(tea.recentSessions).isEmpty()
        assertThat(tea.sessionCount).isEqualTo(0)
    }

    @Test
    fun `GESCHMACK averages each axis over the sessions that set it`() = runTest {
        TeaDetailFixture.seed(collection)
        brewed("s1", daysAgo = 2)
        brewed("s2", daysAgo = 1)
        sessions.tasting.setTaste("s1", TasteAxis.UMAMI, 5)
        sessions.tasting.setTaste("s2", TasteAxis.UMAMI, 4)
        sessions.tasting.setTaste("s1", TasteAxis.BODY, 2)
        sessions.tasting.setTaste("s1", TasteAxis.AROMA, 3)

        val taste = viewModel().tea.taste

        assertThat(taste.averages).containsExactly(TasteAxis.UMAMI, 4.5, TasteAxis.BODY, 2.0, TasteAxis.AROMA, 3.0)
        assertThat(taste.hasData).isTrue()
    }

    @Test
    fun `two axes alone draw no radar and show no section`() = runTest {
        TeaDetailFixture.seed(collection)
        brewed("s1")
        sessions.tasting.setTaste("s1", TasteAxis.UMAMI, 5)
        sessions.tasting.setTaste("s1", TasteAxis.BODY, 2)

        assertThat(viewModel().tea.taste.hasData).isFalse()
    }

    @Test
    fun `descriptors alone show the section`() = runTest {
        TeaDetailFixture.seed(collection)
        brewed("s1")
        sessions.tasting.setDescriptor("s1", TasteDescriptor.MARINE_NORI.key, ticked = true)

        val taste = viewModel().tea.taste

        assertThat(taste.hasData).isTrue()
        assertThat(taste.descriptors).containsExactly(DetailDescriptor(TasteDescriptor.MARINE_NORI, 1))
    }

    @Test
    fun `the top descriptors are five by count then vocabulary order, unknown keys left out`() {
        val counts = listOf(
            // The repository's order: count, then key.
            DescriptorCount("zz.unknown", 9),
            DescriptorCount(TasteDescriptor.SWEET_HONIG.key, 4),
            DescriptorCount(TasteDescriptor.MARINE_NORI.key, 4),
            DescriptorCount(TasteDescriptor.VEGETAL_SPINAT.key, 3),
            DescriptorCount(TasteDescriptor.FLORAL_JASMIN.key, 3),
            DescriptorCount(TasteDescriptor.MARINE_KOMBU.key, 3),
            DescriptorCount(TasteDescriptor.FRUITY_YUZU.key, 2),
            DescriptorCount(TasteDescriptor.MOUTHFEEL_CREMIG.key, 1),
        )

        val top = topDescriptors(counts)

        assertThat(top.map { it.descriptor }).containsExactly(
            // 4 each: Nori before Honig (vocabulary order, not key order).
            TasteDescriptor.MARINE_NORI,
            TasteDescriptor.SWEET_HONIG,
            // 3 each: Kombu, Spinat, Jasmin.
            TasteDescriptor.MARINE_KOMBU,
            TasteDescriptor.VEGETAL_SPINAT,
            TasteDescriptor.FLORAL_JASMIN,
        ).inOrder()
        assertThat(top.map { it.count }).containsExactly(4, 4, 3, 3, 3).inOrder()
    }

    @Test
    fun `fewer than five descriptors are all shown`() {
        val counts = listOf(DescriptorCount(TasteDescriptor.MARINE_NORI.key, 2))

        assertThat(topDescriptors(counts)).hasSize(1)
        assertThat(topDescriptors(emptyList())).isEmpty()
    }
}
