package com.umamit.core.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import com.umamit.core.designsystem.component.DiscardChangesDialog
import com.umamit.core.designsystem.icon.UmamiIcons

/**
 * The shared frame of the tea, recipe and vessel editors (SCR-10, SCR-12, SCR-14, D-101): the top bar
 * ✕ · [title] · *Speichern* (+ [menu]), the discard question on ✕ and back while [hasChanges] (GEN-08), and the
 * keyboard insets of D-102.
 *
 * The content padding holds the keyboard too (safeDrawing on top, end and bottom), so the editor's fields must not add
 * `imePadding()` on top: that hid them behind the keyboard (D-102). Save and [menu] show only while [showActions]
 * (the form is loaded).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
fun EditorScaffold(
    title: String,
    closeDescription: String,
    saveLabel: String,
    saveTag: String,
    showActions: Boolean,
    hasChanges: Boolean,
    saving: Boolean,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    alertKey: String? = null,
    snackbarHost: @Composable () -> Unit = {},
    menu: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val askDiscard = rememberAlertGate(alertKey)
    val requestClose = { if (hasChanges) askDiscard.open() else onClose() }
    BackHandler(enabled = hasChanges) { askDiscard.open() }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                // One line beside ✕, Speichern and ⋮, even at font scale 2.0 on a short screen.
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = requestClose) {
                        Icon(painterResource(UmamiIcons.Close), closeDescription)
                    }
                },
                actions = {
                    if (showActions) {
                        TextButton(onClick = onSave, enabled = !saving, modifier = Modifier.testTag(saveTag)) {
                            Text(saveLabel)
                        }
                        menu()
                    }
                },
            )
        },
        snackbarHost = snackbarHost,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.End + WindowInsetsSides.Bottom,
        ),
        content = content,
    )
    AlertGated(askDiscard) {
        DiscardChangesDialog(
            onKeepEditing = askDiscard::close,
            onDiscard = {
                askDiscard.close()
                onClose()
            },
        )
    }
}
