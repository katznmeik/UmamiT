package com.umamit.feature.catalog

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
import com.umamit.core.designsystem.component.CheckRecipeBadge
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

/**
 * A catalog tea's row (CAT-10): name, secondary name (one line, D-090), *Sorte* (when not grouped),
 * origin, price per 100 g and its badges.
 */
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
    val check = tea.recipeCheckReason != null
    TeaListItem(
        title = tea.name,
        subtitle = tea.secondaryName?.takeIf { it.isNotBlank() },
        meta = listOfNotNull(tea.teaType?.takeIf { showType }, tea.originCountry, price).joinToString(separator),
        onClick = onClick,
        thumbnail = thumbnail,
        placeholderColor = placeholderColor(tea.teaType),
        showDivider = showDivider,
        badges = if (badges.isEmpty() && !check) {
            null
        } else {
            {
                badges.forEach { BadgeTag(it) }
                // „Rezept prüfen" (CAT-05, CAT-10), the same chip as everywhere in the app.
                if (check) CheckRecipeBadge()
            }
        },
    )
}

/** A badge of a catalog row (SCR-19, CAT-10): „Neu" stands out; „im Besitz", Bio and aromatisiert are plain tags. */
private data class Badge(val text: String, val prominent: Boolean = false)

@Composable
private fun rowBadges(tea: CatalogTeaSummary): List<Badge> = buildList {
    if (tea.isNew) add(Badge(stringResource(R.string.catalog_badge_new), prominent = true))
    if (tea.owned) add(Badge(stringResource(R.string.catalog_badge_owned)))
    if (tea.isOrganic) add(Badge(stringResource(R.string.catalog_tea_organic)))
    if (tea.isFlavored) add(Badge(stringResource(R.string.catalog_tea_flavored)))
}

@Composable
private fun BadgeTag(badge: Badge) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = CircleShape, color = if (badge.prominent) colors.primary else colors.secondaryContainer) {
        Text(
            text = badge.text,
            style = MaterialTheme.typography.labelMedium,
            color = if (badge.prominent) colors.onPrimary else colors.onSecondaryContainer,
            // The same height as „Rezept prüfen" beside it (CheckRecipeBadge).
            modifier = Modifier.padding(horizontal = UmamiSpacing.XSmall, vertical = UmamiSpacing.XXSmall),
        )
    }
}
