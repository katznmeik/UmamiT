package com.umamit.feature.collection.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.umamit.core.common.stockGramsText
import com.umamit.core.designsystem.component.PillButton
import com.umamit.core.designsystem.component.SectionHeader
import com.umamit.core.designsystem.component.StatFigure
import com.umamit.core.designsystem.component.StatTriplet
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.model.HarvestSeason
import com.umamit.core.model.StockLevel
import com.umamit.core.model.UmamiLevel
import com.umamit.feature.collection.R
import java.time.format.DateTimeFormatter
import java.util.Locale

/** *Bestand korrigieren* (INV-03). */
const val TEA_DETAIL_CORRECT_STOCK_TAG = "tea_detail_correct_stock"

/** *Beim Tee-Kontor nachbestellen* (INV-09). */
const val TEA_DETAIL_REORDER_TAG = "tea_detail_reorder"

/** *Rezept hinzufügen*. */
const val TEA_DETAIL_ADD_RECIPE_TAG = "tea_detail_add_recipe"

/** A recipe row of REZEPTE, by the recipe's id. */
fun teaDetailRecipeTag(recipeId: String) = "tea_detail_recipe_$recipeId"

/**
 * REZEPTE: each recipe with its key values, the default marked; a tap opens the setup with it, its pencil the
 * recipe editor; *Rezept hinzufügen* below them, up to 10.
 */
@Composable
internal fun Recipes(tea: TeaDetail, actions: TeaDetailActions) {
    Section(stringResource(R.string.collection_tea_section_recipes), padded = false) {
        tea.recipes.forEachIndexed { index, recipe ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = UmamiSpacing.SideMargin),
                    thickness = UmamiSpacing.Hairline,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            RecipeRow(recipe, onBrew = { actions.brew(recipe.id) }, onEdit = { actions.editRecipe(recipe.id) })
        }
        if (tea.canAddRecipe) {
            TextButton(
                onClick = actions.addRecipe,
                modifier = Modifier
                    .padding(horizontal = UmamiSpacing.XSmall)
                    .testTag(TEA_DETAIL_ADD_RECIPE_TAG),
            ) {
                Icon(painterResource(UmamiIcons.Add), contentDescription = null, modifier = Modifier.size(ICON_SIZE))
                Spacer(Modifier.width(UmamiSpacing.XSmall))
                Text(stringResource(R.string.collection_tea_recipe_add))
            }
        }
    }
}

@Composable
private fun RecipeRow(recipe: RecipeSummary, onBrew: () -> Unit, onEdit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_MIN_HEIGHT)
            .clickable(role = Role.Button, onClick = onBrew)
            .padding(start = UmamiSpacing.SideMargin, end = UmamiSpacing.XSmall)
            .padding(vertical = UmamiSpacing.XSmall)
            .testTag(teaDetailRecipeTag(recipe.id)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = recipe.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (recipe.isDefault) {
                    Spacer(Modifier.width(UmamiSpacing.XSmall))
                    Tag(stringResource(R.string.collection_tea_recipe_default))
                }
            }
            Text(
                text = recipe.keyValues(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onEdit) {
            Icon(
                painterResource(UmamiIcons.Edit),
                contentDescription = stringResource(R.string.collection_tea_recipe_edit, recipe.name),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** „Kyusu 250 ml · 5 g · 250 ml · 70 °C · 3 Aufgüsse". */
@Composable
private fun RecipeSummary.keyValues(): String = listOfNotNull(
    vesselName,
    stringResource(R.string.collection_tea_recipe_grams, stockGramsText(leafGrams)),
    stringResource(R.string.collection_tea_recipe_ml, waterMl),
    stringResource(R.string.collection_tea_recipe_temp, tempC),
    pluralStringResource(R.plurals.collection_tea_recipe_infusions, infusions, infusions),
).joinToString(stringResource(R.string.collection_tea_recipe_separator))

/**
 * VORRAT: „38 g übrig" (with *knapp*, or „Leer"), *Bestand korrigieren* and INV-09's *Beim Tee-Kontor
 * nachbestellen*: a filled button when *Knapp* or *Leer*, otherwise a text link with the external-link icon.
 */
@Composable
internal fun Stock(tea: TeaDetail, onCorrect: () -> Unit, onReorder: (String) -> Unit) {
    Section(stringResource(R.string.collection_tea_section_stock)) {
        Text(
            text = stockText(tea.stockGrams, tea.stockLevel, short = false),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(UmamiSpacing.XSmall))
        OutlinedButton(onClick = onCorrect, modifier = Modifier.testTag(TEA_DETAIL_CORRECT_STOCK_TAG)) {
            Icon(painterResource(UmamiIcons.Edit), contentDescription = null, modifier = Modifier.size(ICON_SIZE))
            Spacer(Modifier.width(UmamiSpacing.XSmall))
            Text(stringResource(R.string.collection_tea_stock_correct))
        }
        val reorder = tea.reorder ?: return@Section
        val label = stringResource(R.string.collection_tea_reorder)
        val icon = painterResource(UmamiIcons.OpenInNew)
        if (reorder.prominent) {
            Spacer(Modifier.height(UmamiSpacing.Small))
            PillButton(
                text = label,
                onClick = { onReorder(reorder.url) },
                icon = icon,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TEA_DETAIL_REORDER_TAG),
            )
        } else {
            TextButton(
                onClick = { onReorder(reorder.url) },
                modifier = Modifier.testTag(TEA_DETAIL_REORDER_TAG),
            ) {
                Text(label)
                Spacer(Modifier.width(UmamiSpacing.XSmall))
                Icon(icon, contentDescription = null, modifier = Modifier.size(ICON_SIZE))
            }
        }
    }
}

/** „38 g übrig", „14 g übrig · knapp", „Leer"; [short]: „38 g" for the `StatTriplet`. */
@Composable
internal fun stockText(grams: Double, level: StockLevel, short: Boolean): String = when {
    level == StockLevel.EMPTY -> stringResource(R.string.collection_tea_stock_empty)
    short -> stringResource(R.string.collection_tea_stat_stock_value, stockGramsText(grams))
    level == StockLevel.LOW -> stringResource(R.string.collection_tea_stock_low, stockGramsText(grams))
    else -> stringResource(R.string.collection_tea_stock, stockGramsText(grams))
}

/** BESCHREIBUNG or NOTIZEN: only with content. */
@Composable
internal fun TextSection(title: Int, text: String?) {
    if (text == null) return
    Section(stringResource(title)) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** DETAILS: origin, cultivar, harvest, shading, processing, ingredients and vendor, each only when set. */
@Composable
internal fun Details(facts: TeaFacts) {
    val season = facts.harvestSeason?.label?.let { stringResource(it) }
    val harvest = listOfNotNull(season, facts.harvestYear?.toString()).joinToString(" ").ifEmpty { null }
    val rows = listOfNotNull(
        listOfNotNull(facts.originCountry, facts.originRegion).joinToString(", ").ifEmpty { null }
            ?.let { R.string.collection_tea_origin to it },
        facts.cultivar?.let { R.string.collection_tea_cultivar to it },
        harvest?.let { R.string.collection_tea_harvest to it },
        facts.shadingDays?.let {
            R.string.collection_tea_shading to pluralStringResource(R.plurals.collection_tea_shading_days, it, it)
        },
        facts.processing?.let { R.string.collection_tea_processing to it },
        facts.ingredients?.let { R.string.collection_tea_ingredients to it },
        facts.vendor?.let { R.string.collection_tea_vendor to it },
    )
    if (rows.isEmpty()) return
    Section(stringResource(R.string.collection_tea_section_details)) {
        rows.forEach { (label, value) -> Fact(stringResource(label), value) }
    }
}

internal val HarvestSeason.label: Int?
    get() = when (this) {
        HarvestSeason.FIRST_FLUSH -> R.string.collection_tea_season_first_flush
        HarvestSeason.SECOND_FLUSH -> R.string.collection_tea_season_second_flush
        HarvestSeason.SUMMER -> R.string.collection_tea_season_summer
        HarvestSeason.AUTUMN -> R.string.collection_tea_season_autumn
        HarvestSeason.WINTER -> R.string.collection_tea_season_winter
        HarvestSeason.UNKNOWN -> null
    }

@Composable
private fun Section(title: String, padded: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = UmamiSpacing.Large)) {
        SectionHeader(title = title, modifier = Modifier.fillMaxWidth())
        Column(
            modifier = if (padded) Modifier.padding(horizontal = UmamiSpacing.SideMargin) else Modifier,
            content = content,
        )
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

private const val LABEL_WEIGHT = 0.4f
private val ICON_SIZE = 18.dp
private val ROW_MIN_HEIGHT = 56.dp
