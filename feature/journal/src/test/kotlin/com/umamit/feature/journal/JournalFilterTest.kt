package com.umamit.feature.journal

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.SessionStatus
import com.umamit.feature.journal.JournalSessions.TODAY
import com.umamit.feature.journal.JournalSessions.session
import com.umamit.feature.journal.JournalSessions.step
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** The journal's search and filters (JRN-09, D-112, GEN-10): a pure function over the rows. */
class JournalFilterTest {
    private fun at(date: LocalDate, hour: Int) = LocalDateTime.of(date, LocalTime.of(hour, 0))

    private val labels = mapOf("marine.nori" to "Nori", "floral.jasmin" to "Jasmin", "sweet.suesslich" to "Süßlich")

    private val sessions = listOf(
        session(
            "hisui",
            at(TODAY, 9),
            teaName = "Bio Sencha Hisui",
            rating = 4.5,
            notes = "Sehr frisch, grasig im Abgang",
            descriptorKeys = listOf("marine.nori"),
        ),
        session("ueber", at(TODAY, 8), teaName = "Kabusecha Überraschung", rating = 3.0),
        session("yesterday", at(TODAY.minusDays(1), 18), teaName = "Gyokuro Asahi", rating = 4.0, notes = "mild"),
        session(
            "older",
            at(LocalDate.of(2026, 10, 12), 7),
            teaName = "Bio Sencha Hisui",
            descriptorKeys = listOf("floral.jasmin", "sweet.suesslich"),
        ),
        session(
            "running",
            at(TODAY, 10),
            teaName = "Sencha Tanabata",
            status = SessionStatus.IN_PROGRESS,
            steps = listOf(step(0, 60, actualSeconds = null)),
        ),
    )

    private val list = journalList(sessions, TODAY, labels::get)

    private fun ids(filter: JournalFilter) = list.filtered(filter).days.flatMap { it.entries }.map { it.sessionId }

    @Test
    fun `no search and no filter is the whole journal with the pinned session`() {
        val all = list.filtered(JournalFilter())

        assertThat(all).isSameInstanceAs(list)
        assertThat(all.inProgress.map { it.sessionId }).containsExactly("running")
    }

    @Test
    fun `a blank search is no search`() {
        assertThat(JournalFilter(search = "   ").isActive).isFalse()
        assertThat(list.filtered(JournalFilter(search = "  ")).inProgress).hasSize(1)
    }

    @Test
    fun `the search ignores case`() {
        assertThat(ids(JournalFilter(search = "GYOKURO"))).containsExactly("yesterday")
    }

    @Test
    fun `the search ignores accents in the tea name and in the query`() {
        assertThat(ids(JournalFilter(search = "uberraschung"))).containsExactly("ueber")
        assertThat(ids(JournalFilter(search = "Überr"))).containsExactly("ueber")
    }

    @Test
    fun `the search matches word prefixes of the tea name`() {
        assertThat(ids(JournalFilter(search = "hisu"))).containsExactly("hisui", "older").inOrder()
        assertThat(ids(JournalFilter(search = "isui"))).isEmpty()
    }

    @Test
    fun `the search reaches the note text`() {
        assertThat(ids(JournalFilter(search = "grasig"))).containsExactly("hisui")
        assertThat(ids(JournalFilter(search = "MILD"))).containsExactly("yesterday")
    }

    @Test
    fun `the search reaches the chosen descriptors' labels`() {
        assertThat(ids(JournalFilter(search = "nori"))).containsExactly("hisui")
        assertThat(ids(JournalFilter(search = "jasm"))).containsExactly("older")
        assertThat(ids(JournalFilter(search = "susslich"))).isEmpty()
        assertThat(ids(JournalFilter(search = "suß"))).containsExactly("older")
    }

    @Test
    fun `a descriptor key the app does not know is not searched`() {
        val unknown = journalList(
            listOf(session("s", at(TODAY, 9), descriptorKeys = listOf("future.thing"))),
            TODAY,
            labels::get,
        )

        assertThat(unknown.filtered(JournalFilter(search = "thing")).days).isEmpty()
    }

    @Test
    fun `every word of the search must match`() {
        assertThat(ids(JournalFilter(search = "sencha frisch"))).containsExactly("hisui")
        assertThat(ids(JournalFilter(search = "sencha mild"))).isEmpty()
    }

    @Test
    fun `the tea filter keeps one tea, also across its sessions`() {
        assertThat(ids(JournalFilter(teaKey = "t-hisui"))).containsExactly("hisui")
        assertThat(ids(JournalFilter(teaKey = "t-older"))).containsExactly("older")
    }

    @Test
    fun `a deleted tea is offered and filtered by its recorded name`() {
        val deleted = journalList(
            listOf(
                session("a", at(TODAY, 9), teaName = "Alter Sencha", teaId = null),
                session("b", at(TODAY, 8), teaName = "Alter Sencha", teaId = null),
                session("c", at(TODAY, 7), teaName = "Bancha"),
            ),
            TODAY,
        )

        assertThat(deleted.teaOptions().map { it.name }).containsExactly("Alter Sencha", "Bancha").inOrder()
        val key = deleted.teaOptions().first().key
        assertThat(deleted.filtered(JournalFilter(teaKey = key)).sessionCount).isEqualTo(2)
    }

    @Test
    fun `the tea options are the teas with finished sessions by name`() {
        val names = list.teaOptions().map { it.name }

        // The running session's tea has no finished session; the two Hisui sessions are two teas (two ids).
        assertThat(
            names,
        ).containsExactly("Bio Sencha Hisui", "Bio Sencha Hisui", "Gyokuro Asahi", "Kabusecha Überraschung")
            .inOrder()
    }

    @Test
    fun `each rating step keeps the sessions rated at least that`() {
        assertThat(ids(JournalFilter(minRating = 3.0))).containsExactly("hisui", "ueber", "yesterday")
        assertThat(ids(JournalFilter(minRating = 4.0))).containsExactly("hisui", "yesterday")
        assertThat(ids(JournalFilter(minRating = 4.5))).containsExactly("hisui")
    }

    @Test
    fun `an unrated session never passes a rating filter`() {
        assertThat(ids(JournalFilter(minRating = 3.0))).doesNotContain("older")
    }

    @Test
    fun `mit Notizen keeps the sessions with note text`() {
        assertThat(ids(JournalFilter(withNotes = true))).containsExactly("hisui", "yesterday")
    }

    @Test
    fun `a blank note counts as no note`() {
        val blank = journalList(listOf(session("s", at(TODAY, 9), notes = "  ")), TODAY)

        assertThat(blank.filtered(JournalFilter(withNotes = true)).days).isEmpty()
    }

    @Test
    fun `search and filters combine`() {
        assertThat(ids(JournalFilter(search = "hisui", minRating = 4.0))).containsExactly("hisui")
        assertThat(ids(JournalFilter(search = "hisui", withNotes = true, teaKey = "t-older"))).isEmpty()
        assertThat(ids(JournalFilter(minRating = 4.0, withNotes = true))).containsExactly("hisui", "yesterday")
    }

    @Test
    fun `the day groups are kept and a day without a match is dropped`() {
        val result = list.filtered(JournalFilter(search = "sencha"))

        assertThat(result.days.map { it.entries.map { e -> e.sessionId } })
            .containsExactly(listOf("hisui"), listOf("older"))
            .inOrder()
        assertThat(result.days.map { it.day }).containsExactly(
            JournalDay.Today,
            JournalDay.On(LocalDate.of(2026, 10, 12)),
        ).inOrder()
    }

    @Test
    fun `the pinned session hides while a search or a filter is active`() {
        assertThat(list.filtered(JournalFilter(search = "tanabata")).inProgress).isEmpty()
        assertThat(list.filtered(JournalFilter(search = "tanabata")).days).isEmpty()
        assertThat(list.filtered(JournalFilter(withNotes = true)).inProgress).isEmpty()
    }

    @Test
    fun `the sheet's reset clears the filters and keeps the search`() {
        val filter = JournalFilter(search = "sencha", teaKey = "t-hisui", minRating = 4.0, withNotes = true)

        assertThat(filter.withoutFilters()).isEqualTo(JournalFilter(search = "sencha"))
        assertThat(filter.hasFilters).isTrue()
        assertThat(filter.withoutFilters().hasFilters).isFalse()
    }

    @Test
    fun `a journal with only a running session keeps it pinned and has no finished days`() {
        val runningOnly = journalList(listOf(sessions.last()), TODAY, labels::get)

        val shown = runningOnly.filtered(JournalFilter())

        assertThat(shown.inProgress.map { it.sessionId }).containsExactly("running")
        assertThat(shown.days).isEmpty()
        assertThat(JournalFilter().isActive).isFalse()
    }
}
