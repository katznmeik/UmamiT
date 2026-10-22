package com.umamit.feature.journal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import com.umamit.core.designsystem.component.EmptyState
import com.umamit.core.designsystem.component.OverflowSettingsAction
import com.umamit.core.designsystem.component.ScreenScaffold
import com.umamit.core.designsystem.component.SectionHeader
import com.umamit.core.designsystem.component.TeaThumbnail
import com.umamit.core.designsystem.text.clockText
import com.umamit.core.designsystem.theme.TeaTypeColors
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val TITLE_MAX_LINES = 2
private val ThumbnailSize = 48.dp
private val GERMAN_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)
private val GERMAN_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)
private val GERMAN_DAY_WITH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN)

/**
 * The journal (SCR-15 minimal, JRN-08): read-only, the in-progress session
 * pinned on top (a tap opens the timer), then the finished sessions grouped
 * by day, newest first. Search, filters, ratings and the session detail come
 * with M4.
 *
 * @param list the journal; null while the first read is running (nothing is shown, not the empty state).
 * @param thumbnail loads a tea's cover photo; tests pass a placeholder.
 */
@Composable
fun JournalScreen(
    list: JournalList?,
    onOpenSettings: () -> Unit,
    onOpenTimer: () -> Unit,
    modifier: Modifier = Modifier,
    scrollToTopRequest: Int = 0,
    thumbnail: @Composable (File) -> Painter? = { rememberAsyncImagePainter(it) },
) {
    ScreenScaffold(
        title = stringResource(R.string.journal_title),
        modifier = modifier,
        actions = { OverflowSettingsAction(onOpenSettings) },
        scrollToTopRequest = scrollToTopRequest,
        verticalArrangement = if (list?.isEmpty == false) Arrangement.Top else Arrangement.Center,
    ) {
        when {
            list == null -> Unit

            list.isEmpty -> EmptyState(
                title = stringResource(R.string.journal_empty_title),
                body = stringResource(R.string.journal_empty_body),
            )

            else -> Column(modifier = Modifier.fillMaxWidth().padding(bottom = UmamiSpacing.Large)) {
                if (list.inProgress.isNotEmpty()) {
                    Group(stringResource(R.string.journal_in_progress), list.inProgress, thumbnail, onOpenTimer)
                }
                list.days.forEach { group ->
                    Group(dayTitle(group.day), group.entries, thumbnail, onOpen = null)
                }
            }
        }
    }
}

@Composable
private fun Group(
    title: String,
    entries: List<JournalEntry>,
    thumbnail: @Composable (File) -> Painter?,
    onOpen: (() -> Unit)?,
) {
    SectionHeader(title = title, modifier = Modifier.padding(top = UmamiSpacing.Medium))
    entries.forEachIndexed { index, entry ->
        JournalRow(
            entry = entry,
            thumbnail = entry.photo?.let { thumbnail(it) },
            onClick = onOpen,
            showDivider = index < entries.lastIndex,
        )
    }
}

/** A row as in mockup v2: round photo, serif tea name with the time of day on the right, the meta line. */
@Composable
private fun JournalRow(entry: JournalEntry, thumbnail: Painter?, onClick: (() -> Unit)?, showDivider: Boolean) {
    val openLabel = stringResource(R.string.journal_open_timer)
    Column {
        Row(
            modifier = Modifier
                .then(
                    if (onClick != null) Modifier.clickable(onClickLabel = openLabel, onClick = onClick) else Modifier,
                )
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
                        text = entry.timeOfDay.format(GERMAN_TIME),
                        modifier = Modifier.alignByBaseline(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = metaLine(entry),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
internal fun metaLine(entry: JournalEntry): String = listOfNotNull(
    entry.temperatureC?.let { stringResource(R.string.journal_temperature, it) },
    entry.firstInfusionSeconds?.let(::clockText),
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
).joinToString(META_SEPARATOR)

private const val META_SEPARATOR = " · "

@Composable
@ReadOnlyComposable
private fun dayTitle(day: JournalDay): String = when (day) {
    JournalDay.Today -> stringResource(R.string.journal_today)
    JournalDay.Yesterday -> stringResource(R.string.journal_yesterday)
    is JournalDay.On -> day.date.format(if (day.thisYear) GERMAN_DAY else GERMAN_DAY_WITH_YEAR)
}

/** The leaf placeholder's colour per tea style (03 §3.1 tea-type colours, 08 §2 style keys). */
@Composable
@ReadOnlyComposable
private fun placeholderColor(styleKey: String?): Color {
    val colors: TeaTypeColors = UmamiTheme.extendedColors.teaTypes
    return when (styleKey) {
        "gyokuro" -> colors.gyokuro
        "kabusecha" -> colors.kabusecha
        "sencha", "tamaryokucha", "kamairicha" -> colors.sencha
        "fukamushi_sencha" -> colors.fukamushi
        "shincha" -> colors.shincha
        "matcha" -> colors.matcha
        "karigane", "bancha" -> colors.kukichaKarigane
        "hojicha" -> colors.hojicha
        "genmaicha" -> colors.genmaicha
        "longjing", "anji_bai_cha", "bi_luo_chun", "korean_green" -> colors.chinaKoreaGreen
        else -> colors.other
    }
}
