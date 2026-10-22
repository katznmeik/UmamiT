package com.umamit.core.designsystem.motion

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.KeyframesSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VectorizedAnimationSpec
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The motion tokens match 03 §3.6 „Motion tokens". */
class UmamiMotionTest {

    private val calm = UmamiMotion(reduceMotion = false)
    private val reduced = UmamiMotion(reduceMotion = true)

    private fun FiniteAnimationSpec<Float>.millis(from: Float = 0f, to: Float = 1f): Long {
        val vectorized: VectorizedAnimationSpec<AnimationVector1D> = vectorize(Float.VectorConverter)
        val start = AnimationVector1D(from)
        val end = AnimationVector1D(to)
        return vectorized.getDurationNanos(start, end, AnimationVector1D(0f)) / 1_000_000
    }

    @Test
    fun `standard transition is a 300 ms soft ease-out`() {
        val spec = calm.standard<Float>() as TweenSpec
        assertThat(spec.durationMillis).isEqualTo(300)
        assertThat(spec.easing).isEqualTo(MotionTokens.CalmEaseOut)
    }

    @Test
    fun `emphasised transition takes 450 ms`() {
        assertThat((calm.emphasized<Float>() as TweenSpec).durationMillis).isEqualTo(450)
    }

    @Test
    fun `exit is faster than enter`() {
        assertThat((calm.exit<Float>() as TweenSpec).durationMillis).isEqualTo(200)
    }

    @Test
    fun `sheets and the active brew bar use critically damped springs`() {
        listOf(calm.sheet<Float>(), calm.activeBrewBar<Float>()).forEach { spec ->
            assertThat((spec as SpringSpec).dampingRatio).isEqualTo(1f)
        }
    }

    @Test
    fun `sheet spring settles faster than the active brew bar spring`() {
        assertThat(calm.sheet<Float>().millis()).isLessThan(calm.activeBrewBar<Float>().millis())
    }

    @Test
    fun `long steps update every 10 s with a 600 ms glide`() {
        assertThat(calm.longStepUpdateIntervalMillis).isEqualTo(10_000L)
        assertThat((calm.longStepGlide<Float>() as TweenSpec).durationMillis).isEqualTo(600)
    }

    @Test
    fun `pre-alert glow is one swell as long as the 0,8 s tick`() {
        val glow = calm.preAlertGlow as KeyframesSpec
        assertThat(glow.config.durationMillis).isEqualTo(800)
        assertThat(glow.millis()).isEqualTo(800)
    }

    @Test
    fun `alert pulse cycles every 1,5 s and never flashes`() {
        val pulse = calm.alertPulse as InfiniteRepeatableSpec
        val halfCycle = (pulse.animation as TweenSpec).durationMillis
        assertThat(pulse.repeatMode).isEqualTo(RepeatMode.Reverse)
        assertThat(halfCycle * 2).isEqualTo(1_500)
        // WCAG 2.3.1: at most 3 flashes per second; one full washi-matcha cycle per 1,5 s is 0,67 Hz.
        assertThat(1_000.0 / (halfCycle * 2)).isLessThan(3.0)
    }

    @Test
    fun `alert pulse starts at full matcha on the first frame of alerting`() {
        val pulse = checkNotNull(calm.alertPulse).vectorize(Float.VectorConverter)
        val washi = AnimationVector1D(0f)
        val matcha = AnimationVector1D(1f)

        assertThat(pulse.getValueFromNanos(0L, washi, matcha, washi).value).isEqualTo(1f)
        // Half a cycle later it has breathed out to washi, then back to matcha.
        assertThat(pulse.getValueFromNanos(750_000_000L, washi, matcha, washi).value).isWithin(1e-3f).of(0f)
        assertThat(pulse.getValueFromNanos(1_500_000_000L, washi, matcha, washi).value).isWithin(1e-3f).of(1f)
    }

    @Test
    fun `entering alerting is instant in both motion modes`() {
        assertThat(calm.alertEnter<Float>()).isInstanceOf(SnapSpec::class.java)
        assertThat(reduced.alertEnter<Float>()).isInstanceOf(SnapSpec::class.java)
    }

    @Test
    fun `release after a press is faster than the press`() {
        val release = calm.pressRelease<Float>()
        assertThat((release as SpringSpec).dampingRatio).isEqualTo(1f)
        // Over the real press distance: scale 0.97 back to 1.
        assertThat(release.millis(from = 0.97f, to = 1f)).isLessThan(calm.press<Float>().millis(from = 1f, to = 0.97f))
    }

    @Test
    fun `reduce motion makes functional motion instant`() {
        listOf(
            reduced.standard<Float>(),
            reduced.emphasized<Float>(),
            reduced.exit<Float>(),
            reduced.press<Float>(),
            reduced.pressRelease<Float>(),
            reduced.sheet<Float>(),
            reduced.activeBrewBar<Float>(),
            reduced.longStepGlide<Float>(),
        ).forEach { assertThat(it).isInstanceOf(SnapSpec::class.java) }
    }

    @Test
    fun `reduce motion keeps fades very short`() {
        assertThat(reduced.fade<Float>().millis()).isAtMost(100L)
    }

    @Test
    fun `reduce motion turns off the pre-alert glow and the alert pulse`() {
        assertThat(reduced.preAlertGlow).isNull()
        assertThat(reduced.alertPulse).isNull()
    }

    @Test
    fun `reduce motion follows only the system animator duration scale`() {
        assertThat(isReduceMotion(animatorDurationScale = 0f)).isTrue()
        assertThat(isReduceMotion(animatorDurationScale = 0.5f)).isFalse()
        assertThat(isReduceMotion(animatorDurationScale = 1f)).isFalse()
    }
}
