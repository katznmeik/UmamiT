package com.umamit.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.R
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing

/** What a [StatusCheckRow] shows: ✓, ! (missing), or nothing to check on this platform. */
enum class StatusCheck { Ok, Missing, Unavailable }

/**
 * A reliability row (ONB-02, SET-24): ✓ or ! in a small circle, the [label], an optional
 * [explanation] and, while the check is missing, the action button ([actionLabel]). No
 * motion: a row changes when the screen resumes, never while the user looks at it. TalkBack reads
 * the label, the explanation and the state as one node, then the button. From font scale 1.5 the
 * button moves below the text, so the text keeps the row's width and no word is broken.
 */
@Composable
fun StatusCheckRow(
    label: String,
    state: StatusCheck,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    explanation: String? = null,
) {
    val stateText = stringResource(
        when (state) {
            StatusCheck.Ok -> R.string.ds_status_ok
            StatusCheck.Missing -> R.string.ds_status_missing
            StatusCheck.Unavailable -> R.string.ds_status_unavailable
        },
    )
    val actionBelow = LocalDensity.current.fontScale >= LARGE_FONT_SCALE
    val action: @Composable () -> Unit = {
        TextButton(onClick = onAction) {
            Text(text = actionLabel, style = MaterialTheme.typography.labelLarge)
        }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = UmamiSpacing.MinTouchTarget)
            .padding(vertical = UmamiSpacing.XSmall),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.Small),
        ) {
            StatusMark(state)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) { stateDescription = stateText },
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (explanation != null) {
                    Text(
                        text = explanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state == StatusCheck.Missing && !actionBelow) action()
        }
        if (state == StatusCheck.Missing && actionBelow) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { action() }
        }
    }
}

/** From this font scale a row's action sits below its text (03 §6: 200 % without loss). */
private const val LARGE_FONT_SCALE = 1.5f

@Composable
private fun StatusMark(state: StatusCheck) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (state) {
        StatusCheck.Ok -> colors.primaryContainer to colors.onPrimaryContainer

        StatusCheck.Missing -> colors.secondaryContainer to colors.onSecondaryContainer

        // onSurface: the „–" stays at ≥ 4.5:1 on the light container too.
        StatusCheck.Unavailable -> colors.surfaceContainerHighest to colors.onSurface
    }
    Surface(
        modifier = Modifier
            .size(28.dp)
            .clearAndSetSemantics {},
        shape = CircleShape,
        color = container,
        contentColor = content,
    ) {
        // The circle has a fixed size, so its glyph does not follow the font scale (a scaled „!" was clipped).
        val markSize = with(LocalDensity.current) { MARK_GLYPH.toSp() }
        val markStyle = MaterialTheme.typography.titleMedium.copy(
            fontSize = markSize,
            lineHeight = markSize,
            fontWeight = FontWeight.Bold,
        )
        Box(contentAlignment = Alignment.Center) {
            when (state) {
                StatusCheck.Ok -> Icon(
                    painterResource(UmamiIcons.Check),
                    contentDescription = null,
                    Modifier.size(18.dp),
                )

                StatusCheck.Missing -> Text("!", style = markStyle)

                StatusCheck.Unavailable -> Text("–", style = markStyle)
            }
        }
    }
}

/** The ! and – glyphs: 16 dp in the 28 dp circle, whatever the font scale. */
private val MARK_GLYPH = 16.dp
