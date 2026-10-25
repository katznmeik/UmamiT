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
    fun write(id: String, bytes: ByteArray): File {
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("cannot create $dir")
        val target = file(id)
        val temp = File(dir, "$id.jpg.tmp")
        temp.writeBytes(bytes)
        if (!temp.renameTo(target)) {
            temp.delete()
            throw IOException("cannot move $temp to $target")
        }
        return target
    }

    /** Deletes photo [id]'s file if it exists. */
    fun delete(id: String) {
        file(id).delete()
    }
}
