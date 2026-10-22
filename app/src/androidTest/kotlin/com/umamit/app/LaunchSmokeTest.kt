package com.umamit.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.umamit.feature.home.R as HomeR

/**
 * Launch smoke test (M1-02, #12; plan M1-timer.md §3.1 seam 2): the app starts
 * its launcher activity and opens on Start, the first of the four tabs (M1-05). Runs only in
 * the CI emulator job (API 35 and 36); the cloud container has no KVM.
 */
@RunWith(AndroidJUnit4::class)
class LaunchSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchOpensOnStart() {
        // Read the texts from the resources, so the test follows strings.xml.
        val startBody = composeRule.activity.getString(HomeR.string.home_empty_title)

        composeRule.onNodeWithText(startBody).assertIsDisplayed()
        composeRule.onNodeWithTag(tabTag(Tab.Start)).assertIsSelected()
    }
}
