package com.umamit.core.backup.csv

import com.umamit.core.backup.recipe.RecipeShareText
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
 * The journal as CSV for German Excel (BAK-04, D-130 Q9, Q10): a header row, then one row per finished session
 * (`COMPLETED` and `MANUAL`), newest first; `;` between the fields, CRLF after every row, a field holding `;`, `"` or a
 * line break in quotes with its `"` doubled. Numbers and dates by 05 §7 and 08 („4,0", „21.10.2026", „07:45"); an
 * empty value stays empty and no cell carries a unit. The byte order mark is the writer's ([ExportJournalCsv]).
 *
 * The derived cells follow the journal: „Aufgüsse" counts as the journal row does (TMR-12), „Aufgüsse im Detail"
 * lists the brewed steps (ended, not skipped) with their actual time, and the taste columns average the session's
 * infusions (the ended steps that are not *Spülen*, as the session page's *Alle*) over the six axes of the set in
 * effect (JRN-03, D-128 item 13); the other four stay empty even where a hidden value is stored (D-130 Q10).
 */
object JournalCsv {
    private val GERMAN = Locale.GERMANY
    private val DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy", GERMAN)
    private val TIME = DateTimeFormatter.ofPattern("HH:mm", GERMAN)
    private const val SEPARATOR = ';'
    private const val ROW_END = "\r\n"
    private const val QUOTE_TRIGGERS = ";\"\r\n"
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
        writeRow(out, JournalCsvColumn.entries.map(labels::header))
        val rows = sessions
            .filter { it.session.status != SessionStatus.IN_PROGRESS }
            .sortedByDescending { it.session.startedAt }
        val zones = HashMap<String, ZoneId>()
        rows.forEach { row ->
            val zoneId = row.session.zoneId
            writeRow(out, cells(row, labels, zones.getOrPut(zoneId) { sessionZone(zoneId) }))
        }
        return rows.size
    }

    private fun cells(row: JournalSessionRow, labels: JournalCsvLabels, zone: ZoneId): List<String?> {
        val session = row.session
        val started = Instant.ofEpochMilli(session.startedAt).atZone(zone)
        val steps = row.steps.sortedBy { it.position }
        val set = tasteSet(row)
        val averages = TasteProfile.averages(infusionTastes(steps), set)
        return listOf(
            DATE.format(started),
            TIME.format(started),
            session.teaName,
            session.category?.let(labels::category),
            session.profileName,
            session.vesselName,
            session.leafGrams?.let { "%.1f".format(GERMAN, it) },
            session.waterMl?.toString(),
            infusions(session, steps, labels),
            duration(session, steps),
            session.rating?.let(::rating),
            session.brewingRating?.let(::rating),
            session.costCents?.let(::euros),
            row.descriptors
                .sortedWith(compareBy({ it.createdAt }, { it.descriptorKey }))
                .mapNotNull { labels.descriptor(it.descriptorKey) }
                .joinToString(labels.template(JournalCsvPart.DESCRIPTORS_SEPARATOR)),
            session.notes,
            stepDetails(session, steps, labels),
        ) + TASTE_COLUMNS.map { axis -> averages[axis]?.let { "%.1f".format(GERMAN, it) } }
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
    ): String {
        val ended = steps.filter { it.actualSeconds != null }
        val rinses = ended.count { it.kind == StepKind.RINSE }
        val poured = ended.count { it.kind != StepKind.RINSE && !it.skipped }
        val planned = maxOf(session.plannedStepCount - rinses, poured)
        return labels.template(JournalCsvPart.INFUSIONS_OF).format(GERMAN, poured, planned)
    }

    /** „0:12:30": the first step's start (else the session's) to the session's end; empty without an end. */
    private fun duration(session: BrewSessionEntity, steps: List<SessionStepEntity>): String? {
        val end = session.endedAt ?: return null
        val start = steps.mapNotNull { it.startedAt }.minOrNull() ?: session.startedAt
        val seconds = ((end - start) / MS_PER_SECOND).coerceAtLeast(0)
        return "%d:%02d:%02d".format(
            GERMAN,
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
                        labels.template(JournalCsvPart.CELSIUS).format(GERMAN, it)
                    }
                }
                if (head == null) time else head + parts + time
            }
    }

    /** „4,5", and „4" for a whole star. */
    private fun rating(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else "%.1f".format(GERMAN, value)

    /** „0,36" from the cents, without floating point. */
    private fun euros(cents: Long): String = "%d,%02d".format(GERMAN, cents / CENTS_PER_EURO, cents % CENTS_PER_EURO)

    private fun writeRow(out: Appendable, fields: List<String?>) {
        fields.forEachIndexed { index, field ->
            if (index > 0) out.append(SEPARATOR)
            if (field != null) out.append(quoted(field))
        }
        out.append(ROW_END)
    }

    /** [field] as is, or in quotes with its `"` doubled when it holds `;`, `"` or a line break. */
    internal fun quoted(field: String): String = if (field.any { it in QUOTE_TRIGGERS }) {
        "\"" + field.replace("\"", "\"\"") + "\""
    } else {
        field
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
