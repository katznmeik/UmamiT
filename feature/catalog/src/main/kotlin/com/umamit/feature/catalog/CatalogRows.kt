package com.umamit.feature.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.component.SectionHeader
import com.umamit.core.designsystem.component.TeaListItem
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.model.CatalogListing
import com.umamit.core.model.CatalogTeaSummary

/**
 * The list's teas (SCR-19): under a header per *Sorte* when grouped (D-073), else one flat list
 * whose rows name their *Sorte*.
 */
internal fun LazyListScope.teaRows(
    listing: CatalogListing,
    onOpenTea: (String) -> Unit,
    thumbnail: @Composable (path: String?) -> Painter?,
) {
    listing.sections.forEach { section ->
        if (listing.grouped) {
            item(key = "type:${section.teaType}", contentType = "section") {
                SectionHeader(
                    title = section.teaType ?: stringResource(R.string.catalog_group_untyped),
                    modifier = Modifier.padding(top = UmamiSpacing.Small),
                )
            }
        }
        itemsIndexed(section.teas, key = { _, tea -> tea.key }, contentType = { _, _ -> "tea" }) { index, tea ->
            TeaRow(
                tea = tea,
                showType = !listing.grouped,
                onClick = { onOpenTea(tea.key) },
                thumbnail = thumbnail(tea.thumbnailPath),
                showDivider = index < section.teas.lastIndex,
            )
        }
    }
}

/** A catalog tea's row (CAT-10): name, *Sorte* (when not grouped), origin, price per 100 g and its badges. */
@Composable
private fun TeaRow(
    tea: CatalogTeaSummary,
    showType: Boolean,
    onClick: () -> Unit,
    thumbnail: Painter?,
    showDivider: Boolean,
) {
    val separator = stringResource(R.string.catalog_meta_separator)
    val price = tea.basePriceCentsPer100g?.let {
        stringResource(R.string.catalog_price_per_100g, CatalogFormat.euros(it))
    }
    val badges = rowBadges(tea)
    TeaListItem(
        title = tea.name,
        meta = listOfNotNull(tea.teaType?.takeIf { showType }, tea.originCountry, price).joinToString(separator),
        onClick = onClick,
        thumbnail = thumbnail,
        placeholderColor = placeholderColor(tea.teaType),
        showDivider = showDivider,
        badges = if (badges.isEmpty()) null else ({ Badges(badges) }),
    )
}

/** How a row's badge looks: „Neu" stands out, „Rezept prüfen" warns, the rest are plain tags. */
private enum class BadgeKind { NEW, CHECK, PLAIN }

/** A badge of a catalog row (SCR-19): „Neu", „im Besitz", Bio, aromatisiert, „Rezept prüfen" (CAT-10). */
private data class Badge(val text: String, val kind: BadgeKind)

@Composable
private fun rowBadges(tea: CatalogTeaSummary): List<Badge> = buildList {
    if (tea.isNew) add(Badge(stringResource(R.string.catalog_badge_new), BadgeKind.NEW))
    if (tea.owned) add(Badge(stringResource(R.string.catalog_badge_owned), BadgeKind.PLAIN))
    if (tea.isOrganic) add(Badge(stringResource(R.string.catalog_tea_organic), BadgeKind.PLAIN))
    if (tea.isFlavored) add(Badge(stringResource(R.string.catalog_tea_flavored), BadgeKind.PLAIN))
    if (tea.needsRecipeCheck) add(Badge(stringResource(R.string.catalog_badge_recipe_check), BadgeKind.CHECK))
}

@Composable
private fun Badges(badges: List<Badge>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.XXSmall),
        verticalArrangement = Arrangement.spacedBy(UmamiSpacing.XXSmall),
    ) {
        badges.forEach { BadgeTag(it) }
    }
}

@Composable
private fun BadgeTag(badge: Badge) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (badge.kind) {
        BadgeKind.NEW -> colors.primary to colors.onPrimary
        BadgeKind.CHECK -> colors.tertiaryContainer to colors.onTertiaryContainer
        BadgeKind.PLAIN -> colors.secondaryContainer to colors.onSecondaryContainer
    }
    Surface(shape = CircleShape, color = container) {
        Text(
            text = badge.text,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            modifier = Modifier.padding(horizontal = UmamiSpacing.XSmall, vertical = BadgeVerticalPadding),
        )
    }
}

private val BadgeVerticalPadding = 2.dp
