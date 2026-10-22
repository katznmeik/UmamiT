package com.umamit.core.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.theme.UmamiShape
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme

/**
 * Fully rounded pill (03 §3.8): cream on dark, green on light. The label is the
 * TalkBack text, so the optional [icon] is decorative.
 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interactionSource)
    val colors = if (UmamiTheme.isDark) {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    } else {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        )
    }
    Button(
        onClick = onClick,
        modifier = modifier
            .defaultMinSize(minHeight = UmamiSpacing.MinTouchTarget)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
        enabled = enabled,
        shape = UmamiShape.Pill,
        colors = colors,
        elevation = null,
        contentPadding = PaddingValues(horizontal = UmamiSpacing.Large, vertical = UmamiSpacing.Small),
        interactionSource = interactionSource,
    ) {
        if (icon != null) {
            Icon(painter = icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(UmamiSpacing.XSmall))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}
