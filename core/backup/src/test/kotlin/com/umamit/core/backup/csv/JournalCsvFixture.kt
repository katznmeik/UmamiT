package com.umamit.core.backup.csv

import com.umamit.core.database.entity.BrewSessionEntity
import com.umamit.core.database.entity.SessionDescriptorEntity
import com.umamit.core.database.entity.SessionStepEntity
import com.umamit.core.database.entity.TeaEntity
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.StepKind
import com.umamit.core.model.TasteSet
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import java.time.LocalDateTime
import java.time.ZoneId

// Synthetic journal rows for the CSV tests (D-016): invented tea names, no shop text.

const val BERLIN = "Europe/Berlin"

/** Epoch ms of [dateTime] in [zone]. */
fun at(dateTime: String, zone: String = BERLIN): Long =
    LocalDateTime.parse(dateTime).atZone(ZoneId.of(zone)).toInstant().toEpochMilli()

fun csvTea(id: String, name: String, category: TeaCategory, tasteSet: TasteSet? = null) = TeaEntity(
    id = id,
    name = name,
    secondaryName = null,
    catalogKey = null,
    styleKey = null,
    teaType = null,
    category = category,
    form = TeaForm.LOOSE_LEAF,
    umamiLevel = null,
    isFlavored = false,
    isOrganic = false,
    ingredients = null,
    originCountry = null,
    originRegion = null,
    cultivar = null,
    harvestYear = null,
    harvestSeason = null,
    shadingDays = null,
    processingNotes = null,
    description = null,
    vendor = null,
    productUrl = null,
    openedDate = null,
    bestBefore = null,
    freshnessDays = null,
    lowStockGrams = null,
    lowStockWarning = false,
    storageNote = null,
    notes = null,
    recipeCheckReason = null,
    isFavorite = false,
    isArchived = false,
    coverPhotoId = null,
    createdAt = 0,
    updatedAt = 0,
    tasteSet = tasteSet,
)

/** A finished session of [teaId]'s snapshot [teaName]; everything optional is empty unless given. */
@Suppress("LongParameterList")
fun csvSession(
    id: String,
    teaId: String?,
    teaName: String,
    category: TeaCategory?,
    startedAt: Long,
    endedAt: Long? = startedAt + 600_000,
    status: SessionStatus = SessionStatus.COMPLETED,
    zoneId: String = BERLIN,
    plannedStepCount: Int = 1,
    baseTempC: Int? = null,
    profileName: String? = null,
    vesselName: String? = null,
    leafGrams: Double? = null,
    waterMl: Int? = null,
    costCents: Long? = null,
    rating: Double? = null,
    brewingRating: Double? = null,
    notes: String? = null,
) = BrewSessionEntity(
    id = id,
    status = status,
    teaId = teaId,
    teaName = teaName,
    teaSecondaryName = null,
    teaType = null,
    styleKey = null,
    category = category,
    profileId = null,
    profileName = profileName,
    method = BrewMethod.KYUSU,
    vesselId = null,
    vesselName = vesselName,
    leafGrams = leafGrams,
    waterMl = waterMl,
    baseTempC = baseTempC,
    preAlertSeconds = null,
    plannedStepCount = plannedStepCount,
    costCents = costCents,
    startedAt = startedAt,
    endedAt = endedAt,
    zoneId = zoneId,
    rating = rating,
    // A v0.6.0 session's own taste columns: the CSV, as the journal, never reads them.
    tasteUmami = 1,
    tasteSweetness = null,
    tasteBitterness = null,
    tasteAstringency = null,
    tasteBody = null,
    tasteAroma = null,
    notes = notes,
    autoFinished = false,
    timesOffered = false,
    timesApplied = false,
    createdAt = startedAt,
    updatedAt = startedAt,
    brewingRating = brewingRating,
)

/** A step row of [sessionId]; [actualSeconds] null for one never started. */
@Suppress("LongParameterList")
fun csvStep(
    sessionId: String,
    position: Int,
    kind: StepKind = StepKind.INFUSION,
    actualSeconds: Int? = 60,
    plannedSeconds: Int = actualSeconds ?: 60,
    tempC: Int? = null,
    startedAt: Long? = null,
    skipped: Boolean = false,
    umami: Int? = null,
    sweetness: Int? = null,
    roast: Int? = null,
    acidity: Int? = null,
    freshness: Int? = null,
) = SessionStepEntity(
    id = "$sessionId-$position",
    sessionId = sessionId,
    position = position,
    kind = kind,
    plannedSeconds = plannedSeconds,
    actualSeconds = actualSeconds,
    pausedSeconds = actualSeconds?.let { 0 },
    overtimeSeconds = actualSeconds?.let { 0 },
    tempC = tempC,
    startedAt = startedAt,
    endedAt = startedAt?.let { it + (actualSeconds ?: 0) * 1_000L },
    skipped = skipped,
    overtimeAutoAck = false,
    note = null,
    createdAt = 0,
    updatedAt = 0,
    preAlertSeconds = null,
    tasteUmami = umami,
    tasteSweetness = sweetness,
    tasteRoast = roast,
    tasteAcidity = acidity,
    tasteFreshness = freshness,
)

fun csvDescriptor(sessionId: String, key: String, createdAt: Long) = SessionDescriptorEntity(sessionId, key, createdAt)

/**
 * The test's descriptor labels: `core:ui`'s are not on this module's classpath, so invented ones; one starts like a
 * formula.
 */
val DESCRIPTOR_LABELS = mapOf("marine.nori" to "Nori", "vegetal.spinat" to "Spinat", "test.at" to "@frisch")
