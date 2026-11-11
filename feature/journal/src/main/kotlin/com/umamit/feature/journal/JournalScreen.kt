package com.umamit.feature.journal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import com.umamit.core.designsystem.component.EmptyState
import com.umamit.core.designsystem.component.OverflowSettingsAction
import com.umamit.core.designsystem.component.ScreenScaffold
import com.umamit.core.designsystem.component.SectionHeader
import com.umamit.core.designsystem.component.TeaThumbnail
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.text.clockText
import com.umamit.core.designsystem.text.ratingText
import com.umamit.core.designsystem.text.spokenDuration
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.ui.GermanFormat
import java.io.File

private const val TITLE_MAX_LINES = 2
private val ThumbnailSize = 48.dp
private val BottomClearance = 72.dp

/** The journal's list, for tests. */
const val JOURNAL_LIST_TAG = "journal_list"

/**
 * The journal (SCR-15, JRN-08, JRN-09): the in-progress session pinned on top (a tap opens the timer), then the
 * finished sessions grouped by day, newest first; a tap on one opens its page (SCR-16, D-100). Rated sessions show
 * „★ 4,5". The search „Tees, Notizen …" and the filter sheet narrow the rows in memory, keep the day groups and
 * hide the pinned session; without a match A10 offers *Filter zurücksetzen*. The empty journal shows A6. The list is
 * lazy, so 10 000 sessions scroll smoothly (NFR-PERF-03).
 *
 * @param list the journal, unfiltered; null while the first read is running (nothing is shown, not the empty state).
 * @param filter the search and filters; [onFilterChange] reports a change (the host keeps it, JRN-09).
 * @param alerting an alert plays: the filter sheet gives way (rule 8).
 * @param thumbnail loads a tea's cover photo; tests pass a placeholder.
 * @param onOpenSession a past entry's tap: its session page (SCR-16) by the session's id.
 */
@Composable
fun JournalScreen(
    list: JournalList?,
    onOpenSettings: () -> Unit,
    onOpenTimer: () -> Unit,
    modifier: Modifier = Modifier,
    scrollToTopRequest: Int = 0,
    thumbnail: @Composable (File) -> Painter? = { rememberAsyncImagePainter(it) },
    onOpenSession: (sessionId: String) -> Unit = {},
    filter: JournalFilter = JournalFilter(),
    onFilterChange: (JournalFilter) -> Unit = {},
    alerting: Boolean = false,
) {
    ScreenScaffold(
        title = stringResource(R.string.journal_title),
        modifier = modifier,
        actions = { OverflowSettingsAction(onOpenSettings) },
        scrollable = false,
        verticalArrangement = Arrangement.Top,
    ) {
        when {
            list == null -> Unit

            list.isEmpty -> EmptyJournal()

            else -> JournalContent(
                list = list,
                filter = filter,
                onFilterChange = onFilterChange,
                alerting = alerting,
                scrollToTopRequest = scrollToTopRequest,
                thumbnail = thumbnail,
                onOpenTimer = onOpenTimer,
                onOpenSession = onOpenSession,
            )
        }
    }
}

/** No session yet: A6 alone, centred while it fits, scrollable at a large font scale. */
@Composable
private fun EmptyJournal() {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(bottom = BottomClearance),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EmptyState(
                title = stringResource(R.string.journal_empty_title),
                body = stringResource(R.string.journal_empty_body),
                illustration = painterResource(
                    if (UmamiTheme.isDark) UmamiIcons.JournalEmptyDark else UmamiIcons.JournalEmptyLight,
                ),
                illustrationTint = null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
@Suppress("LongParameterList") // the screen's state and its three callbacks, passed through
private fun JournalContent(
    list: JournalList,
    filter: JournalFilter,
    onFilterChange: (JournalFilter) -> Unit,
    alerting: Boolean,
    scrollToTopRequest: Int,
    thumbnail: @Composable (File) -> Painter?,
    onOpenTimer: () -> Unit,
    onOpenSession: (String) -> Unit,
) {
    val visible = remember(list, filter) { list.filtered(filter) }
    val teas = remember(list) { list.teaOptions() }
    val listState = rememberLazyListState()
    // A counter, as in ScreenScaffold: only an increase while composed scrolls; never replayed (D-049).
    var handled by remember { mutableIntStateOf(scrollToTopRequest) }
    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > handled) {
            handled = scrollToTopRequest
            listState.animateScrollToItem(0)
        }
    }
    var sheetOpen by remember { mutableStateOf(false) }
    LaunchedEffect(alerting) { if (alerting) sheetOpen = false }
    val openLabel = stringResource(R.string.journal_open_session)
    val timerLabel = stringResource(R.string.journal_open_timer)
    val inProgressTitle = stringResource(R.string.journal_in_progress)
    val teaName = teas.firstOrNull { it.key == filter.teaKey }?.name ?: stringResource(R.string.journal_filter_tea)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(JOURNAL_LIST_TAG),
        state = listState,
        contentPadding = PaddingValues(bottom = UmamiSpacing.Large),
    ) {
        item(key = "search") { JournalSearchRow(filter, onFilterChange) { sheetOpen = true } }
        item(key = "chips") { JournalFilterChips(filter, teaName, onFilterChange) }
        if (filter.isActive) {
            item(key = "count") { ResultCount(visible.sessionCount) }
        }
        if (visible.days.isEmpty()) {
            item(key = "no_match") {
                EmptyState(
                    title = stringResource(R.string.journal_no_match_title),
                    body = null,
                    actionLabel = stringResource(R.string.journal_no_match_action),
                    onAction = { onFilterChange(JournalFilter()) },
                    illustration = painterResource(
                        if (UmamiTheme.isDark) UmamiIcons.NoResultsDark else UmamiIcons.NoResultsLight,
                    ),
                    illustrationTint = null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        groups("running", { inProgressTitle }, visible.inProgress, thumbnail, timerLabel) { onOpenTimer() }
        visible.days.forEach { group ->
            val key = "day-${group.entries.first().startedAt.toLocalDate()}"
            groups(key, { dayTitle(group.day) }, group.entries, thumbnail, openLabel) { onOpenSession(it.sessionId) }
        }
    }
    if (sheetOpen && !alerting) {
        JournalFilterSheet(
            filter = filter,
            teas = teas,
            onFilterChange = onFilterChange,
            onDismissRequest = { sheetOpen = false },
        )
    }
}

/**
 * The count of matching sessions, announced by TalkBack after a search or filter change (JRN-09). It takes no
 * space: the text is for the live region only.
 */
@Composable
private fun ResultCount(count: Int) {
    val text = pluralStringResource(R.plurals.journal_result_count, count, count)
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = text
            },
    )
}

private fun LazyListScope.groups(
    keyPrefix: String,
    title: @Composable () -> String,
    entries: List<JournalEntry>,
    thumbnail: @Composable (File) -> Painter?,
    openLabel: String,
    onOpen: (JournalEntry) -> Unit,
) {
    if (entries.isEmpty()) return
    item(key = "header-$keyPrefix") {
        SectionHeader(title = title(), modifier = Modifier.padding(top = UmamiSpacing.Medium))
    }
    itemsIndexed(entries, key = { _, entry -> entry.sessionId }) { index, entry ->
        JournalRow(
            entry = entry,
            thumbnail = entry.photo?.let { thumbnail(it) },
            openLabel = openLabel,
            onClick = { onOpen(entry) },
            showDivider = index < entries.lastIndex,
        )
    }
}

/**
 * A row as in mockup v2: round photo, display-font tea name with the time of day on the right, the meta line and,
 * for a rated session, „★ 4,5" (JRN-08, D-112). A session without notes carries no mark.
 */
@Composable
private fun JournalRow(
    entry: JournalEntry,
    thumbnail: Painter?,
    openLabel: String,
    onClick: () -> Unit,
    showDivider: Boolean,
) {
    Column {
        Row(
            modifier = Modifier
                .clickable(onClickLabel = openLabel, onClick = onClick)
                .semantics(mergeDescendants = true) {}
                .fillMaxWidth()
                .defaultMinSize(minHeight = 72.dp)
                .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TeaThumbnail(thumbnail = thumbnail, placeholderColor = placeholderColor(entry.styleKey))
            Spacer(Modifier.width(UmamiSpacing.Medium))
            Column(modifier = Modifier.weight(1f)) {
                Row {
                    Text(
                        text = entry.teaName,
                        modifier = Modifier
                            .weight(1f)
                            .alignByBaseline(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = TITLE_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(UmamiSpacing.Small))
                    Text(
                        text = GermanFormat.time(entry.timeOfDay),
                        modifier = Modifier.alignByBaseline(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val spokenMeta = spokenMetaLine(entry)
                Text(
                    text = metaLine(entry),
                    modifier = Modifier.semantics { contentDescription = spokenMeta },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                entry.rating?.let { rating ->
                    val value = ratingText(rating.toFloat())
                    val spokenRating = stringResource(R.string.journal_rating_description, value)
                    Text(
                        text = stringResource(R.string.journal_rating, value),
                        modifier = Modifier.semantics { contentDescription = spokenRating },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(
                    start = UmamiSpacing.SideMargin + ThumbnailSize + UmamiSpacing.Medium,
                    end = UmamiSpacing.SideMargin,
                ),
                thickness = UmamiSpacing.Hairline,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/** „55 °C · 1:00 · 3 Aufgüsse", or „… · 2 von 3 Aufgüssen" for an early finish (JRN-08, TMR-12). */
@Composable
@ReadOnlyComposable
internal fun metaLine(entry: JournalEntry): String = metaParts(entry, entry.firstInfusionSeconds?.let(::clockText))
    .joinToString(stringResource(R.string.journal_meta_separator))

/** The meta line for TalkBack: „55 °C, 1 Minute, 3 Aufgüsse", so „1:00" is not read as a time of day. */
@Composable
@ReadOnlyComposable
private fun spokenMetaLine(entry: JournalEntry): String =
    metaParts(entry, entry.firstInfusionSeconds?.let { spokenDuration(it) })
        .joinToString(stringResource(R.string.journal_spoken_separator))

@Composable
@ReadOnlyComposable
private fun metaParts(entry: JournalEntry, firstInfusion: String?): List<String> = listOfNotNull(
    entry.temperatureC?.let { stringResource(R.string.journal_temperature, it) },
    firstInfusion,
    if (entry.finishedEarly) {
        pluralStringResource(
            R.plurals.journal_infusions_of,
            entry.plannedInfusions,
            entry.infusions,
            entry.plannedInfusions,
        )
    } else {
        pluralStringResource(R.plurals.journal_infusions, entry.infusions, entry.infusions)
    },
)

@Composable
@ReadOnlyComposable
private fun dayTitle(day: JournalDay): String = when (day) {
    JournalDay.Today -> stringResource(R.string.journal_today)
    JournalDay.Yesterday -> stringResource(R.string.journal_yesterday)
    is JournalDay.On -> GermanFormat.weekdayDay(day.date, withYear = !day.thisYear)
}

/** The leaf placeholder's colour per tea style (03 §3.1 tea-type colours, 08 §2 style keys). */
@Composable
@ReadOnlyComposable
private fun placeholderColor(styleKey: String?): Color = UmamiTheme.extendedColors.teaTypes.forStyle(styleKey)
