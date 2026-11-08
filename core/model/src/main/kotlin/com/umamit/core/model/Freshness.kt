package com.umamit.core.model

/** A tea's freshness label (INV-06, D-104): *Frisch*, *Bald genießen* or *Über dem Höhepunkt*. */
enum class Freshness {
    /** *Frisch*: the first two thirds of the style's window after opening. */
    FRESH,

    /** *Bald genießen*: the last third of the window. */
    DRINK_SOON,

    /** *Über dem Höhepunkt*: past the window, or past the best-before date. */
    PAST_PEAK,
}
