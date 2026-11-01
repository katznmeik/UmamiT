package com.umamit.feature.collection.editor

import com.umamit.core.data.TeaPurchase
import com.umamit.core.domain.style.BuiltInStyles
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.HarvestSeason
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.model.UmamiLevel
import java.io.Serializable
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Tee bearbeiten's fields as typed (SCR-10, TEA-02, D-100): numbers as text, so a half-typed value stays as it is.
 * [styleKey] null is *Andere / unbekannt*. [purchaseDate] is set only when a tea is added: then *Kauf* also offers
 * the package and its price (INV-01). Serializable, so it lives in the `SavedStateHandle`.
 */
data class TeaEditorForm(
    val name: String = "",
    val secondaryName: String = "",
    val styleKey: String? = null,
    val umami: UmamiLevel? = null,
    val isFlavored: Boolean = false,
    val originCountry: String? = null,
    val region: String = "",
    val cultivar: String = "",
    val harvestYear: String = "",
    val harvestSeason: HarvestSeason? = null,
    val shadingDays: String = "",
    val processing: String = "",
    val vendor: String = "",
    val purchaseDate: LocalDate? = null,
    val packageGrams: String = "",
    val price: String = "",
    val notes: String = "",
) : Serializable {
    /** The Sorte [key] (`other`: *Andere / unbekannt*) with its umami level (TEA-03: the recipes stay). */
    fun withStyle(key: String): TeaEditorForm {
        val style = BuiltInStyles.byKey(key)?.takeIf { key != OTHER_STYLE_KEY }
        return copy(styleKey = style?.key, umami = style?.umami)
    }

    /** What *Speichern* refuses on [today] (TEA-02, 05 §3.1), shown at the field. */
    fun errors(today: LocalDate): Set<TeaFieldError> = buildSet {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) add(TeaFieldError.NameEmpty)
        if (trimmed.length > MAX_NAME) add(TeaFieldError.NameTooLong)
        if (harvestYear.isNotBlank()) {
            val year = harvestYear.trim().toIntOrNull()
            when {
                year == null || year < MIN_YEAR -> add(TeaFieldError.HarvestYear)
                year > today.year -> add(TeaFieldError.HarvestYearFuture)
            }
        }
        if (shadingDays.isNotBlank() &&
            shadingDays.trim().toIntOrNull() !in SHADING_RANGE
        ) {
            add(TeaFieldError.ShadingDays)
        }
        if (packageGrams.isNotBlank() && parseGrams(packageGrams) == null) add(TeaFieldError.PackageGrams)
        when {
            price.isBlank() -> Unit
            parseCents(price) == null -> add(TeaFieldError.Price)
            packageGrams.isBlank() -> add(TeaFieldError.PriceWithoutPackage)
        }
    }

    /** The package entered in *Kauf*, or null (no stock is invented, INV-01); call only when [errors] is empty. */
    internal fun purchase(): TeaPurchase? = purchaseDate?.let { date ->
        parseGrams(packageGrams)?.let { grams -> TeaPurchase(grams, parseCents(price), date) }
    }

    /**
     * [base] with this form's fields; call only when [errors] is empty. A changed Sorte sets the style, its name
     * ([sorteName]), Kategorie (and a new tea's form; TEA-03); everything the editor doesn't show stays as it is.
     */
    internal fun applyTo(base: Tea, sorteName: (String) -> String?, isNew: Boolean): Tea {
        val styleChanged = isNew || styleKey != base.styleKey
        val style = styleKey?.let(BuiltInStyles::byKey)
        val withStyle = if (styleChanged) {
            base.copy(
                styleKey = styleKey,
                teaType = styleKey?.let(sorteName),
                category = style?.category ?: TeaCategory.OTHER,
                // The form is a new tea's only: a stored one keeps the shop's (a tea bag stays one).
                form = when {
                    !isNew -> base.form
                    style?.recipes?.all { it.method in WHISKED } == true -> TeaForm.POWDER
                    else -> TeaForm.LOOSE_LEAF
                },
            )
        } else {
            base
        }
        return withStyle.copy(
            name = name.trim(),
            secondaryName = secondaryName.orNull(),
            umamiLevel = umami,
            isFlavored = isFlavored,
            originCountry = originCountry,
            originRegion = region.orNull(),
            cultivar = cultivar.orNull(),
            harvestYear = harvestYear.trim().toIntOrNull(),
            harvestSeason = harvestSeason,
            shadingDays = shadingDays.trim().toIntOrNull(),
            processingNotes = processing.orNull(),
            vendor = vendor.orNull(),
            notes = notes.orNull(),
        )
    }

    companion object {
        private const val serialVersionUID = 1L

        /** A tea's name has 1–80 characters (TEA-01, 05 §3.1); also the secondary name, region and cultivar. */
        const val MAX_NAME = 80
        const val MAX_PROCESSING = 300
        const val MAX_NOTES = 5000

        /** Shading days (05 §3.1). */
        val SHADING_RANGE = 0..60
        private const val MIN_YEAR = 1900
        private const val MAX_GRAMS = 10_000.0
        private const val CENT_DIGITS = 2
        private val GRAMS = Regex("""\d{1,5}(?:[.,]\d)?""")
        private val AMOUNT = Regex("""\d{1,6}(?:[.,]\d{1,2})?""")

        /** A new tea of the Sorte [styleKey] (`other` or null: *Andere / unbekannt*), bought [today] (SCR-11). */
        fun new(styleKey: String?, today: LocalDate): TeaEditorForm =
            TeaEditorForm(purchaseDate = today).withStyle(styleKey ?: OTHER_STYLE_KEY)

        /** The stored [tea]'s fields; a catalog tea without a vendor shows the shop's (TEA-02). */
        fun of(tea: Tea) = TeaEditorForm(
            name = tea.name,
            secondaryName = tea.secondaryName.orEmpty(),
            styleKey = tea.styleKey,
            umami = tea.umamiLevel ?: tea.styleKey?.let(BuiltInStyles::byKey)?.umami,
            isFlavored = tea.isFlavored,
            originCountry = tea.originCountry,
            region = tea.originRegion.orEmpty(),
            cultivar = tea.cultivar.orEmpty(),
            harvestYear = tea.harvestYear?.toString().orEmpty(),
            harvestSeason = tea.harvestSeason?.takeIf { it != HarvestSeason.UNKNOWN },
            shadingDays = tea.shadingDays?.toString().orEmpty(),
            processing = tea.processingNotes.orEmpty(),
            vendor = tea.vendor ?: if (tea.catalogKey != null) CATALOG_VENDOR else "",
            notes = tea.notes.orEmpty(),
        )

        /** „50", „37,5" → grams in (0, 10 000]; null otherwise. */
        fun parseGrams(text: String): Double? = text.trim().takeIf(GRAMS::matches)
            ?.replace(',', '.')?.toDouble()?.takeIf { it > 0.0 && it <= MAX_GRAMS }

        /** „12,40", „12.4", „12" → cents; null otherwise. */
        fun parseCents(text: String): Long? = text.replace("€", "").trim().takeIf(AMOUNT::matches)
            ?.let { BigDecimal(it.replace(',', '.')).movePointRight(CENT_DIGITS).toLong() }

        private const val CATALOG_VENDOR = "Tee-Kontor Kiel"

        /** A style brewed only by whisking is a powder (matcha, Hōjicha-Pulver, D-091). */
        private val WHISKED = setOf(BrewMethod.MATCHA_USUCHA, BrewMethod.MATCHA_KOICHA)

        private fun String.orNull(): String? = trim().ifEmpty { null }
    }
}

/** A field *Speichern* refuses, shown at the field. */
enum class TeaFieldError {
    NameEmpty,
    NameTooLong,
    HarvestYear,
    HarvestYearFuture,
    ShadingDays,
    PackageGrams,
    Price,
    PriceWithoutPackage,
}
