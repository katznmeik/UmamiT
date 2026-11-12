package com.umamit.feature.journal.session

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.text.germanUppercase
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.TasteDescriptor
import com.umamit.core.model.TastingLimits
import com.umamit.core.ui.DescriptorChip
import com.umamit.core.ui.labelRes
import com.umamit.feature.journal.R
import com.umamit.feature.journal.rememberJournalSheetState
import kotlinx.coroutines.launch

/** The descriptor sheet (SCR-30, JRN-04). */
const val DESCRIPTOR_SHEET_TAG = "descriptor_sheet"
const val DESCRIPTOR_SEARCH_TAG = "descriptor_search"
const val DESCRIPTOR_DONE_TAG = "descriptor_done"
const val DESCRIPTOR_COUNT_TAG = "descriptor_count"

/** Test tag of [descriptor]'s chip in the sheet. */
fun descriptorSheetChipTag(descriptor: TasteDescriptor): String = "descriptor_sheet_chip_${descriptor.key}"

/**
 * *Beschreibungen* (SCR-30, JRN-04, D-112): the title row with the count and *Fertig* and the search stay in place
 * above the scrolling groups (D-110, as the filter sheet). A tap on a chip ticks or unticks and saves at once
 * ([onToggle]); at [TastingLimits.MAX_DESCRIPTORS] the unticked chips are greyed. [chosen] is every key chosen,
 * [total] their number (keys the vocabulary lacks count too, as the write counts them). The caller never composes it
 * during an alert (rule 8).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DescriptorSheet(
    chosen: Set<String>,
    total: Int,
    onToggle: (TasteDescriptor, Boolean) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val reduceMotion = UmamiTheme.reduceMotion
    val sheetState = rememberJournalSheetState(reduceMotion)
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    var query by rememberSaveable { mutableStateOf("") }
    // Set by *Fertig*: the sheet is on its way out, so a second tap does nothing.
    var leaving by remember { mutableStateOf(false) }
    val leave = {
        if (!leaving) {
            leaving = true
            scope.launch { if (!reduceMotion) sheetState.hide() }.invokeOnCompletion {
                if (reduceMotion || !sheetState.isVisible) onDismissRequest() else leaving = false
            }
        }
    }
    val dividerAlpha by animateFloatAsState(
        targetValue = if (scroll.canScrollBackward) 1f else 0f,
        animationSpec = UmamiTheme.motion.fade(),
        label = "descriptorSheetDivider",
    )
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = Modifier.testTag(DESCRIPTOR_SHEET_TAG),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        TitleRow(total, onDone = leave)
        SearchField(query, onChange = { query = it })
        HorizontalDivider(
            modifier = Modifier.graphicsLayer {
                alpha = dividerAlpha
            },
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Groups(query, chosen, total, onToggle, Modifier.verticalScroll(scroll))
    }
}

@Composable
private fun TitleRow(total: Int, onDone: () -> Unit) {
    val max = TastingLimits.MAX_DESCRIPTORS
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = UmamiSpacing.SideMargin, end = UmamiSpacing.XSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.descriptor_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = if (total >= max) {
                    stringResource(R.string.descriptor_sheet_count_full, total, max)
                } else {
                    stringResource(R.string.descriptor_sheet_count, total, max)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(DESCRIPTOR_COUNT_TAG),
            )
        }
        TextButton(onClick = onDone, modifier = Modifier.testTag(DESCRIPTOR_DONE_TAG)) {
            Text(stringResource(R.string.descriptor_sheet_done))
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.XXSmall)
            .testTag(DESCRIPTOR_SEARCH_TAG),
        placeholder = { Text(stringResource(R.string.descriptor_search_hint)) },
        leadingIcon = { Icon(painterResource(UmamiIcons.Search), contentDescription = null) },
        trailingIcon = if (query.isEmpty()) {
            null
        } else {
            {
                IconButton(onClick = { onChange("") }) {
                    Icon(painterResource(UmamiIcons.Close), stringResource(R.string.descriptor_search_clear))
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
    )
}

@Composable
private fun Groups(
    query: String,
    chosen: Set<String>,
    total: Int,
    onToggle: (TasteDescriptor, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val labels = remember(resources) { TasteDescriptor.entries.associateWith { resources.getString(it.labelRes()) } }
    val groups = remember(query, labels) { matchingGroups(query) { labels.getValue(it) } }
    val full = total >= TastingLimits.MAX_DESCRIPTORS
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = UmamiSpacing.SideMargin, end = UmamiSpacing.SideMargin, bottom = UmamiSpacing.Large),
    ) {
        if (groups.isEmpty()) {
            Text(
                text = stringResource(R.string.descriptor_none_found),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = UmamiSpacing.Medium),
            )
        }
        groups.forEach { (group, members) ->
            val title = stringResource(group.labelRes())
            Text(
                text = title.germanUppercase(),
                style = UmamiTheme.textStyles.overline,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = UmamiSpacing.Medium)
                    .semantics {
                        heading()
                        contentDescription = title
                    },
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall),
                verticalArrangement = Arrangement.spacedBy(UmamiSpacing.XXSmall),
            ) {
                members.forEach { descriptor ->
                    val ticked = descriptor.key in chosen
                    DescriptorChip(
                        label = labels.getValue(descriptor),
                        selected = ticked,
                        onClick = { onToggle(descriptor, !ticked) },
                        modifier = Modifier.testTag(descriptorSheetChipTag(descriptor)),
                        enabled = ticked || !full,
                    )
                }
            }
        }
    }
}
