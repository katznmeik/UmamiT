package com.umamit.core.model

/**
 * The built-in tasting vocabulary (08 §4, D-112). The stored [key] is `<group>.<slug>` and never
 * changes, even when a German label is reworded. Declaration order is the display order. There are
 * no custom descriptors. Labels live in `core:ui` (strings.xml).
 */
enum class TasteDescriptor(val group: DescriptorGroup, slug: String) {
    MARINE_ALGEN(DescriptorGroup.MARINE, "algen"),
    MARINE_NORI(DescriptorGroup.MARINE, "nori"),
    MARINE_KOMBU(DescriptorGroup.MARINE, "kombu"),
    MARINE_MEERESBRISE(DescriptorGroup.MARINE, "meeresbrise"),
    MARINE_DASHI(DescriptorGroup.MARINE, "dashi"),
    VEGETAL_SPINAT(DescriptorGroup.VEGETAL, "spinat"),
    VEGETAL_ZUCKERSCHOTE(DescriptorGroup.VEGETAL, "zuckerschote"),
    VEGETAL_EDAMAME(DescriptorGroup.VEGETAL, "edamame"),
    VEGETAL_SPARGEL(DescriptorGroup.VEGETAL, "spargel"),
    VEGETAL_GEDAEMPFTES_GEMUESE(DescriptorGroup.VEGETAL, "gedaempftes_gemuese"),
    VEGETAL_GEMAEHTES_GRAS(DescriptorGroup.VEGETAL, "gemaehtes_gras"),
    VEGETAL_HEU(DescriptorGroup.VEGETAL, "heu"),
    VEGETAL_ARTISCHOCKE(DescriptorGroup.VEGETAL, "artischocke"),
    SWEET_ZUCKERMAIS(DescriptorGroup.SWEET, "zuckermais"),
    SWEET_ESSKASTANIE(DescriptorGroup.SWEET, "esskastanie"),
    SWEET_HONIG(DescriptorGroup.SWEET, "honig"),
    SWEET_KARAMELL(DescriptorGroup.SWEET, "karamell"),
    SWEET_ROHRZUCKER(DescriptorGroup.SWEET, "rohrzucker"),
    SWEET_SUESSKARTOFFEL(DescriptorGroup.SWEET, "suesskartoffel"),
    SWEET_VANILLE(DescriptorGroup.SWEET, "vanille"),
    ROASTED_NUTTY_GEROESTETER_REIS(DescriptorGroup.ROASTED_NUTTY, "geroesteter_reis"),
    ROASTED_NUTTY_GEROESTETE_NUESSE(DescriptorGroup.ROASTED_NUTTY, "geroestete_nuesse"),
    ROASTED_NUTTY_TOAST(DescriptorGroup.ROASTED_NUTTY, "toast"),
    ROASTED_NUTTY_KAKAO(DescriptorGroup.ROASTED_NUTTY, "kakao"),
    ROASTED_NUTTY_KAFFEE(DescriptorGroup.ROASTED_NUTTY, "kaffee"),
    ROASTED_NUTTY_RAUCH(DescriptorGroup.ROASTED_NUTTY, "rauch"),
    FLORAL_ORCHIDEE(DescriptorGroup.FLORAL, "orchidee"),
    FLORAL_JASMIN(DescriptorGroup.FLORAL, "jasmin"),
    FLORAL_OSMANTHUS(DescriptorGroup.FLORAL, "osmanthus"),
    FLORAL_ROSE(DescriptorGroup.FLORAL, "rose"),
    FLORAL_GEISSBLATT(DescriptorGroup.FLORAL, "geissblatt"),
    FLORAL_KIRSCHBLUETE(DescriptorGroup.FLORAL, "kirschbluete"),
    FRUITY_ZITRUS(DescriptorGroup.FRUITY, "zitrus"),
    FRUITY_YUZU(DescriptorGroup.FRUITY, "yuzu"),
    FRUITY_STEINOBST(DescriptorGroup.FRUITY, "steinobst"),
    FRUITY_MUSKATELLER(DescriptorGroup.FRUITY, "muskateller"),
    FRUITY_MELONE(DescriptorGroup.FRUITY, "melone"),
    FRUITY_TROCKENFRUECHTE(DescriptorGroup.FRUITY, "trockenfruechte"),
    FRUITY_BEEREN(DescriptorGroup.FRUITY, "beeren"),
    EARTHY_WOODY_WALDBODEN(DescriptorGroup.EARTHY_WOODY, "waldboden"),
    EARTHY_WOODY_PILZE(DescriptorGroup.EARTHY_WOODY, "pilze"),
    EARTHY_WOODY_HOLZ(DescriptorGroup.EARTHY_WOODY, "holz"),
    EARTHY_WOODY_LEDER(DescriptorGroup.EARTHY_WOODY, "leder"),
    EARTHY_WOODY_KAMPFER(DescriptorGroup.EARTHY_WOODY, "kampfer"),
    EARTHY_WOODY_MINERALISCH(DescriptorGroup.EARTHY_WOODY, "mineralisch"),
    SPICE_HERB_MINZE(DescriptorGroup.SPICE_HERB, "minze"),
    SPICE_HERB_KIEFER(DescriptorGroup.SPICE_HERB, "kiefer"),
    SPICE_HERB_ZIMT(DescriptorGroup.SPICE_HERB, "zimt"),
    SPICE_HERB_KRAEUTER(DescriptorGroup.SPICE_HERB, "kraeuter"),
    MOUTHFEEL_CREMIG(DescriptorGroup.MOUTHFEEL, "cremig"),
    MOUTHFEEL_SEIDIG(DescriptorGroup.MOUTHFEEL, "seidig"),
    MOUTHFEEL_VOLLMUNDIG(DescriptorGroup.MOUTHFEEL, "vollmundig"),
    MOUTHFEEL_FRISCH_HERB(DescriptorGroup.MOUTHFEEL, "frisch_herb"),
    MOUTHFEEL_TROCKNEND(DescriptorGroup.MOUTHFEEL, "trocknend"),
    MOUTHFEEL_BELEGEND(DescriptorGroup.MOUTHFEEL, "belegend"),
    MOUTHFEEL_KLARER_ABGANG(DescriptorGroup.MOUTHFEEL, "klarer_abgang"),
    MOUTHFEEL_ANHALTENDE_SUESSE(DescriptorGroup.MOUTHFEEL, "anhaltende_suesse"),
    OFF_NOTE_ABGESTANDEN(DescriptorGroup.OFF_NOTE, "abgestanden"),
    OFF_NOTE_FLACH(DescriptorGroup.OFF_NOTE, "flach"),
    OFF_NOTE_ZU_BITTER(DescriptorGroup.OFF_NOTE, "zu_bitter"),
    OFF_NOTE_METALLISCH(DescriptorGroup.OFF_NOTE, "metallisch"),
    OFF_NOTE_MUFFIG(DescriptorGroup.OFF_NOTE, "muffig"),
    OFF_NOTE_SAUER(DescriptorGroup.OFF_NOTE, "sauer"),
    ;

    /** The stable stored key, `<group>.<slug>`. */
    val key: String = "${group.key}.$slug"

    companion object {
        private val byKey = entries.associateBy { it.key }

        /** The descriptor for a stored key; an unknown key yields null (kept in the database, not shown). */
        fun fromKey(key: String): TasteDescriptor? = byKey[key]

        /** The descriptors of [group] in display order. */
        fun inGroup(group: DescriptorGroup): List<TasteDescriptor> = entries.filter { it.group == group }
    }
}
