package com.umamit.feature.collection.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
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
import com.umamit.core.ui.STOCK_SHEET_FIELD_TAG
import com.umamit.feature.collection.detail.TeaDetailFixture.TEA_ID
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The tea detail with its view model on fake repositories (SCR-09): what a tap writes shows at once, archive and
 * delete reach the host, the archive snackbar's *Rückgängig* brings the tea back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TeaDetailRouteTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val collection = FakeCollection()
    private val settings = FakeSettingsRepository()
    private val sessions = FakeSessions(collection, settings)
    private val archive = FakeTeaArchive(sessions)
    private val archivedTeas = mutableListOf<ArchivedTea>()
    private var deleted = 0
    private var notice by mutableStateOf<ArchivedTea?>(null)

    private fun show() {
        runBlocking { TeaDetailFixture.seed(collection) }
        val photos = PhotoFiles(folder.newFolder("photos"))
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                TeaDetailRoute(
                    host = TeaDetailHost(
                        onBack = {},
                        onBrew = {},
                        onArchived = {
                            archivedTeas += it
                            notice = it
                        },
                        onDeleted = { deleted++ },
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
                            FakeCatalogRepository(details = listOf(TeaDetailFixture.catalogTea)),
                            archive,
                            photos,
                        )
                    },
                )
                ArchivedTeaSnackbar(
                    archived = notice,
                    onUndo = { runBlocking { archive.restore(it) } },
                    onDone = { notice = null },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun menu(item: String) {
        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.onNodeWithText(item).performClick()
        compose.waitForIdle()
    }

    @Test
    fun `Favorit is checked at once`() {
        show()

        menu("Favorit")

        assertThat(runBlocking { collection.teas.getTea(TEA_ID)!!.isFavorite }).isTrue()
    }

    @Test
    fun `Bestand korrigieren updates VORRAT and the figures at once`() {
        show()

        compose.onNodeWithTag(TEA_DETAIL_CORRECT_STOCK_TAG).performScrollTo().performClick()
        compose.onNodeWithTag(STOCK_SHEET_FIELD_TAG).performTextReplacement("12")
        compose.onNodeWithText("Speichern").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("12\u00A0g übrig · knapp").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Vorrat: 12\u00A0g").assertExists()
    }

    @Test
    fun `Archivieren tells the host, and Rueckgaengig on the snackbar brings the tea back`() {
        show()

        menu("Archivieren")

        assertThat(archivedTeas.map { it.teaName }).containsExactly("Morgennebel")
        assertThat(runBlocking { collection.teas.getTea(TEA_ID)!!.isArchived }).isTrue()
        compose.onNodeWithText("Morgennebel archiviert").assertIsDisplayed()
        compose.onNodeWithText("Rückgängig").performClick()
        compose.waitForIdle()

        assertThat(runBlocking { collection.teas.getTea(TEA_ID)!!.isArchived }).isFalse()
        assertThat(notice).isNull()
    }

    @Test
    fun `Loeschen deletes the tea and tells the host`() {
        show()

        menu("Löschen")
        compose.onNodeWithText("Löschen").performClick()
        compose.waitForIdle()

        assertThat(deleted).isEqualTo(1)
        assertThat(runBlocking { collection.teas.getTea(TEA_ID) }).isNull()
    }

    @Test
    fun `during the tea's brew Archivieren does nothing`() {
        runBlocking { TeaDetailFixture.activeSession(sessions) }
        show()

        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.onNodeWithText("Archivieren").performClick()
        compose.waitForIdle()

        assertThat(archive.archived).isEmpty()
        assertThat(archivedTeas).isEmpty()
    }
}
