package com.umamit.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.StepKind
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TimerPhase

// Session tables of 05 §3.6, §3.7 and §3.12. The lifecycle (05 §3.7) lives in
// core:data's RoomSessionRepository, the active brew in RoomActiveBrewStore.

/** Table `brew_session` (05 §3.6); the tea, recipe and vessel values are snapshots (05 §1.5). */
@Entity(
    tableName = "brew_session",
    foreignKeys = [
        ForeignKey(
            entity = TeaEntity::class,
            parentColumns = ["id"],
            childColumns = ["tea_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = BrewProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = VesselEntity::class,
            parentColumns = ["id"],
            childColumns = ["vessel_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("tea_id"), Index("profile_id"), Index("vessel_id"), Index("started_at")],
)
data class BrewSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "status") val status: SessionStatus,
    @ColumnInfo(name = "tea_id") val teaId: String?,
    @ColumnInfo(name = "tea_name") val teaName: String,
    @ColumnInfo(name = "tea_secondary_name") val teaSecondaryName: String?,
    @ColumnInfo(name = "tea_type") val teaType: String?,
    @ColumnInfo(name = "style_key") val styleKey: String?,
    @ColumnInfo(name = "category") val category: TeaCategory?,
    @ColumnInfo(name = "profile_id") val profileId: String?,
    @ColumnInfo(name = "profile_name") val profileName: String?,
    @ColumnInfo(name = "method") val method: BrewMethod,
    @ColumnInfo(name = "vessel_id") val vesselId: String?,
    @ColumnInfo(name = "vessel_name") val vesselName: String?,
    @ColumnInfo(name = "leaf_grams") val leafGrams: Double?,
    @ColumnInfo(name = "water_ml") val waterMl: Int?,
    @ColumnInfo(name = "base_temp_c") val baseTempC: Int?,
    @ColumnInfo(name = "pre_alert_seconds") val preAlertSeconds: Int?,
    @ColumnInfo(name = "planned_step_count") val plannedStepCount: Int,
    @ColumnInfo(name = "cost_cents") val costCents: Long?,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long?,
    @ColumnInfo(name = "zone_id") val zoneId: String,
    @ColumnInfo(name = "rating") val rating: Double?,
    @ColumnInfo(name = "taste_umami") val tasteUmami: Int?,
    @ColumnInfo(name = "taste_sweetness") val tasteSweetness: Int?,
    @ColumnInfo(name = "taste_bitterness") val tasteBitterness: Int?,
    @ColumnInfo(name = "taste_astringency") val tasteAstringency: Int?,
    @ColumnInfo(name = "taste_body") val tasteBody: Int?,
    @ColumnInfo(name = "taste_aroma") val tasteAroma: Int?,
    @ColumnInfo(name = "notes") val notes: String?,
    @ColumnInfo(name = "auto_finished") val autoFinished: Boolean,
    @ColumnInfo(name = "times_offered") val timesOffered: Boolean,
    @ColumnInfo(name = "times_applied") val timesApplied: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/**
 * Table `session_step` (05 §3.7); recorded values follow 04 §5.1. `pre_alert_seconds` (schema v2, D-093) is the
 * step's pre-alert choice in this session: null *Automatisch*, 0 *Aus*, else 5–30 s.
 */
@Entity(
    tableName = "session_step",
    foreignKeys = [
        ForeignKey(
            entity = BrewSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["session_id", "position"], unique = true)],
)
data class SessionStepEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "position") val position: Int,
    @ColumnInfo(name = "kind") val kind: StepKind,
    @ColumnInfo(name = "planned_seconds") val plannedSeconds: Int,
    @ColumnInfo(name = "actual_seconds") val actualSeconds: Int?,
    @ColumnInfo(name = "paused_seconds") val pausedSeconds: Int?,
    @ColumnInfo(name = "overtime_seconds") val overtimeSeconds: Int?,
    @ColumnInfo(name = "temp_c") val tempC: Int?,
    @ColumnInfo(name = "started_at") val startedAt: Long?,
    @ColumnInfo(name = "ended_at") val endedAt: Long?,
    @ColumnInfo(name = "skipped") val skipped: Boolean,
    @ColumnInfo(name = "overtime_auto_ack") val overtimeAutoAck: Boolean,
    @ColumnInfo(name = "note") val note: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "pre_alert_seconds") val preAlertSeconds: Int?,
)

/**
 * Single-row table `active_brew` (05 §3.12, `id` = [ActiveBrewEntity.SINGLE_ROW_ID]): the timer
 * engine's `ActiveBrewSnapshot` column by column. Counters and flags are non-null, values that
 * only some phases set are nullable. `quick_pre_alert_seconds` (schema v3, D-097) is the quick
 * timer's pre-alert choice: null *Automatisch*, 0 *Aus*, else 5–30 s; always null for a brew session.
 */
@Entity(
    tableName = "active_brew",
    foreignKeys = [
        ForeignKey(
            entity = BrewSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("session_id")],
)
data class ActiveBrewEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    @ColumnInfo(name = "session_id") val sessionId: String?,
    @ColumnInfo(name = "is_quick_timer") val isQuickTimer: Boolean,
    @ColumnInfo(name = "quick_duration_seconds") val quickDurationSeconds: Int?,
    @ColumnInfo(name = "step_index") val stepIndex: Int,
    @ColumnInfo(name = "phase") val phase: TimerPhase,
    @ColumnInfo(name = "deadline_elapsed_ms") val deadlineElapsedMs: Long?,
    @ColumnInfo(name = "deadline_wall_ms") val deadlineWallMs: Long?,
    @ColumnInfo(name = "pre_alert_elapsed_ms") val preAlertElapsedMs: Long?,
    @ColumnInfo(name = "pre_alert_played") val preAlertPlayed: Boolean,
    @ColumnInfo(name = "remaining_ms") val remainingMs: Long?,
    @ColumnInfo(name = "paused_total_ms") val pausedTotalMs: Long,
    @ColumnInfo(name = "paused_since_elapsed_ms") val pausedSinceElapsedMs: Long?,
    @ColumnInfo(name = "paused_since_wall_ms") val pausedSinceWallMs: Long?,
    @ColumnInfo(name = "ready_since_wall_ms") val readySinceWallMs: Long?,
    @ColumnInfo(name = "step_started_wall_ms") val stepStartedWallMs: Long?,
    @ColumnInfo(name = "alert_played") val alertPlayed: Boolean,
    @ColumnInfo(name = "boot_count") val bootCount: Int,
    @ColumnInfo(name = "last_interaction_wall_ms") val lastInteractionWallMs: Long,
    @ColumnInfo(name = "demoted") val demoted: Boolean,
    @ColumnInfo(name = "pre_alert_inherited_seconds") val preAlertInheritedSeconds: Int,
    @ColumnInfo(name = "zone_id") val zoneId: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "quick_pre_alert_seconds") val quickPreAlertSeconds: Int?,
) {
    companion object {
        const val SINGLE_ROW_ID = 1
    }
}
