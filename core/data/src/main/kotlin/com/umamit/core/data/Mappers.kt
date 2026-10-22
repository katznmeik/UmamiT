package com.umamit.core.data

import com.umamit.core.database.dao.ProfileWithSteps
import com.umamit.core.database.dao.TeaWithDetails
import com.umamit.core.database.entity.BrewProfileEntity
import com.umamit.core.database.entity.BrewStepEntity
import com.umamit.core.database.entity.InventoryEventEntity
import com.umamit.core.database.entity.TeaEntity
import com.umamit.core.database.entity.VesselEntity
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.Recipe
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaOverview
import com.umamit.core.model.Vessel
import java.util.UUID

// Mapping between Room rows and the models of core:model. Temperatures are snapped
// to 5 °C on the way in (GEN-12).

internal fun TeaEntity.toModel() = Tea(
    id = id,
    name = name,
    category = category,
    form = form,
    secondaryName = secondaryName,
    catalogKey = catalogKey,
    styleKey = styleKey,
    teaType = teaType,
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
    openedDate = openedDate,
    bestBefore = bestBefore,
    freshnessDays = freshnessDays,
    lowStockGrams = lowStockGrams,
    storageNote = storageNote,
    notes = notes,
    recipeCheckReason = recipeCheckReason,
    isFavorite = isFavorite,
    isArchived = isArchived,
    coverPhotoId = coverPhotoId,
)

internal fun Tea.toEntity(createdAt: Long, updatedAt: Long) = TeaEntity(
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
    openedDate = openedDate,
    bestBefore = bestBefore,
    freshnessDays = freshnessDays,
    lowStockGrams = lowStockGrams,
    storageNote = storageNote,
    notes = notes,
    recipeCheckReason = recipeCheckReason,
    isFavorite = isFavorite,
    isArchived = isArchived,
    coverPhotoId = coverPhotoId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun TeaWithDetails.toOverview() = TeaOverview(
    tea = tea.toModel(),
    defaultRecipe = profiles.firstOrNull { it.profile.isDefault }?.toModel(),
    coverPhoto = coverPhoto?.toModel(),
)

internal fun ProfileWithSteps.toModel() = Recipe(
    id = profile.id,
    teaId = profile.teaId,
    name = profile.name,
    method = profile.method,
    leafGrams = profile.leafGrams,
    waterMl = profile.waterMl,
    baseTempC = profile.baseTempC,
    steps = steps.sortedBy { it.position }.map { RecipeStep(it.kind, it.durationSeconds, it.tempC, it.note) },
    vesselId = profile.vesselId,
    preAlertSeconds = profile.preAlertSeconds,
    isDefault = profile.isDefault,
    position = profile.position,
    source = profile.source,
    notes = profile.notes,
)

internal fun Recipe.toEntity(createdAt: Long, updatedAt: Long) = BrewProfileEntity(
    id = id,
    teaId = teaId,
    name = name,
    method = method,
    leafGrams = leafGrams,
    waterMl = waterMl,
    baseTempC = snapTo5(baseTempC),
    vesselId = vesselId,
    preAlertSeconds = preAlertSeconds,
    isDefault = isDefault,
    position = position,
    source = source,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/** New step rows for a recipe, in list order; ids are fresh because steps are replaced as a whole. */
internal fun List<RecipeStep>.toEntities(profileId: String, now: Long) = mapIndexed { index, step ->
    BrewStepEntity(
        id = UUID.randomUUID().toString(),
        profileId = profileId,
        position = index,
        kind = step.kind,
        durationSeconds = step.durationSeconds,
        tempC = step.tempC?.let(::snapTo5),
        note = step.note,
        createdAt = now,
        updatedAt = now,
    )
}

internal fun VesselEntity.toModel() = Vessel(
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
)

internal fun Vessel.toEntity(createdAt: Long, updatedAt: Long) = VesselEntity(
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

internal fun InventoryEventEntity.toModel() = InventoryEvent(
    id = id,
    teaId = teaId,
    type = type,
    deltaGrams = deltaGrams,
    occurredAtMillis = occurredAt,
    sessionId = sessionId,
    priceCents = priceCents,
    currency = currency,
    packageLabel = packageLabel,
    note = note,
)

internal fun InventoryEvent.toEntity(createdAt: Long, updatedAt: Long) = InventoryEventEntity(
    id = id,
    teaId = teaId,
    sessionId = sessionId,
    type = type,
    deltaGrams = deltaGrams,
    priceCents = priceCents,
    currency = currency,
    packageLabel = packageLabel,
    occurredAt = occurredAtMillis,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
