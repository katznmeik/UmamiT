package com.umamit.core.model

/**
 * Kind of a step in a recipe or a session (05 §5). All kinds go through the
 * same timer lifecycle (04 §3); they differ in captions and in the journal.
 */
enum class StepKind {
    /** Rinse: the water is poured away (TMR-20, button *Weggegossen*). Does not count as a brewed step. */
    RINSE,

    /** Infusion (*Aufguss*). */
    INFUSION,

    /** Whisking matcha (TMR-19). */
    WHISK,
}
