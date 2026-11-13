package com.umamit.feature.collection.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.PhotoFiles
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.testing.FakeCatalogRepository
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.testing.FakeTeaArchive
import com.umamit.core.testing.FakeTeaPhotos
import com.umamit.feature.collection.detail.TeaDetailFixture.CATALOG_KEY
import com.umamit.feature.collection.detail.TeaDetailFixture.TEA_ID
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The tea detail's ⋮ *Im Katalog ansehen* (TEA-13, D-114 Q10) on the real view model and fakes: in the menu for a tea
 * whose catalog key has a catalog tea (discontinued counts), absent without a key or without the catalog tea, and
 * also for an archived tea; its tap hands the key to the host. An invented tea (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TeaDetailCatalogMenuTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val collection = FakeCollection()
    private val settings = FakeSettingsRepository()
    private val sessions = FakeSessions(collection, settings)
    private val archive = FakeTeaArchive(sessions)
    private val openedCatalog = mutableListOf<String>()

    private fun show(inCatalog: Boolean = true) {
        runBlocking { TeaDetailFixture.seed(collection) }
        val photos = PhotoFiles(folder.newFolder("photos"))
        val details = if (inCatalog) listOf(TeaDetailFixture.catalogTea) else emptyList()
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                TeaDetailRoute(
                    host = TeaDetailHost(
                        onBack = {},
                        onBrew = {},
                        onArchived = {},
                        onDeleted = {},
                        onOpenCatalog = { openedCatalog += it },
                    ),
                    viewModel = viewModel {
                        TeaDetailViewModel(
                            SavedStateHandle(mapOf("teaId" to TEA_ID)),
                            collection.teas,
                            collection.recipes,
                            collection.vessels,
                            collection.stock,
                            sessions.journal,
                            settings,
                            FakeCatalogRepository(details = details),
                            archive,
                            photos,
                            FakeTeaPhotos(collection, photos),
                        )
                    },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun openMenu() {
        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.waitForIdle()
    }

    @Test
    fun `a catalog tea's menu offers Im Katalog ansehen, whose tap opens its catalog page`() {
        show()

        openMenu()
        compose.onNodeWithTag(TEA_DETAIL_CATALOG_TAG).assertIsDisplayed()
        compose.onNodeWithText("Favorit").assertIsDisplayed()
        compose.onNodeWithTag(TEA_DETAIL_CATALOG_TAG).performClick()
        compose.waitForIdle()

        assertThat(openedCatalog).containsExactly(CATALOG_KEY)
        // The menu closed with the tap.
        compose.onNodeWithText("Favorit").assertDoesNotExist()
    }

    @Test
    fun `a tea without a catalog key has no Im Katalog ansehen`() {
        show()
        runBlocking { collection.teas.saveTea(collection.teas.getTea(TEA_ID)!!.copy(catalogKey = null)) }
        compose.waitForIdle()

        openMenu()

        compose.onNodeWithText("Favorit").assertIsDisplayed()
        compose.onNodeWithTag(TEA_DETAIL_CATALOG_TAG).assertDoesNotExist()
    }

    @Test
    fun `a key whose catalog tea is gone has no Im Katalog ansehen`() {
        show(inCatalog = false)

        openMenu()

        compose.onNodeWithText("Favorit").assertIsDisplayed()
        compose.onNodeWithTag(TEA_DETAIL_CATALOG_TAG).assertDoesNotExist()
    }

    @Test
    fun `an archived catalog tea still offers Im Katalog ansehen`() {
        show()
        runBlocking { archive.archive(TEA_ID) }
        compose.waitForIdle()

        openMenu()

        compose.onNodeWithTag(TEA_DETAIL_CATALOG_TAG).assertIsDisplayed()
        compose.onNodeWithText("Wiederherstellen").assertIsDisplayed()
    }
}
