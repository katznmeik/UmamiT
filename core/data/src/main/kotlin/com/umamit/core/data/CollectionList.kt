package com.umamit.core.data

import com.umamit.core.database.dao.TeaDao
import com.umamit.core.model.CollectionRow
import com.umamit.core.model.Photo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** A tea of *Meine Tees* (SCR-08): its [row] for the list's search, filters and sort, and its cover photo. */
data class CollectionListEntry(val row: CollectionRow, val coverPhoto: Photo?)

/** The rows of *Meine Tees* (SCR-08, TEA-08–TEA-11, D-100). */
interface CollectionListRepository {
    /**
     * Every tea of the collection, archived ones included (the *Archiviert* filter), each with when it was added
     * (`tea.created_at`), the start of its latest session and its stock (the Σ of its events, null without any)
     * at the level SET-16's „Knapp ab" or the tea's own threshold gives (INV-05). In no particular order: the
     * list sorts with `CollectionBrowser`.
     */
    fun observeRows(): Flow<List<CollectionListEntry>>
}

/** [CollectionListRepository] on Room and the settings; reads existing columns only (no schema change). */
class RoomCollectionListRepository @Inject constructor(
    private val dao: TeaDao,
    private val settings: SettingsRepository,
) : CollectionListRepository {
    override fun observeRows(): Flow<List<CollectionListEntry>> = combine(
        dao.observeAll(),
        dao.observeListFacts(),
        settings.settings.map { it.lowStockGrams }.distinctUntilChanged(),
    ) { teas, facts, lowStockGrams ->
        val byId = facts.associateBy { it.teaId }
        // The two queries emit apart: a tea just written may briefly lack its facts (never brewed, no stock).
        teas.map { details ->
            val overview = details.toOverview()
            val fact = byId[overview.tea.id]
            CollectionListEntry(
                row = CollectionRow.of(
                    tea = overview.tea,
                    addedAtMillis = details.tea.createdAt,
                    lastBrewedAtMillis = fact?.lastBrewedAt,
                    remainingGrams = fact?.remainingGrams,
                    lowStockGrams = lowStockGrams,
                ),
                coverPhoto = overview.coverPhoto,
            )
        }
    }.distinctUntilChanged()
}
