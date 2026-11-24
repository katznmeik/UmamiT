package com.umamit.app

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.StepKind
import com.umamit.core.model.TimerPhase
import com.umamit.core.timer.GermanLocaleRunner
import com.umamit.core.timer.TimerNotifications
import com.umamit.core.timer.UnlockedDeviceRule
import com.umamit.core.timer.controller.BrewSessionController
import com.umamit.core.timer.controller.BrewStart
import com.umamit.core.timer.controller.TimerHost
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.controller.send
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.engine.TransitionOutcome
import com.umamit.core.ui.GermanFormat
import com.umamit.feature.settings.AppLocales
import com.umamit.feature.settings.LanguageChoice
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * A language change with a brew running keeps the brew (GEN-10 AC3, SET-35; D-136 item 15). *Sprache* hands the
 * language to Android ([AppLocales]), which re-creates the activity in English (`locale` is not in `configChanges`);
 * the session, its phase and its deadline in the timer controller stay exactly as they were, and the Live Update is
 * still posted. Then German again, as the runner set it ([GermanLocaleRunner]). The step is long, so no alert
 * rings (rule 8).
 */
@RunWith(AndroidJUnit4::class)
class LanguageSwitchDeviceTest {
    @get:Rule(order = 0)
    val unlocked = UnlockedDeviceRule()

    @get:Rule(order = 1)
    val setupDone = SetupDoneRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val locales = AppLocales(context)
    private val creations = MainActivityCreations()
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val controller = (context.applicationContext as TimerHost).timerInputs as BrewSessionController

    @Before
    fun setUp() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        endTimer()
        creations.start()
    }

    @After
    fun tearDown() {
        creations.stop()
        // The brew ends first, so a German that never takes leaves no running step to alert later (rule 8).
        endTimer()
        GermanLocaleRunner.pinGerman(context)
    }

    @Test
    fun switchingToEnglishAndBackKeepsTheRunningBrew() {
        ActivityScenario.launch(MainActivity::class.java).use {
            eventually("MainActivity in German", TIMEOUT) { resumedMainActivity()?.takeIf { it.language() == "de" } }
            runBlocking {
                assertEquals(TransitionOutcome.Accepted, controller.startSession(brew()))
                assertEquals(TransitionOutcome.Accepted, controller.send(TimerEvent.Start))
            }
            val before = eventually("the running step", TIMEOUT) { running() }
            eventually("the Live Update", TIMEOUT) { liveUpdate() }

            switchTo(LanguageChoice.ENGLISH, "en", before)
            switchTo(LanguageChoice.GERMAN, "de", before)
        }
    }

    /** *Sprache* → [choice]: a new MainActivity in [language], and the brew exactly as [before]. */
    private fun switchTo(choice: LanguageChoice, language: String, before: TimerUiState.Active) {
        val created = creations.count
        instrumentation.runOnMainSync { locales.apply(choice) }
        eventually("a MainActivity re-created in $language", TIMEOUT) {
            resumedMainActivity()?.takeIf { creations.count > created && it.language() == language }
        }
        assertEquals("the language Android holds for the app", choice, locales.current())
        // The formatters read the default locale at each call, the notifications the application's resources.
        assertEquals("the default locale", language, Locale.getDefault().language)
        assertEquals("the formatting", if (language == "en") "4.0" else "4,0", GermanFormat.gramsOneDecimal(4.0))
        assertEquals(
            "the application's resources (notifications)",
            language,
            context.applicationContext.resources.configuration.locales[0].language,
        )
        val after = running()
        assertNotNull("the brew must still run after the switch to $language: ${controller.state.value}", after)
        assertEquals("the same session", before.sessionId, after?.sessionId)
        assertEquals("the same step", before.stepIndex, after?.stepIndex)
        assertEquals("the same deadline", before.deadlineElapsedMs, after?.deadlineElapsedMs)
        assertTrue("the Live Update must still be posted", liveUpdate() != null)
    }

    private fun running(): TimerUiState.Active? =
        (controller.state.value as? TimerUiState.Active)?.takeIf { it.phase == TimerPhase.RUNNING }

    private fun liveUpdate() = notifications.activeNotifications.firstOrNull {
        it.id ==
            TimerNotifications.ID_LIVE_UPDATE
    }

    private fun Activity.language(): String = resources.configuration.locales[0].language

    private fun resumedMainActivity(): MainActivity? {
        var resumed: MainActivity? = null
        instrumentation.runOnMainSync {
            resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>().firstOrNull()
        }
        return resumed
    }

    private fun endTimer() = runBlocking {
        if (controller.state.value is TimerUiState.Active) controller.send(TimerEvent.Discard)
    }

    private fun <T : Any> eventually(what: String, timeout: Duration, poll: () -> T?): T {
        val start = TimeSource.Monotonic.markNow()
        while (start.elapsedNow() < timeout) {
            poll()?.let { return it }
            Thread.sleep(POLL_MS)
        }
        throw AssertionError("$what did not happen within $timeout (state=${controller.state.value})")
    }

    private companion object {
        val TIMEOUT = 10.seconds
        val STEP = 10.minutes
        const val POLL_MS = 200L

        fun brew() = BrewStart(
            snapshot = SessionSnapshot(
                teaId = null,
                teaName = "Testtee",
                teaSecondaryName = null,
                teaType = null,
                styleKey = null,
                category = null,
                profileId = null,
                profileName = null,
                method = BrewMethod.KYUSU,
                vesselId = null,
                vesselName = null,
                leafGrams = null,
                waterMl = null,
                baseTempC = 60,
            ),
            steps = listOf(
                PlannedStep(StepKind.INFUSION, STEP.inWholeSeconds.toInt(), tempC = 60, preAlertSeconds = 0),
            ),
            recipePreAlertSeconds = 0,
        )
    }
}
