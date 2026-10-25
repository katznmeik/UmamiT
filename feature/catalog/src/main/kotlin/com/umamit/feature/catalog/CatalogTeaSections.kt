package com.umamit.feature.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.component.SectionHeader
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.model.BrewTableRow
import com.umamit.core.model.CatalogTeaDetail
import com.umamit.core.model.TasteScale

// The sections of a catalog tea's detail (SCR-21): only those the shop fills are shown.

/** Origin, cultivar, harvest, shading, steaming and the other properties: only those the shop shows. */
@Composable
internal fun Facts(tea: CatalogTeaDetail) {
    val facts = listOfNotNull(
        tea.originCountry?.let { R.string.catalog_tea_origin to it },
        tea.originRegion?.let { R.string.catalog_tea_region to it },
        tea.cultivar?.let { R.string.catalog_tea_cultivar to it },
        tea.harvest?.let { R.string.catalog_tea_harvest to it },
        tea.shading?.let { R.string.catalog_tea_shading to it },
        tea.steaming?.let { R.string.catalog_tea_steaming to it },
        tea.season?.let { R.string.catalog_tea_season to it },
        tea.matchaGrade?.let { R.string.catalog_tea_matcha_grade to it },
        tea.manufacturer?.let { R.string.catalog_tea_manufacturer to it },
    )
    if (facts.isEmpty()) return
    Section(stringResource(R.string.catalog_tea_section_profile)) {
        facts.forEach { (label, value) -> Fact(stringResource(label), value) }
    }
}

@Composable
internal fun Taste(tea: CatalogTeaDetail) {
    if (tea.tasteScales.isEmpty() && tea.aroma == null && tea.tasteNotes == null) return
    Section(stringResource(R.string.catalog_tea_section_taste)) {
        tea.tasteScales.forEach { Scale(it) }
        tea.aroma?.let { Fact(stringResource(R.string.catalog_tea_aroma), it) }
        tea.tasteNotes?.let { Fact(stringResource(R.string.catalog_tea_taste_notes), it) }
    }
}

/** A taste scale as a row of five leaves (SCR-21); TalkBack reads „Umami: 4 von 5". */
@Composable
private fun Scale(scale: TasteScale) {
    val description = stringResource(R.string.catalog_tea_scale_description, scale.label, scale.leaves)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = UmamiSpacing.XXSmall)
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = scale.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.XXSmall)) {
            repeat(TasteScale.MAX_LEAVES) { index ->
                Icon(
                    painter = painterResource(UmamiIcons.Leaf),
                    contentDescription = null,
                    tint = if (index < scale.leaves) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = EMPTY_LEAF_ALPHA)
                    },
                    modifier = Modifier.size(LEAF_SIZE),
                )
            }
        }
    }
}

@Composable
internal fun Description(tea: CatalogTeaDetail) {
    val html = tea.descriptionHtml
    if (html == null && tea.ingredients == null) return
    Section(stringResource(R.string.catalog_tea_section_description)) {
        if (html != null) {
            val linkColor = MaterialTheme.colorScheme.primary
            val text = remember(html, linkColor) {
                AnnotatedString.fromHtml(
                    html,
                    linkStyles = TextLinkStyles(
                        SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                    ),
                ).let { it.subSequence(0, it.text.trimEnd().length) } // no blank lines after the last paragraph
            }
            Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        tea.ingredients?.let {
            Spacer(Modifier.height(UmamiSpacing.XSmall))
            Fact(stringResource(R.string.catalog_tea_ingredients), it)
        }
    }
}

/** ZUBEREITUNG: the shop's brewing grid and text, verbatim (CAT-11); the derived recipe follows with #68. */
@Composable
internal fun Brewing(tea: CatalogTeaDetail) {
    if (tea.brewTable.isEmpty() && tea.brewText == null) return
    Section(stringResource(R.string.catalog_tea_section_brewing)) {
        tea.brewTable.forEachIndexed { index, row ->
            if (index > 0) Spacer(Modifier.height(UmamiSpacing.Small))
            Text(
                text = row.methodLabel(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            row.cells.forEach { Fact(it.label.orEmpty(), it.text) }
        }
        tea.brewText?.let {
            if (tea.brewTable.isNotEmpty()) Spacer(Modifier.height(UmamiSpacing.Small))
            Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(UmamiSpacing.XSmall))
        Text(
            text = stringResource(R.string.catalog_tea_brewing_source),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BrewTableRow.methodLabel(): String = when (kind) {
    BrewTableRow.KIND_JAPAN -> stringResource(R.string.catalog_tea_brew_kyusu)
    BrewTableRow.KIND_WESTERN -> stringResource(R.string.catalog_tea_brew_western)
    else -> kind.replaceFirstChar { it.uppercase() }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = UmamiSpacing.Large)) {
        SectionHeader(title = title, modifier = Modifier.fillMaxWidth())
        Column(modifier = Modifier.padding(horizontal = UmamiSpacing.SideMargin), content = content)
    }
}

/** A label and its value side by side; one node for TalkBack. */
@Composable
private fun Fact(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = UmamiSpacing.XXSmall)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.Small),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(LABEL_WEIGHT),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f - LABEL_WEIGHT),
        )
    }
}

private const val EMPTY_LEAF_ALPHA = 0.3f
private const val LABEL_WEIGHT = 0.4f
private val LEAF_SIZE = 20.dp
