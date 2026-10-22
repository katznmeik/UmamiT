package com.umamit.core.data.diagnostics

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * One entry of the [DiagnosticsLog]: the local [time] it happened, to the
 * millisecond with the zone offset, and its one-line [message].
 */
data class DiagnosticsEntry(val time: String, val message: String) {
    /** The entry as one line of text, as *Kopieren* puts it on the clipboard. */
    val line: String get() = "$time  $message"
}

/**
 * The app's rolling diagnostics log (06 §5.6, M1-20; D-060): plain text lines
 * in app-private storage that no backup includes, at most [maxBytes] in all.
 * It is kept in two files of half the cap each; when the newer one is full it
 * replaces the older one, so the oldest entries go first.
 *
 * Writing never blocks and never throws: [append] only queues the entry, and
 * one coroutine on the background [dispatcher] writes the queue in order. A
 * storage failure loses that entry and nothing else (rule 8: the timer that
 * writes here must never wait for or fail on its log). [entries] is queued
 * behind the writes before it, so it sees them.
 *
 * Entries hold no personal content: tea names are allowed, notes never
 * (06 §5.6); the writers only pass plain values.
 */
class DiagnosticsLog(
    /** The log's own directory; created on the first write. */
    val directory: File,
    private val maxBytes: Long,
    dispatcher: CoroutineDispatcher,
) {
    private sealed interface Command {
        class Append(val wallMs: Long, val zone: ZoneId, val message: String) : Command

        class Read(val result: CompletableDeferred<List<DiagnosticsEntry>>) : Command
    }

    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val current = File(directory, CURRENT_FILE)
    private val previous = File(directory, PREVIOUS_FILE)
    private val mutableRevision = MutableStateFlow(0L)

    /** Raised after every write, so a screen showing the log can read it again. */
    val revision: StateFlow<Long> = mutableRevision.asStateFlow()

    init {
        CoroutineScope(SupervisorJob() + dispatcher).launch {
            for (command in commands) {
                when (command) {
                    is Command.Append -> {
                        write(command)
                        mutableRevision.value += 1
                    }

                    is Command.Read -> command.result.complete(read())
                }
            }
        }
    }

    /**
     * Queues [message] as an entry at [wallMs] in [zone]; returns at once.
     * Line breaks and tabs in [message] become spaces, so an entry is one line.
     */
    fun append(wallMs: Long, zone: ZoneId, message: String) {
        // An unlimited channel never refuses while it is open, and it is never closed.
        commands.trySend(Command.Append(wallMs, zone, message))
    }

    /** All entries, newest first; empty when the log cannot be read. */
    suspend fun entries(): List<DiagnosticsEntry> {
        val result = CompletableDeferred<List<DiagnosticsEntry>>()
        commands.send(Command.Read(result))
        return result.await()
    }

    private fun write(entry: Command.Append) {
        val time = TIME_FORMAT.format(Instant.ofEpochMilli(entry.wallMs).atZone(entry.zone))
        val message = entry.message.replace(LINE_BREAK, " ").replace('\t', ' ').take(MAX_MESSAGE_CHARS)
        val bytes = "$time$SEPARATOR$message\n".toByteArray(Charsets.UTF_8)
        guarded {
            directory.mkdirs()
            if (current.exists() && current.length() + bytes.size > maxBytes / 2) {
                previous.delete()
                current.renameTo(previous)
            }
            FileOutputStream(current, true).use { it.write(bytes) }
        }
    }

    private fun read(): List<DiagnosticsEntry> = guarded {
        listOf(previous, current)
            .filter { it.isFile }
            .flatMap { it.readLines(Charsets.UTF_8) }
            .mapNotNull(::parse)
            .asReversed()
    } ?: emptyList()

    private fun parse(line: String): DiagnosticsEntry? {
        if (line.isBlank()) return null
        val split = line.indexOf(SEPARATOR)
        return if (split < 0) DiagnosticsEntry("", line) else DiagnosticsEntry(line.take(split), line.drop(split + 1))
    }

    /** Runs [block]; a storage failure loses this write or read only (rule 8), so it gives null. */
    @Suppress("SwallowedException") // the log is best effort; there is nowhere left to report its own failure
    private inline fun <T> guarded(block: () -> T): T? = try {
        block()
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        null
    }

    companion object {
        /** The cap of 06 §5.6: 200 KB for both files together. */
        const val MAX_BYTES: Long = 200L * 1024

        /** The log's directory inside the app's no-backup storage (`Context.noBackupFilesDir`, 05 §8). */
        const val DIRECTORY = "diagnostics"

        private const val CURRENT_FILE = "diagnostics.log"
        private const val PREVIOUS_FILE = "diagnostics.1.log"
        private const val SEPARATOR = '\t'
        private const val MAX_MESSAGE_CHARS = 2_000
        private val LINE_BREAK = Regex("\r\n|\r|\n")
        private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS xxx")
    }
}
