package com.umamit.feature.brew.steps

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.StepKind
import com.umamit.core.timer.engine.PreAlertChoice
import com.umamit.core.timer.engine.StepStatus
import com.umamit.core.timer.engine.TimerEvent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The modal sheet around the content: showing, closing and keeping the edits (SCR-31). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class StepsSheetHostTest {

    @get:Rule
    val compose = createComposeRule()

    private val input = StepsSheetInput(
        steps = listOf(
            StepsSheetStep(StepKind.INFUSION, 60, 60, StepStatus.DONE),
            StepsSheetStep(StepKind.INFUSION, 30, 60, StepStatus.PENDING),
        ),
        preAlert = PreAlertChoice.Inherit,
        hasRecipe = true,
        baseTempC = 60,
    )

    private var visible by mutableStateOf(true)
    private val done = mutableListOf<TimerEvent.EditSteps>()
    private var dismissals = 0

    private fun showHost() {
        compose.setContent {
            UmamiTheme(reduceMotion = false) {
                Box {
                    StepsSheet(
                        visible = visible,
                        state = rememberStepsSheetState(input),
                        onDone = {
                            done += it
                            visible = false
                        },
                        onSaveToRecipe = {},
                        onDismissRequest = {
                            dismissals++
                            visible = false
                        },
                    )
                }
            }
        }
    }

    @Test
    fun `Fertig emits the request and the host hides the sheet`() {
        showHost()

        compose.onNodeWithText("Fertig").performClick()

        assertThat(done).hasSize(1)
        compose.onNodeWithText("Schritte für diese Sitzung").assertDoesNotExist()
        assertThat(dismissals).isEqualTo(0)
    }

    @Test
    fun `the handle closes the sheet without applying anything`() {
        showHost()

        compose.onNodeWithContentDescription("Schritte schließen").performClick()

        assertThat(dismissals).isEqualTo(1)
        assertThat(done).isEmpty()
        compose.onNodeWithText("Schritte für diese Sitzung").assertDoesNotExist()
    }

    @Test
    fun `a hidden sheet shows nothing`() {
        visible = false
        showHost()

        compose.onNodeWithText("Schritte für diese Sitzung").assertDoesNotExist()
    }

    @Test
    fun `the edits survive a configuration change`() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            UmamiTheme(reduceMotion = true) {
                StepsSheetContent(state = rememberStepsSheetState(input), onDone = { done += it }, onSaveToRecipe = {})
            }
        }
        compose.onNodeWithContentDescription("Ziehzeit verlängern, Schritt 2").performScrollTo().performClick()

        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Fertig").performClick()

        assertThat(done.single().steps.last().plannedSeconds).isEqualTo(35)
    }
}
