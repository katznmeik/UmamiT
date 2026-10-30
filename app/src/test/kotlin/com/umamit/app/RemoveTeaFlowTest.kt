package com.umamit.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.common.truth.Truth.assertThat
import com.umamit.core.common.MonotonicClock
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.SeedData
import com.umamit.core.data.SessionStart
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.StepKind
import com.umamit.core.testing.FakeBrewStarter
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeLowStockRepository
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeTeaArchive
import com.umamit.core.testing.FakeTimerReliability
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.StepRecord
import com.umamit.feature.brew.picker.TEA_PICKER_TAG
import com.umamit.feature.brew.picker.TeaPickerViewModel
import com.umamit.feature.home.HomeViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * *Aus Sammlung entfernen* from the tea picker (TEA-04, SCR-03, D-093 point 8): the tea leaves
 * the picker and Start's *Nochmal brühen* at once; *Rückgängig* brings it back. Real Start and
 * picker view models on fake repositories.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class RemoveTeaFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val folder = TemporaryFolder()

    private val collection = FakeCollection()
    private val sessions = FakeSessions(collection)
    private val archive = FakeTeaArchive(sessions)
    private val clock = object : MonotonicClock {
        override fun elapsedMs() = 0L

        override fun bootCount() = 1
    }

    /** The seed teas, Hisui brewed once. */
    private fun seed() = runBlocking {
        SeedData.teas.forEach { collection.teas.saveTea(it) }
        val snapshot = SessionSnapshot(
            SeedData.HISUI_TEA_ID, "Bio Sencha Hisui", null, "Sencha", "sencha", null, null, "Kyusu",
            BrewMethod.KYUSU, SeedData.KYUSU_VESSEL_ID, "Kyusu", 5.0, 250, 65,
        )
        val steps = listOf(PlannedStep(StepKind.INFUSION, 60, 65))
        sessions.sessions.startSession(SessionStart("last", snapshot, steps, 10, 1_000, "Europe/Berlin"))
        sessions.sessions.recordStep("last", 0, StepRecord(60, 1, 0, 0, 1, 2, false, false))
        sessions.sessions.completeSession("last", autoFinished = false)
    }

    private fun show() {
        val photos = PhotoFiles(folder.newFolder("photos"))
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                UmamitRoot(
                    activeBrewSource = NoActiveBrewSource,
                    journalSources = testJournalSources(sessions, collection.teas),
                    alertKey = MutableStateFlow(null),
                    startScreen = { tab ->
                        StartTabContent(
                            tab = tab,
                            homeViewModel = viewModel {
                                HomeViewModel(
                                    collection.teas,
                                    sessions.sessions,
                                    FakeBrewStarter(),
                                    clock,
                                    photos,
                                    FakeTimerReliability(),
                                    FakeLowStockRepository(),
                                )
                            },
                            pickerViewModel = viewModel {
                                TeaPickerViewModel(collection.teas, sessions.sessions, archive, photos)
                            },
                        )
                    },
                )
            }
        }
        compose.waitForIdle()
    }

    private val inPicker = hasAnyAncestor(hasTestTag(TEA_PICKER_TAG))

    @Test
    fun `a removed tea leaves the picker and Nochmal bruehen, Rueckgaengig brings it back`() {
        seed()
        show()
        compose.onNodeWithContentDescription("Nochmal brühen: Bio Sencha Hisui").assertExists()

        compose.onNodeWithText("Aufguss starten").performClick()
        compose.onAllNodes(hasText("Bio Sencha Hisui") and inPicker).onFirst().performTouchInput { longClick() }
        compose.onNodeWithText("Aus Sammlung entfernen").performClick()
        compose.waitForIdle()

        assertThat(archive.archived).containsExactly(SeedData.HISUI_TEA_ID)
        compose.onAllNodes(hasText("Bio Sencha Hisui") and inPicker).assertCountEquals(0)
        compose.onAllNodes(hasText("Bio Sencha Tanabata") and inPicker).onFirst().assertIsDisplayed()
        compose.onNodeWithContentDescription("Nochmal brühen: Bio Sencha Hisui").assertDoesNotExist()
        compose.onNodeWithText("Bio Sencha Hisui aus der Sammlung entfernt").assertIsDisplayed()

        compose.onNodeWithText("Rückgängig").performClick()
        compose.waitForIdle()

        compose.onAllNodes(hasText("Bio Sencha Hisui") and inPicker).onFirst().assertExists()
        compose.onNodeWithContentDescription("Nochmal brühen: Bio Sencha Hisui").assertExists()
        assertThat(runBlocking { collection.teas.getTea(SeedData.HISUI_TEA_ID)!!.isArchived }).isFalse()
    }
}
