package com.umamit.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umamit.core.designsystem.component.BackAction
import com.umamit.core.designsystem.component.ScreenScaffold
import com.umamit.core.designsystem.component.SectionHeader
import com.umamit.core.designsystem.component.StatusCheck
import com.umamit.core.designsystem.component.StatusCheckRow
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiShape
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.model.AlertLength
import com.umamit.core.model.UserSettings
import com.umamit.core.timer.alert.AlertSound
import com.umamit.core.timer.alert.PreAlertSound
import com.umamit.core.timer.reliability.CheckState
import com.umamit.core.timer.reliability.ReliabilityCheck
import com.umamit.core.timer.reliability.ReliabilityStatus
import com.umamit.core.timer.reliability.startFirstIntent
import com.umamit.core.ui.rememberNotificationPermissionAction

/** Test tags of SCR-25. */
object SettingsTags {
    const val ABOUT = "SettingsAbout"
    const val RELIABILITY = "SettingsReliability"
    const val CATALOG = "SettingsCatalog"
}

/** The dialogs of SCR-25; saveable by name. */
internal enum class SettingsDialog { Tone, PreAlert, PreAlertSound, AlertLength }

/** What the rows of SCR-25 do. */
class SettingsActions(
    val fix: (ReliabilityCheck) -> Unit = {},
    val setTone: (String) -> Unit = {},
    /** *Anhören*: the picked tone once (SET-09). */
    val previewTone: () -> Unit = {},
    /** The picker closes, or an alert removes it: the preview stops. */
    val stopPreview: () -> Unit = {},
    val setPreAlert: (Int) -> Unit = {},
    /** SET-26 *Vorwarnton*: the stored key of the picked sound. */
    val setPreAlertSound: (String) -> Unit = {},
    /** *Anhören* in the *Vorwarnton* picker: the picked sound once (SET-26). */
    val previewPreAlertSound: () -> Unit = {},
    val setVibration: (Boolean) -> Unit = {},
    val setAlertLength: (AlertLength) -> Unit = {},
    val setKeepScreenOn: (Boolean) -> Unit = {},
    val setDim: (Boolean) -> Unit = {},
    /** SET-22 *Katalog: Fotos laden*. */
    val setCatalogPhotos: (Boolean) -> Unit = {},
    val testAlert: () -> Unit = {},
    /** *Stopp*: ends a running *Alarm testen*. */
    val stopTest: () -> Unit = {},
    val openAbout: () -> Unit = {},
    /** *Jetzt aktualisieren* (SET-23). */
    val refreshCatalog: () -> Unit = {},
)

/**
 * Settings (SCR-25) with their view model: the checks are read again on every resume; a test
 * alert stops when the screen leaves. [alertKey] is the alert playing now: no dialog is composed
 * over it (rule 8).
 */
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    alertKey: String?,
    modifier: Modifier = Modifier,
    scrollToTopRequest: Int = 0,
    viewModel: SettingsViewModel = hiltViewModel(),
    catalogViewModel: CatalogSettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val settings by viewModel.current.collectAsStateWithLifecycle()
    val catalog by catalogViewModel.state.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val saveFailed by viewModel.saveFailed.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    DisposableEffect(viewModel) {
        onDispose {
            viewModel.stopTest()
            viewModel.stopPreview()
        }
    }
    val requestNotifications = rememberNotificationPermissionAction(
        onResult = viewModel::refresh,
        openSettings = { context.startFirstIntent(viewModel.open(ReliabilityCheck.Notifications)) },
    )
    SettingsScreen(
        settings = settings ?: UserSettings(),
        status = status,
        alerting = alertKey != null,
        saveFailed = saveFailed,
        onBack = onBack,
        actions = SettingsActions(
            fix = {
                if (it == ReliabilityCheck.Notifications) {
                    requestNotifications()
                } else {
                    context.startFirstIntent(viewModel.open(it))
                }
            },
            setTone = { key -> viewModel.change { it.copy(alertSound = key) } },
            previewTone = { viewModel.previewTone((settings ?: UserSettings()).alertSound) },
            stopPreview = viewModel::stopPreview,
            setPreAlert = { seconds -> viewModel.change { it.copy(preAlertSeconds = seconds) } },
            setPreAlertSound = { key -> viewModel.change { it.copy(preAlertSound = key) } },
            previewPreAlertSound = { viewModel.previewPreAlertSound((settings ?: UserSettings()).preAlertSound) },
            setVibration = { on -> viewModel.change { it.copy(vibration = on) } },
            setAlertLength = { length -> viewModel.change { it.copy(alertLength = length) } },
            setKeepScreenOn = { on -> viewModel.change { it.copy(keepScreenOn = on) } },
            setDim = { on -> viewModel.change { it.copy(dimWhileSteeping = on) } },
            setCatalogPhotos = { on -> viewModel.change { it.copy(catalogDownloadPhotos = on) } },
            testAlert = viewModel::testAlert,
            stopTest = viewModel::stopTest,
            openAbout = onOpenAbout,
            refreshCatalog = catalogViewModel::refreshNow,
        ),
        modifier = modifier,
        scrollToTopRequest = scrollToTopRequest,
        catalog = catalog,
    )
}

/** SCR-25 without state: the reliability card first, then *Timer & Alarm*, *Katalog* and *Über* (SET-30). */
@Composable
fun SettingsScreen(
    settings: UserSettings,
    status: ReliabilityStatus,
    alerting: Boolean,
    onBack: () -> Unit,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    saveFailed: Boolean = false,
    scrollToTopRequest: Int = 0,
    catalog: CatalogSettingsState = CatalogSettingsState(),
) {
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }
    // Rule 8: an alert removes an open dialog in its first frame, without animation.
    LaunchedEffect(alerting) { if (alerting) dialog = null }
    ScreenScaffold(
        title = stringResource(R.string.settings_title),
        modifier = modifier,
        navigationIcon = { BackAction(onBack) },
        scrollToTopRequest = scrollToTopRequest,
        verticalArrangement = Arrangement.Top,
    ) {
        if (saveFailed) {
            Text(
                text = stringResource(R.string.settings_save_failed),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.XSmall)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        ReliabilityCard(status = status, onFix = actions.fix)
        SectionHeader(title = stringResource(R.string.settings_section_timer), modifier = Modifier.fillMaxWidth())
        TimerSection(settings, actions, openDialog = { if (!alerting) dialog = it })
        SectionHeader(title = stringResource(R.string.settings_section_catalog), modifier = Modifier.fillMaxWidth())
        CatalogSection(catalog, onRefresh = actions.refreshCatalog)
        SwitchRow(
            title = stringResource(R.string.settings_catalog_photos),
            checked = settings.catalogDownloadPhotos,
            onCheckedChange = actions.setCatalogPhotos,
            detail = stringResource(R.string.settings_catalog_photos_detail),
        )
        SectionHeader(title = stringResource(R.string.settings_section_about), modifier = Modifier.fillMaxWidth())
        SettingsRow(
            title = stringResource(R.string.settings_about),
            onClick = actions.openAbout,
            modifier = Modifier.testTag(SettingsTags.ABOUT),
            chevron = true,
        )
    }
    if (!alerting) {
        when (dialog) {
            SettingsDialog.Tone -> ToneDialog(settings.alertSound, actions, onDismiss = { dialog = null })

            SettingsDialog.PreAlert -> PreAlertDialog(settings.preAlertSeconds, actions.setPreAlert) { dialog = null }

            SettingsDialog.PreAlertSound -> PreAlertSoundDialog(settings.preAlertSound, actions) { dialog = null }

            SettingsDialog.AlertLength -> AlertLengthDialog(settings.alertLength, actions.setAlertLength) {
                dialog =
                    null
            }

            null -> Unit
        }
    }
}

/** The *Timer-Zuverlässigkeit* card (SET-24): one row per check, *Beheben* on a missing one. */
@Composable
private fun ReliabilityCard(status: ReliabilityStatus, onFix: (ReliabilityCheck) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.XSmall)
            .testTag(SettingsTags.RELIABILITY),
        shape = UmamiShape.Card,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(horizontal = UmamiSpacing.Medium, vertical = UmamiSpacing.Small)) {
            Text(
                text = stringResource(R.string.settings_reliability_title),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(
                    if (status.hasProblem) R.string.settings_reliability_problem else R.string.settings_reliability_ok,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ReliabilityCheck.entries.forEach { check ->
                StatusCheckRow(
                    label = stringResource(check.label()),
                    state = status.stateOf(check).toStatusCheck(),
                    actionLabel = stringResource(R.string.settings_fix),
                    onAction = { onFix(check) },
                )
            }
        }
    }
}

@Composable
private fun TimerSection(settings: UserSettings, actions: SettingsActions, openDialog: (SettingsDialog) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsRow(
            title = stringResource(R.string.settings_tone),
            value = stringResource(AlertSound.tone(settings.alertSound).label()),
            onClick = { openDialog(SettingsDialog.Tone) },
        )
        SettingsRow(
            title = stringResource(R.string.settings_pre_alert),
            value = preAlertLabel(settings.preAlertSeconds),
            onClick = { openDialog(SettingsDialog.PreAlert) },
        )
        SettingsRow(
            title = stringResource(R.string.settings_pre_alert_sound),
            value = stringResource(PreAlertSound.of(settings.preAlertSound).sound.label()),
            onClick = { openDialog(SettingsDialog.PreAlertSound) },
        )
        SwitchRow(stringResource(R.string.settings_vibration), settings.vibration, actions.setVibration)
        SettingsRow(
            title = stringResource(R.string.settings_alert_length),
            value = stringResource(settings.alertLength.label()),
            onClick = { openDialog(SettingsDialog.AlertLength) },
        )
        SwitchRow(stringResource(R.string.settings_keep_screen_on), settings.keepScreenOn, actions.setKeepScreenOn)
        SwitchRow(
            title = stringResource(R.string.settings_dim),
            checked = settings.dimWhileSteeping,
            onCheckedChange = actions.setDim,
            detail = stringResource(R.string.settings_dim_detail),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.Small),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall)) {
                OutlinedButton(onClick = actions.testAlert) { Text(stringResource(R.string.settings_test_alert)) }
                TextButton(onClick = actions.stopTest) { Text(stringResource(R.string.settings_test_stop)) }
            }
            Text(
                text = stringResource(R.string.settings_test_alert_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = UmamiSpacing.SideMargin))
    }
}

/** A row that opens something: [title], the current [value] below it, a chevron for a page. */
@Composable
internal fun SettingsRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    chevron: Boolean = false,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = value?.let { { Text(it) } },
        trailingContent = if (chevron) {
            { Icon(painterResource(UmamiIcons.ChevronRight), contentDescription = null) }
        } else {
            null
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = UmamiSpacing.XXSmall),
    )
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, detail: String? = null) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = detail?.let { { Text(it) } },
        // The row is the switch: one node, one target.
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        // toggleable: TalkBack reads the title with „an" or „aus" and the switch role.
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = UmamiSpacing.XXSmall),
    )
}

@Composable
private fun preAlertLabel(seconds: Int): String = if (seconds == 0) {
    stringResource(R.string.settings_pre_alert_off)
} else {
    stringResource(R.string.settings_pre_alert_seconds, seconds)
}

/** The German name of each bundled sound: the tones of SET-09 and the *Vorwarnton* choices of SET-26. */
@StringRes
internal fun AlertSound.label(): Int = when (this) {
    AlertSound.KLANGSCHALE -> R.string.settings_tone_klangschale
    AlertSound.GLOCKE -> R.string.settings_tone_glocke
    AlertSound.HOLZBLOCK -> R.string.settings_tone_holzblock
    AlertSound.GLOCKENSPIEL -> R.string.settings_tone_glockenspiel
    AlertSound.SANFT -> R.string.settings_tone_sanft
    AlertSound.TICK -> R.string.settings_pre_alert_sound_ting
    AlertSound.ZIMBEL -> R.string.settings_pre_alert_sound_zimbel
    AlertSound.GLOECKCHEN -> R.string.settings_pre_alert_sound_gloeckchen
    AlertSound.KLANGSTAB -> R.string.settings_pre_alert_sound_klangstab
    AlertSound.TIEFE_SCHALE -> R.string.settings_pre_alert_sound_tiefe_schale
}

@StringRes
internal fun AlertLength.label(): Int = when (this) {
    AlertLength.SHORT -> R.string.settings_alert_length_short
    AlertLength.NORMAL -> R.string.settings_alert_length_normal
    AlertLength.LONG -> R.string.settings_alert_length_long
}

@StringRes
private fun ReliabilityCheck.label(): Int = when (this) {
    ReliabilityCheck.Notifications -> R.string.settings_check_notifications
    ReliabilityCheck.LiveUpdates -> R.string.settings_check_live
    ReliabilityCheck.FullScreenAlert -> R.string.settings_check_full_screen
    ReliabilityCheck.Battery -> R.string.settings_check_battery
    ReliabilityCheck.ExactAlarms -> R.string.settings_check_exact_alarms
}

private fun CheckState.toStatusCheck(): StatusCheck = when (this) {
    CheckState.Ok -> StatusCheck.Ok
    CheckState.Missing -> StatusCheck.Missing
    CheckState.Unavailable -> StatusCheck.Unavailable
}
