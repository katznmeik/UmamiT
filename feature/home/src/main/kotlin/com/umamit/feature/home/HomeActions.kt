package com.umamit.feature.home

/** What a tap on Start does (SCR-02). */
class HomeActions(
    val openSettings: () -> Unit = {},
    /** *Aufguss starten*: the tea picker (SCR-03). */
    val startBrew: () -> Unit = {},
    /** *Aufguss fortsetzen*: the brew's timer (SCR-05). */
    val openTimer: () -> Unit = {},
    /** *Schnelltimer* (SCR-07). */
    val quickTimer: () -> Unit = {},
    /** *Nochmal brühen* on a tea (BRW-11). */
    val brewAgain: (teaId: String) -> Unit = {},
    /** A favourite: its setup (SCR-04). */
    val openSetup: (teaId: String) -> Unit = {},
    /** *Zuletzt gebrüht › Alle anzeigen*: the journal. */
    val showJournal: () -> Unit = {},
    /** The empty state's *Katalog öffnen*. */
    val openCatalog: () -> Unit = {},
    /** *Beenden & neu starten* (TMR-21). */
    val confirmRestart: () -> Unit = {},
    /** *Abbrechen* of the question. */
    val dismissQuestion: () -> Unit = {},
    /** The reliability banner's *Beheben* (TMR-31). */
    val fixReliability: () -> Unit = {},
    /** The low-stock card's *Nachbestellen*: INV-09's shop page in the browser (INV-10). */
    val reorder: (url: String) -> Unit = {},
    /** The low-stock card's *Ausblenden* (INV-10 AC2). */
    val hideLowStock: () -> Unit = {},
)
