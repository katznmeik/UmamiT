package com.umamit.feature.teaware

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.model.VesselMaterial
import com.umamit.core.model.VesselType

/** The type's line icon (03 §3.4; placeholders until the PO's icons exist). */
@DrawableRes
fun VesselType.icon(): Int = when (this) {
    VesselType.KYUSU -> UmamiIcons.Kyusu
    VesselType.HOUHIN, VesselType.SHIBORIDASHI -> UmamiIcons.Houhin
    VesselType.GAIWAN -> UmamiIcons.Gaiwan
    VesselType.CLAY_TEAPOT, VesselType.WESTERN_TEAPOT -> UmamiIcons.Teapot
    VesselType.MUG_INFUSER, VesselType.GLASS, VesselType.OTHER -> UmamiIcons.Cup
    VesselType.CHAWAN -> UmamiIcons.Chawan
    VesselType.COLD_BREW_BOTTLE -> UmamiIcons.ColdBrewBottle
}

/** The type's German name. */
@StringRes
fun VesselType.label(): Int = when (this) {
    VesselType.KYUSU -> R.string.vessel_type_kyusu
    VesselType.HOUHIN -> R.string.vessel_type_houhin
    VesselType.SHIBORIDASHI -> R.string.vessel_type_shiboridashi
    VesselType.GAIWAN -> R.string.vessel_type_gaiwan
    VesselType.CLAY_TEAPOT -> R.string.vessel_type_clay_teapot
    VesselType.WESTERN_TEAPOT -> R.string.vessel_type_western_teapot
    VesselType.MUG_INFUSER -> R.string.vessel_type_mug_infuser
    VesselType.GLASS -> R.string.vessel_type_glass
    VesselType.CHAWAN -> R.string.vessel_type_chawan
    VesselType.COLD_BREW_BOTTLE -> R.string.vessel_type_cold_brew_bottle
    VesselType.OTHER -> R.string.vessel_type_other
}

/** The material's German name. */
@StringRes
fun VesselMaterial.label(): Int = when (this) {
    VesselMaterial.UNGLAZED_CLAY -> R.string.vessel_material_unglazed_clay
    VesselMaterial.GLAZED_CERAMIC -> R.string.vessel_material_glazed_ceramic
    VesselMaterial.PORCELAIN -> R.string.vessel_material_porcelain
    VesselMaterial.GLASS -> R.string.vessel_material_glass
    VesselMaterial.CAST_IRON -> R.string.vessel_material_cast_iron
    VesselMaterial.STAINLESS_STEEL -> R.string.vessel_material_stainless_steel
    VesselMaterial.OTHER -> R.string.vessel_material_other
}
