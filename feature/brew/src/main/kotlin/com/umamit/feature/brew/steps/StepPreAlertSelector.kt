package com.umamit.feature.brew.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.timer.engine.BrewTimerEngine
import com.umamit.core.timer.engine.PreAlertChoice
import com.umamit.feature.brew.R

/*
 * Each step's *Vorwarnung* selector in the Schritte-Sheet (SCR-31, TMR-23, D-093 point 3 and Q17): *Automatisch*
 * with the lead it will use, *Aus* or 5–30 s, under every pending step and the running one.
 */

/** The pre-alert options of each step (SCR-31, D-093): *Automatisch*, *Aus*, 5–30 s in 5 s steps. */
val PreAlertOptions: List<PreAlertChoice> =
    listOf(PreAlertChoice.Inherit, PreAlertChoice.Off) + (5..30 step 5).map { PreAlertChoice.Seconds(it) }

/**
 * What a step's *Vorwarnung* selector shows (SCR-31, TMR-23, D-093 Q17): the
 * choice with the lead the session will give the step, from the engine's rules.
 */
sealed interface StepPreAlert {

    /** *Automatisch*, with the lead it will use: „Auto · 5 s", or „Auto · keine" for [leadSeconds] null. */
    data class Auto(val leadSeconds: Int?) : StepPreAlert

    /** *Aus*. */
    data object Off : StepPreAlert

    /** An explicit lead of [seconds] that applies as set. */
    data class Lead(val seconds: Int) : StepPreAlert

    /** An explicit [seconds] the step is too short for (d < N + 5 s, or a rinse under 15 s): „zu kurz", none. */
    data class TooShort(val seconds: Int) : StepPreAlert

    /** An explicit [seconds] on a long step, which never has a pre-alert (TMR-18): none. */
    data class LongStep(val seconds: Int) : StepPreAlert
}

/**
 * What step [index]'s selector shows for [choice] (its own by default), with the lead the session will give the
 * step as planned now: the engine's rules with the session's B (TMR-23, E7, TMR-18), so a duration edit updates it.
 */
fun StepsSheetState.preAlertShown(index: Int, choice: PreAlertChoice = steps[index].preAlert): StepPreAlert {
    val step = steps[index].planned.copy(preAlertSeconds = choice.explicitSeconds)
    val lead = BrewTimerEngine.stepPreAlertLeadSeconds(step, preAlertBaseSeconds, longStepsOnly)
    return when (choice) {
        PreAlertChoice.Inherit -> StepPreAlert.Auto(lead)

        PreAlertChoice.Off -> StepPreAlert.Off

        is PreAlertChoice.Seconds -> when {
            lead != null -> StepPreAlert.Lead(choice.seconds)
            BrewTimerEngine.isLongStep(step, longStepsOnly) -> StepPreAlert.LongStep(choice.seconds)
            else -> StepPreAlert.TooShort(choice.seconds)
        }
    }
}

/**
 * Step [index]'s *Vorwarnung* selector (SCR-31, TMR-23, D-093): *Automatisch* with the lead it will use
 * („Auto · 5 s", „Auto · keine"), *Aus* or 5–30 s, „zu kurz" when the step is too short for the value.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StepPreAlertRow(state: StepsSheetState, index: Int) {
    var expanded by remember { mutableStateOf(false) }
    val selected = state.steps[index].preAlert
    val shown = state.preAlertShown(index)
    val description = stringResource(R.string.brew_steps_pre_alert_description, index + 1, preAlertSpoken(shown))
    // Under its step's label: a read-only (running) step's label follows the status icon column.
    val indent = if (state.isEditable(index)) 0.dp else StatusIconSize + UmamiSpacing.Small
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(start = indent, bottom = UmamiSpacing.XSmall),
        horizontalArrangement = Arrangement.SpaceBetween,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.brew_steps_pre_alert),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = UmamiSpacing.XSmall),
        )
        Box {
            TextButton(
                onClick = { expanded = true },
                // One TalkBack element with the step, the spoken value, a button role and a click action.
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = description
                    role = Role.Button
                    onClick {
                        expanded = true
                        true
                    }
                },
            ) {
                Text(preAlertLabel(shown), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(UmamiSpacing.XXSmall))
                Icon(
                    painterResource(R.drawable.ic_expand_more),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                PreAlertOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(preAlertOptionLabel(state.preAlertShown(index, option))) },
                        onClick = {
                            state.setPreAlert(index, option)
                            expanded = false
                        },
                        leadingIcon = {
                            if (option == selected) {
                                Icon(painterResource(UmamiIcons.Check), contentDescription = null)
                            } else {
                                Spacer(Modifier.size(24.dp))
                            }
                        },
                    )
                }
            }
        }
    }
}

/** The selector's label: „Auto · 5 s", „Auto · keine", „Aus", „10 s", „10 s · zu kurz" or „10 s · keine". */
@Composable
private fun preAlertLabel(shown: StepPreAlert): String = when (shown) {
    is StepPreAlert.Auto -> stringResource(R.string.brew_steps_pre_alert_auto, leadLabel(shown.leadSeconds))
    StepPreAlert.Off -> stringResource(R.string.brew_steps_pre_alert_off)
    is StepPreAlert.Lead -> secondsLabel(shown.seconds)
    is StepPreAlert.TooShort -> stringResource(R.string.brew_steps_pre_alert_too_short, secondsLabel(shown.seconds))
    is StepPreAlert.LongStep -> stringResource(R.string.brew_steps_pre_alert_long_step, secondsLabel(shown.seconds))
}

/** A menu option's label: as [preAlertLabel], with *Automatisch* spelt out („Automatisch · 5 s"). */
@Composable
private fun preAlertOptionLabel(shown: StepPreAlert): String = if (shown is StepPreAlert.Auto) {
    stringResource(R.string.brew_steps_pre_alert_auto_option, leadLabel(shown.leadSeconds))
} else {
    preAlertLabel(shown)
}

@Composable
private fun leadLabel(leadSeconds: Int?): String =
    leadSeconds?.let { secondsLabel(it) } ?: stringResource(R.string.brew_steps_pre_alert_none)

@Composable
private fun secondsLabel(seconds: Int): String = stringResource(R.string.brew_steps_pre_alert_seconds, seconds)

/** What TalkBack says for the selector's value, as [preAlertLabel] in words („Automatisch, 5 Sekunden vorher"). */
@Composable
private fun preAlertSpoken(shown: StepPreAlert): String = when (shown) {
    is StepPreAlert.Auto -> stringResource(
        R.string.brew_steps_pre_alert_auto_description,
        shown.leadSeconds?.let { spokenLead(it) } ?: stringResource(R.string.brew_steps_pre_alert_none),
    )

    StepPreAlert.Off -> stringResource(R.string.brew_steps_pre_alert_off)

    is StepPreAlert.Lead -> spokenLead(shown.seconds)

    is StepPreAlert.TooShort ->
        stringResource(R.string.brew_steps_pre_alert_too_short_description, spokenLead(shown.seconds))

    is StepPreAlert.LongStep ->
        stringResource(R.string.brew_steps_pre_alert_long_step_description, spokenLead(shown.seconds))
}

@Composable
private fun spokenLead(seconds: Int): String =
    pluralStringResource(R.plurals.brew_steps_pre_alert_seconds_description, seconds, seconds)
