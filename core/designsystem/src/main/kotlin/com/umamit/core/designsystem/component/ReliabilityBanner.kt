package com.umamit.core.designsystem.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.R
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiShape
import com.umamit.core.designsystem.theme.UmamiSpacing

/**
 * Quiet warning bar with *Beheben* (03 §3.8), e.g. when exact alarms or
 * notifications are off. It is calm on purpose: a hint, not an alarm. TalkBack
 * announces the message politely when the banner appears.
 */
@Composable
fun ReliabilityBanner(message: String, onFix: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.ds_reliability_description, message)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = UmamiShape.Card,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(start = UmamiSpacing.Medium, end = UmamiSpacing.XSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(UmamiIcons.Warning),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.width(UmamiSpacing.Small))
            Text(
                text = message,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = UmamiSpacing.Small)
                    .semantics {
                        contentDescription = description
                        liveRegion = LiveRegionMode.Polite
                    },
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onFix) {
                Text(text = stringResource(R.string.ds_reliability_fix), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
