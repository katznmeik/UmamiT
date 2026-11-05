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
 * @property steps every row: a session completed from v0.4.0 on keeps its never-started rows, without an actual
 * time (Q-19, D-100); one completed by v0.3.0 or earlier has only its ended rows.
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
) {
    /**
     * The ended rows, those with an actual time (05 §3.7): all that journal counts, cost and
     * statistics count of a completed session, as before v0.4.0 kept its never-started rows (Q-19).
     */
    val endedSteps: List<SessionStepRow> get() = steps.filter { it.actualSeconds != null }
}
