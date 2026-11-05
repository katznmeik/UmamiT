package com.umamit.feature.journal.session

/**
 * What the session page (SCR-16) gets from the app.
 *
 * @property onBack leaves the page.
 * @property onOpenTea the tea's name: its detail (SCR-09) by its id.
 * @property onBrewAgain *So nochmal brühen* (BRW-06): the setup prefilled with the session, by its id. The app opens
 *   the setup above the shell, through the same setup → *Brühen starten* path as every brew, or the alerting timer
 *   during an alert (rule 8).
 */
class SessionPageHost(
    val onBack: () -> Unit,
    val onOpenTea: (teaId: String) -> Unit,
    val onBrewAgain: (sessionId: String) -> Unit,
)
