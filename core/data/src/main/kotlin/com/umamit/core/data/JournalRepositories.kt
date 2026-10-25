package com.umamit.core.data

import com.umamit.core.database.UmamiDatabase
import com.umamit.core.model.BrewSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject

/** A session as the journal lists it (SCR-15): the row with its steps and its tea's cover photo file, if any. */
data class JournalSession(val session: BrewSession, val coverPhoto: File?)

/**
 * The journal's read side (JRN-01, JRN-08). It never writes: the session rows,
 * the stock deduction and the opened date come from [SessionRepository] (#36).
 */
interface JournalRepository {
    /** Every session, in progress or finished, newest start first; emits again on every write. */
    fun observeSessions(): Flow<List<JournalSession>>
}

/** [JournalRepository] on Room. */
class RoomJournalRepository @Inject constructor(db: UmamiDatabase, private val photos: PhotoFiles) :
    JournalRepository {
    private val journal = db.journalDao()

    override fun observeSessions(): Flow<List<JournalSession>> = journal.observeSessions().map { rows ->
        rows.map { row ->
            JournalSession(
                session = row.session.toModel(row.steps.sortedBy { it.position }),
                coverPhoto = row.teaCoverPhotoId?.let(photos::file),
            )
        }
    }
}
