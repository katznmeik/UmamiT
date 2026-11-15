package com.umamit.feature.journal

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Hold, then show fresh (03 §3.6, A-09): which list the Journal shows, and when it cross-fades. */
class ShownJournalTest {
    private val old = JournalList.EMPTY
    private val fresh = JournalList(emptyList(), listOf(JournalDayGroup(JournalDay.Today, emptyList())))

    @Test
    fun `an equal list is never faded`() {
        val shown = ShownJournal(old)
        assertThat(shown.next(old.copy(), holding = false)).isSameInstanceAs(shown)
    }

    @Test
    fun `a changed list within the hold takes the place in place`() {
        assertThat(ShownJournal(old).next(fresh, holding = true)).isEqualTo(ShownJournal(fresh, changes = 0))
    }

    @Test
    fun `a changed list after the hold cross-fades once`() {
        assertThat(ShownJournal(old).next(fresh, holding = false)).isEqualTo(ShownJournal(fresh, changes = 1))
    }

    @Test
    fun `the first list after nothing shows at once`() {
        assertThat(ShownJournal(null).next(fresh, holding = false)).isEqualTo(ShownJournal(fresh, changes = 0))
    }
}
