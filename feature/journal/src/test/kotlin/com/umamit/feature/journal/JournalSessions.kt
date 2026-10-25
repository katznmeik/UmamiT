package com.umamit.feature.journal

import com.umamit.core.data.JournalSession
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.BrewSession
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.SessionStepRow
import com.umamit.core.model.StepKind
import com.umamit.core.model.TeaCategory
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Test sessions for the journal: built as the session repository stores them (05 §3.6, §3.7). */
object JournalSessions {
    val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")

    /** „Today" of the tests: Thursday, 22 October 2026. */
    val TODAY: LocalDate = LocalDate.of(2026, 10, 22)

    fun ms(at: LocalDateTime, zone: ZoneId = BERLIN): Long = at.atZone(zone).toInstant().toEpochMilli()

    fun step(
        position: Int,
        plannedSeconds: Int,
        actualSeconds: Int? = plannedSeconds,
        kind: StepKind = StepKind.INFUSION,
        tempC: Int? = 55,
        skipped: Boolean = false,
    ) = SessionStepRow(
        position = position,
        kind = kind,
        plannedSeconds = plannedSeconds,
        tempC = tempC,
        actualSeconds = actualSeconds,
        pausedSeconds = actualSeconds?.let { 0 },
        overtimeSeconds = actualSeconds?.let { 0 },
        startedAtWallMs = null,
        endedAtWallMs = null,
        skipped = skipped,
        overtimeAutoAck = false,
    )

    @Suppress("LongParameterList") // one parameter per field a journal row shows
    fun session(
        id: String,
        startedAt: LocalDateTime,
        teaName: String = "Bio Sencha Hisui",
        zone: ZoneId = BERLIN,
        status: SessionStatus = SessionStatus.COMPLETED,
        baseTempC: Int? = 55,
        steps: List<SessionStepRow> = listOf(step(0, 60), step(1, 20), step(2, 60)),
        plannedStepCount: Int = steps.size,
        styleKey: String? = "sencha",
        photo: File? = null,
    ) = JournalSession(
        session = BrewSession(
            id = id,
            status = status,
            snapshot = SessionSnapshot(
                teaId = "t-$id",
                teaName = teaName,
                teaSecondaryName = null,
                teaType = "Sencha",
                styleKey = styleKey,
                category = TeaCategory.GREEN,
                profileId = null,
                profileName = "Kyusu",
                method = BrewMethod.KYUSU,
                vesselId = null,
                vesselName = "Kyusu",
                leafGrams = 4.0,
                waterMl = 250,
                baseTempC = baseTempC,
            ),
            preAlertSeconds = 10,
            plannedStepCount = plannedStepCount,
            costCents = null,
            startedAtMs = ms(startedAt, zone),
            endedAtMs = if (status == SessionStatus.IN_PROGRESS) null else ms(startedAt, zone) + 600_000,
            zoneId = zone.id,
            autoFinished = false,
            steps = steps,
        ),
        coverPhoto = photo,
    )
}
