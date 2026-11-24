package com.umamit.app

import android.view.ViewGroup
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ActivityScenario
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * The animation duration scale Compose applies in [MainActivity]'s window, made to follow the system setting.
 *
 * Compose keeps one scale per process (`MotionDurationScaleImpl` on a shared `StateFlow`, `WhileSubscribed`): it
 * reads `animator_duration_scale` once when the flow is first made and then only hears changes while a window is
 * subscribed. A setting written with no activity open (as [TransitionJankTest]'s set-up does) is missed, and the
 * process keeps the scale it had, 0 on the CI AVD (`disable-animations: true`). Then every Compose animation ends in
 * its first frame while the app's own reduce-motion still reads 1: the reference fade takes 2 frames instead of ~10,
 * and the relative gate compares against nothing, and the Teeauswahl's slide ends before its content comes, so the
 * sheet stays at the screen's bottom edge with its teas below it (#358: since the language-switch test runs first, the
 * process no longer happens to hold 1 when this class starts). [ensure] reads the scale in the window and, when it is
 * not the target, changes the setting while the window listens, so Compose follows. Test-only: it touches no app
 * state and arms nothing (rule 8); its probe is removed again.
 */
internal class ComposeAnimationScale(
    private val scenario: ActivityScenario<MainActivity>,
    private val shell: (String) -> String,
) {
    @Volatile
    private var scale = Float.NaN
    private var view: ComposeView? = null

    /** Compose's scale in the window is [target], with the setting changed while the window listens if needed. */
    fun ensure(target: Float) {
        attach()
        try {
            if (settled() == target) return
            // A change is heard only if the value changes; a non-zero step keeps the app's reduce-motion off.
            for (value in listOf(STEP, target)) {
                shell("settings put global $KEY $value")
                if (!await { scale == value }) {
                    throw AssertionError(
                        "Compose's animation scale in the app's window stays at $scale after " +
                            "$KEY=$value (the setting reads ${shell("settings get global $KEY").trim()})",
                    )
                }
            }
        } finally {
            detach()
        }
    }

    /** The scale once the probe's collector has run (before that Compose reports 1 whatever it holds). */
    private fun settled(): Float {
        await { !scale.isNaN() }
        Thread.sleep(COLLECT_MS)
        return scale
    }

    private fun attach() = scenario.onActivity { activity ->
        val probe = ComposeView(activity).apply {
            setContent {
                LaunchedEffect(Unit) {
                    val motion = coroutineContext[MotionDurationScale]
                    while (isActive) {
                        scale = motion?.scaleFactor ?: 1f
                        delay(POLL_MS)
                    }
                }
            }
        }
        activity.addContentView(probe, ViewGroup.LayoutParams(0, 0))
        view = probe
    }

    private fun detach() = scenario.onActivity {
        view?.let { (it.parent as? ViewGroup)?.removeView(it) }
        view = null
        scale = Float.NaN
    }

    private fun await(condition: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < end) {
            if (condition()) return true
            Thread.sleep(POLL_MS)
        }
        return condition()
    }

    private companion object {
        const val KEY = "animator_duration_scale"
        const val STEP = 0.5f
        const val POLL_MS = 50L
        const val COLLECT_MS = 300L
        const val TIMEOUT_MS = 5_000L
    }
}
