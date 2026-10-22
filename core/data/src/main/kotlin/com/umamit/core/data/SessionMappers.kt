package com.umamit.core.data

import com.umamit.core.common.snap5
import com.umamit.core.database.entity.ActiveBrewEntity
import com.umamit.core.database.entity.BrewSessionEntity
import com.umamit.core.database.entity.SessionStepEntity
import com.umamit.core.model.ActiveBrewSnapshot
import com.umamit.core.model.BrewSession
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.SessionStepRow
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.StepRecord
import java.util.UUID

// Mapping between the session rows (05 §3.6, §3.7, §3.12) and the models of core:model
// and the timer engine. Temperatures are snapped to 5 °C on the way in (GEN-12).

internal fun SessionStart.toEntity(now: Long) = BrewSessionEntity(
    id = id,
    status = SessionStatus.IN_PROGRESS,
    teaId = snapshot.teaId,
    teaName = snapshot.teaName,
    teaSecondaryName = snapshot.teaSecondaryName,
    teaType = snapshot.teaType,
    styleKey = snapshot.styleKey,
    category = snapshot.category,
    profileId = snapshot.profileId,
    profileName = snapshot.profileName,
    method = snapshot.method,
    vesselId = snapshot.vesselId,
    vesselName = snapshot.vesselName,
    leafGrams = snapshot.leafGrams,
    waterMl = snapshot.waterMl,
    baseTempC = snapshot.baseTempC?.let(::snap5),
    preAlertSeconds = preAlertSeconds,
    plannedStepCount = steps.size,
    costCents = null,
    startedAt = startedAtMs,
    endedAt = null,
    zoneId = zoneId,
    rating = null,
    tasteUmami = null,
    tasteSweetness = null,
    tasteBitterness = null,
    tasteAstringency = null,
    tasteBody = null,
    tasteAroma = null,
    notes = null,
    autoFinished = false,
    timesOffered = false,
    timesApplied = false,
    createdAt = now,
    updatedAt = now,
)

internal fun BrewSessionEntity.toModel(steps: List<SessionStepEntity>) = BrewSession(
    id = id,
    status = status,
    snapshot = SessionSnapshot(
        teaId = teaId,
        teaName = teaName,
        teaSecondaryName = teaSecondaryName,
        teaType = teaType,
        styleKey = styleKey,
        category = category,
        profileId = profileId,
        profileName = profileName,
        method = method,
        vesselId = vesselId,
        vesselName = vesselName,
        leafGrams = leafGrams,
        waterMl = waterMl,
        baseTempC = baseTempC,
    ),
    preAlertSeconds = preAlertSeconds,
    plannedStepCount = plannedStepCount,
    costCents = costCents,
    startedAtMs = startedAt,
    endedAtMs = endedAt,
    zoneId = zoneId,
    autoFinished = autoFinished,
    steps = steps.map { it.toRow() },
)

/** A planned, unstarted row at [position]. */
internal fun PlannedStep.toEntity(sessionId: String, position: Int, now: Long) = SessionStepEntity(
    id = UUID.randomUUID().toString(),
    sessionId = sessionId,
    position = position,
    kind = kind,
    plannedSeconds = plannedSeconds,
    actualSeconds = null,
    pausedSeconds = null,
    overtimeSeconds = null,
    tempC = tempC?.let(::snap5),
    startedAt = null,
    endedAt = null,
    skipped = false,
    overtimeAutoAck = false,
    note = null,
    createdAt = now,
    updatedAt = now,
)

/** This row with the recorded values of 04 §5.1. */
internal fun SessionStepEntity.recorded(record: StepRecord, now: Long) = copy(
    plannedSeconds = record.plannedSeconds,
    actualSeconds = record.actualSeconds,
    pausedSeconds = record.pausedSeconds,
    overtimeSeconds = record.overtimeSeconds,
    startedAt = record.startedAtWallMs,
    endedAt = record.endedAtWallMs,
    skipped = record.skipped,
    overtimeAutoAck = record.overtimeAutoAck,
    updatedAt = now,
)

internal fun SessionStepEntity.toRow() = SessionStepRow(
    position = position,
    kind = kind,
    plannedSeconds = plannedSeconds,
    tempC = tempC,
    actualSeconds = actualSeconds,
    pausedSeconds = pausedSeconds,
    overtimeSeconds = overtimeSeconds,
    startedAtWallMs = startedAt,
    endedAtWallMs = endedAt,
    skipped = skipped,
    overtimeAutoAck = overtimeAutoAck,
)

/** This row with the timer values of [row]; the id, the note and the creation time stay. */
internal fun SessionStepEntity.with(row: SessionStepRow, now: Long): SessionStepEntity {
    val updated = copy(
        kind = row.kind,
        plannedSeconds = row.plannedSeconds,
        tempC = row.tempC?.let(::snap5),
        actualSeconds = row.actualSeconds,
        pausedSeconds = row.pausedSeconds,
        overtimeSeconds = row.overtimeSeconds,
        startedAt = row.startedAtWallMs,
        endedAt = row.endedAtWallMs,
        skipped = row.skipped,
        overtimeAutoAck = row.overtimeAutoAck,
    )
    return if (updated == this) this else updated.copy(updatedAt = now)
}

internal fun SessionStepRow.toEntity(sessionId: String, now: Long) = SessionStepEntity(
    id = UUID.randomUUID().toString(),
    sessionId = sessionId,
    position = position,
    kind = kind,
    plannedSeconds = plannedSeconds,
    actualSeconds = actualSeconds,
    pausedSeconds = pausedSeconds,
    overtimeSeconds = overtimeSeconds,
    tempC = tempC?.let(::snap5),
    startedAt = startedAtWallMs,
    endedAt = endedAtWallMs,
    skipped = skipped,
    overtimeAutoAck = overtimeAutoAck,
    note = null,
    createdAt = now,
    updatedAt = now,
)

internal fun ActiveBrewSnapshot.toEntity(createdAt: Long, updatedAt: Long) = ActiveBrewEntity(
    sessionId = sessionId,
    isQuickTimer = isQuickTimer,
    quickDurationSeconds = quickDurationSeconds,
    stepIndex = stepIndex,
    phase = phase,
    deadlineElapsedMs = deadlineElapsedMs,
    deadlineWallMs = deadlineWallMs,
    preAlertElapsedMs = preAlertElapsedMs,
    preAlertPlayed = preAlertPlayed,
    remainingMs = remainingMs,
    pausedTotalMs = pausedTotalMs,
    pausedSinceElapsedMs = pausedSinceElapsedMs,
    pausedSinceWallMs = pausedSinceWallMs,
    readySinceWallMs = readySinceWallMs,
    stepStartedWallMs = stepStartedWallMs,
    alertPlayed = alertPlayed,
    bootCount = bootCount,
    lastInteractionWallMs = lastInteractionWallMs,
    demoted = demoted,
    preAlertOverrideSeconds = preAlertOverrideSeconds,
    preAlertInheritedSeconds = preAlertInheritedSeconds,
    zoneId = zoneId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun ActiveBrewEntity.toSnapshot() = ActiveBrewSnapshot(
    sessionId = sessionId,
    isQuickTimer = isQuickTimer,
    quickDurationSeconds = quickDurationSeconds,
    stepIndex = stepIndex,
    phase = phase,
    deadlineElapsedMs = deadlineElapsedMs,
    deadlineWallMs = deadlineWallMs,
    preAlertElapsedMs = preAlertElapsedMs,
    preAlertPlayed = preAlertPlayed,
    remainingMs = remainingMs,
    pausedTotalMs = pausedTotalMs,
    pausedSinceElapsedMs = pausedSinceElapsedMs,
    pausedSinceWallMs = pausedSinceWallMs,
    readySinceWallMs = readySinceWallMs,
    stepStartedWallMs = stepStartedWallMs,
    alertPlayed = alertPlayed,
    bootCount = bootCount,
    lastInteractionWallMs = lastInteractionWallMs,
    demoted = demoted,
    preAlertOverrideSeconds = preAlertOverrideSeconds,
    preAlertInheritedSeconds = preAlertInheritedSeconds,
    zoneId = zoneId,
)
