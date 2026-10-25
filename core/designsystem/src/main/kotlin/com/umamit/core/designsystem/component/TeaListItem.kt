package com.umamit.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.R
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.text.ratingText
import com.umamit.core.designsystem.theme.UmamiShape
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme

private val ThumbnailSize = 48.dp
private const val PLACEHOLDER_FILL_ALPHA = 0.22f
private const val TITLE_MAX_LINES = 2

/**
 * A tea row (03 §3.8): 48 dp round thumbnail, serif title, meta line
 * („8. Juni · 3 Min. · 80 °C"), trailing „★ 4,5", hairline divider. Without a
 * [thumbnail], a leaf in the tea type's [placeholderColor] stands in (03 §3.5).
 * TalkBack reads the row as one item; with [onClick] it is a button with a chevron. [badges]
 * are chips below the meta line (e.g. [CheckRecipeBadge], CAT-10).
 */
@Composable
fun TeaListItem(
    title: String,
    meta: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    rating: Float? = null,
    thumbnail: Painter? = null,
    placeholderColor: Color = UmamiTheme.extendedColors.teaTypes.other,
    showDivider: Boolean = true,
    badges: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .semantics(mergeDescendants = true) {}
                .defaultMinSize(minHeight = 72.dp)
                .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TeaThumbnail(thumbnail = thumbnail, placeholderColor = placeholderColor)
            Spacer(Modifier.width(UmamiSpacing.Medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = TITLE_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (badges != null) {
                    Row(
                        modifier = Modifier.padding(top = UmamiSpacing.XXSmall),
                        horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.XXSmall),
                        verticalAlignment = Alignment.CenterVertically,
                        content = badges,
                    )
                }
            }
            if (rating != null) {
                Spacer(Modifier.width(UmamiSpacing.Small))
                Rating(rating)
            }
            if (onClick != null) {
                Spacer(Modifier.width(UmamiSpacing.XSmall))
                Icon(
                    painter = painterResource(UmamiIcons.ChevronRight),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(
                    start = UmamiSpacing.SideMargin + ThumbnailSize + UmamiSpacing.Medium,
                    end = UmamiSpacing.SideMargin,
                ),
                thickness = UmamiSpacing.Hairline,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/**
 * The 48 dp round tea thumbnail of a row (03 §3.8): the photo, or a leaf in the
 * tea type's [placeholderColor] without one (03 §3.5). Decorative for TalkBack.
 */
@Composable
fun TeaThumbnail(thumbnail: Painter?, placeholderColor: Color, modifier: Modifier = Modifier) {
    if (thumbnail != null) {
        Image(
            painter = thumbnail,
            contentDescription = null,
            modifier = modifier
                .size(ThumbnailSize)
                .clip(UmamiShape.Thumbnail),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = modifier
                .size(ThumbnailSize)
                .clip(UmamiShape.Thumbnail)
                .background(placeholderColor.copy(alpha = PLACEHOLDER_FILL_ALPHA)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(UmamiIcons.Leaf),
                contentDescription = null,
                tint = placeholderColor,
            )
        }
    }
}

@Composable
private fun Rating(rating: Float) {
    val value = ratingText(rating)
    val description = stringResource(R.string.ds_rating_description, value)
    Text(
        text = stringResource(R.string.ds_rating, value),
        modifier = Modifier.semantics { contentDescription = description },
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
}
