package com.umamit.core.data

import com.umamit.core.database.UmamiDatabase
import com.umamit.core.database.entity.BrewSessionEntity
import com.umamit.core.model.TasteSet
import com.umamit.core.model.TeaCategory

/**
 * The taste set a session shows (JRN-03, 05 §3.7, D-128): its tea's now, from [teaCategory] and [teaTasteSet] (its
 * `taste_set`); for a session whose tea is gone ([teaCategory] null), the set of [snapshotCategory].
 */
internal fun sessionTasteSet(teaCategory: TeaCategory?, teaTasteSet: TasteSet?, snapshotCategory: TeaCategory?) =
    if (teaCategory == null) TasteSet.of(snapshotCategory) else TasteSet.effective(teaTasteSet, teaCategory)

/** [session]'s taste set ([sessionTasteSet]), its tea read from [db]. */
internal suspend fun tasteSetOf(db: UmamiDatabase, session: BrewSessionEntity): TasteSet {
    val tea = session.teaId?.let { db.teaDao().get(it) }
    return sessionTasteSet(tea?.category, tea?.tasteSet, session.category)
}
