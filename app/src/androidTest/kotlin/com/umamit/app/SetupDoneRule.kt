package com.umamit.app

import android.app.Application
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource

/**
 * Marks the first-start setup done (ONB-04) before the activity starts, through the app's own
 * settings store, so a test opens on Start and the first start (seed data, the seed photo
 * download to the shop) never runs in the emulator. Use it as the outer rule around the
 * activity's rule.
 */
class SetupDoneRule : ExternalResource() {
    override fun before() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val settings = EntryPointAccessors.fromApplication(app, SettingsEntryPoint::class.java).settings()
        runBlocking { settings.update { it.copy(onboardingCompleted = true) } }
    }
}
