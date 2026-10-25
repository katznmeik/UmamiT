package com.umamit.core.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import com.umamit.core.designsystem.component.CheckRecipeBadge
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.model.RecipeCheckReason

/**
 * „Rezept prüfen" with its [reason] (CAT-05): the [CheckRecipeBadge] and one line saying why
 * the recipe did not come straight from the shop's table. One node for TalkBack. Shown on the
 * catalog tea (SCR-21) and above the steps of the setup (SCR-04).
 */
@Composable
fun RecipeCheckNote(reason: RecipeCheckReason, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        CheckRecipeBadge()
        Spacer(Modifier.height(UmamiSpacing.XXSmall))
        Text(
            text = stringResource(reason.text()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@StringRes
private fun RecipeCheckReason.text(): Int = when (this) {
    RecipeCheckReason.FROM_TEXT -> R.string.ui_recipe_reason_from_text
    RecipeCheckReason.FROM_STYLE_DEFAULT -> R.string.ui_recipe_reason_from_style
    RecipeCheckReason.TABLE_TEXT_CONFLICT -> R.string.ui_recipe_reason_conflict
    RecipeCheckReason.IMPLAUSIBLE_VALUE -> R.string.ui_recipe_reason_implausible
    RecipeCheckReason.UNMAPPED_STYLE -> R.string.ui_recipe_reason_unmapped
}
