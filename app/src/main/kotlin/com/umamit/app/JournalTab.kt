package com.umamit.app

import androidx.compose.runtime.Composable
import com.umamit.feature.journal.session.SessionPageHost
import com.umamit.feature.journal.session.SessionPageRoute

/**
 * The screens of the Journal stack beyond the list: a past session's page (SCR-16, D-100). The app uses the real
 * route; tests replace it.
 */
internal class JournalStackScreens(val session: @Composable (SessionPageHost) -> Unit = { SessionPageRoute(it) })

/**
 * What the Journal tab's screens get from the shell: the [screens] and [openSetupFromSession], *So nochmal brühen*
 * (BRW-06): the setup prefilled with a past session above the shell, or the alerting timer during an alert (rule 8).
 */
internal class JournalWiring(val screens: JournalStackScreens, val openSetupFromSession: (sessionId: String) -> Unit)
