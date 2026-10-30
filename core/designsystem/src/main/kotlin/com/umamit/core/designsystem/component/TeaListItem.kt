package com.umamit.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
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
 * TalkBack reads the row as one item; with [onClick] it is a button with a chevron. [subtitle]
 * is one line under the title, cut off when longer (e.g. a catalog tea's secondary name, CAT-10).
 * [badges] are chips below the meta line, wrapping when they don't fit (e.g. the catalog's „Neu",
 * Bio, „im Besitz" and [CheckRecipeBadge]; SCR-19, CAT-10).
 * [onLongClick] (with a haptic tick, read by TalkBack as [onLongClickLabel]) opens a row's menu,
 * and [customActions] offer its actions to TalkBack directly (e.g. the tea picker's *Aus Sammlung
 * entfernen*, SCR-03). [trailing] is a control of its own at the row's end, before the chevron
 * (e.g. the catalog's bookmark toggle, CAT-18); TalkBack reads it apart from the row.
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
    subtitle: String? = null,
    badges: (@Composable FlowRowScope.() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    customActions: List<CustomAccessibilityAction> = emptyList(),
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .then(
                    when {
                        onLongClick != null -> Modifier.combinedClickable(
                            onLongClickLabel = onLongClickLabel,
                            onLongClick = onLongClick,
                            onClick = onClick ?: {},
                        )

                        onClick != null -> Modifier.clickable(onClick = onClick)

                        else -> Modifier
                    },
                )
                .semantics(mergeDescendants = true) {
                    if (customActions.isNotEmpty()) this.customActions = customActions
                }
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
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (badges != null) {
                    // Wraps onto further lines at a large font scale (03 §6).
                    FlowRow(
                        modifier = Modifier.padding(top = UmamiSpacing.XXSmall),
                        horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.XXSmall),
                        verticalArrangement = Arrangement.spacedBy(UmamiSpacing.XXSmall),
                        itemVerticalAlignment = Alignment.CenterVertically,
                        content = badges,
                    )
                }
            }
            if (rating != null) {
                Spacer(Modifier.width(UmamiSpacing.Small))
                Rating(rating)
            }
            trailing?.invoke()
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
