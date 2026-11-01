package com.umamit.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.component.ActiveBrew
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.Vessel
import com.umamit.core.model.VesselType
import com.umamit.core.testing.FakeCollection
import com.umamit.feature.settings.SettingsTags
import com.umamit.feature.teaware.ArchivedVessel
import com.umamit.feature.teaware.TeawareTags
import com.umamit.feature.teaware.VesselEditorResult
import com.umamit.feature.teaware.VesselEditorTags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * *Einstellungen › Teegeschirr* (SET-30, SCR-13) and the vessel editor (SCR-14) as routes of the shell, and the
 * editor's result contract for the setup's *Neues Gefäß …* (WARE-02, D-100).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TeawareNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val collection = FakeCollection()

    private val source = object : ActiveBrewSource {
        override val activeBrew: StateFlow<ActiveBrew?> = MutableStateFlow(null)
        override val progress: () -> Float = { 0f }
    }

    private fun start() {
        runBlocking { collection.vessels.saveVessel(Vessel("k", "Kyusu 250 ml", VesselType.KYUSU, 300, 250)) }
        compose.setContent {
            UmamiTheme(darkTheme = false, reduceMotion = true) {
                UmamitApp(
                    activeBrewSource = source,
                    journalSources = testJournalSources(),
                    settings = testSettingsScreens(collection),
                    catalogScreen = { PlaceholderCatalog(it) },
                )
            }
        }
    }

    private fun openTeaware() {
        compose.onNodeWithContentDescription("Einstellungen").performClick()
        compose.onNodeWithTag(SettingsTags.TEAWARE).performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun stored(): List<Vessel> = runBlocking { collection.vessels.observeVessels().first() }

    @Test
    fun `Einstellungen Teegeschirr lists the vessels, and a new vessel saved there joins the list`() {
        start()
        openTeaware()
        compose.onNodeWithText("Kyusu 250 ml").assertIsDisplayed()

        compose.onNodeWithTag(TeawareTags.ADD).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Neues Gefäß").assertIsDisplayed()
        compose.onNodeWithTag(VesselEditorTags.NAME).performTextInput("Gaiwan")
        compose.onNodeWithTag(VesselEditorTags.CAPACITY).performTextInput("120")
        compose.onNodeWithTag(VesselEditorTags.SAVE).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Gaiwan").assertIsDisplayed()
        compose.onNodeWithText("120 ml").assertIsDisplayed()
        assertThat(stored().map { it.name }).containsExactly("Gaiwan", "Kyusu 250 ml")
    }

    @Test
    fun `Archivieren in the editor returns to the list with Rueckgaengig`() {
        start()
        openTeaware()

        compose.onNodeWithText("Kyusu 250 ml").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Gefäß bearbeiten").assertIsDisplayed()
        compose.onNodeWithTag(VesselEditorTags.MORE).performClick()
        compose.onNodeWithText("Archivieren").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Kyusu 250 ml archiviert").assertIsDisplayed()
        compose.onNodeWithText("ARCHIVIERT").assertIsDisplayed()
        compose.onNodeWithText("Rückgängig").performClick()
        compose.waitForIdle()

        assertThat(stored().single().isArchived).isFalse()
        compose.onNodeWithText("ARCHIVIERT").assertDoesNotExist()
    }

    @Test
    fun `the editor closes with the saved vessel's id in the entry below`() {
        compose.setContent {
            val nav = rememberNavController()
            NavHost(nav, startDestination = EditorResultBelow) {
                composable<EditorResultBelow> { entry ->
                    val saved by entry.savedStateHandle
                        .getStateFlow<String?>(VesselEditorResult.SAVED_VESSEL_ID, null)
                        .collectAsStateWithLifecycle()
                    Text("saved=$saved")
                    val archived = entry.archivedVessel()
                    Text("archived=${archived?.name}")
                }
                composable<VesselEditorRoute> { Text("editor") }
            }
            LaunchedEffect(nav) {
                controller = nav
                nav.navigate(VesselEditorRoute())
            }
        }
        compose.onNodeWithText("editor").assertIsDisplayed()

        compose.runOnUiThread { controller!!.closeVesselEditor(savedVesselId = "v9") }
        compose.waitForIdle()
        compose.onNodeWithText("saved=v9").assertIsDisplayed()

        // Only the editor on top is closed: a late second result pops nothing.
        compose.runOnUiThread { controller!!.closeVesselEditor(archived = ArchivedVessel("v9", "Gaiwan")) }
        compose.waitForIdle()
        compose.onNodeWithText("saved=v9").assertIsDisplayed()
        compose.onNodeWithText("archived=null").assertIsDisplayed()
    }

    private var controller: NavHostController? = null
}

/** The screen below the editor in the result test (a route object must be visible to the serializer). */
@Serializable
internal data object EditorResultBelow
