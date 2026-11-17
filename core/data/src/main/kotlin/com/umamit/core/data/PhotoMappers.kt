package com.umamit.core.data

import com.umamit.core.database.entity.PhotoEntity
import com.umamit.core.model.Photo
import com.umamit.core.model.PhotoOrigin
import com.umamit.core.model.PhotoOwner

// Photo rows (05 §3.9): exactly one of tea_id / session_id / vessel_id is set.

internal fun PhotoEntity.toModel() = Photo(
    id = id,
    owner = when {
        teaId != null -> PhotoOwner.OfTea(teaId!!)
        sessionId != null -> PhotoOwner.OfSession(sessionId!!)
        else -> PhotoOwner.OfVessel(checkNotNull(vesselId) { "photo $id has no owner" })
    },
    origin = origin,
    widthPx = widthPx,
    heightPx = heightPx,
    byteSize = byteSize,
    position = position,
)

internal fun Photo.toEntity(createdAt: Long, updatedAt: Long) = PhotoEntity(
    id = id,
    teaId = (owner as? PhotoOwner.OfTea)?.id,
    sessionId = (owner as? PhotoOwner.OfSession)?.id,
    vesselId = (owner as? PhotoOwner.OfVessel)?.id,
    position = position,
    widthPx = widthPx,
    heightPx = heightPx,
    byteSize = byteSize,
    origin = origin,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/** The `photo` row of this re-encoded image as photo [id] of [owner] (05 §3.9). */
internal fun EncodedPhoto.toPhoto(id: String, owner: PhotoOwner, origin: PhotoOrigin, position: Int = 0) = Photo(
    id = id,
    owner = owner,
    origin = origin,
    widthPx = width,
    heightPx = height,
    byteSize = bytes.size.toLong(),
    position = position,
)
