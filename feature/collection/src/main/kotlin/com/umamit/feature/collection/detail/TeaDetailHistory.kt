package com.umamit.feature.collection.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.ui.AlertGate
import com.umamit.core.ui.AlertGated
import com.umamit.core.ui.GermanFormat
import com.umamit.feature.collection.R
import kotlin.math.abs

/** A row of VORRAT's stock history (SCR-29), by the event's id. */
fun teaDetailHistoryRowTag(eventId: String) = "tea_detail_history_row_$eventId"

/** A history row's *Löschen* (a purchase or a correction), by the event's id. */
fun teaDetailHistoryDeleteTag(eventId: String) = "tea_detail_history_delete_$eventId"

/** *Alle … Einträge anzeigen* below the newest rows. */
const val TEA_DETAIL_HISTORY_MORE_TAG = "tea_detail_history_more"

/** The delete question's *Löschen*. */
const val HISTORY_DELETE_CONFIRM_TAG = "tea_detail_history_delete_confirm"

/** The rows shown before *Alle … Einträge anzeigen*. */
internal const val HISTORY_VISIBLE_ROWS = 5

/**
 * VORRAT's stock history (INV-03, SCR-29, D-104): purchases with grams and price, corrections and brews, newest
 * first; the newest [HISTORY_VISIBLE_ROWS], the rest after *Alle … Einträge anzeigen*. A purchase or correction has a
 * *Löschen* that asks first; a brew has none (it leaves only with its journal entry, D-102) and opens its session
 * page through [onOpenSession] (none: the row is not tappable). TalkBack reads each row as one sentence. [onAskDelete]
 * opens [StockEntryDeleteQuestion].
 */
@Composable
internal fun StockHistory(
    history: List<StockEntry>,
    onAskDelete: (eventId: String) -> Unit,
    onOpenSession: ((sessionId: String) -> Unit)?,
) {
    if (history.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(top = UmamiSpacing.Small)) {
        val shown = if (expanded) history else history.take(HISTORY_VISIBLE_ROWS)
        shown.forEachIndexed { index, entry ->
            if (index > 0) {
                HorizontalDivider(thickness = UmamiSpacing.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
            }
            HistoryRow(
                entry = entry,
                onDelete = { onAskDelete(entry.id) },
                onOpen = entry.sessionId?.let { id -> onOpenSession?.let { open -> { open(id) } } },
            )
        }
        if (shown.size < history.size) {
            TextButton(
                onClick = { expanded = true },
                modifier = Modifier.testTag(TEA_DETAIL_HISTORY_MORE_TAG),
            ) {
                Text(pluralStringResource(R.plurals.collection_tea_history_more, history.size, history.size))
            }
        }
    }
}

/**
 * The history's delete question for the entry [askedId] while [question] is shown: *Löschen* hands its id to
 * [onDelete]. It gives way to an alert in its first frame and stays closed after it (rule 8); an entry gone meanwhile
 * closes it.
 */
@Composable
internal fun StockEntryDeleteQuestion(
    history: List<StockEntry>,
    askedId: String?,
    question: AlertGate,
    onDelete: (eventId: String) -> Unit,
) {
    AlertGated(question) {
        val entry = history.firstOrNull { it.id == askedId && it.canDelete }
        if (entry == null) {
            // Gone meanwhile (deleted elsewhere): nothing to ask.
            LaunchedEffect(Unit) { question.close() }
        } else {
            DeleteQuestion(
                entry = entry,
                onConfirm = {
                    question.close()
                    onDelete(entry.id)
                },
                onDismiss = question::close,
            )
        }
    }
}

@Composable
private fun HistoryRow(entry: StockEntry, onDelete: () -> Unit, onOpen: (() -> Unit)?) {
    val day = GermanFormat.day(entry.day)
    val description = entry.description(day)
    val openLabel = stringResource(R.string.collection_tea_history_open_session)
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = ROW_MIN_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // One node for TalkBack: its sentence, and for a brew the tap (chevron included) that opens the session.
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = ROW_MIN_HEIGHT)
                .then(
                    if (onOpen != null) {
                        Modifier.clickable(onClickLabel = openLabel, role = Role.Button, onClick = onOpen)
                    } else {
                        Modifier
                    },
                )
                .semantics(mergeDescendants = true) { contentDescription = description }
                .testTag(teaDetailHistoryRowTag(entry.id)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(vertical = UmamiSpacing.XSmall)) {
                Text(
                    text = stringResource(entry.title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = day,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(UmamiSpacing.Small))
            Text(
                text = entry.amount(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (onOpen != null) {
                Icon(
                    painterResource(UmamiIcons.ChevronRight),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = CHEVRON_PADDING).size(ICON_SIZE),
                )
            }
        }
        when {
            entry.canDelete -> IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag(teaDetailHistoryDeleteTag(entry.id)),
            ) {
                Icon(
                    painterResource(UmamiIcons.Delete),
                    contentDescription = stringResource(entry.deleteLabel, day),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(ICON_SIZE),
                )
            }

            onOpen != null -> Unit

            // A brew without a session page here: the row's end lines up with the others.
            else -> Spacer(Modifier.width(ACTION_WIDTH))
        }
    }
}

/** „Kauf löschen?" · „Vom 20. Okt. 2026. Dein Vorrat sinkt dadurch um 100 g." · *Abbrechen* / *Löschen*. */
@Composable
private fun DeleteQuestion(entry: StockEntry, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val grams = GermanFormat.gramsTrimmed(abs(entry.deltaGrams))
    val day = GermanFormat.day(entry.day)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (entry.kind == StockEntryKind.PURCHASE) {
                        R.string.collection_tea_history_delete_purchase_title
                    } else {
                        R.string.collection_tea_history_delete_correction_title
                    },
                ),
            )
        },
        text = {
            Text(
                stringResource(
                    if (entry.deltaGrams > 0) {
                        R.string.collection_tea_history_delete_lowers
                    } else {
                        R.string.collection_tea_history_delete_raises
                    },
                    day,
                    grams,
                ),
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.collection_tea_history_delete_cancel)) }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag(HISTORY_DELETE_CONFIRM_TAG)) {
                Text(stringResource(R.string.collection_tea_history_delete_confirm))
            }
        },
    )
}

private val StockEntry.title: Int
    get() = when (kind) {
        StockEntryKind.PURCHASE -> R.string.collection_tea_history_purchase
        StockEntryKind.CORRECTION -> R.string.collection_tea_history_correction
        StockEntryKind.BREW -> R.string.collection_tea_history_brew
    }

private val StockEntry.deleteLabel: Int
    get() = if (kind == StockEntryKind.PURCHASE) {
        R.string.collection_tea_history_delete_purchase
    } else {
        R.string.collection_tea_history_delete_correction
    }

/** „+100 g · 18,90 €", „−15 g", „+5 g". */
@Composable
private fun StockEntry.amount(): String {
    val grams = GermanFormat.gramsTrimmed(abs(deltaGrams))
    val signed = stringResource(
        if (deltaGrams < 0) R.string.collection_tea_history_grams_down else R.string.collection_tea_history_grams_up,
        grams,
    )
    val price = priceCents?.takeIf { kind == StockEntryKind.PURCHASE } ?: return signed
    return stringResource(R.string.collection_tea_history_with_price, signed, GermanFormat.euros(price))
}

/** TalkBack's sentence: „Kauf am 20. Okt. 2026: 100 g dazu für 18,90 €". */
@Composable
private fun StockEntry.description(day: String): String {
    val grams = GermanFormat.gramsTrimmed(abs(deltaGrams))
    return when (kind) {
        StockEntryKind.PURCHASE -> priceCents?.let {
            stringResource(
                R.string.collection_tea_history_purchase_priced_description,
                day,
                grams,
                GermanFormat.euros(it),
            )
        } ?: stringResource(R.string.collection_tea_history_purchase_description, day, grams)

        StockEntryKind.CORRECTION -> stringResource(
            if (deltaGrams < 0) {
                R.string.collection_tea_history_correction_down_description
            } else {
                R.string.collection_tea_history_correction_up_description
            },
            day,
            grams,
        )

        StockEntryKind.BREW -> stringResource(R.string.collection_tea_history_brew_description, day, grams)
    }
}

private val ROW_MIN_HEIGHT = 56.dp
private val ICON_SIZE = 20.dp
private val CHEVRON_PADDING = 14.dp
private val ACTION_WIDTH = 48.dp
