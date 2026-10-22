package com.umamit.core.model

/**
 * What a brew session copies at its start (05 §1.5, §3.6), so renaming or
 * deleting the tea, recipe or vessel never changes the journal. The ids are
 * soft links that become null when their row is deleted.
 */
data class SessionSnapshot(
    val teaId: String?,
    val teaName: String,
    val teaSecondaryName: String?,
    val teaType: String?,
    val styleKey: String?,
    val category: TeaCategory?,
    val profileId: String?,
    val profileName: String?,
    val method: BrewMethod,
    val vesselId: String?,
    val vesselName: String?,
    val leafGrams: Double?,
    val waterMl: Int?,
    /** °C, a multiple of 5 (GEN-12). */
    val baseTempC: Int?,
)

/**
 * A `brew_session` row with its `session_step` rows in position order
 * (05 §3.6, §3.7). The tasting fields (rating, notes, …) come with M4.
 *
 * @property preAlertSeconds the effective pre-alert of the session (04 §5): session override ?: recipe ?: global.
 * @property plannedStepCount steps planned at the finish, never-started ones included („2 von 3 Aufgüssen", TMR-12).
 * @property costCents leaf × cost per gram at the finish (INV-08); null without a tea, leaf amount or priced stock.
 * @property endedAtMs the end of the last ended step, set when the session is completed.
 * @property zoneId the session's time zone (IANA id).
 */
data class BrewSession(
    val id: String,
    val status: SessionStatus,
    val snapshot: SessionSnapshot,
    val preAlertSeconds: Int?,
    val plannedStepCount: Int,
    val costCents: Long?,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val zoneId: String,
    val autoFinished: Boolean,
    val steps: List<SessionStepRow>,
)
