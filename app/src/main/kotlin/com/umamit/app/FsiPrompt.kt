package com.umamit.app

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umamit.core.data.FsiPromptRepository
import com.umamit.core.designsystem.component.PillButton
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.timer.reliability.CheckState
import com.umamit.core.timer.reliability.ReliabilityCheck
import com.umamit.core.timer.reliability.TimerReliability
import com.umamit.core.timer.reliability.startFirstIntent
import com.umamit.core.ui.rememberExpandedSheetState
import com.umamit.core.ui.rememberSheetClose
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The Vollbild sheet (SCR-34). */
internal const val FSI_SHEET_TAG = "fsi_sheet"
internal const val FSI_ALLOW_TAG = "fsi_allow"
internal const val FSI_LATER_TAG = "fsi_later"

/**
 * Whether the „Vollbild-Alarm wieder erlauben" sheet shows now (TMR-31, SCR-34, D-136 item 13): in the first open of a
 * new versionCode ([promptedVersionCode] is not [currentVersionCode]) with full-screen alerts not allowed, never on the
 * first start ([onboardingCompleted], the setup asks itself), never over a timer screen ([timerScreenOnTop]: it waits
 * until that is left) and never over an alert ([alerting], rule 8). [promptedVersionCode] null: not read yet.
 */
internal fun shouldShowFsiSheet(
    promptedVersionCode: Int?,
    currentVersionCode: Int,
    onboardingCompleted: Boolean,
    fullScreenAllowed: Boolean,
    timerScreenOnTop: Boolean,
    alerting: Boolean,
): Boolean = promptedVersionCode != null &&
    promptedVersionCode != currentVersionCode &&
    onboardingCompleted &&
    !fullScreenAllowed &&
    !timerScreenOnTop &&
    !alerting

/** What the root needs of the Vollbild sheet's world: the stored versionCode, the permission and the system page. */
internal interface FsiPrompt {
    /** The versionCode the sheet was last shown or answered in; null until read. */
    val promptedVersionCode: StateFlow<Int?>

    /** The app's versionCode now. */
    val currentVersionCode: Int

    /**
     * Whether full-screen alerts are allowed now: the reliability status, a few system calls on the calling thread,
     * read again on every resume while the sheet could show ([FsiPromptHost]).
     */
    fun fullScreenAllowed(): Boolean

    /** Stores [currentVersionCode]: the sheet is answered for this version. */
    fun markPrompted()

    /** Opens `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` for the app (the app's details where it is missing). */
    fun openSystemPage()

    /** The sheet never shows. */
    data object None : FsiPrompt {
        override val promptedVersionCode: StateFlow<Int?> = MutableStateFlow(null)
        override val currentVersionCode: Int = 0
        override fun fullScreenAllowed(): Boolean = true
        override fun markPrompted() = Unit
        override fun openSystemPage() = Unit
    }
}

/** [FsiPrompt] on the device: the reliability status of TMR-31 and the stored versionCode. */
internal class AndroidFsiPrompt(
    private val activity: Activity,
    private val reliability: TimerReliability,
    private val repository: FsiPromptRepository,
    private val scope: CoroutineScope,
    override val currentVersionCode: Int,
) : FsiPrompt {
    override val promptedVersionCode: StateFlow<Int?> =
        repository.promptedVersionCode.stateIn(scope, SharingStarted.Eagerly, null)

    override fun fullScreenAllowed(): Boolean =
        reliability.status().stateOf(ReliabilityCheck.FullScreenAlert) != CheckState.Missing

    override fun markPrompted() {
        scope.launch { repository.markPrompted(currentVersionCode) }
    }

    override fun openSystemPage() {
        activity.startFirstIntent(reliability.settingsIntents(ReliabilityCheck.FullScreenAlert))
    }

    companion object {
        /** The prompt of [activity], for the versionCode it runs in. */
        fun of(
            activity: Activity,
            reliability: TimerReliability,
            repository: FsiPromptRepository,
            scope: CoroutineScope,
        ) = AndroidFsiPrompt(
            activity = activity,
            reliability = reliability,
            repository = repository,
            scope = scope,
            // The build's own versionCode, the installed one: no package manager call on the main thread.
            currentVersionCode = BuildConfig.VERSION_CODE,
        )
    }
}

/**
 * Shows the sheet while [shouldShowFsiSheet] says so. *Erlauben* stores the versionCode and opens the system page,
 * *Später* and a dismissal store it, so the sheet shows at most once per versionCode. The permission is read again on
 * every resume, since the user comes back from the system page, but only while the sheet could show: over a timer
 * screen or an alert it is not read (a resume by the alert's full-screen intent, a cold start into the alert: about
 * eight system calls on the main thread, rule 8), and it is read once that is left. The read follows the composition
 * that saw the resume, so it sees the alert and the timer screen of that resume, not those before the stop.
 */
@Composable
internal fun FsiPromptHost(
    prompt: FsiPrompt,
    onboardingCompleted: Boolean,
    timerScreenOnTop: Boolean,
    alerting: Boolean,
) {
    // null: not read (before the first resume, over a timer screen or an alert); the sheet shows only for a read that
    // found the permission missing.
    var allowed by remember { mutableStateOf<Boolean?>(null) }
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    val gateOpen = !timerScreenOnTop && !alerting
    val currentPrompt by rememberUpdatedState(prompt)
    // This composition's count, not the live one: the effect of a composition before the first resume never reads.
    val resumed = resumes
    LaunchedEffect(resumed, gateOpen) {
        allowed = if (gateOpen && resumed > 0) currentPrompt.fullScreenAllowed() else null
    }
    val prompted by prompt.promptedVersionCode.collectAsStateWithLifecycle()
    val show = shouldShowFsiSheet(
        promptedVersionCode = prompted,
        currentVersionCode = prompt.currentVersionCode,
        onboardingCompleted = onboardingCompleted,
        fullScreenAllowed = allowed != false,
        timerScreenOnTop = timerScreenOnTop,
        alerting = alerting,
    )
    if (show) {
        FsiSheet(
            onAllow = {
                prompt.markPrompted()
                prompt.openSystemPage()
            },
            onDismissRequest = prompt::markPrompted,
        )
    }
}

/**
 * „Vollbild-Alarm wieder erlauben" (SCR-34): [onAllow] for *Erlauben*; *Später*, a drag and a tap outside end in
 * [onDismissRequest].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FsiSheet(onAllow: () -> Unit, onDismissRequest: () -> Unit) {
    val sheetState = rememberExpandedSheetState(UmamiTheme.reduceMotion)
    val close = rememberSheetClose(sheetState, onDismissRequest)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = Modifier.testTag(FSI_SHEET_TAG),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = UmamiSpacing.SideMargin, end = UmamiSpacing.SideMargin, bottom = UmamiSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(UmamiSpacing.Small),
        ) {
            Text(
                text = stringResource(R.string.fsi_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.fsi_sheet_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PillButton(
                text = stringResource(R.string.fsi_sheet_allow),
                onClick = onAllow,
                modifier = Modifier.fillMaxWidth().testTag(FSI_ALLOW_TAG),
            )
            OutlinedButton(onClick = close, modifier = Modifier.fillMaxWidth().testTag(FSI_LATER_TAG)) {
                Text(stringResource(R.string.fsi_sheet_later))
            }
        }
    }
}
