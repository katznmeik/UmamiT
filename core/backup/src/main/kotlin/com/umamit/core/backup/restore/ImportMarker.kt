package com.umamit.core.backup.restore

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/** Which replace a marker guards: an import (it ends keeping the safety copy) or its undo (it ends deleting it). */
internal enum class ImportKind { IMPORT, UNDO }

/**
 * How far a replace got (the D-130 build note of #336). [PREPARED]: `photos.new/` and `import/settings.json` are in
 * place and the transaction may have committed, which only the database's token tells. [SETTINGS_WRITTEN]: the folder
 * swap and the settings write are done; step 5 (or 5' for an undo) is left.
 */
internal enum class ImportPhase { PREPARED, SETTINGS_WRITTEN }

/**
 * The phase marker `import/marker.json`; [token] is the commit token the transaction sets (`application_id`).
 * [importedAt] (epoch ms) is the import's time, which step 5 stores as `safetyCopyAt` even when it runs later
 * (D-133 Q3); [durable] is set once the commit is checkpointed into the database file, so `photos.old/` is no longer
 * needed for a roll back (D-133 item 13). Both have defaults, so a marker written without them still reads.
 */
@Serializable
internal data class ImportMarker(
    val v: Int = VERSION,
    val kind: ImportKind,
    val token: Int,
    val phase: ImportPhase,
    val importedAt: Long? = null,
    val durable: Boolean = false,
) {
    companion object {
        const val VERSION = 1
    }
}

/** Reads and writes the marker, each write atomic and durable. */
internal object MarkerFile {
    private val json = Json { encodeDefaults = true }

    /** The marker in [paths]; null when there is none or it cannot be read (an atomic write leaves no half one). */
    fun read(paths: ImportPaths): ImportMarker? {
        if (!paths.marker.exists()) return null
        return try {
            json.decodeFromString(ImportMarker.serializer(), paths.marker.readText())
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun write(paths: ImportPaths, marker: ImportMarker) {
        paths.importDir.mkdirs()
        Durable.writeAtomically(
            paths.marker,
            json.encodeToString(ImportMarker.serializer(), marker).encodeToByteArray(),
        )
    }

    /**
     * [marker] marked `durable` on disk (its commit is checkpointed), so the swap frees `photos.old/` at once; the
     * marker unchanged if that write fails (then `photos.old/` waits for the clean up).
     */
    fun markDurable(paths: ImportPaths, marker: ImportMarker): ImportMarker = try {
        marker.copy(durable = true).also { write(paths, it) }
    } catch (e: IOException) {
        Log.w("UmamiTImport", "marker not marked durable", e)
        marker
    }

    /** Deletes the marker, durably: the last step of a finished or rolled back replace. */
    fun delete(paths: ImportPaths) {
        if (paths.marker.exists() && !paths.marker.delete()) throw IOException("Cannot delete the import marker")
        Durable.syncDir(paths.importDir)
    }
}

/** Writes that survive a crash or a power loss: data fsynced before a rename, the folder fsynced after it. */
internal object Durable {
    /** Flushes [file]'s bytes to the disk. */
    fun sync(file: File) {
        FileOutputStream(file, true).use { it.fd.sync() }
    }

    /** Flushes [dir]'s entries (a rename in it); a file system that cannot open a folder skips it. */
    fun syncDir(dir: File) {
        if (!dir.isDirectory) return
        try {
            FileChannel.open(dir.toPath(), StandardOpenOption.READ).use { it.force(true) }
        } catch (_: IOException) {
            // Not every file system lets a folder be opened; the rename itself is still atomic there.
        }
    }

    /** [bytes] to [target]: a temporary file, fsynced, renamed over [target], the folder fsynced. */
    fun writeAtomically(target: File, bytes: ByteArray) {
        val temp = File(target.parentFile, "${target.name}.tmp")
        FileOutputStream(temp).use { out ->
            out.write(bytes)
            out.fd.sync()
        }
        rename(temp, target)
        target.parentFile?.let(::syncDir)
    }

    /** Renames [from] to [to] atomically, replacing a file at [to]; throws [IOException] if it cannot. */
    fun rename(from: File, to: File) {
        Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    /**
     * Moves the folder [from] to [to] (which must not exist): a rename where the file system allows it, else a durable
     * copy, then [from] deleted.
     */
    fun moveDir(from: File, to: File) {
        try {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            if (!to.mkdirs()) throw IOException("Cannot create $to")
            from.listFiles().orEmpty().forEach { file ->
                val copy = File(to, file.name)
                file.copyTo(copy)
                sync(copy)
            }
            try {
                deleteTree(from)
            } catch (_: IOException) {
                // The copy is complete; what stays of [from] is a staging, which the clean up deletes later.
            }
        }
        to.parentFile?.let(::syncDir)
    }

    /**
     * Deletes [file], and everything in it if it is a folder (a link is deleted, never followed); throws [IOException]
     * if something stays. A file or folder that vanishes meanwhile counts as deleted: no existence check comes before
     * the walk (unlike `deleteRecursively`, whose walk fails on a root that vanished after its check, #349).
     */
    fun deleteTree(file: File) {
        val path = file.toPath()
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            // Null when the folder vanished since the check: nothing left to walk.
            file.listFiles().orEmpty().forEach(::deleteTree)
        }
        Files.deleteIfExists(path)
    }
}
