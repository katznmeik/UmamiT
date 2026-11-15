package com.umamit.app

import android.view.ViewGroup
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ActivityScenario

/**
 * The trivial reference transition of the relative jank gate (NFR-PERF-12, D-117 build note „Jank gate"): a plain
 * [DURATION_MS] ms crossfade between two near-empty full-screen surfaces. It is laid over the app's content in
 * [MainActivity]'s own window, so its frames pay the same window, the same render pipeline and the same JankStats
 * tracker as the app's transitions; only the content is trivial. Test-only: it touches no app state, no session and
 * no alert (rule 8), and [detach] removes it again.
 */
internal class ReferenceTransition(private val scenario: ActivityScenario<MainActivity>) {
    private val second = mutableStateOf(false)
    private var view: ComposeView? = null

    fun attach() = scenario.onActivity { activity ->
        val overlay = ComposeView(activity).apply {
            setContent {
                Crossfade(targetState = second.value, animationSpec = tween(DURATION_MS), label = "reference") { on ->
                    Box(Modifier.fillMaxSize().background(if (on) SURFACE_B else SURFACE_A))
                }
            }
        }
        activity.addContentView(
            overlay,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        view = overlay
    }

    /** Starts one crossfade to the other surface (main thread, as Compose state writes from the UI are). */
    fun toggle() = scenario.onActivity { second.value = !second.value }

    fun detach() = scenario.onActivity {
        view?.let { (it.parent as? ViewGroup)?.removeView(it) }
        view = null
    }

    companion object {
        const val NAME = "reference fade 300 ms"
        const val DURATION_MS = 300
        private val SURFACE_A = Color(0xFFF4EFE6)
        private val SURFACE_B = Color(0xFFE6ECDD)
    }
}
