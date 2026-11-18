package com.umamit.core.backup.csv

import com.umamit.core.model.TeaCategory

/**
 * The words of the journal CSV (BAK-04), from `strings.xml` ([ResourceJournalCsvLabels]); format templates take
 * `String.format` arguments.
 */
interface JournalCsvLabels {
    /** The header of [column]. */
    fun header(column: JournalCsvColumn): String

    /** The short name of [category] („Grün"). */
    fun category(category: TeaCategory): String

    /** The label of a descriptor key (`marine.nori` → „Nori"); null for a key the app does not know (JRN-04). */
    fun descriptor(key: String): String?

    fun template(part: JournalCsvPart): String
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
