package com.umamit.feature.journal

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.StepKind
import com.umamit.feature.journal.JournalSessions.TODAY
import com.umamit.feature.journal.JournalSessions.session
import com.umamit.feature.journal.JournalSessions.step
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** The journal list (JRN-08, SCR-15): day groups, newest first, the pinned session and the counts (TMR-12). */
class JournalListTest {
    private fun at(date: LocalDate, hour: Int, minute: Int = 0) = LocalDateTime.of(date, LocalTime.of(hour, minute))

    @Test
    fun `no sessions is the empty journal`() {
        assertThat(journalList(emptyList(), TODAY).isEmpty).isTrue()
    }

    @Test
    fun `sessions are grouped by local day, newest day and newest session first`() {
        val list = journalList(
            listOf(
                session("today-late", at(TODAY, 18, 30)),
                session("today-early", at(TODAY, 9, 12)),
                session("yesterday", at(TODAY.minusDays(1), 7, 21)),
                session("monday", at(LocalDate.of(2026, 10, 12), 6, 48)),
            ),
            TODAY,
        )

        assertThat(list.days.map { it.day }).containsExactly(
            JournalDay.Today,
            JournalDay.Yesterday,
            JournalDay.On(LocalDate.of(2026, 10, 12)),
        ).inOrder()
        assertThat(
            list.days.first().entries.map {
                it.sessionId
            },
        ).containsExactly("today-late", "today-early").inOrder()
        assertThat(list.days.first().entries.last().timeOfDay).isEqualTo(LocalTime.of(9, 12))
    }

    @Test
    fun `a session that spans midnight belongs to the day it started (E13)`() {
        val list = journalList(listOf(session("late-night", at(TODAY.minusDays(1), 23, 50))), TODAY)

        assertThat(list.days.single().day).isEqualTo(JournalDay.Yesterday)
    }

    @Test
    fun `day and time of day are those of the session's own time zone (E13)`() {
        // 01:00 on 22 October in Tokyo is 18:00 on 21 October in Berlin, where the phone is now.
        val tokyo = ZoneId.of("Asia/Tokyo")
        val list = journalList(listOf(session("travel", at(TODAY, 1), zone = tokyo)), TODAY)

        val group = list.days.single()
        assertThat(group.day).isEqualTo(JournalDay.Today)
        assertThat(group.entries.single().timeOfDay).isEqualTo(LocalTime.of(1, 0))
    }

    @Test
    fun `days are ordered by date even when a later zone started earlier in absolute time`() {
        // Tokyo 01:00 on the 22nd (absolute: 21st 16:00 UTC) is newer by local day than Berlin 20:00 on the 21st.
        val list = journalList(
            listOf(
                session("berlin", at(TODAY.minusDays(1), 20)),
                session("tokyo", at(TODAY, 1), zone = ZoneId.of("Asia/Tokyo")),
            ),
            TODAY,
        )

        assertThat(list.days.map { it.day }).containsExactly(JournalDay.Today, JournalDay.Yesterday).inOrder()
    }

    @Test
    fun `the in-progress session is pinned on top and not in a day group`() {
        val list = journalList(
            listOf(
                session("finished", at(TODAY, 9)),
                session(
                    "running",
                    at(TODAY, 8),
                    status = SessionStatus.IN_PROGRESS,
                    steps = listOf(step(0, 60), step(1, 20, actualSeconds = null), step(2, 60, actualSeconds = null)),
                ),
            ),
            TODAY,
        )

        assertThat(list.inProgress.map { it.sessionId }).containsExactly("running")
        assertThat(list.inProgress.single().infusions).isEqualTo(1)
        assertThat(list.inProgress.single().plannedInfusions).isEqualTo(3)
        assertThat(list.days.single().entries.map { it.sessionId }).containsExactly("finished")
    }

    @Test
    fun `an early finish counts the poured infusions against the plan („2 von 3", TMR-12)`() {
        val entry = journalList(
            listOf(session("early", at(TODAY, 9), steps = listOf(step(0, 60), step(1, 20)), plannedStepCount = 3)),
            TODAY,
        ).days.single().entries.single()

        assertThat(entry.infusions).isEqualTo(2)
        assertThat(entry.plannedInfusions).isEqualTo(3)
        assertThat(entry.finishedEarly).isTrue()
    }

    @Test
    fun `a full session is not an early finish`() {
        val entry = journalList(listOf(session("full", at(TODAY, 9))), TODAY).days.single().entries.single()

        assertThat(entry.infusions).isEqualTo(3)
        assertThat(entry.finishedEarly).isFalse()
    }

    @Test
    fun `rinses and skipped steps are not infusions, and the meta shows base temperature and the first infusion`() {
        val entry = journalList(
            listOf(
                session(
                    "rinse",
                    at(TODAY, 9),
                    baseTempC = 60,
                    steps = listOf(
                        step(0, 10, kind = StepKind.RINSE),
                        step(1, 90),
                        step(2, 30, skipped = true),
                        step(3, 45),
                    ),
                ),
            ),
            TODAY,
        ).days.single().entries.single()

        assertThat(entry.infusions).isEqualTo(2)
        assertThat(entry.plannedInfusions).isEqualTo(3)
        assertThat(entry.temperatureC).isEqualTo(60)
        assertThat(entry.firstInfusionSeconds).isEqualTo(90)
    }

    @Test
    fun `without a base temperature the first infusion's temperature is shown`() {
        val entry = journalList(
            listOf(session("no-base", at(TODAY, 9), baseTempC = null, steps = listOf(step(0, 60, tempC = 70)))),
            TODAY,
        ).days.single().entries.single()

        assertThat(entry.temperatureC).isEqualTo(70)
    }
}
