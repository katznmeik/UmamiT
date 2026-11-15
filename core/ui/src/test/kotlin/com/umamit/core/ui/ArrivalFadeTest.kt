package com.umamit.core.ui

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.designsystem.motion.MotionTokens
import com.umamit.core.designsystem.theme.UmamiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Hold, then show fresh for a screen's first data (03 §3.6, D-117 "Arrival fade", NFR-PERF-12): data that arrives
 * within [ARRIVAL_HOLD_MS] of the composition's first frame is drawn at full opacity at once; only a later first
 * arrival fades with `fade` (300 ms; reduce motion 100 ms). [fadeInOnArrival]'s `onFadeEnd` runs once either way.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ArrivalFadeTest {
    @get:Rule
    val compose = createComposeRule()

    private var arrived by mutableStateOf(false)
    private var alpha = -1f
    private var fadeEnds = 0

    private fun show(reduceMotion: Boolean = false) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            UmamiTheme(darkTheme = false, reduceMotion = reduceMotion) {
                val shown = rememberArrivalAlpha(arrived, onFadeEnd = { fadeEnds++ })
                val value = shown.value
                SideEffect { alpha = value }
            }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun arriveAt(ms: Long) {
        if (ms > 0) compose.mainClock.advanceTimeBy(ms)
        arrived = true
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
    }

    private fun settle() = compose.mainClock.advanceTimeBy(SETTLE_MS)

    @Test
    fun `data that arrives at once is drawn at full opacity`() {
        show()
        arriveAt(0)
        assertWithMessage("alpha right after the arrival").that(alpha).isEqualTo(1f)
        settle()
        assertWithMessage("onFadeEnd").that(fadeEnds).isEqualTo(1)
    }

    @Test
    fun `data that arrives within the hold is drawn at full opacity`() {
        show()
        arriveAt(100)
        assertWithMessage("alpha right after an arrival at 100 ms").that(alpha).isEqualTo(1f)
        settle()
        assertWithMessage("onFadeEnd").that(fadeEnds).isEqualTo(1)
    }

    @Test
    fun `data that arrives after the hold fades in with fade`() {
        show()
        arriveAt(200)
        assertWithMessage("alpha right after an arrival at 200 ms").that(alpha).isLessThan(1f)
        compose.mainClock.advanceTimeBy(HALF_FADE_MS)
        assertWithMessage("alpha 150 ms into the fade").that(alpha).isLessThan(1f)
        assertWithMessage("onFadeEnd during the fade").that(fadeEnds).isEqualTo(0)
        settle()
        assertWithMessage("alpha after the fade").that(alpha).isEqualTo(1f)
        assertWithMessage("onFadeEnd").that(fadeEnds).isEqualTo(1)
    }

    @Test
    fun `composed with the data it is drawn at once`() {
        arrived = true
        show()
        assertWithMessage("alpha in the first frame").that(alpha).isEqualTo(1f)
        settle()
        assertWithMessage("onFadeEnd").that(fadeEnds).isEqualTo(1)
    }

    @Test
    fun `under reduce motion a late arrival fades in 100 ms`() {
        show(reduceMotion = true)
        arriveAt(200)
        assertWithMessage("alpha right after an arrival at 200 ms").that(alpha).isLessThan(1f)
        compose.mainClock.advanceTimeBy(MotionTokens.REDUCED_FADE_MILLIS.toLong())
        assertWithMessage("alpha 100 ms into the fade").that(alpha).isEqualTo(1f)
        assertWithMessage("onFadeEnd").that(fadeEnds).isEqualTo(1)
        settle()
        assertWithMessage("onFadeEnd after settling").that(fadeEnds).isEqualTo(1)
    }

    private companion object {
        const val HALF_FADE_MS = 150L
        const val SETTLE_MS = 500L
    }
}
