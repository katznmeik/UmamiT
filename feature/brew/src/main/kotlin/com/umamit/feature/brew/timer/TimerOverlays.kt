package com.umamit.feature.brew.timer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.umamit.core.model.StepKind
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.feature.brew.R
import com.umamit.feature.brew.steps.StepsSheet
import com.umamit.feature.brew.steps.StepsSheetInput
import com.umamit.feature.brew.steps.StepsSheetStep
import com.umamit.feature.brew.steps.rememberStepsSheetState

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
 * The menu, the question and the Schritte-Sheet of the timer. Each menu or
 * question remembers the alert it was opened in ([alertKey]); it shows only
 * while no alert plays or while that same alert plays, so whatever was opened
 * before an alert, or during an earlier one, never covers it (rule 8, D-058).
 * The sheet never shows while alerting (D-051). All of it survives a
 * configuration change (GEN-07).
 */
internal class TimerOverlays {
    /** The alert playing now, or null; set by the screen in each composition. */
    var currentAlert by mutableStateOf<String?>(null)
    var menuOpen by mutableStateOf(false)
    var menuAlert by mutableStateOf<String?>(null)
    var confirmation by mutableStateOf<TimerConfirmation?>(null)
    var confirmationAlert by mutableStateOf<String?>(null)
    var stepsOpen by mutableStateOf(false)

    private val alerting: Boolean get() = currentAlert != null
    val menuShown: Boolean get() = menuOpen && (!alerting || menuAlert == currentAlert)
    val confirmationShown: Boolean get() = confirmation != null && (!alerting || confirmationAlert == currentAlert)
    val anyOpen: Boolean get() = menuShown || confirmationShown || (stepsOpen && !alerting)

    /** Whether the menu may stay composed: not while an alert it was not opened in plays (no exit animation). */
    val menuComposable: Boolean get() = !alerting || menuShown

    fun openMenu() {
        menuOpen = true
        menuAlert = currentAlert
    }

    fun ask(question: TimerConfirmation) {
        confirmation = question
        confirmationAlert = currentAlert
    }

    fun finish(state: TimerUiState.Active, onEvent: (TimerEvent) -> Unit) {
        if (state.finishWouldDiscard) ask(TimerConfirmation.FinishDiscard) else onEvent(TimerEvent.FinishSession)
    }

    /** Alert [alert] started: closes everything opened before it or during another alert. */
    fun closeAllNotOpenedIn(alert: String) {
        if (menuAlert != alert) menuOpen = false
        if (confirmationAlert != alert) confirmation = null
        stepsOpen = false
    }

    companion object {
        val Saver: Saver<TimerOverlays, Any> = Saver(
            save = {
                listOf(it.menuOpen, it.menuAlert, it.confirmation?.name, it.confirmationAlert, it.stepsOpen)
            },
            restore = { saved ->
                val values = saved as List<*>
                TimerOverlays().apply {
                    menuOpen = values[0] as Boolean
                    menuAlert = values[1] as String?
                    confirmation = (values[2] as String?)?.let(TimerConfirmation::valueOf)
                    confirmationAlert = values[3] as String?
                    stepsOpen = values[4] as Boolean
                }
            },
        )
    }
}

/** The overlays of the timer, kept across configuration changes (GEN-07). */
@Composable
internal fun rememberTimerOverlays(): TimerOverlays = rememberSaveable(saver = TimerOverlays.Saver) { TimerOverlays() }

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
