package com.umamit.core.designsystem.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.KeyframesSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable

/**
 * Motion tokens of 03 §3.6 „Motion tokens" (D-041). The values and the reasons
 * for them live in the spec; this object is their single source in code.
 */
object MotionTokens {
    /** Press feedback (scale on touch-down). */
    const val PRESS_MILLIS = 120

    /** Leaving elements: faster than entering, the system is answering. */
    const val EXIT_MILLIS = 200

    /** Standard state and content transitions; a little slower than Material's 200–250 ms. */
    const val STANDARD_MILLIS = 300

    /** Emphasised transitions: step changes, a whole surface changing state. */
    const val EMPHASIZED_MILLIS = 450

    /** Glide of the ring and liquor fill for long steps. */
    const val GLIDE_MILLIS = 600

    /** Long steps update the ring this often instead of per frame. */
    const val LONG_STEP_UPDATE_INTERVAL_MILLIS = 10_000L

    /** Pre-alert glow: one swell as long as the 0.8 s pre-alert tick (D-045). */
    const val PRE_ALERT_GLOW_MILLIS = 800

    /** The glow peaks early, like the strike of the bowl, then decays with the sound. */
    const val PRE_ALERT_GLOW_PEAK_MILLIS = 200

    /** One full washi → matcha → washi alert pulse. */
    const val ALERT_PULSE_CYCLE_MILLIS = 1_500

    /** Under reduce motion, opacity and colour changes still fade, but only this long. */
    const val REDUCED_FADE_MILLIS = 100

    /** Spring stiffness for sheets: critically damped, response ≈ 0.35 s. */
    const val SHEET_STIFFNESS = 320f

    /** Spring stiffness for the active brew bar: critically damped, response ≈ 0.4 s. */
    const val ACTIVE_BREW_BAR_STIFFNESS = 250f

    /** No overshoot anywhere: tea, not a game. */
    const val CRITICAL_DAMPING = 1f

    /** Soft ease-out: moves at once, then settles for a long, quiet tail. */
    val CalmEaseOut = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

    /** Ease-in-out for things already on screen that move (ring glide). */
    val CalmEaseInOut = CubicBezierEasing(0.45f, 0f, 0.2f, 1f)

    /** Sine ease-in-out: the alert pulse breathes instead of blinking. */
    val SineInOut = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)
}

/**
 * The motion specs for one reduce-motion state, read with `UmamiTheme.motion`.
 * Under reduce motion (system „Animationen entfernen", 03 §3.6) functional
 * motion is instant, fades stay but are very short, and the pre-alert glow and
 * the alert pulse are off (the alert becomes a static matcha surface).
 *
 * Short steps need no spec: the ring follows the remaining time per frame.
 * The liquor fill always rises from the bottom of the ring.
 */
@Immutable
class UmamiMotion(val reduceMotion: Boolean) {

    /** Standard transition: 300 ms soft ease-out. */
    fun <T> standard(): FiniteAnimationSpec<T> = calm(MotionTokens.STANDARD_MILLIS)

    /** Emphasised transition: 450 ms soft ease-out. */
    fun <T> emphasized(): FiniteAnimationSpec<T> = calm(MotionTokens.EMPHASIZED_MILLIS)

    /** Exit: 200 ms soft ease-out. */
    fun <T> exit(): FiniteAnimationSpec<T> = calm(MotionTokens.EXIT_MILLIS)

    /** Press feedback: 120 ms soft ease-out. */
    fun <T> press(): FiniteAnimationSpec<T> = calm(MotionTokens.PRESS_MILLIS)

    /** Opacity and colour changes; kept (very short) under reduce motion. */
    fun <T> fade(): FiniteAnimationSpec<T> = if (reduceMotion) {
        tween(MotionTokens.REDUCED_FADE_MILLIS, easing = LinearEasing)
    } else {
        tween(MotionTokens.STANDARD_MILLIS, easing = MotionTokens.CalmEaseOut)
    }

    /** Bottom sheets (Schritte-Sheet): critically damped spring, interruptible. */
    fun <T> sheet(): FiniteAnimationSpec<T> = springOrSnap(MotionTokens.SHEET_STIFFNESS)

    /** The active brew bar arriving and leaving: critically damped spring. */
    fun <T> activeBrewBar(): FiniteAnimationSpec<T> = springOrSnap(MotionTokens.ACTIVE_BREW_BAR_STIFFNESS)

    /** Ring and liquor fill of long steps: a 600 ms glide to the new value. */
    fun <T> longStepGlide(): FiniteAnimationSpec<T> = if (reduceMotion) {
        snap()
    } else {
        tween(MotionTokens.GLIDE_MILLIS, easing = MotionTokens.CalmEaseInOut)
    }

    /** How often long steps update the ring. */
    val longStepUpdateIntervalMillis: Long = MotionTokens.LONG_STEP_UPDATE_INTERVAL_MILLIS

    /** Glow intensity 0 → 1 → 0 in sync with the pre-alert tick; `null` under reduce motion. */
    val preAlertGlow: KeyframesSpec<Float>? = if (reduceMotion) {
        null
    } else {
        keyframes {
            durationMillis = MotionTokens.PRE_ALERT_GLOW_MILLIS
            0f at 0 using MotionTokens.CalmEaseOut
            1f at MotionTokens.PRE_ALERT_GLOW_PEAK_MILLIS using MotionTokens.SineInOut
            0f at MotionTokens.PRE_ALERT_GLOW_MILLIS
        }
    }

    /**
     * Alert pulse fraction 0 (washi) ↔ 1 (matcha), a slow sine with a 1.5 s
     * cycle: unmistakable across the room, far from flashing. `null` under
     * reduce motion: the alert is a static high-contrast matcha surface.
     */
    val alertPulse: InfiniteRepeatableSpec<Float>? = if (reduceMotion) {
        null
    } else {
        infiniteRepeatable(
            animation = tween(MotionTokens.ALERT_PULSE_CYCLE_MILLIS / 2, easing = MotionTokens.SineInOut),
            repeatMode = RepeatMode.Reverse,
        )
    }

    private fun <T> calm(durationMillis: Int): FiniteAnimationSpec<T> =
        if (reduceMotion) snap() else tween(durationMillis, easing = MotionTokens.CalmEaseOut)

    private fun <T> springOrSnap(stiffness: Float): FiniteAnimationSpec<T> =
        if (reduceMotion) snap() else spring(dampingRatio = MotionTokens.CRITICAL_DAMPING, stiffness = stiffness)
}

/**
 * Reduce motion follows only the system setting „Animationen entfernen", which
 * sets the animator duration scale to 0 (03 §3.6). Slower or faster scales are
 * developer options, not a request for less motion.
 */
fun isReduceMotion(animatorDurationScale: Float): Boolean = animatorDurationScale == 0f
