package com.umamit.core.data.catalog

import com.umamit.core.catalog.parser.TeeKontorParser
import com.umamit.core.database.entity.CatalogPackageEntity
import com.umamit.core.database.entity.CatalogTeaEntity
import java.security.MessageDigest
import java.util.HexFormat

/**
 * A catalog tea's `content_hash` (05 §4.1), so a later refresh can report it as „geändert"
 * (CAT-07 AC3, #70): `<parser version>:<SHA-256>` over what the shop shows of the tea, its page's
 * fields, its photo's URL and its package sizes with their prices and stock.
 *
 * Left out are what the app derives itself (style, recipes, the flag, the plain-text description)
 * and the refresh's own bookkeeping (dates, photo file, discontinued): they change with an app
 * update, not with the shop. Two hashes are compared only under the same parser version, as a
 * new parser reads the same page differently (D-086).
 */
internal object CatalogContentHash {
    private const val FIELD = '\u001f'
    private const val ROW = '\u001e'
    private const val NULL = "\u0000"

    fun of(tea: CatalogTeaEntity, packages: List<CatalogPackageEntity>): String {
        val text = buildString {
            listOf(
                tea.name, tea.secondaryName, tea.productUrl, tea.shopCategory, tea.teaType,
                tea.originCountry, tea.originRegion, tea.cultivar, tea.harvest, tea.shading, tea.steaming,
                tea.isOrganic, tea.isFlavored, tea.coldBrewSuitable, tea.aroma, tea.tasteNotes,
                tea.manufacturer, tea.season, tea.matchaGrade, tea.tasteScalesJson, tea.breadcrumb,
                tea.ingredients, tea.descriptionHtml, tea.brewTableRaw, tea.brewTextRaw,
                tea.basePriceCentsPer100g, tea.available, tea.photoUrl,
            ).forEach { append(it ?: NULL).append(FIELD) }
            packages.sortedBy { it.grams }.forEach { pkg ->
                append(ROW)
                listOf(
                    pkg.articleNumber,
                    pkg.label,
                    pkg.grams,
                    pkg.priceCents,
                    pkg.priceIsEstimate,
                    pkg.savingsPercent,
                    pkg.variantUrl,
                    pkg.available,
                ).forEach { append(it ?: NULL).append(FIELD) }
            }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return "${TeeKontorParser.PARSER_VERSION}:${HexFormat.of().formatHex(digest)}"
    }

    /**
     * Whether the shop changed a tea between two refreshes: both hashes known and of the same
     * parser version, and different. A row from before #70 has no hash and counts as unchanged.
     */
    fun changed(before: String?, now: String?): Boolean {
        if (before == null || now == null) return false
        return before.substringBefore(':') == now.substringBefore(':') && before != now
    }
}
