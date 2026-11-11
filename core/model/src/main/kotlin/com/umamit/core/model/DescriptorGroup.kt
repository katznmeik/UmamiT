package com.umamit.core.model

/** The ten groups of the tasting vocabulary, in the order of 08 §4 (D-112). [key] is the stored prefix. */
enum class DescriptorGroup(val key: String) {
    MARINE("marine"),
    VEGETAL("vegetal"),
    SWEET("sweet"),
    ROASTED_NUTTY("roasted_nutty"),
    FLORAL("floral"),
    FRUITY("fruity"),
    EARTHY_WOODY("earthy_woody"),
    SPICE_HERB("spice_herb"),
    MOUTHFEEL("mouthfeel"),
    OFF_NOTE("off_note"),
}
