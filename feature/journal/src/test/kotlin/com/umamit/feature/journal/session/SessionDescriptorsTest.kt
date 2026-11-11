package com.umamit.feature.journal.session

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.SessionStart
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.StepKind
import com.umamit.core.model.TasteDescriptor
import com.umamit.core.model.TastingLimits
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeTeaArchive
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.feature.journal.JournalSessions
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/**
 * BESCHREIBUNGEN and the descriptor sheet (SCR-16, SCR-30, JRN-04, D-112) with the real view model on the fakes of
 * core:testing (seam 2). An invented tea (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class SessionDescriptorsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val collection = FakeCollection()
    private val sessions = FakeSessions(collection)
    private val archive = FakeTeaArchive(sessions)
    private val alert = mutableStateOf<String?>(null)

    @Before
    fun seed() = runBlocking {
        collection.teas.saveTea(Tea(TEA, "Sencha Morgentau", category = TeaCategory.GREEN, form = TeaForm.LOOSE_LEAF))
        val snapshot = SessionSnapshot(
            teaId = TEA,
            teaName = "Sencha Morgentau",
            teaSecondaryName = null,
            teaType = "Sencha",
            styleKey = "sencha",
            category = TeaCategory.GREEN,
            profileId = "p",
            profileName = "Kräftig",
            method = BrewMethod.KYUSU,
            vesselId = "kyusu",
            vesselName = "Kyusu 250 ml",
            leafGrams = 4.5,
            waterMl = 180,
            baseTempC = 65,
        )
        sessions.sessions.startSession(
            SessionStart(
                PAST,
                snapshot,
                listOf(PlannedStep(StepKind.INFUSION, 75, null)),
                10,
                STARTED,
                "Europe/Berlin",
            ),
        )
        sessions.sessions.completeSession(PAST, autoFinished = false)
    }

    private fun show() {
        val viewModel = SessionPageViewModel(
            SavedStateHandle(mapOf("sessionId" to PAST)),
            sessions.journal,
            collection.teas,
            archive,
            sessions.tasting,
        )
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                SessionPageRoute(
                    host = SessionPageHost(onBack = {}, onOpenTea = {}, onBrewAgain = {}, alertKey = alert.value),
                    viewModel = viewModel,
                )
            }
        }
        compose.waitForIdle()
    }

    private fun keys() = runBlocking { sessions.sessions.getSession(PAST)!!.descriptorKeys }

    private fun openSheet() {
        compose.onNodeWithTag(SESSION_DESCRIPTORS_ADD_TAG).performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun chip(descriptor: TasteDescriptor) = compose.onNodeWithTag(descriptorSheetChipTag(descriptor))

    @Test
    fun `the section starts with only the add chip and opening the sheet writes nothing`() {
        show()

        compose.onNodeWithTag(SESSION_DESCRIPTORS_ADD_TAG).performScrollTo().assertIsDisplayed()
        openSheet()
        compose.onNodeWithTag(DESCRIPTOR_SHEET_TAG).assertIsDisplayed()
        compose.onNodeWithText("0 von 20").assertIsDisplayed()
        assertThat(keys()).isEmpty()
    }

    @Test
    fun `a tick persists and shows as a chip on the page, an untick removes it`() {
        show()
        openSheet()

        chip(TasteDescriptor.MARINE_NORI).performClick()
        compose.waitForIdle()
        assertThat(keys()).containsExactly("marine.nori")
        compose.onNodeWithText("1 von 20").assertIsDisplayed()

        chip(TasteDescriptor.MARINE_NORI).performClick()
        compose.waitForIdle()
        assertThat(keys()).isEmpty()

        chip(TasteDescriptor.MARINE_NORI).performClick()
        compose.onNodeWithTag(DESCRIPTOR_DONE_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(DESCRIPTOR_SHEET_TAG).assertDoesNotExist()
        compose.onNodeWithTag(sessionDescriptorTag(TasteDescriptor.MARINE_NORI)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a tap on a chosen chip of the page opens the sheet and removes nothing`() {
        runBlocking { sessions.tasting.setDescriptor(PAST, "marine.nori", true) }
        show()

        compose.onNodeWithTag(sessionDescriptorTag(TasteDescriptor.MARINE_NORI)).performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(DESCRIPTOR_SHEET_TAG).assertIsDisplayed()
        assertThat(keys()).containsExactly("marine.nori")
    }

    @Test
    fun `the search narrows the groups`() {
        show()
        openSheet()

        compose.onNodeWithTag(DESCRIPTOR_SEARCH_TAG).performTextInput("nor")
        compose.waitForIdle()

        chip(TasteDescriptor.MARINE_NORI).assertIsDisplayed()
        chip(TasteDescriptor.MARINE_ALGEN).assertDoesNotExist()
        chip(TasteDescriptor.FLORAL_JASMIN).assertDoesNotExist()
    }

    @Test
    fun `a search without a match says so`() {
        show()
        openSheet()

        compose.onNodeWithTag(DESCRIPTOR_SEARCH_TAG).performTextInput("zzzz")
        compose.waitForIdle()

        compose.onNodeWithText("Keine Beschreibung gefunden").assertIsDisplayed()
    }

    @Test
    fun `at 20 the others are greyed, the count says so and a 21st is not written`() {
        val vocabulary = TasteDescriptor.entries
        runBlocking {
            vocabulary.take(TastingLimits.MAX_DESCRIPTORS).forEach {
                sessions.tasting.setDescriptor(PAST, it.key, true)
            }
        }
        show()
        openSheet()

        compose.onNodeWithText("20 von 20 · Höchstens 20 Beschreibungen").assertIsDisplayed()
        val twentyFirst = vocabulary[TastingLimits.MAX_DESCRIPTORS]
        chip(twentyFirst).performScrollTo().assertIsNotEnabled().performClick()
        compose.waitForIdle()
        assertThat(keys()).hasSize(TastingLimits.MAX_DESCRIPTORS)
        assertThat(keys()).doesNotContain(twentyFirst.key)

        // A ticked chip stays enabled, so a place can be freed.
        chip(vocabulary.first()).performScrollTo().assertIsEnabled().performClick()
        compose.waitForIdle()
        chip(twentyFirst).performScrollTo().assertIsEnabled()
        compose.onNodeWithText("19 von 20").assertIsDisplayed()
    }

    @Test
    fun `the repository refuses a 21st key and the view model leaves the 20 as they are`() {
        val vocabulary = TasteDescriptor.entries
        runBlocking {
            vocabulary.take(TastingLimits.MAX_DESCRIPTORS).forEach {
                sessions.tasting.setDescriptor(PAST, it.key, true)
            }
        }
        val viewModel = SessionPageViewModel(
            SavedStateHandle(mapOf("sessionId" to PAST)),
            sessions.journal,
            collection.teas,
            archive,
            sessions.tasting,
        )

        viewModel.setDescriptor(vocabulary[TastingLimits.MAX_DESCRIPTORS].key, true)

        assertThat(keys()).hasSize(TastingLimits.MAX_DESCRIPTORS)
    }

    @Test
    fun `Fertig stays in view at any scroll position`() {
        show()
        openSheet()

        compose.onNodeWithTag(DESCRIPTOR_DONE_TAG).assertIsDisplayed()
        chip(TasteDescriptor.OFF_NOTE_SAUER).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(DESCRIPTOR_DONE_TAG).assertIsDisplayed()
        compose.onNodeWithTag(DESCRIPTOR_COUNT_TAG).assertIsDisplayed()
    }

    @Test
    fun `chips are toggles for TalkBack`() {
        runBlocking { sessions.tasting.setDescriptor(PAST, "marine.nori", true) }
        show()
        openSheet()

        compose.onNodeWithTag(descriptorSheetChipTag(TasteDescriptor.MARINE_NORI))
            .assert(hasStateDescription("ausgewählt"))
        compose.onNodeWithTag(descriptorSheetChipTag(TasteDescriptor.MARINE_ALGEN))
            .assert(hasStateDescription("nicht ausgewählt"))
    }

    @Test
    fun `an alert closes the sheet in its first frame and a started write completes`() {
        show()
        openSheet()
        chip(TasteDescriptor.FLORAL_JASMIN).performClick()

        compose.runOnUiThread { alert.value = "alert-1" }
        compose.waitForIdle()

        compose.onNodeWithTag(DESCRIPTOR_SHEET_TAG).assertDoesNotExist()
        assertThat(keys()).containsExactly("floral.jasmin")

        // It does not come back after the alert.
        compose.runOnUiThread { alert.value = null }
        compose.waitForIdle()
        compose.onNodeWithTag(DESCRIPTOR_SHEET_TAG).assertDoesNotExist()
    }

    @Test
    fun `a key the vocabulary lacks is kept, not shown, and counts towards the 20`() {
        runBlocking { sessions.tasting.setDescriptor(PAST, "future.thing", true) }
        show()
        openSheet()

        compose.onNodeWithText("1 von 20").assertIsDisplayed()
        chip(TasteDescriptor.MARINE_NORI).performClick()
        compose.waitForIdle()
        assertThat(keys()).containsExactly("future.thing", "marine.nori").inOrder()
    }

    private companion object {
        const val TEA = "morgentau"
        const val PAST = "past"
        val STARTED = JournalSessions.ms(LocalDateTime.of(2026, 10, 22, 9, 12))
    }
}
