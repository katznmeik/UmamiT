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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.umamit.core.designsystem.component.CheckRecipeBadge
import com.umamit.core.designsystem.component.SectionHeader
import com.umamit.core.designsystem.component.TeaListItem
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.CatalogFilter
import com.umamit.core.model.CatalogKind
import com.umamit.core.model.CatalogListing
import com.umamit.core.model.CatalogTeaSummary
import com.umamit.core.ui.GermanFormat

/**
 * The list's teas (SCR-19): under a header per *Sorte* when grouped (D-073), else one flat list
 * whose rows name their *Sorte*. Each tea not in the collection ends with its bookmark toggle
 * ([onToggleMark] with its key and new state, CAT-18). While the groups fold ([folding], D-098) each header is a
 * toggle ([CatalogGroupHeader]) and a folded group shows its header only, with no motion: its rows snap.
 */
internal fun LazyListScope.teaRows(
    listing: CatalogListing,
    onOpenTea: (String) -> Unit,
    onToggleMark: (key: String, marked: Boolean) -> Unit,
    thumbnail: @Composable (path: String?) -> Painter?,
    folding: GroupFolding? = null,
) {
    listing.sections.forEach { section ->
        val folded = folding?.isFolded(section.teaType) == true
        if (listing.grouped) {
            item(key = "type:${section.teaType}", contentType = if (folding != null) "fold_section" else "section") {
                val title = section.teaType ?: stringResource(R.string.catalog_group_untyped)
                if (folding != null) {
                    CatalogGroupHeader(
                        title = title,
                        count = section.teas.size,
                        folded = folded,
                        onToggle = { folding.onToggle(section.teaType) },
                        modifier = Modifier
                            .padding(top = UmamiSpacing.Small)
                            .testTag(catalogGroupTag(section.teaType)),
                    )
                } else {
                    SectionHeader(title = title, modifier = Modifier.padding(top = UmamiSpacing.Small))
                }
            }
        }
        if (folded) return@forEach
        itemsIndexed(section.teas, key = { _, tea -> tea.key }, contentType = { _, _ -> "tea" }) { index, tea ->
            TeaRow(
                tea = tea,
                showType = !listing.grouped,
                onClick = { onOpenTea(tea.key) },
                onToggleMark = { onToggleMark(tea.key, it) },
                thumbnail = thumbnail(tea.thumbnailPath),
                showDivider = index < section.teas.lastIndex,
            )
        }
    }
}

/**
 * A kind's pending hint above its list (D-093 Q20, D-108) and the no-match notice, whose „Filter zurücksetzen" clears
 * the kind too (D-114).
 */
internal fun LazyListScope.viewNotices(state: CatalogUiState, actions: ListActions) {
    val listing = state.listing
    // Before the first refresh after the update a new kind is still to come (D-093 Q20, D-108).
    val kind = state.filter.kind
    val pendingHint = kind != null && kind in state.pendingKinds
    if (pendingHint) item(key = "kind_pending") { PendingKindRow(kind, state.isRefreshing, actions) }
    val pendingOnly = if (kind == null) state.pendingKinds.isNotEmpty() else pendingHint
    if (listing.sections.isEmpty() && !(pendingOnly && listing.totalCount == 0)) {
        item(key = "no_match") {
            NoticeRow(
                stringResource(R.string.catalog_no_match),
                stringResource(R.string.catalog_reset_filters),
            ) { actions.onFilterChange { CatalogFilter.DEFAULT.copy(search = it.search, sort = it.sort) } }
        }
    }
}

/** The teas: by kind without one (D-114), else by *Sorte* or flat as the sort says (D-073, D-098). */
internal fun LazyListScope.listRows(
    state: CatalogUiState,
    actions: ListActions,
    thumbnail: @Composable (path: String?) -> Painter?,
) {
    val listing = state.listing
    if (listing.byKind && !state.filter.marked) {
        val folding = if (state.foldable) KindFolding(state::isKindFolded, actions.onToggleKindFold) else null
        kindGroups(
            listing = listing,
            pendingKinds = state.pendingKinds,
            pendingHint = { kind -> PendingKindRow(kind, state.isRefreshing, actions) },
            rows = TeaRowActions(actions.onOpenTea, actions.onToggleMark, thumbnail),
            folding = folding,
        )
    } else {
        val folding = if (state.foldable) GroupFolding(state::isFolded, actions.onToggleFold) else null
        teaRows(listing, actions.onOpenTea, actions.onToggleMark, thumbnail, folding)
    }
}

/** The groups' folding in a view where they fold (D-098): which are folded, and a header's tap. */
internal class GroupFolding(val isFolded: (teaType: String?) -> Boolean, val onToggle: (teaType: String?) -> Unit)

/** The kind groups' folding in the view without a kind (D-114): which are folded, and a header's tap. */
internal class KindFolding(val isFolded: (CatalogKind) -> Boolean, val onToggle: (CatalogKind) -> Unit)

/** What a tea row does: opens the tea, toggles its bookmark (CAT-18), and loads its thumbnail. */
internal class TeaRowActions(
    val onOpenTea: (String) -> Unit,
    val onToggleMark: (key: String, marked: Boolean) -> Unit,
    val thumbnail: @Composable (path: String?) -> Painter?,
)

/**
 * The list without a kind (v0.6.1, SCR-19, D-114): a group per kind in the chip order, under its header „Grüntee ·
 * 274" (a toggle while the groups fold, [folding]); inside, with *Sortierung* *Sorte* the *Sorte* headings without
 * folding of their own, else the kind's teas in the sort's order, each row naming its *Sorte*. A kind still in
 * [pendingKinds] shows its [pendingHint] as its group's body (above any teas it has); a kind with no tea shown and
 * none to come is left out. A folded group shows its header only, with no motion.
 */
internal fun LazyListScope.kindGroups(
    listing: CatalogListing,
    pendingKinds: Set<CatalogKind>,
    pendingHint: @Composable (CatalogKind) -> Unit,
    rows: TeaRowActions,
    folding: KindFolding?,
) {
    val byKind = listing.sections.groupBy { it.kind }
    CatalogKind.ALL.forEach { kind ->
        val sections = byKind[kind].orEmpty()
        val pending = kind in pendingKinds
        if (sections.isEmpty() && !pending) return@forEach
        val folded = folding?.isFolded(kind) == true
        item(key = "kind:${kind.key}", contentType = "kind_section") {
            CatalogKindHeader(
                title = stringResource(kind.chipLabel),
                count = sections.sumOf { it.teas.size },
                folded = if (folding != null) folded else null,
                onToggle = { folding?.onToggle(kind) },
                modifier = Modifier
                    .padding(top = UmamiSpacing.Small)
                    .testTag(catalogKindGroupTag(kind)),
            )
        }
        if (folded) return@forEach
        if (pending) item(key = "kind_pending:${kind.key}") { pendingHint(kind) }
        sections.forEach { section ->
            if (listing.grouped) {
                item(key = "type:${kind.key}:${section.teaType}", contentType = "section") {
                    SectionHeader(
                        title = section.teaType ?: stringResource(R.string.catalog_group_untyped),
                        modifier = Modifier.padding(top = UmamiSpacing.XXSmall),
                    )
                }
            }
            itemsIndexed(section.teas, key = { _, tea -> tea.key }, contentType = { _, _ -> "tea" }) { index, tea ->
                TeaRow(
                    tea = tea,
                    showType = !listing.grouped,
                    onClick = { rows.onOpenTea(tea.key) },
                    onToggleMark = { rows.onToggleMark(tea.key, it) },
                    thumbnail = rows.thumbnail(tea.thumbnailPath),
                    showDivider = index < section.teas.lastIndex,
                )
            }
        }
    }
}

/**
 * A catalog tea's row (CAT-10): name, secondary name (one line, D-090), *Sorte* (when not grouped),
 * origin, price per 100 g and its badges; a tea not in the collection (an archived one included)
 * ends with its bookmark toggle (CAT-18 AC2).
 */
@Composable
@Suppress("LongParameterList") // the tea, its layout, its two actions, the thumbnail and the divider
private fun TeaRow(
    tea: CatalogTeaSummary,
    showType: Boolean,
    onClick: () -> Unit,
    onToggleMark: (Boolean) -> Unit,
    thumbnail: Painter?,
    showDivider: Boolean,
) {
    val separator = stringResource(R.string.catalog_meta_separator)
    val price = tea.basePriceCentsPer100g?.let {
        stringResource(R.string.catalog_price_per_100g, GermanFormat.euros(it))
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
        trailing = if (tea.owned) {
            null
        } else {
            {
                BookmarkToggle(
                    marked = tea.marked,
                    onToggle = onToggleMark,
                    modifier = Modifier.testTag(catalogMarkTag(tea.key)),
                )
            }
        },
    )
}

/**
 * A badge of a catalog row (SCR-19, CAT-10): „Neu" stands out; „Nicht mehr im Sortiment", „im
 * Besitz", Bio and aromatisiert are plain tags.
 */
private data class Badge(val text: String, val prominent: Boolean = false)

@Composable
private fun rowBadges(tea: CatalogTeaSummary): List<Badge> = buildList {
    // Only the *Gemerkt* view lists a tea no longer in the range (CAT-18 AC3).
    if (tea.discontinued) add(Badge(stringResource(R.string.catalog_tea_discontinued)))
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

/**
 * A8, the catalog's empty-state vignette (SCR-19), for the theme: its paper is the theme's
 * background, so it is drawn untinted (`illustrationTint = null`). Every empty state of the
 * catalog module uses it (the empty catalog, no guides yet, a tea or guide that is gone).
 */
@Composable
internal fun catalogEmptyIllustration(): Painter =
    painterResource(if (UmamiTheme.isDark) UmamiIcons.CatalogEmptyDark else UmamiIcons.CatalogEmptyLight)
