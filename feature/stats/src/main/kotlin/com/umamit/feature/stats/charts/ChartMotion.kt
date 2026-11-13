package com.umamit.feature.stats.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.umamit.core.designsystem.motion.LocalReduceMotion
import com.umamit.core.designsystem.motion.MotionTokens
import kotlin.math.min

/** One bar (or point) grows in the standard 300 ms (03 §3.6, INS-08). */
private const val GROW_MILLIS = MotionTokens.STANDARD_MILLIS.toFloat()

/** Charts stagger their bars by at most 20 ms each, and spread the stagger over at most 300 ms in all. */
private const val MAX_STAGGER_MILLIS = 20f
private const val MAX_STAGGER_SPREAD_MILLIS = 300f

/** A range change morphs the heights in about 250 ms (SCR-18 Motion). */
private const val MORPH_MILLIS = 250

/**
 * The first-show reveal of a chart (INS-08, 03 §3.6): every bar grows from the baseline over ≈ 300 ms with
 * `CalmEaseOut` (the app's emphasized-decelerate ease), staggered by ≤ 20 ms; under reduce motion the bars
 * stand at full height at once and only the whole chart fades in over 100 ms.
 */
@Stable
class ChartReveal internal constructor(
    /** Opacity of the whole chart: 1 while growing, a 100 ms fade under reduce motion. */
    val alpha: Float,
    private val elapsedMillis: Float,
    private val staggerMillis: Float,
    private val settled: Boolean,
) {
    /** How far item [index] has grown, 0 (on the baseline) to 1. */
    fun fraction(index: Int): Float = if (settled) {
        1f
    } else {
        MotionTokens.CalmEaseOut.transform(((elapsedMillis - index * staggerMillis) / GROW_MILLIS).coerceIn(0f, 1f))
    }

    internal companion object {
        fun staggerFor(count: Int): Float =
            if (count <= 1) 0f else min(MAX_STAGGER_MILLIS, MAX_STAGGER_SPREAD_MILLIS / (count - 1))

        fun totalMillis(count: Int): Float = GROW_MILLIS + staggerFor(count) * (count - 1).coerceAtLeast(0)
    }
}

/**
 * Plays the reveal once when the chart first enters the composition. [animateIn] false (the tab was
 * shown before in this visit) starts settled. [reduceMotion] is the app's existing check, the
 * theme's `LocalReduceMotion`.
 */
@Composable
internal fun rememberChartReveal(
    count: Int,
    animateIn: Boolean = true,
    reduceMotion: Boolean = LocalReduceMotion.current,
): ChartReveal {
    val progress = remember { Animatable(if (animateIn) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (progress.value >= 1f) return@LaunchedEffect
        if (reduceMotion) {
            progress.animateTo(1f, tween(MotionTokens.REDUCED_FADE_MILLIS, easing = LinearEasing))
        } else {
            progress.animateTo(1f, tween(ChartReveal.totalMillis(count).toInt(), easing = LinearEasing))
        }
    }
    return if (reduceMotion) {
        ChartReveal(alpha = progress.value, elapsedMillis = 0f, staggerMillis = 0f, settled = true)
    } else {
        val total = ChartReveal.totalMillis(count)
        ChartReveal(1f, progress.value * total, ChartReveal.staggerFor(count), settled = progress.value >= 1f)
    }
}

/**
 * [target] as it should be drawn now: a change of the values (a range change) morphs from the shown
 * values to the new ones in ≈ 250 ms; lists of different length line up at their end (the latest period
 * stays the latest). Under [reduceMotion] the new values apply at once.
 */
@Composable
internal fun rememberMorphed(target: List<Float>, reduceMotion: Boolean = LocalReduceMotion.current): List<Float> {
    val state = remember { MorphState(target) }
    var version by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(target, reduceMotion) {
        if (target == state.to) return@LaunchedEffect
        state.from = lerpAligned(state.from, state.to, progress.value, target.size)
        state.to = target
        version++
        if (reduceMotion) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(MORPH_MILLIS, easing = MotionTokens.CalmEaseOut))
        }
    }
    check(version >= 0) // read, so a change of the values recomposes
    // One frame before the effect runs, the old values stay (resized), never a jump to the new ones.
    return lerpAligned(state.from, state.to, progress.value, target.size)
}

/** The values a morph starts from and runs to; a plain holder, the animation's progress drives the frames. */
private class MorphState(var to: List<Float>) {
    var from: List<Float> = to
}

/** The values between [from] and [to] at [t], both aligned at their end and resized to [size]. */
internal fun lerpAligned(from: List<Float>, to: List<Float>, t: Float, size: Int): List<Float> = List(size) { index ->
    val toValue = to.getOrNull(index - (size - to.size)) ?: 0f
    val fromValue = from.getOrNull(index - (size - from.size)) ?: 0f
    fromValue + (toValue - fromValue) * t
}
