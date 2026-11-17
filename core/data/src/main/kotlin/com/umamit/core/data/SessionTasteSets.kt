package com.umamit.core.data

import android.util.Log
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.database.entity.BrewSessionEntity
import com.umamit.core.model.TasteSet
import com.umamit.core.model.TeaCategory
import kotlin.coroutines.cancellation.CancellationException

/**
 * The taste set a session shows (JRN-03, 05 §3.7, D-128): its tea's now, from [teaCategory] and [teaTasteSet] (its
 * `taste_set`); for a session whose tea is gone ([teaCategory] null), the set of [snapshotCategory].
 */
internal fun sessionTasteSet(teaCategory: TeaCategory?, teaTasteSet: TasteSet?, snapshotCategory: TeaCategory?) =
    if (teaCategory == null) TasteSet.of(snapshotCategory) else TasteSet.effective(teaTasteSet, teaCategory)

/**
 * [session]'s taste set ([sessionTasteSet]), its tea read from [db]. A tea row that cannot be read (an undecodable
 * `taste_set`, a failing read) is logged and gives the set of the session's snapshot category, so a session read, and
 * with it the timer's recovery, never fails on the set (rule 8).
 */
internal suspend fun tasteSetOf(db: UmamiDatabase, session: BrewSessionEntity): TasteSet {
    val tea = try {
        session.teaId?.let { db.teaDao().get(it) }
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        Log.w(TAG, "Tea of a session not readable; its snapshot category's taste set is shown", e)
        return TasteSet.of(session.category)
    }
    return sessionTasteSet(tea?.category, tea?.tasteSet, session.category)
}

private const val TAG = "UmamiTTasteSets"
