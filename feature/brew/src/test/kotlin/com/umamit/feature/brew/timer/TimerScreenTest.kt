package com.umamit.feature.brew.timer

import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.espresso.Espresso
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.StepKind
import com.umamit.core.model.TimerPhase
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.feature.brew.timer.TimerStates.NOW
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SCR-05 with a fake controller state (spec #40 seam 4): the controls and the
 * overflow per state, the confirmations, the Schritte-Sheet removed in the
 * same frame as the alert starts (rule 8), the TalkBack announcements, and
 * the screen staying on and dimming (TMR-17). The screen's clock is the test's
 * main clock, so advancing it moves the countdown.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TimerScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var state by mutableStateOf<TimerUiState.Active>(TimerStates.ready())
    private var settings by mutableStateOf(TimerScreenSettings())
    private val events = mutableListOf<TimerEvent>()
    private val opened = mutableListOf<String>()
    private var start = 0L
    private val clock: () -> Long = { NOW + compose.mainClock.currentTime - start }

    private fun show(initial: TimerUiState.Active) {
        state = initial
        start = compose.mainClock.currentTime
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                TimerScreen(
                    state = state,
                    nowElapsedMs = clock,
                    onEvent = { events += it },
                    onBack = { opened += "back" },
                    onAdjustBrew = { opened += "adjust" },
                    onOpenTimerSettings = { opened += "settings" },
                    settings = settings,
                )
            }
        }
    }

    private fun openMenu() = compose.onNodeWithContentDescription("Weitere Optionen").performClick()

    private fun ring() = compose.onNodeWithTag(TIMER_RING_TAG)

    private fun hasDescriptionContaining(text: String) = SemanticsMatcher("description contains „$text“") {
        it.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.any { d -> text in d }
    }

    private fun announcement(text: String, mode: LiveRegionMode) =
        SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(text))
            .and(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, mode))

    // --- Controls per state ---------------------------------------------------------------

    @Test
    fun `READY waits with the kettle hint and starts from the pill or the ring (TMR-08)`() {
        show(TimerStates.ready())

        compose.onNodeWithText("Aufguss 1 · 1:00", useUnmergedTree = true).assertIsDisplayed()
        ring().assert(hasDescriptionContaining("Bereit"))
        compose.onNodeWithText("Start").performClick()
        ring().performClick()

        assertThat(events).containsExactly(TimerEvent.Start, TimerEvent.Start)
    }

    @Test
    fun `RUNNING has Pause as the only control and reads Gleich fertig from the pre-alert (TMR-03, TMR-23)`() {
        show(TimerStates.running(remainingMs = 48_000))

        ring().assert(hasDescriptionContaining("Zieht …"))
        compose.mainClock.advanceTimeBy(38_000)
        ring().assert(hasDescriptionContaining("Gleich fertig …"))
        compose.onNodeWithText("Start").assertDoesNotExist()
        compose.onNodeWithText("Pause").performClick()

        assertThat(events).containsExactly(TimerEvent.Pause)
    }

    @Test
    fun `PAUSED resumes with Weiter`() {
        show(TimerStates.paused())

        ring().assert(hasDescriptionContaining("Pausiert"))
        compose.onNodeWithText("Weiter").performClick()

        assertThat(events).containsExactly(TimerEvent.Resume)
    }

    @Test
    fun `ALERTING shows the overtime, large from 15 s on, and Abgegossen acknowledges (TMR-06, TMR-07)`() {
        show(TimerStates.alerting(overtimeMs = 7_000))

        ring().assert(hasDescriptionContaining("Jetzt abgießen!"))
        compose.onNodeWithText("+0:07", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("7 Sekunden überzogen").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(8_000)
        compose.onNodeWithText("+0:15 überzogen", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("15 Sekunden überzogen").assertIsDisplayed()
        compose.onNodeWithText("Abgegossen").performClick()

        assertThat(events).containsExactly(TimerEvent.Poured)
    }

    @Test
    fun `the alert is found as the app's emulator test looks for it over the lock screen (#47)`() {
        show(TimerStates.alerting())

        // The same matchers as TimerAlertOverLockScreenTest (app androidTest).
        compose.onNodeWithTag(TIMER_RING_TAG)
            .assert(hasContentDescription("Jetzt abgießen!", substring = true))
            .assertIsDisplayed()
        compose.onNodeWithText("Abgegossen").assertIsDisplayed()
    }

    @Test
    fun `a rinse is acknowledged with Weggegossen (TMR-20)`() {
        show(TimerStates.alerting(rinse = true, stepIndex = 0))

        compose.onNodeWithText("Weggegossen").performClick()

        assertThat(events).containsExactly(TimerEvent.Poured)
    }

    @Test
    fun `FINISHED offers Sitzung beenden and Weiterer Aufguss, which opens the Schritte-Sheet (TMR-10, TMR-12)`() {
        show(TimerStates.finished())

        ring().assert(hasDescriptionContaining("Alle Aufgüsse fertig"))
        compose.onNodeWithText("Sitzung beenden").performClick()
        compose.onNodeWithText("Weiterer Aufguss").performClick()

        assertThat(events).containsExactly(TimerEvent.FinishSession, TimerEvent.AddStep).inOrder()
        compose.onNodeWithText("Schritte für diese Sitzung").assertIsDisplayed()
    }

    @Test
    fun `the timer's Schritte-Sheet shows each step's lead from the session's B, the running step's is editable`() {
        // SCR-31, TMR-23, #92. B = 20 s: 1:00 → 20 s, 0:20 → 5 s, 0:40 → 10 s.
        show(TimerStates.running().copy(preAlertBaseSeconds = 20))
        openMenu()
        compose.onNodeWithText("Schritte bearbeiten").performClick()

        compose.onNodeWithContentDescription("Vorwarnung, Schritt 2: Automatisch, 5 Sekunden vorher. Ändern")
            .assertExists()
        compose.onNodeWithContentDescription("Vorwarnung, Schritt 3: Automatisch, 10 Sekunden vorher. Ändern")
            .assertExists()
        compose.onNodeWithContentDescription("Vorwarnung, Schritt 1: Automatisch, 20 Sekunden vorher. Ändern")
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Aus").performClick()
        compose.onNodeWithText("Fertig").performClick()

        // Only the running step's pre-alert changes; its time and temperature stay (SCR-31, D-093).
        assertThat(events).containsExactly(
            TimerEvent.EditSteps(
                listOf(
                    PlannedStep(StepKind.INFUSION, 60, 55, preAlertSeconds = 0),
                    PlannedStep(StepKind.INFUSION, 20, 60),
                    PlannedStep(StepKind.INFUSION, 40, 60),
                ),
            ),
        )
    }

    @Test
    fun `in a cold or ice brew the timer's Schritte-Sheet shows no pre-alert, however short a step`() {
        show(TimerStates.ready().copy(longStepsOnly = true))
        openMenu()
        compose.onNodeWithText("Schritte bearbeiten").performClick()

        compose.onNodeWithContentDescription("Vorwarnung, Schritt 1: Automatisch, keine. Ändern").assertExists()
    }

    @Test
    fun `back leaves the screen and keeps the session`() {
        show(TimerStates.running())

        compose.onNodeWithContentDescription("Zurück").performClick()

        assertThat(opened).containsExactly("back")
        assertThat(events).isEmpty()
    }

    // --- Overflow ⋮ per state (SCR-05 table) ------------------------------------------------

    private val allItems = listOf(
        "Brühen anpassen",
        "Aufguss jetzt beenden",
        "Schritt überspringen",
        "Schritte bearbeiten",
        "Weiterer Aufguss",
        "Sitzung beenden",
        "Sitzung verwerfen",
        "Timer-Einstellungen",
        "Abbrechen",
    )

    private fun count(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().size

    private fun assertMenu(state: TimerUiState.Active, vararg expected: String) {
        show(state)
        // FINISHED has *Sitzung beenden* and *Weiterer Aufguss* as buttons too: count what the menu adds.
        val before = allItems.associateWith(::count)
        openMenu()
        allItems.forEach { item ->
            assertWithMessage(item).that(count(item) - before.getValue(item)).isEqualTo(if (item in expected) 1 else 0)
        }
    }

    @Test
    fun `the overflow of READY`() = assertMenu(
        TimerStates.ready(),
        "Brühen anpassen",
        "Schritt überspringen",
        "Schritte bearbeiten",
        "Weiterer Aufguss",
        "Sitzung beenden",
        "Sitzung verwerfen",
        "Timer-Einstellungen",
    )

    @Test
    fun `the overflow of RUNNING`() = assertMenu(
        TimerStates.running(),
        "Aufguss jetzt beenden",
        "Schritte bearbeiten",
        "Weiterer Aufguss",
        "Sitzung beenden",
        "Sitzung verwerfen",
        "Timer-Einstellungen",
    )

    @Test
    fun `the overflow of PAUSED`() = assertMenu(
        TimerStates.paused(),
        "Aufguss jetzt beenden",
        "Schritte bearbeiten",
        "Weiterer Aufguss",
        "Sitzung beenden",
        "Sitzung verwerfen",
        "Timer-Einstellungen",
    )

    @Test
    fun `the overflow of ALERTING never opens the Schritte-Sheet (D-055)`() = assertMenu(
        TimerStates.alerting(),
        "Sitzung beenden",
        "Sitzung verwerfen",
        "Timer-Einstellungen",
    )

    @Test
    fun `the overflow of FINISHED`() = assertMenu(
        TimerStates.finished(),
        "Schritte bearbeiten",
        "Weiterer Aufguss",
        "Sitzung beenden",
        "Sitzung verwerfen",
        "Timer-Einstellungen",
    )

    // --- Quick timer (SCR-07, 04 sec 10) ------------------------------------------------------

    @Test
    fun `the quick timer pauses and resumes, and shows no step label (SCR-07)`() {
        show(TimerStates.quick(TimerPhase.RUNNING))

        compose.onNodeWithText("Schnelltimer").assertIsDisplayed()
        compose.onNodeWithText("Aufguss 1 von 1", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Pause").performClick()
        state = TimerStates.quick(TimerPhase.PAUSED)
        compose.onNodeWithText("Weiter").performClick()

        assertThat(events).containsExactly(TimerEvent.Pause, TimerEvent.Resume).inOrder()
    }

    @Test
    fun `Fertig ends the quick timer on its alert (SCR-07)`() {
        show(TimerStates.quick(TimerPhase.ALERTING))

        compose.onNodeWithText("Abgegossen").assertDoesNotExist()
        compose.onNodeWithText("Fertig").performClick()

        assertThat(events).containsExactly(TimerEvent.Poured)
    }

    @Test
    fun `Abbrechen in the overflow cancels the quick timer (SCR-07)`() {
        show(TimerStates.quick(TimerPhase.RUNNING))
        openMenu()
        compose.onNodeWithText("Sitzung beenden").assertDoesNotExist()
        compose.onNodeWithText("Abbrechen").performClick()

        assertThat(events).containsExactly(TimerEvent.Cancel)
    }

    @Test
    fun `the quick timer's alert has no Abbrechen`() = assertMenu(
        TimerStates.quick(TimerPhase.ALERTING),
        "Timer-Einstellungen",
    )

    @Test
    fun `a cold brew waits with Kaltes Wasser, an ice brew with Eiswuerfel (BRW-10)`() {
        show(TimerStates.coldReady(iceBrew = false))
        compose.onNodeWithText("Kaltes Wasser", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Wasserkocher auf 5\u00A0°C", useUnmergedTree = true).assertDoesNotExist()

        state = TimerStates.coldReady(iceBrew = true)
        compose.onNodeWithText("Eiswürfel", useUnmergedTree = true).assertIsDisplayed()
    }

    // --- Leaf and water on READY (BRW-11, D-097) -----------------------------------------------
    // The merged tree: the slot's invisible copy (the same text, so the pill never moves) is not in it.

    private val amounts = "4,0 g Tee · 250 ml Wasser"

    /** The kettle hint's lines, top to bottom. */
    private fun hasLines(vararg lines: String) = SemanticsMatcher("lines ${lines.toList()}") {
        it.config.getOrElse(SemanticsProperties.Text) { emptyList() }.map { text -> text.text } == lines.toList()
    }

    @Test
    fun `READY shows the session's leaf and water under the kettle hint, at every infusion (BRW-11, D-097)`() {
        show(TimerStates.ready().copy(teaName = "Tanabata"))

        compose.onNodeWithText(amounts).assertIsDisplayed()
            .assert(hasLines("Wasserkocher auf 55 °C", amounts, "Aufguss 1 · 1:00"))

        state = TimerStates.ready(stepIndex = 1).copy(teaName = "Tanabata")
        compose.onNodeWithText(amounts).assertIsDisplayed()
            .assert(hasLines("Wasserkocher auf 60 °C", amounts, "Aufguss 2 · 0:20"))
    }

    @Test
    fun `RUNNING, PAUSED, ALERTING and FINISHED show no leaf and water (BRW-11, D-097)`() {
        show(TimerStates.running())
        listOf(TimerStates.running(), TimerStates.paused(), TimerStates.alerting(), TimerStates.finished()).forEach {
            state = it
            compose.onNodeWithText(amounts).assertDoesNotExist()
        }
    }

    @Test
    fun `READY shows only what the session has, leaf or water or nothing (BRW-11, D-097)`() {
        show(TimerStates.ready().copy(leafGrams = 12.5, waterMl = null))
        compose.onNodeWithText("12,5 g Tee").assertIsDisplayed()
            .assert(hasLines("Wasserkocher auf 55 °C", "12,5 g Tee", "Aufguss 1 · 1:00"))

        state = TimerStates.ready().copy(leafGrams = null, waterMl = 300)
        compose.onNodeWithText("300 ml Wasser").assertIsDisplayed()
            .assert(hasLines("Wasserkocher auf 55 °C", "300 ml Wasser", "Aufguss 1 · 1:00"))

        state = TimerStates.ready().copy(leafGrams = null, waterMl = null)
        compose.onNodeWithText("Aufguss 1 · 1:00").assert(hasLines("Wasserkocher auf 55 °C", "Aufguss 1 · 1:00"))
    }

    @Test
    fun `the quick timer's READY shows no leaf and water (BRW-11, SCR-07)`() {
        show(TimerStates.quick(TimerPhase.READY))
        compose.onNodeWithText("g Tee", substring = true, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("ml Wasser", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `TalkBack reads the leaf and water in words with the kettle hint (BRW-11, D-097)`() {
        show(TimerStates.ready())
        compose.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ContentDescription,
                listOf("Wasserkocher auf 55 Grad, 4 Komma 0 Gramm Tee, 250 Milliliter Wasser, Aufguss 1 · 1:00"),
            ),
        ).assertIsDisplayed()
    }

    @Test
    fun `overflow items send their events or open their screens`() {
        show(TimerStates.running(stepIndex = 1))
        openMenu()
        compose.onNodeWithText("Aufguss jetzt beenden").performClick()
        openMenu()
        compose.onNodeWithText("Timer-Einstellungen").performClick()
        openMenu()
        // A session with a completed step is finished without asking.
        compose.onNodeWithText("Sitzung beenden").performClick()

        assertThat(events).containsExactly(TimerEvent.EndStepNow, TimerEvent.FinishSession).inOrder()
        assertThat(opened).containsExactly("settings")
    }

    @Test
    fun `READY skips a step and goes back to the setup before the first step`() {
        show(TimerStates.ready())
        openMenu()
        compose.onNodeWithText("Schritt überspringen").performClick()
        openMenu()
        compose.onNodeWithText("Brühen anpassen").performClick()

        assertThat(events).containsExactly(TimerEvent.SkipStep)
        assertThat(opened).containsExactly("adjust")
    }

    // --- Confirmations (TMR-12 AC1, TMR-13) -------------------------------------------------

    @Test
    fun `Sitzung verwerfen asks first`() {
        show(TimerStates.running(stepIndex = 1))
        openMenu()
        compose.onNodeWithText("Sitzung verwerfen").performClick()
        compose.onNodeWithText("Nichts wird gespeichert.").assertIsDisplayed()
        compose.onNodeWithText("Weiter brühen").performClick()
        assertThat(events).isEmpty()

        openMenu()
        compose.onNodeWithText("Sitzung verwerfen").performClick()
        compose.onNodeWithText("Verwerfen").performClick()

        assertThat(events).containsExactly(TimerEvent.Discard)
    }

    @Test
    fun `finishing without a completed step asks, because the session would be discarded`() {
        show(TimerStates.ready())
        openMenu()
        compose.onNodeWithText("Sitzung beenden").performClick()

        compose.onNodeWithText("Sitzung verwerfen?").assertIsDisplayed()
        assertThat(events).isEmpty()
        compose.onNodeWithText("Verwerfen").performClick()

        assertThat(events).containsExactly(TimerEvent.FinishSession)
    }

    // --- Alerts first (rule 8, D-051, D-055) ------------------------------------------------

    @Test
    fun `the Schritte-Sheet is removed in the same frame as the alert starts`() {
        show(TimerStates.running(stepIndex = 1))
        openMenu()
        compose.onNodeWithText("Schritte bearbeiten").performClick()
        compose.onNodeWithText("Schritte für diese Sitzung").assertIsDisplayed()

        compose.mainClock.autoAdvance = false
        state = TimerStates.alerting()
        // The same two frames in which a normal close of the sheet still animates (StepsSheetHostTest).
        repeat(2) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }

        compose.onNodeWithText("Schritte für diese Sitzung").assertDoesNotExist()
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNodeWithText("Abgegossen").assertExists()
    }

    private fun advanceTwoFrames() = repeat(2) {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    @Test
    fun `a menu left open is gone, without its exit animation, when the alert starts`() {
        show(TimerStates.running(stepIndex = 1))
        openMenu()
        compose.onNodeWithText("Sitzung verwerfen").assertIsDisplayed()

        compose.mainClock.autoAdvance = false
        state = TimerStates.alerting()
        advanceTwoFrames()

        // „Sitzung verwerfen" is an ALERTING item too: only a menu still on screen would show it.
        compose.onNode(isPopup()).assertDoesNotExist()
        compose.onNodeWithText("Sitzung verwerfen").assertDoesNotExist()
        compose.onNodeWithText("Abgegossen").assertExists()
    }

    @Test
    fun `an alert after PAUSED draws its ring at full strength in the first frame`() {
        show(TimerStates.paused())
        ring().assert(SemanticsMatcher.expectValue(RingDimKey, 0.6f))
        compose.mainClock.autoAdvance = false

        // The Live Update's *Weiter* and the deadline passed while the app was stopped: PAUSED → ALERTING.
        Snapshot.withMutableSnapshot { state = TimerStates.alerting(stepIndex = 0) }
        compose.mainClock.advanceTimeByFrame()

        ring().assert(SemanticsMatcher.expectValue(RingDimKey, 1f))
    }

    @Test
    fun `a question opened during one alert never covers the next alert`() {
        show(TimerStates.alerting(stepIndex = 0))
        openMenu()
        compose.onNodeWithText("Sitzung verwerfen").performClick()
        compose.onNodeWithText("Nichts wird gespeichert.").assertIsDisplayed()

        // *Abgegossen* and *Start* from the notification; the question stays open meanwhile.
        state = TimerStates.ready(stepIndex = 1)
        compose.waitForIdle()
        state = TimerStates.running(remainingMs = 10_000, stepIndex = 1)
        compose.waitForIdle()
        compose.onNodeWithText("Nichts wird gespeichert.").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        state = TimerStates.alerting(stepIndex = 1)
        advanceTwoFrames()

        compose.onNodeWithText("Nichts wird gespeichert.").assertDoesNotExist()
    }

    @Test
    fun `a menu or a question opened before the alert is gone when it starts`() {
        show(TimerStates.running(stepIndex = 1))
        openMenu()
        compose.onNodeWithText("Sitzung verwerfen").performClick()
        compose.onNodeWithText("Nichts wird gespeichert.").assertIsDisplayed()

        compose.mainClock.autoAdvance = false
        state = TimerStates.alerting()
        // The same two frames in which a normal close of the sheet still animates (StepsSheetHostTest).
        repeat(2) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }

        compose.onNodeWithText("Nichts wird gespeichert.").assertDoesNotExist()
    }

    // --- TalkBack (SCR-05, 03 §6) -----------------------------------------------------------

    @Test
    fun `TalkBack hears the state, the pre-alert with 10 s remaining, and the alert`() {
        show(TimerStates.running(remainingMs = 48_000))
        val announcer = compose.onNodeWithTag(TIMER_ANNOUNCEMENT_TAG)

        announcer.assert(announcement("Aufguss 1 von 3 läuft", LiveRegionMode.Polite))
        compose.mainClock.advanceTimeBy(38_000)
        announcer.assert(announcement("Gleich fertig, noch 10 Sekunden", LiveRegionMode.Polite))

        state = TimerStates.alerting()
        compose.waitForIdle()
        announcer.assert(announcement("Jetzt abgießen!", LiveRegionMode.Assertive))

        state = TimerStates.ready(stepIndex = 2)
        compose.waitForIdle()
        announcer.assert(announcement("Bereit für Aufguss 3 von 3", LiveRegionMode.Polite))
    }

    @Test
    fun `TalkBack hears 10 s remaining on its own when the pre-alert came earlier`() {
        val running = TimerStates.running(remainingMs = 48_000)
        show(running.copy(preAlertLeadSeconds = 20, preAlertAtElapsedMs = NOW + 28_000))
        val announcer = compose.onNodeWithTag(TIMER_ANNOUNCEMENT_TAG)

        compose.mainClock.advanceTimeBy(28_000)
        announcer.assert(announcement("Gleich fertig", LiveRegionMode.Polite))
        compose.mainClock.advanceTimeBy(10_000)
        announcer.assert(announcement("Noch 10 Sekunden", LiveRegionMode.Polite))
    }

    // --- Screen on and dimming (SET-17, TMR-17, SET-25) -------------------------------------

    private fun brightness() = compose.activity.window.attributes.screenBrightness

    private fun View.anyKeepsScreenOn(): Boolean =
        keepScreenOn || (this is ViewGroup && (0 until childCount).any { getChildAt(it).anyKeepsScreenOn() })

    @Test
    fun `the screen stays on while the timer shows`() {
        show(TimerStates.ready())

        assertThat(compose.activity.window.decorView.anyKeepsScreenOn()).isTrue()
    }

    @Test
    fun `a running step dims after 10 s untouched and a touch brightens it`() {
        show(TimerStates.running(remainingMs = 48_000))

        compose.mainClock.advanceTimeBy(9_000)
        assertThat(brightness()).isEqualTo(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
        compose.mainClock.advanceTimeBy(1_100)
        assertThat(brightness()).isEqualTo(DIMMED_BRIGHTNESS)

        compose.onRoot().performTouchInput {
            down(center)
            up()
        }
        compose.waitForIdle()
        assertThat(brightness()).isEqualTo(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
    }

    @Test
    fun `the alert is bright in its first frame`() {
        show(TimerStates.running(remainingMs = 48_000))
        compose.mainClock.advanceTimeBy(11_000)
        assertThat(brightness()).isEqualTo(DIMMED_BRIGHTNESS)
        compose.mainClock.autoAdvance = false

        Snapshot.withMutableSnapshot { state = TimerStates.alerting() }
        compose.mainClock.advanceTimeByFrame()

        assertThat(brightness()).isEqualTo(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
    }

    @Test
    fun `closing the menu counts as a touch, so the screen does not dim right after it`() {
        show(TimerStates.running(remainingMs = 48_000))
        openMenu()
        compose.mainClock.advanceTimeBy(12_000)
        assertThat(brightness()).isEqualTo(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)

        Espresso.pressBack()
        compose.waitForIdle()
        assertThat(brightness()).isEqualTo(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
        compose.mainClock.advanceTimeBy(10_100)

        assertThat(brightness()).isEqualTo(DIMMED_BRIGHTNESS)
    }

    @Test
    fun `the first frame after Start reads the clock fresh, not the time of READY`() {
        show(TimerStates.ready())
        compose.mainClock.advanceTimeBy(60_000)
        compose.mainClock.autoAdvance = false

        // Started now: 20 s left. A stale clock from READY would show 1:20.
        Snapshot.withMutableSnapshot { state = TimerStates.running(remainingMs = 80_000) }
        compose.mainClock.advanceTimeByFrame()

        ring().assert(hasDescriptionContaining("noch 20 Sekunden"))
    }

    @Test
    fun `the pre-alert brightens the screen`() {
        show(TimerStates.running(remainingMs = 48_000))
        compose.mainClock.advanceTimeBy(20_000)
        assertThat(brightness()).isEqualTo(DIMMED_BRIGHTNESS)

        compose.mainClock.advanceTimeBy(18_100)

        assertThat(brightness()).isEqualTo(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
    }

    @Test
    fun `the alert brightens the screen`() {
        show(TimerStates.running(remainingMs = 48_000))
        compose.mainClock.advanceTimeBy(11_000)
        assertThat(brightness()).isEqualTo(DIMMED_BRIGHTNESS)

        state = TimerStates.alerting()
        compose.waitForIdle()

        assertThat(brightness()).isEqualTo(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
    }

    @Test
    fun `Beim Ziehen abdunkeln off keeps the screen bright`() {
        settings = TimerScreenSettings(dimWhileSteeping = false)
        show(TimerStates.running(remainingMs = 48_000))

        compose.mainClock.advanceTimeBy(20_000)

        assertThat(brightness()).isEqualTo(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
    }
}
