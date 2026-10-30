package com.umamit.feature.brew.quick

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.component.FinishActiveSessionDialog
import com.umamit.core.designsystem.component.PillButton
import com.umamit.core.designsystem.component.TimerRing
import com.umamit.core.designsystem.component.TimerRingDefaults
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.text.clockText
import com.umamit.core.designsystem.text.spokenDuration
import com.umamit.core.designsystem.theme.SteepingTheme
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.timer.engine.PreAlertChoice
import com.umamit.feature.brew.R
import com.umamit.feature.brew.steps.PreAlertDropdown
import com.umamit.feature.brew.steps.preAlertSpoken
import com.umamit.feature.brew.timer.CircledIconButton
import com.umamit.feature.brew.timer.TimerBand
import com.umamit.feature.brew.timer.TimerBandPage

/** Test tag of SCR-07's ring: a tap opens the duration picker. */
const val QUICK_RING_TAG = "quick_ring"

/** The pre-alert mark on SCR-07's ring as a fraction of the duration, null without one (for tests; D-097). */
internal val PreAlertMarkKey = SemanticsPropertyKey<Float?>("PreAlertMark")

private val PillMinHeight = 72.dp
private val PillMinWidth = 200.dp

/** onSurfaceVariant at 60 % on the steeping background: 3.3:1. */
private const val CHIP_BORDER_ALPHA = 0.6f

/** What a tap on SCR-07 does. */
class QuickTimerActions(
    val back: () -> Unit = {},
    val select: (seconds: Int) -> Unit = {},
    val selectPreAlert: (PreAlertChoice) -> Unit = {},
    val start: () -> Unit = {},
    val confirmRestart: () -> Unit = {},
    val dismissQuestion: () -> Unit = {},
)

/**
 * The quick timer before its *Start* (SCR-07, TMR-22): dark like SCR-05 with
 * its ink-wash band and no seal (D-095), the ring with the chosen duration and its pre-alert mark, the chips
 * 0:30 … 5:00, the *Vorwarnung* selector (D-097) and *Start*. A tap on
 * the ring opens the duration picker (5 s – 24 h, PRF-04 increments). Once it
 * runs, the timer screen shows it (*Pause* / *Weiter*, *Fertig*, overflow
 * *Abbrechen*), as the notification and the full-screen alert open it.
 *
 * **Alerts first (rule 8):** the picker, the *Vorwarnung* menu and the second-timer question
 * remember the alert they were opened in; another alert removes them in its
 * first frame, without animation (the host then gives way to the timer).
 * Nothing on this screen animates except Material's chip colour and the
 * pill's press scale.
 */
@Composable
fun QuickTimerScreen(state: QuickTimerUiState, actions: QuickTimerActions, modifier: Modifier = Modifier) {
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var pickerOpenedIn by rememberSaveable { mutableStateOf<String?>(null) }
    val pickerCovered = state.alertKey != null && state.alertKey != pickerOpenedIn
    // A new alert closes the picker for good; it is already gone in the frame the alert started.
    LaunchedEffect(pickerCovered) { if (pickerCovered) pickerOpen = false }

    SteepingTheme(reduceMotion = UmamiTheme.reduceMotion) {
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            QuickTimerBody(
                state = state,
                actions = actions,
                openPicker = {
                    pickerOpenedIn = state.alertKey
                    pickerOpen = true
                },
            )
        }
        if (pickerOpen && !pickerCovered) {
            QuickDurationPicker(
                initialSeconds = state.durationSeconds,
                onApply = {
                    pickerOpen = false
                    actions.select(it)
                },
                onDismiss = { pickerOpen = false },
            )
        }
        // Never over an alert, by construction (the view model also closes it).
        val question = state.question?.takeIf { state.alertKey == null }
        if (question != null) {
            FinishActiveSessionDialog(
                teaName = question.teaName,
                onRestart = actions.confirmRestart,
                onCancel = actions.dismissQuestion,
            )
        }
    }
}

@Composable
private fun QuickTimerBody(state: QuickTimerUiState, actions: QuickTimerActions, openPicker: () -> Unit) {
    // The ink-wash band without a seal, laid out like the running quick timer, so nothing moves at *Start* (D-095).
    // With the *Vorwarnung* row (D-097) the content no longer fits a short screen at the largest font: there the band
    // gives way as a brew's does, so *Start* lies on the screen (rule 8, D-096); wherever it fits it keeps its height.
    TimerBandPage(
        withSeal = false,
        adaptive = true,
        controlHeight = PillMinHeight,
        band = { TimerBand() },
        topBar = {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = UmamiSpacing.XXSmall)) {
                CircledIconButton(
                    painter = painterResource(UmamiIcons.ArrowBack),
                    contentDescription = stringResource(R.string.brew_quick_back),
                    onClick = actions.back,
                )
            }
        },
        seal = {},
        aboveControls = { QuickTimerContent(state, actions, openPicker) },
        controls = {
            PillButton(
                text = stringResource(R.string.brew_quick_start),
                onClick = actions.start,
                icon = painterResource(UmamiIcons.Play),
                modifier = Modifier
                    .heightIn(min = PillMinHeight)
                    .widthIn(min = PillMinWidth),
            )
        },
    )
}

/** The title, the ring, the chips and the *Vorwarnung* row, above *Start*. */
@Composable
private fun ColumnScope.QuickTimerContent(
    state: QuickTimerUiState,
    actions: QuickTimerActions,
    openPicker: () -> Unit,
) {
    val title = stringResource(R.string.brew_quick_title)
    Spacer(Modifier.height(UmamiSpacing.Small))
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .padding(horizontal = UmamiSpacing.SideMargin)
            .semantics { heading() },
    )
    Spacer(Modifier.height(UmamiSpacing.Large))
    val mark = state.preAlertPosition()
    TimerRing(
        progress = { 0f },
        remainingSeconds = state.durationSeconds,
        caption = stringResource(R.string.brew_quick_caption),
        liquorColor = UmamiTheme.extendedColors.liquor.green,
        stepDescription = title,
        // Where the running quick timer shows it, so it does not move at *Start* (D-097).
        preAlertPosition = mark,
        modifier = Modifier
            .testTag(QUICK_RING_TAG)
            .semantics { this[PreAlertMarkKey] = mark }
            .clickable(
                interactionSource = null,
                // Round like the ring: the bounded default flashes a square around it (A2).
                indication = ripple(bounded = false, radius = TimerRingDefaults.Size / 2),
                onClickLabel = stringResource(R.string.brew_quick_ring_label),
                onClick = openPicker,
            ),
    )
    Spacer(Modifier.height(UmamiSpacing.Medium))
    DurationChips(selected = state.durationSeconds, onSelect = actions.select)
    QuickPreAlertRow(state = state, onSelect = actions.selectPreAlert)
    Spacer(Modifier.height(UmamiSpacing.Medium))
}

/**
 * The *Vorwarnung* row below the chips (SCR-07, TMR-22, D-097): the selector of SCR-31 with the lead this quick
 * timer will get („Auto · 10 s", „Aus", „20 s", „20 s · zu kurz"), remembered for the next quick timer.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickPreAlertRow(state: QuickTimerUiState, onSelect: (PreAlertChoice) -> Unit) {
    val shown = state.preAlertShown()
    FlowRow(
        modifier = Modifier.padding(horizontal = UmamiSpacing.SideMargin),
        horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall, Alignment.CenterHorizontally),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.brew_steps_pre_alert),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PreAlertDropdown(
            shown = shown,
            selected = state.preAlertChoice,
            description = stringResource(R.string.brew_quick_pre_alert_description, preAlertSpoken(shown)),
            optionShown = { state.preAlertShown(it) },
            onSelect = onSelect,
            // Never over an alert (rule 8), as the duration picker.
            alertKey = state.alertKey,
        )
    }
}

/**
 * The chips 0:30 … 8:00 (TMR-22), wrapping onto a second line at narrow widths
 * and large fonts. No check mark on the selected one: it would widen the chip
 * and shift its neighbours under the finger; the colour and the selected state
 * (TalkBack) mark it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DurationChips(selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(
        modifier = Modifier.padding(horizontal = UmamiSpacing.SideMargin),
        horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(UmamiSpacing.XXSmall),
    ) {
        // Unselected chips are outlined at ≥ 3:1 against the steeping background (WCAG 1.4.11, D-065).
        val borderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = CHIP_BORDER_ALPHA)
        QuickDuration.CHIPS.forEach { seconds ->
            val spoken = spokenDuration(seconds)
            val isSelected = seconds == selected
            FilterChip(
                selected = isSelected,
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = borderColor,
                ),
                onClick = { onSelect(seconds) },
                label = { Text(clockText(seconds), style = MaterialTheme.typography.labelLarge) },
                modifier = Modifier.semantics { contentDescription = spoken },
            )
        }
    }
}
