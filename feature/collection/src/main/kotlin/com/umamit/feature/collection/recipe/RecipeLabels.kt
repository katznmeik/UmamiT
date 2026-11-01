package com.umamit.feature.collection.recipe

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.umamit.core.domain.brew.brewMl
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.StepKind
import com.umamit.core.model.Vessel
import com.umamit.core.timer.engine.BrewTimerEngine
import com.umamit.feature.collection.R

/* The words of Rezept bearbeiten (SCR-12): methods, step kinds, vessels and each step's Vorwarnung. */

@get:StringRes
internal val BrewMethod.label: Int
    get() = when (this) {
        BrewMethod.KYUSU -> R.string.recipe_method_kyusu
        BrewMethod.GONGFU -> R.string.recipe_method_gongfu
        BrewMethod.WESTERN -> R.string.recipe_method_western
        BrewMethod.MATCHA_USUCHA -> R.string.recipe_method_matcha_usucha
        BrewMethod.MATCHA_KOICHA -> R.string.recipe_method_matcha_koicha
        BrewMethod.COLD_BREW -> R.string.recipe_method_cold_brew
        BrewMethod.ICE_BREW -> R.string.recipe_method_ice_brew
        BrewMethod.CUSTOM -> R.string.recipe_method_custom
    }

/** *Aufguss*, *Spülen*, *Aufschlagen* (G3, D-100). */
@get:StringRes
internal val StepKind.label: Int
    get() = when (this) {
        StepKind.INFUSION -> R.string.recipe_step_kind_infusion
        StepKind.RINSE -> R.string.recipe_step_kind_rinse
        StepKind.WHISK -> R.string.recipe_step_kind_whisk
    }

/**
 * A vessel as the setup names it (WARE-03): its name alone when it already ends with the volume it brews („Kyusu
 * 120 ml"), else „Lieblingskanne · 450 ml"; an archived one with „(archiviert)" (D-100).
 */
@Composable
internal fun vesselLabel(vessel: Vessel): String {
    val ml = vessel.brewMl()
    val name = vessel.name.trim()
    val plain = if (Regex("""(^|\D)$ml[\s  ]*ml$""", RegexOption.IGNORE_CASE).containsMatchIn(name)) {
        name.replace(VOLUME_SPACE, "$1 ml")
    } else {
        stringResource(R.string.recipe_editor_vessel_option, name, ml)
    }
    return if (vessel.isArchived) stringResource(R.string.recipe_editor_vessel_archived, plain) else plain
}

/** The space between a vessel name's volume and its „ml". */
private val VOLUME_SPACE = Regex("""(\d)\s+ml$""", RegexOption.IGNORE_CASE)

/** The Vorwarnung choices of a step (*Automatisch*, *Aus*, 5–30 s) and of a recipe (*Wie in Einstellungen* …). */
internal val PreAlertChoices: List<Int?> = listOf(null, 0) + (5..30 step 5).toList()

/**
 * What a step's Vorwarnung selector shows (SCR-31's mapping, TMR-23, D-093 Q17): the choice with the lead the
 * timer will give the step.
 */
internal sealed interface StepPreAlert {
    /** *Automatisch*, with its lead: „Auto · 5 s", or „Auto · keine" for [leadSeconds] null. */
    data class Auto(val leadSeconds: Int?) : StepPreAlert

    data object Off : StepPreAlert

    /** A lead of [seconds] that applies as set. */
    data class Lead(val seconds: Int) : StepPreAlert

    /** A lead of [seconds] the step is too short for (E7): none. */
    data class TooShort(val seconds: Int) : StepPreAlert

    /** A lead of [seconds] on a long step, which never has one (TMR-18). */
    data class LongStep(val seconds: Int) : StepPreAlert
}

/**
 * What step [index]'s selector shows for [choice] (its own by default): the timer engine's rules with this recipe's
 * base B and method, so a duration, method or base change updates it.
 */
internal fun RecipeEditorUiState.Ready.stepPreAlert(
    index: Int,
    choice: Int? = form.steps[index].preAlertSeconds,
): StepPreAlert {
    val step = form.steps[index].planned(form.baseTempC).copy(preAlertSeconds = choice)
    val longStepsOnly = BrewTimerEngine.longStepsOnly(form.method)
    val lead = BrewTimerEngine.stepPreAlertLeadSeconds(step, preAlertBaseSeconds, longStepsOnly)
    return when (choice) {
        null -> StepPreAlert.Auto(lead)

        0 -> StepPreAlert.Off

        else -> when {
            lead != null -> StepPreAlert.Lead(choice)
            BrewTimerEngine.isLongStep(step, longStepsOnly) -> StepPreAlert.LongStep(choice)
            else -> StepPreAlert.TooShort(choice)
        }
    }
}

/** The selector's label: „Auto · 5 s", „Aus", „10 s", „10 s · zu kurz"; with [spelledOut], „Automatisch · 5 s". */
@Composable
internal fun stepPreAlertLabel(shown: StepPreAlert, spelledOut: Boolean = false): String = when (shown) {
    is StepPreAlert.Auto -> stringResource(
        if (spelledOut) R.string.recipe_step_pre_alert_auto_option else R.string.recipe_step_pre_alert_auto,
        shown.leadSeconds?.let { secondsLabel(it) } ?: stringResource(R.string.recipe_step_pre_alert_none),
    )

    StepPreAlert.Off -> stringResource(R.string.recipe_step_pre_alert_off)

    is StepPreAlert.Lead -> secondsLabel(shown.seconds)

    is StepPreAlert.TooShort ->
        stringResource(R.string.recipe_step_pre_alert_too_short, secondsLabel(shown.seconds))

    is StepPreAlert.LongStep ->
        stringResource(R.string.recipe_step_pre_alert_long_step, secondsLabel(shown.seconds))
}

/** The recipe's Vorwarnung: „Wie in Einstellungen · 10 s", „Aus", „20 s". */
@Composable
internal fun recipePreAlertLabel(seconds: Int?, globalSeconds: Int): String = when (seconds) {
    null -> stringResource(
        R.string.recipe_editor_pre_alert_settings,
        if (globalSeconds == 0) stringResource(R.string.recipe_step_pre_alert_off) else secondsLabel(globalSeconds),
    )

    0 -> stringResource(R.string.recipe_step_pre_alert_off)

    else -> secondsLabel(seconds)
}

@Composable
private fun secondsLabel(seconds: Int): String = stringResource(R.string.recipe_step_pre_alert_seconds, seconds)
