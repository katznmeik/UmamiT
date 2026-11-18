@file:Suppress("TooManyFunctions") // one mapper each way per table of 05 §3

package com.umamit.core.backup.archive

import com.umamit.core.database.dao.BackupRows
import com.umamit.core.database.entity.BrewProfileEntity
import com.umamit.core.database.entity.BrewSessionEntity
import com.umamit.core.database.entity.BrewStepEntity
import com.umamit.core.database.entity.CatalogBookmarkEntity
import com.umamit.core.database.entity.InventoryEventEntity
import com.umamit.core.database.entity.PhotoEntity
import com.umamit.core.database.entity.SessionDescriptorEntity
import com.umamit.core.database.entity.SessionStepEntity
import com.umamit.core.database.entity.TeaEntity
import com.umamit.core.database.entity.VesselEntity
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.HarvestSeason
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.PhotoOrigin
import com.umamit.core.model.RecipeCheckReason
import com.umamit.core.model.RecipeSource
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.StepKind
import com.umamit.core.model.TasteSet
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.model.UmamiLevel
import com.umamit.core.model.VesselMaterial
import com.umamit.core.model.VesselType
import kotlinx.serialization.Serializable
import java.time.LocalDate

// `data.json` of the backup (05 §8): one array per personal table of 05 §3, each object a row with the table's
// columns in camelCase, one to one (`leaf_grams` → `leafGrams`), no derived values. Every key is written, nulls
// included. Enums by name (05 §5), booleans as true/false, dates as ISO text (`2026-10-21`), timestamps as epoch ms.
// The mappers [toBackup] and [toEntity] are the only translation between a row and its JSON object.

/**
 * The whole `data.json` (05 §8). `customDescriptors` is always empty while custom descriptors are not built (D-112);
 * the key is written so a later format can fill it.
 */
@Serializable
data class BackupData(
    val teas: List<BackupTea> = emptyList(),
    val brewProfiles: List<BackupBrewProfile> = emptyList(),
    val brewSteps: List<BackupBrewStep> = emptyList(),
    val vessels: List<BackupVessel> = emptyList(),
    val sessions: List<BackupSession> = emptyList(),
    val sessionSteps: List<BackupSessionStep> = emptyList(),
    val customDescriptors: List<BackupCustomDescriptor> = emptyList(),
    val sessionDescriptors: List<BackupSessionDescriptor> = emptyList(),
    val inventoryEvents: List<BackupInventoryEvent> = emptyList(),
    val photos: List<BackupPhoto> = emptyList(),
    val catalogBookmarks: List<BackupCatalogBookmark> = emptyList(),
)

/** A custom descriptor (05 §3.8): not built yet (D-112), so `data.json` holds an empty array. */
@Serializable
data class BackupCustomDescriptor(val key: String, val label: String)

/** Row of `tea` (05 §3.1). */
@Serializable
data class BackupTea(
    val id: String,
    val name: String,
    val secondaryName: String?,
    val catalogKey: String?,
    val styleKey: String?,
    val teaType: String?,
    val category: TeaCategory,
    val form: TeaForm,
    val umamiLevel: UmamiLevel?,
    val isFlavored: Boolean,
    val isOrganic: Boolean,
    val ingredients: String?,
    val originCountry: String?,
    val originRegion: String?,
    val cultivar: String?,
    val harvestYear: Int?,
    val harvestSeason: HarvestSeason?,
    val shadingDays: Int?,
    val processingNotes: String?,
    val description: String?,
    val vendor: String?,
    val productUrl: String?,
    val openedDate: String?,
    val bestBefore: String?,
    val freshnessDays: Int?,
    val lowStockGrams: Double?,
    val lowStockWarning: Boolean,
    val storageNote: String?,
    val notes: String?,
    val recipeCheckReason: RecipeCheckReason?,
    val isFavorite: Boolean,
    val isArchived: Boolean,
    val coverPhotoId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    /** Absent or null = from [category] (v0.8.0, D-128). */
    val tasteSet: TasteSet? = null,
)

/** Row of `brew_profile` (05 §3.2). */
@Serializable
data class BackupBrewProfile(
    val id: String,
    val teaId: String,
    val name: String,
    val method: BrewMethod,
    val leafGrams: Double,
    val waterMl: Int,
    val baseTempC: Int,
    val vesselId: String?,
    val preAlertSeconds: Int?,
    val isDefault: Boolean,
    val position: Int,
    val source: RecipeSource,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Row of `brew_step` (05 §3.3). */
@Serializable
data class BackupBrewStep(
    val id: String,
    val profileId: String,
    val position: Int,
    val kind: StepKind,
    val durationSeconds: Int,
    val tempC: Int?,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val preAlertSeconds: Int?,
)

/** Row of `vessel` (05 §3.4). */
@Serializable
data class BackupVessel(
    val id: String,
    val name: String,
    val type: VesselType,
    val capacityMl: Int,
    val usableMl: Int?,
    val material: VesselMaterial?,
    val notes: String?,
    val isFavorite: Boolean,
    val isArchived: Boolean,
    val coverPhotoId: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Row of `brew_session` (05 §3.6); `rating` is *Tee*, `brewingRating` *Zubereitung* (D-117). */
@Serializable
data class BackupSession(
    val id: String,
    val status: SessionStatus,
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
    val baseTempC: Int?,
    val preAlertSeconds: Int?,
    val plannedStepCount: Int,
    val costCents: Long?,
    val startedAt: Long,
    val endedAt: Long?,
    val zoneId: String,
    val rating: Double?,
    val tasteUmami: Int?,
    val tasteSweetness: Int?,
    val tasteBitterness: Int?,
    val tasteAstringency: Int?,
    val tasteBody: Int?,
    val tasteAroma: Int?,
    val notes: String?,
    val autoFinished: Boolean,
    val timesOffered: Boolean,
    val timesApplied: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val brewingRating: Double? = null,
)

/** Row of `session_step` (05 §3.7) with its ten taste columns and `tastingNote` (D-114, D-128). */
@Serializable
data class BackupSessionStep(
    val id: String,
    val sessionId: String,
    val position: Int,
    val kind: StepKind,
    val plannedSeconds: Int,
    val actualSeconds: Int?,
    val pausedSeconds: Int?,
    val overtimeSeconds: Int?,
    val tempC: Int?,
    val startedAt: Long?,
    val endedAt: Long?,
    val skipped: Boolean,
    val overtimeAutoAck: Boolean,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val preAlertSeconds: Int?,
    val tasteUmami: Int? = null,
    val tasteSweetness: Int? = null,
    val tasteBitterness: Int? = null,
    val tasteAstringency: Int? = null,
    val tasteBody: Int? = null,
    val tasteAroma: Int? = null,
    val tastingNote: String? = null,
    val tasteRoast: Int? = null,
    val tasteAcidity: Int? = null,
    val tasteFreshness: Int? = null,
    val tasteSpice: Int? = null,
)

/** Row of `session_descriptor` (05 §3.8). */
@Serializable
data class BackupSessionDescriptor(val sessionId: String, val descriptorKey: String, val createdAt: Long)

/** Row of `inventory_event` (05 §3.10). */
@Serializable
data class BackupInventoryEvent(
    val id: String,
    val teaId: String,
    val sessionId: String?,
    val type: InventoryEventType,
    val deltaGrams: Double,
    val priceCents: Long?,
    val currency: String?,
    val packageLabel: String?,
    val occurredAt: Long,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Row of `photo` (05 §3.9); its image is the entry `photos/<id>.jpg`. */
@Serializable
data class BackupPhoto(
    val id: String,
    val teaId: String?,
    val sessionId: String?,
    val vesselId: String?,
    val position: Int,
    val widthPx: Int,
    val heightPx: Int,
    val byteSize: Long,
    val origin: PhotoOrigin,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Row of `catalog_bookmark` (05 §3.13). */
@Serializable
data class BackupCatalogBookmark(val catalogKey: String, val createdAt: Long)

/** These rows as `data.json`. */
fun BackupRows.toBackup() = BackupData(
    teas = teas.map { it.toBackup() },
    brewProfiles = brewProfiles.map { it.toBackup() },
    brewSteps = brewSteps.map { it.toBackup() },
    vessels = vessels.map { it.toBackup() },
    sessions = sessions.map { it.toBackup() },
    sessionSteps = sessionSteps.map { it.toBackup() },
    customDescriptors = emptyList(),
    sessionDescriptors = sessionDescriptors.map {
        BackupSessionDescriptor(it.sessionId, it.descriptorKey, it.createdAt)
    },
    inventoryEvents = inventoryEvents.map { it.toBackup() },
    photos = photos.map { it.toBackup() },
    catalogBookmarks = catalogBookmarks.map { BackupCatalogBookmark(it.catalogKey, it.createdAt) },
)

/** The rows of this `data.json`, for the reader (#335) and the replace (#336). */
fun BackupData.toRows() = BackupRows(
    teas = teas.map { it.toEntity() },
    brewProfiles = brewProfiles.map { it.toEntity() },
    brewSteps = brewSteps.map { it.toEntity() },
    vessels = vessels.map { it.toEntity() },
    sessions = sessions.map { it.toEntity() },
    sessionSteps = sessionSteps.map { it.toEntity() },
    sessionDescriptors = sessionDescriptors.map {
        SessionDescriptorEntity(it.sessionId, it.descriptorKey, it.createdAt)
    },
    inventoryEvents = inventoryEvents.map { it.toEntity() },
    photos = photos.map { it.toEntity() },
    catalogBookmarks = catalogBookmarks.map { CatalogBookmarkEntity(it.catalogKey, it.createdAt) },
)

fun TeaEntity.toBackup() = BackupTea(
    id = id,
    name = name,
    secondaryName = secondaryName,
    catalogKey = catalogKey,
    styleKey = styleKey,
    teaType = teaType,
    category = category,
    form = form,
    umamiLevel = umamiLevel,
    isFlavored = isFlavored,
    isOrganic = isOrganic,
    ingredients = ingredients,
    originCountry = originCountry,
    originRegion = originRegion,
    cultivar = cultivar,
    harvestYear = harvestYear,
    harvestSeason = harvestSeason,
    shadingDays = shadingDays,
    processingNotes = processingNotes,
    description = description,
    vendor = vendor,
    productUrl = productUrl,
    openedDate = openedDate?.toString(),
    bestBefore = bestBefore?.toString(),
    freshnessDays = freshnessDays,
    lowStockGrams = lowStockGrams,
    lowStockWarning = lowStockWarning,
    storageNote = storageNote,
    notes = notes,
    recipeCheckReason = recipeCheckReason,
    isFavorite = isFavorite,
    isArchived = isArchived,
    coverPhotoId = coverPhotoId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    tasteSet = tasteSet,
)

/** Throws [java.time.format.DateTimeParseException] for a date that is not ISO. */
fun BackupTea.toEntity() = TeaEntity(
    id = id,
    name = name,
    secondaryName = secondaryName,
    catalogKey = catalogKey,
    styleKey = styleKey,
    teaType = teaType,
    category = category,
    form = form,
    umamiLevel = umamiLevel,
    isFlavored = isFlavored,
    isOrganic = isOrganic,
    ingredients = ingredients,
    originCountry = originCountry,
    originRegion = originRegion,
    cultivar = cultivar,
    harvestYear = harvestYear,
    harvestSeason = harvestSeason,
    shadingDays = shadingDays,
    processingNotes = processingNotes,
    description = description,
    vendor = vendor,
    productUrl = productUrl,
    openedDate = openedDate?.let(LocalDate::parse),
    bestBefore = bestBefore?.let(LocalDate::parse),
    freshnessDays = freshnessDays,
    lowStockGrams = lowStockGrams,
    lowStockWarning = lowStockWarning,
    storageNote = storageNote,
    notes = notes,
    recipeCheckReason = recipeCheckReason,
    isFavorite = isFavorite,
    isArchived = isArchived,
    coverPhotoId = coverPhotoId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    tasteSet = tasteSet,
)

fun BrewProfileEntity.toBackup() = BackupBrewProfile(
    id = id,
    teaId = teaId,
    name = name,
    method = method,
    leafGrams = leafGrams,
    waterMl = waterMl,
    baseTempC = baseTempC,
    vesselId = vesselId,
    preAlertSeconds = preAlertSeconds,
    isDefault = isDefault,
    position = position,
    source = source,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun BackupBrewProfile.toEntity() = BrewProfileEntity(
    id = id,
    teaId = teaId,
    name = name,
    method = method,
    leafGrams = leafGrams,
    waterMl = waterMl,
    baseTempC = baseTempC,
    vesselId = vesselId,
    preAlertSeconds = preAlertSeconds,
    isDefault = isDefault,
    position = position,
    source = source,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun BrewStepEntity.toBackup() = BackupBrewStep(
    id = id,
    profileId = profileId,
    position = position,
    kind = kind,
    durationSeconds = durationSeconds,
    tempC = tempC,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
    preAlertSeconds = preAlertSeconds,
)

fun BackupBrewStep.toEntity() = BrewStepEntity(
    id = id,
    profileId = profileId,
    position = position,
    kind = kind,
    durationSeconds = durationSeconds,
    tempC = tempC,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
    preAlertSeconds = preAlertSeconds,
)

fun VesselEntity.toBackup() = BackupVessel(
    id = id,
    name = name,
    type = type,
    capacityMl = capacityMl,
    usableMl = usableMl,
    material = material,
    notes = notes,
    isFavorite = isFavorite,
    isArchived = isArchived,
    coverPhotoId = coverPhotoId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun BackupVessel.toEntity() = VesselEntity(
    id = id,
    name = name,
    type = type,
    capacityMl = capacityMl,
    usableMl = usableMl,
    material = material,
    notes = notes,
    isFavorite = isFavorite,
    isArchived = isArchived,
    coverPhotoId = coverPhotoId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun BrewSessionEntity.toBackup() = BackupSession(
    id = id,
    status = status,
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
    preAlertSeconds = preAlertSeconds,
    plannedStepCount = plannedStepCount,
    costCents = costCents,
    startedAt = startedAt,
    endedAt = endedAt,
    zoneId = zoneId,
    rating = rating,
    tasteUmami = tasteUmami,
    tasteSweetness = tasteSweetness,
    tasteBitterness = tasteBitterness,
    tasteAstringency = tasteAstringency,
    tasteBody = tasteBody,
    tasteAroma = tasteAroma,
    notes = notes,
    autoFinished = autoFinished,
    timesOffered = timesOffered,
    timesApplied = timesApplied,
    createdAt = createdAt,
    updatedAt = updatedAt,
    brewingRating = brewingRating,
)

fun BackupSession.toEntity() = BrewSessionEntity(
    id = id,
    status = status,
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
    preAlertSeconds = preAlertSeconds,
    plannedStepCount = plannedStepCount,
    costCents = costCents,
    startedAt = startedAt,
    endedAt = endedAt,
    zoneId = zoneId,
    rating = rating,
    tasteUmami = tasteUmami,
    tasteSweetness = tasteSweetness,
    tasteBitterness = tasteBitterness,
    tasteAstringency = tasteAstringency,
    tasteBody = tasteBody,
    tasteAroma = tasteAroma,
    notes = notes,
    autoFinished = autoFinished,
    timesOffered = timesOffered,
    timesApplied = timesApplied,
    createdAt = createdAt,
    updatedAt = updatedAt,
    brewingRating = brewingRating,
)

fun SessionStepEntity.toBackup() = BackupSessionStep(
    id = id,
    sessionId = sessionId,
    position = position,
    kind = kind,
    plannedSeconds = plannedSeconds,
    actualSeconds = actualSeconds,
    pausedSeconds = pausedSeconds,
    overtimeSeconds = overtimeSeconds,
    tempC = tempC,
    startedAt = startedAt,
    endedAt = endedAt,
    skipped = skipped,
    overtimeAutoAck = overtimeAutoAck,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
    preAlertSeconds = preAlertSeconds,
    tasteUmami = tasteUmami,
    tasteSweetness = tasteSweetness,
    tasteBitterness = tasteBitterness,
    tasteAstringency = tasteAstringency,
    tasteBody = tasteBody,
    tasteAroma = tasteAroma,
    tastingNote = tastingNote,
    tasteRoast = tasteRoast,
    tasteAcidity = tasteAcidity,
    tasteFreshness = tasteFreshness,
    tasteSpice = tasteSpice,
)

fun BackupSessionStep.toEntity() = SessionStepEntity(
    id = id,
    sessionId = sessionId,
    position = position,
    kind = kind,
    plannedSeconds = plannedSeconds,
    actualSeconds = actualSeconds,
    pausedSeconds = pausedSeconds,
    overtimeSeconds = overtimeSeconds,
    tempC = tempC,
    startedAt = startedAt,
    endedAt = endedAt,
    skipped = skipped,
    overtimeAutoAck = overtimeAutoAck,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
    preAlertSeconds = preAlertSeconds,
    tasteUmami = tasteUmami,
    tasteSweetness = tasteSweetness,
    tasteBitterness = tasteBitterness,
    tasteAstringency = tasteAstringency,
    tasteBody = tasteBody,
    tasteAroma = tasteAroma,
    tastingNote = tastingNote,
    tasteRoast = tasteRoast,
    tasteAcidity = tasteAcidity,
    tasteFreshness = tasteFreshness,
    tasteSpice = tasteSpice,
)

fun InventoryEventEntity.toBackup() = BackupInventoryEvent(
    id = id,
    teaId = teaId,
    sessionId = sessionId,
    type = type,
    deltaGrams = deltaGrams,
    priceCents = priceCents,
    currency = currency,
    packageLabel = packageLabel,
    occurredAt = occurredAt,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun BackupInventoryEvent.toEntity() = InventoryEventEntity(
    id = id,
    teaId = teaId,
    sessionId = sessionId,
    type = type,
    deltaGrams = deltaGrams,
    priceCents = priceCents,
    currency = currency,
    packageLabel = packageLabel,
    occurredAt = occurredAt,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun PhotoEntity.toBackup() = BackupPhoto(
    id = id,
    teaId = teaId,
    sessionId = sessionId,
    vesselId = vesselId,
    position = position,
    widthPx = widthPx,
    heightPx = heightPx,
    byteSize = byteSize,
    origin = origin,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun BackupPhoto.toEntity() = PhotoEntity(
    id = id,
    teaId = teaId,
    sessionId = sessionId,
    vesselId = vesselId,
    position = position,
    widthPx = widthPx,
    heightPx = heightPx,
    byteSize = byteSize,
    origin = origin,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
