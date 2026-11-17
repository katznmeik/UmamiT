package com.umamit.core.data

import androidx.room.withTransaction
import com.umamit.core.common.WallClock
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.database.entity.SessionDescriptorEntity
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.StepKind
import com.umamit.core.model.TasteAxis
import com.umamit.core.model.TasteDescriptor
import com.umamit.core.model.TastingLimits
import javax.inject.Inject

/**
 * [SessionTasting] on Room (05 §3.7 „Tasting writes", „Infusion tasting writes", §3.8): each write one transaction.
 * The session's inputs on a `COMPLETED` session only, setting its `updated_at`; an infusion's taste and note on its
 * ended, non-*Spülen* step row of a session in progress or completed, column by column. Stock, cost and `active_brew`
 * are never read or written here.
 */
class RoomSessionTasting @Inject constructor(private val db: UmamiDatabase, private val time: WallClock) :
    SessionTasting {
    private val tasting = db.tastingDao()

    override suspend fun setRating(sessionId: String, rating: Double?): TastingWrite {
        if (rating != null && !TastingLimits.isValidRating(rating)) return TastingWrite.INVALID
        return tasting.setRating(sessionId, rating, time.nowMs()).written()
    }

    override suspend fun setBrewingRating(sessionId: String, rating: Double?): TastingWrite {
        if (rating != null && !TastingLimits.isValidRating(rating)) return TastingWrite.INVALID
        return tasting.setBrewingRating(sessionId, rating, time.nowMs()).written()
    }

    override suspend fun setInfusionTaste(
        sessionId: String,
        position: Int,
        axis: TasteAxis,
        value: Int?,
    ): TastingWrite {
        if (value != null && !TastingLimits.isValidTaste(value)) return TastingWrite.INVALID
        return infusionWrite(sessionId, position) { tasting.setInfusionTaste(sessionId, position, axis.name, value) }
    }

    override suspend fun clearInfusionTaste(sessionId: String, position: Int): TastingWrite =
        infusionWrite(sessionId, position) { tasting.clearInfusionTaste(sessionId, position) }

    override suspend fun setInfusionNote(sessionId: String, position: Int, note: String?): TastingWrite {
        val trimmed = note?.trim()?.takeIf { it.isNotEmpty() }
        if (trimmed != null && trimmed.length > TastingLimits.MAX_INFUSION_NOTE_LENGTH) return TastingWrite.INVALID
        return infusionWrite(sessionId, position) { tasting.setInfusionNote(sessionId, position, trimmed) }
    }

    /**
     * One infusion write in its own transaction (05 §3.7 „Infusion tasting writes"): [write] runs only when the
     * session is in progress or completed and its row at [position] is ended and not *Spülen*; a `COMPLETED`
     * session's `updated_at` follows. An in-progress session's row and `active_brew` are never touched.
     */
    private suspend fun infusionWrite(sessionId: String, position: Int, write: suspend () -> Int): TastingWrite {
        val now = time.nowMs()
        return db.withTransaction {
            val target = tasting.infusionTarget(sessionId, position)
            when {
                target == null || target.status !in INFUSION_STATUSES -> TastingWrite.NOT_COMPLETED

                target.kind == null || target.kind == StepKind.RINSE || target.ended != true ->
                    TastingWrite.NOT_AN_INFUSION

                write() == 0 -> TastingWrite.NOT_AN_INFUSION

                else -> {
                    if (target.status == SessionStatus.COMPLETED) tasting.touch(sessionId, now)
                    TastingWrite.SAVED
                }
            }
        }
    }

    override suspend fun setNotes(sessionId: String, notes: String?): TastingWrite {
        val trimmed = notes?.trim()?.takeIf { it.isNotEmpty() }
        if (trimmed != null && trimmed.length > TastingLimits.MAX_NOTES_LENGTH) return TastingWrite.INVALID
        return tasting.setNotes(sessionId, trimmed, time.nowMs()).written()
    }

    override suspend fun setDescriptor(sessionId: String, key: String, ticked: Boolean): TastingWrite {
        // A tick needs a key the app knows; unticking any stored key stays allowed (unknown keys stay stored).
        if (key.isBlank() || (ticked && TasteDescriptor.fromKey(key) == null)) return TastingWrite.INVALID
        val now = time.nowMs()
        return db.withTransaction {
            when {
                !tasting.isCompleted(sessionId) -> TastingWrite.NOT_COMPLETED

                !ticked -> {
                    tasting.deleteDescriptor(sessionId, key)
                    tasting.touch(sessionId, now).written()
                }

                tasting.hasDescriptor(sessionId, key) -> tasting.touch(sessionId, now).written()

                tasting.descriptorCount(sessionId) >= TastingLimits.MAX_DESCRIPTORS -> TastingWrite.DESCRIPTOR_LIMIT

                else -> {
                    tasting.insertDescriptor(SessionDescriptorEntity(sessionId, key, createdAt = now))
                    tasting.touch(sessionId, now).written()
                }
            }
        }
    }

    /** A single-row UPDATE's count as the write's outcome: 0 rows means no completed session of that id. */
    private fun Int.written(): TastingWrite = if (this > 0) TastingWrite.SAVED else TastingWrite.NOT_COMPLETED
}

/** The sessions whose infusions take tasting writes (05 §3.7, D-114): one running and one completed. */
private val INFUSION_STATUSES = setOf(SessionStatus.IN_PROGRESS, SessionStatus.COMPLETED)
