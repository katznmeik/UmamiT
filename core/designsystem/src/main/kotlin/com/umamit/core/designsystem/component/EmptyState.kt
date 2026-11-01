package com.umamit.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing

/**
 * Calm empty state (03 §3.8, GEN-09): illustration, serif title, body and an
 * optional button; without a [body] the title stands alone („Kein Tee passt zu
 * deiner Suche."). The illustration is decorative; the default is the
 * placeholder landscape, tinted from the theme so it works in both schemes.
 * A finished illustration (a light and a dark WebP whose paper is the theme's
 * background, docs/design/assets.md) passes `illustrationTint = null`.
 */
@Composable
fun EmptyState(
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    illustration: Painter = painterResource(UmamiIcons.LandscapePlaceholder),
    illustrationTint: ColorFilter? = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
) {
    Column(
        modifier = modifier.padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.XLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = illustration,
            contentDescription = null,
            modifier = Modifier
                .widthIn(max = 240.dp)
                .fillMaxWidth(),
            contentScale = ContentScale.FillWidth,
            colorFilter = illustrationTint,
        )
        Spacer(Modifier.height(UmamiSpacing.Large))
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (body != null) {
            Spacer(Modifier.height(UmamiSpacing.XSmall))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(UmamiSpacing.Large))
            PillButton(text = actionLabel, onClick = onAction)
        }
    }
}
