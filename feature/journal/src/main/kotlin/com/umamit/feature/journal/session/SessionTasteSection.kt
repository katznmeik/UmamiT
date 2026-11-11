package com.umamit.feature.journal.session

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.component.SectionHeader
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.TasteAxis
import com.umamit.core.model.TasteProfile
import com.umamit.core.ui.TasteRadar
import com.umamit.core.ui.TasteSlider
import com.umamit.core.ui.radarValues
import com.umamit.feature.journal.R

const val SESSION_TASTE_ADD_TAG = "session_taste_add"
const val SESSION_TASTE_RESET_TAG = "session_taste_reset"

private val CHEVRON_SIZE = 20.dp

/**
 * GESCHMACK (JRN-03, D-112), below the step table: without values one row „Geschmack hinzufügen"; opened (and always
 * with values) the radar once three axes are set, the six sliders and *Zurücksetzen*. Each release of a slider saves;
 * *Zurücksetzen* clears all six and folds back to the one row. The fold uses the `sheet` spring, none with reduce
 * motion (03 §3.6). Nothing here marks the inputs as missing (D-112).
 */
@Composable
internal fun TasteSection(page: SessionPage, actions: SessionPageActions, modifier: Modifier = Modifier) {
    // The taste as the sliders last said it, kept until the repository shows exactly that: a stale emission still
    // carrying older values must neither flick a value back nor reopen the section after Zurücksetzen.
    var pending by remember(page.sessionId) { mutableStateOf<TasteProfile?>(null) }
    LaunchedEffect(page.taste, pending) { if (pending == page.taste) pending = null }
    val shown = pending ?: page.taste
    var opened by rememberSaveable(page.sessionId) { mutableStateOf(false) }
    val expanded = opened || shown.setCount > 0
    val motion = UmamiTheme.motion
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            title = stringResource(R.string.session_taste),
            modifier = Modifier.padding(top = UmamiSpacing.Medium),
        )
        AnimatedVisibility(
            visible = !expanded,
            enter = expandVertically(motion.sheet()) + fadeIn(motion.fade()),
            exit = shrinkVertically(motion.sheet()) + fadeOut(motion.exitFade()),
        ) {
            AddRow(onClick = { opened = true })
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(motion.sheet()) + fadeIn(motion.fade()),
            exit = shrinkVertically(motion.sheet()) + fadeOut(motion.exitFade()),
        ) {
            TasteInputs(
                taste = shown,
                onSet = { axis, value ->
                    pending = shown.with(axis, value)
                    actions.setTaste(axis, value)
                },
                onReset = {
                    pending = TasteProfile.EMPTY
                    opened = false
                    actions.clearTaste()
                },
            )
        }
    }
}

/** „Geschmack hinzufügen ›": the folded form. */
@Composable
private fun AddRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = UmamiSpacing.MinTouchTarget)
            .padding(horizontal = UmamiSpacing.SideMargin)
            .testTag(SESSION_TASTE_ADD_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.session_taste_add),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painterResource(UmamiIcons.ChevronRight),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(CHEVRON_SIZE),
        )
    }
}

/** The open form: the radar from three axes on, the six sliders and *Zurücksetzen*. */
@Composable
private fun TasteInputs(taste: TasteProfile, onSet: (TasteAxis, Int) -> Unit, onReset: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = UmamiSpacing.SideMargin)) {
        TasteRadar(taste.radarValues())
        TasteAxis.entries.forEach { axis ->
            TasteSlider(axis = axis, value = taste[axis], onChange = { onSet(axis, it) })
        }
        TextButton(
            onClick = onReset,
            modifier = Modifier.align(Alignment.End).testTag(SESSION_TASTE_RESET_TAG),
        ) {
            Text(stringResource(R.string.session_taste_reset))
        }
    }
}

private fun TasteProfile.with(axis: TasteAxis, value: Int): TasteProfile = when (axis) {
    TasteAxis.UMAMI -> copy(umami = value)
    TasteAxis.SWEETNESS -> copy(sweetness = value)
    TasteAxis.BITTERNESS -> copy(bitterness = value)
    TasteAxis.ASTRINGENCY -> copy(astringency = value)
    TasteAxis.BODY -> copy(body = value)
    TasteAxis.AROMA -> copy(aroma = value)
}
