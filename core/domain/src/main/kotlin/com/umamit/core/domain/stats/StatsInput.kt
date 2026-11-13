package com.umamit.core.domain.stats

import com.umamit.core.domain.collection.CostRule
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.StepKind
import com.umamit.core.model.StockLevel
import java.io.File

/**
 * Everything the statistics read (02 §12, D-115), loaded by the data source. No database migration: every figure is
 * computed from these when read.
 *
 * @property sessions the finished sessions only (`COMPLETED` or `MANUAL`, auto-finished included, also those of
 * archived or deleted teas); a session still `IN_PROGRESS` is never passed.
 * @property teas every tea in the collection, archived ones included (they are filtered here).
 * @property inventoryEvents the stock events of the existing teas; only the INITIAL and PURCHASE events with a
 * price count (INS-05), the others are ignored. A deleted tea's events are gone with it (cascade).
 * @property cupSizeMl the cup size setting (`cupSizeMl`, D-115 Amendment 1): the water of one cup.
 */
data class StatsInput(
    val sessions: List<StatsSession>,
    val teas: List<StatsTea>,
    val inventoryEvents: List<InventoryEvent>,
    val cupSizeMl: Int = CostRule.DEFAULT_CUP_SIZE_ML,
)

/**
 * A finished session as the statistics see it, with the snapshot it was counted under (05 §3.6).
 *
 * @property teaId the tea, or null when it was deleted (the soft link is cleared).
 * @property teaName the `tea_name` snapshot.
 * @property styleKey the `style_key` snapshot (the leaf placeholder's colour of a deleted tea's row, INS-01).
 * @property teaType the `tea_type` snapshot, the Sorte („Sencha"); null or blank counts as „Andere" (INS-03).
 * @property profileId the recipe it was brewed with, or null (INS-02 best recipe).
 * @property profileName the `profile_name` snapshot.
 * @property startedAtMs `started_at`; the session belongs to this instant's day in [zoneId] (the journal's day).
 * @property zoneId the session's own time zone (IANA id).
 * @property rating 0,5–5,0 in half steps; null when unrated.
 * @property leafGrams the leaf used; null adds nothing to the grams.
 * @property waterMl `water_ml` of the session (the water of each infusion); null counts one cup per infusion.
 * @property costCents `cost_cents` (INV-08); null without a cost.
 * @property steps every step row, never-started ones included.
 */
data class StatsSession(
    val id: String,
    val teaId: String?,
    val teaName: String,
    val teaType: String?,
    val profileId: String?,
    val profileName: String?,
    val startedAtMs: Long,
    val zoneId: String,
    val rating: Double? = null,
    val leafGrams: Double? = null,
    val costCents: Long? = null,
    val steps: List<StatsStep> = emptyList(),
    val styleKey: String? = null,
    val waterMl: Int? = null,
) {
    /** The session's cups, unrounded (INV-08): poured non-rinse infusions × water ÷ [cupSizeMl], at least 1. */
    fun cups(cupSizeMl: Int): Double =
        CostRule.sessionCups(steps.filter { it.ended && !it.skipped }.map { it.kind }, waterMl, cupSizeMl)
}

/**
 * A `session_step` row as the statistics see it (05 §3.7).
 *
 * @property ended whether the step has an actual time (it ran to its end); a never-started row has none.
 */
data class StatsStep(val kind: StepKind, val ended: Boolean, val skipped: Boolean = false)

/**
 * A tea of the collection (INS-01 teas, INS-05 running out).
 *
 * @property createdAtMs `created_at`; „+n im Zeitraum" counts it by this instant's day in the clock's zone.
 * @property coverPhoto the cover photo's file, or null (INS-01 most brewed thumbnail).
 * @property styleKey the tea's style key (08 §2), for the placeholder colour.
 * @property stockLevel INV-05's status of [remainingGrams] with the tea's „Knapp ab".
 * @property lowStockWarning `low_stock_warning`: only teas with the warning on are listed as running out.
 */
data class StatsTea(
    val id: String,
    val name: String,
    val archived: Boolean,
    val createdAtMs: Long,
    val remainingGrams: Double,
    val stockLevel: StockLevel,
    val lowStockWarning: Boolean,
    val coverPhoto: File? = null,
    val styleKey: String? = null,
)
