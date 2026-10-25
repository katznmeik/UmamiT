package com.umamit.app

import com.umamit.core.data.TeaRepository
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeSessions
import com.umamit.core.timer.controller.SavedSheetRequests
import com.umamit.feature.journal.JournalSources

/** An empty journal and no saved sheet, on the fakes of core:testing; [teas] are the sessions' teas. */
fun testJournalSources(
    sessions: FakeSessions = FakeSessions(),
    teas: TeaRepository = FakeCollection().teas,
    requests: SavedSheetRequests = SavedSheetRequests(FakeClock()),
): JournalSources = JournalSources(sessions.journal, sessions.sessions, requests, FakeClock(), teas)
