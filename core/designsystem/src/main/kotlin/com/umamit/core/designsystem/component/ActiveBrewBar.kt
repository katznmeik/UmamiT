package com.umamit.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umamit.core.designsystem.R
import com.umamit.core.designsystem.text.clockText
import com.umamit.core.designsystem.text.spokenDuration
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme

/** The mini ring of the bar (03 §3.8). */
private val MiniRingSize = 56.dp
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
 * it keeps showing the last brew. Under reduce motion it appears at once and
 * fades very briefly.
 *
 * TalkBack: one clickable element with a German description, e.g.
 * „Laufender Aufguss: Bio Sencha Hisui, Aufguss 2 von 4, noch 1 Minute 12 Sekunden. Zum Timer".
 *
 * @param brew the active brew, or `null` when nothing runs.
 * @param windowInsets system bar insets applied inside the bar, so its background stays
 *   edge-to-edge and its content clear of the bars (the rail layout has no bar to do it).
 * @param progress elapsed fraction of the current step, 0 to 1, read only while
 *   drawing, so a per-frame value redraws the mini ring without recomposing the bar.
 */
@Composable
fun ActiveBrewBar(
    brew: ActiveBrew?,
    progress: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets(0),
) {
    // The last brew, so the bar keeps its content while it leaves.
    val last = remember { LastBrew(brew) }
    SideEffect { if (brew != null) last.value = brew }
    val shown = brew ?: last.value
    val motion = UmamiTheme.motion
    AnimatedVisibility(
        visible = brew != null,
        modifier = modifier.clipToBounds(),
        enter = expandVertically(motion.activeBrewBar(), expandFrom = Alignment.Bottom) + fadeIn(motion.fade()),
        exit = shrinkVertically(motion.activeBrewBar(), shrinkTowards = Alignment.Bottom) + fadeOut(motion.exit()),
    ) {
        shown?.let { ActiveBrewBarContent(it, progress, onClick, windowInsets) }
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
    val remaining = spokenDuration(brew.remainingSeconds)
    val spoken = if (brew.stepDescription != null) {
        stringResource(R.string.ds_active_brew_description, brew.title, brew.stepDescription, remaining)
    } else {
        stringResource(R.string.ds_active_brew_description_no_step, brew.title, remaining)
    }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = spoken },
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
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
            MiniRing(progress, brew.liquorColor)
            Spacer(Modifier.width(UmamiSpacing.Small))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = brew.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (brew.step != null) {
                    Text(
                        text = brew.step,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.width(UmamiSpacing.Small))
            Text(
                text = clockText(brew.remainingSeconds),
                style = UmamiTheme.textStyles.timerDigits.copy(
                    fontSize = RemainingFontSize,
                    lineHeight = RemainingLineHeight,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MiniRing(progress: () -> Float, liquorColor: Color) {
    val track = MaterialTheme.colorScheme.outline
    val arc = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.size(MiniRingSize)) {
        val stroke = MiniRingStroke.toPx()
        val radius = size.minDimension / 2 - stroke / 2
        val level = progress().coerceIn(0f, 1f)
        drawLiquor(
            level,
            liquorColor.copy(alpha = MINI_RING_LIQUOR_ALPHA),
            radius,
            stroke / 2 + MiniRingLiquorInset.toPx(),
        )
        drawRing(level, track, arc, radius, stroke)
    }
}
