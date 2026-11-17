package com.umamit.core.data

import android.database.SQLException
import androidx.room.withTransaction
import com.umamit.core.database.UmamiDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/**
 * [write] off the main thread and not cancelled halfway: leaving the screen while a photo is processed never leaves
 * a file without its row. A database, file or processing error answers [failed]; cancellation is passed on.
 */
internal suspend fun <T> photoWrite(failed: T, write: suspend () -> T): T = try {
    withContext(NonCancellable + Dispatchers.IO) { write() }
} catch (_: SQLException) {
    failed
} catch (_: IOException) {
    failed
} catch (e: CancellationException) {
    throw e
} catch (
    // Rule 8: the timer runs in this process; a bug in the processing fails the write, never the app (D-090).
    @Suppress("TooGenericExceptionCaught", "SwallowedException") e: RuntimeException,
) {
    failed
}

/**
 * Adds a photo to a tea or a session (TEA-05, JRN-07) inside [photoWrite]: refused before the image is read when
 * [refusal] knows the answer already, else [load]ed, else why ([AddPhotoResult.Unreadable] when it can't be read).
 * The file is written before the row; the row goes in with [insert] in a transaction that asks [refusal] again,
 * and the file is removed unless that ends [AddPhotoResult.Added].
 */
internal suspend fun addPhoto(
    db: UmamiDatabase,
    files: PhotoFiles,
    refusal: suspend () -> AddPhotoResult?,
    load: suspend () -> EncodedPhoto?,
    insert: suspend (id: String, photo: EncodedPhoto) -> AddPhotoResult,
): AddPhotoResult = photoWrite(AddPhotoResult.Failed) {
    val photo = if (refusal() == null) load() else null
    when {
        photo != null -> storePhoto(db, files, photo) { id -> refusal() ?: insert(id, photo) }
        else -> refusal() ?: AddPhotoResult.Unreadable
    }
}

/** Writes [photo]'s file, then its row with [row]; a row that isn't written takes the file with it. */
private suspend fun storePhoto(
    db: UmamiDatabase,
    files: PhotoFiles,
    photo: EncodedPhoto,
    row: suspend (id: String) -> AddPhotoResult,
): AddPhotoResult {
    val id = UUID.randomUUID().toString()
    files.write(id, photo.bytes)
    var result: AddPhotoResult = AddPhotoResult.Failed
    try {
        result = db.withTransaction { row(id) }
    } finally {
        if (result !is AddPhotoResult.Added) files.delete(id)
    }
    return result
}
