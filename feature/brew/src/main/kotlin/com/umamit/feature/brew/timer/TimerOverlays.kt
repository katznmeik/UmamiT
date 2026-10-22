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

/** What a tap on the screen does. */
internal class TimerActions(val event: (TimerEvent) -> Unit, val back: () -> Unit, val menu: (TimerMenuItem) -> Unit)

/** The two questions of the timer: both read „Sitzung verwerfen?" (03 §5; TMR-12 AC1, TMR-13). */
internal enum class TimerConfirmation {
    /** *Sitzung verwerfen*. */
    Discard,

    /** *Sitzung beenden* without a completed non-rinse step: the session would be discarded. */
    FinishDiscard,
}

/**
 * The menu, the question and the Schritte-Sheet of the timer. A menu or a
 * question opened before the alert is hidden at once when the alert starts
 * (rule 8); one opened during the alert stays. The sheet never shows while
 * alerting (D-051).
 */
internal class TimerOverlays {
    var alerting by mutableStateOf(false)
    var menuOpen by mutableStateOf(false)
    var menuOpenedAlerting by mutableStateOf(false)
    var confirmation by mutableStateOf<TimerConfirmation?>(null)
    var confirmationOpenedAlerting by mutableStateOf(false)
    var stepsOpen by mutableStateOf(false)

    val menuShown: Boolean get() = menuOpen && (!alerting || menuOpenedAlerting)
    val confirmationShown: Boolean get() = confirmation != null && (!alerting || confirmationOpenedAlerting)
    val anyOpen: Boolean get() = menuShown || confirmationShown || (stepsOpen && !alerting)

    fun openMenu() {
        menuOpen = true
        menuOpenedAlerting = alerting
    }

    fun ask(question: TimerConfirmation) {
        confirmation = question
        confirmationOpenedAlerting = alerting
    }

    fun finish(state: TimerUiState.Active, onEvent: (TimerEvent) -> Unit) {
        if (state.finishWouldDiscard) ask(TimerConfirmation.FinishDiscard) else onEvent(TimerEvent.FinishSession)
    }

    fun closeAllOpenedBeforeAlert() {
        if (!menuOpenedAlerting) menuOpen = false
        if (!confirmationOpenedAlerting) confirmation = null
        stepsOpen = false
    }
}

/** The Schritte-Sheet (SCR-31) for this session, never over the alert (D-051). */
@Composable
internal fun TimerStepsSheet(state: TimerUiState.Active, overlays: TimerOverlays, onEvent: (TimerEvent) -> Unit) {
    val input = remember(state.steps, state.preAlert) {
        StepsSheetInput(
            steps = state.steps.map { StepsSheetStep(it.kind, it.plannedSeconds, it.tempC, it.status) },
            preAlert = state.preAlert,
            // SCR-05 calls only the controller: *Im Rezept speichern* stays on the setup (D-058).
            hasRecipe = false,
            baseTempC = state.steps.firstNotNullOfOrNull { it.tempC },
        )
    }
    StepsSheet(
        visible = overlays.stepsOpen && !state.alerting,
        state = rememberStepsSheetState(input),
        onDone = {
            onEvent(it)
            overlays.stepsOpen = false
        },
        onSaveToRecipe = {},
        onDismissRequest = { overlays.stepsOpen = false },
        alerting = state.alerting,
    )
}

@Composable
internal fun DiscardSessionDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.brew_timer_discard_title)) },
        text = { Text(stringResource(R.string.brew_timer_discard_text)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.brew_timer_discard_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.brew_timer_discard_cancel)) }
        },
    )
}

/**
 * The TalkBack announcements (SCR-05, 03 §6): a live region whose text changes
 * with the state, at the pre-alert and at 10 s remaining; assertive while the
 * alert plays. The ring itself is not a live region.
 */
@Composable
internal fun TimerAnnouncer(state: TimerUiState.Active, now: Long) {
    val step = state.currentStep
    val label = step?.let { stepLabel(it.kind, state.stepIndex, state.steps.size) }.orEmpty()
    val text = when (state.announcement(now)) {
        TimerAnnouncement.Ready -> stringResource(R.string.brew_timer_announce_ready, label)
        TimerAnnouncement.Running -> stringResource(R.string.brew_timer_announce_running, label)
        TimerAnnouncement.PreAlert -> stringResource(R.string.brew_timer_announce_pre_alert)
        TimerAnnouncement.TenSeconds -> stringResource(R.string.brew_timer_announce_ten_seconds)
        TimerAnnouncement.PreAlertTenSeconds -> stringResource(R.string.brew_timer_announce_pre_alert_ten_seconds)
        TimerAnnouncement.Paused -> stringResource(R.string.brew_timer_announce_paused)
        TimerAnnouncement.Alert -> stringResource(R.string.brew_timer_announce_alert)
        TimerAnnouncement.Finished -> stringResource(R.string.brew_timer_announce_finished)
    }
    val mode = if (state.alerting) LiveRegionMode.Assertive else LiveRegionMode.Polite
    Box(
        modifier = Modifier
            .size(1.dp)
            .testTag(TIMER_ANNOUNCEMENT_TAG)
            .semantics {
                liveRegion = mode
                contentDescription = text
            },
    )
}

@Composable
internal fun stepLabel(kind: StepKind, index: Int, count: Int): String =
    stringResource(R.string.brew_timer_step_label, stringResource(kind.label), index + 1, count)

/** The step kinds in German (D-051). */
internal val StepKind.label: Int
    get() = when (this) {
        StepKind.INFUSION -> R.string.brew_steps_kind_infusion
        StepKind.RINSE -> R.string.brew_steps_kind_rinse
        StepKind.WHISK -> R.string.brew_steps_kind_whisk
    }
