package com.umamit.feature.journal.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.component.BackAction
import com.umamit.core.designsystem.component.EmptyState
import com.umamit.core.designsystem.component.PillButton
import com.umamit.core.designsystem.component.ScreenScaffold
import com.umamit.core.designsystem.component.SectionHeader
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.model.BrewMethod
import com.umamit.feature.journal.R
import java.time.format.DateTimeFormatter
import java.util.Locale

const val SESSION_TEA_TAG = "session_tea"
const val SESSION_BREW_AGAIN_TAG = "session_brew_again"
const val SESSION_RESTORE_TAG = "session_restore"

private val GERMAN_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d. MMM yyyy", Locale.GERMAN)
private val GERMAN_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)

private val CHEVRON_SIZE = 20.dp

/** What a tap on the session page does. */
class SessionPageActions(
    val back: () -> Unit = {},
    /** The tea's name: its detail (SCR-09) by its id. */
    val openTea: (teaId: String) -> Unit = {},
    /** *So nochmal brühen* (BRW-06): the setup prefilled with this session, by its id. */
    val brewAgain: (sessionId: String) -> Unit = {},
    /** *Wiederherstellen* of the archived tea. */
    val restore: () -> Unit = {},
)

/**
 * The session page (SCR-16), read-only in v0.4.0 (JRN-10, D-100): the tea (a tap opens its detail), date and time,
 * method, vessel, leaf / water / ratio, and the step table (*geplant* / *tatsächlich* / °C, each step's kind; a step
 * never started reads „nicht gebrüht", Q-19). *So nochmal brühen* (BRW-06) opens the setup prefilled with the
 * session; for an archived tea it is greyed out with „Tee ist archiviert" and *Wiederherstellen*, for a deleted tea
 * there is neither the link nor the button. Rating, taste, notes, photos, *Bearbeiten* and *Löschen* come with M4.
 *
 * Nothing here covers the screen: the host opens the alerting timer in place of the setup or the tea during an alert
 * (rule 8).
 */
@Composable
fun SessionPageScreen(
    state: SessionPageUiState,
    modifier: Modifier = Modifier,
    actions: SessionPageActions = SessionPageActions(),
) {
    ScreenScaffold(
        title = stringResource(R.string.session_title),
        modifier = modifier,
        navigationIcon = { BackAction(actions.back) },
        verticalArrangement = if (state is SessionPageUiState.Ready) Arrangement.Top else Arrangement.Center,
    ) {
        when (state) {
            SessionPageUiState.Loading -> Unit

            SessionPageUiState.NotFound -> EmptyState(
                title = stringResource(R.string.session_not_found_title),
                body = stringResource(R.string.session_not_found_body),
            )

            is SessionPageUiState.Ready -> SessionContent(state.page, actions)
        }
    }
}

@Composable
private fun SessionContent(page: SessionPage, actions: SessionPageActions) {
    Column(modifier = Modifier.fillMaxWidth()) {
        TeaHeader(page, onOpenTea = page.teaId?.let { id -> { actions.openTea(id) } })
        Spacer(Modifier.height(UmamiSpacing.Small))
        DetailRow(stringResource(R.string.session_method), methodLabel(page.method))
        DetailRow(
            stringResource(R.string.session_vessel),
            page.vesselName?.trim() ?: stringResource(R.string.session_vessel_none),
        )
        DetailRow(stringResource(R.string.session_leaf), page.leafGrams?.let { grams(it) } ?: unknown())
        DetailRow(
            stringResource(R.string.session_water),
            page.waterMl?.let { stringResource(R.string.session_ml, it) } ?: unknown(),
        )
        DetailRow(
            stringResource(R.string.session_ratio),
            page.ratioPer100Ml?.let { stringResource(R.string.session_ratio_value, oneDecimal(it)) } ?: unknown(),
        )
        SectionHeader(
            title = stringResource(R.string.session_steps),
            modifier = Modifier.padding(top = UmamiSpacing.Medium),
        )
        SessionSteps(page.steps)
        BrewAgain(page, actions)
    }
}

/** The tea's name in the serif, a link to its detail while the tea is in the collection or archived. */
@Composable
private fun TeaHeader(page: SessionPage, onOpenTea: (() -> Unit)?) {
    Column(modifier = Modifier.padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.XSmall)) {
        Row(
            modifier = Modifier
                .then(
                    if (onOpenTea != null) {
                        Modifier.clickable(
                            role = Role.Button,
                            onClickLabel = stringResource(R.string.session_open_tea),
                            onClick = onOpenTea,
                        )
                    } else {
                        Modifier
                    },
                )
                .defaultMinSize(minHeight = UmamiSpacing.MinTouchTarget)
                .testTag(SESSION_TEA_TAG),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = page.teaName,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (onOpenTea != null) {
                Spacer(Modifier.width(UmamiSpacing.XSmall))
                Icon(
                    painterResource(UmamiIcons.ChevronRight),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(CHEVRON_SIZE),
                )
            }
        }
        page.teaSecondaryName?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(
                R.string.session_started,
                page.startedAt.format(GERMAN_DATE),
                page.startedAt.format(GERMAN_TIME),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** „Methode    Kyusu": the label on the left, the value on the right, wrapping under a large font. */
@Composable
private fun DetailRow(label: String, value: String) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.XSmall),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = UmamiSpacing.Medium),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.tabular(),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * *So nochmal brühen* (BRW-06): enabled for a tea of the collection; greyed out with „Tee ist archiviert" and
 * *Wiederherstellen* for an archived one; absent for a deleted one.
 */
@Composable
private fun BrewAgain(page: SessionPage, actions: SessionPageActions) {
    if (page.tea == SessionTea.DELETED) {
        Spacer(Modifier.height(UmamiSpacing.Large))
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.Large),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PillButton(
            text = stringResource(R.string.session_brew_again),
            onClick = { actions.brewAgain(page.sessionId) },
            enabled = page.canBrewAgain,
            icon = painterResource(UmamiIcons.Play),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(SESSION_BREW_AGAIN_TAG),
        )
        if (page.tea == SessionTea.ARCHIVED) {
            FlowRow(
                modifier = Modifier.padding(top = UmamiSpacing.XSmall),
                horizontalArrangement = Arrangement.Center,
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.session_tea_archived),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = actions.restore, modifier = Modifier.testTag(SESSION_RESTORE_TAG)) {
                    Text(stringResource(R.string.session_restore))
                }
            }
        }
    }
}

@Composable
@ReadOnlyComposable
private fun methodLabel(method: BrewMethod): String = stringResource(
    when (method) {
        BrewMethod.KYUSU -> R.string.session_method_kyusu
        BrewMethod.GONGFU -> R.string.session_method_gongfu
        BrewMethod.WESTERN -> R.string.session_method_western
        BrewMethod.MATCHA_USUCHA -> R.string.session_method_usucha
        BrewMethod.MATCHA_KOICHA -> R.string.session_method_koicha
        BrewMethod.COLD_BREW -> R.string.session_method_cold_brew
        BrewMethod.ICE_BREW -> R.string.session_method_ice_brew
        BrewMethod.CUSTOM -> R.string.session_method_custom
    },
)

@Composable
@ReadOnlyComposable
private fun grams(value: Double): String = stringResource(R.string.session_grams, oneDecimal(value))

@Composable
@ReadOnlyComposable
internal fun unknown(): String = stringResource(R.string.session_unknown)

/** „4,5", „4,0": one decimal, German (as the setup shows leaf and ratio). */
private fun oneDecimal(value: Double): String = "%.1f".format(Locale.GERMAN, value)
