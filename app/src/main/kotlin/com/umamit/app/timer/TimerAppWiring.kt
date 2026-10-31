package com.umamit.app.timer

import android.content.res.Resources
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import com.umamit.app.ActiveBrewSource
import com.umamit.core.common.MonotonicClock
import com.umamit.core.designsystem.component.ActiveBrew
import com.umamit.core.designsystem.theme.DefaultExtendedColors
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.controller.BrewStart
import com.umamit.core.timer.controller.BrewStarter
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.engine.TransitionOutcome
import com.umamit.feature.brew.timer.BrewTimer
import com.umamit.feature.brew.timer.ControllerBrewTimer
import com.umamit.feature.brew.timer.activeBrewOf
import com.umamit.feature.brew.timer.displayTicks
import com.umamit.feature.brew.timer.progressAt
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The timer screen's seams in the app (SCR-05, #45): [BrewTimer] is the
 * `BrewSessionController` itself ([ControllerBrewTimer]). Every port of the
 * controller has its Android adapter since #47 bound the notifier (D-056,
 * D-057), so the app runs the real timer.
 */
@Module
@InstallIn(SingletonComponent::class)
object TimerAppModule {
    @Provides
    fun brewTimer(impl: ControllerBrewTimer): BrewTimer = impl

    /**
     * Start and the setup (M1-13) start brews through the same object as the timer, so this follows the
     * `brewTimer` binding: the controller ([ControllerBrewTimer]) since #47.
     */
    @Provides
    fun brewStarter(timer: BrewTimer): BrewStarter =
        checkNotNull(timer as? BrewStarter) { "the bound BrewTimer must also start brews (BrewStarter)" }
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
 * 10 s (03 §3.6), so the mini ring holds still in between. Its first value is
 * read synchronously from the controller, so a new activity starts with the bar.
 * It reads the two slots (D-098): the brew session first, else the quick timer;
 * the quick timer beside a brew session is [alongside], whichever is in front.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimerActiveBrewSource(
    timer: BrewTimer,
    private val clock: MonotonicClock,
    private val resources: Resources,
    private val scope: CoroutineScope,
) : ActiveBrewSource {
    // Snapshot state: the mini ring reads it while drawing and redraws when it changes.
    private val level = mutableFloatStateOf(0f)
    private val alongsideLevel = mutableFloatStateOf(0f)

    override val progress: () -> Float = { level.floatValue }

    override val alongsideProgress: () -> Float = { alongsideLevel.floatValue }

    private val brewSlot = timer.state(TimerSlot.BREW)
    private val quickSlot = timer.state(TimerSlot.QUICK)

    override val activeBrew: StateFlow<ActiveBrew?> = bar(level) { brew, quick -> brew ?: quick }

    override val alongside: StateFlow<ActiveBrew?> =
        bar(alongsideLevel) { brew, quick -> quick?.takeIf { brew != null } }

    /** One part of the bar: the timer [pick] chooses of the two slots', ticking, with its [level]. */
    private fun bar(
        level: MutableFloatState,
        pick: (brew: TimerUiState.Active?, quick: TimerUiState.Active?) -> TimerUiState.Active?,
    ): StateFlow<ActiveBrew?> {
        val picked = { brew: TimerUiState, quick: TimerUiState ->
            pick(brew as? TimerUiState.Active, quick as? TimerUiState.Active)
        }
        return combine(brewSlot, quickSlot, picked)
            .distinctUntilChanged()
            .flatMapLatest { state ->
                state?.displayTicks(clock::elapsedMs)?.map { now -> brewAt(state, now, level) } ?: flowOf(null)
            }
            // Synchronous first value from the controller's current state: a shell recreated while a
            // step alerts (rotation, theme switch, reopening) draws the matcha bar in its first frame
            // instead of letting it arrive a frame later with its enter motion (03 §3.8, rule 8).
            .stateIn(
                scope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                picked(brewSlot.value, quickSlot.value)?.let { brewAt(it, clock.elapsedMs(), level) },
            )
    }

    private fun brewAt(state: TimerUiState.Active, now: Long, level: MutableFloatState): ActiveBrew {
        level.floatValue = state.progressAt(now)
        return activeBrewOf(state, now, resources, DefaultExtendedColors.liquor.green)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
