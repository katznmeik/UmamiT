package com.umamit.core.model

/**
 * Tea category (05 §5), stored by name. `ROOIBOS` and `FRUIT` from v0.5.0 (D-108, #206): the shop's Rotbusch
 * and Früchtetee; a chai takes its base tea's category, else `OTHER`.
 */
enum class TeaCategory {
    GREEN,
    WHITE,
    YELLOW,
    OOLONG,
    BLACK,
    DARK,
    HERBAL,
    ROOIBOS,
    FRUIT,
    OTHER,
}
