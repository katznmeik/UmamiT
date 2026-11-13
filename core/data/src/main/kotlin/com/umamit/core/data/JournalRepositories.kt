package com.umamit.core.data

import androidx.room.withTransaction
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.database.dao.JournalSessionRow
import com.umamit.core.database.dao.StepTastingRow
import com.umamit.core.database.dao.TasteAveragesRow
import com.umamit.core.model.BrewSession
import com.umamit.core.model.DescriptorCount
import com.umamit.core.model.StepTasting
import com.umamit.core.model.TasteAxis
import com.umamit.core.model.TasteProfile
import com.umamit.core.model.TeaRating
import com.umamit.core.model.TeaTasting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
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
     * event, so the leaf it deducted is back in the stock (INV-02), its descriptors and its photos (JRN-07): their
     * rows go by CASCADE, their files after the commit. A session in progress, or one that is gone already, is left
     * alone.
     */
    suspend fun deleteSession(sessionId: String)

    /** [sessionId]'s session as [observeSessions] lists it; null while there is none (a deleted one). */
    fun observeSession(sessionId: String): Flow<JournalSession?>

    /**
     * The taste and note stored per step `position` of [sessionId] (JRN-03, JRN-05, D-114); steps without any are
     * absent, and so is a session that does not exist. Only the step rows' taste columns are read, for the timer's
     * taste button and sheet (SCR-05 v0.6.1), so a taste write next to a running timer re-reads no whole session.
     */
    fun observeStepTastings(sessionId: String): Flow<Map<Int, StepTasting>> =
        observeSession(sessionId).map { it?.session?.stepTastings.orEmpty() }

    /**
     * [teaId]'s tasting summary over its finished sessions (TEA-13, JRN-03); the taste averages over every infusion of
     * them (D-114). [TeaTasting.EMPTY] without data.
     */
    fun observeTeaTasting(teaId: String): Flow<TeaTasting>
}

/** [JournalRepository] on Room. */
class RoomJournalRepository @Inject constructor(private val db: UmamiDatabase, private val photos: PhotoFiles) :
    JournalRepository {
    private val journal = db.journalDao()
    private val sessions = db.sessionDao()
    private val tasting = db.tastingDao()

    // NonCancellable: a caller that is cleared after the commit never leaves the files of a deleted session behind.
    override suspend fun deleteSession(sessionId: String) = withContext(NonCancellable + Dispatchers.IO) {
        // The photo ids are collected first: the rows go with the session by CASCADE (05 §3.9).
        val photoIds = db.withTransaction {
            val ids = db.sessionPhotoDao().photoIds(sessionId)
            sessions.deleteFinished(sessionId)
            if (sessions.get(sessionId) == null) ids else emptyList()
        }
        // After the commit: a failed transaction keeps the files its rows still name.
        photoIds.forEach(photos::delete)
    }

    override fun observeSessions(): Flow<List<JournalSession>> =
        journal.observeSessions().map { rows -> rows.map { it.toJournalSession() } }.flowOn(Dispatchers.Default)

    override fun observeSession(sessionId: String): Flow<JournalSession?> =
        journal.observeSession(sessionId).map { it?.toJournalSession() }.flowOn(Dispatchers.Default)

    override fun observeStepTastings(sessionId: String): Flow<Map<Int, StepTasting>> =
        tasting.observeStepTastings(sessionId).map { rows -> rows.associate { it.position to it.tasting() } }

    private fun JournalSessionRow.toJournalSession() = JournalSession(
        session = session.toModel(steps.sortedBy { it.position }, descriptors),
        coverPhoto = teaCoverPhotoId?.let(photos::file),
    )

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

/** The set axes' averages; an axis no infusion set is absent. */
private fun TasteAveragesRow.byAxis(): Map<TasteAxis, Double> = buildMap {
    umami?.let { put(TasteAxis.UMAMI, it) }
    sweetness?.let { put(TasteAxis.SWEETNESS, it) }
    bitterness?.let { put(TasteAxis.BITTERNESS, it) }
    astringency?.let { put(TasteAxis.ASTRINGENCY, it) }
    body?.let { put(TasteAxis.BODY, it) }
    aroma?.let { put(TasteAxis.AROMA, it) }
}

private fun StepTastingRow.tasting() = StepTasting(
    taste = TasteProfile(
        umami = tasteUmami,
        sweetness = tasteSweetness,
        bitterness = tasteBitterness,
        astringency = tasteAstringency,
        body = tasteBody,
        aroma = tasteAroma,
    ),
    note = tastingNote,
)
