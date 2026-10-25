package com.umamit.feature.catalog

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import com.umamit.core.designsystem.text.clockText
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.model.CatalogTeaDetail
import com.umamit.core.model.RecipeSource
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.RecipeTemplate
import com.umamit.core.model.StepKind
import com.umamit.core.ui.RecipeCheckNote
import java.util.Locale

/**
 * ABGELEITETES REZEPT (SCR-21; CAT-04, CAT-05): the timer recipes derived from the shop's table
 * above, the default first; „Rezept prüfen" with its reason when the default didn't come
 * straight from the table, and the source of every other recipe that didn't either.
 */
@Composable
internal fun Recipes(tea: CatalogTeaDetail) {
    if (tea.recipes.isEmpty()) return
    Section(pluralStringResource(R.plurals.catalog_tea_section_recipes, tea.recipes.size)) {
        tea.recipeCheckReason?.let { reason ->
            RecipeCheckNote(reason)
            Spacer(Modifier.height(UmamiSpacing.Small))
        }
        tea.recipes.forEachIndexed { index, recipe ->
            if (index > 0) Spacer(Modifier.height(UmamiSpacing.Small))
            Recipe(recipe)
        }
    }
}

/** One recipe: name (and „Standard"), amounts and temperature, the steps; one node for TalkBack. */
@Composable
private fun Recipe(recipe: RecipeTemplate) {
    Column(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = recipe.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (recipe.isDefault) {
                Spacer(Modifier.width(UmamiSpacing.XSmall))
                Text(
                    text = stringResource(R.string.catalog_tea_recipe_default),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        val grams = "%.1f".format(Locale.GERMAN, recipe.leafGrams)
        Text(
            text = if (recipe.baseTempC <= COLD_MAX_C) {
                stringResource(R.string.catalog_tea_recipe_values_cold, grams, recipe.waterMl)
            } else {
                stringResource(R.string.catalog_tea_recipe_values, grams, recipe.waterMl, recipe.baseTempC)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        val separator = stringResource(R.string.catalog_meta_separator)
        Text(
            text = recipe.steps.map { step(it, recipe.baseTempC) }.joinToString(separator),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        // The default's source is the tea's flag above; the others say theirs here (CAT-05).
        val note = recipe.source.note()
        if (!recipe.isDefault && note != null) {
            Text(
                text = stringResource(note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** „1:00", „0:15 bei 70 °C" for a step hotter than the recipe, „Aufschlagen 0:20". */
@Composable
private fun step(step: RecipeStep, baseTempC: Int): String {
    val time = clockText(step.durationSeconds)
    val temperature = step.tempC?.takeIf { it != baseTempC }
    return when {
        step.kind == StepKind.WHISK -> stringResource(R.string.catalog_tea_recipe_whisk, time)
        temperature != null -> stringResource(R.string.catalog_tea_recipe_step_temperature, time, temperature)
        else -> time
    }
}

@StringRes
private fun RecipeSource.note(): Int? = when (this) {
    RecipeSource.CATALOG_TEXT -> R.string.catalog_tea_recipe_source_text
    RecipeSource.STYLE_DEFAULT -> R.string.catalog_tea_recipe_source_style
    RecipeSource.CATALOG_TABLE, RecipeSource.USER -> null
}

/** PRF-03: „kalt" up to 10 °C. */
private const val COLD_MAX_C = 10
