package com.umamit.app

import android.app.Activity
import android.app.Dialog
import android.view.Choreographer
import android.view.View
import android.view.Window
import android.view.inspector.WindowInspector
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.metrics.performance.FrameData
import androidx.metrics.performance.FrameDataApi31
import androidx.metrics.performance.JankStats
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Records the duration of every frame the app's windows draw while [measure] runs, with JankStats
 * (NFR-PERF-12, D-117). Not only the activity's window: a modal sheet (Teeauswahl, Schritte-Sheet) is
 * a dialog with its own window, so each frame of a measurement looks for new app windows
 * ([WindowInspector]) and tracks them before their first draw. A window is found by its view tree's
 * lifecycle owner, the activity or the dialog; a popup (a menu) has no [Window] and is not tracked.
 *
 * Main-thread state is touched only on the main thread; JankStats reports on its own thread, so the
 * frames go into a concurrent queue.
 */
internal class FrameRecorder {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    // Main thread only.
    private val tracked = mutableMapOf<Window, JankStats>()
    private var scanning = false

    private val frames = ConcurrentLinkedQueue<Long>()

    @Volatile
    private var recording = false

    private val scanEachFrame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!scanning) return
            trackNewWindows()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    /** The windows tracked so far (activity and dialogs), for the report. */
    val windowCount: Int get() = onMain { tracked.size }

    /**
     * The frames of [action] and of the [settleMs] after it, the time the transition's animations
     * need to end; JankStats reports a frame shortly after it is drawn, hence the flush.
     */
    fun measure(settleMs: Long, action: () -> Unit): List<Long> {
        frames.clear()
        onMain {
            trackNewWindows()
            scanning = true
            Choreographer.getInstance().postFrameCallback(scanEachFrame)
        }
        recording = true
        try {
            action()
            Thread.sleep(settleMs)
        } finally {
            Thread.sleep(FLUSH_MS)
            recording = false
            onMain { scanning = false }
        }
        return frames.toList()
    }

    /** Stops every JankStats this recorder started; call it when the test ends. */
    fun release() = onMain {
        tracked.values.forEach { it.isTrackingEnabled = false }
        tracked.clear()
    }

    private fun trackNewWindows() {
        WindowInspector.getGlobalWindowViews().forEach { root ->
            val window = windowOf(root) ?: return@forEach
            if (window !in tracked) {
                tracked[window] =
                    JankStats.createAndTrack(window) { frame -> if (recording) frames.add(durationOf(frame)) }
            }
        }
    }

    private fun windowOf(root: View): Window? = when (val owner = root.findViewTreeLifecycleOwner()) {
        is Activity -> owner.window.takeIf { it.peekDecorView() === root }
        is Dialog -> owner.window?.takeIf { it.peekDecorView() === root }
        else -> null
    }

    /** The whole frame where the platform reports it (API 31+), else its UI-thread part. FrameData is reused. */
    private fun durationOf(frame: FrameData): Long =
        (frame as? FrameDataApi31)?.frameDurationTotalNanos ?: frame.frameDurationUiNanos

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    private companion object {
        const val FLUSH_MS = 300L
    }
}
