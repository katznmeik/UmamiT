package com.umamit.feature.journal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

const val RECIPE_OFFER_TAG = "recipe_offer"

/** The offer's line and the line that replaces it share this minimum height (a text button's, SCR-06). */
private val OfferLineMinHeight = 40.dp

/** What the line of *Zeiten ins Rezept übernehmen* shows (PRF-07, D-112); without an offer there is no line. */
enum class RecipeOfferState {
    /** The offer applies: the text button. */
    OFFERED,

    /** Tapped: „Rezept aktualisiert.". */
    ACCEPTED,
}

/**
 * *Zeiten ins Rezept übernehmen* on the „Gespeichert" sheet (SCR-06) and under the session page's step table
 * (SCR-16), then „Rezept aktualisiert." in the same height (PRF-07, D-112). It is offered, never asked: no dialog, no
 * badge, nothing that reminds. The button's label lines up with the lines above (no side padding).
 */
@Composable
internal fun RecipeOfferLine(state: RecipeOfferState, onAccept: () -> Unit, modifier: Modifier = Modifier) {
    val lineModifier = modifier.heightIn(min = OfferLineMinHeight)
    when (state) {
        RecipeOfferState.OFFERED -> TextButton(
            modifier = lineModifier.testTag(RECIPE_OFFER_TAG),
            onClick = onAccept,
            contentPadding = PaddingValues(
                vertical = ButtonDefaults.TextButtonContentPadding.calculateTopPadding(),
            ),
        ) {
            Text(stringResource(R.string.journal_recipe_offer))
        }

        RecipeOfferState.ACCEPTED -> Box(modifier = lineModifier, contentAlignment = Alignment.CenterStart) {
            Text(
                text = stringResource(R.string.journal_recipe_offer_done),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
