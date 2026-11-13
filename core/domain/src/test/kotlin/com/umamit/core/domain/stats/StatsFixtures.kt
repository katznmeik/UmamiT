package com.umamit.core.domain.stats

import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.StepKind
import com.umamit.core.model.StockLevel
import com.umamit.core.model.TasteProfile
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Invented teas and sessions for the statistics tests; today is Friday 13 Nov 2026 in Berlin. */
internal object StatsFixtures {
    val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")
    val TODAY: LocalDate = LocalDate.of(2026, 11, 13)

    fun clock(today: LocalDate = TODAY, zone: ZoneId = BERLIN): Clock =
        Clock.fixed(today.atTime(LocalTime.NOON).atZone(zone).toInstant(), zone)

    private var counter = 0

    fun ms(at: LocalDateTime, zone: ZoneId = BERLIN): Long = at.atZone(zone).toInstant().toEpochMilli()

    fun infusion(taste: TasteProfile = TasteProfile.EMPTY) = StatsStep(StepKind.INFUSION, ended = true, taste = taste)

    @Suppress("LongParameterList")
    fun session(
        day: LocalDate = TODAY,
        time: LocalTime = LocalTime.of(9, 0),
        teaId: String? = "t1",
        teaName: String = "Morgennebel",
        teaType: String? = "Sencha",
        profileId: String? = null,
        profileName: String? = null,
        rating: Double? = null,
        leafGrams: Double? = null,
        costCents: Long? = null,
        steps: List<StatsStep> = listOf(infusion()),
        sessionTaste: TasteProfile = TasteProfile.EMPTY,
        descriptorKeys: List<String> = emptyList(),
        zone: ZoneId = BERLIN,
        styleKey: String? = null,
    ) = StatsSession(
        id = "s${counter++}",
        teaId = teaId,
        teaName = teaName,
        teaType = teaType,
        profileId = profileId,
        profileName = profileName,
        startedAtMs = ms(day.atTime(time), zone),
        zoneId = zone.id,
        rating = rating,
        leafGrams = leafGrams,
        costCents = costCents,
        steps = steps,
        sessionTaste = sessionTaste,
        descriptorKeys = descriptorKeys,
        styleKey = styleKey,
    )

    fun tea(
        id: String,
        name: String = "Tee $id",
        archived: Boolean = false,
        created: LocalDate = TODAY.minusYears(1),
        grams: Double = 100.0,
        level: StockLevel = StockLevel.AVAILABLE,
        warning: Boolean = true,
        coverPhoto: java.io.File? = null,
        styleKey: String? = null,
    ) = StatsTea(id, name, archived, ms(created.atStartOfDay()), grams, level, warning, coverPhoto, styleKey)

    fun purchase(day: LocalDate, cents: Long?, type: InventoryEventType = InventoryEventType.PURCHASE) =
        InventoryEvent("e${counter++}", "t1", type, 50.0, ms(day.atTime(LocalTime.NOON)), priceCents = cents)

    fun calculator(
        sessions: List<StatsSession> = emptyList(),
        teas: List<StatsTea> = emptyList(),
        events: List<InventoryEvent> = emptyList(),
        known: Set<String> = emptySet(),
        clock: Clock = clock(),
    ) = StatsCalculator(StatsInput(sessions, teas, events, known), clock)
}
