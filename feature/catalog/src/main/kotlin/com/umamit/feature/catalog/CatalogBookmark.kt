package com.umamit.feature.catalog

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.umamit.core.designsystem.component.EmptyState
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.model.CatalogListing

/** The bookmark toggle of catalog tea [key]'s row (SCR-19, CAT-18). */
fun catalogMarkTag(key: String): String = "catalog_mark_$key"

/** The bookmark toggle of a catalog tea's detail (SCR-21, CAT-18). */
const val CATALOG_TEA_MARK_TAG = "catalog_tea_mark"

/**
 * *Merken* (CAT-18, D-097): a bookmark toggle with a 48 dp target, outlined while the tea is not
 * [marked] and filled once it is, read by TalkBack as „Merken" / „Gemerkt", with a short haptic
 * tick. [onToggle] gets the new state.
 */
@Composable
internal fun BookmarkToggle(marked: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    IconToggleButton(
        checked = marked,
        onCheckedChange = { mark ->
            haptics.performHapticFeedback(if (mark) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
            onToggle(mark)
        },
        modifier = modifier,
    ) {
        Icon(
            painter = painterResource(if (marked) UmamiIcons.BookmarkFilled else UmamiIcons.Bookmark),
            contentDescription = stringResource(if (marked) R.string.catalog_marked else R.string.catalog_mark),
            tint = if (marked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The *Gemerkt* view's notices (SCR-19, CAT-18): its empty state without any mark („Noch nichts
 * gemerkt · Tippe bei einem Tee auf 🔖"), or a search that matches none of the marked teas.
 */
internal fun LazyListScope.markedNotices(listing: CatalogListing) {
    when {
        listing.totalCount == 0 -> item(key = "marked_empty") {
            EmptyState(
                title = stringResource(R.string.catalog_marked_empty_title),
                body = stringResource(R.string.catalog_marked_empty_body),
                illustration = catalogEmptyIllustration(),
                illustrationTint = null,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        listing.sections.isEmpty() -> item(key = "marked_no_match") {
            Text(
                text = stringResource(R.string.catalog_marked_no_match),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.Small),
            )
        }
    }
}
