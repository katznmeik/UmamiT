package com.umamit.core.catalog.parser

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/**
 * One tea of the catalog: its [key] (05 §4.1), the [page] its details come from and its
 * [packages] (05 §4.2), smallest first.
 *
 * @property ownSizes the sizes a fetched page of this tea showed as its own, at that page's price.
 * @property mergedKeys the other base numbers merged into this entry (08 §2.4 item 7), for the log.
 */
data class CatalogEntry(
    val key: String,
    val page: ProductPage,
    val packages: List<ShopPackage>,
    val ownSizes: Set<Double>,
    val mergedKeys: List<String> = emptyList(),
) {
    companion object {
        /** The entry of one fetched [page] of [group]: keyed by the page's number, else the group's. */
        fun of(group: ProductGroup, page: ProductPage): CatalogEntry {
            val packages = Packages.of(page, group)
            val own = page.packageGrams ?: page.sizeOptions.firstOrNull { it.selected }?.grams
            return CatalogEntry(
                key = page.key ?: group.base,
                page = page,
                packages = packages,
                ownSizes = setOfNotNull(own),
            )
        }
    }
}

/**
 * Merges the teas the shop sells under one base number per size (08 §2.4 item 7, e.g. a 50 g
 * and a 100 g tin with numbers of their own): entries with the same [normalisedName] become one
 * entry under the lowest base number (string order), whose page gives the details. Only entries
 * whose own sizes differ are merged: two pages of the same name and size are two teas (e.g. two
 * harvests), and a size can be a package only once (D-081).
 */
object CatalogEntries {
    /** A size in a name or slug: „30g", „100 g", „0,5kg" as a word of its own. */
    private val SIZE = Regex("""(?<![\p{L}\p{N}])\d+(?:[.,]\d+)?\s*k?g(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)
    private val NOT_A_WORD = Regex("""[^\p{L}\p{N}]+""")

    /** [entries] with split numbers merged, sorted by key. */
    fun merge(entries: List<CatalogEntry>): List<CatalogEntry> = entries
        .groupBy { entry -> nameOf(entry.page)?.let(::normalisedName)?.ifEmpty { null } ?: "\u0000${entry.key}" }
        .values
        .flatMap { sameName ->
            sameName.sortedBy { it.key }.fold(mutableListOf<CatalogEntry>()) { merged, entry ->
                val index = merged.indexOfFirst { it.splitFrom(entry) }
                if (index < 0) merged += entry else merged[index] = merged[index].absorb(entry)
                merged
            }
        }
        .sortedBy { it.key }

    /** [name] lower-case, without sizes and punctuation: „Bio Abendrot-Matcha 30g" → „bio abendrot matcha". */
    fun normalisedName(name: String): String =
        name.lowercase(Locale.GERMAN).replace(SIZE, " ").replace(NOT_A_WORD, " ").trim()

    /** The page's name, else its URL's slug (`abendrot-matcha-30g`). */
    private fun nameOf(page: ProductPage): String? = page.name ?: try {
        URI(page.productUrl).path?.split('/')?.firstOrNull { it.isNotEmpty() }
    } catch (_: URISyntaxException) {
        null
    }

    /** The same tea split by size: both pages show an own size, and no size is on both. */
    private fun CatalogEntry.splitFrom(other: CatalogEntry): Boolean =
        ownSizes.isNotEmpty() && other.ownSizes.isNotEmpty() && ownSizes.none { it in other.ownSizes }

    /** This entry (the lower key) with [other]'s sizes; a size's own page wins over an estimate. */
    private fun CatalogEntry.absorb(other: CatalogEntry): CatalogEntry {
        val packages = (packages + other.packages).groupBy { it.grams }.map { (grams, candidates) ->
            val owner = if (grams in other.ownSizes) other.packages else packages
            owner.firstOrNull { it.grams == grams } ?: candidates.maxBy { it.rank() }
        }.sortedBy { it.grams }
        val available = listOf(page.available, other.page.available).let { stock ->
            when {
                true in stock -> true
                false in stock -> false
                else -> null
            }
        }
        return copy(
            page = page.copy(
                available = available,
                basePriceCentsPer100g = page.basePriceCentsPer100g ?: other.page.basePriceCentsPer100g,
            ),
            packages = packages,
            ownSizes = ownSizes + other.ownSizes,
            mergedKeys = mergedKeys + other.key + other.mergedKeys,
        )
    }

    /** An exact price before an estimate before none. */
    private fun ShopPackage.rank(): Int = when {
        priceCents == null -> 0
        priceIsEstimate -> 1
        else -> 2
    }
}
