package com.umamit.app.timer

import android.content.res.Resources
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.umamit.app.ActiveBrewSource
import com.umamit.core.common.MonotonicClock
import com.umamit.core.designsystem.component.ActiveBrew
import com.umamit.core.designsystem.theme.DefaultExtendedColors
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.controller.BrewStart
import com.umamit.core.timer.controller.BrewStarter
import com.umamit.core.timer.controller.ControllerBrewStarter
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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
     * Start, the setup and the quick timer (M1-13) start brews on the same controller as the timer:
     * [ControllerBrewStarter], like [ControllerBrewTimer] a stateless front of the one controller.
     */
    @Provides
    fun brewStarter(impl: ControllerBrewStarter): BrewStarter = impl
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
 * at each second of the countdown or the overtime while the activity is started, so it stops
 * with the app in the background. A long step's progress changes only every
 * 10 s (03 §3.6), so the mini ring holds still in between. Its first value is
 * read synchronously from the controller, so a new activity starts with the bar.
 * It reads every slot (D-098, D-108): the first active timer in the slot order
 * (`BREW`, `BREW_2`, `QUICK`) is [activeBrew], the second [alongside],
 * whichever is in front; [barSlots] names their slots for the taps.
 *
 * Driven by the activity's [lifecycle], not by its subscribers (A-02, NFR-PERF-12): at `ON_START`, before
 * the first frame after unlock or a return from recents, the bar is read afresh from the controller and
 * the clock, so that frame never replays the lock-time bar or the bar of the moment the timer opened (the
 * shell is not composed under the timer). It also ticks while the timer is on top, so the shell that comes
 * back has the bar of now. Without a [lifecycle] (tests) it runs for as long as [scope].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimerActiveBrewSource(
    timer: BrewTimer,
    private val clock: MonotonicClock,
    private val resources: Resources,
    private val scope: CoroutineScope,
    lifecycle: Lifecycle? = null,
) : ActiveBrewSource {
    // Snapshot state: the mini ring reads it while drawing and redraws when it changes.
    private val level = mutableFloatStateOf(0f)
    private val alongsideLevel = mutableFloatStateOf(0f)

    override val progress: () -> Float = { level.floatValue }

    override val alongsideProgress: () -> Float = { alongsideLevel.floatValue }

    private val slots = TimerSlot.entries.map(timer::state)

    /** The active timers of [states] (one per slot) in the slot order. */
    private fun activeOf(states: List<TimerUiState>) = states.filterIsInstance<TimerUiState.Active>()

    private fun activeNow() = activeOf(slots.map { it.value })

    private val first: (List<TimerUiState.Active>) -> TimerUiState.Active? = { it.getOrNull(0) }
    private val second: (List<TimerUiState.Active>) -> TimerUiState.Active? = { it.getOrNull(1) }

    // Synchronous first values from the controller's current state: a shell recreated while a
    // step alerts (rotation, reopening) draws the matcha bar in its first frame
    // instead of letting it arrive a frame later with its enter motion (03 §3.8, rule 8).
    private val brewState = MutableStateFlow(snapshot(level, first))
    private val alongsideState = MutableStateFlow(snapshot(alongsideLevel, second))
    private val slotsState = MutableStateFlow(activeNow().map { it.slot })

    override val activeBrew: StateFlow<ActiveBrew?> = brewState.asStateFlow()

    override val alongside: StateFlow<ActiveBrew?> = alongsideState.asStateFlow()

    override val barSlots: StateFlow<List<TimerSlot>> = slotsState.asStateFlow()

    private var ticking: Job? = null

    init {
        if (lifecycle == null) {
            start()
        } else {
            lifecycle.addObserver(
                object : DefaultLifecycleObserver {
                    override fun onStart(owner: LifecycleOwner) {
                        refresh()
                        start()
                    }

                    override fun onStop(owner: LifecycleOwner) {
                        ticking?.cancel()
                        ticking = null
                    }
                },
            )
        }
    }

    /** Reads the bar afresh from the controller and the clock, synchronously. */
    private fun refresh() {
        brewState.value = snapshot(level, first)
        alongsideState.value = snapshot(alongsideLevel, second)
        slotsState.value = activeNow().map { it.slot }
    }

    private fun start() {
        if (ticking?.isActive == true) return
        ticking = scope.launch {
            launch { ticks(level, first).collect { brewState.value = it } }
            launch { ticks(alongsideLevel, second).collect { alongsideState.value = it } }
            launch {
                combine(slots) { states -> activeOf(states.toList()).map { it.slot } }
                    .distinctUntilChanged()
                    .collect { slotsState.value = it }
            }
        }
    }

    private fun snapshot(level: MutableFloatState, pick: (List<TimerUiState.Active>) -> TimerUiState.Active?) =
        pick(activeNow())?.let { brewAt(it, clock.elapsedMs(), level) }

    /** One part of the bar: the timer [pick] chooses of the active ones (slot order), ticking, with its [level]. */
    private fun ticks(
        level: MutableFloatState,
        pick: (active: List<TimerUiState.Active>) -> TimerUiState.Active?,
    ): Flow<ActiveBrew?> = combine(slots) { states -> pick(activeOf(states.toList())) }
        .distinctUntilChanged()
        .flatMapLatest { state ->
            state?.displayTicks(clock::elapsedMs)?.map { now -> brewAt(state, now, level) } ?: flowOf(null)
        }

    private fun brewAt(state: TimerUiState.Active, now: Long, level: MutableFloatState): ActiveBrew {
        level.floatValue = state.progressAt(now)
        return activeBrewOf(state, now, resources, DefaultExtendedColors.liquor.green)
    }
}
