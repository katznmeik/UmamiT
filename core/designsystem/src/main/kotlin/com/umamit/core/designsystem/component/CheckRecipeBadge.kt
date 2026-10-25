package com.umamit.core.designsystem.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.R
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing

private val IconSize = 16.dp

/**
 * „Rezept prüfen" (03 §3.8, CAT-05): a quiet chip with the warning icon for a tea whose recipe
 * did not come straight from the shop's brewing table. TalkBack reads its text.
 */
@Composable
fun CheckRecipeBadge(modifier: Modifier = Modifier) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = UmamiSpacing.XSmall, vertical = UmamiSpacing.XXSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(UmamiIcons.Warning),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IconSize),
            )
            Spacer(Modifier.width(UmamiSpacing.XXSmall))
            Text(
                text = stringResource(R.string.ds_check_recipe),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
