package com.umamit.core.model

/** Type of a stock event (05 §3.10). */
enum class InventoryEventType {
    INITIAL,
    PURCHASE,
    BREW,
    ADJUSTMENT,
}
