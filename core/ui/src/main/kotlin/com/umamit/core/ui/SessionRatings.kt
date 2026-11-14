package com.umamit.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.text.ratingText

/** Test tag of the compact ratings text of a row (D-117 Q1). */
const val SESSION_RATINGS_TAG = "session_ratings"

/**
 * A session's two ratings as read-only compact text in a row (03 §3.8, JRN-08, TEA-13, D-117 Q1): „Tee ★ 4,5 ·
 * Zubereitung ★ 3,5", only the set one when just one is rated; nothing when neither is. At a large font it wraps
 * after the „·". TalkBack reads „Tee 4,5 von 5, Zubereitung 3,5 von 5" as one node.
 */
@Composable
fun SessionRatings(
    rating: Double?,
    brewingRating: Double?,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    if (rating == null && brewingRating == null) return
    val tea = rating?.let { ratingText(it.toFloat()) }
    val brewing = brewingRating?.let { ratingText(it.toFloat()) }
    val spoken = sessionRatingsSpoken(rating, brewingRating).orEmpty()
    // Two items so that a narrow row wraps after the „·", never inside „Zubereitung ★ 3,5".
    FlowRow(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(SEPARATOR_GAP),
    ) {
        if (tea != null) {
            val text = stringResource(R.string.ui_session_rating_tea, tea)
            Text(
                text = if (brewing != null) stringResource(R.string.ui_session_rating_then, text) else text,
                style = style,
                color = color,
            )
        }
        if (brewing != null) {
            Text(text = stringResource(R.string.ui_session_rating_brewing, brewing), style = style, color = color)
        }
    }
}

/**
 * „Tee 4,5 von 5, Zubereitung 3,5 von 5" for TalkBack, only the set one; null when neither is set. A row that
 * reads its parts as one sentence (LETZTE SITZUNGEN) takes it into its own description.
 */
@Composable
fun sessionRatingsSpoken(rating: Double?, brewingRating: Double?): String? {
    val tea = rating?.let { stringResource(R.string.ui_session_rating_tea_spoken, ratingText(it.toFloat())) }
    val brewing = brewingRating?.let {
        stringResource(R.string.ui_session_rating_brewing_spoken, ratingText(it.toFloat()))
    }
    val separator = stringResource(R.string.ui_session_rating_spoken_separator)
    return listOfNotNull(tea, brewing).joinToString(separator).ifEmpty { null }
}

private val SEPARATOR_GAP = 4.dp
