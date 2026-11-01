package com.umamit.feature.journal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

/**
 * The journal tab: the sessions as the repository writes them, so a finished
 * brew appears at once (JRN-01). „Heute" moves on at midnight and on resume.
 * A past entry's tap calls [onOpenSession] with its id (SCR-16, D-100).
 */
@Composable
fun JournalRoute(
    sources: JournalSources,
    onOpenSettings: () -> Unit,
    onOpenTimer: () -> Unit,
    modifier: Modifier = Modifier,
    scrollToTopRequest: Int = 0,
    onOpenSession: (sessionId: String) -> Unit = {},
) {
    val sessionsFlow = remember(sources) { sources.journal.observeSessions() }
    val sessions by sessionsFlow.collectAsStateWithLifecycle(initialValue = sources.lastSessions)
    SideEffect { sources.lastSessions = sessions }
    var today by remember(sources) { mutableStateOf(sources.today()) }
    LifecycleResumeEffect(sources) {
        today = sources.today()
        onPauseOrDispose {}
    }
    LaunchedEffect(sources, today) {
        delay(sources.millisUntilTomorrow())
        today = sources.today()
    }
    val list = remember(sessions, today) { sessions?.let { journalList(it, today) } }
    JournalScreen(
        list = list,
        onOpenSettings = onOpenSettings,
        onOpenTimer = onOpenTimer,
        modifier = modifier,
        scrollToTopRequest = scrollToTopRequest,
        onOpenSession = onOpenSession,
    )
}
