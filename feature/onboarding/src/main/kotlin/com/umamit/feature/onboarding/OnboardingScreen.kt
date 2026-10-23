package com.umamit.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umamit.core.designsystem.component.PillButton
import com.umamit.core.designsystem.component.StatusCheck
import com.umamit.core.designsystem.component.StatusCheckRow
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiShape
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.timer.reliability.CheckState
import com.umamit.core.timer.reliability.ReliabilityCheck
import com.umamit.core.timer.reliability.ReliabilityStatus
import com.umamit.core.timer.reliability.samsungAutoRestartIntents
import com.umamit.core.timer.reliability.startFirstIntent
import com.umamit.core.ui.rememberNotificationPermissionAction

/** The pages of SCR-01 in M1; the catalog page (ONB-03) follows in M2. */
enum class OnboardingPage { Welcome, Reliability }

/** Test tags of SCR-01. */
object OnboardingTags {
    fun row(check: ReliabilityCheck) = "OnboardingRow_${check.name}"
}

/** The setup page's rows (ONB-02), in its order; exact alarms are always granted and not shown here. */
private val SETUP_CHECKS = listOf(
    ReliabilityCheck.Notifications,
    ReliabilityCheck.Battery,
    ReliabilityCheck.LiveUpdates,
    ReliabilityCheck.FullScreenAlert,
)

/** What the setup's buttons do. */
class OnboardingActions(
    val start: () -> Unit = {},
    val openCheck: (ReliabilityCheck) -> Unit = {},
    val openAutoRestart: () -> Unit = {},
    val skipPhotos: () -> Unit = {},
    val next: () -> Unit = {},
)

/**
 * The first-start setup (SCR-01) with its view model: the rows are read again on every resume,
 * so they are current after the system settings; [onFinish] runs after *Weiter* has stored
 * `onboardingCompleted` (ONB-04).
 */
@Composable
fun OnboardingRoute(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var page by rememberSaveable { mutableStateOf(OnboardingPage.Welcome) }
    val status by viewModel.status.collectAsStateWithLifecycle()
    val photosLoading by viewModel.photosLoading.collectAsStateWithLifecycle()
    val saveFailed by viewModel.saveFailed.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    BackHandler(enabled = page == OnboardingPage.Reliability) { page = OnboardingPage.Welcome }
    val requestNotifications = rememberNotificationPermissionAction(
        onResult = viewModel::refresh,
        openSettings = { context.startFirstIntent(viewModel.open(ReliabilityCheck.Notifications)) },
    )
    OnboardingScreen(
        page = page,
        status = status,
        photosLoading = photosLoading,
        saveFailed = saveFailed,
        actions = OnboardingActions(
            start = { page = OnboardingPage.Reliability },
            openCheck = { check ->
                if (check == ReliabilityCheck.Notifications) {
                    requestNotifications()
                } else {
                    context.startFirstIntent(viewModel.open(check))
                }
            },
            openAutoRestart = { context.startFirstIntent(samsungAutoRestartIntents()) },
            skipPhotos = viewModel::skipPhotos,
            next = { viewModel.finish(onFinish) },
        ),
        modifier = modifier,
    )
}

/** SCR-01 without state: the [page] with its content; pages change with the tab fade-through (03 §3.6). */
@Composable
fun OnboardingScreen(
    page: OnboardingPage,
    status: ReliabilityStatus,
    photosLoading: Boolean,
    actions: OnboardingActions,
    modifier: Modifier = Modifier,
    saveFailed: Boolean = false,
) {
    val motion = UmamiTheme.motion
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(
            targetState = page,
            transitionSpec = { fadeIn(motion.tabSwitchIn()) togetherWith fadeOut(motion.tabSwitchOut()) },
            label = "onboardingPage",
        ) { shown ->
            when (shown) {
                OnboardingPage.Welcome -> WelcomePage(onStart = actions.start)
                OnboardingPage.Reliability -> ReliabilityPage(status, photosLoading, saveFailed, actions)
            }
        }
    }
}

@Composable
private fun WelcomePage(onStart: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        // The full-bleed ink wash A2 (SCR-01): mountains in its lower half, the tea branches at its
        // right edge, so a narrow screen crops its left side; its paper is the theme's background.
        Image(
            painter = painterResource(if (UmamiTheme.isDark) UmamiIcons.WelcomeDark else UmamiIcons.WelcomeLight),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = Alignment.BottomEnd,
        )
        WelcomeContent(onStart)
    }
}

/** Logo, name, welcome and *Los geht's* on the empty paper above the landscape. */
@Composable
private fun WelcomeContent(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = UmamiSpacing.SideMargin)
            .padding(top = WELCOME_TOP_DP.dp, bottom = UmamiSpacing.XLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Icon(
            painter = painterResource(UmamiIcons.Leaf),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(UmamiSpacing.XSmall))
        Text(
            text = stringResource(R.string.onboarding_app_name),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(UmamiSpacing.Medium))
        Text(
            text = stringResource(R.string.onboarding_welcome),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(UmamiSpacing.XSmall))
        Text(
            text = stringResource(R.string.onboarding_welcome_japanese),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(UmamiSpacing.XXLarge))
        PillButton(text = stringResource(R.string.onboarding_start), onClick = onStart)
    }
}

/** Where the welcome content starts: on the empty paper, above the landscape (A2). */
private const val WELCOME_TOP_DP = 96

/** The reliability vignette's share of the page width (A5). */
private const val RELIABILITY_ART_WIDTH = 0.7f

@Composable
private fun ReliabilityPage(
    status: ReliabilityStatus,
    photosLoading: Boolean,
    saveFailed: Boolean,
    actions: OnboardingActions,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.Large),
    ) {
        // The vignette A5 (kyusu and a ringing phone); its paper is the theme's background.
        Image(
            painter = painterResource(
                if (UmamiTheme.isDark) UmamiIcons.ReliabilityDark else UmamiIcons.ReliabilityLight,
            ),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .fillMaxWidth(RELIABILITY_ART_WIDTH),
            contentScale = ContentScale.FillWidth,
        )
        Spacer(Modifier.height(UmamiSpacing.Medium))
        Text(
            text = stringResource(R.string.onboarding_reliability_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(UmamiSpacing.XSmall))
        Text(
            text = stringResource(R.string.onboarding_reliability_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(UmamiSpacing.Medium))
        SETUP_CHECKS.forEach { check ->
            val texts = check.texts()
            StatusCheckRow(
                label = stringResource(texts.label),
                explanation = stringResource(texts.explanation),
                state = status.stateOf(check).toStatusCheck(),
                actionLabel = stringResource(texts.action),
                onAction = { actions.openCheck(check) },
                modifier = Modifier.testTag(OnboardingTags.row(check)),
            )
        }
        Spacer(Modifier.height(UmamiSpacing.Medium))
        AutoRestartHint(onOpen = actions.openAutoRestart)
        Spacer(Modifier.height(UmamiSpacing.Large))
        PillButton(
            text = stringResource(R.string.onboarding_next),
            onClick = actions.next,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        if (saveFailed) {
            Spacer(Modifier.height(UmamiSpacing.XSmall))
            Text(
                text = stringResource(R.string.onboarding_save_failed),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        // Below *Weiter*, so *Weiter* never moves under a finger when the download ends.
        if (photosLoading) {
            Spacer(Modifier.height(UmamiSpacing.Small))
            PhotosLine(onSkip = actions.skipPhotos)
        }
    }
}

@Composable
private fun AutoRestartHint(onOpen: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = UmamiShape.Card,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(
                start = UmamiSpacing.Medium,
                end = UmamiSpacing.Medium,
                top = UmamiSpacing.Small,
            ),
        ) {
            Text(
                text = stringResource(R.string.onboarding_restart_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.onboarding_restart_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onOpen, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.onboarding_restart_link), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** The quiet progress line of the seed photos (ONB-06); never an error. */
@Composable
private fun PhotosLine(onSkip: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.onboarding_photos_loading),
            modifier = Modifier
                .weight(1f)
                .semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onSkip) {
            Text(stringResource(R.string.onboarding_photos_skip), style = MaterialTheme.typography.labelLarge)
        }
    }
}

private class CheckTexts(@StringRes val label: Int, @StringRes val explanation: Int, @StringRes val action: Int)

private fun ReliabilityCheck.texts(): CheckTexts = when (this) {
    ReliabilityCheck.Notifications -> CheckTexts(
        R.string.onboarding_check_notifications,
        R.string.onboarding_check_notifications_explanation,
        R.string.onboarding_check_notifications_action,
    )

    ReliabilityCheck.Battery -> CheckTexts(
        R.string.onboarding_check_battery,
        R.string.onboarding_check_battery_explanation,
        R.string.onboarding_check_battery_action,
    )

    ReliabilityCheck.LiveUpdates -> CheckTexts(
        R.string.onboarding_check_live,
        R.string.onboarding_check_live_explanation,
        R.string.onboarding_check_live_action,
    )

    ReliabilityCheck.FullScreenAlert -> CheckTexts(
        R.string.onboarding_check_full_screen,
        R.string.onboarding_check_full_screen_explanation,
        R.string.onboarding_check_full_screen_action,
    )

    ReliabilityCheck.ExactAlarms -> error("exact alarms are always granted and not on the setup page")
}

private fun CheckState.toStatusCheck(): StatusCheck = when (this) {
    CheckState.Ok -> StatusCheck.Ok
    CheckState.Missing -> StatusCheck.Missing
    CheckState.Unavailable -> StatusCheck.Unavailable
}
