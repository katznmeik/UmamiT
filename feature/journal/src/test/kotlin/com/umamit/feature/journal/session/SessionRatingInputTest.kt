package com.umamit.feature.journal.session

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.BrewMethod
import com.umamit.core.ui.ratingStarTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/**
 * The stars of the session page (JRN-02): the shown value follows the taps until the store shows exactly it, so an
 * older stored value arriving while a later write is queued changes neither the stars nor the next tap.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class SessionRatingInputTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val page = mutableStateOf(basePage(rating = null))
    private val written = mutableListOf<Double?>()

    private fun basePage(rating: Double?) = SessionPage(
        sessionId = "s1",
        teaId = null,
        teaName = "Sencha Morgentau",
        teaSecondaryName = null,
        styleKey = null,
        tea = SessionTea.AVAILABLE,
        startedAt = LocalDateTime.of(2026, 10, 22, 9, 12),
        method = BrewMethod.KYUSU,
        vesselName = null,
        leafGrams = null,
        waterMl = null,
        steps = emptyList(),
        rating = rating,
    )

    private fun show() = compose.setContent {
        UmamiTheme(reduceMotion = true) {
            TastingInputs(page.value, SessionPageActions(setRating = { written += it }))
        }
    }

    private fun tap(star: Int) {
        compose.onNodeWithTag(ratingStarTag(star), useUnmergedTree = true).performClick()
        compose.waitForIdle()
    }

    private fun emitStored(rating: Double?) {
        compose.runOnUiThread { page.value = basePage(rating) }
        compose.waitForIdle()
    }

    @Test
    fun `a stale stored value between taps does not change the next tap`() {
        show()
        tap(4)
        tap(4)
        // The store shows the first write while the second is still queued.
        emitStored(4.0)
        tap(4)
        assertThat(written).containsExactly(4.0, 3.5, null).inOrder()
    }

    @Test
    fun `the stored value that equals the shown one hands the stars back to the store`() {
        show()
        tap(4)
        emitStored(4.0)
        emitStored(2.0) // changed elsewhere (the editor): the stars follow it again
        tap(2)
        assertThat(written).containsExactly(4.0, 1.5).inOrder()
    }
}
