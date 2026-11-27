package com.umamit.feature.onboarding.mockup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [HelpMockup] is static and reads as one picture (SCR-36 „Mock-ups"; ONB-08). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HelpMockupTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aTapReachesNothingInsideAndTalkBackReadsOnlyTheDescription() {
        var clicked = false
        compose.setContent {
            UmamiTheme(darkTheme = false, reduceMotion = true) {
                HelpMockup(description = "Bild: Test", size = HelpMockupSize.Tour) {
                    Text("Inside", Modifier.fillMaxSize().clickable { clicked = true })
                }
            }
        }

        compose.onNodeWithContentDescription("Bild: Test").performClick()

        assertThat(clicked).isFalse()
        compose.onAllNodesWithText("Inside").assertCountEquals(0)
    }
}
