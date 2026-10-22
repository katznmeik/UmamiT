package com.umamit.core.timer.diagnostics

import android.util.Log
import com.umamit.core.data.diagnostics.DiagnosticsLog
import com.umamit.core.timer.controller.TimerDiagnostic
import com.umamit.core.timer.controller.TimerDiagnosticsPort
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * The timer's diagnostics port on the app's [DiagnosticsLog] (06 §5.6, M1-20):
 * each [TimerDiagnostic] becomes one line ([TimerDiagnosticFormat]) at the
 * wall time of its clock reading. The controller calls it inside its event
 * lock and the service and alert player on the main thread, so it only
 * formats and queues; the log writes on its own background thread. Nothing
 * here throws (rule 8): an entry that cannot be formatted is lost.
 */
class LogTimerDiagnostics @Inject constructor(private val log: DiagnosticsLog) : TimerDiagnosticsPort {
    @Suppress("TooGenericExceptionCaught") // a broken entry must never reach the timer (rule 8)
    override fun record(entry: TimerDiagnostic) {
        try {
            log.append(entry.at.wallMs, entry.at.zone, TimerDiagnosticFormat.message(entry))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Diagnostics entry lost: ${entry::class.simpleName}", e)
        }
    }

    private companion object {
        const val TAG = "UmamiTDiagnostics"
    }
}
