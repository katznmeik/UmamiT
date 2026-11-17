package com.umamit.core.data

import androidx.room.withTransaction
import com.umamit.core.database.UmamiDatabase
import javax.inject.Inject

/** [DatabaseTransactions] on Room: nested repository transactions join the outer one. */
class RoomDatabaseTransactions @Inject constructor(private val db: UmamiDatabase) : DatabaseTransactions {
    override suspend fun <R> inTransaction(block: suspend () -> R): R = db.withTransaction(block)
}
