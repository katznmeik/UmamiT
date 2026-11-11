package com.umamit.feature.journal.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.umamit.core.designsystem.component.SectionHeader
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.model.TasteDescriptor
import com.umamit.core.ui.AlertGated
import com.umamit.core.ui.DescriptorChip
import com.umamit.core.ui.labelRes
import com.umamit.core.ui.rememberAlertGate
import com.umamit.feature.journal.R

const val SESSION_DESCRIPTORS_ADD_TAG = "session_descriptors_add"

/** Test tag of the chosen [descriptor]'s chip on the page. */
fun sessionDescriptorTag(descriptor: TasteDescriptor): String = "session_descriptor_${descriptor.key}"

/**
 * BESCHREIBUNGEN (JRN-04, D-112), below GESCHMACK: the chosen chips in the vocabulary's order and *+ Beschreibung*;
 * a tap on any of them opens the descriptor sheet (SCR-30), so a stray tap removes nothing. Nothing here marks the
 * input as missing (D-112). An alert ([alertKey]) closes the sheet in its first frame (rule 8).
 */
@Composable
internal fun DescriptorsSection(
    page: SessionPage,
    actions: SessionPageActions,
    alertKey: String?,
    modifier: Modifier = Modifier,
) {
    // The ticks as the sheet last said them, kept until the repository shows exactly that: a stale emission must
    // not flick a chip back.
    var pending by remember(page.sessionId) { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    LaunchedEffect(page.descriptorKeys, pending) {
        val settled = pending.filter { (key, ticked) -> (key in page.descriptorKeys) == ticked }
        if (settled.isNotEmpty()) pending = pending - settled.keys
    }
    val keys = remember(page.descriptorKeys, pending) {
        (page.descriptorKeys.filter { pending[it] != false } + pending.filterValues { it }.keys).toSet()
    }
    val sheet = rememberAlertGate(alertKey)
    val resources = LocalResources.current
    val addDescription = stringResource(R.string.session_descriptor_add_description)
    val editDescription = stringResource(R.string.session_descriptor_edit_description)
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            title = stringResource(R.string.session_descriptors),
            modifier = Modifier.padding(top = UmamiSpacing.Medium),
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = UmamiSpacing.SideMargin),
            horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall),
            verticalArrangement = Arrangement.spacedBy(UmamiSpacing.XXSmall),
        ) {
            TasteDescriptor.entries.filter { it.key in keys }.forEach { descriptor ->
                DescriptorChip(
                    label = resources.getString(descriptor.labelRes()),
                    onClick = sheet::open,
                    modifier = Modifier.testTag(sessionDescriptorTag(descriptor)),
                    onClickLabel = editDescription,
                )
            }
            AssistChip(
                onClick = sheet::open,
                label = {
                    Text(stringResource(R.string.session_descriptor_add), style = MaterialTheme.typography.labelLarge)
                },
                modifier = Modifier
                    .testTag(SESSION_DESCRIPTORS_ADD_TAG)
                    .semantics { contentDescription = addDescription },
            )
        }
    }
    AlertGated(sheet) {
        DescriptorSheet(
            chosen = keys,
            total = keys.size,
            onToggle = { descriptor, ticked ->
                pending = pending + (descriptor.key to ticked)
                actions.setDescriptor(descriptor.key, ticked) { pending = pending - descriptor.key }
            },
            onDismissRequest = sheet::close,
        )
    }
}
