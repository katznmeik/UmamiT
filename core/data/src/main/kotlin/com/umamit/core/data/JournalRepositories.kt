package com.umamit.core.data

import com.umamit.core.database.UmamiDatabase
import com.umamit.core.database.dao.TasteAveragesRow
import com.umamit.core.model.BrewSession
import com.umamit.core.model.DescriptorCount
import com.umamit.core.model.TasteAxis
import com.umamit.core.model.TeaRating
import com.umamit.core.model.TeaTasting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject

/**
 * A session as the journal lists it (SCR-15): the row with its steps, its tasting inputs and descriptor keys
 * ([BrewSession]) and its tea's cover photo file, if any.
 */
data class JournalSession(val session: BrewSession, val coverPhoto: File?)

/**
 * The journal's read side (JRN-01, JRN-08) and the per-tea tasting summaries (TEA-08, TEA-13, JRN-03). It writes
 * only the deletion: the session rows, the stock deduction and the opened date come from [SessionRepository]
 * (#36), the tasting inputs from [SessionTasting].
 */
interface JournalRepository {
    /** Every session, in progress or finished, newest start first; emits again on every write. */
    fun observeSessions(): Flow<List<JournalSession>>

    /**
     * *Löschen* on the session page (JRN-10, D-102): deletes a finished session with its steps and its BREW stock
     * event, so the leaf it deducted is back in the stock (INV-02). A session in progress, or one that is gone
     * already, is left alone.
     */
    suspend fun deleteSession(sessionId: String)

    /**
     * Every tea with a rated finished session, by tea id: the average rating and the number of rated sessions
     * (TEA-08, TEA-11). A tea without one is absent.
     */
    fun observeTeaRatings(): Flow<Map<String, TeaRating>>

    /** [teaId]'s tasting summary over its finished sessions (TEA-13, JRN-03); [TeaTasting.EMPTY] without data. */
    fun observeTeaTasting(teaId: String): Flow<TeaTasting>
}

/** [JournalRepository] on Room. */
class RoomJournalRepository @Inject constructor(db: UmamiDatabase, private val photos: PhotoFiles) :
    JournalRepository {
    private val journal = db.journalDao()
    private val sessions = db.sessionDao()
    private val tasting = db.tastingDao()

    override suspend fun deleteSession(sessionId: String) = sessions.deleteFinished(sessionId)

    override fun observeSessions(): Flow<List<JournalSession>> = journal.observeSessions().map { rows ->
        rows.map { row ->
            JournalSession(
                session = row.session.toModel(row.steps.sortedBy { it.position }, row.descriptors),
                coverPhoto = row.teaCoverPhotoId?.let(photos::file),
            )
        }
    }

    override fun observeTeaRatings(): Flow<Map<String, TeaRating>> = tasting.observeTeaRatings().map { rows ->
        rows.associate { it.teaId to TeaRating(it.average, it.count) }
    }

    override fun observeTeaTasting(teaId: String): Flow<TeaTasting> = combine(
        tasting.observeTeaRating(teaId),
        tasting.observeTasteAverages(teaId),
        tasting.observeDescriptorCounts(teaId),
    ) { rating, taste, descriptors ->
        TeaTasting(
            rating = rating?.let { TeaRating(it.average, it.count) },
            tasteAverages = taste.byAxis(),
            descriptorCounts = descriptors.map { DescriptorCount(it.descriptorKey, it.count) },
        )
    }
}

/** The set axes' averages; an axis no session set is absent. */
private fun TasteAveragesRow.byAxis(): Map<TasteAxis, Double> = buildMap {
    umami?.let { put(TasteAxis.UMAMI, it) }
    sweetness?.let { put(TasteAxis.SWEETNESS, it) }
    bitterness?.let { put(TasteAxis.BITTERNESS, it) }
    astringency?.let { put(TasteAxis.ASTRINGENCY, it) }
    body?.let { put(TasteAxis.BODY, it) }
    aroma?.let { put(TasteAxis.AROMA, it) }
}
