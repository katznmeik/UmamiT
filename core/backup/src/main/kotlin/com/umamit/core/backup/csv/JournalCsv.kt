package com.umamit.core.backup.csv

import com.umamit.core.backup.recipe.RecipeShareText
import com.umamit.core.common.AppLanguage
import com.umamit.core.common.sessionZone
import com.umamit.core.database.dao.JournalSessionRow
import com.umamit.core.database.entity.BrewSessionEntity
import com.umamit.core.database.entity.SessionStepEntity
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.StepKind
import com.umamit.core.model.TasteAxis
import com.umamit.core.model.TasteProfile
import com.umamit.core.model.TasteSet
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The journal as CSV (BAK-04, D-130 Q9, Q10): a header row, then one row per finished session (`COMPLETED` and
 * `MANUAL`), newest first; the separator between the fields, CRLF after every row, a field holding the separator, `"`
 * or a line break in quotes with its `"` doubled. An empty value stays empty and no cell carries a unit.
 *
 * The file follows the labels' [JournalCsvLabels.language], resolved once per export (GEN-10, D-136 item 15). German,
 * for German Excel: `;`, decimal comma and dates by 05 §7 and 08 („4,0", „21.10.2026", „07:45"). English: `,`, decimal
 * point and ISO dates („4.0", „2026-10-21", „07:45"); ISO, because a spreadsheet reads it as a date in any locale and
 * it needs no quotes, where „Oct 21, 2026" would hold the separator.
 *
 * A free-text cell starting with `=`, `+`, `-` or `@` gets a leading apostrophe, so Excel does not read it as a
 * formula. The byte order mark is the writer's ([ExportJournalCsv]).
 *
 * The derived cells follow the journal: „Aufgüsse" counts as the journal row does (TMR-12), „Aufgüsse im Detail"
 * lists the brewed steps (ended, not skipped) with their actual time, and the taste columns average the session's
 * infusions (the ended steps that are not *Spülen*, as the session page's *Alle*) over the six axes of the set in
 * effect (JRN-03, D-128 item 13); the other four stay empty even where a hidden value is stored (D-130 Q10).
 */
object JournalCsv {
    private const val ROW_END = "\r\n"
    private const val CENTS_PER_EURO = 100
    private const val MS_PER_SECOND = 1_000L
    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_PER_HOUR = 3_600L

    /** The ten taste columns in 05 §3.7's order, matching [JournalCsvColumn]'s last ten. */
    private val TASTE_COLUMNS = listOf(
        TasteAxis.UMAMI,
        TasteAxis.SWEETNESS,
        TasteAxis.BITTERNESS,
        TasteAxis.ASTRINGENCY,
        TasteAxis.BODY,
        TasteAxis.AROMA,
        TasteAxis.ROAST,
        TasteAxis.ACIDITY,
        TasteAxis.FRESHNESS,
        TasteAxis.SPICE,
    )

    /** Appends the header and the rows of [sessions]' finished ones to [out]; returns how many sessions it wrote. */
    fun write(sessions: List<JournalSessionRow>, labels: JournalCsvLabels, out: Appendable): Int {
        val format = CsvFormat.of(labels.language)
        writeRow(out, format, JournalCsvColumn.entries.map(labels::header))
        val rows = sessions
            .filter { it.session.status != SessionStatus.IN_PROGRESS }
            .sortedByDescending { it.session.startedAt }
        val zones = HashMap<String, ZoneId>()
        rows.forEach { row ->
            val zoneId = row.session.zoneId
            writeRow(out, format, cells(row, labels, format, zones.getOrPut(zoneId) { sessionZone(zoneId) }))
        }
        return rows.size
    }

    private fun cells(
        row: JournalSessionRow,
        labels: JournalCsvLabels,
        format: CsvFormat,
        zone: ZoneId,
    ): List<String?> {
        val session = row.session
        val started = Instant.ofEpochMilli(session.startedAt).atZone(zone)
        val steps = row.steps.sortedBy { it.position }
        val set = tasteSet(row)
        val averages = TasteProfile.averages(infusionTastes(steps), set)
        return listOf(
            format.date.format(started),
            format.time.format(started),
            textCell(session.teaName),
            session.category?.let(labels::category)?.let(::textCell),
            session.profileName?.let(::textCell),
            session.vesselName?.let(::textCell),
            session.leafGrams?.let { "%.1f".format(format.locale, it) },
            session.waterMl?.toString(),
            infusions(session, steps, labels, format),
            duration(session, steps),
            session.rating?.let { rating(it, format) },
            session.brewingRating?.let { rating(it, format) },
            session.costCents?.let { euros(it, format) },
            row.descriptors
                .sortedWith(compareBy({ it.createdAt }, { it.descriptorKey }))
                .mapNotNull { labels.descriptor(it.descriptorKey) }
                .joinToString(labels.template(JournalCsvPart.DESCRIPTORS_SEPARATOR))
                .let(::textCell),
            session.notes?.let(::textCell),
            stepDetails(session, steps, labels, format),
        ) + TASTE_COLUMNS.map { axis -> averages[axis]?.let { "%.1f".format(format.locale, it) } }
    }

    /**
     * The set in effect (JRN-03, D-128 item 13): the tea's now (its *Geschmacksprofil*, else its Kategorie); for a
     * session whose tea is gone, its snapshot Kategorie's. As core:data's `sessionTasteSet`.
     */
    private fun tasteSet(row: JournalSessionRow): TasteSet = row.teaCategory
        ?.let { TasteSet.effective(row.teaTasteSet, it) }
        ?: TasteSet.of(row.session.category)

    /** The infusions' tastes: the ended steps that are not *Spülen* (`BrewSession.infusions`, D-114). */
    private fun infusionTastes(steps: List<SessionStepEntity>): List<TasteProfile> = steps
        .filter { it.actualSeconds != null && it.kind != StepKind.RINSE }
        .map { it.taste() }

    /** „3 von 4": poured of planned infusions, as the journal row counts them (JRN-08, TMR-12). */
    private fun infusions(
        session: BrewSessionEntity,
        steps: List<SessionStepEntity>,
        labels: JournalCsvLabels,
        format: CsvFormat,
    ): String {
        val ended = steps.filter { it.actualSeconds != null }
        val rinses = ended.count { it.kind == StepKind.RINSE }
        val poured = ended.count { it.kind != StepKind.RINSE && !it.skipped }
        val planned = maxOf(session.plannedStepCount - rinses, poured)
        return labels.template(JournalCsvPart.INFUSIONS_OF).format(format.locale, poured, planned)
    }

    /** „0:12:30": the first step's start (else the session's) to the session's end; empty without an end. */
    private fun duration(session: BrewSessionEntity, steps: List<SessionStepEntity>): String? {
        val end = session.endedAt ?: return null
        val start = steps.mapNotNull { it.startedAt }.minOrNull() ?: session.startedAt
        val seconds = ((end - start) / MS_PER_SECOND).coerceAtLeast(0)
        return "%d:%02d:%02d".format(
            Locale.ROOT,
            seconds / SECONDS_PER_HOUR,
            seconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE,
            seconds % SECONDS_PER_MINUTE,
        )
    }

    /**
     * „Spülen 0:10 | 60 °C 1:30 | 65 °C 0:20": the brewed steps (ended, not skipped) in order with their actual time,
     * each with its temperature (its own, else the base; none for a rinse, or when neither is set).
     */
    private fun stepDetails(
        session: BrewSessionEntity,
        steps: List<SessionStepEntity>,
        labels: JournalCsvLabels,
        format: CsvFormat,
    ): String {
        val parts = labels.template(JournalCsvPart.STEP_PARTS_SEPARATOR)
        return steps
            .filter { it.actualSeconds != null && !it.skipped }
            .joinToString(labels.template(JournalCsvPart.STEPS_SEPARATOR)) { step ->
                val time = RecipeShareText.clock(step.actualSeconds ?: 0)
                val head = if (step.kind == StepKind.RINSE) {
                    labels.template(JournalCsvPart.RINSE)
                } else {
                    (step.tempC ?: session.baseTempC)?.let {
                        labels.template(JournalCsvPart.CELSIUS).format(format.locale, it)
                    }
                }
                if (head == null) time else head + parts + time
            }
    }

    /** „4,5" („4.5"), and „4" for a whole star. */
    private fun rating(value: Double, format: CsvFormat): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else "%.1f".format(format.locale, value)

    /** „0,36" („0.36") from the cents, without floating point. */
    private fun euros(cents: Long, format: CsvFormat): String =
        "%d%c%02d".format(Locale.ROOT, cents / CENTS_PER_EURO, format.decimalMark, cents % CENTS_PER_EURO)

    private fun writeRow(out: Appendable, format: CsvFormat, fields: List<String?>) {
        fields.forEachIndexed { index, field ->
            if (index > 0) out.append(format.separator)
            if (field != null) out.append(quoted(field, format))
        }
        out.append(ROW_END)
    }

    /** [field] as is, or in quotes with its `"` doubled when it holds the separator, `"` or a line break. */
    private fun quoted(field: String, format: CsvFormat): String = if (field.any { it in format.quoteTriggers }) {
        "\"" + field.replace("\"", "\"\"") + "\""
    } else {
        field
    }
}

/** One export's separator, number locale and date format (GEN-10, D-136 item 15), by [AppLanguage]. */
private class CsvFormat(val locale: Locale, val separator: Char, val decimalMark: Char, datePattern: String) {
    val date: DateTimeFormatter = DateTimeFormatter.ofPattern(datePattern, locale)
    val time: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", locale)

    /** What puts a field in quotes: the separator, `"` and a line break. */
    val quoteTriggers = "$separator\"\r\n"

    companion object {
        private val GERMAN = CsvFormat(AppLanguage.GERMAN.locale, ';', ',', "dd.MM.yyyy")
        private val ENGLISH = CsvFormat(AppLanguage.ENGLISH.locale, ',', '.', "yyyy-MM-dd")

        fun of(language: AppLanguage): CsvFormat = when (language) {
            AppLanguage.GERMAN -> GERMAN
            AppLanguage.ENGLISH -> ENGLISH
        }
    }
}

/** A step row's ten taste columns (05 §3.7). */
private fun SessionStepEntity.taste() = TasteProfile(
    umami = tasteUmami,
    sweetness = tasteSweetness,
    bitterness = tasteBitterness,
    astringency = tasteAstringency,
    body = tasteBody,
    aroma = tasteAroma,
    roast = tasteRoast,
    acidity = tasteAcidity,
    freshness = tasteFreshness,
    spice = tasteSpice,
)

/** The first characters a spreadsheet reads as a formula. */
private const val FORMULA_STARTS = "=+-@"

/**
 * A free-text cell (Tee, Kategorie, Rezept, Gefäß, Beschreibungen, Notizen) that Excel must show as text: one starting
 * with [FORMULA_STARTS] gets a leading apostrophe („'- etwas bitter"), nothing trimmed (the PO, #337); quoting
 * applies to the result. Number and generated cells never pass here.
 */
private fun textCell(value: String): String = if (value.isNotEmpty() && value[0] in FORMULA_STARTS) "'$value" else value
