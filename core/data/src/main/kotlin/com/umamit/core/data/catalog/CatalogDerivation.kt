package com.umamit.core.data.catalog

import com.umamit.core.database.entity.CatalogTeaEntity
import com.umamit.core.domain.style.BuiltInStyles
import com.umamit.core.domain.style.TeaStyle
import com.umamit.core.model.RecipeTemplate

/**
 * What a collection tea takes from its catalog tea: *In Sammlung aufnehmen* (CAT-12) and *Aus Katalog
 * zurücksetzen* (PRF-06, CAT-13) derive the description and the recipes the same way.
 */
internal object CatalogDerivation {
    /** A collection description's limit (05 §3.1). */
    private const val DESCRIPTION_MAX = 10_000

    /** The derived recipes (CAT-04), else the style's; exactly one is the default, the default first. */
    fun templates(entry: CatalogTeaEntity): List<RecipeTemplate> {
        val derived = DerivedRecipesColumn.recipes(entry.derivedRecipesJson)
        val templates = derived.ifEmpty { style(entry).recipes }
        val default = templates.firstOrNull { it.isDefault } ?: templates.first()
        return listOf(default.copy(isDefault = true)) +
            templates.filter { it !== default }.map { it.copy(isDefault = false) }
    }

    /** The catalog tea's description as the collection keeps it. */
    fun description(entry: CatalogTeaEntity): String? = entry.descriptionText?.take(DESCRIPTION_MAX)

    /** The catalog tea's style, [BuiltInStyles.GENERIC] without one. */
    fun style(entry: CatalogTeaEntity): TeaStyle = entry.styleKey?.let(BuiltInStyles::byKey) ?: BuiltInStyles.GENERIC
}
