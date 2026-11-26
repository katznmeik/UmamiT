package com.umamit.feature.onboarding.mockup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing

/** A mock-up's screen: the theme's paper, content top to bottom. */
@Composable
fun MockupScreen(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize(), content = content)
    }
}

/**
 * A simplified top bar: [title] in the display sans, ← when [back], and on the right ⋮ ([menu]) or the settings
 * gear ([settings]). The icons are drawn only; a mock-up has no actions.
 */
@Composable
fun MockupTopBar(
    title: String,
    modifier: Modifier = Modifier,
    back: Boolean = false,
    menu: Boolean = false,
    settings: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .height(TOP_BAR_HEIGHT)
            .padding(horizontal = UmamiSpacing.XSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (back) MockupIcon(UmamiIcons.ArrowBack)
        Text(
            text = title,
            modifier = Modifier.weight(1f).padding(horizontal = UmamiSpacing.XSmall),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (menu) MockupIcon(UmamiIcons.MoreVert)
        if (settings) MockupIcon(UmamiIcons.Settings)
    }
}

/** A 24 dp icon in a 40 dp slot, as a top bar shows it. */
@Composable
fun MockupIcon(icon: Int, modifier: Modifier = Modifier) {
    Box(modifier.size(ICON_SLOT), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
    }
}

/** A blank bar standing for text that does not matter in this mock-up (SCR-36 „Mock-ups"). */
@Composable
fun MockupBar(width: Dp, modifier: Modifier = Modifier, height: Dp = BAR_HEIGHT) {
    Box(
        modifier
            .width(width)
            .height(height)
            .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(height / 2)),
    )
}

/** Blank bars of [widths], one below the other, for a block of text that does not matter. */
@Composable
fun MockupBars(widths: List<Dp>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall)) {
        widths.forEach { MockupBar(it) }
    }
}

/**
 * An open ⋮ menu drawn in place (a real `DropdownMenu` opens a window of its own and would leave the frame): a
 * raised surface at the top right with [items] top to bottom. An item is drawn by its slot.
 */
@Composable
fun MockupMenu(modifier: Modifier = Modifier, items: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = MENU_ELEVATION,
        tonalElevation = MENU_ELEVATION,
    ) {
        Column(Modifier.padding(vertical = UmamiSpacing.XSmall), content = items)
    }
}

/**
 * The open ⋮ menu at the top right, as on the phone (D-137): at most [MOCKUP_MENU_MAX_WIDTH] wide, so a long item
 * wraps instead of reaching across the screen; it opens at [top].
 */
@Composable
fun BoxScope.MockupOverflowMenu(
    modifier: Modifier = Modifier,
    top: Dp = MOCKUP_MENU_TOP,
    items: @Composable ColumnScope.() -> Unit,
) {
    MockupMenu(
        modifier
            .align(Alignment.TopEnd)
            .padding(top = top, end = UmamiSpacing.XXSmall)
            .widthIn(max = MOCKUP_MENU_MAX_WIDTH),
        items,
    )
}

/** One menu item with its text; a long one wraps to a second line. */
@Composable
fun MockupMenuItem(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = MENU_ITEM_HEIGHT)
            .padding(horizontal = UmamiSpacing.Small, vertical = UmamiSpacing.XXSmall),
        Alignment.CenterStart,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** One menu item as a blank bar. */
@Composable
fun MockupMenuBar(width: Dp, modifier: Modifier = Modifier) {
    Box(modifier.height(MENU_ITEM_HEIGHT).padding(horizontal = UmamiSpacing.Small), Alignment.CenterStart) {
        MockupBar(width)
    }
}

/** A gap between blocks. */
@Composable
fun MockupGap(modifier: Modifier = Modifier, height: Dp = UmamiSpacing.Small) {
    Spacer(modifier.height(height))
}

internal val TOP_BAR_HEIGHT = 56.dp
private val ICON_SLOT = 40.dp
private val BAR_HEIGHT = 10.dp
private val MENU_ITEM_HEIGHT = 40.dp
private val MENU_ELEVATION = 3.dp

/** Where a drawn ⋮ menu opens: just under the top bar's ⋮. */
val MOCKUP_MENU_TOP = 44.dp

/** The widest a drawn ⋮ menu gets on the [MOCKUP_SCREEN_WIDTH] screen. */
val MOCKUP_MENU_MAX_WIDTH = 190.dp

/** A long and a medium blank menu item, both within [MOCKUP_MENU_MAX_WIDTH]. */
val MOCKUP_MENU_BAR_LONG = 150.dp
val MOCKUP_MENU_BAR_MEDIUM = 110.dp
