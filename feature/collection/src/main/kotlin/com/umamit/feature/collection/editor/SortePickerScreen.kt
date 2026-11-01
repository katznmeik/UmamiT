package com.umamit.feature.collection.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.umamit.core.designsystem.component.SectionHeader
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.domain.style.BuiltInStyles
import com.umamit.core.model.TeaCategory
import com.umamit.feature.collection.R
import com.umamit.feature.collection.label

/**
 * Sorte wählen (SCR-11; TEA-01, D-100): every built-in style by its Sorte name, grouped by Kategorie (Grüntee,
 * Weißer Tee, Oolong, Schwarzer Tee, Pu-Erh, Kräutertee) and searchable, with *Andere / unbekannt* at the end. A tap
 * calls [onPick] with the style's key (`other` for *Andere / unbekannt*); the host opens the editor (SCR-10) for a
 * new tea of it. [onBack] leaves without a tea.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortePickerScreen(onBack: () -> Unit, onPick: (styleKey: String) -> Unit, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    val names = BuiltInStyles.all.associate { style ->
        style.key to (sorteNameRes(style.key)?.let { stringResource(it) } ?: style.key)
    }
    val categories = TeaCategory.entries.associateWith { stringResource(it.label) }
    val groups = SortePicker.groups(query, { names[it] ?: it }, { categories.getValue(it) })
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.sorte_picker_title), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(UmamiIcons.ArrowBack), stringResource(R.string.sorte_picker_back))
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
        ),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            SearchField(query) { query = it }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (groups.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.sorte_picker_no_match),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(UmamiSpacing.SideMargin),
                        )
                    }
                }
                groups.forEach { group ->
                    group.category?.let { category ->
                        item(key = "header_$category", contentType = "header") {
                            SectionHeader(
                                title = stringResource(category.label),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = UmamiSpacing.XSmall),
                            )
                        }
                    }
                    items(group.styleKeys, key = { it }, contentType = { "style" }) { key ->
                        ListItem(
                            headlineContent = { Text(stringResource(sorteNameRes(key) ?: R.string.sorte_other)) },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(SortePickerTags.style(key))
                                .clickable { onPick(key) }
                                .padding(horizontal = UmamiSpacing.XXSmall),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.XXSmall)
            .testTag(SortePickerTags.SEARCH),
        placeholder = { Text(stringResource(R.string.sorte_picker_search_hint)) },
        leadingIcon = { Icon(painterResource(UmamiIcons.Search), contentDescription = null) },
        trailingIcon = if (query.isEmpty()) {
            null
        } else {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(painterResource(UmamiIcons.Close), stringResource(R.string.sorte_picker_search_clear))
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
    )
}
