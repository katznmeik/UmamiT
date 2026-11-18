package com.umamit.core.backup.archive

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.StepKind
import org.junit.Test

/**
 * The row checks of 05 §8 „Validation" ([BackupValidator]), pure: one valid file and one mutation per rule of
 * 05 §3, each refused as damaged; and the rows the app may legitimately hold, each accepted.
 */
class BackupValidatorTest {
    private val valid = validData()

    private fun refusal(data: BackupData): BackupRefusal? = try {
        BackupValidator.check(data)
        null
    } catch (refused: BackupRefusedException) {
        refused.refusal
    }

    private fun BackupData.tea(edit: (BackupTea) -> BackupTea) = copy(teas = listOf(edit(teas[0])) + teas.drop(1))
    private fun BackupData.profile(edit: (BackupBrewProfile) -> BackupBrewProfile) =
        copy(brewProfiles = brewProfiles.map(edit))
    private fun BackupData.step(edit: (BackupBrewStep) -> BackupBrewStep) = copy(
        brewSteps =
        listOf(edit(brewSteps[0])) + brewSteps.drop(1),
    )
    private fun BackupData.vessel(edit: (BackupVessel) -> BackupVessel) = copy(vessels = vessels.map(edit))
    private fun BackupData.session(edit: (BackupSession) -> BackupSession) = copy(
        sessions =
        listOf(edit(sessions[0])) + sessions.drop(1),
    )
    private fun BackupData.sessionStep(edit: (BackupSessionStep) -> BackupSessionStep) =
        copy(sessionSteps = listOf(edit(sessionSteps[0])) + sessionSteps.drop(1))
    private fun BackupData.event(edit: (BackupInventoryEvent) -> BackupInventoryEvent) =
        copy(inventoryEvents = listOf(edit(inventoryEvents[0])) + inventoryEvents.drop(1))
    private fun BackupData.photo(edit: (BackupPhoto) -> BackupPhoto) = copy(
        photos =
        listOf(edit(photos[0])) + photos.drop(1),
    )

    @Test
    fun `the fixture is valid`() {
        assertThat(refusal(valid)).isNull()
    }

    private val longText = "x".repeat(10_001)

    private fun assertDamaged(vararg cases: Pair<String, BackupData>) =
        cases.forEach { (why, data) -> assertWithMessage(why).that(refusal(data)).isEqualTo(BackupRefusal.DAMAGED) }

    /** Tea (05 §3.1). */
    @Test
    fun `every broken tea rule is damage`() = assertDamaged(
        "duplicate tea id" to valid.copy(teas = valid.teas + valid.teas[0].copy(coverPhotoId = null)),
        "blank tea name" to valid.tea { it.copy(name = " ") },
        "tea name 81" to valid.tea { it.copy(name = "x".repeat(81)) },
        "secondary name 81" to valid.tea { it.copy(secondaryName = "x".repeat(81)) },
        "ingredients 1001" to valid.tea { it.copy(ingredients = "x".repeat(1_001)) },
        "region 81" to valid.tea { it.copy(originRegion = "x".repeat(81)) },
        "cultivar 81" to valid.tea { it.copy(cultivar = "x".repeat(81)) },
        "processing 301" to valid.tea { it.copy(processingNotes = "x".repeat(301)) },
        "description 10001" to valid.tea { it.copy(description = longText) },
        "storage 121" to valid.tea { it.copy(storageNote = "x".repeat(121)) },
        "tea notes 5001" to valid.tea { it.copy(notes = "x".repeat(5_001)) },
        "shading 61" to valid.tea { it.copy(shadingDays = 61) },
        "shading -1" to valid.tea { it.copy(shadingDays = -1) },
        "low stock 0" to valid.tea { it.copy(lowStockGrams = 0.0) },
        "low stock 501" to valid.tea { it.copy(lowStockGrams = 501.0) },
        "low stock NaN" to valid.tea { it.copy(lowStockGrams = Double.NaN) },
        "opened date" to valid.tea { it.copy(openedDate = "21.10.2026") },
        "best before" to valid.tea { it.copy(bestBefore = "2026-13-01") },
        "cover of another tea" to valid.tea { it.copy(coverPhotoId = Ids.PHOTO_SHOP) },
        "missing cover" to valid.tea { it.copy(coverPhotoId = "ghost") },
    )

    /** Recipe (05 §3.2, §3.3). */
    @Test
    fun `every broken recipe and step rule is damage`() = assertDamaged(
        "duplicate recipe id" to
            valid.copy(brewProfiles = valid.brewProfiles + valid.brewProfiles[0].copy(isDefault = false)),
        "recipe of a missing tea" to valid.profile { it.copy(teaId = "ghost") },
        "recipe with a missing vessel" to valid.profile { it.copy(vesselId = "ghost") },
        "recipe name blank" to valid.profile { it.copy(name = "") },
        "recipe name 41" to valid.profile { it.copy(name = "x".repeat(41)) },
        "leaf 0.05" to valid.profile { it.copy(leafGrams = 0.05) },
        "leaf 101" to valid.profile { it.copy(leafGrams = 101.0) },
        "leaf infinite" to valid.profile { it.copy(leafGrams = Double.POSITIVE_INFINITY) },
        "water 9" to valid.profile { it.copy(waterMl = 9) },
        "water 3001" to valid.profile { it.copy(waterMl = 3_001) },
        "base 0" to valid.profile { it.copy(baseTempC = 0) },
        "base 62" to valid.profile { it.copy(baseTempC = 62) },
        "base 105" to valid.profile { it.copy(baseTempC = 105) },
        "pre-alert 3" to valid.profile { it.copy(preAlertSeconds = 3) },
        "pre-alert 12" to valid.profile { it.copy(preAlertSeconds = 12) },
        "pre-alert 35" to valid.profile { it.copy(preAlertSeconds = 35) },
        "recipe notes 501" to valid.profile { it.copy(notes = "x".repeat(501)) },
        "two defaults" to valid.copy(
            brewProfiles = valid.brewProfiles + valid.brewProfiles[0].copy(id = "profile-2", position = 1),
        ),
        "duplicate step id" to valid.copy(brewSteps = valid.brewSteps + valid.brewSteps[0].copy(position = 5)),
        "step of a missing recipe" to valid.step { it.copy(profileId = "ghost") },
        "step position -1" to valid.step { it.copy(position = -1) },
        "step positions twice" to valid.step { it.copy(position = 1) },
        "step 4 s" to valid.step { it.copy(durationSeconds = 4) },
        "step 86401 s" to valid.step { it.copy(durationSeconds = 86_401) },
        "step 63 °C" to valid.step { it.copy(tempC = 63) },
        "step note 121" to valid.step { it.copy(note = "x".repeat(121)) },
        "step pre-alert 7" to valid.step { it.copy(preAlertSeconds = 7) },
    )

    /** Vessel (05 §3.4). */
    @Test
    fun `every broken vessel rule is damage`() = assertDamaged(
        "duplicate vessel id" to valid.copy(vessels = valid.vessels + valid.vessels[0].copy(coverPhotoId = null)),
        "vessel name 41" to valid.vessel { it.copy(name = "x".repeat(41)) },
        "capacity 9" to valid.vessel { it.copy(capacityMl = 9) },
        "usable over capacity" to valid.vessel { it.copy(usableMl = 251) },
        "usable negative" to valid.vessel { it.copy(usableMl = -1) },
        "vessel cover of a tea photo" to valid.vessel { it.copy(coverPhotoId = Ids.PHOTO_OWN) },
    )

    /** Session (05 §3.6). */
    @Test
    fun `every broken session rule is damage`() = assertDamaged(
        "duplicate session id" to valid.copy(sessions = valid.sessions + valid.sessions[0]),
        "in progress" to valid.session { it.copy(status = SessionStatus.IN_PROGRESS) },
        "session of a missing tea" to valid.session { it.copy(teaId = "ghost") },
        "session of a missing recipe" to valid.session { it.copy(profileId = "ghost") },
        "session with a missing vessel" to valid.session { it.copy(vesselId = "ghost") },
        "blank tea name snapshot" to valid.session { it.copy(teaName = "") },
        "negative leaf" to valid.session { it.copy(leafGrams = -1.0) },
        "NaN leaf" to valid.session { it.copy(leafGrams = Double.NaN) },
        "negative water" to valid.session { it.copy(waterMl = -1) },
        "session 61 °C" to valid.session { it.copy(baseTempC = 61) },
        "session pre-alert 31" to valid.session { it.copy(preAlertSeconds = 31) },
        "planned -1" to valid.session { it.copy(plannedStepCount = -1) },
        "negative cost" to valid.session { it.copy(costCents = -1) },
        "zone" to valid.session { it.copy(zoneId = "Europe/Kiel") },
        "rating 0" to valid.session { it.copy(rating = 0.0) },
        "rating 4.2" to valid.session { it.copy(rating = 4.2) },
        "rating 5.5" to valid.session { it.copy(rating = 5.5) },
        "brewing rating 0.25" to valid.session { it.copy(brewingRating = 0.25) },
        "session taste 6" to valid.session { it.copy(tasteAroma = 6) },
        "session taste -1" to valid.session { it.copy(tasteUmami = -1) },
        "session notes 5001" to valid.session { it.copy(notes = "x".repeat(5_001)) },
    )

    /** Session step (05 §3.7). */
    @Test
    fun `every broken session step rule is damage`() = assertDamaged(
        "duplicate session step id" to
            valid.copy(sessionSteps = valid.sessionSteps + valid.sessionSteps[0].copy(position = 2)),
        "step of a missing session" to valid.sessionStep { it.copy(sessionId = "ghost") },
        "planned -1 s" to valid.sessionStep { it.copy(plannedSeconds = -1) },
        "actual -1 s" to valid.sessionStep { it.copy(actualSeconds = -1) },
        "paused -1 s" to valid.sessionStep { it.copy(pausedSeconds = -1) },
        "overtime -1 s" to valid.sessionStep { it.copy(overtimeSeconds = -1) },
        "session step 58 °C" to valid.sessionStep { it.copy(tempC = 58) },
        "session step note 301" to valid.sessionStep { it.copy(note = "x".repeat(301)) },
        "tasting note 201" to valid.sessionStep { it.copy(tastingNote = "x".repeat(201)) },
        "session step pre-alert 1" to valid.sessionStep { it.copy(preAlertSeconds = 1) },
        "step taste 6" to valid.sessionStep { it.copy(tasteSpice = 6) },
        "positions not 0…n−1" to valid.sessionStep { it.copy(position = 2) },
        "positions twice" to valid.sessionStep { it.copy(position = 1) },
    )

    /** Descriptors (05 §3.8). */
    @Test
    fun `every broken descriptor rule is damage`() = assertDamaged(
        "descriptor of a missing session" to valid.copy(
            sessionDescriptors = valid.sessionDescriptors.map { it.copy(sessionId = "ghost") },
        ),
        "blank descriptor key" to
            valid.copy(sessionDescriptors = valid.sessionDescriptors.map { it.copy(descriptorKey = " ") }),
        "descriptor twice" to valid.copy(sessionDescriptors = valid.sessionDescriptors + valid.sessionDescriptors),
        "21 descriptors" to valid.copy(
            sessionDescriptors = (1..21).map { BackupSessionDescriptor(Ids.COMPLETED, "key.$it", T0) },
        ),
        "a custom descriptor" to valid.copy(customDescriptors = listOf(BackupCustomDescriptor("c1", "Eigenes"))),
    )

    /** Stock (05 §3.10). */
    @Test
    fun `every broken stock event rule is damage`() = assertDamaged(
        "duplicate event id" to valid.copy(inventoryEvents = valid.inventoryEvents + valid.inventoryEvents[0]),
        "event of a missing tea" to valid.event { it.copy(teaId = "ghost") },
        "event of a missing session" to valid.event { it.copy(sessionId = "ghost") },
        "two events of one session" to valid.event { it.copy(sessionId = Ids.COMPLETED) },
        "infinite delta" to valid.event { it.copy(deltaGrams = Double.NEGATIVE_INFINITY) },
        "negative price" to valid.event { it.copy(priceCents = -1) },
        "event note 201" to valid.event { it.copy(note = "x".repeat(201)) },
    )

    /** Photo (05 §3.9). */
    @Test
    fun `every broken photo rule is damage`() = assertDamaged(
        "duplicate photo id" to valid.copy(photos = valid.photos + valid.photos[0]),
        "photo id with a slash" to valid.photo { it.copy(id = "../x") }.tea { it.copy(coverPhotoId = null) },
        "photo id of 65" to valid.photo { it.copy(id = "x".repeat(65)) }.tea { it.copy(coverPhotoId = null) },
        "photo without owner" to valid.photo { it.copy(teaId = null) }.tea { it.copy(coverPhotoId = null) },
        "photo with two owners" to valid.photo { it.copy(vesselId = Ids.VESSEL) },
        "photo of a missing tea" to valid.photo { it.copy(teaId = "ghost") }.tea { it.copy(coverPhotoId = null) },
        "photo position -1" to valid.photo { it.copy(position = -1) },
        "photo bytes -1" to valid.photo { it.copy(byteSize = -1) },
        "photo 2049 px" to valid.photo { it.copy(widthPx = 2_049) },
        "photo 0 px" to valid.photo { it.copy(heightPx = 0) },
    )

    /** Bookmark (05 §3.13). */
    @Test
    fun `every broken bookmark rule is damage`() = assertDamaged(
        "bookmark twice" to valid.copy(catalogBookmarks = valid.catalogBookmarks + valid.catalogBookmarks),
        "blank bookmark" to valid.copy(catalogBookmarks = listOf(BackupCatalogBookmark("", T0))),
    )

    @Test
    fun `what the app may hold is accepted`() {
        val cases: Map<String, BackupData> = mapOf(
            "a tea without a default recipe" to valid.profile { it.copy(isDefault = false) },
            "no recipe at all" to valid.copy(
                brewProfiles = emptyList(),
                brewSteps = emptyList(),
                sessions = valid.sessions.map { it.copy(profileId = null) },
            ),
            "a country name, not ISO" to valid.tea { it.copy(originCountry = "Japan") },
            "tastes on a rinse that never brewed" to valid.sessionStep {
                it.copy(kind = StepKind.RINSE, actualSeconds = null, tasteUmami = 3)
            },
            "a session of a deleted tea" to valid.session { it.copy(teaId = null, profileId = null) },
            "a cold brew at 0 °C … 5 °C" to valid.sessionStep { it.copy(tempC = 0) }.session { it.copy(baseTempC = 5) },
            "photo positions tied" to valid.photo { it.copy(position = 0) },
            "pre-alert off and 30 s" to
                valid.profile { it.copy(preAlertSeconds = 0) }.step { it.copy(preAlertSeconds = 30) },
            "ratings 0.5 and 5" to valid.session { it.copy(rating = 0.5, brewingRating = 5.0) },
            "an empty file" to BackupData(),
        )
        cases.forEach { (why, data) -> assertWithMessage(why).that(refusal(data)).isNull() }
    }
}
