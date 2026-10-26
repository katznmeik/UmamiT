package com.umamit.core.data.catalog

import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * The guides' images (CAT-09, D-087): ≤ 1200 px WebP files in app-private
 * `filesDir/catalog/guides/`, which no backup includes (05 §8). Each is named after its source
 * URL (`g_<hash>.webp`), so an image the shop changes (a new `?ts=`) is a new file, an unchanged
 * one is never fetched again, and a failed refresh never overwrites a file the stored guides show.
 * Only the catalog refresh writes them.
 */
class GuideImageFiles(val dir: File) {
    /** The file name of the image from [url], as a guide's body and `hero_file` store it. */
    fun name(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        return PREFIX + digest.take(HASH_BYTES).joinToString("") { "%02x".format(it) } + SUFFIX
    }

    fun file(name: String): File = File(dir, checked(name))

    fun exists(name: String): Boolean = file(name).isFile

    /** Writes the image [name] atomically (a temporary file renamed into place). */
    fun write(name: String, bytes: ByteArray) {
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("cannot create $dir")
        val target = file(name)
        val temp = File(dir, target.name + TEMP_SUFFIX)
        temp.writeBytes(bytes)
        if (!temp.renameTo(target)) {
            temp.delete()
            throw IOException("cannot move $temp to $target")
        }
    }

    /** Deletes every image (and leftover temporary file) not in [names]. */
    fun retainOnly(names: Set<String>) {
        dir.listFiles()?.filter { it.isFile && it.name !in names }?.forEach { it.delete() }
    }

    /** A stored name is ours: no path, so it never leaves the folder. */
    private fun checked(name: String): String = name.also { require(NAME.matches(it)) { "not a guide image: $it" } }

    companion object {
        /** The folder inside the catalog's photo folder (05 §4.3). */
        const val DIRECTORY = "guides"

        private const val PREFIX = "g_"
        private const val SUFFIX = ".webp"
        private const val TEMP_SUFFIX = ".tmp"
        private const val HASH_BYTES = 16
        private val NAME = Regex("""g_[0-9a-f]{32}\.webp""")

        /** Whether [name] is a guide image's file name (and so safe to resolve in [dir]). */
        fun isName(name: String): Boolean = NAME.matches(name)
    }
}
