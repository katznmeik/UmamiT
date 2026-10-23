package com.umamit.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.umamit.feature.home.R as HomeR

/**
 * Launch smoke test (M1-02, #12; plan M1-timer.md §3.1 seam 2): the app starts its launcher
 * activity and opens on Start, the first of the four tabs (M1-05), with Start's own content. Runs
 * only in the CI emulator job (API 35 and 36); the cloud container has no KVM.
 *
 * It asserts what it means, independent of Start's data: the Start tab selected, Start's title,
 * and the *Schnelltimer* entry, which Start always shows, scrolled to (what else Start shows,
 * such as the reliability banner or a session's card, varies, and its lists load from Room after
 * the first frame). The setup is marked done first, so the first-start setup (D-066) is never the
 * start destination here.
 */
@RunWith(AndroidJUnit4::class)
class LaunchSmokeTest {

    @get:Rule(order = 0)
    val setupDone = SetupDoneRule()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchOpensOnStart() {
        // Read the texts from the resources, so the test follows strings.xml.
        val title = composeRule.activity.getString(HomeR.string.home_title)
        val quickTimer = composeRule.activity.getString(HomeR.string.home_quick_timer)

        composeRule.onNodeWithTag(tabTag(Tab.Start)).assertIsSelected()
        composeRule.onNodeWithText(title).assertIsDisplayed()
        // The Schnelltimer entry is always on Start, loaded or not, with or without a collection.
        composeRule.waitUntil(DATA_TIMEOUT_MS) {
            composeRule.onAllNodesWithText(quickTimer).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText(quickTimer).onFirst().performScrollTo().assertIsDisplayed()
    }

    private companion object {
        const val DATA_TIMEOUT_MS = 5_000L
    }
}
