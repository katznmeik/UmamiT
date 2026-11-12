package com.umamit.feature.journal

import com.umamit.core.common.WallClock
import com.umamit.core.data.JournalRepository
import com.umamit.core.data.JournalSession
import com.umamit.core.data.RecipeOffers
import com.umamit.core.data.SessionRepository
import com.umamit.core.data.SessionTasting
import com.umamit.core.data.TeaRepository
import com.umamit.core.timer.controller.SavedSheetRequests
import kotlinx.coroutines.Dispatchers
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

/**
 * What the journal tab and the „Gespeichert" sheet read (M1-16, D-061). The
 * app injects it; tests build it from the fakes of core:testing.
 *
 * @property teas the session's tea, for the sheet's *Rezept passt* (CAT-05, D-073).
 * @property recipeOffers the sheet's *Zeiten ins Rezept übernehmen* (PRF-07, D-112).
 * @property listContext where the journal list is built (JRN-09): `Dispatchers.Default` in the app, off the main
 *   thread. Tests pass `EmptyCoroutineContext`, so the list is built in the effect's own context: a Compose UI test
 *   runs effects on an unconfined test dispatcher, which would resume after a hop to `Default` on the worker thread
 *   and send the snapshot's apply notifications, and so touch the view, from there (#233).
 */
class JournalSources(
    val journal: JournalRepository,
    val sessions: SessionRepository,
    val savedSheets: SavedSheetRequests,
    val clock: WallClock,
    val teas: TeaRepository,
    val tasting: SessionTasting,
    val recipeOffers: RecipeOffers,
    internal val listContext: CoroutineContext,
) {
    @Inject
    constructor(
        journal: JournalRepository,
        sessions: SessionRepository,
        savedSheets: SavedSheetRequests,
        clock: WallClock,
        teas: TeaRepository,
        tasting: SessionTasting,
        recipeOffers: RecipeOffers,
    ) : this(journal, sessions, savedSheets, clock, teas, tasting, recipeOffers, Dispatchers.Default)

    /**
     * The journal's last read: a tab that comes back shows it in its first frame, so the
     * list keeps its height and its scroll position until the new read arrives.
     */
    internal var lastSessions: List<JournalSession>? = null

    /** The list built from [lastSessions], shown in the same first frame. */
    internal var lastList: JournalList? = null

    /** The phone's date now, for „Heute" and „Gestern". */
    fun today(): LocalDate = Instant.ofEpochMilli(clock.nowMs()).atZone(clock.zone()).toLocalDate()

    /** Milliseconds until the phone's next midnight, when „Heute" becomes „Gestern". */
    fun millisUntilTomorrow(): Long {
        val now = Instant.ofEpochMilli(clock.nowMs())
        val midnight = today().plusDays(1).atStartOfDay(clock.zone()).toInstant()
        return Duration.between(now, midnight).toMillis().coerceAtLeast(1)
    }
}
