package com.umamit.core.ui

import androidx.annotation.StringRes
import com.umamit.core.model.DescriptorGroup
import com.umamit.core.model.TasteDescriptor

/** German labels of the tasting vocabulary (08 §4, D-112); exhaustive, so a new entry cannot lack one. */
@StringRes
fun DescriptorGroup.labelRes(): Int = when (this) {
    DescriptorGroup.MARINE -> R.string.ui_descriptor_group_marine
    DescriptorGroup.VEGETAL -> R.string.ui_descriptor_group_vegetal
    DescriptorGroup.SWEET -> R.string.ui_descriptor_group_sweet
    DescriptorGroup.ROASTED_NUTTY -> R.string.ui_descriptor_group_roasted_nutty
    DescriptorGroup.FLORAL -> R.string.ui_descriptor_group_floral
    DescriptorGroup.FRUITY -> R.string.ui_descriptor_group_fruity
    DescriptorGroup.EARTHY_WOODY -> R.string.ui_descriptor_group_earthy_woody
    DescriptorGroup.SPICE_HERB -> R.string.ui_descriptor_group_spice_herb
    DescriptorGroup.MOUTHFEEL -> R.string.ui_descriptor_group_mouthfeel
    DescriptorGroup.OFF_NOTE -> R.string.ui_descriptor_group_off_note
}

@StringRes
@Suppress("CyclomaticComplexMethod", "LongMethod")
fun TasteDescriptor.labelRes(): Int = when (this) {
    TasteDescriptor.MARINE_ALGEN -> R.string.ui_descriptor_marine_algen
    TasteDescriptor.MARINE_NORI -> R.string.ui_descriptor_marine_nori
    TasteDescriptor.MARINE_KOMBU -> R.string.ui_descriptor_marine_kombu
    TasteDescriptor.MARINE_MEERESBRISE -> R.string.ui_descriptor_marine_meeresbrise
    TasteDescriptor.MARINE_DASHI -> R.string.ui_descriptor_marine_dashi
    TasteDescriptor.VEGETAL_SPINAT -> R.string.ui_descriptor_vegetal_spinat
    TasteDescriptor.VEGETAL_ZUCKERSCHOTE -> R.string.ui_descriptor_vegetal_zuckerschote
    TasteDescriptor.VEGETAL_EDAMAME -> R.string.ui_descriptor_vegetal_edamame
    TasteDescriptor.VEGETAL_SPARGEL -> R.string.ui_descriptor_vegetal_spargel
    TasteDescriptor.VEGETAL_GEDAEMPFTES_GEMUESE -> R.string.ui_descriptor_vegetal_gedaempftes_gemuese
    TasteDescriptor.VEGETAL_GEMAEHTES_GRAS -> R.string.ui_descriptor_vegetal_gemaehtes_gras
    TasteDescriptor.VEGETAL_HEU -> R.string.ui_descriptor_vegetal_heu
    TasteDescriptor.VEGETAL_ARTISCHOCKE -> R.string.ui_descriptor_vegetal_artischocke
    TasteDescriptor.SWEET_ZUCKERMAIS -> R.string.ui_descriptor_sweet_zuckermais
    TasteDescriptor.SWEET_ESSKASTANIE -> R.string.ui_descriptor_sweet_esskastanie
    TasteDescriptor.SWEET_HONIG -> R.string.ui_descriptor_sweet_honig
    TasteDescriptor.SWEET_KARAMELL -> R.string.ui_descriptor_sweet_karamell
    TasteDescriptor.SWEET_ROHRZUCKER -> R.string.ui_descriptor_sweet_rohrzucker
    TasteDescriptor.SWEET_SUESSKARTOFFEL -> R.string.ui_descriptor_sweet_suesskartoffel
    TasteDescriptor.SWEET_VANILLE -> R.string.ui_descriptor_sweet_vanille
    TasteDescriptor.ROASTED_NUTTY_GEROESTETER_REIS -> R.string.ui_descriptor_roasted_nutty_geroesteter_reis
    TasteDescriptor.ROASTED_NUTTY_GEROESTETE_NUESSE -> R.string.ui_descriptor_roasted_nutty_geroestete_nuesse
    TasteDescriptor.ROASTED_NUTTY_TOAST -> R.string.ui_descriptor_roasted_nutty_toast
    TasteDescriptor.ROASTED_NUTTY_KAKAO -> R.string.ui_descriptor_roasted_nutty_kakao
    TasteDescriptor.ROASTED_NUTTY_KAFFEE -> R.string.ui_descriptor_roasted_nutty_kaffee
    TasteDescriptor.ROASTED_NUTTY_RAUCH -> R.string.ui_descriptor_roasted_nutty_rauch
    TasteDescriptor.FLORAL_ORCHIDEE -> R.string.ui_descriptor_floral_orchidee
    TasteDescriptor.FLORAL_JASMIN -> R.string.ui_descriptor_floral_jasmin
    TasteDescriptor.FLORAL_OSMANTHUS -> R.string.ui_descriptor_floral_osmanthus
    TasteDescriptor.FLORAL_ROSE -> R.string.ui_descriptor_floral_rose
    TasteDescriptor.FLORAL_GEISSBLATT -> R.string.ui_descriptor_floral_geissblatt
    TasteDescriptor.FLORAL_KIRSCHBLUETE -> R.string.ui_descriptor_floral_kirschbluete
    TasteDescriptor.FRUITY_ZITRUS -> R.string.ui_descriptor_fruity_zitrus
    TasteDescriptor.FRUITY_YUZU -> R.string.ui_descriptor_fruity_yuzu
    TasteDescriptor.FRUITY_STEINOBST -> R.string.ui_descriptor_fruity_steinobst
    TasteDescriptor.FRUITY_MUSKATELLER -> R.string.ui_descriptor_fruity_muskateller
    TasteDescriptor.FRUITY_MELONE -> R.string.ui_descriptor_fruity_melone
    TasteDescriptor.FRUITY_TROCKENFRUECHTE -> R.string.ui_descriptor_fruity_trockenfruechte
    TasteDescriptor.FRUITY_BEEREN -> R.string.ui_descriptor_fruity_beeren
    TasteDescriptor.EARTHY_WOODY_WALDBODEN -> R.string.ui_descriptor_earthy_woody_waldboden
    TasteDescriptor.EARTHY_WOODY_PILZE -> R.string.ui_descriptor_earthy_woody_pilze
    TasteDescriptor.EARTHY_WOODY_HOLZ -> R.string.ui_descriptor_earthy_woody_holz
    TasteDescriptor.EARTHY_WOODY_LEDER -> R.string.ui_descriptor_earthy_woody_leder
    TasteDescriptor.EARTHY_WOODY_KAMPFER -> R.string.ui_descriptor_earthy_woody_kampfer
    TasteDescriptor.EARTHY_WOODY_MINERALISCH -> R.string.ui_descriptor_earthy_woody_mineralisch
    TasteDescriptor.SPICE_HERB_MINZE -> R.string.ui_descriptor_spice_herb_minze
    TasteDescriptor.SPICE_HERB_KIEFER -> R.string.ui_descriptor_spice_herb_kiefer
    TasteDescriptor.SPICE_HERB_ZIMT -> R.string.ui_descriptor_spice_herb_zimt
    TasteDescriptor.SPICE_HERB_KRAEUTER -> R.string.ui_descriptor_spice_herb_kraeuter
    TasteDescriptor.MOUTHFEEL_CREMIG -> R.string.ui_descriptor_mouthfeel_cremig
    TasteDescriptor.MOUTHFEEL_SEIDIG -> R.string.ui_descriptor_mouthfeel_seidig
    TasteDescriptor.MOUTHFEEL_VOLLMUNDIG -> R.string.ui_descriptor_mouthfeel_vollmundig
    TasteDescriptor.MOUTHFEEL_FRISCH_HERB -> R.string.ui_descriptor_mouthfeel_frisch_herb
    TasteDescriptor.MOUTHFEEL_TROCKNEND -> R.string.ui_descriptor_mouthfeel_trocknend
    TasteDescriptor.MOUTHFEEL_BELEGEND -> R.string.ui_descriptor_mouthfeel_belegend
    TasteDescriptor.MOUTHFEEL_KLARER_ABGANG -> R.string.ui_descriptor_mouthfeel_klarer_abgang
    TasteDescriptor.MOUTHFEEL_ANHALTENDE_SUESSE -> R.string.ui_descriptor_mouthfeel_anhaltende_suesse
    TasteDescriptor.OFF_NOTE_ABGESTANDEN -> R.string.ui_descriptor_off_note_abgestanden
    TasteDescriptor.OFF_NOTE_FLACH -> R.string.ui_descriptor_off_note_flach
    TasteDescriptor.OFF_NOTE_ZU_BITTER -> R.string.ui_descriptor_off_note_zu_bitter
    TasteDescriptor.OFF_NOTE_METALLISCH -> R.string.ui_descriptor_off_note_metallisch
    TasteDescriptor.OFF_NOTE_MUFFIG -> R.string.ui_descriptor_off_note_muffig
    TasteDescriptor.OFF_NOTE_SAUER -> R.string.ui_descriptor_off_note_sauer
}
