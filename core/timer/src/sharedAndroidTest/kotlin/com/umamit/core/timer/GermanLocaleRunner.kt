package com.umamit.core.timer

import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.os.Bundle
import android.os.LocaleList
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnitRunner
import org.junit.runner.Description
import org.junit.runner.notification.RunListener
import java.util.Locale

/**
 * The runner of the device tests of `:app` and `:core:timer` (CI emulator job): the app's own language is German
 * (GEN-10, D-136 item 15) before every test, so the German texts the tests read and assert hold on the en-US
 * emulator. The per-app language goes through the platform `LocaleManager`, as *Sprache* (SET-35) does.
 *
 * Android keeps that language for the package, but a process holds it only while it has an activity: when its last
 * activity goes (a test's `ActivityScenario` closes, `TimerTestActivity` finishes), the process falls back to the
 * phone's English (`WindowProcessController.unregisterActivityConfigurationListener`). Setting the language again
 * puts it back on every running process of the app, so [PinGermanBeforeEachTest] does that before each test and
 * waits until the test's own process is German. The timer's notifications follow the app's language in any process
 * themselves (`AppLanguageContext`). A test that switches the language puts German back with [pinGerman].
 */
class GermanLocaleRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle?) {
        val args = arguments ?: Bundle()
        val listeners = listOfNotNull(args.getString(ARGUMENT_LISTENER)?.takeIf { it.isNotBlank() })
        args.putString(ARGUMENT_LISTENER, (listeners + PinGermanBeforeEachTest::class.java.name).joinToString(","))
        // German for the work before the first test too; on the main thread, so without waiting (the listener waits).
        targetContext.getSystemService(LocaleManager::class.java)?.applicationLocales = GERMAN
        super.onCreate(args)
    }

    companion object {
        private const val ARGUMENT_LISTENER = "listener"
        private const val TIMEOUT_MS = 10_000L
        private const val POLL_MS = 50L
        private val GERMAN = LocaleList.forLanguageTags("de")

        /**
         * Sets [context]'s app to German and waits until this process's application resources and
         * `Locale.getDefault()` are German; fails with what it saw if they are not within 10 s. Not on the main
         * thread, which applies the configuration change.
         */
        fun pinGerman(context: Context) {
            check(Looper.myLooper() != Looper.getMainLooper()) { "pinGerman waits for the main thread: call it off it" }
            val app = context.applicationContext
            val localeManager = app.getSystemService(LocaleManager::class.java)
            // Even when Android already holds German: setting it again re-applies it to the app's processes.
            localeManager.applicationLocales = GERMAN
            val start = SystemClock.uptimeMillis()
            while (!isGerman(app)) {
                if (SystemClock.uptimeMillis() - start > TIMEOUT_MS) {
                    throw AssertionError(
                        "the German app language (GEN-10) did not take in process ${Application.getProcessName()} " +
                            "within $TIMEOUT_MS ms: app language ${localeManager.applicationLocales}, resources " +
                            "${app.resources.configuration.locales}, Locale.getDefault() ${Locale.getDefault()}",
                    )
                }
                SystemClock.sleep(POLL_MS)
            }
        }

        private fun isGerman(app: Context): Boolean =
            app.resources.configuration.locales[0].language == "de" && Locale.getDefault().language == "de"
    }
}

/**
 * Puts the German app language back before each test ([GermanLocaleRunner]). A listener cannot fail a test, so a
 * language that never takes is logged here, and the test's German assertions then fail.
 */
class PinGermanBeforeEachTest : RunListener() {
    override fun testStarted(description: Description) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val before = context.applicationContext.resources.configuration.locales
        if (before[0].language != "de") Log.i(TAG, "before ${description.displayName}: the process was in $before")
        runCatching { GermanLocaleRunner.pinGerman(context) }
            .onFailure { Log.e(TAG, "before ${description.displayName}: ${it.message}", it) }
    }

    private companion object {
        const val TAG = "UmamiTLocale"
    }
}
