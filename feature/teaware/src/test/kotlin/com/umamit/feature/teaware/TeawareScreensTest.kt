package com.umamit.feature.teaware

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.PhotoFiles
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.Vessel
import com.umamit.core.model.VesselType
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakePhotoPicker
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeVesselArchive
import com.umamit.core.testing.FakeVesselPhotos
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** SCR-13 and SCR-14 through their view models on fakes (WARE-01, WARE-04, D-100). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TeawareScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val collection = FakeCollection()
    private val archive = FakeVesselArchive(FakeSessions(collection))
    private var alertKey by mutableStateOf<String?>(null)

    @get:Rule
    val folder = TemporaryFolder()

    private val files by lazy { PhotoFiles(folder.newFolder("photos")) }
    private val photos by lazy { FakeVesselPhotos(collection, files) }

    private fun save(vararg vessels: Vessel) = runBlocking { vessels.forEach { collection.vessels.saveVessel(it) } }

    private fun stored(): List<Vessel> = runBlocking { collection.vessels.observeVessels().first() }

    private fun showList(archived: ArchivedVessel? = null, onOpen: (String) -> Unit = {}, onAdd: () -> Unit = {}) {
        val vm = TeawareViewModel(collection.vessels, archive, files)
        compose.setContent {
            UmamiTheme(darkTheme = false, reduceMotion = true) {
                TeawareRoute(
                    onBack = {},
                    onOpenVessel = onOpen,
                    onAddVessel = onAdd,
                    archived = archived,
                    photo = { ColorPainter(Color(PLACEHOLDER)) },
                    viewModel = vm,
                )
            }
        }
    }

    private fun showEditor(
        vesselId: String? = null,
        onSave: (String) -> Unit = {},
        onArchive: (ArchivedVessel) -> Unit = {},
        onClose: () -> Unit = {},
        picker: FakePhotoPicker = FakePhotoPicker(),
    ) {
        val saved = SavedStateHandle().apply { if (vesselId != null) set(VesselEditorViewModel.VESSEL_ID, vesselId) }
        val vm = VesselEditorViewModel(saved, collection.vessels, archive, photos, files)
        compose.setContent {
            UmamiTheme(darkTheme = false, reduceMotion = true) {
                VesselEditorRoute(
                    onClose = onClose,
                    onSave = onSave,
                    onArchive = onArchive,
                    alertKey = alertKey,
                    photoPicker = picker,
                    photo = { ColorPainter(Color(PLACEHOLDER)) },
                    viewModel = vm,
                )
            }
        }
    }

    @Test
    fun `the list shows each vessel with its volume, archived ones with Wiederherstellen`() {
        save(
            Vessel("k", "Kyusu 250 ml", VesselType.KYUSU, 300, 250, isFavorite = true),
            Vessel("a", "Alte Kanne", VesselType.CLAY_TEAPOT, 500, isArchived = true),
        )
        var opened: String? = null
        showList(onOpen = { opened = it })

        compose.onNodeWithText("300 ml · 250 ml nutzbar").assertIsDisplayed()
        compose.onNodeWithContentDescription("Favorit").assertIsDisplayed()
        compose.onNodeWithText("ARCHIVIERT").assertIsDisplayed()

        compose.onNodeWithText("Kyusu 250 ml").performClick()
        assertThat(opened).isEqualTo("k")

        compose.onNodeWithText("Wiederherstellen").performClick()
        compose.waitForIdle()
        assertThat(stored().single { it.id == "a" }.isArchived).isFalse()
        compose.onNodeWithText("ARCHIVIERT").assertDoesNotExist()
    }

    @Test
    fun `the FAB adds a vessel`() {
        var added = false
        showList(onAdd = { added = true })

        compose.onNodeWithText("Noch kein Teegeschirr").assertIsDisplayed()
        compose.onNodeWithContentDescription("Gefäß hinzufügen").performClick()

        assertThat(added).isTrue()
    }

    @Test
    fun `after Archivieren the list offers Rueckgaengig`() {
        save(Vessel("k", "Kyusu 250 ml", VesselType.KYUSU, 300, isArchived = true))
        showList(archived = ArchivedVessel("k", "Kyusu 250 ml"))

        compose.onNodeWithText("Kyusu 250 ml archiviert").assertIsDisplayed()
        compose.onNodeWithText("Rückgängig").performClick()
        compose.waitForIdle()

        assertThat(stored().single().isArchived).isFalse()
    }

    @Test
    fun `a new vessel is saved and its id handed back`() {
        var savedId: String? = null
        showEditor(onSave = { savedId = it })

        compose.onNodeWithText("Neues Gefäß").assertIsDisplayed()
        compose.onNodeWithTag(VesselEditorTags.NAME).performTextInput("Gaiwan klein")
        compose.onNodeWithTag(VesselEditorTags.TYPE).performClick()
        compose.onNodeWithText("Gaiwan").performClick()
        compose.onNodeWithTag(VesselEditorTags.CAPACITY).performTextInput("1 20")
        compose.onNodeWithTag(VesselEditorTags.FAVORITE).performScrollTo().performClick()
        compose.onNodeWithTag(VesselEditorTags.SAVE).performClick()
        compose.waitForIdle()

        val vessel = stored().single()
        assertThat(savedId).isEqualTo(vessel.id)
        assertThat(vessel.name).isEqualTo("Gaiwan klein")
        assertThat(vessel.type).isEqualTo(VesselType.GAIWAN)
        assertThat(vessel.capacityMl).isEqualTo(120)
        assertThat(vessel.isFavorite).isTrue()
    }

    @Test
    fun `Speichern shows the errors at the fields and stores nothing`() {
        var savedId: String? = null
        showEditor(onSave = { savedId = it })

        compose.onNodeWithTag(VesselEditorTags.SAVE).performClick()

        compose.onNodeWithText("Gib einen Namen ein.").assertIsDisplayed()
        compose.onNodeWithText("10 bis 3000 ml").assertIsDisplayed()
        assertThat(savedId).isNull()
        assertThat(stored()).isEmpty()
    }

    @Test
    fun `closing with changes asks Aenderungen verwerfen`() {
        save(Vessel("k", "Kyusu 250 ml", VesselType.KYUSU, 300, 250))
        var closed = false
        showEditor("k", onClose = { closed = true })

        compose.onNodeWithTag(VesselEditorTags.CAPACITY).performTextReplacement("350")
        compose.onNodeWithContentDescription("Schließen").performClick()
        compose.onNodeWithText("Änderungen verwerfen?").assertIsDisplayed()
        assertThat(closed).isFalse()

        compose.onNodeWithText("Verwerfen").performClick()
        assertThat(closed).isTrue()
        assertThat(stored().single().capacityMl).isEqualTo(300)
    }

    @Test
    fun `closing without changes closes at once`() {
        save(Vessel("k", "Kyusu 250 ml", VesselType.KYUSU, 300, 250))
        var closed = false
        showEditor("k", onClose = { closed = true })

        compose.onNodeWithContentDescription("Schließen").performClick()

        assertThat(closed).isTrue()
    }

    @Test
    fun `an alert removes the type menu and it does not come back (rule 8)`() {
        showEditor()
        compose.onNodeWithTag(VesselEditorTags.TYPE).performClick()
        compose.onNodeWithText("Gaiwan").assertExists()

        alertAndEnd()

        compose.onNodeWithText("Gaiwan").assertDoesNotExist()
    }

    @Test
    fun `an alert removes the discard question and the menu, and they do not come back (rule 8)`() {
        save(Vessel("k", "Kyusu 250 ml", VesselType.KYUSU, 300, 250))
        var closed = false
        showEditor("k", onClose = { closed = true })
        compose.onNodeWithTag(VesselEditorTags.CAPACITY).performTextReplacement("350")
        compose.onNodeWithContentDescription("Schließen").performClick()
        compose.onNodeWithText("Änderungen verwerfen?").assertIsDisplayed()

        alertAndEnd()

        compose.onNodeWithText("Änderungen verwerfen?").assertDoesNotExist()
        assertThat(closed).isFalse()
        compose.onNodeWithTag(VesselEditorTags.MORE).performClick()
        compose.onNodeWithText("Archivieren").assertExists()
        alertAndEnd(key = "s1:1:2")
        compose.onNodeWithText("Archivieren").assertDoesNotExist()
    }

    /** An alert starts: what was open is gone after the frame that recomposes and the one that drops its window. */
    private fun alertAndEnd(key: String = "s1:0:1") {
        compose.mainClock.autoAdvance = false
        alertKey = key
        repeat(2) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        compose.onNodeWithText("Gaiwan").assertDoesNotExist()
        compose.onNodeWithText("Änderungen verwerfen?").assertDoesNotExist()
        compose.onNodeWithText("Archivieren").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
        alertKey = null
        compose.waitForIdle()
    }

    @Test
    fun `Archivieren in the menu archives and closes`() {
        save(Vessel("k", "Kyusu 250 ml", VesselType.KYUSU, 300, 250))
        var archived: ArchivedVessel? = null
        showEditor("k", onArchive = { archived = it })

        compose.onNodeWithTag(VesselEditorTags.MORE).performClick()
        compose.onNodeWithText("Archivieren").performClick()
        compose.waitForIdle()

        assertThat(archived).isEqualTo(ArchivedVessel("k", "Kyusu 250 ml"))
        assertThat(stored().single().isArchived).isTrue()
    }

    @Test
    fun `an archived vessel offers Wiederherstellen in the menu`() {
        save(Vessel("k", "Kyusu 250 ml", VesselType.KYUSU, 300, isArchived = true))
        showEditor("k")

        compose.onNodeWithTag(VesselEditorTags.MORE).performClick()
        compose.onAllNodesWithText("Archivieren").fetchSemanticsNodes().let { assertThat(it).isEmpty() }
        compose.onNodeWithText("Wiederherstellen").performClick()
        compose.waitForIdle()

        assertThat(stored().single().isArchived).isFalse()
    }

    @Test
    fun `a new vessel has no menu`() {
        showEditor()

        compose.onNodeWithTag(VesselEditorTags.MORE).assertDoesNotExist()
    }

    @Test
    fun `the blocked Archivieren is greyed out with Erst die Sitzung beenden`() {
        compose.setContent {
            UmamiTheme(darkTheme = false, reduceMotion = true) {
                VesselEditorScreen(
                    state = VesselEditorUiState.Ready(
                        form = VesselForm(name = "Kyusu", capacity = "300"),
                        isNew = false,
                        isArchived = false,
                        archiveBlocked = true,
                        hasChanges = false,
                        errors = emptySet(),
                    ),
                    actions = VesselEditorActions(),
                )
            }
        }

        compose.onNodeWithTag(VesselEditorTags.MORE).performClick()

        compose.onNodeWithText("Erst die Sitzung beenden").assertIsDisplayed()
        compose.onNodeWithText("Archivieren").assertIsNotEnabled()
    }

    @Test
    fun `the list shows a vessel's photo in place of its type icon, TalkBack reads it`() {
        files.write("p1", byteArrayOf(1))
        save(
            Vessel("k", "Kyusu 250 ml", VesselType.KYUSU, 300, coverPhotoId = "p1"),
            Vessel("g", "Gaiwan", VesselType.GAIWAN, 120),
        )
        showList()

        compose.onNodeWithContentDescription("Foto von Kyusu 250 ml").assertIsDisplayed()
        compose.onAllNodesWithTag(TeawareTags.PHOTO, useUnmergedTree = true).assertCountEquals(1)
        // The vessel with a photo has no type icon; the one without keeps it.
        compose.onNodeWithContentDescription("Kyusu").assertDoesNotExist()
        compose.onNodeWithContentDescription("Gaiwan").assertIsDisplayed()
    }

    @Test
    fun `the editor without a photo offers Foto hinzufuegen, picks one and stores it with Speichern`() {
        save(Vessel("k", "Kyusu 250 ml", VesselType.KYUSU, 300))
        val picker = FakePhotoPicker(Uri.parse("content://picked/1"))
        showEditor("k", picker = picker)

        compose.onNodeWithContentDescription("Noch kein Foto").assertIsDisplayed()
        compose.onNodeWithTag(VesselEditorTags.PHOTO_REMOVE).assertDoesNotExist()
        compose.onNodeWithText("Foto hinzufügen").performClick()
        compose.waitForIdle()

        assertThat(picker.launches).isEqualTo(1)
        compose.onNodeWithContentDescription("Foto des Gefäßes").assertIsDisplayed()
        compose.onNodeWithText("Foto ersetzen").assertIsDisplayed()
        assertThat(photos.set).isEmpty()

        compose.onNodeWithTag(VesselEditorTags.SAVE).performClick()
        compose.waitForIdle()

        assertThat(photos.set).containsExactly("content://picked/1")
        assertThat(stored().single().coverPhotoId).isNotNull()
    }

    @Test
    fun `Foto ersetzen replaces the stored photo and Foto entfernen removes it`() {
        save(Vessel("k", "Kyusu 250 ml", VesselType.KYUSU, 300))
        runBlocking { photos.set("k", "content://old") }
        val old = stored().single().coverPhotoId!!
        val picker = FakePhotoPicker(Uri.parse("content://picked/2"))
        showEditor("k", picker = picker)

        compose.onNodeWithContentDescription("Foto des Gefäßes").assertIsDisplayed()
        compose.onNodeWithText("Foto ersetzen").performClick()
        compose.onNodeWithTag(VesselEditorTags.SAVE).performClick()
        compose.waitForIdle()

        val replaced = stored().single().coverPhotoId!!
        assertThat(replaced).isNotEqualTo(old)
        assertThat(files.file(old).exists()).isFalse()
        assertThat(files.file(replaced).exists()).isTrue()
    }

    @Test
    fun `Foto entfernen takes the photo away with Speichern`() {
        save(Vessel("k", "Kyusu 250 ml", VesselType.KYUSU, 300))
        runBlocking { photos.set("k", "content://old") }
        val old = stored().single().coverPhotoId!!
        showEditor("k")

        compose.onNodeWithText("Foto entfernen").performClick()
        compose.onNodeWithContentDescription("Noch kein Foto").assertIsDisplayed()
        assertThat(stored().single().coverPhotoId).isEqualTo(old)

        compose.onNodeWithTag(VesselEditorTags.SAVE).performClick()
        compose.waitForIdle()

        assertThat(stored().single().coverPhotoId).isNull()
        assertThat(files.file(old).exists()).isFalse()
    }

    @Test
    fun `leaving the editor after picking a photo asks Aenderungen verwerfen and stores nothing`() {
        save(Vessel("k", "Kyusu 250 ml", VesselType.KYUSU, 300))
        var closed = false
        showEditor("k", onClose = { closed = true }, picker = FakePhotoPicker(Uri.parse("content://picked/3")))

        compose.onNodeWithText("Foto hinzufügen").performClick()
        compose.onNodeWithContentDescription("Schließen").performClick()

        compose.onNodeWithText("Änderungen verwerfen?").assertIsDisplayed()
        assertThat(closed).isFalse()
        assertThat(photos.set).isEmpty()
    }

    private companion object {
        /** A plain washi-green square in place of a photo (D-016: no real photos in tests or renders). */
        const val PLACEHOLDER = 0xFF8A9A7B
    }
}
