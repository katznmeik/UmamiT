package com.umamit.feature.onboarding.mockup

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.component.TeaListItem
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.feature.onboarding.R

/** A tea's hero in a mock-up: the type colour washed out, the leaf in the middle (photos are the leaf on paper). */
@Composable
internal fun MockupHero(tea: DemoTea, modifier: Modifier = Modifier, height: Dp = HERO_HEIGHT) {
    Box(
        modifier.fillMaxWidth().height(height).background(tea.color().copy(alpha = HERO_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(UmamiIcons.Leaf), null, Modifier.size(HERO_LEAF), tint = tea.color())
    }
}

/** An outlined field: its [label] over its [value], a blank bar when there is none. */
@Composable
internal fun MockupField(label: String, modifier: Modifier = Modifier, value: String? = null) {
    Column(
        modifier
            .fillMaxWidth()
            .border(UmamiSpacing.Hairline, MaterialTheme.colorScheme.outline, RoundedCornerShape(FIELD_CORNER))
            .padding(horizontal = UmamiSpacing.Small, vertical = UmamiSpacing.XSmall),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (value == null) {
            MockupGap(height = UmamiSpacing.XXSmall)
            MockupBar(FIELD_BAR)
            MockupGap(height = UmamiSpacing.XXSmall)
        } else {
            Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/**
 * *Sammlung* with *Meine Tees* or *Katalog* ([catalog]) selected, the demo teas and the ⋮ menu open at the right
 * with [item] marked between two blank items (M-MYTEAS-MENU, M-CATALOG-MENU). The menu opens just below the
 * segments, so the selected one stays readable (D-137).
 */
@Composable
internal fun CollectionMenuMockup(catalog: Boolean, item: String) {
    Box(Modifier.fillMaxSize()) {
        MockupScreen {
            MockupTopBar(title = stringResource(R.string.mockup_collection_title), menu = true)
            MockupGap(height = UmamiSpacing.XSmall)
            MockupSegments(catalog = catalog)
            MockupGap(height = UmamiSpacing.XSmall)
            MockupDemo.teas.forEach { tea ->
                TeaListItem(
                    title = stringResource(tea.name),
                    meta = stringResource(tea.sorte),
                    onClick = {},
                    placeholderColor = tea.color(),
                )
            }
        }
        MockupOverflowMenu(top = COLLECTION_MENU_TOP) {
            MockupMenuBar(MOCKUP_MENU_BAR_LONG)
            MockupMenuItem(item, Modifier.mockupMarker())
            MockupMenuBar(MOCKUP_MENU_BAR_MEDIUM)
        }
    }
}

/** *Meine Tees* | *Katalog* as a segmented row; [catalog] selects the second, which shows a ✓ as in the app. */
@Composable
internal fun MockupSegments(catalog: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier
            .padding(horizontal = UmamiSpacing.Medium)
            .fillMaxWidth()
            .height(SEGMENT_HEIGHT)
            .border(UmamiSpacing.Hairline, MaterialTheme.colorScheme.outline, CircleShape),
    ) {
        MockupSegment(R.string.mockup_collection_segment_my_teas, !catalog)
        MockupSegment(R.string.mockup_collection_segment_catalog, catalog)
    }
}

@Composable
private fun RowScope.MockupSegment(@StringRes text: Int, selected: Boolean) {
    Box(
        Modifier
            .weight(1f)
            .fillMaxSize()
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val color = with(MaterialTheme.colorScheme) { if (selected) onSecondaryContainer else onSurface }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall),
        ) {
            if (selected) Icon(painterResource(UmamiIcons.Check), null, Modifier.size(SEGMENT_CHECK), tint = color)
            Text(stringResource(text), style = MaterialTheme.typography.labelLarge, color = color)
        }
    }
}

/** The dimmed screen behind a sheet drawn in place. */
@Composable
internal fun MockupScrim(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Color.Black.copy(alpha = SCRIM_ALPHA)))
}

/** A bottom sheet drawn in place (a real one opens a window of its own): rounded top, a handle, then [content]. */
@Composable
internal fun MockupSheet(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = SHEET_CORNER, topEnd = SHEET_CORNER),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = SHEET_ELEVATION,
    ) {
        Column(
            Modifier.padding(horizontal = UmamiSpacing.Medium, vertical = UmamiSpacing.XSmall),
            verticalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(HANDLE_WIDTH, HANDLE_HEIGHT)
                    .background(MaterialTheme.colorScheme.outline, CircleShape),
            )
            content()
        }
    }
}

/** A list row: [text] or a blank bar at the start, [value] at the end if given, a chevron if asked. */
@Composable
internal fun MockupListRow(
    text: String?,
    modifier: Modifier = Modifier,
    value: String? = null,
    chevron: Boolean = false,
    barWidth: Dp = ROW_BAR,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .padding(horizontal = UmamiSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (text == null) {
                MockupBar(barWidth)
            } else {
                Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        if (value != null) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (chevron) {
            Icon(
                painterResource(UmamiIcons.ChevronRight),
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal val SEGMENT_HEIGHT = 36.dp
private val SEGMENT_CHECK = 18.dp

/** Below the top bar, the gap and the segments: the collection's ⋮ menu leaves the segments uncovered. */
private val COLLECTION_MENU_TOP = TOP_BAR_HEIGHT + UmamiSpacing.XSmall + SEGMENT_HEIGHT + UmamiSpacing.XXSmall
private val HERO_HEIGHT = 84.dp
private val HERO_LEAF = 36.dp
private const val HERO_ALPHA = 0.25f
private val FIELD_CORNER = 8.dp
private val FIELD_BAR = 80.dp
private const val SCRIM_ALPHA = 0.32f
private val SHEET_CORNER = 24.dp
private val SHEET_ELEVATION = 6.dp
private val HANDLE_WIDTH = 32.dp
private val HANDLE_HEIGHT = 4.dp
private val ROW_HEIGHT = 44.dp
private val ROW_BAR = 120.dp
