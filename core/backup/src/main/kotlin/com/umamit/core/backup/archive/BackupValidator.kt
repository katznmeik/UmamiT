package com.umamit.core.backup.archive

import com.umamit.core.model.SessionStatus
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * The row checks of 05 §8 „Validation" on a converted `data.json`: 05 §3's ranges and constraints, and referential
 * integrity (every id a row points to is in the file, so the rows are closed under their references; `catalogKey`s
 * are not, the catalog is re-downloadable). Any failure refuses the file as damaged.
 *
 * The reader also checks the safety copy (#336), which the export wrote from this device's database, so a rule that
 * the app's own writes do not guarantee would block every import there. Hence only these are checked: every rule
 * 05 §3 states for the columns below, where the write paths enforce it (catalog fields are cut to it, the editors
 * limit the rest), and what a later screen parses (ISO dates, zone ids, finite numbers). Not checked, because rows
 * may legitimately break them: taste values on a *Spülen* or unended step (05 §9 v7 moved them there), `originCountry`
 * as ISO 3166 (seed and older rows hold names), photo positions (gaps and ties are allowed), a tea or session's
 * number of photos, and whether a tea has a default recipe at all (at most one is checked).
 */
@Suppress("TooManyFunctions") // one check per table of 05 §3, plus the shared value rules
internal object BackupValidator {
    private const val TEA_NAME = 80
    private const val TEA_TEXT = 80
    private const val INGREDIENTS = 1_000
    private const val PROCESSING_NOTES = 300
    private const val DESCRIPTION = 10_000
    private const val STORAGE_NOTE = 120
    private const val NOTES = 5_000
    private const val MAX_SHADING_DAYS = 60
    private const val MIN_LOW_STOCK_GRAMS = 1.0
    private const val MAX_LOW_STOCK_GRAMS = 500.0

    private const val RECIPE_NAME = 40
    private const val RECIPE_NOTES = 500
    private const val MIN_LEAF_GRAMS = 0.1
    private const val MAX_LEAF_GRAMS = 100.0
    private const val MIN_ML = 10
    private const val MAX_ML = 3_000
    private const val MIN_BASE_TEMP_C = 5
    private const val MAX_TEMP_C = 100
    private const val TEMP_STEP = 5
    private const val MAX_PRE_ALERT_SECONDS = 30
    private const val MIN_STEP_SECONDS = 5
    private const val MAX_STEP_SECONDS = 86_400
    private const val BREW_STEP_NOTE = 120

    private const val VESSEL_NAME = 40

    private const val MIN_RATING = 0.5
    private const val MAX_RATING = 5.0
    private const val MAX_TASTE = 5
    private const val SESSION_STEP_NOTE = 300
    private const val TASTING_NOTE = 200
    private const val MAX_DESCRIPTORS = 20
    private const val EVENT_NOTE = 200

    fun check(data: BackupData) {
        ensure(data.customDescriptors.isEmpty()) // not built (D-112): nothing could take them
        teas(data)
        recipes(data)
        vessels(data)
        sessions(data)
        sessionSteps(data)
        descriptors(data)
        inventory(data)
        photos(data)
        unique(data.catalogBookmarks.map { it.catalogKey }.onEach { ensure(it.isNotBlank()) })
    }

    private fun teas(data: BackupData) {
        unique(data.teas.map { it.id })
        val photoOwners = data.photos.associate { it.id to it.teaId }
        data.teas.forEach { tea ->
            name(tea.name, TEA_NAME)
            text(tea.secondaryName, TEA_TEXT)
            text(tea.ingredients, INGREDIENTS)
            text(tea.originRegion, TEA_TEXT)
            text(tea.cultivar, TEA_TEXT)
            text(tea.processingNotes, PROCESSING_NOTES)
            text(tea.description, DESCRIPTION)
            text(tea.storageNote, STORAGE_NOTE)
            text(tea.notes, NOTES)
            ensure(tea.shadingDays == null || tea.shadingDays in 0..MAX_SHADING_DAYS)
            ensure(tea.lowStockGrams == null || tea.lowStockGrams in MIN_LOW_STOCK_GRAMS..MAX_LOW_STOCK_GRAMS)
            date(tea.openedDate)
            date(tea.bestBefore)
            // The cover is one of the tea's own photos.
            ensure(tea.coverPhotoId == null || photoOwners[tea.coverPhotoId] == tea.id)
        }
    }

    private fun recipes(data: BackupData) {
        val teas = data.teas.map { it.id }.toSet()
        val vessels = data.vessels.map { it.id }.toSet()
        unique(data.brewProfiles.map { it.id })
        data.brewProfiles.forEach { profile ->
            ensure(profile.teaId in teas)
            ensure(profile.vesselId == null || profile.vesselId in vessels)
            name(profile.name, RECIPE_NAME)
            ensure(profile.leafGrams.isFinite() && profile.leafGrams in MIN_LEAF_GRAMS..MAX_LEAF_GRAMS)
            ensure(profile.waterMl in MIN_ML..MAX_ML)
            ensure(profile.baseTempC >= MIN_BASE_TEMP_C && temp(profile.baseTempC))
            preAlert(profile.preAlertSeconds)
            text(profile.notes, RECIPE_NOTES)
        }
        // One default recipe per tea (at most: a tea may have none).
        ensure(data.brewProfiles.filter { it.isDefault }.groupBy { it.teaId }.values.all { it.size == 1 })

        val profiles = data.brewProfiles.map { it.id }.toSet()
        unique(data.brewSteps.map { it.id })
        data.brewSteps.forEach { step ->
            ensure(step.profileId in profiles)
            ensure(step.position >= 0)
            ensure(step.durationSeconds in MIN_STEP_SECONDS..MAX_STEP_SECONDS)
            ensure(step.tempC == null || temp(step.tempC))
            text(step.note, BREW_STEP_NOTE)
            preAlert(step.preAlertSeconds)
        }
        unique(data.brewSteps.map { it.profileId to it.position })
    }

    private fun vessels(data: BackupData) {
        unique(data.vessels.map { it.id })
        val photoOwners = data.photos.associate { it.id to it.vesselId }
        data.vessels.forEach { vessel ->
            name(vessel.name, VESSEL_NAME)
            ensure(vessel.capacityMl in MIN_ML..MAX_ML)
            ensure(vessel.usableMl == null || vessel.usableMl in 0..vessel.capacityMl)
            ensure(vessel.coverPhotoId == null || photoOwners[vessel.coverPhotoId] == vessel.id)
        }
    }

    private fun sessions(data: BackupData) {
        val teas = data.teas.map { it.id }.toSet()
        val profiles = data.brewProfiles.map { it.id }.toSet()
        val vessels = data.vessels.map { it.id }.toSet()
        unique(data.sessions.map { it.id })
        data.sessions.forEach { session ->
            // Never an IN_PROGRESS session (D-130 item 27): nothing could resume it.
            ensure(session.status == SessionStatus.COMPLETED || session.status == SessionStatus.MANUAL)
            ensure(session.teaId == null || session.teaId in teas)
            ensure(session.profileId == null || session.profileId in profiles)
            ensure(session.vesselId == null || session.vesselId in vessels)
            ensure(session.teaName.isNotBlank())
            ensure(session.leafGrams == null || (session.leafGrams.isFinite() && session.leafGrams >= 0))
            ensure(session.waterMl == null || session.waterMl >= 0)
            ensure(session.baseTempC == null || temp(session.baseTempC))
            preAlert(session.preAlertSeconds)
            ensure(session.plannedStepCount >= 0)
            ensure(session.costCents == null || session.costCents >= 0)
            ensure(validZone(session.zoneId))
            rating(session.rating)
            rating(session.brewingRating)
            tastes(
                session.tasteUmami,
                session.tasteSweetness,
                session.tasteBitterness,
                session.tasteAstringency,
                session.tasteBody,
                session.tasteAroma,
            )
            text(session.notes, NOTES)
        }
    }

    private fun sessionSteps(data: BackupData) {
        val sessions = data.sessions.map { it.id }.toSet()
        unique(data.sessionSteps.map { it.id })
        data.sessionSteps.forEach { step ->
            ensure(step.sessionId in sessions)
            ensure(step.plannedSeconds >= 0)
            listOf(step.actualSeconds, step.pausedSeconds, step.overtimeSeconds).forEach {
                ensure(it == null || it >= 0)
            }
            ensure(step.tempC == null || temp(step.tempC))
            text(step.note, SESSION_STEP_NOTE)
            text(step.tastingNote, TASTING_NOTE)
            preAlert(step.preAlertSeconds)
            tastes(
                step.tasteUmami,
                step.tasteSweetness,
                step.tasteBitterness,
                step.tasteAstringency,
                step.tasteBody,
                step.tasteAroma,
                step.tasteRoast,
                step.tasteAcidity,
                step.tasteFreshness,
                step.tasteSpice,
            )
        }
        // (session, position) is unique and the positions run 0…n−1 (05 §3.7).
        data.sessionSteps.groupBy { it.sessionId }.values.forEach { steps ->
            ensure(steps.map { it.position }.sorted() == steps.indices.toList())
        }
    }

    private fun descriptors(data: BackupData) {
        val sessions = data.sessions.map { it.id }.toSet()
        data.sessionDescriptors.forEach { ensure(it.sessionId in sessions && it.descriptorKey.isNotBlank()) }
        unique(data.sessionDescriptors.map { it.sessionId to it.descriptorKey })
        ensure(data.sessionDescriptors.groupBy { it.sessionId }.values.all { it.size <= MAX_DESCRIPTORS })
    }

    private fun inventory(data: BackupData) {
        val teas = data.teas.map { it.id }.toSet()
        val sessions = data.sessions.map { it.id }.toSet()
        unique(data.inventoryEvents.map { it.id })
        data.inventoryEvents.forEach { event ->
            ensure(event.teaId in teas)
            ensure(event.sessionId == null || event.sessionId in sessions)
            ensure(event.deltaGrams.isFinite())
            ensure(event.priceCents == null || event.priceCents >= 0)
            text(event.note, EVENT_NOTE)
        }
        unique(data.inventoryEvents.mapNotNull { it.sessionId }) // session_id is unique (05 §3.10)
    }

    private fun photos(data: BackupData) {
        val teas = data.teas.map { it.id }.toSet()
        val sessions = data.sessions.map { it.id }.toSet()
        val vessels = data.vessels.map { it.id }.toSet()
        unique(data.photos.map { it.id })
        data.photos.forEach { photo ->
            ensure(BackupPaths.isPhotoId(photo.id))
            // Exactly one owner (05 §3.9), and it is in the file.
            val owners = listOfNotNull(photo.teaId, photo.sessionId, photo.vesselId)
            ensure(owners.size == 1)
            ensure(photo.teaId in teas || photo.sessionId in sessions || photo.vesselId in vessels)
            ensure(photo.position >= 0 && photo.byteSize >= 0)
            ensure(photo.widthPx in 1..BackupLimits.PHOTO_SIDE && photo.heightPx in 1..BackupLimits.PHOTO_SIDE)
        }
    }

    private fun unique(keys: List<Any>) = ensure(keys.toSet().size == keys.size)

    /** A required name: not blank, at most [max] characters. */
    private fun name(value: String, max: Int) = ensure(value.isNotBlank() && value.length <= max)

    private fun text(value: String?, max: Int) = ensure(value == null || value.length <= max)

    private fun date(value: String?) {
        if (value == null) return
        try {
            LocalDate.parse(value)
        } catch (_: DateTimeParseException) {
            refuse(BackupRefusal.DAMAGED)
        }
    }

    /** 0–100 °C in 5 °C steps (GEN-12). */
    private fun temp(tempC: Int) = tempC in 0..MAX_TEMP_C && tempC % TEMP_STEP == 0

    /** null *Automatisch*, 0 *Aus*, or 5–30 s in 5 s steps (TMR-23). */
    private fun preAlert(seconds: Int?) = ensure(
        seconds == null || seconds == 0 || (seconds in TEMP_STEP..MAX_PRE_ALERT_SECONDS && seconds % TEMP_STEP == 0),
    )

    /** 0,5–5,0 in half steps (JRN-02). */
    private fun rating(value: Double?) =
        ensure(value == null || (value in MIN_RATING..MAX_RATING && (value * 2) % 1.0 == 0.0))

    private fun tastes(vararg values: Int?) = values.forEach { ensure(it == null || it in 0..MAX_TASTE) }
}
