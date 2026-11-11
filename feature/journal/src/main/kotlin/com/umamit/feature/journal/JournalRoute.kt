package com.umamit.feature.journal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umamit.core.model.TasteDescriptor
import com.umamit.core.ui.labelRes
import kotlinx.coroutines.delay

/**
 * A request to show the journal's list for one tea (TEA-13's *Alle anzeigen*, JRN-09): [teaKey] is the tea's id (as
 * in [JournalEntry.teaKey]). Each request carries a new, increasing [token] (from 1), so asking again for the same tea
 * after its chip was cleared is a new request and applies again.
 */
data class JournalTeaRequest(val teaKey: String, val token: Int)

/**
 * The journal tab: the sessions as the repository writes them, so a finished
 * brew appears at once (JRN-01). „Heute" moves on at midnight and on resume.
 * A past entry's tap calls [onOpenSession] with its id (SCR-16, D-100).
 *
 * The search and filters (JRN-09) live here: they survive a tab switch and a visit to a session page, and not an
 * app restart. [teaRequest] shows the list with *Tee* set to a tea and the search and every other filter cleared (the
 * tea detail's *Alle anzeigen*, TEA-13); it is applied once per request ([JournalTeaRequest.token]), so the user's
 * later changes to the filter stand. [alerting]: an alert plays, so the filter sheet gives way (rule 8).
 */
@Composable
fun JournalRoute(
    sources: JournalSources,
    onOpenSettings: () -> Unit,
    onOpenTimer: () -> Unit,
    modifier: Modifier = Modifier,
    scrollToTopRequest: Int = 0,
    onOpenSession: (sessionId: String) -> Unit = {},
    teaRequest: JournalTeaRequest? = null,
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
    var appliedToken by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(teaRequest) {
        if (teaRequest != null && teaRequest.token != appliedToken) {
            appliedToken = teaRequest.token
            filter = JournalFilter(teaKey = teaRequest.teaKey)
        }
    }
    // Keyed by the locale, not by the label list: the labels change only with it, and a list key is rebuilt per frame.
    val locale = LocalConfiguration.current.locales.toLanguageTags()
    val resources = LocalResources.current
    val descriptorLabels = remember(locale) {
        TasteDescriptor.entries.associate { it.key to resources.getString(it.labelRes()) }
    }
    val list = remember(sessions, today, locale) {
        sessions?.let { journalList(it, today, descriptorLabels::get) }
    }
    // A tea without finished sessions is no filter option, so its name is looked up (a deleted tea carries it).
    val teaKey = filter.teaKey
    val teaNameFallback by produceState<String?>(null, sources, teaKey) {
        value = when {
            teaKey == null -> null
            teaKey.startsWith(DELETED_TEA_PREFIX) -> teaKey.removePrefix(DELETED_TEA_PREFIX)
            else -> sources.teas.getTea(teaKey)?.name
        }
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
        teaNameFallback = teaNameFallback,
    )
}
