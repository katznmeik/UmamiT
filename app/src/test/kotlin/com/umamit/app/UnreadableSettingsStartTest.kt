package com.umamit.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.datastore.core.DataStoreFactory
import com.umamit.core.data.DataStoreSettingsRepository
import com.umamit.core.datastore.SettingsDataSource
import com.umamit.core.datastore.SettingsSerializer
import com.umamit.core.designsystem.theme.UmamiTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * An unreadable settings file opens the app on Start (D-066, D-068; #54 re-review R2-1): the
 * onboarding decision of [MainActivity] on the real settings repository, whose file DataStore
 * cannot read, never shows the first-start setup, so Start and the timer stay reachable. A fresh
 * install (no file) still opens the setup.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class UnreadableSettingsStartTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() = scope.cancel()

    private fun onboardingCompleted(file: File): StateFlow<Boolean?> {
        val repository = DataStoreSettingsRepository(
            SettingsDataSource(DataStoreFactory.create(SettingsSerializer, scope = scope) { file }),
        )
        return repository.onboardingCompleted().stateIn(scope, SharingStarted.Eagerly, null)
    }

    private fun show(onboardingCompleted: StateFlow<Boolean?>) = compose.setContent {
        UmamiTheme(reduceMotion = true) {
            UmamitRoot(
                activeBrewSource = NoActiveBrewSource,
                journalSources = testJournalSources(),
                alertKey = MutableStateFlow(null),
                onboardingCompleted = onboardingCompleted,
                onboardingScreen = { Text("EINRICHTUNG") },
                timerScreen = { _, _, _, _ -> Text("TIMER") },
                startScreen = { Text("START") },
                settings = testSettingsScreens(),
            )
        }
    }

    @Test
    fun anUnreadableSettingsFileOpensOnStart() {
        // A directory where the file should be: DataStore's read throws an IOException.
        show(onboardingCompleted(File(folder.root, "settings.json").apply { mkdirs() }))

        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithTextCount("START") > 0 }
        compose.onNodeWithText("START").assertIsDisplayed()
        compose.onNodeWithText("EINRICHTUNG").assertDoesNotExist()
    }

    @Test
    fun aFreshInstallStillOpensTheSetup() {
        show(onboardingCompleted(File(folder.root, "settings.json")))

        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithTextCount("EINRICHTUNG") > 0 }
        compose.onNodeWithText("START").assertDoesNotExist()
    }

    private fun AndroidComposeTestRule<*, *>.onAllNodesWithTextCount(text: String) =
        onAllNodes(hasText(text)).fetchSemanticsNodes().size

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
