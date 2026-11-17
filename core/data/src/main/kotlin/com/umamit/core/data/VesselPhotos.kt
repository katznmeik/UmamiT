package com.umamit.core.data

import android.content.Context
import androidx.room.withTransaction
import com.umamit.core.common.WallClock
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.model.PhotoOrigin
import com.umamit.core.model.PhotoOwner
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import javax.inject.Inject

/** How [VesselPhotos.set] ended. */
sealed interface SetVesselPhotoResult {
    /** The photo [photoId] is the vessel's photo now; a former one is gone. */
    data class Set(val photoId: String) : SetVesselPhotoResult

    /** The picked image could not be read or decoded; nothing changed. */
    data object Unreadable : SetVesselPhotoResult

    /** The vessel is gone or the write failed; nothing changed. */
    data object Failed : SetVesselPhotoResult
}

/**
 * A vessel's photo (WARE-01, SCR-14, 05 §3.9, D-104): at most one, set, replaced or removed. The cover of the
 * vessel (`vessel.cover_photo_id`) is that photo.
 */
interface VesselPhotos {
    /**
     * Makes the image the photo picker returned as [uri] the photo of vessel [vesselId], processed like a tea's
     * (06 §5.2). A former photo is replaced: its row goes with the new row's transaction, its file after the
     * commit. The new file is written before the row and removed again when the row isn't.
     */
    suspend fun set(vesselId: String, uri: String): SetVesselPhotoResult

    /** Removes the vessel's photo, row and file; true also when it had none, false when the write failed. */
    suspend fun remove(vesselId: String): Boolean
}

/** [VesselPhotos] on Room and [PhotoFiles]; a picked image is read through the app's content resolver. */
class RoomVesselPhotos @Inject constructor(
    private val db: UmamiDatabase,
    private val time: WallClock,
    private val files: PhotoFiles,
    @param:ApplicationContext private val context: Context,
) : VesselPhotos {
    override suspend fun set(vesselId: String, uri: String): SetVesselPhotoResult =
        photoWrite(SetVesselPhotoResult.Failed) { setNow(vesselId, uri) }

    private suspend fun setNow(vesselId: String, uri: String): SetVesselPhotoResult {
        val photo = if (db.vesselDao().get(vesselId) == null) null else loadPicked(context, uri)
        return when {
            photo != null -> store(vesselId, photo)
            db.vesselDao().get(vesselId) == null -> SetVesselPhotoResult.Failed
            else -> SetVesselPhotoResult.Unreadable
        }
    }

    /** Writes [photo]'s file, then its row; a row that isn't written takes the file with it. */
    private suspend fun store(vesselId: String, photo: EncodedPhoto): SetVesselPhotoResult {
        val id = UUID.randomUUID().toString()
        files.write(id, photo.bytes)
        var former: List<String>? = null
        try {
            former = db.withTransaction { replace(vesselId, id, photo) }
        } finally {
            if (former == null) files.delete(id)
        }
        former.forEach(files::delete)
        return SetVesselPhotoResult.Set(id)
    }

    /** Stores the new row as the cover and drops the others; returns the former ids. Throws for a vanished vessel. */
    private suspend fun replace(vesselId: String, id: String, photo: EncodedPhoto): List<String> {
        val dao = db.vesselDao()
        check(dao.get(vesselId) != null) { "vessel $vesselId is gone" }
        val now = time.nowMs()
        val former = dao.photoIds(vesselId)
        val row = photo.toPhoto(id, PhotoOwner.OfVessel(vesselId), PhotoOrigin.OWN)
        dao.upsertPhoto(row.toEntity(createdAt = now, updatedAt = now))
        dao.setCoverPhoto(vesselId, id, now)
        dao.deletePhotosExcept(vesselId, keepId = id)
        return former
    }

    override suspend fun remove(vesselId: String): Boolean = photoWrite(failed = false) {
        val former = db.withTransaction {
            val dao = db.vesselDao()
            val ids = dao.photoIds(vesselId)
            dao.setCoverPhoto(vesselId, null, time.nowMs())
            dao.deletePhotosExcept(vesselId, keepId = "")
            ids
        }
        former.forEach(files::delete)
        true
    }
}

/** Binds [VesselPhotos] (WARE-01). */
@Module
@InstallIn(SingletonComponent::class)
abstract class VesselPhotosModule {
    @Binds
    abstract fun vesselPhotos(impl: RoomVesselPhotos): VesselPhotos
}
