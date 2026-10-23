package com.umamit.app

import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeSessions
import com.umamit.core.timer.controller.SavedSheetRequests
import com.umamit.feature.journal.JournalSources

/** An empty journal and no saved sheet, on the fakes of core:testing. */
fun testJournalSources(sessions: FakeSessions = FakeSessions()): JournalSources =
    JournalSources(sessions.journal, sessions.sessions, SavedSheetRequests(FakeClock()), FakeClock())
