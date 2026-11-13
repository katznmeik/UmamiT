package com.umamit.core.data

import com.umamit.core.common.WallClock
import com.umamit.core.database.dao.StatsDao
import com.umamit.core.database.dao.StatsSessionRow
import com.umamit.core.database.dao.StatsTeaRow
import com.umamit.core.domain.stats.ConsumptionStats
import com.umamit.core.domain.stats.OverviewStats
import com.umamit.core.domain.stats.RatingsStats
import com.umamit.core.domain.stats.StatsCalculator
import com.umamit.core.domain.stats.StatsInput
import com.umamit.core.domain.stats.StatsSession
import com.umamit.core.domain.stats.StatsStep
import com.umamit.core.domain.stats.StatsTea
import com.umamit.core.model.StatsRange
import com.umamit.core.model.StockLevel
import com.umamit.core.model.TasteDescriptor
import com.umamit.core.model.TasteProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

/** The statistics' data source (INS-01–INS-07, D-115). */
interface StatsRepository {
    /**
     * Everything the statistics read, from the existing rows (no schema change): the finished sessions (`COMPLETED`
     * or `MANUAL`; also those of a deleted or archived tea) with steps and known descriptor keys, every tea
     * (archived ones too) and the priced INITIAL / PURCHASE events. Emits again on every write to the sessions,
     * their steps or descriptors, the teas, their stock events, or SET-16's „Knapp ab".
     */
    fun observeInput(): Flow<StatsInput>
}

/** [StatsRepository] on Room and the settings. */
class RoomStatsRepository @Inject constructor(private val dao: StatsDao, private val settings: SettingsRepository) :
    StatsRepository {
    override fun observeInput(): Flow<StatsInput> = combine(
        dao.observeFinishedSessions(),
        dao.observeTeas(),
        dao.observePricedPurchases(),
        settings.settings.map { it.lowStockGrams }.distinctUntilChanged(),
    ) { sessions, teas, events, lowStockGrams ->
        StatsInput(
            sessions = sessions.map { it.toStatsSession() },
            teas = teas.map { it.toStatsTea(lowStockGrams) },
            inventoryEvents = events.map { it.toModel() },
            knownDescriptorKeys = KNOWN_DESCRIPTOR_KEYS,
        )
    }.flowOn(Dispatchers.Default)
}

/** The descriptor vocabulary of 08 §4; every other key is dropped from the sessions (INS-04). */
private val KNOWN_DESCRIPTOR_KEYS: Set<String> = TasteDescriptor.entries.mapTo(HashSet()) { it.key }

private fun StatsSessionRow.toStatsSession() = StatsSession(
    id = session.id,
    teaId = session.teaId,
    teaName = session.teaName,
    teaType = session.teaType,
    profileId = session.profileId,
    profileName = session.profileName,
    startedAtMs = session.startedAt,
    zoneId = session.zoneId,
    rating = session.rating,
    leafGrams = session.leafGrams,
    costCents = session.costCents,
    steps = steps.sortedBy { it.position }.map { step ->
        StatsStep(
            kind = step.kind,
            ended = step.actualSeconds != null,
            skipped = step.skipped,
            taste = step.tasting().taste,
        )
    },
    // Only a v0.6.0 session without a step row still has values here; the v7 migration nulled the others (05 §9).
    sessionTaste = TasteProfile(
        umami = session.tasteUmami,
        sweetness = session.tasteSweetness,
        bitterness = session.tasteBitterness,
        astringency = session.tasteAstringency,
        body = session.tasteBody,
        aroma = session.tasteAroma,
    ),
    descriptorKeys = descriptors
        .sortedWith(compareBy({ it.createdAt }, { it.descriptorKey }))
        .map { it.descriptorKey }
        .filter { it in KNOWN_DESCRIPTOR_KEYS },
)

/** A tea without any stock event is not tracked (as in the collection list): never *Knapp* or *Leer*. */
private fun StatsTeaRow.toStatsTea(lowStockGrams: Double) = StatsTea(
    id = id,
    name = name,
    archived = isArchived,
    createdAtMs = createdAt,
    remainingGrams = remainingGrams ?: 0.0,
    stockLevel = remainingGrams?.let { StockLevel.of(it, this.lowStockGrams ?: lowStockGrams) }
        ?: StockLevel.AVAILABLE,
    lowStockWarning = lowStockWarning,
)

/**
 * The figures of the Statistik screen as flows (INS-01–INS-07): [StatsRepository]'s input through #256's
 * [StatsCalculator] on [Dispatchers.Default], dated by [clock]'s today and zone at the time of each emission.
 */
class ObserveStats @Inject constructor(private val repository: StatsRepository, private val clock: WallClock) {
    /** A calculator per emission, for [StatsCalculator.hasSessions], [StatsCalculator.window] and every tab. */
    fun calculator(): Flow<StatsCalculator> = repository.observeInput()
        .map { StatsCalculator(it, javaClock()) }
        .flowOn(Dispatchers.Default)

    fun overview(range: StatsRange): Flow<OverviewStats> = compute { it.overview(range) }

    fun ratings(range: StatsRange): Flow<RatingsStats> = compute { it.ratings(range) }

    fun consumption(range: StatsRange): Flow<ConsumptionStats> = compute { it.consumption(range) }

    private fun <T> compute(figures: (StatsCalculator) -> T): Flow<T> = repository.observeInput()
        .map { figures(StatsCalculator(it, javaClock())) }
        .flowOn(Dispatchers.Default)

    private fun javaClock(): Clock = Clock.fixed(Instant.ofEpochMilli(clock.nowMs()), clock.zone())
}
