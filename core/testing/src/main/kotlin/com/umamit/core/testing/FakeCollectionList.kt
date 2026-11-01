package com.umamit.core.testing

import com.umamit.core.data.CollectionListEntry
import com.umamit.core.data.CollectionListRepository
import com.umamit.core.model.CollectionRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

/**
 * [CollectionListRepository] on [FakeSessions] and its collection, as on Room: every tea, archived ones included,
 * with the start of its latest session, the Σ of its stock events (null without any) and its level by the
 * settings' „Knapp ab". The fake collection keeps no creation time: [setAddedAt] sets it (0 by default).
 */
class FakeCollectionList(private val s: FakeSessions) : CollectionListRepository {
    private val addedAt = MutableStateFlow<Map<String, Long>>(emptyMap())

    /** When tea [teaId] was added (`tea.created_at`), for *Zuletzt hinzugefügt*. */
    fun setAddedAt(teaId: String, millis: Long) = addedAt.update { it + (teaId to millis) }

    override fun observeRows(): Flow<List<CollectionListEntry>> = combine(
        s.collection.teaRows,
        s.collection.photoRows,
        s.collection.eventRows,
        s.sessionRows,
        combine(addedAt, s.settings.settings) { added, settings -> added to settings.lowStockGrams },
    ) { teas, photos, events, sessions, (added, lowStockGrams) ->
        val stock = events.values.groupBy { it.teaId }.mapValues { (_, list) -> list.sumOf { it.deltaGrams } }
        val lastBrewed = sessions.values.mapNotNull { session -> session.snapshot.teaId?.let { it to session } }
            .groupBy({ it.first }, { it.second.startedAtMs })
            .mapValues { (_, starts) -> starts.max() }
        teas.values.map { tea ->
            CollectionListEntry(
                row = CollectionRow.of(
                    tea = tea,
                    addedAtMillis = added[tea.id] ?: 0L,
                    lastBrewedAtMillis = lastBrewed[tea.id],
                    remainingGrams = stock[tea.id],
                    lowStockGrams = lowStockGrams,
                ),
                coverPhoto = tea.coverPhotoId?.let(photos::get),
            )
        }
    }
}
