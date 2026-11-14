package com.umamit.feature.journal.session

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.BrewMethod
import com.umamit.core.ui.brewingRatingStarTag
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

    private val page = mutableStateOf(basePage(rating = null, brewingRating = null))
    private val written = mutableListOf<Double?>()
    private val brewingWritten = mutableListOf<Double?>()

    private fun basePage(rating: Double?, brewingRating: Double? = null) = SessionPage(
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
        brewingRating = brewingRating,
    )

    private fun show() = compose.setContent {
        UmamiTheme(reduceMotion = true) {
            TastingInputs(
                page.value,
                SessionPageActions(setRating = { written += it }, setBrewingRating = {
                    brewingWritten +=
                        it
                }),
            )
        }
    }

    private fun tap(star: Int) {
        compose.onNodeWithTag(ratingStarTag(star), useUnmergedTree = true).performClick()
        compose.waitForIdle()
    }

    private fun tapBrewing(star: Int) {
        compose.onNodeWithTag(brewingRatingStarTag(star), useUnmergedTree = true).performClick()
        compose.waitForIdle()
    }

    private fun emitStored(rating: Double?, brewingRating: Double? = null) {
        compose.runOnUiThread { page.value = basePage(rating, brewingRating) }
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

    @Test
    fun `the two rows keep their own shown values when the other one is stored (D-117)`() {
        show()
        tap(4)
        tapBrewing(2)
        // Only the Tee write is stored so far: the Zubereitung stars must not flick back to empty.
        emitStored(4.0, null)
        tapBrewing(2)
        assertThat(brewingWritten).containsExactly(2.0, 1.5).inOrder()
        assertThat(written).containsExactly(4.0)
        emitStored(4.0, 1.5)
        tap(4)
        assertThat(written).containsExactly(4.0, 3.5).inOrder()
    }

    @Test
    fun `a stale stored Zubereitung value between taps does not change the next tap`() {
        show()
        tapBrewing(4)
        tapBrewing(4)
        emitStored(null, 4.0)
        tapBrewing(4)
        assertThat(brewingWritten).containsExactly(4.0, 3.5, null).inOrder()
        assertThat(written).isEmpty()
    }
}
