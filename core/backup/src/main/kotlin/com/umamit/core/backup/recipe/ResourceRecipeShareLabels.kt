package com.umamit.core.backup.recipe

import android.content.res.Resources
import com.umamit.core.backup.R
import com.umamit.core.common.AppLanguage
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.StepKind
import com.umamit.core.model.VesselType
import java.util.Locale

/**
 * [RecipeShareLabels] from this module's `strings.xml` (CLAUDE.md rule 4), German or English with [resources], and
 * the numbers in [language], the app language when the labels are made (GEN-13, D-136 item 15).
 */
class ResourceRecipeShareLabels(private val resources: Resources, language: AppLanguage = AppLanguage.current()) :
    RecipeShareLabels {
    override val locale: Locale = language.locale

    override fun method(method: BrewMethod): String = resources.getString(
        when (method) {
            BrewMethod.KYUSU -> R.string.recipe_file_method_kyusu
            BrewMethod.GONGFU -> R.string.recipe_file_method_gongfu
            BrewMethod.WESTERN -> R.string.recipe_file_method_western
            BrewMethod.MATCHA_USUCHA -> R.string.recipe_file_method_matcha_usucha
            BrewMethod.MATCHA_KOICHA -> R.string.recipe_file_method_matcha_koicha
            BrewMethod.COLD_BREW -> R.string.recipe_file_method_cold_brew
            BrewMethod.ICE_BREW -> R.string.recipe_file_method_ice_brew
            BrewMethod.CUSTOM -> R.string.recipe_file_method_custom
        },
    )

    override fun stepKind(kind: StepKind): String = resources.getString(
        when (kind) {
            StepKind.RINSE -> R.string.recipe_file_step_kind_rinse
            StepKind.INFUSION -> R.string.recipe_file_step_kind_infusion
            StepKind.WHISK -> R.string.recipe_file_step_kind_whisk
        },
    )

    override fun vesselType(type: VesselType): String = resources.getString(
        when (type) {
            VesselType.KYUSU -> R.string.recipe_file_vessel_type_kyusu
            VesselType.HOUHIN -> R.string.recipe_file_vessel_type_houhin
            VesselType.SHIBORIDASHI -> R.string.recipe_file_vessel_type_shiboridashi
            VesselType.GAIWAN -> R.string.recipe_file_vessel_type_gaiwan
            VesselType.CLAY_TEAPOT -> R.string.recipe_file_vessel_type_clay_teapot
            VesselType.WESTERN_TEAPOT -> R.string.recipe_file_vessel_type_western_teapot
            VesselType.MUG_INFUSER -> R.string.recipe_file_vessel_type_mug_infuser
            VesselType.GLASS -> R.string.recipe_file_vessel_type_glass
            VesselType.CHAWAN -> R.string.recipe_file_vessel_type_chawan
            VesselType.COLD_BREW_BOTTLE -> R.string.recipe_file_vessel_type_cold_brew_bottle
            VesselType.OTHER -> R.string.recipe_file_vessel_type_other
        },
    )

    override fun template(part: RecipeSharePart): String = resources.getString(
        when (part) {
            RecipeSharePart.TITLE -> R.string.recipe_file_share_title
            RecipeSharePart.TITLE_WITH_SECONDARY -> R.string.recipe_file_share_title_secondary
            RecipeSharePart.SEPARATOR -> R.string.recipe_file_share_separator
            RecipeSharePart.AMOUNTS -> R.string.recipe_file_share_amounts
            RecipeSharePart.VESSEL -> R.string.recipe_file_share_vessel
            RecipeSharePart.GRAMS -> R.string.recipe_file_share_grams
            RecipeSharePart.MILLILITRES -> R.string.recipe_file_share_ml
            RecipeSharePart.CELSIUS -> R.string.recipe_file_share_celsius
            RecipeSharePart.STEP -> R.string.recipe_file_share_step
            RecipeSharePart.FOOTER -> R.string.recipe_file_share_footer
        },
    )
}
