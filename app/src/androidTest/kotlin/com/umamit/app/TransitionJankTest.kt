package com.umamit.app

import android.Manifest
import android.app.Application
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.umamit.core.data.SeedData
import com.umamit.core.data.SessionStart
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.StepKind
import com.umamit.core.model.TimerPhase
import com.umamit.core.timer.controller.BrewSessionController
import com.umamit.core.timer.controller.TimerHost
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.controller.send
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.StepRecord
import com.umamit.core.timer.engine.TimerEvent
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.AssumptionViolatedException
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId
import java.util.regex.Pattern
import kotlin.math.roundToLong
import com.umamit.core.designsystem.R as DsR
import com.umamit.feature.brew.R as BrewR
import com.umamit.feature.collection.R as CollectionR
import com.umamit.feature.home.R as HomeR
import com.umamit.feature.journal.R as JournalR

/**
 * The frame metric of the transition audit (NFR-PERF-12, D-117 Q15–Q17 „Metric test", #285): each
 * in-scope transition runs [JankGate.runs] times on the debug build in the CI emulator job, JankStats
 * records the frames of every app window ([FrameRecorder]), and one line per transition reports the
 * median run's frames over the budget and its longest frame ([JankReport]). Each test first measures a
 * trivial [ReferenceTransition] in the same window, and each line also gives the transition relative to
 * it (D-117 build note „Jank gate"). It only reports until the gate is switched on ([JankGate]); the
 * gate then fails a test whose transition is over the relative limit, from API 36 on.
 *
 * The screens are driven through UiAutomator with real taps and back presses: a Compose test rule
 * would pace every composition with its own test clock and so never show a real frame's cost. The
 * animations run at scale 1 for this class (the CI emulator has them off), restored afterwards.
 *
 * Data: one tea for the detail, the tea picker, the setup and the timer, and a finished session of a
 * second tea for the journal and the „Gespeichert" sheet, written once through the app's own stores
 * ([TestDataEntryPoint]); nothing is fetched. The timer is only ever opened with its session waiting
 * and is then discarded: no step runs, so the test never waits on, delays or rings an alert (rule 8).
 *
 * Not covered: a cold start after process death (the test runs in the app's process, so it measures
 * a cold activity start) and the real lock screen (STARTED → RESUMED after stop stands in for it).
 * In report-only mode a UI step that fails skips its test with the reason instead of failing it.
 */
@RunWith(AndroidJUnit4::class)
class TransitionJankTest {
    @get:Rule(order = 0)
    val unlocked = UnlockedDeviceRule()

    @get:Rule(order = 1)
    val setupDone = SetupDoneRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val data = EntryPointAccessors.fromApplication(
        context.applicationContext as Application,
        TestDataEntryPoint::class.java,
    )
    private val controller = (context.applicationContext as TimerHost).timerInputs as BrewSessionController
    private val recorder = FrameRecorder()
    private val reports = mutableListOf<JankReport>()
    private val savedScales = mutableMapOf<String, String>()
    private var scenario: ActivityScenario<MainActivity>? = null
    private var budgetNs = DEFAULT_BUDGET_NS
    private var reference: JankReference? = null

    private val teaName = SeedData.tanabata.name
    private val sessionTeaName = SeedData.teas.single { it.id == SeedData.HISUI_TEA_ID }.name

    @Before
    fun setUp() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        ANIMATION_SCALES.forEach { key ->
            savedScales[key] = shell("settings get global $key").trim()
            shell("settings put global $key 1")
        }
        runBlocking { seedData() }
        endTimer()
    }

    @After
    fun tearDown() {
        endTimer()
        runCatching { data.savedSheets().dismiss(SESSION_ID) }
        scenario?.close()
        recorder.release()
        savedScales.forEach { (key, value) ->
            if (value.isBlank() ||
                value == "null"
            ) {
                shell("settings delete global $key")
            } else {
                shell("settings put global $key $value")
            }
        }
    }

    @Test
    fun coldStartToStart() = measured {
        repeat(JankGate.runs) {
            scenario?.close()
            scenario = null
            Thread.sleep(PAUSE_MS)
            record("cold start → Start", COLD_SETTLE_MS) {
                scenario = ActivityScenario.launch(MainActivity::class.java)
            }
            awaitStart()
        }
    }

    @Test
    fun resumeAfterStop() = measured {
        launchOnStart()
        repeat(JankGate.runs) {
            scenario!!.moveToState(Lifecycle.State.CREATED)
            Thread.sleep(PAUSE_MS)
            record("resume after stop") { scenario!!.moveToState(Lifecycle.State.RESUMED) }
            awaitStart()
        }
    }

    @Test
    fun tabSwitches() = measured {
        launchOnStart()
        val tabs = Tab.entries.map { context.getString(it.label) }
        repeat(JankGate.runs) {
            (tabs + tabs.first()).zipWithNext().forEach { (from, to) ->
                val target = tab(to)
                record("tab $from → $to") { target.click() }
            }
        }
    }

    @Test
    fun teaDetailPushAndPop() = measured {
        launchOnStart()
        val myTeas = context.getString(CollectionR.string.collection_segment_my_teas)
        tab(context.getString(Tab.Collection.label)).click()
        await(By.text(myTeas), "Meine Tees").click()
        Thread.sleep(SETTLE_MS)
        repeat(JankGate.runs) {
            val row = await(text(teaName), "the tea in Meine Tees")
            record("tea detail push") { row.click() }
            awaitGone(By.text(myTeas), "Meine Tees under the tea detail")
            record("tea detail pop") { device.pressBack() }
            await(By.text(myTeas), "Meine Tees after back")
        }
    }

    @Test
    fun sessionPagePushAndPop() = measured {
        launchOnStart()
        tab(context.getString(Tab.Journal.label)).click()
        Thread.sleep(SETTLE_MS)
        // The loaded page is the hero layout (SCR-16, D-112) without a „Sitzung" title (that shows only while it
        // loads or for a missing session), so its ← (Zurück) marks it: nothing on the Journal tab carries one.
        val page = By.desc(context.getString(DsR.string.ds_back))
        repeat(JankGate.runs) {
            val row = await(text(sessionTeaName), "the session in the journal")
            record("session page push") { row.click() }
            await(page, "the session page (its Zurück)")
            record("session page pop") { device.pressBack() }
            awaitGone(page, "the session page after back")
        }
    }

    @Test
    fun savedSheetInAndOut() = measured {
        launchOnStart()
        val title = context.getString(JournalR.string.journal_saved_title)
        val requests = data.savedSheets()
        repeat(JankGate.runs) {
            record("Gespeichert sheet in") { requests.showSavedSheet(SESSION_ID) }
            await(text(title), "the Gespeichert sheet")
            record("Gespeichert sheet out") { requests.dismiss(SESSION_ID) }
            awaitGone(text(title), "the Gespeichert sheet after its dismissal")
        }
    }

    @Test
    fun teaPickerInAndOut() = measured {
        launchOnStart()
        val pickerTitle = By.text(context.getString(BrewR.string.brew_picker_title))
        repeat(JankGate.runs) {
            val startBrew = await(text(context.getString(HomeR.string.home_start_brew)), "Aufguss starten")
            record("Teeauswahl in") { startBrew.click() }
            await(pickerTitle, "the Teeauswahl")
            record("Teeauswahl out") { device.pressBack() }
            awaitGone(pickerTitle, "the Teeauswahl after back")
        }
    }

    @Test
    fun stepsSheetInAndOut() = measured {
        launchOnStart()
        openSetup()
        val title = By.text(context.getString(BrewR.string.brew_steps_title))
        val done = By.text(context.getString(BrewR.string.brew_steps_done))
        repeat(JankGate.runs) {
            val edit =
                find(By.desc(context.getString(BrewR.string.brew_setup_steps_edit_description)), "Bearb. in the setup")
            record("Schritte-Sheet in") { edit.click() }
            await(title, "the Schritte-Sheet")
            val close = await(done, "Fertig in the Schritte-Sheet")
            record("Schritte-Sheet out") { close.click() }
            awaitGone(title, "the Schritte-Sheet after Fertig")
        }
        device.pressBack()
    }

    @Test
    fun setupToTimerAndTimerToShell() = measured {
        launchOnStart()
        repeat(JankGate.runs) {
            openSetup()
            val start = find(By.text(context.getString(BrewR.string.brew_setup_start)), "Brühen starten")
            // The session waits for its first step: nothing is scheduled and no alert can come (rule 8).
            record("setup → timer") { start.click() }
            // Fails loudly should *Brühen starten* ever start the step: a step here would arm a real alert.
            val phase = (controller.state.value as? TimerUiState.Active)?.phase
            check(phase == TimerPhase.READY) { "the session after Brühen starten is $phase, not waiting (READY)" }
            // Discarding the waiting session closes the timer route (TimerRoute's onClose) into the shell.
            record("timer → shell") { endTimer() }
            // Not the „Start" text: the waiting timer shows a Start button too.
            awaitStart()
        }
    }

    /**
     * Measures the reference transition, runs the test's transitions, reports each against the reference, then
     * applies the relative gate (or skips a failed UI step in report mode).
     */
    private fun measured(block: () -> Unit) {
        val referenceFailure = runCatching(::measureReference).exceptionOrNull()
        val failure = runCatching(block).exceptionOrNull() ?: referenceFailure
        val windows = recorder.windowCount
        reference?.let { Log.i(TAG, it.line(budgetNs, windows)) }
        reports.forEach { Log.i(TAG, it.line(budgetNs, windows, reference)) }
        failure?.let(::uiStepFailed)
        val over = reports.filterNot { it.passes(reference) }
        if (JankGate.active && over.isNotEmpty()) {
            throw AssertionError(
                "NFR-PERF-12: over the relative limit (median longest frame ≤ ${JankGate.STALL_FACTOR}× " +
                    "the reference's typical frame, janky frames ≤ the reference's scaled " +
                    "+ ${JankGate.JANKY_ALLOWANCE}):\n" +
                    over.joinToString("\n") { it.line(budgetNs, windows, reference) },
            )
        }
    }

    /**
     * [JankGate.runs] runs of [ReferenceTransition] in the activity's window, after one unmeasured warm-up crossfade,
     * measured as every transition is. It needs no session and arms nothing (rule 8).
     */
    private fun measureReference() {
        launchOnStart()
        val transition = ReferenceTransition(scenario!!)
        transition.attach()
        try {
            Thread.sleep(SETTLE_MS)
            transition.toggle()
            Thread.sleep(SETTLE_MS)
            val runs = List(JankGate.runs) { JankRun(recorder.measure(SETTLE_MS, transition::toggle), budgetNs) }
            reference = JankReference(JankReport(ReferenceTransition.NAME, runs))
        } finally {
            transition.detach()
        }
        Thread.sleep(PAUSE_MS)
    }

    /** A UI step that failed fails the test with the gate on; in report-only mode it skips it, with the reason. */
    private fun uiStepFailed(failure: Throwable): Nothing {
        val line = "${JankReport.LINE_PREFIX} UI step failed (gate=${JankGate.describe}): ${failure.message}"
        Log.e(TAG, line, failure)
        if (JankGate.active) throw failure
        throw AssumptionViolatedException("report-only: a UI step failed: ${failure.message}")
    }

    /** One measured run of [transition]: the frames of [action] and of [settleMs] after it. */
    private fun record(transition: String, settleMs: Long = SETTLE_MS, action: () -> Unit) {
        val run = JankRun(recorder.measure(settleMs, action), budgetNs)
        readBudget()
        val index = reports.indexOfFirst { it.transition == transition }
        if (index < 0) {
            reports += JankReport(transition, listOf(run))
        } else {
            reports[index] = reports[index].copy(runs = reports[index].runs + run)
        }
    }

    /** The frame budget from the display's refresh rate (D-117: 60 Hz, 16,7 ms). */
    private fun readBudget() {
        scenario?.onActivity { activity ->
            val rate = activity.display.refreshRate
            if (rate > 0f) budgetNs = (NANOS_PER_SECOND / rate).roundToLong()
        }
    }

    /** The activity on Start; launched only when no activity is open (the reference may have launched it). */
    private fun launchOnStart() {
        if (scenario == null) scenario = ActivityScenario.launch(MainActivity::class.java)
        awaitStart()
        readBudget()
        Thread.sleep(SETTLE_MS)
    }

    private fun awaitStart() {
        await(text(context.getString(HomeR.string.home_quick_timer)), "Start")
    }

    /** Start → Teeauswahl → the tea's setup, unmeasured. */
    private fun openSetup() {
        await(text(context.getString(HomeR.string.home_start_brew)), "Aufguss starten").click()
        await(By.text(context.getString(BrewR.string.brew_picker_title)), "the Teeauswahl")
        await(text(teaName), "the tea in the Teeauswahl").click()
        await(By.text(context.getString(BrewR.string.brew_setup_title)), "the setup")
        Thread.sleep(SETTLE_MS)
    }

    /** The navigation item [label]: the lowest of the texts (the Journal's title is „Journal" too). */
    private fun tab(label: String): UiObject2 {
        await(By.text(label), "the tab $label")
        return device.findObjects(By.text(label)).maxBy { it.visibleBounds.bottom }
    }

    private fun text(value: String): BySelector =
        By.text(Pattern.compile(".*${Pattern.quote(value)}.*", Pattern.DOTALL))

    private fun await(selector: BySelector, what: String): UiObject2 =
        device.wait(Until.findObject(selector), UI_TIMEOUT_MS) ?: throw AssertionError("$what is not shown")

    private fun awaitGone(selector: BySelector, what: String) {
        if (device.wait(Until.gone(selector), UI_TIMEOUT_MS) != true) throw AssertionError("$what is still shown")
    }

    /** [selector] on screen, scrolling the screen's scrollable content down to it when needed. */
    private fun find(selector: BySelector, what: String): UiObject2 {
        device.wait(Until.findObject(selector), SHORT_TIMEOUT_MS)?.let { return it }
        device.findObject(By.scrollable(true))?.scrollUntil(Direction.DOWN, Until.findObject(selector))
        return await(selector, what)
    }

    /** Discards a waiting session (and one an earlier test class left), so a start is never refused (TMR-21). */
    private fun endTimer() = runBlocking {
        if (controller.state.value is TimerUiState.Active) controller.send(TimerEvent.Discard)
    }

    /**
     * Once per install: the tea (with its vessels, recipes and stock) and a finished session of the
     * second tea, through the app's stores. The first start's seed never runs here ([SetupDoneRule]).
     */
    private suspend fun seedData() {
        SeedData.vessels.forEach { if (data.vessels().getVessel(it.id) == null) data.vessels().saveVessel(it) }
        val now = System.currentTimeMillis()
        SeedData.teas.forEach { tea ->
            if (data.teas().getTea(tea.id) == null) {
                data.teas().saveTea(tea)
                SeedData.recipes.filter { it.teaId == tea.id }.forEach { data.recipes().saveRecipe(it) }
                SeedData.initialStock(now).filter { it.teaId == tea.id }.forEach { data.stock().addEvent(it) }
            }
        }
        if (data.sessions().getSession(SESSION_ID) == null) {
            val started = now - SESSION_AGO_MS
            data.sessions().startSession(
                SessionStart(
                    id = SESSION_ID,
                    snapshot = sessionSnapshot(),
                    steps = listOf(PlannedStep(StepKind.INFUSION, STEP_SECONDS, tempC = TEMP_C, preAlertSeconds = 0)),
                    preAlertSeconds = 0,
                    startedAtMs = started,
                    zoneId = ZoneId.systemDefault().id,
                ),
            )
            data.sessions().recordStep(
                SESSION_ID,
                0,
                StepRecord(
                    plannedSeconds = STEP_SECONDS,
                    actualSeconds = STEP_SECONDS,
                    pausedSeconds = 0,
                    overtimeSeconds = 0,
                    startedAtWallMs = started,
                    endedAtWallMs = started + STEP_SECONDS * MILLIS_PER_SECOND,
                    skipped = false,
                    overtimeAutoAck = false,
                ),
            )
            data.sessions().completeSession(SESSION_ID, autoFinished = false)
        }
    }

    private fun sessionSnapshot() = SessionSnapshot(
        teaId = SeedData.HISUI_TEA_ID,
        teaName = sessionTeaName,
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
        baseTempC = TEMP_C,
    )

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .use { it.readBytes().decodeToString() }

    private companion object {
        const val TAG = "TransitionJankTest"

        /** The journal session of this test, fixed so it is written once per install. */
        const val SESSION_ID = "7a3c1e52-90d4-4b8f-a6e1-2f5d8c0b9e47"
        const val STEP_SECONDS = 60
        const val TEMP_C = 70
        const val SESSION_AGO_MS = 60 * 60 * 1000L
        const val MILLIS_PER_SECOND = 1000L

        /** Longer than the longest transition of 03 §3.6, so a measurement holds its whole animation. */
        const val SETTLE_MS = 1_200L
        const val COLD_SETTLE_MS = 2_500L
        const val PAUSE_MS = 500L
        const val UI_TIMEOUT_MS = 10_000L
        const val SHORT_TIMEOUT_MS = 1_000L
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val DEFAULT_BUDGET_NS = 16_666_667L

        val ANIMATION_SCALES = listOf("animator_duration_scale", "transition_animation_scale", "window_animation_scale")
    }
}
