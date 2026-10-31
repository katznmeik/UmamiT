package com.umamit.feature.catalog

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.text.germanUppercase
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme

/** The foldable header of the *Sorte* group [teaType] (null = „Ohne Sorte"; SCR-19, D-098). */
fun catalogGroupTag(teaType: String?): String = "catalog_group_${teaType ?: "-"}"

/** The chevron's turn from open (pointing down) to folded (pointing right). */
private const val OPEN_DEGREES = 90f

/**
 * A *Sorte* header that folds its group (v0.3.0, CAT-10, SCR-19, D-098): the overline „BANCHA · 12" ([count] =
 * the group's teas under the current filters), the hairline and a chevron, down while open and right while
 * [folded]. The whole row is the 48 dp toggle; TalkBack reads „Bancha, 12 Tees, eingeklappt" with the click action
 * „Ausklappen" („ausgeklappt", „Einklappen"). Only the chevron turns (short, a snap under reduce motion); the
 * list's content snaps, as it is browsed often.
 */
@Composable
internal fun CatalogGroupHeader(
    title: String,
    count: Int,
    folded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = pluralStringResource(R.plurals.catalog_group_description, count, title, count)
    val state = stringResource(if (folded) R.string.catalog_group_folded else R.string.catalog_group_unfolded)
    val action = stringResource(if (folded) R.string.catalog_group_unfold else R.string.catalog_group_fold)
    val turn by animateFloatAsState(
        targetValue = if (folded) 0f else OPEN_DEGREES,
        animationSpec = UmamiTheme.motion.exit(),
        label = "chevron",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = UmamiSpacing.MinTouchTarget)
            .clickable(onClickLabel = action, onClick = onToggle)
            .semantics {
                heading()
                contentDescription = description
                stateDescription = state
            }
            .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.XSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.catalog_group_title, title, count).germanUppercase(),
            style = UmamiTheme.textStyles.overline,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(UmamiSpacing.Small))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = UmamiSpacing.Hairline,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.width(UmamiSpacing.Small))
        Icon(
            painter = painterResource(UmamiIcons.ChevronRight),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.rotate(turn),
        )
    }
}

/**
 * The ⋮ menu's *Alle einklappen* / *Alle ausklappen* counters (D-098): each increase while this is shown folds or
 * unfolds every group of the view; a count from before is never replayed (D-049). Tells [onFoldableChange] whether
 * the view shown folds at all.
 */
@Composable
@Suppress("LongParameterList") // the view's state, the two counters and their three answers
internal fun FoldRequests(
    foldable: Boolean,
    foldAllRequest: Int,
    unfoldAllRequest: Int,
    onFoldableChange: (Boolean) -> Unit,
    onFoldAll: () -> Unit,
    onUnfoldAll: () -> Unit,
) {
    val currentOnFoldableChange by rememberUpdatedState(onFoldableChange)
    val currentOnFoldAll by rememberUpdatedState(onFoldAll)
    val currentOnUnfoldAll by rememberUpdatedState(onUnfoldAll)
    LaunchedEffect(foldable) { currentOnFoldableChange(foldable) }
    var handledFold by remember { mutableIntStateOf(foldAllRequest) }
    LaunchedEffect(foldAllRequest) {
        if (foldAllRequest > handledFold) {
            handledFold = foldAllRequest
            currentOnFoldAll()
        }
    }
    var handledUnfold by remember { mutableIntStateOf(unfoldAllRequest) }
    LaunchedEffect(unfoldAllRequest) {
        if (unfoldAllRequest > handledUnfold) {
            handledUnfold = unfoldAllRequest
            currentOnUnfoldAll()
        }
    }
}
