package com.umamit.feature.onboarding.mockup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.component.PillButton
import com.umamit.core.designsystem.component.SectionHeader
import com.umamit.core.designsystem.component.TeaListItem
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.feature.onboarding.R

/** M-SORTE: *Sorte wählen*, the row Sencha marked. */
@Composable
internal fun SorteMockup() {
    MockupScreen {
        MockupTopBar(title = stringResource(R.string.mockup_sorte_picker_title), back = true)
        MockupGap(height = UmamiSpacing.XSmall)
        Row(Modifier.padding(horizontal = UmamiSpacing.Medium)) { MockupField(label = "", value = null) }
        MockupGap(height = UmamiSpacing.XSmall)
        SorteRow(R.string.mockup_demo_sorte_sencha, MockupDemo.morgentau.color(), Modifier.mockupMarker())
        SorteRow(R.string.mockup_demo_sorte_gyokuro, MockupDemo.nebelhang.color())
        SorteRow(R.string.mockup_demo_sorte_genmaicha, MockupDemo.herbstfeuer.color())
        MockupListRow(text = null)
        MockupListRow(text = null, barWidth = SHORT_BAR)
        SorteRow(R.string.mockup_sorte_other, MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun SorteRow(name: Int, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(horizontal = UmamiSpacing.Medium).fillMaxWidth().padding(vertical = UmamiSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.Small),
    ) {
        Icon(painterResource(UmamiIcons.Leaf), null, Modifier.size(LEAF_ICON), tint = color)
        Text(
            stringResource(name),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** M-TEA-NEW: *Neuer Tee*, the *Kauf* section (*Packung* and *Preis*) marked. */
@Composable
internal fun TeaNewMockup() {
    MockupScreen {
        MockupTopBar(title = stringResource(R.string.mockup_tea_editor_title_new), back = true)
        Column(
            Modifier.padding(horizontal = UmamiSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall),
        ) {
            MockupGap(height = UmamiSpacing.XSmall)
            MockupField(label = "", value = null)
            MockupField(label = "", value = null)
            MockupField(label = "", value = null)
        }
        Column(Modifier.mockupMarker()) {
            SectionHeader(stringResource(R.string.mockup_tea_editor_section_purchase))
            Row(
                Modifier.padding(horizontal = UmamiSpacing.Medium),
                horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall),
            ) {
                Box(Modifier.weight(1f)) { MockupField(stringResource(R.string.mockup_tea_editor_package)) }
                Box(Modifier.weight(1f)) { MockupField(stringResource(R.string.mockup_tea_editor_price)) }
            }
            MockupGap(height = UmamiSpacing.XSmall)
        }
        MockupGap(height = UmamiSpacing.XSmall)
        Column(Modifier.padding(horizontal = UmamiSpacing.Medium)) { MockupField(label = "", value = null) }
    }
}

/** M-CATALOG-MENU: *Katalog* with ⋮ open, *Link einfügen* marked. */
@Composable
internal fun CatalogMenuMockup() {
    Box(Modifier.fillMaxSize()) {
        MockupScreen {
            MockupTopBar(title = stringResource(R.string.mockup_collection_title), menu = true)
            MockupGap(height = UmamiSpacing.XSmall)
            MockupSegments(catalog = true)
            MockupGap(height = UmamiSpacing.XSmall)
            MockupDemo.teas.forEach { tea ->
                TeaListItem(
                    title = stringResource(tea.name),
                    meta = stringResource(tea.sorte),
                    onClick = {},
                    placeholderColor = tea.color(),
                )
            }
        }
        MockupMenu(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = MENU_TOP, end = UmamiSpacing.XXSmall)
                .widthIn(max = MENU_MAX_WIDTH),
        ) {
            MockupMenuBar(MENU_BAR_LONG)
            MockupMenuItem(stringResource(R.string.mockup_collection_catalog_paste_link), Modifier.mockupMarker())
            MockupMenuBar(MENU_BAR_MEDIUM)
        }
    }
}

/** M-CATALOG-TEA: a catalog tea (demo), *In Sammlung aufnehmen* marked. */
@Composable
internal fun CatalogTeaMockup() {
    CatalogTeaScreen(marked = true)
}

@Composable
private fun CatalogTeaScreen(marked: Boolean) {
    val tea = MockupDemo.morgentau
    MockupScreen {
        MockupHero(tea)
        Column(Modifier.padding(horizontal = UmamiSpacing.Medium)) {
            MockupGap(height = UmamiSpacing.XSmall)
            Text(
                stringResource(tea.name),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(tea.sorte),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MockupGap(height = UmamiSpacing.XXSmall)
            Text(
                stringResource(R.string.mockup_collection_tea_cost_price, MockupDemo.pricePer100g()),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            MockupGap()
            PillButton(
                text = stringResource(R.string.mockup_catalog_tea_add),
                onClick = {},
                modifier = if (marked) Modifier.mockupMarker() else Modifier,
            )
            MockupGap()
            MockupBars(listOf(BAR_LONG, BAR_MEDIUM, BAR_LONG, BAR_SHORT))
        }
    }
}

/** M-ADD-SHEET: the sheet *In Sammlung aufnehmen* over the catalog tea, *Aufnehmen* marked. */
@Composable
internal fun AddSheetMockup() {
    Box(Modifier.fillMaxSize()) {
        CatalogTeaScreen(marked = false)
        MockupScrim()
        MockupSheet(Modifier.align(Alignment.BottomCenter)) {
            Text(
                stringResource(R.string.mockup_catalog_add_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            MockupField(stringResource(R.string.mockup_catalog_add_package))
            MockupField(stringResource(R.string.mockup_catalog_add_price))
            MockupField(stringResource(R.string.mockup_catalog_add_date))
            PillButton(
                text = stringResource(R.string.mockup_catalog_add_save),
                onClick = {},
                modifier = Modifier.align(Alignment.End).mockupMarker(),
            )
            MockupGap(height = UmamiSpacing.XSmall)
        }
    }
}

private val LEAF_ICON = 20.dp
private val SHORT_BAR = 80.dp
private val MENU_TOP = 44.dp
private val MENU_MAX_WIDTH = 280.dp
private val MENU_BAR_LONG = 150.dp
private val MENU_BAR_MEDIUM = 110.dp
private val BAR_LONG = 220.dp
private val BAR_MEDIUM = 160.dp
private val BAR_SHORT = 100.dp
