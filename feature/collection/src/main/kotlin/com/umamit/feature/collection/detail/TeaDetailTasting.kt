package com.umamit.feature.collection.detail

import com.umamit.core.model.BrewSession
import com.umamit.core.model.DescriptorCount
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.StepKind
import com.umamit.core.model.TasteAxis
import com.umamit.core.model.TasteDescriptor
import com.umamit.core.model.TeaTasting
import com.umamit.core.ui.TasteRadarShape
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** How many of the tea's most frequent descriptors GESCHMACK shows (TEA-13, D-112). */
internal const val TOP_DESCRIPTORS = 5

/** How many sessions LETZTE SITZUNGEN lists before *Alle anzeigen* (TEA-13, D-112). */
internal const val RECENT_SESSIONS = 3

/** A descriptor of GESCHMACK with the number of the tea's sessions that chose it („Nori · 4"). */
data class DetailDescriptor(val descriptor: TasteDescriptor, val count: Int)

/**
 * GESCHMACK on the tea detail (TEA-13, JRN-03, D-112): the tea's average taste per axis (over the sessions that set
 * that axis) and its most frequent descriptors. Both only from what the sessions recorded; nothing is invented.
 *
 * @property averages the set axes' averages; the radar is drawn from three axes on.
 * @property descriptors up to [TOP_DESCRIPTORS] known descriptors, most frequent first, ties in vocabulary order.
 */
data class DetailTasting(
    val averages: Map<TasteAxis, Double> = emptyMap(),
    val descriptors: List<DetailDescriptor> = emptyList(),
) {
    /** Whether the section shows: a radar to draw or a descriptor to name. Fewer than three axes show no radar. */
    val hasData: Boolean get() = TasteRadarShape.isDrawn(averages) || descriptors.isNotEmpty()

    companion object {
        /** The section's content from the repository's summary of the tea. */
        fun of(tasting: TeaTasting) =
            DetailTasting(averages = tasting.tasteAverages, descriptors = topDescriptors(tasting.descriptorCounts))
    }
}

/**
 * The [TOP_DESCRIPTORS] most frequent descriptors of [counts]: by count, then by the vocabulary's order (08 §4). The
 * repository's counts come ordered by key and include keys the vocabulary lacks, which are left out before taking
 * five (they stay in the database, JRN-04).
 */
internal fun topDescriptors(counts: List<DescriptorCount>): List<DetailDescriptor> = counts
    .mapNotNull { count -> TasteDescriptor.fromKey(count.key)?.let { DetailDescriptor(it, count.count) } }
    .sortedWith(compareByDescending<DetailDescriptor> { it.count }.thenBy { it.descriptor.ordinal })
    .take(TOP_DESCRIPTORS)

/**
 * A row of LETZTE SITZUNGEN (TEA-13, D-112): „8. Juni · 55 °C · 1:00 · 3 Aufgüsse" and „★ 4,5" when rated, as the
 * journal's row has it.
 *
 * @property temperatureC the first infusion's temperature as brewed (its own, else the session's base).
 * @property firstInfusionSeconds the first infusion's planned time.
 * @property infusions the poured infusions; [plannedInfusions] the planned ones (fewer poured: „2 von 3").
 * @property rating 0,5–5,0; null when unrated.
 */
data class RecentSession(
    val id: String,
    val day: LocalDate,
    val temperatureC: Int?,
    val firstInfusionSeconds: Int?,
    val infusions: Int,
    val plannedInfusions: Int,
    val rating: Double?,
) {
    val finishedEarly: Boolean get() = infusions < plannedInfusions
}

/** The finished session [this] as a row of LETZTE SITZUNGEN, with the journal's counting rules (JRN-08, TMR-12). */
internal fun BrewSession.recent(): RecentSession {
    val rows = if (status == SessionStatus.IN_PROGRESS) steps else endedSteps
    val infusionRows = rows.filter { it.kind != StepKind.RINSE }
    val poured = infusionRows.count { it.actualSeconds != null && !it.skipped }
    val planned = plannedStepCount - rows.count { it.kind == StepKind.RINSE }
    val first = infusionRows.firstOrNull()
    return RecentSession(
        id = id,
        day = Instant.ofEpochMilli(startedAtMs).atZone(zoneOf(zoneId)).toLocalDate(),
        temperatureC = first?.let { it.tempC ?: snapshot.baseTempC } ?: snapshot.baseTempC,
        firstInfusionSeconds = first?.plannedSeconds,
        infusions = poured,
        plannedInfusions = maxOf(planned, poured),
        rating = rating,
    )
}

/** The session's zone; one this device doesn't know counts as the device's (as in the journal, D-054). */
private fun zoneOf(id: String): ZoneId = try {
    ZoneId.of(id)
} catch (_: DateTimeException) {
    ZoneId.systemDefault()
}
