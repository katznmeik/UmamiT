package com.umamit.core.backup.csv

import android.content.res.Resources
import com.umamit.core.backup.R
import com.umamit.core.common.AppLanguage
import com.umamit.core.model.TeaCategory

/**
 * [JournalCsvLabels] in [language], the app's language when the labels are made, i.e. once per export (GEN-13,
 * D-136 item 15): German from this module's `strings.xml` (CLAUDE.md rule 4), English from [EnglishJournalCsvWords]
 * where English has its own word, else the German one (the taste headers, GEN-13 AC2). The descriptor labels live in
 * `core:ui` (08 §4), so the caller passes [descriptorLabel], as the journal builds its search words (JRN-09); they
 * stay German in both languages.
 */
class ResourceJournalCsvLabels(
    private val resources: Resources,
    private val descriptorLabel: (key: String) -> String?,
    override val language: AppLanguage = AppLanguage.current(),
) : JournalCsvLabels {
    private val english = language == AppLanguage.ENGLISH
    private val headers = JournalCsvColumn.entries.associateWith { column ->
        (if (english) EnglishJournalCsvWords.header(column) else null) ?: resources.getString(column.headerRes())
    }
    private val categories = TeaCategory.entries.associateWith { category ->
        if (english) EnglishJournalCsvWords.category(category) else resources.getString(category.labelRes())
    }
    private val templates = JournalCsvPart.entries.associateWith { part ->
        (if (english) EnglishJournalCsvWords.template(part) else null) ?: resources.getString(part.templateRes())
    }

    override fun header(column: JournalCsvColumn): String = headers.getValue(column)

    override fun category(category: TeaCategory): String = categories.getValue(category)

    override fun descriptor(key: String): String? = descriptorLabel(key)

    override fun template(part: JournalCsvPart): String = templates.getValue(part)
}

private fun JournalCsvPart.templateRes(): Int = when (this) {
    JournalCsvPart.INFUSIONS_OF -> R.string.csv_infusions_of
    JournalCsvPart.CELSIUS -> R.string.csv_celsius
    JournalCsvPart.RINSE -> R.string.csv_rinse
    JournalCsvPart.STEP_PARTS_SEPARATOR -> R.string.csv_step_parts_separator
    JournalCsvPart.STEPS_SEPARATOR -> R.string.csv_steps_separator
    JournalCsvPart.DESCRIPTORS_SEPARATOR -> R.string.csv_descriptors_separator
}

@Suppress("CyclomaticComplexMethod") // one string per column of BAK-04
private fun JournalCsvColumn.headerRes(): Int = when (this) {
    JournalCsvColumn.DATE -> R.string.csv_header_date
    JournalCsvColumn.TIME -> R.string.csv_header_time
    JournalCsvColumn.TEA -> R.string.csv_header_tea
    JournalCsvColumn.CATEGORY -> R.string.csv_header_category
    JournalCsvColumn.RECIPE -> R.string.csv_header_recipe
    JournalCsvColumn.VESSEL -> R.string.csv_header_vessel
    JournalCsvColumn.LEAF -> R.string.csv_header_leaf
    JournalCsvColumn.WATER -> R.string.csv_header_water
    JournalCsvColumn.INFUSIONS -> R.string.csv_header_infusions
    JournalCsvColumn.DURATION -> R.string.csv_header_duration
    JournalCsvColumn.RATING_TEA -> R.string.csv_header_rating_tea
    JournalCsvColumn.RATING_BREWING -> R.string.csv_header_rating_brewing
    JournalCsvColumn.COST -> R.string.csv_header_cost
    JournalCsvColumn.DESCRIPTORS -> R.string.csv_header_descriptors
    JournalCsvColumn.NOTES -> R.string.csv_header_notes
    JournalCsvColumn.STEPS -> R.string.csv_header_steps
    JournalCsvColumn.TASTE_UMAMI -> R.string.csv_header_taste_umami
    JournalCsvColumn.TASTE_SWEETNESS -> R.string.csv_header_taste_sweetness
    JournalCsvColumn.TASTE_BITTERNESS -> R.string.csv_header_taste_bitterness
    JournalCsvColumn.TASTE_ASTRINGENCY -> R.string.csv_header_taste_astringency
    JournalCsvColumn.TASTE_BODY -> R.string.csv_header_taste_body
    JournalCsvColumn.TASTE_AROMA -> R.string.csv_header_taste_aroma
    JournalCsvColumn.TASTE_ROAST -> R.string.csv_header_taste_roast
    JournalCsvColumn.TASTE_ACIDITY -> R.string.csv_header_taste_acidity
    JournalCsvColumn.TASTE_FRESHNESS -> R.string.csv_header_taste_freshness
    JournalCsvColumn.TASTE_SPICE -> R.string.csv_header_taste_spice
}

private fun TeaCategory.labelRes(): Int = when (this) {
    TeaCategory.GREEN -> R.string.csv_category_green
    TeaCategory.WHITE -> R.string.csv_category_white
    TeaCategory.YELLOW -> R.string.csv_category_yellow
    TeaCategory.OOLONG -> R.string.csv_category_oolong
    TeaCategory.BLACK -> R.string.csv_category_black
    TeaCategory.DARK -> R.string.csv_category_dark
    TeaCategory.HERBAL -> R.string.csv_category_herbal
    TeaCategory.ROOIBOS -> R.string.csv_category_rooibos
    TeaCategory.FRUIT -> R.string.csv_category_fruit
    TeaCategory.OTHER -> R.string.csv_category_other
}
