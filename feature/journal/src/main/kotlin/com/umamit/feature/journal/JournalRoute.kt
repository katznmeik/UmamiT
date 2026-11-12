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
import kotlinx.coroutines.withContext

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
    // A request seeds the first composition, so the first frame is already filtered; the effect handles later ones.
    val firstRequest = teaRequest?.takeIf { it.token != 0 }
    var filter by rememberSaveable(stateSaver = JournalFilter.Saver) {
        mutableStateOf(JournalFilter(teaKey = firstRequest?.teaKey))
    }
    var appliedToken by rememberSaveable { mutableIntStateOf(firstRequest?.token ?: 0) }
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
    // Grouping and the search words are built off the main thread ([JournalSources.listContext]); the last list stays
    // in the first frame of a tab that comes back, and while a new read is built, so nothing flashes (JRN-09).
    val list by produceState(sources.lastList, sessions, today, locale) {
        val read = sessions ?: return@produceState
        value = withContext(sources.listContext) { journalList(read, today, descriptorLabels::get) }
    }
    SideEffect { sources.lastList = list }
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
