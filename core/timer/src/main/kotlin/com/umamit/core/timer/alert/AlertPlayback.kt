package com.umamit.core.timer.alert

import android.content.Context
import android.util.Log
import com.umamit.core.data.SettingsRepository
import com.umamit.core.model.UserSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** *Alarm testen* (SET-31) for the timer settings (SCR-25, #50). */
interface AlertTester {
    /**
     * Plays the pre-alert tick, then the alert, with the current settings
     * (tone, vibration, alert length) and the ringer-mode matrix, exactly as
     * a step would. Ignored while a real alert or tick plays; a real one
     * replaces a running test.
     */
    fun testAlert()

    /** Stops a running test (e.g. the settings screen closes); a real alert or tick plays on. */
    fun stopTestAlert()
}

/**
 * The production alert player (04 §9; M1-10, D-059): the [RingerModeAlertPlayer]
 * with the device outputs, playing with the stored settings (SET-09 tone,
 * SET-11 vibration, SET-14 alert length) read at the start of every alert,
 * tick and test. [ControllerAlertPort] drives it for the controller, the
 * settings screen through [AlertTester]; both share this one player, so a
 * test never plays over a real alert.
 *
 * Every call only queues a command and returns at once (the controller calls
 * it inside its event lock). The commands run in order, one at a time, on
 * [scope]'s thread, the main thread in production, which the player and its
 * Android outputs need. The settings come from a cache that follows the
 * settings file. Right after the process starts, a command waits for the
 * first value at most [SETTINGS_WAIT_MS]; an unreadable or late file plays
 * the defaults (Klangschale, Normal, vibration on) rather than a late alert
 * (rule 8).
 */
@Singleton
class AlertPlayback internal constructor(
    environment: AlertEnvironment,
    sound: AlertSoundOutput,
    haptics: AlertHaptics,
    scheduler: AlertScheduler,
    settings: Flow<UserSettings>,
    private val scope: CoroutineScope,
) : AlertTester {

    @Inject
    constructor(@ApplicationContext context: Context, settings: SettingsRepository) : this(
        environment = AndroidAlertEnvironment(context),
        sound = MediaPlayerSoundOutput(context),
        haptics = VibratorHaptics(context),
        scheduler = HandlerAlertScheduler(),
        settings = settings.settings,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    )

    private sealed interface Command {
        data object PreAlert : Command

        class Alert(val onFinished: () -> Unit) : Command

        data object Stop : Command

        data object Test : Command

        data object StopTest : Command
    }

    private val latest: StateFlow<AlertSettings?> = settings
        .map { AlertSettings.of(it) }
        .catch { e ->
            // Whatever goes wrong with the settings file, the alert plays (rule 8).
            Log.w(TAG, "Settings unreadable; the alert uses the defaults", e)
            emit(AlertSettings())
        }
        .stateIn(scope, SharingStarted.Eagerly, initialValue = null)

    /** The settings of the command that runs; read only on [scope]'s thread. */
    private var current = AlertSettings()
    private val player = RingerModeAlertPlayer(environment, sound, haptics, scheduler) { current }
    private val commands = Channel<Command>(Channel.UNLIMITED)

    init {
        scope.launch { for (command in commands) runGuarded(command) }
    }

    /** The pre-alert tick (TMR-23); a playing alert is never cut by it. */
    fun playPreAlert() = send(Command.PreAlert)

    /** Starts the alert, replacing anything playing; [onFinished] runs once when its last cycle has run. */
    fun startAlert(onFinished: () -> Unit) = send(Command.Alert(onFinished))

    /** Stops the playing alert, tick or test at once; idempotent. */
    fun stopAlert() = send(Command.Stop)

    override fun testAlert() = send(Command.Test)

    override fun stopTestAlert() = send(Command.StopTest)

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
            Command.PreAlert -> {
                current = settings()
                player.playPreAlert()
            }

            is Command.Alert -> {
                current = settings()
                player.startAlert(onFinished = command.onFinished)
            }

            Command.Stop -> player.stopAlert()

            Command.Test -> {
                current = settings()
                player.testAlert()
            }

            Command.StopTest -> player.stopTestAlert()
        }
    }

    private suspend fun settings(): AlertSettings = latest.value
        ?: withTimeoutOrNull(SETTINGS_WAIT_MS) { latest.filterNotNull().first() }
        ?: AlertSettings()

    companion object {
        /** How long a command waits for the settings file's first value after the process starts. */
        const val SETTINGS_WAIT_MS = 300L
    }
}

private const val TAG = "UmamiTAlert"
