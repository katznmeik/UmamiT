package com.umamit.core.designsystem.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.umamit.core.designsystem.R

/**
 * The unsaved-changes guard (GEN-08, 03 §5): „Änderungen verwerfen?" with
 * *Weiter bearbeiten* and *Verwerfen*. Back and a tap outside keep editing.
 */
@Composable
fun DiscardChangesDialog(onKeepEditing: () -> Unit, onDiscard: () -> Unit, modifier: Modifier = Modifier) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        modifier = modifier,
        title = { Text(stringResource(R.string.ds_discard_changes_title)) },
        confirmButton = {
            TextButton(onClick = onDiscard) { Text(stringResource(R.string.ds_discard_changes_discard)) }
        },
        dismissButton = {
            TextButton(onClick = onKeepEditing) { Text(stringResource(R.string.ds_discard_changes_keep)) }
        },
    )
}
