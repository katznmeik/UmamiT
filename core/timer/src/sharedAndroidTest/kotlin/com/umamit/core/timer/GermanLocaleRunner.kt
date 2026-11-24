package com.umamit.core.timer

import android.app.LocaleManager
import android.content.Context
import android.os.Bundle
import android.os.LocaleList
import androidx.test.runner.AndroidJUnitRunner

/**
 * The runner of the device tests of `:app` and `:core:timer` (CI emulator job): it sets the app's own language to
 * German (GEN-10, D-136 item 15) before any test runs, so the German texts the tests read and assert hold on the
 * en-US emulator. The per-app language goes through the platform `LocaleManager`, as *Sprache* (SET-35) does;
 * Android keeps it for the package, and every activity started after it is German. A test that switches the
 * language puts German back with [pinGerman].
 */
class GermanLocaleRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle?) {
        pinGerman(targetContext)
        super.onCreate(arguments)
    }

    companion object {
        /** Sets [context]'s app to German. */
        fun pinGerman(context: Context) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales = LocaleList.forLanguageTags("de")
        }
    }
}
