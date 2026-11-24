package com.umamit.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * *Sprache* (SET-35, GEN-13, 03 SCR-25): *System*, „Deutsch" and „English", the two names in their own language. A
 * pick closes the dialog and goes to Android at once, which re-creates the activity in the new language.
 */
@Composable
internal fun LanguageDialog(selected: LanguageChoice, onSelect: (LanguageChoice) -> Unit, onDismiss: () -> Unit) {
    ChoiceDialog(title = stringResource(R.string.settings_language), onDismiss = onDismiss) {
        LanguageChoice.entries.forEach { choice ->
            ChoiceRow(
                label = stringResource(choice.label()),
                selected = choice == selected,
                onClick = { onSelect(choice) },
            )
        }
    }
}

internal fun LanguageChoice.label(): Int = when (this) {
    LanguageChoice.SYSTEM -> R.string.settings_language_system
    LanguageChoice.GERMAN -> R.string.settings_language_german
    LanguageChoice.ENGLISH -> R.string.settings_language_english
}
