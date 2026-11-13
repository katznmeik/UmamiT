package com.umamit.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.model.TasteAxis
import com.umamit.core.model.TasteProfile
import com.umamit.core.model.TastingLimits
import kotlinx.coroutines.delay

/** Test tag of [InfusionTasteEditor]'s note field. */
const val INFUSION_NOTE_TAG = "infusion_note"

/** Test tag of [InfusionTasteEditor]'s *Zurücksetzen*. */
const val INFUSION_TASTE_RESET_TAG = "infusion_taste_reset"

/** The infusion note is saved this long after the last key (JRN-05, D-114). */
const val INFUSION_NOTE_DEBOUNCE_MS = 1_000L

/** The note's counter shows from this length on (D-114: ≤ 200, a counter from 180). */
const val INFUSION_NOTE_COUNTER_FROM = 180

/**
 * The taste inputs of one infusion (JRN-03, JRN-05, D-114), shared by the session page's GESCHMACK (SCR-16) and the
 * timer's taste sheet (SCR-05): the six [TasteSlider]s (each a `SlimSlider`), the field „Notiz zum Aufguss …"
 * (≤ 200 characters, a counter from 180) and *Zurücksetzen*. No radar and no heading: the host places those.
 *
 * - [taste] is shown as given; [onTasteChange] gets a slider's value on release (a release saves).
 * - The note is edited here and handed to [onNoteSave] 1 s after the last key, and at once when the field loses focus,
 *   when the editor leaves the composition (fold, chip change, sheet closed, back, an alert's screen on top) and when
 *   the app stops; only a text that differs from the one last handed over. [note] is the stored note (a host may
 *   pass the text it was last handed until the store shows it, so a field re-created before a save lands starts
 *   from the newer text): the field starts from it, and follows a newer [note] while it holds no unsaved edit, so a
 *   field re-created from an older note never writes that older text over a newer one; an echo of one of the
 *   field's own earlier saves is not taken. A draft restored after the process died is handed over again, as it
 *   may not have been stored. A host showing several infusions wraps each in `key(position)`, so the field and its
 *   pending save belong to one infusion. The host's write must complete when its screen goes (rule 8).
 * - [onReset] is *Zurücksetzen*: the host clears the infusion's six values; the note stays (D-114).
 */
@Composable
fun InfusionTasteEditor(
    taste: TasteProfile,
    note: String?,
    onTasteChange: (TasteAxis, Int) -> Unit,
    onNoteSave: (String) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        TasteAxis.entries.forEach { axis ->
            TasteSlider(axis = axis, value = taste[axis], onChange = { onTasteChange(axis, it) })
        }
        InfusionNoteField(note = note, onSave = onNoteSave)
        TextButton(
            onClick = onReset,
            modifier = Modifier.align(Alignment.End).testTag(INFUSION_TASTE_RESET_TAG),
        ) {
            Text(stringResource(R.string.ui_infusion_taste_reset))
        }
    }
}

/** „Notiz zum Aufguss …": ≤ 200 characters, a counter from 180; saved as [InfusionTasteEditor] says. */
@Composable
private fun InfusionNoteField(note: String?, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(note.orEmpty()) }
    // The text the store holds or will hold: the stored note at first (so a draft restored after process death,
    // which differs from it, is handed over again, T4 Nit 7), then the text last handed over.
    var saved by remember { mutableStateOf(note.orEmpty()) }
    // The texts handed over whose store has not shown the latest yet, oldest first (stale echoes are skipped).
    val handed = remember { mutableListOf<String>() }
    var focused by remember { mutableStateOf(false) }
    val save by rememberUpdatedState(onSave)
    val flush = {
        val current = text
        if (current != saved) {
            saved = current
            handed += current
            save(current)
        }
    }
    val currentFlush by rememberUpdatedState(flush)
    LaunchedEffect(note) {
        val stored = note.orEmpty()
        val echo = handed.indexOfLast { it.trim() == stored }
        when {
            // The latest save is stored.
            echo >= 0 && echo == handed.lastIndex -> handed.clear()

            // An echo of an older save of this field, a newer one still on its way: not taken.
            echo >= 0 -> Unit

            // A newer note (a save that landed after this field was created, or one from elsewhere) and no unsaved
            // edit: the field follows it, so an edit never starts from older text (m5).
            text == saved -> {
                handed.clear()
                text = stored
                saved = stored
            }
        }
    }
    LaunchedEffect(text) {
        if (text != saved) {
            delay(INFUSION_NOTE_DEBOUNCE_MS)
            currentFlush()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { currentFlush() }
    DisposableEffect(Unit) { onDispose { currentFlush() } }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.take(TastingLimits.MAX_INFUSION_NOTE_LENGTH) },
        placeholder = { Text(stringResource(R.string.ui_infusion_note_placeholder)) },
        supportingText = if (text.length >= INFUSION_NOTE_COUNTER_FROM) {
            {
                Text(
                    stringResource(
                        R.string.ui_infusion_note_counter,
                        text.length,
                        TastingLimits.MAX_INFUSION_NOTE_LENGTH,
                    ),
                )
            }
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = UmamiSpacing.Small)
            .onFocusChanged {
                if (focused && !it.isFocused) currentFlush()
                focused = it.isFocused
            }
            .testTag(INFUSION_NOTE_TAG),
    )
}

/**
 * „Umami 4 · Süße 3": the set axes of [values] in the sliders' order, averages to one decimal („3,5"); „Noch nichts
 * eingetragen" when none is set. The folded taste's line below three axes (SCR-16 v0.6.1, D-114).
 */
@Composable
fun tasteSummary(values: Map<TasteAxis, Double>): String {
    if (values.isEmpty()) return stringResource(R.string.ui_taste_summary_empty)
    val separator = stringResource(R.string.ui_taste_summary_separator)
    return TasteAxis.entries.mapNotNull { axis ->
        values[axis]?.let {
            stringResource(R.string.ui_taste_summary_item, axis.tasteLabel(), TasteRadarShape.valueText(it))
        }
    }.joinToString(separator)
}
