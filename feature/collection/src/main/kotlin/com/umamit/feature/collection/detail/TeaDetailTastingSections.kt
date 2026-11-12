package com.umamit.feature.collection.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.text.averageRatingText
import com.umamit.core.designsystem.text.clockText
import com.umamit.core.designsystem.text.ratingText
import com.umamit.core.designsystem.text.spokenDuration
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.model.TeaRating
import com.umamit.core.ui.DescriptorChip
import com.umamit.core.ui.GermanFormat
import com.umamit.core.ui.TasteRadar
import com.umamit.core.ui.labelRes
import com.umamit.feature.collection.R
import java.time.LocalDate

/** The header's „★ 4,5 (12)" (TEA-13, D-112). */
const val TEA_DETAIL_RATING_TAG = "tea_detail_rating"

/** GESCHMACK (TEA-13, D-112): the average radar and the top descriptors. */
const val TEA_DETAIL_TASTE_TAG = "tea_detail_taste"

/** LETZTE SITZUNGEN (TEA-13, D-112). */
const val TEA_DETAIL_RECENT_TAG = "tea_detail_recent"

/** A row of LETZTE SITZUNGEN, by the session's id. */
fun teaDetailSessionTag(sessionId: String) = "tea_detail_session_$sessionId"

/** The header's „★ 4,5 (12)" under the names; TalkBack reads „Bewertung 4,5 von 5, aus 12 Sitzungen". */
@Composable
internal fun RatingLine(rating: TeaRating, modifier: Modifier = Modifier) {
    val value = averageRatingText(rating.average)
    val description = pluralStringResource(
        R.plurals.collection_tea_rating_description,
        rating.count,
        value,
        rating.count,
    )
    // One node for TalkBack: the sentence, not the figures.
    Box(modifier = modifier.testTag(TEA_DETAIL_RATING_TAG).clearAndSetSemantics { contentDescription = description }) {
        Text(
            text = stringResource(R.string.collection_tea_rating, value, rating.count),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * GESCHMACK (TEA-13, JRN-03, D-112), after FRISCHE and only with data: the tea's average `TasteRadar` (from three
 * axes on) and below it up to five read-only `DescriptorChip`s with their counts („Nori · 4").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TasteSection(taste: DetailTasting) {
    if (!taste.hasData) return
    Section(stringResource(R.string.collection_tea_section_taste)) {
        Column(modifier = Modifier.testTag(TEA_DETAIL_TASTE_TAG)) {
            TasteRadar(taste.averages)
            if (taste.descriptors.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall),
                    verticalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall),
                ) {
                    taste.descriptors.forEach { entry ->
                        DescriptorChip(label = stringResource(entry.descriptor.labelRes()), count = entry.count)
                    }
                }
            }
        }
    }
}

/**
 * LETZTE SITZUNGEN (TEA-13, D-112), after GESCHMACK and only with finished sessions: the last three as rows
 * „8. Juni · 55 °C · 1:00 · 3 Aufgüsse" with „★ 4,5" when rated. A row opens the session page through [onOpenSession]
 * (null: the detail has no page to open, the rows are not tappable); with more sessions than rows, the header's *Alle
 * anzeigen* calls [onShowAll] (null: hidden), which shows the Journal filtered to the tea.
 */
@Composable
internal fun RecentSessions(tea: TeaDetail, onOpenSession: ((sessionId: String) -> Unit)?, onShowAll: (() -> Unit)?) {
    if (tea.recentSessions.isEmpty()) return
    Section(
        title = stringResource(R.string.collection_tea_section_recent),
        padded = false,
        onShowAll = onShowAll.takeIf { tea.hasMoreSessions },
        modifier = Modifier.testTag(TEA_DETAIL_RECENT_TAG),
    ) {
        tea.recentSessions.forEachIndexed { index, session ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = UmamiSpacing.SideMargin),
                    thickness = UmamiSpacing.Hairline,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            SessionRow(session, tea.today, onOpen = onOpenSession?.let { open -> { open(session.id) } })
        }
    }
}

@Composable
private fun SessionRow(session: RecentSession, today: LocalDate, onOpen: (() -> Unit)?) {
    val day = if (session.day.year == today.year) {
        GermanFormat.dayMonthLong(session.day)
    } else {
        GermanFormat.day(session.day)
    }
    val separator = stringResource(R.string.collection_tea_recipe_separator)
    val spokenSeparator = stringResource(R.string.collection_tea_session_spoken_separator)
    val meta = session.metaParts(day, session.firstInfusionSeconds?.let(::clockText)).joinToString(separator)
    val spoken = session.metaParts(day, session.firstInfusionSeconds?.let { spokenDuration(it) })
    val rating = session.rating?.let { ratingText(it.toFloat()) }
    val ratingSpoken = rating?.let { stringResource(R.string.collection_tea_session_rating_description, it) }
    val description = (spoken + listOfNotNull(ratingSpoken)).joinToString(spokenSeparator)
    val openLabel = stringResource(R.string.collection_tea_history_open_session)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_MIN_HEIGHT)
            .then(
                if (onOpen != null) {
                    Modifier.clickable(onClickLabel = openLabel, role = Role.Button, onClick = onOpen)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.XSmall)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .testTag(teaDetailSessionTag(session.id)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = meta,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (rating != null) {
            Spacer(Modifier.width(UmamiSpacing.Small))
            Text(
                text = stringResource(R.string.collection_tea_session_rating, rating),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (onOpen != null) {
            Icon(
                painterResource(UmamiIcons.ChevronRight),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = UmamiSpacing.XSmall).size(CHEVRON_SIZE),
            )
        }
    }
}

/** „8. Juni", „55 °C", the first infusion's time, „3 Aufgüsse" (or „2 von 3 Aufgüssen") as the journal's row has it. */
@Composable
private fun RecentSession.metaParts(day: String, firstInfusion: String?): List<String> = listOfNotNull(
    day,
    temperatureC?.let { stringResource(R.string.collection_tea_recipe_temp, it) },
    firstInfusion,
    if (finishedEarly) {
        pluralStringResource(
            R.plurals.collection_tea_session_infusions_of,
            plannedInfusions,
            infusions,
            plannedInfusions,
        )
    } else {
        pluralStringResource(R.plurals.collection_tea_recipe_infusions, infusions, infusions)
    },
)

private val CHEVRON_SIZE = 20.dp
private val ROW_MIN_HEIGHT = 56.dp
