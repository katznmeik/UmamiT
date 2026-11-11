package com.umamit.feature.journal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umamit.core.model.TasteDescriptor
import com.umamit.core.ui.labelRes
import kotlinx.coroutines.delay

/**
 * The journal tab: the sessions as the repository writes them, so a finished
 * brew appears at once (JRN-01). „Heute" moves on at midnight and on resume.
 * A past entry's tap calls [onOpenSession] with its id (SCR-16, D-100).
 *
 * The search and filters (JRN-09) live here: they survive a tab switch and a visit to a session page, and not an
 * app restart. [initialTeaKey], a tea's id, opens the tab with *Tee* set to it (the tea detail's *Alle anzeigen*,
 * TEA-13); it is applied once per value. [alerting]: an alert plays, so the filter sheet gives way (rule 8).
 */
@Composable
fun JournalRoute(
    sources: JournalSources,
    onOpenSettings: () -> Unit,
    onOpenTimer: () -> Unit,
    modifier: Modifier = Modifier,
    scrollToTopRequest: Int = 0,
    onOpenSession: (sessionId: String) -> Unit = {},
    initialTeaKey: String? = null,
    alerting: Boolean = false,
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
    var filter by rememberSaveable(stateSaver = JournalFilter.Saver) { mutableStateOf(JournalFilter()) }
    var appliedTeaKey by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(initialTeaKey) {
        if (initialTeaKey != null && initialTeaKey != appliedTeaKey) {
            appliedTeaKey = initialTeaKey
            filter = filter.copy(teaKey = initialTeaKey)
        }
    }
    val descriptorLabels = TasteDescriptor.entries.associate { it.key to stringResource(it.labelRes()) }
    val list = remember(sessions, today, descriptorLabels.values.toList()) {
        sessions?.let { journalList(it, today, descriptorLabels::get) }
    }
    JournalScreen(
        list = list,
        onOpenSettings = onOpenSettings,
        onOpenTimer = onOpenTimer,
        modifier = modifier,
        scrollToTopRequest = scrollToTopRequest,
        onOpenSession = onOpenSession,
        filter = filter,
        onFilterChange = { filter = it },
        alerting = alerting,
    )
}
