package com.umamit.core.data

import java.io.File
import java.io.IOException

/**
 * The image files of the `photo` table (05 §3.9): `<id>.jpg` in app-private `filesDir/photos/`,
 * which is excluded from device backup (05 §8).
 */
class PhotoFiles(val dir: File) {
    /** The file of photo [id]. */
    fun file(id: String): File = File(dir, "$id.jpg")

    /** Writes [bytes] as photo [id] atomically (a temporary file renamed into place). */
    fun write(id: String, bytes: ByteArray): File = file(id).also { it.writeAtomically(bytes) }

    /** Deletes photo [id]'s file if it exists. */
    fun delete(id: String) {
        file(id).delete()
    }
}

/**
 * Writes [bytes] to this file atomically: its folder is created if missing, the bytes go to `<name>.tmp` beside it,
 * which is then renamed into place; a failed rename deletes the temporary file. Throws [IOException] when the folder
 * cannot be created or the file not moved.
 */
internal fun File.writeAtomically(bytes: ByteArray) {
    val dir = parentFile
    if (!dir.isDirectory && !dir.mkdirs()) throw IOException("cannot create $dir")
    val temp = File(dir, "$name.tmp")
    temp.writeBytes(bytes)
    if (!temp.renameTo(this)) {
        temp.delete()
        throw IOException("cannot move $temp to $this")
    }
}
