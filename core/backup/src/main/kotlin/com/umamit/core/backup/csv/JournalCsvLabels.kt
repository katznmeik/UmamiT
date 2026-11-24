package com.umamit.core.backup.csv

import com.umamit.core.common.AppLanguage
import com.umamit.core.model.TeaCategory

/**
 * The words of the journal CSV (BAK-04), from `strings.xml` ([ResourceJournalCsvLabels]) in German and from
 * [EnglishJournalCsvWords] in English; format templates take `String.format` arguments.
 */
interface JournalCsvLabels {
    /**
     * The language of the whole file (GEN-10, D-136 item 15): its words, separator, numbers and dates. Resolved once
     * per export, so one file never mixes the two.
     */
    val language: AppLanguage

    /** The header of [column]. */
    fun header(column: JournalCsvColumn): String

    /** The short name of [category] („Grün", „Green"). */
    fun category(category: TeaCategory): String

    /**
     * The label of a descriptor key (`marine.nori` → „Nori"); null for a key the app does not know (JRN-04). German in
     * both languages (GEN-10 AC2).
     */
    fun descriptor(key: String): String?

    fun template(part: JournalCsvPart): String
}

/**
 * The English words of the CSV (GEN-10, D-136 item 15), in Kotlin rather than in a `values-en/strings.xml`: Lint's
 * `MissingTranslation` would then demand every other string of this module in English, and the words follow
 * [JournalCsvLabels.language] rather than the resources' locale, so a phone list such as [Français, English] still
 * gives one consistent file. Null where English keeps the German word: the ten taste headers (the taste-slider names
 * stay German, GEN-10 AC2), the temperature and the separators.
 */
internal object EnglishJournalCsvWords {
    @Suppress("CyclomaticComplexMethod") // one word per column of BAK-04
    fun header(column: JournalCsvColumn): String? = when (column) {
        JournalCsvColumn.DATE -> "Date"

        JournalCsvColumn.TIME -> "Time"

        JournalCsvColumn.TEA -> "Tea"

        JournalCsvColumn.CATEGORY -> "Category"

        JournalCsvColumn.RECIPE -> "Recipe"

        JournalCsvColumn.VESSEL -> "Vessel"

        JournalCsvColumn.LEAF -> "Leaf (g)"

        JournalCsvColumn.WATER -> "Water (ml)"

        JournalCsvColumn.INFUSIONS -> "Infusions"

        JournalCsvColumn.DURATION -> "Duration"

        JournalCsvColumn.RATING_TEA -> "Tea rating"

        JournalCsvColumn.RATING_BREWING -> "Brewing rating"

        JournalCsvColumn.COST -> "Cost (€)"

        JournalCsvColumn.DESCRIPTORS -> "Descriptors"

        JournalCsvColumn.NOTES -> "Notes"

        JournalCsvColumn.STEPS -> "Infusion details"

        JournalCsvColumn.TASTE_UMAMI,
        JournalCsvColumn.TASTE_SWEETNESS,
        JournalCsvColumn.TASTE_BITTERNESS,
        JournalCsvColumn.TASTE_ASTRINGENCY,
        JournalCsvColumn.TASTE_BODY,
        JournalCsvColumn.TASTE_AROMA,
        JournalCsvColumn.TASTE_ROAST,
        JournalCsvColumn.TASTE_ACIDITY,
        JournalCsvColumn.TASTE_FRESHNESS,
        JournalCsvColumn.TASTE_SPICE,
        -> null
    }

    fun category(category: TeaCategory): String = when (category) {
        TeaCategory.GREEN -> "Green"
        TeaCategory.WHITE -> "White"
        TeaCategory.YELLOW -> "Yellow"
        TeaCategory.OOLONG -> "Oolong"
        TeaCategory.BLACK -> "Black"
        TeaCategory.DARK -> "Pu-erh"
        TeaCategory.HERBAL -> "Herbal"
        TeaCategory.ROOIBOS -> "Rooibos"
        TeaCategory.FRUIT -> "Fruit"
        TeaCategory.OTHER -> "Other"
    }

    fun template(part: JournalCsvPart): String? = when (part) {
        JournalCsvPart.INFUSIONS_OF -> "%1\$d of %2\$d"

        JournalCsvPart.RINSE -> "Rinse"

        JournalCsvPart.CELSIUS,
        JournalCsvPart.STEP_PARTS_SEPARATOR,
        JournalCsvPart.STEPS_SEPARATOR,
        JournalCsvPart.DESCRIPTORS_SEPARATOR,
        -> null
    }
}

/** The columns of BAK-04, in its order. */
enum class JournalCsvColumn {
    DATE,
    TIME,
    TEA,
    CATEGORY,
    RECIPE,
    VESSEL,
    LEAF,
    WATER,
    INFUSIONS,
    DURATION,
    RATING_TEA,
    RATING_BREWING,
    COST,
    DESCRIPTORS,
    NOTES,
    STEPS,
    TASTE_UMAMI,
    TASTE_SWEETNESS,
    TASTE_BITTERNESS,
    TASTE_ASTRINGENCY,
    TASTE_BODY,
    TASTE_AROMA,
    TASTE_ROAST,
    TASTE_ACIDITY,
    TASTE_FRESHNESS,
    TASTE_SPICE,
}

/** The templates and words of [JournalCsvLabels.template]. */
enum class JournalCsvPart {
    /** „%1$d von %2$d": poured, planned infusions. */
    INFUSIONS_OF,

    /** „%1$d °C". */
    CELSIUS,

    /** „Spülen". */
    RINSE,

    /** „ " between a step's temperature and its time. */
    STEP_PARTS_SEPARATOR,

    /** „ | " between the steps. */
    STEPS_SEPARATOR,

    /** „, " between the descriptors. */
    DESCRIPTORS_SEPARATOR,
}
