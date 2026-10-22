package com.umamit.feature.brew.timer

import android.provider.Settings
import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import com.umamit.core.model.TimerPhase
import com.umamit.core.timer.controller.TimerUiState
import kotlinx.coroutines.delay

/** The screen dims after this long untouched while a step runs (TMR-17). */
const val DIM_AFTER_UNTOUCHED_MS = 10_000L

/** The window brightness while dimmed: about 20 % (TMR-17, SET-25). */
const val DIMMED_BRIGHTNESS = 0.2f

/** The largest value of `Settings.System.SCREEN_BRIGHTNESS`. */
private const val SYSTEM_BRIGHTNESS_MAX = 255f

/**
 * The dimmed window brightness for a phone at [systemLevel] (0..1; null when
 * unknown): about 20 %, but never brighter than the phone already is, so a dark
 * kitchen at night is not lit up by „dimming" (TMR-17, D-058).
 */
fun dimmedBrightness(systemLevel: Float?): Float = minOf(DIMMED_BRIGHTNESS, systemLevel ?: DIMMED_BRIGHTNESS)

/**
 * The dimming rule of the timer screen (TMR-17, SET-25): while a step is
 * RUNNING and the screen is untouched for [DIM_AFTER_UNTOUCHED_MS], the
 * window dims; it is bright again at the pre-alert, at the alert, on any
 * touch, and in READY, PAUSED and FINISHED. Times are on the monotonic clock.
 *
 * @property lastTouchMs the last touch, or the moment the current phase began
 *   (a phase change counts as a touch: *Start* and *Weiter* are taps).
 */
data class ScreenDimming(val lastTouchMs: Long) {

    /** A touch at [nowElapsedMs]. */
    fun touched(nowElapsedMs: Long): ScreenDimming = copy(lastTouchMs = nowElapsedMs)

    fun isDimmed(state: TimerUiState.Active, enabled: Boolean, nowElapsedMs: Long): Boolean = enabled &&
        state.phase == TimerPhase.RUNNING &&
        !state.preAlertReached(nowElapsedMs) &&
        nowElapsedMs - lastTouchMs >= DIM_AFTER_UNTOUCHED_MS

    /** How long until [isDimmed] may change without a touch or a new state; null when it cannot. */
    fun nextChangeDelayMs(state: TimerUiState.Active, enabled: Boolean, nowElapsedMs: Long): Long? {
        if (!enabled || state.phase != TimerPhase.RUNNING || state.preAlertReached(nowElapsedMs)) return null
        val dimAt = lastTouchMs + DIM_AFTER_UNTOUCHED_MS
        val brightAt = if (state.preAlertPlayed) null else state.preAlertAtElapsedMs
        return listOfNotNull(dimAt, brightAt).map { it - nowElapsedMs }.filter { it > 0 }.minOrNull()
    }
}

/** The dimming of a running step (TMR-17): whether it is dimmed now, and the touch that brightens it. */
internal class DimmingHandle(private val nowElapsedMs: () -> Long) {
    var policy by mutableStateOf(ScreenDimming(nowElapsedMs()))
    var dimmed by mutableStateOf(false)

    fun touched() {
        policy = policy.touched(nowElapsedMs())
    }

    fun restart() {
        policy = ScreenDimming(nowElapsedMs())
    }
}

/**
 * The dimming of the timer screen. A new phase or step, and closing a menu, a
 * question or the sheet, count as a touch: taps inside those windows never
 * reach the screen, so they restart the 10 s when they close ([interacting]).
 */
@Composable
internal fun rememberScreenDimming(
    state: TimerUiState.Active,
    enabled: Boolean,
    interacting: Boolean,
    nowElapsedMs: () -> Long,
): DimmingHandle {
    val handle = remember(nowElapsedMs) { DimmingHandle(nowElapsedMs) }
    LaunchedEffect(state.phase, state.stepIndex, interacting) { handle.restart() }
    val active = enabled && !interacting
    LaunchedEffect(state, active, handle.policy, nowElapsedMs) {
        while (true) {
            val now = nowElapsedMs()
            handle.dimmed = handle.policy.isDimmed(state, active, now)
            val delayMs = handle.policy.nextChangeDelayMs(state, active, now) ?: break
            delay(delayMs)
        }
    }
    return handle
}

/**
 * Keeps the screen on (SET-17) and sets the window brightness while [dimmed]
 * (TMR-17); both end with the screen. The brightness is applied in the
 * composition that changes [dimmed], so the alert is bright in its first frame.
 */
@Composable
internal fun TimerWindowEffects(keepScreenOn: Boolean, dimmed: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    val window = LocalActivity.current?.window
    DisposableEffect(window, dimmed) {
        window?.setBrightness(
            if (dimmed) {
                dimmedBrightness(
                    window.systemBrightness(),
                )
            } else {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            },
        )
        onDispose { window?.setBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) }
    }
}

/** The phone's own brightness setting (0..1), or null when it cannot be read. */
private fun Window.systemBrightness(): Float? = try {
    Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / SYSTEM_BRIGHTNESS_MAX
} catch (_: Settings.SettingNotFoundException) {
    null
}

private fun Window.setBrightness(value: Float) {
    attributes = attributes.apply { screenBrightness = value }
}
