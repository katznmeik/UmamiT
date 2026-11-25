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

    /**
     * The phone turned to landscape (90°), or still held so. With rotation locked, WindowManager writes the rotation
     * that an app's fixed orientation forced back into `user_rotation` (seen on API 35 and 36: `user_rotation=0` once
     * the portrait timer turned the display), whereas a real phone's sensor still reports it sideways. So after a timer
     * screen turned the display upright, a test calls this again to keep the phone held sideways.
     */
    fun landscape() {
        shell("settings put system user_rotation ${Surface.ROTATION_90}")
    }

    /** The phone turned back upright. */
    fun portrait() {
        shell("settings put system user_rotation ${Surface.ROTATION_0}")
    }

    /**
     * What decides the display's rotation, for a failure message (D-136 build note „Portrait timer"): the rotation
     * settings and the display's rotation lines of `dumpsys window displays`, so a failed turn tells a release the app
     * never sent (the requested orientation in [Upright] still portrait) from a display that did not follow it.
     */
    fun state(): String {
        val settings = "user_rotation=${shell("settings get system user_rotation").trim()} " +
            "accelerometer_rotation=${shell("settings get system accelerometer_rotation").trim()}"
        val display = Dumpsys.matchingLines(shell("dumpsys window displays"), DISPLAY_MARKERS)
        return "$settings\n$display"
    }

    /** Back to the settings [save] read; nothing when it never ran. */
    fun restore() {
        val saved = accelerometer ?: return
        put("user_rotation", userRotation)
        put("accelerometer_rotation", saved)
        accelerometer = null
    }

    private companion object {
        val DISPLAY_MARKERS = listOf("mRotation=", "mUserRotation", "mLastOrientation", "mCurrentAppOrientation")
    }

    private fun put(key: String, value: String?) {
        if (value.isNullOrBlank() || value == "null") {
            shell("settings delete system $key")
        } else {
            shell("settings put system $key $value")
        }
    }
}

/**
 * The resumed [MainActivity]'s configuration orientation, its display's rotation and the orientation it asks for
 * ([requested], `ActivityInfo.SCREEN_ORIENTATION_*`), read on the main thread.
 */
internal data class Upright(val orientation: Int, val rotation: Int, val requested: Int) {
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
                    ?.let {
                        upright =
                            Upright(
                                it.resources.configuration.orientation,
                                it.display.rotation,
                                it.requestedOrientation,
                            )
                    }
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
