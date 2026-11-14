package com.umamit.feature.stats.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import com.umamit.core.designsystem.motion.LocalReduceMotion
import com.umamit.core.designsystem.motion.MotionTokens
import kotlin.math.min
import kotlin.math.roundToInt

/** One bar (or point) grows in the standard 300 ms (03 §3.6, INS-08). */
private const val GROW_MILLIS = MotionTokens.STANDARD_MILLIS.toFloat()

/** Charts stagger their bars by at most 20 ms each, and spread the stagger over at most 120 ms in all (≤ 420 ms). */
private const val MAX_STAGGER_MILLIS = 20f
private const val MAX_STAGGER_SPREAD_MILLIS = 120f

/** A range change morphs the heights in about 250 ms (SCR-18 Motion); a new set of periods grows in as long. */
private const val MORPH_MILLIS = 250

/** When the number of periods changes, the old bars leave first, quickly (the system answers). */
private const val FADE_OUT_MILLIS = 100

/** A chart counts as seen once this share of it is inside the page's viewport (INS-08). */
private const val VISIBLE_SHARE = 0.5f

/**
 * The first-show reveal of a chart (INS-08, 03 §3.6): every bar grows from the baseline over ≈ 300 ms with
 * `CalmEaseOut` (the app's emphasized-decelerate ease), staggered by ≤ 20 ms; under reduce motion the bars
 * stand at full height at once and only the whole chart fades in over 100 ms.
 *
 * The animated values are read through [alpha] and [fraction], which belong in draw and graphics-layer
 * lambdas: the chart does not recompose per frame.
 */
@Stable
class ChartReveal internal constructor(startSettled: Boolean) {
    internal val clock = Animatable(0f)
    internal var done by mutableStateOf(startSettled)
    internal var started by mutableStateOf(false)
    internal var startedCount = 0
    internal var count by mutableIntStateOf(0)
    internal var reduceMotion by mutableStateOf(false)
    internal var bounds by mutableStateOf(Rect.Zero)

    /** Opacity of the whole chart: 1 while growing, a 100 ms fade under reduce motion. */
    val alpha: Float
        get() = when {
            done || !reduceMotion -> 1f
            else -> (clock.value / MotionTokens.REDUCED_FADE_MILLIS).coerceIn(0f, 1f)
        }

    /** How far item [index] has grown, 0 (on the baseline) to 1. */
    fun fraction(index: Int): Float = if (done || reduceMotion) {
        1f
    } else {
        val stagger = staggerFor(count)
        MotionTokens.CalmEaseOut.transform(((clock.value - index * stagger) / GROW_MILLIS).coerceIn(0f, 1f))
    }

    internal companion object {
        fun staggerFor(count: Int): Float =
            if (count <= 1) 0f else min(MAX_STAGGER_MILLIS, MAX_STAGGER_SPREAD_MILLIS / (count - 1))

        fun totalMillis(count: Int): Float = GROW_MILLIS + staggerFor(count) * (count - 1).coerceAtLeast(0)
    }
}

/**
 * Plays the reveal of the chart [key] once: when its page is the settled one and at least half of the chart is
 * in the page's viewport (a chart below the fold waits until it is scrolled to). A chart the page has revealed
 * before (the tab was left and came back) starts settled. [animateIn] false starts settled. Without a
 * [LocalChartPage] (a chart on its own) the reveal starts at once. A change of [count] while the bars grow
 * settles them; [reduceMotion] is the app's existing check, the theme's `LocalReduceMotion`.
 */
@Composable
internal fun rememberChartReveal(
    count: Int,
    key: String,
    animateIn: Boolean = true,
    reduceMotion: Boolean = LocalReduceMotion.current,
): ChartReveal {
    val page = LocalChartPage.current
    val reveal = remember { ChartReveal(startSettled = !animateIn || page?.isRevealed(key) == true) }
    reveal.count = count
    reveal.reduceMotion = reduceMotion
    val visible by remember(page) { derivedStateOf { page == null || page.sees(reveal.bounds) } }
    val go = page == null || (page.active && visible)
    val run = go || reveal.started
    LaunchedEffect(run, reduceMotion) {
        if (!run || reveal.done) return@LaunchedEffect
        val total = if (reduceMotion) MotionTokens.REDUCED_FADE_MILLIS.toFloat() else ChartReveal.totalMillis(count)
        reveal.started = true
        reveal.startedCount = count
        if (page != null) page.growStarts++
        reveal.clock.snapTo(0f)
        reveal.clock.animateTo(total, tween(total.roundToInt(), easing = LinearEasing))
        reveal.done = true
        page?.markRevealed(key)
    }
    LaunchedEffect(count) {
        if (reveal.started && !reveal.done && count != reveal.startedCount) {
            reveal.done = true
            page?.markRevealed(key)
        }
    }
    return reveal
}

/** Tracks where the chart sits, for [rememberChartReveal]'s „at least half in view" start. */
internal fun Modifier.trackReveal(reveal: ChartReveal): Modifier =
    onGloballyPositioned { reveal.bounds = it.boundsInWindow() }

/** Whether at least half of [bounds] lies inside this page's viewport. */
internal fun ChartPageState.sees(bounds: Rect): Boolean {
    val view = viewport
    if (view.isEmpty || bounds.height <= 0f) return false
    val overlap = bounds.intersect(view)
    return overlap.height > 0f && overlap.height / bounds.height >= VISIBLE_SHARE
}

/** What a morph shows right now (see [ChartMorph]). */
private const val PHASE_LERP = 0
private const val PHASE_FADE_OUT = 1
private const val PHASE_GROW = 2

/**
 * The morph between two sets of bars (a range change, SCR-18). The bars are normalised fractions of their
 * axis, so the heights move together with the axis instead of being read against the new one at once. Same
 * number of periods: the fractions move over ≈ 250 ms. A different number (the index morph is undefined):
 * the old bars fade out in 100 ms, then the new set grows in, unstaggered, over 250 ms. [displayed] is the set the
 * chart lays out; it switches at the start of a morph, or after the fade-out. Under reduce motion everything
 * applies at once. [fraction] and [alpha] are read in draw only.
 */
@Stable
internal class ChartMorph<T>(initial: List<T>, private val fractionsOf: (List<T>) -> List<Float>) {
    var displayed by mutableStateOf(initial)
        private set
    private val progress = Animatable(1f)
    private var from: List<Float> by mutableStateOf(fractionsOf(initial))
    private var to: List<Float> by mutableStateOf(from)
    private var phase by mutableIntStateOf(PHASE_LERP)

    /** The height (0 to 1 of the axis) of item [index] now. */
    fun fraction(index: Int): Float = when (phase) {
        PHASE_LERP -> from.getOrElse(index) { 0f } + (to.getOrElse(index) { 0f } - from.getOrElse(index) { 0f }) *
            progress.value

        PHASE_FADE_OUT -> from.getOrElse(index) { 0f }

        else -> to.getOrElse(index) { 0f } * progress.value
    }

    /** The opacity of the bars: below 1 only while the old set fades out. */
    val alpha: Float get() = if (phase == PHASE_FADE_OUT) 1f - progress.value else 1f

    /** Runs the morph to [target]; cancelled by the next one, which starts from what is drawn. */
    suspend fun morphTo(target: List<T>, reduceMotion: Boolean) {
        if (target == displayed && phase == PHASE_LERP && progress.value >= 1f) return
        val fractions = fractionsOf(target)
        if (reduceMotion) {
            displayed = target
            from = fractions
            to = fractions
            phase = PHASE_LERP
            progress.snapTo(1f)
            return
        }
        val current = List(displayed.size) { fraction(it) }
        if (target.size == displayed.size) {
            from = current
            to = fractions
            displayed = target
            phase = PHASE_LERP
            progress.snapTo(0f)
            progress.animateTo(1f, tween(MORPH_MILLIS, easing = MotionTokens.CalmEaseOut))
        } else {
            from = current
            phase = PHASE_FADE_OUT
            progress.snapTo(0f)
            progress.animateTo(1f, tween(FADE_OUT_MILLIS, easing = LinearEasing))
            displayed = target
            to = fractions
            phase = PHASE_GROW
            progress.snapTo(0f)
            progress.animateTo(1f, tween(MORPH_MILLIS, easing = MotionTokens.CalmEaseOut))
            from = fractions
            phase = PHASE_LERP
        }
    }
}

/** A [ChartMorph] over [target], following it as it changes; [fractionsOf] gives a set's normalised heights. */
@Composable
internal fun <T> rememberChartMorph(
    target: List<T>,
    reduceMotion: Boolean = LocalReduceMotion.current,
    fractionsOf: (List<T>) -> List<Float>,
): ChartMorph<T> {
    val morph = remember { ChartMorph(target, fractionsOf) }
    LaunchedEffect(target, reduceMotion) { morph.morphTo(target, reduceMotion) }
    return morph
}
