package com.umamit.core.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.umamit.core.designsystem.theme.UmamiSpacing
import androidx.compose.ui.semantics.onClick as semanticsOnClick

private const val CHIP_BORDER_ALPHA = 0.6f

/**
 * The toggle form of `DescriptorChip` (03 §3.8, JRN-04): [selected] is ticked. TalkBack reads „Nori, ausgewählt".
 * Outlined at >= 3:1 against the background while off (WCAG 1.4.11, D-065). A chip that is not [enabled] (the
 * 20 descriptors are chosen) is greyed.
 */
@Composable
fun DescriptorChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val state = stringResource(if (selected) R.string.ui_descriptor_selected else R.string.ui_descriptor_not_selected)
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.semantics { stateDescription = state },
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = CHIP_BORDER_ALPHA),
        ),
    )
}

/**
 * The read-only form of `DescriptorChip`: the chosen look, with an optional [count] („Nori · 4", the tea detail's
 * most frequent descriptors; [countFormat] takes the label and the count, the statistics read „Nori · 12×"). With [onClick] it is a button (the session page's chosen chips open the sheet), announced as [onClickLabel].
 */
@Composable
fun DescriptorChip(
    label: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    @StringRes countFormat: Int = R.string.ui_descriptor_with_count,
) {
    val text = if (count == null) label else stringResource(countFormat, label, count)
    val content: @Composable () -> Unit = {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = UmamiSpacing.Small, vertical = UmamiSpacing.XSmall),
        )
    }
    val shape = FilterChipDefaults.shape
    val color = MaterialTheme.colorScheme.secondaryContainer
    val border = BorderStroke(UmamiSpacing.Hairline, MaterialTheme.colorScheme.outlineVariant)
    if (onClick == null) {
        Surface(modifier = modifier, shape = shape, color = color, border = border, content = content)
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier.semantics { semanticsOnClick(label = onClickLabel, action = null) },
            shape = shape,
            color = color,
            border = border,
            content = content,
        )
    }
}
