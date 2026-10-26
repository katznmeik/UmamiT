package com.umamit.core.timer.alert

import android.util.Log
import com.umamit.core.timer.controller.BrewSessionController
import com.umamit.core.timer.controller.ScheduledTime
import com.umamit.core.timer.controller.StepAlert
import com.umamit.core.timer.controller.TimerAlertPort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * The controller's alert port (04 §4, §9; D-056, D-059) on the production
 * [AlertPlayback]. When an alert has run its full length (SET-14), it reports
 * `AlertCompleted` for exactly that [StepAlert] by calling the controller's
 * suspend input from its own coroutine, never from inside the controller's
 * turn. An alert that is stopped or replaced first is not reported; the
 * controller has then moved on. So each alert is reported at most once by
 * the player, and recovery replays an alert whose report never arrived
 * (at least once, D-047).
 *
 * The controller comes through a [Provider]: it depends on this port, so the
 * provider breaks the cycle; it is only resolved when the first report runs.
 */
@Singleton
class ControllerAlertPort internal constructor(
    private val playback: AlertPlayback,
    private val controller: Provider<BrewSessionController>,
    private val scope: CoroutineScope,
) : TimerAlertPort {

    @Inject
    constructor(playback: AlertPlayback, controller: Provider<BrewSessionController>) :
        this(playback, controller, CoroutineScope(SupervisorJob() + Dispatchers.Default))

    override fun playPreAlert(scheduled: ScheduledTime?) = playback.playPreAlert(scheduled)

    override fun startAlert(alert: StepAlert, scheduled: ScheduledTime?) =
        playback.startAlert(scheduled, onFinished = { report(alert) })

    override fun stopAlert() = playback.stopAlert()

    override fun keepReady(ready: Boolean) = playback.keepReady(ready)

    @Suppress("TooGenericExceptionCaught") // a failed report is logged; recovery replays the alert (D-047)
    private fun report(alert: StepAlert) {
        scope.launch {
            try {
                controller.get().alertCompleted(alert)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("UmamiTAlert", "AlertCompleted for $alert failed", e)
            }
        }
    }
}
