package com.umamit.feature.brew.timer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.text.clockText
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.model.TimerPhase
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.controller.TimerUiStep
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.feature.brew.R

/** Test tag of the row for the other timer (SCR-05, SCR-07, D-098). */
const val TIMER_OTHER_ROW_TAG = "timer_other_row"

/** The row's height: one 48 dp touch target, also at the largest font (its text stays on one line). */
private val TimerOtherRowHeight = UmamiSpacing.MinTouchTarget

private val OtherRowIconSize = 18.dp
private val OtherRowButtonPadding = PaddingValues(horizontal = UmamiSpacing.Small)

/** How much of a long name the row keeps before the brew's step gives way. */
private const val NAME_KEPT_CHARS = 8
private const val ELLIPSIS = "…"

/** The digits keep their width as they tick, so the centred row never shifts by a pixel (no motion of its own). */
private const val TABULAR_FIGURES = "tnum"

/**
 * The slim row for the other active timer below the band (SCR-05, SCR-07, D-098 Q3): on the brew „⏱ Schnelltimer
 * 2:14" („· Pausiert 2:14"), on the quick timer „🍃 Tanabata · Aufguss 2 · 0:48" („· bereit", „· fertig"), its
 * countdown ticking, with a small *Pause* / *Weiter* for that timer's running or paused step. A tap on the row
 * opens that timer ([onOpen]); the button sends its event to that timer only ([onEvent]). A long name gives way
 * first (ellipsis), down to its first few letters; where even then the step does not fit on the row's one line (a
 * large font), the step gives way: „Tanabata · 0:48".
 *
 * With [hidden] (the alert surface, rule 8) it draws and says nothing and takes no touch, but keeps its space, so
 * nothing moves when the alert starts. No motion of its own: the digits tick, the button has its press feedback.
 */
@Composable
internal fun TimerOtherRow(
    other: TimerUiState.Active,
    nowElapsedMs: () -> Long,
    hidden: Boolean,
    onEvent: (TimerEvent) -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val now by rememberTimerNow(other, nowElapsedMs)
    val openLabel = stringResource(
        if (other.isQuick) R.string.brew_timer_other_open_quick else R.string.brew_timer_other_open_brew,
    )
    val name = other.teaName ?: stringResource(R.string.brew_timer_quick_title)
    val style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TABULAR_FIGURES)
    val full = otherDetail(other, now, withStep = true)
    val short = otherDetail(other, now, withStep = false)
    val resume = stringResource(R.string.brew_timer_resume)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TimerOtherRowHeight),
        contentAlignment = Alignment.Center,
    ) {
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val buttonStyle = MaterialTheme.typography.labelLarge
        // What the name and the detail have beside the icon, the gaps and the widest button („Weiter").
        val fixed = with(density) {
            (UmamiSpacing.SideMargin * 2 + OtherRowIconSize + UmamiSpacing.XSmall * 3 + UmamiSpacing.Small * 2)
                .roundToPx()
        } + measurer.measure(resume, buttonStyle).size.width
        // The step stays while the name keeps its first few letters („Bio Uji G…"); the name never gives way to it.
        val shortestName = if (name.length > NAME_KEPT_CHARS) name.take(NAME_KEPT_CHARS) + ELLIPSIS else name
        val fits = measurer.measure(shortestName, style).size.width + measurer.measure(full, style).size.width <=
            constraints.maxWidth - fixed
        val detail = if (fits) full else short
        // The row's height with a button, also where it has none (READY, FINISHED) or is hidden: the screen below
        // never moves for it (rule 8).
        OtherRowContent(
            other = other,
            name = name,
            detail = detail,
            style = style,
            toggle = otherToggle(other) ?: OtherToggle(TimerEvent.Resume, resume, description = ""),
            onEvent = {},
            modifier = Modifier
                .alpha(0f)
                .clearAndSetSemantics {},
        )
        if (!hidden) {
            OtherRowContent(
                other = other,
                name = name,
                detail = detail,
                style = style,
                toggle = otherToggle(other),
                onEvent = onEvent,
                modifier = Modifier
                    .testTag(TIMER_OTHER_ROW_TAG)
                    .clickable(onClickLabel = openLabel, onClick = onOpen),
            )
        }
    }
}

@Composable
private fun OtherRowContent(
    other: TimerUiState.Active,
    name: String,
    detail: String,
    style: TextStyle,
    toggle: OtherToggle?,
    onEvent: (TimerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TimerOtherRowHeight)
            .padding(horizontal = UmamiSpacing.SideMargin),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(if (other.isQuick) UmamiIcons.Timer else UmamiIcons.Leaf),
            contentDescription = null,
            tint = LocalContentColor.current,
            modifier = Modifier.size(OtherRowIconSize),
        )
        Spacer(Modifier.width(UmamiSpacing.XSmall))
        // The name gives way (ellipsis); the detail with the countdown always shows whole.
        Text(
            text = name,
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(UmamiSpacing.XSmall))
        Text(text = detail, style = style, maxLines = 1)
        Box(Modifier.padding(start = UmamiSpacing.XSmall)) {
            toggle?.let {
                TextButton(
                    onClick = { onEvent(it.event) },
                    contentPadding = OtherRowButtonPadding,
                    modifier = Modifier.semantics { contentDescription = it.description },
                ) {
                    Text(it.label, maxLines = 1)
                }
            }
        }
    }
}

/** The row's button: its [event], its [label] and what TalkBack reads ([description]). */
private class OtherToggle(val event: TimerEvent, val label: String, val description: String)

/**
 * „2:14", „· Pausiert 2:14", „· Aufguss 2 · 0:48", „· bereit", „· fertig"; without [withStep] the brew's step is left
 * out („· 0:48", „· Pausiert 0:48").
 */
@Composable
private fun otherDetail(other: TimerUiState.Active, now: Long, withStep: Boolean): String {
    val time = clockText(other.display(now).remainingSeconds.toInt())
    val step = other.currentStep
    return if (other.isQuick || step == null || !withStep) plainDetail(other, time) else stepDetail(other, step, time)
}

/** The detail without a step: „2:14" (the quick timer), „· 0:48", „· Pausiert 2:14", „· bereit", „· fertig". */
@Composable
private fun plainDetail(other: TimerUiState.Active, time: String): String = when (other.phase) {
    TimerPhase.RUNNING -> if (other.isQuick) time else stringResource(R.string.brew_timer_other_time, time)

    TimerPhase.PAUSED -> stringResource(R.string.brew_timer_other_paused, time)

    TimerPhase.READY -> stringResource(R.string.brew_timer_other_ready)

    TimerPhase.ALERTING -> stringResource(
        if (other.isQuick) R.string.brew_timer_other_done else R.string.brew_timer_other_alert,
    )

    TimerPhase.FINISHED -> stringResource(R.string.brew_timer_other_done)
}

/** The brew's detail with its [step]: „· Aufguss 2 · 0:48", „· Aufguss 2 · Pausiert 0:48", „· Aufguss 2 · bereit". */
@Composable
private fun stepDetail(other: TimerUiState.Active, step: TimerUiStep, time: String): String {
    val kind = stringResource(step.kind.label)
    val number = other.stepIndex + 1
    return when (other.phase) {
        TimerPhase.RUNNING -> stringResource(R.string.brew_timer_other_step, kind, number, time)
        TimerPhase.PAUSED -> stringResource(R.string.brew_timer_other_step_paused, kind, number, time)
        TimerPhase.READY -> stringResource(R.string.brew_timer_other_step_ready, kind, number)
        TimerPhase.ALERTING -> stringResource(R.string.brew_timer_other_alert)
        TimerPhase.FINISHED -> stringResource(R.string.brew_timer_other_done)
    }
}

/** The row's button for a running or paused step; none otherwise. */
@Composable
private fun otherToggle(other: TimerUiState.Active): OtherToggle? = when (other.phase) {
    TimerPhase.RUNNING -> OtherToggle(
        TimerEvent.Pause,
        stringResource(R.string.brew_timer_pause),
        stringResource(
            if (other.isQuick) R.string.brew_timer_other_pause_quick else R.string.brew_timer_other_pause_brew,
        ),
    )

    TimerPhase.PAUSED -> OtherToggle(
        TimerEvent.Resume,
        stringResource(R.string.brew_timer_resume),
        stringResource(
            if (other.isQuick) R.string.brew_timer_other_resume_quick else R.string.brew_timer_other_resume_brew,
        ),
    )

    else -> null
}
