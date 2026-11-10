package com.umamit.feature.home

import com.umamit.core.model.TimerSlot

/** What a tap on Start does (SCR-02). */
class HomeActions(
    val openSettings: () -> Unit = {},
    /** *Aufguss starten*: the tea picker (SCR-03). */
    val startBrew: () -> Unit = {},
    /** *Aufguss fortsetzen*: the timer of the card's brew, in its slot (SCR-05, D-108). */
    val openTimer: (TimerSlot) -> Unit = {},
    /** *Schnelltimer* (SCR-07). */
    val quickTimer: () -> Unit = {},
    /** *Nochmal brühen* (BRW-11): a tap on a tea of *Zuletzt gebrüht* (D-110). */
    val brewAgain: (teaId: String) -> Unit = {},
    /** A favourite: its setup (SCR-04). */
    val openSetup: (teaId: String) -> Unit = {},
    /** *Zuletzt gebrüht › Alle anzeigen*: the journal. */
    val showJournal: () -> Unit = {},
    /** The empty state's *Katalog öffnen*. */
    val openCatalog: () -> Unit = {},
    /** An answer of „Welchen Timer beenden?" (TMR-21, D-108): that slot's timer ends, then the brew starts. */
    val endTimer: (TimerSlot) -> Unit = {},
    /** *Abbrechen* of the question. */
    val dismissQuestion: () -> Unit = {},
    /** The reliability banner's *Beheben* (TMR-31). */
    val fixReliability: () -> Unit = {},
    /** The low-stock card's *Nachbestellen*: INV-09's shop page in the browser (INV-10). */
    val reorder: (url: String) -> Unit = {},
    /** The low-stock card's *Ausblenden* (INV-10 AC2). */
    val hideLowStock: () -> Unit = {},
)
