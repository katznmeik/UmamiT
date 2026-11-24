package com.umamit.app

import android.app.Activity
import android.content.res.Configuration
import android.view.Surface
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage

/**
 * Turns the emulator's display as a phone turned in the hand would (D-136 build note „Portrait timer"): auto-rotate
 * off and the user rotation set, so the display follows it wherever the app asks for no orientation, and stays upright
 * where it asks for portrait (Android's display rotation gives an app's fixed orientation precedence over the user
 * rotation). [save] before the first turn, [restore] after the test.
 */
internal class DisplayRotation(private val shell: (String) -> String) {
    private var accelerometer: String? = null
    private var userRotation: String? = null

    fun save() {
        accelerometer = shell("settings get system accelerometer_rotation").trim()
        userRotation = shell("settings get system user_rotation").trim()
        shell("settings put system accelerometer_rotation 0")
    }

    /** The phone turned to landscape (90°). */
    fun landscape() {
        shell("settings put system user_rotation ${Surface.ROTATION_90}")
    }

    /** The phone turned back upright. */
    fun portrait() {
        shell("settings put system user_rotation ${Surface.ROTATION_0}")
    }

    /** Back to the settings [save] read; nothing when it never ran. */
    fun restore() {
        val saved = accelerometer ?: return
        put("user_rotation", userRotation)
        put("accelerometer_rotation", saved)
        accelerometer = null
    }

    private fun put(key: String, value: String?) {
        if (value.isNullOrBlank() || value == "null") {
            shell("settings delete system $key")
        } else {
            shell("settings put system $key $value")
        }
    }
}

/** The resumed [MainActivity]'s configuration orientation and its display's rotation, read on the main thread. */
internal data class Upright(val orientation: Int, val rotation: Int) {
    val portrait: Boolean get() = orientation == Configuration.ORIENTATION_PORTRAIT && rotation == Surface.ROTATION_0
    val landscape: Boolean get() = orientation == Configuration.ORIENTATION_LANDSCAPE && rotation == Surface.ROTATION_90

    companion object {
        /** Null while no [MainActivity] is resumed. */
        fun read(): Upright? {
            var upright: Upright? = null
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>()
                    .firstOrNull()
                    ?.let { upright = Upright(it.resources.configuration.orientation, it.display.rotation) }
            }
            return upright
        }
    }
}

/**
 * Counts the [MainActivity] instances created while [start]ed: a rotation handled in place (`configChanges`, D-136
 * build note „Portrait timer") creates none, a re-creation one.
 */
internal class MainActivityCreations : ActivityLifecycleCallback {
    @Volatile
    var count: Int = 0
        private set

    override fun onActivityLifecycleChanged(activity: Activity, stage: Stage) {
        if (activity is MainActivity && stage == Stage.CREATED) count++
    }

    fun start() = InstrumentationRegistry.getInstrumentation().runOnMainSync {
        ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(this)
    }

    fun stop() = InstrumentationRegistry.getInstrumentation().runOnMainSync {
        ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(this)
    }
}
