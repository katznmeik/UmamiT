package com.umamit.app.timer

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import androidx.compose.runtime.mutableFloatStateOf
import com.umamit.app.ActiveBrewSource
import com.umamit.app.probe.AndroidPermissionStatusChecks
import com.umamit.app.probe.PermissionState
import com.umamit.app.probe.PermissionStatusChecks
import com.umamit.app.probe.PermissionStatusReader
import com.umamit.app.probe.permissionStatusSettingsIntents
import com.umamit.app.probe.startFirstPermissionStatusIntent
import com.umamit.core.common.MonotonicClock
import com.umamit.core.designsystem.component.ActiveBrew
import com.umamit.core.designsystem.theme.DefaultExtendedColors
import com.umamit.core.timer.controller.BrewStart
import com.umamit.core.timer.controller.BrewStarter
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.engine.TransitionOutcome
import com.umamit.feature.brew.timer.BrewTimer
import com.umamit.feature.brew.timer.TimerReliability
import com.umamit.feature.brew.timer.activeBrewOf
import com.umamit.feature.brew.timer.displayTicks
import com.umamit.feature.brew.timer.progressAt
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The timer screen's seams in the app (SCR-05, #45).
 *
 * **Until #42** binds the controller's remaining ports (alarms, service;
 * then #43, #44, #47 and M1-16), `BrewSessionController` cannot be injected
 * (D-056), so [BrewTimer] is [NoBrewTimer]: no session is ever active, the
 * timer route closes at once and the active brew bar stays hidden, exactly as
 * before. When the ports are bound, replace the `brewTimer` provider with
 * `@Binds fun brewTimer(impl: ControllerBrewTimer): BrewTimer` (feature:brew).
 */
@Module
@InstallIn(SingletonComponent::class)
object TimerAppModule {
    @Provides
    fun brewTimer(): BrewTimer = NoBrewTimer

    /** Start and the setup (M1-13); with #42 `@Binds fun brewStarter(impl: ControllerBrewStarter): BrewStarter`. */
    @Provides
    fun brewStarter(): BrewStarter = NoBrewTimer

    @Provides
    fun timerReliability(@ApplicationContext context: Context): TimerReliability = PermissionTimerReliability(
        checks = AndroidPermissionStatusChecks(context),
        packageName = context.packageName,
        open = { context.startFirstPermissionStatusIntent(it) },
    )
}

/**
 * No controller yet (#42): always idle, and every event and start is ignored. Not a stand-in timer:
 * nothing ever runs, so *Brühen starten* and *Nochmal brühen* stay on their screen until then.
 */
object NoBrewTimer : BrewTimer, BrewStarter {
    override val state: StateFlow<TimerUiState> = MutableStateFlow<TimerUiState>(TimerUiState.Idle).asStateFlow()

    override suspend fun send(event: TimerEvent): TransitionOutcome = TransitionOutcome.Ignored

    override suspend fun startSession(request: BrewStart): TransitionOutcome = TransitionOutcome.Ignored

    override suspend fun finishActiveAndStart(request: BrewStart): TransitionOutcome = TransitionOutcome.Ignored
}

/**
 * The live reliability checks of TMR-31, with the probe's status logic (#14):
 * a check that is denied is a problem; one the platform lacks („nicht
 * verfügbar") is not. *Beheben* opens the page of the first failing check.
 */
class PermissionTimerReliability(
    private val checks: PermissionStatusChecks,
    private val packageName: String,
    private val open: (List<Intent>) -> Unit,
) : TimerReliability {
    private fun firstFailing() = PermissionStatusReader(checks).read().firstOrNull {
        it.state == PermissionState.Denied
    }

    override fun hasProblem(): Boolean = firstFailing() != null

    override fun fix() {
        firstFailing()?.let { open(permissionStatusSettingsIntents(it.item, it.state, packageName)) }
    }
}

/** The window flags of the timer (SCR-05, TMR-06). */
data class LockScreenFlags(val showWhenLocked: Boolean, val turnScreenOn: Boolean)

/**
 * Only the timer shows over the lock screen, while it is the current route with
 * an active session (unlocking is only needed to leave it); the alert turns the
 * screen on there. The full-screen intent (#47) opens the timer first, so an
 * alert under the keyguard never shows the shell (D-058).
 */
fun lockScreenFlags(state: TimerUiState, timerVisible: Boolean): LockScreenFlags {
    val active = state as? TimerUiState.Active
    val shown = timerVisible && active != null
    return LockScreenFlags(showWhenLocked = shown, turnScreenOn = shown && active.alerting)
}

/**
 * The active brew bar fed by the controller's state (GEN-06, D-055): it ticks
 * at each second of the countdown or the overtime while collected, so it stops
 * with the app in the background. A long step's progress changes only every
 * 10 s (03 §3.6), so the mini ring holds still in between.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimerActiveBrewSource(timer: BrewTimer, clock: MonotonicClock, resources: Resources, scope: CoroutineScope) :
    ActiveBrewSource {
    // Snapshot state: the mini ring reads it while drawing and redraws when it changes.
    private val level = mutableFloatStateOf(0f)

    override val progress: () -> Float = { level.floatValue }

    override val activeBrew: StateFlow<ActiveBrew?> = timer.state
        .flatMapLatest { state ->
            if (state is TimerUiState.Active) {
                state.displayTicks(clock::elapsedMs).map { now ->
                    level.floatValue = state.progressAt(now)
                    activeBrewOf(state, now, resources, DefaultExtendedColors.liquor.green)
                }
            } else {
                flowOf(null)
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
