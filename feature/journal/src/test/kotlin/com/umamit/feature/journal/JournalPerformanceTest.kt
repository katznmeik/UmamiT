package com.umamit.feature.journal

import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.JournalSession
import com.umamit.feature.journal.JournalSessions.TODAY
import com.umamit.feature.journal.JournalSessions.session
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * JRN-09 AC1 / NFR-PERF-03: the journal with 10 000 sessions. The per-keystroke work (the search and the filters
 * over the rows, in memory) must stay within one frame of a 120 Hz display (8,3 ms) at the median, and within a
 * 60 Hz frame (16,7 ms) at the 95th percentile, measured over warmed-up runs. The list itself is lazy
 * (`JournalScreen` composes only the visible rows), so scrolling does not depend on the row count.
 */
class JournalPerformanceTest {
    private val labels = mapOf("marine.nori" to "Nori", "floral.jasmin" to "Jasmin")

    private val sessions: List<JournalSession> = List(SESSION_COUNT) { index ->
        val day = TODAY.minusDays((index / SESSIONS_PER_DAY).toLong())
        session(
            "s$index",
            LocalDateTime.of(day, LocalTime.of(index % HOURS_PER_DAY, 0)),
            teaName = "Tee ${index % TEA_COUNT} Sencha Hisui",
            teaId = "tea-${index % TEA_COUNT}",
            rating = if (index % 3 == 0) 3.5 + index % 4 * 0.5 else null,
            notes = if (index % 2 == 0) NOTE else null,
            descriptorKeys = if (index % 5 == 0) listOf("marine.nori") else emptyList(),
        )
    }

    private val list = journalList(sessions, TODAY, labels::get)

    @Test
    fun `building the list and filtering 10 000 sessions fit a frame`() {
        val filters = listOf(
            JournalFilter(search = "s"),
            JournalFilter(search = "sencha frisch"),
            JournalFilter(search = "nori"),
            JournalFilter(search = "zzz"),
            JournalFilter(minRating = 4.0, withNotes = true),
            JournalFilter(teaKey = "tea-7", search = "abgang"),
        )
        repeat(WARM_UP) { filters.forEach { list.filtered(it) } }

        val millis = List(RUNS) { run ->
            val filter = filters[run % filters.size]
            val start = System.nanoTime()
            list.filtered(filter)
            (System.nanoTime() - start) / NANOS_PER_MILLI
        }.sorted()

        assertThat(list.sessionCount).isEqualTo(SESSION_COUNT)
        assertThat(millis[RUNS / 2]).isLessThan(FRAME_120_HZ_MS)
        assertThat(millis[RUNS * 95 / 100]).isLessThan(FRAME_60_HZ_MS)
    }

    private companion object {
        const val SESSION_COUNT = 10_000
        const val SESSIONS_PER_DAY = 3
        const val HOURS_PER_DAY = 24
        const val TEA_COUNT = 40
        const val WARM_UP = 20
        const val RUNS = 120
        const val NANOS_PER_MILLI = 1_000_000.0
        const val FRAME_120_HZ_MS = 8.3
        const val FRAME_60_HZ_MS = 16.7
        val NOTE = "Sehr frisch, grasig im Abgang, süßlich und lange nachklingend. ".repeat(4)
    }
}
