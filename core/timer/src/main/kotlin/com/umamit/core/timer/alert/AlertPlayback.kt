package com.umamit.core.timer.alert

import android.content.Context
import android.os.Handler
import android.util.Log
import com.umamit.core.common.ClockReading
import com.umamit.core.data.SettingsRepository
import com.umamit.core.model.UserSettings
import com.umamit.core.timer.controller.ScheduledTime
import com.umamit.core.timer.controller.TimerClocks
import com.umamit.core.timer.controller.TimerDiagnostic
import com.umamit.core.timer.controller.TimerDiagnosticsPort
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** *Alarm testen* (SET-31) for the timer settings (SCR-25, #50). */
interface AlertTester {
    /**
     * Plays the pre-alert tick, then the alert, with the current settings
     * (tone, vibration, alert length, *Lautstärke*) and the ringer-mode matrix,
     * exactly as a step would. Ignored while a real alert or tick plays; a real one
     * replaces a running test.
     */
    fun testAlert()

    /** Stops a running test (e.g. the settings screen closes); a real alert or tick plays on. */
    fun stopTestAlert()
}

/**
 * The production alert player (04 §9; M1-10, D-059): the [RingerModeAlertPlayer]
 * with the device outputs, playing with the stored settings (SET-09 tone,
 * SET-11 vibration, SET-14 alert length, SET-26 pre-alert sound, SET-27
 * *Lautstärke*) read at the start of every alert, tick and test, so a change
 * applies to the next cue. [ControllerAlertPort] drives it for
 * the controller, the settings screen through [AlertTester]; both share this
 * one player, so a test never plays over a real alert.
 *
 * Every call only queues a command and returns at once (the controller calls
 * it inside its event lock). The commands run in order, one at a time, on
 * [scope]'s thread: in production the alert thread ([AlertThread], D-092),
 * where the player, its repeats and its sound callbacks all run, so a busy
 * main thread never delays a cue. The settings come from a cache that follows
 * the settings file. Right after the process starts, a command waits for the
 * first value at most [SETTINGS_WAIT_MS]; an unreadable or late file plays
 * the defaults (Klangschale, Normal, vibration on) rather than a late alert
 * (rule 8).
 *
 * While the controller keeps it ready ([keepReady]: a timer is active), the
 * chosen pre-alert sound and the current tone stay loaded, so a cue only
 * starts its sound (D-092); one changed in the settings meanwhile is loaded at
 * once (SET-26). The loads run on the loader thread, never as a command here:
 * a cue queued after a settings change never waits for the new sound to load
 * (rule 8, D-096). Each tick and alert logs where its start spent the time
 * ([CueStart]).
 */
@Singleton
class AlertPlayback internal constructor(
    private val environment: AlertEnvironment,
    private val sound: AlertSoundOutput,
    haptics: AlertHaptics,
    scheduler: AlertScheduler,
    settings: Flow<UserSettings>,
    private val scope: CoroutineScope,
    private val diagnostics: TimerDiagnosticsPort,
    private val clock: () -> ClockReading,
) : AlertTester {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        settings: SettingsRepository,
        diagnostics: TimerDiagnosticsPort,
        clocks: TimerClocks,
    ) : this(
        environment = AndroidAlertEnvironment(context),
        sound = MediaPlayerSoundOutput(context, AlertThread.looper),
        haptics = VibratorHaptics(context),
        scheduler = HandlerAlertScheduler(Handler(AlertThread.looper)),
        settings = settings.settings,
        scope = CoroutineScope(SupervisorJob() + Handler(AlertThread.looper).asCoroutineDispatcher(THREAD)),
        diagnostics = diagnostics,
        clock = clocks::read,
    )

    /** A command; `sentAtMs` is when a cue was queued (elapsed clock), for the log's [CueStart]. */
    private sealed interface Command {
        class PreAlert(val scheduled: ScheduledTime?, val sentAtMs: Long) : Command

        class Alert(val scheduled: ScheduledTime?, val onFinished: () -> Unit, val sentAtMs: Long) : Command

        data object Stop : Command

        data object Test : Command

        data object StopTest : Command

        class KeepReady(val ready: Boolean) : Command

        /** The tone or the pre-alert sound changed in the settings: load it now if a timer is active. */
        data object SoundsChanged : Command
    }

    /**
     * The settings as the file holds them now. A failed read never ends it (D-068): the alert
     * plays the last settings read, or the defaults before the first one (rule 8), and the file
     * is read again after [retryDelayMs], so a later alert follows tone, length and vibration
     * again (SET-09, SET-11, SET-14).
     */
    private val latest: StateFlow<AlertSettings?> = flow {
        var read = false
        emitAll(
            settings
                .map { AlertSettings.of(it) }
                .onEach { read = true }
                .retryWhen { e, attempt ->
                    if (e is CancellationException) return@retryWhen false
                    // Whatever goes wrong with the settings file, the alert plays (rule 8).
                    Log.w(TAG, "Settings unreadable; the alert uses the ${if (read) "last" else "default"} ones", e)
                    if (!read) emit(AlertSettings())
                    delay(retryDelayMs(attempt))
                    true
                },
        )
    }.stateIn(scope, SharingStarted.Eagerly, initialValue = null)

    /** The settings of the command that runs; read only on [scope]'s thread. */
    private var current = AlertSettings()
    private val player = RingerModeAlertPlayer(environment, sound, haptics, scheduler) { current }
    private val commands = Channel<Command>(Channel.UNLIMITED)

    /** The controller keeps the sounds ready (a timer is active); read only on [scope]'s thread. */
    private var keptReady = false

    /** The sounds last asked to stay loaded; read only on [scope]'s thread. */
    private var prepared: Set<AlertSound> = emptySet()

    init {
        scope.launch { for (command in commands) runGuarded(command) }
        // A tone or Vorwarnton changed in the settings while a timer is active is loaded at once,
        // not only at the controller's next turn, so the next cue still finds it loaded (D-092,
        // SET-26). Other settings changes load nothing. The command only asks for the load, which
        // runs on the loader thread: a cue queued behind it starts at once (rule 8, D-096).
        scope.launch {
            latest.filterNotNull().map { it.sounds }.distinctUntilChanged().collect { send(Command.SoundsChanged) }
        }
    }

    /**
     * The pre-alert tick (TMR-23); a playing alert is never cut by it. [scheduled]: when it was
     * due, for the log's delay up to the moment it started (D-068).
     */
    fun playPreAlert(scheduled: ScheduledTime? = null) = send(Command.PreAlert(scheduled, clock().elapsedMs))

    /**
     * Starts the alert, replacing anything playing; [onFinished] runs once when its last cycle
     * has run. [scheduled]: the step's deadline, for the log's delay up to the moment the sound
     * started (D-068).
     */
    fun startAlert(scheduled: ScheduledTime? = null, onFinished: () -> Unit) =
        send(Command.Alert(scheduled, onFinished, clock().elapsedMs))

    /** Stops the playing alert, tick or test at once; idempotent. */
    fun stopAlert() = send(Command.Stop)

    override fun testAlert() = send(Command.Test)

    override fun stopTestAlert() = send(Command.StopTest)

    /**
     * While [ready] (a timer is active), the chosen pre-alert sound (SET-26) and the current tone
     * stay loaded, so the next tick or alert only starts its sound; false releases them (D-092).
     * The controller calls it at the end of every turn, after the turn's own cues. Loading never
     * waits for the settings file: it takes the sounds known now (the defaults before the first
     * read). A sound changed in the settings while ready is loaded at once, the old one released.
     * The loads run off the alert thread ([AlertSoundOutput.prepare]), so no cue waits for one.
     */
    fun keepReady(ready: Boolean) = send(Command.KeepReady(ready))

    private fun send(command: Command) {
        // An unlimited channel never refuses while the scope lives.
        commands.trySend(command)
    }

    /** A command that throws (a device output failing) is logged; the next alert still plays (rule 8). */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun runGuarded(command: Command) {
        try {
            run(command)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Alert command $command failed", e)
        }
    }

    private suspend fun run(command: Command) {
        when (command) {
            is Command.PreAlert -> {
                val begunAtMs = clock().elapsedMs
                current = settings()
                val timing =
                    StartTiming(command.sentAtMs, begunAtMs, clock().elapsedMs, current.preAlert in sound.ready)
                player.playPreAlert()
                logPlayed(AlertCue.PRE_ALERT, command.scheduled, timing)
            }

            is Command.Alert -> {
                val begunAtMs = clock().elapsedMs
                current = settings()
                val timing = StartTiming(command.sentAtMs, begunAtMs, clock().elapsedMs, current.tone in sound.ready)
                player.startAlert(onFinished = command.onFinished)
                logPlayed(AlertCue.ALERT, command.scheduled, timing)
            }

            Command.Stop -> player.stopAlert()

            Command.Test -> {
                current = settings()
                player.testAlert()
                logPlayed(AlertCue.TEST)
            }

            Command.StopTest -> player.stopTestAlert()

            // The pre-alert sound and the tone known now, never a wait for the settings file (rule 8).
            is Command.KeepReady -> {
                keptReady = command.ready
                prepared = if (command.ready) (latest.value ?: current).sounds else emptySet()
                sound.prepare(prepared)
            }

            // Only while a timer is active, and only when the sounds really changed.
            Command.SoundsChanged -> (latest.value ?: current).sounds.takeIf { keptReady && it != prepared }?.let {
                prepared = it
                sound.prepare(it)
            }
        }
    }

    /**
     * The moments of a cue's start before its outputs (elapsed clock): queued, its command
     * begun, the settings read; [soundLoaded]: its sound was loaded before it.
     */
    private class StartTiming(
        val sentAtMs: Long,
        val begunAtMs: Long,
        val settledAtMs: Long,
        val soundLoaded: Boolean,
    ) {
        /** The [CueStart] of outputs started by [startedAtMs]; [sound]: a sound played. */
        fun cueStart(startedAtMs: Long, sound: Boolean) = CueStart(
            queuedMs = begunAtMs - sentAtMs,
            settingsMs = settledAtMs - begunAtMs,
            outputsMs = startedAtMs - settledAtMs,
            soundLoaded = soundLoaded.takeIf { sound },
        )
    }

    /**
     * Logs the ringer mode and the outputs of [cue] (06 §5.6), after the player has started
     * it: the log never delays or stops an alert (rule 8). With its [scheduled] time, the entry
     * shows how late the cue actually started, which *Diagnose* and the accuracy run read
     * (TMR-30, D-068); with its [timing], where the time went before the sound (D-092).
     */
    @Suppress("TooGenericExceptionCaught")
    private fun logPlayed(cue: AlertCue, scheduled: ScheduledTime? = null, timing: StartTiming? = null) {
        try {
            val at = clock()
            val conditions = environment.conditions()
            val outputs = AlertMatrix.outputs(conditions, current.vibration, current.ringUnderDnd)
            diagnostics.record(
                TimerDiagnostic.AlertPlayed(
                    at = at,
                    cue = cue,
                    conditions = conditions,
                    outputs = outputs,
                    tone = if (cue == AlertCue.PRE_ALERT) current.preAlert.key else current.tone.key,
                    length = current.length,
                    scheduled = scheduled,
                    start = timing?.cueStart(at.elapsedMs, outputs.sound),
                    volumePercent = current.volumePercent,
                    ringUnderDnd = current.ringUnderDnd,
                    // Read here, after the outputs started: never ahead of a cue's sound (rule 8).
                    alarmVolumeOff = environment.alarmVolumeOff(),
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Diagnostics of $cue lost", e)
        }
    }

    private suspend fun settings(): AlertSettings = latest.value
        ?: withTimeoutOrNull(SETTINGS_WAIT_MS) { latest.filterNotNull().first() }
        ?: AlertSettings()

    companion object {
        /** The name of the alert thread's dispatcher (D-092). */
        private const val THREAD = "UmamiTAlert"

        /** How long a command waits for the settings file's first value after the process starts. */
        const val SETTINGS_WAIT_MS = 300L

        /** The wait before the settings file is read again after a failed read; it doubles up to [MAX_RETRY_MS]. */
        const val FIRST_RETRY_MS = 1_000L
        const val MAX_RETRY_MS = 60_000L
        private const val MAX_DOUBLINGS = 6

        /** The wait after failed read number [attempt] + 1: 1 s, 2 s, 4 s, … up to [MAX_RETRY_MS]. */
        internal fun retryDelayMs(attempt: Long): Long =
            (FIRST_RETRY_MS shl attempt.coerceIn(0, MAX_DOUBLINGS.toLong()).toInt()).coerceAtMost(MAX_RETRY_MS)
    }
}

private const val TAG = "UmamiTAlert"
