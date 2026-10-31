package com.umamit.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umamit.core.designsystem.R
import com.umamit.core.designsystem.text.clockText
import com.umamit.core.designsystem.text.spokenDuration
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme

/** The mini ring of the bar (03 §3.8); 40 dp in each half of the bar with two timers (D-098). */
private val MiniRingSize = 56.dp
private val HalfMiniRingSize = 40.dp
private val HalfRemainingFontSize = 20.sp
private val HalfRemainingLineHeight = 24.sp
private const val MINI_RING_LIQUOR_ALPHA = 0.5f
private val MiniRingStroke = 3.dp
private val MiniRingLiquorInset = 4.dp
private val RemainingFontSize = 28.sp
private val RemainingLineHeight = 32.sp

/**
 * The active brew bar above the navigation bar (03 §3.8, GEN-06): a 56 dp mini
 * ring with the liquor fill, the tea name, the step and the remaining time.
 * It arrives and leaves with the `activeBrewBar` spring and a fade (03 §3.6):
 * it grows from the bottom edge (the navigation bar), clipped, so the content
 * above glides with the same spring instead of jumping 72 dp. While it leaves
 * it keeps showing the last brew. Under reduce motion it appears at once, and so
 * does a bar that arrives already alerting (rule 8).
 *
 * **ALERTING** ([ActiveBrew.alerting]): the bar turns static matcha on the next
 * frame (`alertEnter`, no transition) with ink content, „Jetzt abgießen!" in
 * place of the step and the overtime counting up („+0:07"); the mini ring is
 * full, in ink, without liquor. It does not pulse: the full alert is the timer
 * screen, which a tap on the bar opens. Rule 8: nothing in the bar delays or
 * softens that state.
 *
 * TalkBack: one clickable element with a German description, e.g.
 * „Laufender Aufguss: Bio Sencha Hisui, Aufguss 2 von 4, noch 1 Minute 12 Sekunden. Zum Timer".
 *
 * **Two timers (D-098).** With [alongside], the quick timer beside a brew session, the bar holds two halves side by
 * side, the brew ([brew]) first, each with its 40 dp mini ring, its name, the step and the countdown, each its own
 * tap target ([onClick], [onAlongsideClick]) with its own description. An alerting half takes the ALERTING style
 * alone (matcha, ink, the full ring and the overtime; „Jetzt abgießen!" only in its description, the half has no
 * room for it); a held quick alert ([ActiveBrew.held]) reads „fertig" without it. The second half comes and goes
 * without motion of its own.
 *
 * @param brew the active brew, or `null` when nothing runs.
 * @param windowInsets system bar insets applied inside the bar, so its background stays
 *   edge-to-edge and its content clear of the bars (the rail layout has no bar to do it).
 * @param progress elapsed fraction of the current step, 0 to 1, read only while
 *   drawing, so a per-frame value redraws the mini ring without recomposing the bar.
 *   For a long step ([ActiveBrew.longStep]) the source must update it only about every
 *   `UmamiMotion.longStepUpdateIntervalMillis` (03 §3.6 battery rule): the bar is on
 *   every tab for the whole step, and the mini ring glides to each update.
 */
@Composable
fun ActiveBrewBar(
    brew: ActiveBrew?,
    progress: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets(0),
    alongside: ActiveBrew? = null,
    alongsideProgress: () -> Float = { 0f },
    onAlongsideClick: () -> Unit = {},
) {
    // The last brew, so the bar keeps its content while it leaves.
    val last = remember { LastBrew(brew) }
    SideEffect { if (brew != null) last.value = brew }
    val shown = brew ?: last.value
    val pair = alongside?.takeIf { brew != null }
    val motion = UmamiTheme.motion
    AnimatedVisibility(
        visible = brew != null,
        modifier = modifier.clipToBounds(),
        // A bar that arrives already alerting (a recreated activity while the alert plays) snaps in
        // like the RUNNING→ALERTING switch: no growth, no fade (`alertEnter`, rule 8).
        enter = if (brew?.alerting == true || pair?.alerting == true) {
            EnterTransition.None
        } else {
            expandVertically(motion.activeBrewBar(), expandFrom = Alignment.Bottom) + fadeIn(motion.fade())
        },
        exit = shrinkVertically(motion.activeBrewBar(), shrinkTowards = Alignment.Bottom) + fadeOut(motion.exitFade()),
    ) {
        when {
            pair != null && shown != null -> TwoTimersBarContent(
                halves = listOf(
                    BarHalf(shown, progress, onClick),
                    BarHalf(pair, alongsideProgress, onAlongsideClick),
                ),
                windowInsets = windowInsets,
            )

            shown != null -> ActiveBrewBarContent(shown, progress, onClick, windowInsets)
        }
    }
}

private class LastBrew(var value: ActiveBrew?)

@Composable
private fun ActiveBrewBarContent(
    brew: ActiveBrew,
    progress: () -> Float,
    onClick: () -> Unit,
    windowInsets: WindowInsets,
) {
    val alert = UmamiTheme.extendedColors.alert
    // Static matcha with ink while alerting (4.55:1); the calm bar otherwise. No colour
    // animation: entering ALERTING is instant (`alertEnter`).
    val background = if (brew.alerting) alert.matcha else MaterialTheme.colorScheme.surfaceContainerHigh
    val content = if (brew.alerting) alert.content else MaterialTheme.colorScheme.onSurface
    val spoken = brew.spokenDescription()
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = spoken },
        color = background,
        contentColor = content,
    ) {
        Row(
            // The visible texts are in the description already; the bar is one element.
            modifier = Modifier
                .windowInsetsPadding(windowInsets)
                .clearAndSetSemantics {}
                .heightIn(min = 72.dp)
                .padding(horizontal = UmamiSpacing.Medium, vertical = UmamiSpacing.XSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiniRing(progress, brew)
            Spacer(Modifier.width(UmamiSpacing.Small))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = brew.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val secondLine = if (brew.alerting) stringResource(R.string.ds_active_brew_alerting) else brew.step
                if (secondLine != null) {
                    // Content colour, not onSurfaceVariant: that is only 4.21:1 on surfaceContainerHigh (03 §6).
                    Text(
                        text = secondLine,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(UmamiSpacing.Small))
            Text(
                text = if (brew.alerting) {
                    stringResource(R.string.ds_overtime, clockText(brew.overtimeSeconds))
                } else {
                    clockText(brew.remainingSeconds)
                },
                style = UmamiTheme.textStyles.timerDigits.copy(
                    fontSize = RemainingFontSize,
                    lineHeight = RemainingLineHeight,
                ),
                maxLines = 1,
            )
        }
    }
}

/** One half of the bar with two timers: its timer, its progress and its tap. */
private class BarHalf(val brew: ActiveBrew, val progress: () -> Float, val onClick: () -> Unit)

/** The bar with two timers (D-098): two halves on the calm bar, each its own element. */
@Composable
private fun TwoTimersBarContent(halves: List<BarHalf>, windowInsets: WindowInsets) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier
                .windowInsetsPadding(windowInsets)
                .height(IntrinsicSize.Min)
                .heightIn(min = 72.dp),
        ) {
            halves.forEach { half -> BarHalfContent(half, Modifier.weight(1f).fillMaxHeight()) }
        }
    }
}

@Composable
private fun BarHalfContent(half: BarHalf, modifier: Modifier = Modifier) {
    val brew = half.brew
    val alert = UmamiTheme.extendedColors.alert
    // The alerting half alone turns static matcha with ink (`alertEnter`, no transition); a held one stays calm.
    val background = if (brew.alerting) alert.matcha else Color.Transparent
    val content = if (brew.alerting) alert.content else MaterialTheme.colorScheme.onSurface
    val spoken = brew.spokenDescription()
    Surface(
        onClick = half.onClick,
        modifier = modifier.semantics { contentDescription = spoken },
        color = background,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier
                .clearAndSetSemantics {}
                .padding(horizontal = UmamiSpacing.Small, vertical = UmamiSpacing.XSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiniRing(half.progress, brew, HalfMiniRingSize)
            Spacer(Modifier.width(UmamiSpacing.Small))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = brew.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val digits = UmamiTheme.textStyles.timerDigits.copy(
                        fontSize = HalfRemainingFontSize,
                        lineHeight = HalfRemainingLineHeight,
                    )
                    when {
                        brew.held -> Text(
                            text = stringResource(R.string.ds_active_brew_held),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                        )

                        brew.alerting -> Text(
                            text = stringResource(R.string.ds_overtime, clockText(brew.overtimeSeconds)),
                            style = digits,
                            maxLines = 1,
                        )

                        else -> Text(text = clockText(brew.remainingSeconds), style = digits, maxLines = 1)
                    }
                    // An alerting half says it by its matcha and the overtime; „Jetzt abgießen!" would not fit a
                    // half (TalkBack reads it).
                    val step = brew.step?.takeUnless { brew.alerting || brew.held }
                    if (step != null) {
                        Spacer(Modifier.width(UmamiSpacing.XSmall))
                        Text(
                            text = step,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveBrew.spokenDescription(): String = when {
    held -> stringResource(R.string.ds_active_brew_held_description, title)
    alerting -> alertingDescription()
    else -> runningDescription()
}

@Composable
private fun ActiveBrew.alertingDescription(): String {
    val overtime = spokenDuration(overtimeSeconds)
    return when {
        stepDescription != null && overtimeSeconds > 0 ->
            stringResource(R.string.ds_active_brew_alerting_description, title, stepDescription, overtime)

        stepDescription != null ->
            stringResource(R.string.ds_active_brew_alerting_description_no_overtime, title, stepDescription)

        overtimeSeconds > 0 ->
            stringResource(R.string.ds_active_brew_alerting_description_no_step, title, overtime)

        else -> stringResource(R.string.ds_active_brew_alerting_description_no_step_no_overtime, title)
    }
}

@Composable
private fun ActiveBrew.runningDescription(): String {
    val remaining = spokenDuration(remainingSeconds)
    return if (stepDescription != null) {
        stringResource(R.string.ds_active_brew_description, title, stepDescription, remaining)
    } else {
        stringResource(R.string.ds_active_brew_description_no_step, title, remaining)
    }
}

/**
 * The mini ring: the timer ring's drawing at 56 dp, with the same long-step glide
 * (03 §3.6). While alerting it is full and drawn in ink, without liquor, like the
 * ring on the alert surface.
 */
@Composable
private fun MiniRing(progress: () -> Float, brew: ActiveBrew, size: Dp = MiniRingSize) {
    val motion = UmamiTheme.motion
    val alertRing = TimerRingDefaults.alertColors()
    val track = if (brew.alerting) alertRing.track else MaterialTheme.colorScheme.outline
    val arc = if (brew.alerting) alertRing.progress else MaterialTheme.colorScheme.primary
    val liquorAlpha = if (brew.alerting) 0f else MINI_RING_LIQUOR_ALPHA
    val level = rememberRingLevel(progress, brew.longStep, motion)
    MiniRingDrawing(
        level = if (brew.alerting) FullLevel else level,
        liquor = brew.liquorColor.copy(alpha = liquorAlpha),
        track = track,
        arc = arc,
        ringSize = size,
    )
}

private val FullLevel: () -> Float = { 1f }

/** Built once per size, like the timer ring, so a per-frame progress allocates nothing. */
@Composable
private fun MiniRingDrawing(level: () -> Float, liquor: Color, track: Color, arc: Color, ringSize: Dp) {
    Spacer(
        modifier = Modifier
            .size(ringSize)
            .drawWithCache {
                val stroke = MiniRingStroke.toPx()
                val radius = size.minDimension / 2 - stroke / 2
                val strokes = RingStrokes(stroke)
                val inset = stroke / 2 + MiniRingLiquorInset.toPx()
                onDrawBehind {
                    val current = level()
                    drawLiquor(current, liquor, radius, inset)
                    drawRing(current, track, arc, radius, strokes)
                }
            },
    )
}
