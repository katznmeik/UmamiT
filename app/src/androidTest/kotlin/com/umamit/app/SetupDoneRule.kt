package com.umamit.app

import android.app.Application
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource

/**
 * Marks the first-start setup done (ONB-04) before the activity starts, through the app's own
 * settings store, so a test opens on Start. The first start creates nothing and sends nothing (D-135); the seed marker
 * is still written, as the older versions' rule did. Use it as the outer rule around the activity's rule.
 */
class SetupDoneRule : ExternalResource() {
    override fun before() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val settings = EntryPointAccessors.fromApplication(app, SettingsEntryPoint::class.java).settings()
        runBlocking {
            settings.markSeedDataCreated()
            settings.update { it.copy(onboardingCompleted = true) }
        }
    }
}
