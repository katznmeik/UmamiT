package com.umamit.core.data.catalog

import com.umamit.core.catalog.parser.CatalogEntry
import com.umamit.core.catalog.parser.CatalogRecipes
import com.umamit.core.catalog.parser.ProductPage
import com.umamit.core.catalog.parser.ShopPackage
import com.umamit.core.database.entity.CatalogPackageEntity
import com.umamit.core.database.entity.CatalogTeaEntity

/**
 * A green tea's page as a catalog row first and last seen at [seenAt] (05 §4.1), with its style
 * and derived recipes (CAT-04 – CAT-06), without its photo file yet.
 */
internal fun ProductPage.toEntity(base: String, seenAt: Long): CatalogTeaEntity {
    val recipes = CatalogRecipes.derive(this)
    return CatalogTeaEntity(
        key = key ?: base,
        name = name,
        secondaryName = secondaryName,
        productUrl = productUrl,
        shopCategory = shopCategory,
        teaType = teaType,
        styleKey = recipes.styleKey,
        derivedRecipesJson = DerivedRecipesColumn.json(recipes.recipes),
        recipeCheckReason = recipes.checkReason,
        originCountry = originCountry,
        originRegion = originRegion,
        cultivar = cultivar,
        harvest = harvest,
        shading = shading,
        steaming = steaming,
        isOrganic = isOrganic,
        isFlavored = isFlavored,
        coldBrewSuitable = coldBrewSuitable,
        aroma = aroma,
        tasteNotes = tasteNotes,
        manufacturer = manufacturer,
        season = season,
        matchaGrade = matchaGrade,
        tasteScalesJson = CatalogColumns.tasteScalesJson(tasteScales),
        // The path the style was mapped from, so a collection copy maps the same (#206, user story 36).
        breadcrumb = stylePath,
        ingredients = ingredients,
        descriptionHtml = descriptionHtml,
        descriptionText = descriptionText,
        brewTableRaw = CatalogColumns.brewTableJson(brewTable),
        brewTextRaw = brewText,
        basePriceCentsPer100g = basePriceCentsPer100g,
        available = available,
        photoUrl = photoUrl,
        firstSeenAt = seenAt,
        lastSeenAt = seenAt,
    )
}

/** A tea row with its [CatalogContentHash] and its package rows (05 §4.1–4.2). */
internal data class CatalogRows(val tea: CatalogTeaEntity, val packages: List<CatalogPackageEntity>)

/**
 * This entry as its catalog rows, first and last seen at [seenAt]: the refresh and the shop link build them the same
 * way, so a tea's [CatalogContentHash] is the same from both.
 */
internal fun CatalogEntry.rows(seenAt: Long): CatalogRows {
    val packages = this.packages.map { it.toEntity(key) }
    val page = this.page.toEntity(key, seenAt)
    return CatalogRows(page.copy(contentHash = CatalogContentHash.of(page, packages)), packages)
}

/** A package size of the tea [key] as a catalog row (05 §4.2). */
internal fun ShopPackage.toEntity(key: String) = CatalogPackageEntity(
    catalogKey = key,
    articleNumber = articleNumber,
    label = CatalogColumns.packageLabel(grams, savingsPercent),
    grams = grams,
    priceCents = priceCents,
    priceIsEstimate = priceIsEstimate,
    savingsPercent = savingsPercent,
    variantUrl = variantUrl,
    available = available,
)
