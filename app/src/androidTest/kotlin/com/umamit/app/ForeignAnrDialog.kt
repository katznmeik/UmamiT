package com.umamit.app

import android.util.Log
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

/**
 * The system's „isn't responding" dialog of another app, which covers the screen of a later test (NFR-PERF-12): on
 * the API 36 CI emulator System UI's ANR dialog took the focus from MainActivity (CI run 44622487547, „Start is not
 * shown" with `mCurrentFocus=Window{… Application Not Responding: com.android.systemui}`). [dismiss] answers it with
 * „Wait"/„Warten", logs it and lets the test go on; an ANR of UmamiT itself (the app or its test package) is never
 * dismissed and fails the test.
 */
internal object ForeignAnrDialog {
    private const val TAG = "ForeignAnrDialog"
    private const val WINDOW_MARKER = "Application Not Responding: "
    private const val GONE_TIMEOUT_MS = 2_000L

    /** At most this many dialogs in a row (several apps, or one shown again): then the test goes on as it is. */
    private const val MAX_DIALOGS = 3

    private val WAIT = By.res("android", "aerr_wait")
    private val WAIT_TEXT = By.text(Pattern.compile("Wait|Warten", Pattern.CASE_INSENSITIVE))

    /** The packages of the ANR windows in [windowDump] (`dumpsys window`'s `Application Not Responding: <pkg>`). */
    fun packagesIn(windowDump: String): Set<String> = windowDump.lines()
        .mapNotNull { line ->
            line.substringAfter(WINDOW_MARKER, "").takeWhile { !it.isWhitespace() && it != '}' }.ifEmpty { null }
        }
        .toSet()

    /**
     * Closes another app's ANR dialog with „Wait", if one shows; [shell] runs a shell command, [ownPackages] are
     * UmamiT's. Throws an [AssertionError] for an ANR of one of [ownPackages].
     */
    fun dismiss(device: UiDevice, shell: (String) -> String, ownPackages: Set<String>) {
        repeat(MAX_DIALOGS) {
            val packages = packagesIn(shell("dumpsys window windows"))
            if (packages.isEmpty()) return
            val own = packages.intersect(ownPackages)
            if (own.isNotEmpty()) throw AssertionError("UmamiT is not responding: ANR dialog of $own")
            val button = device.findObject(WAIT) ?: device.findObject(WAIT_TEXT)
            if (button == null) {
                Log.w(TAG, "ANR window of $packages without a Wait button on screen; going on")
                return
            }
            Log.w(TAG, "foreign ANR dialog of $packages: answering Wait")
            button.click()
            device.wait(Until.gone(WAIT), GONE_TIMEOUT_MS)
        }
    }
}
