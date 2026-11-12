package com.umamit.feature.journal.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
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
import com.umamit.core.model.TasteAxis
import com.umamit.core.ui.AlertGated
import com.umamit.core.ui.AlertGatedDropdownMenu
import com.umamit.core.ui.GermanFormat
import com.umamit.core.ui.rememberAlertGate
import com.umamit.core.ui.rememberPhotoPainter
import com.umamit.feature.journal.R
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.umamit.core.designsystem.R as DsR

const val SESSION_TEA_TAG = "session_tea"
const val SESSION_BREW_AGAIN_TAG = "session_brew_again"
const val SESSION_RESTORE_TAG = "session_restore"
const val SESSION_MENU_TAG = "session_menu"
const val SESSION_EDIT_TAG = "session_edit"

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
    /** *Löschen*, confirmed in its dialog (JRN-10, D-102). */
    val delete: () -> Unit = {},
    /** ⋮ *Bearbeiten* (JRN-11, D-112): the session editor (SCR-17) of this session, by its id. */
    val edit: (sessionId: String) -> Unit = {},
    /** A tap on the stars (JRN-02): the new rating, null for none; saved at once. */
    val setRating: (Double?) -> Unit = {},
    /** A taste slider of the infusion at `position` released on a value (JRN-03, D-114): saved at once. */
    val setTaste: (position: Int, TasteAxis, Int) -> Unit = { _, _, _ -> },
    /** *Zurücksetzen* (JRN-03): the six taste values of the infusion at `position` are unset. */
    val clearTaste: (position: Int) -> Unit = {},
    /** The note of the infusion at `position` (JRN-05, D-114), handed over by the shared editor: saved now. */
    val setInfusionNote: (position: Int, String) -> Unit = { _, _ -> },
    /** The notes field changed (JRN-05): saved 1 s after the last key. */
    val notesChanged: (String) -> Unit = {},
    /** The notes field or the page is left (JRN-05): a pending note is saved now. */
    val flushNotes: () -> Unit = {},
    /** A chip ticked or unticked in the descriptor sheet (JRN-04): saved at once; `onRefused` runs if not stored. */
    val setDescriptor: (key: String, ticked: Boolean, onRefused: () -> Unit) -> Unit = { _, _, _ -> },
    /** *Zeiten ins Rezept übernehmen* (PRF-07, D-112): the session's changes into its recipe. */
    val acceptRecipeOffer: () -> Unit = {},
)

/**
 * The session page (SCR-16; JRN-10, D-100): the tea (a tap opens its detail), date and time,
 * method, vessel, leaf / water / ratio, and the step table (*geplant* / *tatsächlich* / °C, each step's kind; a step
 * never started reads „nicht gebrüht", Q-19). *So nochmal brühen* (BRW-06) opens the setup prefilled with the
 * session; for an archived tea it is greyed out with „Tee ist archiviert" and *Wiederherstellen*, for a deleted tea
 * there is neither the link nor the button. ⋮ *Löschen* (D-102) asks „Sitzung löschen?" and removes a finished
 * session with its stock deduction. BEWERTUNG (the stars, JRN-02) and NOTIZEN (JRN-05) sit above the step table and
 * save as they change (D-112), GESCHMACK (JRN-03) and BESCHREIBUNGEN (JRN-04) follow the step table; ⋮ *Bearbeiten*
 * opens the editor (JRN-11). The hero (JRN-07) shows the session's photos ([hero], with *+ Foto* and *Löschen*
 * through [heroActions]), else the tea's cover, else the type leaf; while [hero] is loading (null) only its tint;
 * ← and ⋮ sit over it.
 *
 * The menu and the question give way to another alert than the one they were opened in ([alertKey], rule 8); the
 * host opens the alerting timer in place of the setup or the tea during an alert.
 */
@Composable
fun SessionPageScreen(
    state: SessionPageUiState,
    modifier: Modifier = Modifier,
    actions: SessionPageActions = SessionPageActions(),
    alertKey: String? = null,
    hero: SessionHero? = SessionHero(),
    heroActions: SessionHeroActions = SessionHeroActions(),
    photo: @Composable (path: String) -> Painter? = { rememberPhotoPainter(it) },
) {
    val deleteAsk = rememberAlertGate(alertKey)
    if (state is SessionPageUiState.Ready) {
        val page = state.page
        // The hero carries ← and ⋮ on their translucent surfaces (SCR-16, D-112), as the tea detail does.
        SessionHeroLayout(
            onBack = actions.back,
            menu = if (page.inProgress) {
                null
            } else {
                { SessionMenu(alertKey, onEdit = { actions.edit(page.sessionId) }, onDelete = deleteAsk::open) }
            },
            hero = {
                SessionHeroPager(hero, page.styleKey, canAdd = page.canTaste, heroActions, alertKey, photo)
                Spacer(Modifier.height(UmamiSpacing.Small))
            },
            modifier = modifier,
        ) {
            SessionContent(page, actions, alertKey)
        }
    } else {
        ScreenScaffold(
            title = stringResource(R.string.session_title),
            modifier = modifier,
            navigationIcon = { BackAction(actions.back) },
            verticalArrangement = Arrangement.Center,
        ) {
            if (state == SessionPageUiState.NotFound) {
                EmptyState(
                    title = stringResource(R.string.session_not_found_title),
                    body = stringResource(R.string.session_not_found_body),
                )
            }
        }
    }
    AlertGated(deleteAsk) {
        DeleteDialog(
            onDelete = {
                deleteAsk.close()
                actions.delete()
            },
            onDismiss = deleteAsk::close,
        )
    }
}

/** ⋮ with *Bearbeiten* (JRN-11) above *Löschen* (JRN-10, D-102); the menu gives way to another alert (rule 8). */
@Composable
private fun SessionMenu(alertKey: String?, onEdit: () -> Unit, onDelete: () -> Unit) {
    val menu = rememberAlertGate(alertKey)
    Box {
        IconButton(onClick = menu::open, modifier = Modifier.testTag(SESSION_MENU_TAG)) {
            Icon(
                painterResource(UmamiIcons.MoreVert),
                contentDescription = stringResource(DsR.string.ds_more_options),
            )
        }
        AlertGatedDropdownMenu(menu) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.session_edit)) },
                onClick = {
                    menu.close()
                    onEdit()
                },
                modifier = Modifier.testTag(SESSION_EDIT_TAG),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.session_delete)) },
                onClick = {
                    menu.close()
                    onDelete()
                },
            )
        }
    }
}

/** „Sitzung löschen?" · *Abbrechen* / *Löschen* (03 SCR-16, D-102). */
@Composable
private fun DeleteDialog(onDelete: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.session_delete_title)) },
        text = { Text(stringResource(R.string.session_delete_body)) },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.session_delete_cancel)) }
        },
        confirmButton = {
            TextButton(onClick = onDelete) { Text(stringResource(R.string.session_delete_confirm)) }
        },
    )
}

@Composable
private fun SessionContent(page: SessionPage, actions: SessionPageActions, alertKey: String?) {
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
            page.ratioPer100Ml?.let { stringResource(R.string.session_ratio_value, GermanFormat.gramsOneDecimal(it)) }
                ?: unknown(),
        )
        if (page.canTaste) TastingInputs(page, actions)
        SectionHeader(
            title = stringResource(R.string.session_steps),
            modifier = Modifier.padding(top = UmamiSpacing.Medium),
        )
        SessionSteps(page.steps)
        page.recipeOffer?.let { SessionRecipeOffer(it, actions.acceptRecipeOffer) }
        if (page.canTaste) TasteSection(page, actions)
        if (page.canTaste) DescriptorsSection(page, actions, alertKey)
        BrewAgain(page, actions)
    }
}

/** The tea's name in the display font, a link to its detail while the tea is in the collection or archived. */
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
                GermanFormat.day(page.startedAt),
                GermanFormat.time(page.startedAt),
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
private fun grams(value: Double): String = stringResource(R.string.session_grams, GermanFormat.gramsOneDecimal(value))

@Composable
@ReadOnlyComposable
internal fun unknown(): String = stringResource(R.string.session_unknown)
