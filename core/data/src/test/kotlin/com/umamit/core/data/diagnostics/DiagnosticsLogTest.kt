package com.umamit.core.data.diagnostics

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The rolling diagnostics log (06 §5.6, M1-20): entries come back newest
 * first with their local time, the files never grow beyond the cap (the
 * oldest entries go first), the log survives a process restart, and a
 * storage failure never reaches the writer.
 */
class DiagnosticsLogTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dir: File get() = File(folder.root, "diagnostics")

    private fun log(maxBytes: Long = DiagnosticsLog.MAX_BYTES) = DiagnosticsLog(dir, maxBytes, Dispatchers.IO)

    @Test
    fun `entries come back newest first with their local time to the millisecond`() = runBlocking {
        val log = log()

        log.append(AT_10_00, BERLIN, "first")
        log.append(AT_10_00 + 5_120, BERLIN, "second")

        assertThat(log.entries()).containsExactly(
            DiagnosticsEntry("2026-10-22 10:00:05.120 +02:00", "second"),
            DiagnosticsEntry("2026-10-22 10:00:00.000 +02:00", "first"),
        ).inOrder()
    }

    @Test
    fun `an entry is one line, whatever its message holds`() = runBlocking {
        val log = log()

        log.append(AT_10_00, BERLIN, "line one\nline two\r\n\tindented")

        assertThat(log.entries().single().message).isEqualTo("line one line two  indented")
    }

    @Test
    fun `the log never grows beyond its cap and drops the oldest entries first`() = runBlocking {
        val log = log()

        // About 350 KB of entries, well beyond the 200 KB cap.
        repeat(ENTRIES) { log.append(AT_10_00 + it, BERLIN, "entry %05d ".format(it) + "x".repeat(PADDING)) }
        val entries = log.entries()

        assertThat(dir.listFiles()!!.sumOf { it.length() }).isAtMost(200L * 1024)
        assertThat(entries.first().message).startsWith("entry %05d".format(ENTRIES - 1))
        assertThat(entries.none { it.message.startsWith("entry 00000") }).isTrue()
        // What is left is the newest part, without gaps.
        val numbers = entries.map { it.message.substring(6, 11).toInt() }
        assertThat(numbers).isEqualTo((ENTRIES - 1 downTo ENTRIES - entries.size).toList())
        // It keeps at least half of the cap (less one entry).
        assertThat(dir.listFiles()!!.sumOf { it.length() }).isAtLeast(100L * 1024 - 200)
    }

    @Test
    fun `the log survives a process restart and keeps its cap across it`() = runBlocking {
        val before = log(maxBytes = 4_096)
        repeat(40) { before.append(AT_10_00 + it, BERLIN, "before %02d ".format(it) + "x".repeat(40)) }
        before.entries()

        val after = log(maxBytes = 4_096)
        assertThat(after.entries().first().message).startsWith("before 39")
        repeat(40) { after.append(AT_10_00 + 100 + it, BERLIN, "after %02d ".format(it) + "x".repeat(40)) }

        assertThat(after.entries().first().message).startsWith("after 39")
        assertThat(dir.listFiles()!!.sumOf { it.length() }).isAtMost(4_096L)
    }

    @Test
    fun `a storage failure is swallowed and the log reads as empty`() = runBlocking {
        // A file where the directory should be: nothing can be written or read.
        dir.writeText("not a directory")
        val log = log()

        log.append(AT_10_00, BERLIN, "lost")

        assertThat(log.entries()).isEmpty()
    }

    @Test
    fun `every write raises the revision, so a screen can follow the log`() = runBlocking {
        val log = log()
        val start = log.revision.value

        log.append(AT_10_00, BERLIN, "one")
        log.append(AT_10_00, BERLIN, "two")
        log.entries()

        assertThat(log.revision.value).isEqualTo(start + 2)
    }

    @Test
    fun `the full text is the entries as shown, one per line`() = runBlocking {
        val log = log()
        log.append(AT_10_00, BERLIN, "first")
        log.append(AT_10_00 + 1, BERLIN, "second")

        assertThat(log.entries().joinToString("\n") { it.line }).isEqualTo(
            "2026-10-22 10:00:00.001 +02:00  second\n2026-10-22 10:00:00.000 +02:00  first",
        )
    }

    private companion object {
        val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")
        val AT_10_00: Long = ZonedDateTime.of(2026, 10, 22, 10, 0, 0, 0, BERLIN).toInstant().toEpochMilli()
        const val ENTRIES = 3_000
        const val PADDING = 80
    }
}
