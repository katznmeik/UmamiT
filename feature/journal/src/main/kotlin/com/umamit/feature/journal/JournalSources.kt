package com.umamit.feature.journal

import com.umamit.core.common.WallClock
import com.umamit.core.data.JournalRepository
import com.umamit.core.data.JournalSession
import com.umamit.core.data.SessionRepository
import com.umamit.core.timer.controller.SavedSheetRequests
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/**
 * What the journal tab and the „Gespeichert" sheet read (M1-16, D-061). The
 * app injects it; tests build it from the fakes of core:testing.
 */
class JournalSources @Inject constructor(
    val journal: JournalRepository,
    val sessions: SessionRepository,
    val savedSheets: SavedSheetRequests,
    val clock: WallClock,
) {
    /**
     * The journal's last read: a tab that comes back shows it in its first frame, so the
     * list keeps its height and its scroll position until the new read arrives.
     */
    internal var lastSessions: List<JournalSession>? = null

    /** The phone's date now, for „Heute" and „Gestern". */
    fun today(): LocalDate = Instant.ofEpochMilli(clock.nowMs()).atZone(clock.zone()).toLocalDate()

    /** Milliseconds until the phone's next midnight, when „Heute" becomes „Gestern". */
    fun millisUntilTomorrow(): Long {
        val now = Instant.ofEpochMilli(clock.nowMs())
        val midnight = today().plusDays(1).atStartOfDay(clock.zone()).toInstant()
        return Duration.between(now, midnight).toMillis().coerceAtLeast(1)
    }
}
