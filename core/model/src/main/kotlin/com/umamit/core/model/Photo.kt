package com.umamit.core.model

/**
 * A stored photo (05 §3.9): the file `<id>.jpg` in `filesDir/photos/`.
 * It belongs to exactly one tea, session or vessel.
 */
data class Photo(
    val id: String,
    val owner: PhotoOwner,
    val origin: PhotoOrigin,
    val widthPx: Int,
    val heightPx: Int,
    val byteSize: Long,
    val position: Int = 0,
) {
    companion object {
        /** A tea holds at most 10 photos (TEA-05, SCR-28, D-104). */
        const val MAX_PER_TEA = 10
    }
}

/** What a photo belongs to; exactly one owner per photo (05 §3.9). */
sealed interface PhotoOwner {
    val id: String

    data class OfTea(override val id: String) : PhotoOwner

    data class OfSession(override val id: String) : PhotoOwner

    data class OfVessel(override val id: String) : PhotoOwner
}
