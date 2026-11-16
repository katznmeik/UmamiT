package com.umamit.core.model

/** Type of a stock event (05 §3.10). */
enum class InventoryEventType {
    INITIAL,
    PURCHASE,
    BREW,
    ADJUSTMENT,
    ;

    /** A package: the first stock or a purchase (an INITIAL or a PURCHASE event), the events that carry a price. */
    val isPackage: Boolean get() = this == INITIAL || this == PURCHASE
}
