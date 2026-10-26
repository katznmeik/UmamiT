package com.umamit.core.data.catalog

import java.io.File
import java.io.IOException

/**
 * The catalog's photo files (CAT-08, 05 §4.1, D-076): `<key>.webp` (≤ 1200 px) and the list
 * thumbnail `<key>_thumb.webp` in app-private `filesDir/catalog/`, which no backup includes
 * (05 §8). Only the catalog refresh writes them.
 */
class CatalogPhotoFiles(val dir: File) {
    /** The guides' images, in their own folder inside [dir] (CAT-09, D-087); [retainOnly] never touches it. */
    val guideImages: GuideImageFiles = GuideImageFiles(File(dir, GuideImageFiles.DIRECTORY))

    /** The photo's file name as `catalog_tea.photo_file` stores it. */
    fun photoName(key: String): String = "${checked(key)}$PHOTO_SUFFIX"

    fun photo(key: String): File = File(dir, photoName(key))

    fun thumbnail(key: String): File = File(dir, "${checked(key)}$THUMBNAIL_SUFFIX")

    /** Whether tea [key] has both of its files. */
    fun exists(key: String): Boolean = photo(key).isFile && thumbnail(key).isFile

    /** Writes tea [key]'s [photo] and [thumbnail], each atomically (a temporary file renamed into place). */
    fun write(key: String, photo: ByteArray, thumbnail: ByteArray) {
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("cannot create $dir")
        writeAtomically(thumbnail(key), thumbnail)
        writeAtomically(photo(key), photo)
    }

    /** Deletes the photos and thumbnails (and leftover temporary files) of every tea not in [keys]. */
    fun retainOnly(keys: Set<String>) {
        dir.listFiles()?.filter { it.isFile }?.forEach { file ->
            val name = file.name
            val key = when {
                name.endsWith(TEMP_SUFFIX) -> null
                name.endsWith(THUMBNAIL_SUFFIX) -> name.removeSuffix(THUMBNAIL_SUFFIX)
                name.endsWith(PHOTO_SUFFIX) -> name.removeSuffix(PHOTO_SUFFIX)
                else -> return@forEach
            }
            if (key == null || key !in keys) file.delete()
        }
    }

    private fun writeAtomically(target: File, bytes: ByteArray) {
        val temp = File(dir, target.name + TEMP_SUFFIX)
        temp.writeBytes(bytes)
        if (!temp.renameTo(target)) {
            temp.delete()
            throw IOException("cannot move $temp to $target")
        }
    }

    /** A catalog key is a base article number (05 §4.1): letters and digits only, so it is a safe file name. */
    private fun checked(key: String): String = key.also { require(KEY.matches(it)) { "not a catalog key: $it" } }

    companion object {
        /** The folder in `filesDir` (05 §4.1). */
        const val DIRECTORY = "catalog"

        private const val PHOTO_SUFFIX = ".webp"
        private const val THUMBNAIL_SUFFIX = "_thumb.webp"
        private const val TEMP_SUFFIX = ".tmp"
        private val KEY = Regex("[A-Za-z0-9]+")
    }
}
