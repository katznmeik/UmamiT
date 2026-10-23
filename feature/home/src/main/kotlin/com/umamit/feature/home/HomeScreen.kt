package com.umamit.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.component.EmptyState
import com.umamit.core.designsystem.component.FinishActiveSessionDialog
import com.umamit.core.designsystem.component.PrimaryActionCard
import com.umamit.core.designsystem.component.ReliabilityBanner
import com.umamit.core.designsystem.component.ScreenScaffold
import com.umamit.core.designsystem.component.SectionHeader
import com.umamit.core.designsystem.component.SettingsGearAction
import com.umamit.core.designsystem.component.TeaListItem
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.text.clockText
import com.umamit.core.designsystem.theme.UmamiShape
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.TimerPhase
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.ui.rememberPhotoPainter
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal const val HOME_ACTIVE_CARD_TAG = "home_active_card"
internal const val HOME_HEADER_HEIGHT_DP = 120

/**
 * Start (SCR-02): the header illustration, *Aufguss starten* (or *Aufguss
 * fortsetzen* with a live countdown while a timer is active, GEN-06),
 * *Schnelltimer*, *Nochmal brühen*, *Zuletzt gebrüht* and *Favoriten*; with
 * an empty collection the calm empty state (GEN-09). The second-session
 * question never covers an alert: the view model closes it when one starts.
 *
 * @param nowElapsedMs the monotonic clock of the timer's deadlines.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    nowElapsedMs: () -> Long,
    modifier: Modifier = Modifier,
    actions: HomeActions = HomeActions(),
    scrollToTopRequest: Int = 0,
) {
    ScreenScaffold(
        title = stringResource(R.string.home_title),
        modifier = modifier,
        actions = { SettingsGearAction(actions.openSettings) },
        scrollToTopRequest = scrollToTopRequest,
        verticalArrangement = Arrangement.Top,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (state.reliabilityProblem) {
                ReliabilityBanner(
                    message = stringResource(R.string.home_reliability),
                    onFix = actions.fixReliability,
                    modifier = Modifier.padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.XSmall),
                )
            }
            // The empty state brings its own landscape; one is enough.
            val empty = state.collectionEmpty && state.brewAgain.isEmpty() && state.recent.isEmpty()
            if (!empty) Header()
            val active = state.active
            if (active != null) {
                ActiveCard(active, nowElapsedMs, actions.openTimer)
            } else if (!state.collectionEmpty && !state.loading) {
                PrimaryActionCard(
                    title = stringResource(R.string.home_start_brew),
                    subtitle = stringResource(R.string.home_start_brew_subtitle),
                    onClick = actions.startBrew,
                    modifier = Modifier.padding(horizontal = UmamiSpacing.SideMargin),
                )
            }
            QuickTimerEntry(actions.quickTimer)
            if (empty) {
                EmptyState(
                    title = stringResource(R.string.home_empty_title),
                    body = stringResource(R.string.home_empty_body),
                    actionLabel = stringResource(R.string.home_empty_action),
                    onAction = actions.openCatalog,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Lists(state, actions)
            }
        }
        // Alerts first (rule 8): never over an alert, by construction (the view model also closes it).
        val question = state.question?.takeIf { state.alertKey == null }
        if (question != null) {
            FinishActiveSessionDialog(
                teaName = question.teaName,
                onRestart = actions.confirmRestart,
                onCancel = actions.dismissQuestion,
            )
        }
    }
}

@Composable
private fun Lists(state: HomeUiState, actions: HomeActions) = Column(modifier = Modifier.fillMaxWidth()) {
    if (state.brewAgain.isNotEmpty()) {
        SectionHeader(stringResource(R.string.home_brew_again), Modifier.padding(top = UmamiSpacing.Large))
        BrewAgainRow(state.brewAgain, actions.brewAgain)
    }
    if (state.recent.isNotEmpty()) {
        SectionHeader(
            stringResource(R.string.home_recent),
            Modifier.padding(top = UmamiSpacing.Large),
            onShowAll = actions.showJournal,
        )
        state.recent.forEachIndexed { i, brew ->
            TeaListItem(
                title = brew.teaName,
                meta = recentMeta(brew),
                thumbnail = rememberPhotoPainter(brew.photoPath),
                showDivider = i < state.recent.lastIndex,
            )
        }
    }
    if (state.favorites.isNotEmpty()) {
        SectionHeader(stringResource(R.string.home_favorites), Modifier.padding(top = UmamiSpacing.Large))
        state.favorites.forEachIndexed { i, tea ->
            TeaListItem(
                title = tea.name,
                meta = "",
                onClick = { actions.openSetup(tea.id) },
                thumbnail = rememberPhotoPainter(tea.photoPath),
                showDivider = i < state.favorites.lastIndex,
            )
        }
    }
    Spacer(Modifier.height(UmamiSpacing.Large))
}

/**
 * The header illustration A3, no text (D-035): edge to edge, its paper matched to the theme's
 * background so it has no visible border; cropped to the band, keeping the mountains centred.
 */
@Composable
private fun Header() {
    Image(
        painter = painterResource(if (UmamiTheme.isDark) UmamiIcons.HomeHeaderDark else UmamiIcons.HomeHeaderLight),
        contentDescription = null,
        modifier = Modifier
            .fillMaxWidth()
            .height(HOME_HEADER_HEIGHT_DP.dp)
            .padding(vertical = UmamiSpacing.Small),
        contentScale = ContentScale.Crop,
    )
}

/** *Aufguss fortsetzen* with the tea, the step and a countdown that ticks once a second (GEN-06). */
@Composable
private fun ActiveCard(active: TimerUiState.Active, nowElapsedMs: () -> Long, onClick: () -> Unit) {
    val now = rememberTickingNow(active, nowElapsedMs)
    val name = active.teaName ?: stringResource(R.string.home_active_quick)
    val time = { seconds: Long -> clockText(seconds.toInt()) }
    val subtitle = when (active.phase) {
        TimerPhase.READY -> stringResource(R.string.home_active_ready, name, active.stepIndex + 1)

        TimerPhase.RUNNING -> stringResource(
            R.string.home_active_running,
            name,
            time(active.display(now).remainingSeconds),
        )

        TimerPhase.PAUSED -> stringResource(
            R.string.home_active_paused,
            name,
            time(active.display(now).remainingSeconds),
        )

        TimerPhase.ALERTING -> stringResource(R.string.home_active_alerting, name)

        TimerPhase.FINISHED -> stringResource(R.string.home_active_finished, name)
    }
    PrimaryActionCard(
        title = stringResource(R.string.home_continue_brew),
        subtitle = subtitle,
        onClick = onClick,
        icon = painterResource(UmamiIcons.Timer),
        modifier = Modifier
            .padding(horizontal = UmamiSpacing.SideMargin)
            .testTag(HOME_ACTIVE_CARD_TAG),
    )
}

/** The monotonic time, read again at each whole second of the countdown or the overtime while composed. */
@Composable
private fun rememberTickingNow(active: TimerUiState.Active, nowElapsedMs: () -> Long): Long {
    var now by remember(active) { mutableLongStateOf(nowElapsedMs()) }
    val clock by rememberUpdatedState(nowElapsedMs)
    val deadline = active.deadlineElapsedMs
    if (deadline != null && (active.phase == TimerPhase.RUNNING || active.phase == TimerPhase.ALERTING)) {
        LaunchedEffect(active) {
            while (true) {
                now = clock()
                delay(MS_PER_SECOND - Math.floorMod(now - deadline, MS_PER_SECOND) + 1)
            }
        }
    }
    return now
}

private const val MS_PER_SECOND = 1_000L

@Composable
private fun QuickTimerEntry(onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = UmamiSpacing.Small, vertical = UmamiSpacing.XSmall),
    ) {
        Icon(painterResource(UmamiIcons.Timer), contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(UmamiSpacing.XSmall))
        Text(stringResource(R.string.home_quick_timer), style = MaterialTheme.typography.labelLarge)
    }
}

private val TileSize = 72.dp

/** *Nochmal brühen*: up to three round tea tiles; one tap opens the timer in READY (BRW-11). */
@Composable
private fun BrewAgainRow(teas: List<HomeTea>, onBrewAgain: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.XSmall),
        horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.Small),
    ) {
        teas.forEach { tea ->
            val description = stringResource(R.string.home_brew_again_description, tea.name)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(UmamiShape.Card)
                    .clickable(role = Role.Button) { onBrewAgain(tea.id) }
                    .semantics(mergeDescendants = true) { contentDescription = description }
                    .padding(vertical = UmamiSpacing.XSmall),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TeaTile(tea.photoPath)
                Spacer(Modifier.height(UmamiSpacing.XSmall))
                Text(
                    text = tea.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = TILE_NAME_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Keep the tiles their size when fewer than three teas were brewed.
        repeat(BREW_AGAIN_SLOTS - teas.size) { Spacer(Modifier.weight(1f)) }
    }
}

private const val BREW_AGAIN_SLOTS = 3
private const val TILE_NAME_MAX_LINES = 3
private const val TILE_PLACEHOLDER_ALPHA = 0.22f

@Composable
private fun TeaTile(photoPath: String?) {
    val painter = rememberPhotoPainter(photoPath)
    val leaf = UmamiTheme.extendedColors.teaTypes.sencha
    if (painter != null) {
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier
                .size(TileSize)
                .clip(UmamiShape.Thumbnail),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = Modifier
                .size(TileSize)
                .clip(UmamiShape.Thumbnail)
                .background(leaf.copy(alpha = TILE_PLACEHOLDER_ALPHA)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(UmamiIcons.Leaf), contentDescription = null, tint = leaf)
        }
    }
}

private val DATE = DateTimeFormatter.ofPattern("d. MMMM", Locale.GERMAN)
private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3_600

/** „8. Juni · 1 Min · 65 °C" (03 §3.8, 05 §7: whole hours „4 Std", whole minutes „3 Min" in lists, else „0:45"). */
@Composable
private fun recentMeta(brew: RecentBrew): String {
    val date = DATE.format(Instant.ofEpochMilli(brew.startedAtMs).atZone(zoneOf(brew.zoneId)))
    val seconds = brew.firstStepSeconds
    val duration = when {
        seconds == null -> null
        seconds % SECONDS_PER_HOUR == 0 -> stringResource(R.string.home_hours_short, seconds / SECONDS_PER_HOUR)
        seconds % SECONDS_PER_MINUTE == 0 -> stringResource(R.string.home_minutes_short, seconds / SECONDS_PER_MINUTE)
        else -> clockText(seconds)
    }
    val temp = brew.tempC
    return when {
        duration == null -> date
        temp == null -> stringResource(R.string.home_recent_meta_no_temperature, date, duration)
        else -> stringResource(R.string.home_recent_meta, date, duration, temp)
    }
}

private fun zoneOf(id: String): ZoneId = runCatching { ZoneId.of(id) }.getOrDefault(ZoneId.systemDefault())
