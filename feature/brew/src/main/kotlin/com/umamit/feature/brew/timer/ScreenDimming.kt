package com.umamit.feature.brew.timer

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.component.AlertSurface
import com.umamit.core.designsystem.component.KettleHint
import com.umamit.core.designsystem.component.PillButton
import com.umamit.core.designsystem.component.ReliabilityBanner
import com.umamit.core.designsystem.component.TimerRing
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.text.clockText
import com.umamit.core.designsystem.text.spokenDuration
import com.umamit.core.designsystem.theme.SteepingTheme
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.StepKind
import com.umamit.core.model.TimerPhase
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.controller.TimerUiStep
import com.umamit.core.timer.engine.StepStatus
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.feature.brew.R
import com.umamit.feature.brew.steps.StepsSheet
import com.umamit.feature.brew.steps.StepsSheetInput
import com.umamit.feature.brew.steps.StepsSheetStep
import com.umamit.feature.brew.steps.rememberStepsSheetState
import kotlinx.coroutines.delay

/** The screen dims after this long untouched while a step runs (TMR-17). */
const val DIM_AFTER_UNTOUCHED_MS = 10_000L

/** The window brightness while dimmed: about 20 % (TMR-17, SET-25). */
const val DIMMED_BRIGHTNESS = 0.2f

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

@Composable
internal fun rememberScreenDimming(
    state: TimerUiState.Active,
    enabled: Boolean,
    nowElapsedMs: () -> Long,
): DimmingHandle {
    val handle = remember(nowElapsedMs) { DimmingHandle(nowElapsedMs) }
    // A new phase or step counts as a touch: *Start* and *Weiter* are taps.
    LaunchedEffect(state.phase, state.stepIndex) { handle.restart() }
    LaunchedEffect(state, enabled, handle.policy, nowElapsedMs) {
        while (true) {
            val now = nowElapsedMs()
            handle.dimmed = handle.policy.isDimmed(state, enabled, now)
            val delayMs = handle.policy.nextChangeDelayMs(state, enabled, now) ?: break
            delay(delayMs)
        }
    }
    return handle
}

/** Keeps the screen on (SET-17) and sets the window brightness while dimmed (TMR-17); both end with the screen. */
@Composable
internal fun TimerWindowEffects(keepScreenOn: Boolean, dimmed: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    val window = LocalActivity.current?.window
    DisposableEffect(window, dimmed) {
        window?.setBrightness(if (dimmed) DIMMED_BRIGHTNESS else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
        onDispose { window?.setBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) }
    }
}

private fun android.view.Window.setBrightness(value: Float) {
    attributes = attributes.apply { screenBrightness = value }
}
