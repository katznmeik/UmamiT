package com.umamit.core.backup.archive

import com.umamit.core.database.UmamiDatabase
import com.umamit.core.database.entity.ActiveBrewEntity
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
import com.umamit.core.model.RecipeSource
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.StepKind
import com.umamit.core.model.TasteSet
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.model.TimerPhase
import com.umamit.core.model.TimerSlot
import com.umamit.core.model.UmamiLevel
import com.umamit.core.model.VesselMaterial
import com.umamit.core.model.VesselType
import java.io.File
import java.time.LocalDate

// A synthetic data set (D-016): every personal table of 05 §8 has rows, with ten taste columns, two ratings, a
// bookmark, a MANUAL session, own and shop photos; plus an IN_PROGRESS session with its rows and an active brew,
// which the backup leaves out. Photos are placeholder bytes, not real images.

const val T0 = 1_760_000_000_000L

/** The ids of the rows [insertFixture] writes. */
object Ids {
    const val TEA = "tea-1"
    const val TEA_SHOP = "tea-2"
    const val VESSEL = "vessel-1"
    const val PROFILE = "profile-1"
    const val COMPLETED = "session-completed"
    const val MANUAL = "session-manual"
    const val RUNNING = "session-running"
    const val PHOTO_OWN = "photo-own"
    const val PHOTO_SHOP = "photo-shop"
    const val PHOTO_VESSEL = "photo-vessel"
    const val PHOTO_SESSION = "photo-session"
    const val PHOTO_RUNNING = "photo-running"

    /** The photos the backup holds (the running session's is left out). */
    val EXPORTED_PHOTOS = listOf(PHOTO_OWN, PHOTO_SESSION, PHOTO_SHOP, PHOTO_VESSEL)
}

fun tea(id: String, name: String, catalogKey: String? = null) = TeaEntity(
    id = id,
    name = name,
    secondaryName = "Zweitname",
    catalogKey = catalogKey,
    styleKey = "sencha",
    teaType = "Sencha",
    category = TeaCategory.GREEN,
    form = TeaForm.LOOSE_LEAF,
    umamiLevel = UmamiLevel.HIGH,
    isFlavored = false,
    isOrganic = true,
    ingredients = null,
    originCountry = "Japan",
    originRegion = "Shizuoka",
    cultivar = "Yabukita",
    harvestYear = 2026,
    harvestSeason = HarvestSeason.FIRST_FLUSH,
    shadingDays = 7,
    processingNotes = null,
    description = "Synthetische Beschreibung",
    vendor = "Testladen",
    productUrl = null,
    openedDate = LocalDate.of(2026, 10, 21),
    bestBefore = null,
    freshnessDays = 90,
    lowStockGrams = 15.0,
    lowStockWarning = true,
    storageNote = null,
    notes = "Notiz äöü",
    recipeCheckReason = null,
    isFavorite = true,
    isArchived = false,
    coverPhotoId = null,
    createdAt = T0,
    updatedAt = T0,
    tasteSet = TasteSet.GREEN,
)

fun photo(id: String, origin: PhotoOrigin, teaId: String? = null, sessionId: String? = null, vesselId: String? = null) =
    PhotoEntity(
        id = id,
        teaId = teaId,
        sessionId = sessionId,
        vesselId = vesselId,
        position = 0,
        widthPx = 1200,
        heightPx = 900,
        byteSize = 3,
        origin = origin,
        createdAt = T0,
        updatedAt = T0,
    )

fun session(id: String, status: SessionStatus, startedAt: Long) = BrewSessionEntity(
    id = id,
    status = status,
    teaId = Ids.TEA,
    teaName = "Testtee",
    teaSecondaryName = null,
    teaType = "Sencha",
    styleKey = "sencha",
    category = TeaCategory.GREEN,
    profileId = Ids.PROFILE,
    profileName = "Kyusu",
    method = BrewMethod.KYUSU,
    vesselId = Ids.VESSEL,
    vesselName = "Kyusu 250 ml",
    leafGrams = 4.0,
    waterMl = 200,
    baseTempC = 60,
    preAlertSeconds = 10,
    plannedStepCount = 2,
    costCents = 40,
    startedAt = startedAt,
    endedAt = if (status == SessionStatus.IN_PROGRESS) null else startedAt + 300_000,
    zoneId = "Europe/Berlin",
    rating = 4.5,
    tasteUmami = 5,
    tasteSweetness = 4,
    tasteBitterness = 1,
    tasteAstringency = 2,
    tasteBody = 3,
    tasteAroma = 0,
    notes = "Sitzungsnotiz",
    autoFinished = false,
    timesOffered = false,
    timesApplied = false,
    createdAt = startedAt,
    updatedAt = startedAt,
    brewingRating = 3.5,
)

/**
 * The MANUAL session: every value that repeats across sessions differs from the COMPLETED one's (ratings, amounts,
 * temperature and the six tastes, each a value of its own), so a swapped column cannot round-trip unseen.
 */
fun manualSession() = session(Ids.MANUAL, SessionStatus.MANUAL, T0 + 20_000).copy(
    brewingRating = null,
    rating = 2.5,
    leafGrams = 5.5,
    waterMl = 150,
    baseTempC = 70,
    tasteUmami = 0,
    tasteSweetness = 1,
    tasteBitterness = 2,
    tasteAstringency = 3,
    tasteBody = 4,
    tasteAroma = 5,
)

/**
 * The ten step tastes of [variant] 0, 1 or 2 in the order umami, sweetness, bitterness, astringency, body, aroma,
 * roast, acidity, freshness, spice: across the three, every pair of columns differs in at least one step (finding 3 of
 * the T3 review), within 0–5.
 */
private val STEP_TASTES = listOf(
    listOf(5, 4, 3, 2, 1, 0, 5, 4, 3, 2),
    listOf(0, 1, 2, 3, 4, 5, 1, 2, 3, 4),
    listOf(1, 2, 3, 4, 5, 0, 3, 4, 5, 0),
)

fun sessionStep(id: String, sessionId: String, position: Int, variant: Int = 0) = SessionStepEntity(
    id = id,
    sessionId = sessionId,
    position = position,
    kind = StepKind.INFUSION,
    plannedSeconds = 60,
    actualSeconds = 61,
    pausedSeconds = 0,
    overtimeSeconds = 1,
    tempC = 60,
    startedAt = T0 + 1_000,
    endedAt = T0 + 62_000,
    skipped = false,
    overtimeAutoAck = false,
    note = null,
    createdAt = T0,
    updatedAt = T0,
    preAlertSeconds = null,
    tasteUmami = STEP_TASTES[variant][0],
    tasteSweetness = STEP_TASTES[variant][1],
    tasteBitterness = STEP_TASTES[variant][2],
    tasteAstringency = STEP_TASTES[variant][3],
    tasteBody = STEP_TASTES[variant][4],
    tasteAroma = STEP_TASTES[variant][5],
    tastingNote = "Nori",
    tasteRoast = STEP_TASTES[variant][6],
    tasteAcidity = STEP_TASTES[variant][7],
    tasteFreshness = STEP_TASTES[variant][8],
    tasteSpice = STEP_TASTES[variant][9],
)

private fun activeBrew(sessionId: String) = ActiveBrewEntity(
    slot = TimerSlot.BREW,
    sessionId = sessionId,
    isQuickTimer = false,
    quickDurationSeconds = null,
    stepIndex = 0,
    phase = TimerPhase.RUNNING,
    deadlineElapsedMs = 60_000,
    deadlineWallMs = T0 + 60_000,
    preAlertElapsedMs = null,
    preAlertPlayed = false,
    remainingMs = null,
    pausedTotalMs = 0,
    pausedSinceElapsedMs = null,
    pausedSinceWallMs = null,
    readySinceWallMs = null,
    stepStartedWallMs = T0,
    alertPlayed = false,
    bootCount = 1,
    lastInteractionWallMs = T0,
    demoted = false,
    preAlertInheritedSeconds = 10,
    zoneId = "Europe/Berlin",
    createdAt = T0,
    updatedAt = T0,
    quickPreAlertSeconds = null,
    alertRingingSinceElapsedMs = null,
    alertRingingSinceWallMs = null,
)

/** Writes the synthetic data set to [db] and a placeholder file for every photo row into [photosDir]. */
suspend fun insertFixture(db: UmamiDatabase, photosDir: File) {
    val teas = db.teaDao()
    val vessels = db.vesselDao()
    teas.upsert(tea(Ids.TEA, "Eigener Tee"))
    teas.upsert(tea(Ids.TEA_SHOP, "Shop-Tee", catalogKey = "kontor-123"))
    teas.upsertPhoto(photo(Ids.PHOTO_OWN, PhotoOrigin.OWN, teaId = Ids.TEA))
    teas.upsertPhoto(photo(Ids.PHOTO_SHOP, PhotoOrigin.CATALOG_COPY, teaId = Ids.TEA_SHOP))
    teas.upsert(tea(Ids.TEA, "Eigener Tee").copy(coverPhotoId = Ids.PHOTO_OWN))
    teas.upsert(tea(Ids.TEA_SHOP, "Shop-Tee", catalogKey = "kontor-123").copy(coverPhotoId = Ids.PHOTO_SHOP))

    val vessel = VesselEntity(
        id = Ids.VESSEL,
        name = "Kyusu 250 ml",
        type = VesselType.KYUSU,
        capacityMl = 250,
        usableMl = 200,
        material = VesselMaterial.UNGLAZED_CLAY,
        notes = null,
        isFavorite = false,
        isArchived = false,
        coverPhotoId = null,
        createdAt = T0,
        updatedAt = T0,
    )
    vessels.upsert(vessel)
    vessels.upsertPhoto(photo(Ids.PHOTO_VESSEL, PhotoOrigin.OWN, vesselId = Ids.VESSEL))
    vessels.upsert(vessel.copy(coverPhotoId = Ids.PHOTO_VESSEL))

    insertRecipe(db)
    insertSessions(db)
    photosDir.mkdirs()
    (Ids.EXPORTED_PHOTOS + Ids.PHOTO_RUNNING).forEach { id ->
        File(photosDir, "$id.jpg").writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), id.length.toByte()))
    }
}

private suspend fun insertRecipe(db: UmamiDatabase) {
    db.recipeDao().save(
        BrewProfileEntity(
            id = Ids.PROFILE,
            teaId = Ids.TEA,
            name = "Kyusu",
            method = BrewMethod.KYUSU,
            leafGrams = 4.0,
            waterMl = 200,
            baseTempC = 60,
            vesselId = Ids.VESSEL,
            preAlertSeconds = null,
            isDefault = true,
            position = 0,
            source = RecipeSource.USER,
            notes = null,
            createdAt = T0,
            updatedAt = T0,
        ),
        listOf(
            BrewStepEntity("step-1", Ids.PROFILE, 0, StepKind.INFUSION, 60, 60, null, T0, T0, null),
            BrewStepEntity("step-2", Ids.PROFILE, 1, StepKind.INFUSION, 20, 65, "kurz", T0, T0, 5),
        ),
        T0,
    )
}

/** The sessions (COMPLETED, MANUAL, IN_PROGRESS with an active brew), their rows, the stock and a bookmark. */
private suspend fun insertSessions(db: UmamiDatabase) {
    val sessions = db.sessionDao()
    sessions.insert(session(Ids.COMPLETED, SessionStatus.COMPLETED, T0 + 10_000))
    sessions.insert(manualSession())
    sessions.insert(session(Ids.RUNNING, SessionStatus.IN_PROGRESS, T0 + 30_000))
    sessions.insertSteps(
        listOf(
            sessionStep("ss-1", Ids.COMPLETED, 0),
            sessionStep("ss-2", Ids.COMPLETED, 1, variant = 1),
            sessionStep("ss-3", Ids.MANUAL, 0, variant = 2),
            sessionStep("ss-run", Ids.RUNNING, 0),
        ),
    )
    db.tastingDao().insertDescriptor(SessionDescriptorEntity(Ids.COMPLETED, "marine.nori", T0))
    db.tastingDao().insertDescriptor(SessionDescriptorEntity(Ids.RUNNING, "marine.nori", T0))
    val teas = db.teaDao()
    teas.upsertPhoto(photo(Ids.PHOTO_SESSION, PhotoOrigin.OWN, sessionId = Ids.COMPLETED))
    teas.upsertPhoto(photo(Ids.PHOTO_RUNNING, PhotoOrigin.OWN, sessionId = Ids.RUNNING))
    db.activeBrewDao().upsert(activeBrew(Ids.RUNNING))

    val inventory = db.inventoryDao()
    inventory.upsert(event("event-initial", sessionId = null, type = InventoryEventType.INITIAL, delta = 100.0))
    inventory.upsert(event("event-brew", sessionId = Ids.COMPLETED, type = InventoryEventType.BREW, delta = -4.0))
    db.catalogBookmarkDao().insert(CatalogBookmarkEntity("kontor-456", T0))
}

fun event(id: String, sessionId: String?, type: InventoryEventType, delta: Double) = InventoryEventEntity(
    id = id,
    teaId = Ids.TEA,
    sessionId = sessionId,
    type = type,
    deltaGrams = delta,
    priceCents = if (type == InventoryEventType.INITIAL) 1590 else null,
    currency = if (type == InventoryEventType.INITIAL) "EUR" else null,
    packageLabel = null,
    occurredAt = T0,
    note = null,
    createdAt = T0,
    updatedAt = T0,
)
