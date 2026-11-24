package com.umamit.app

import android.Manifest
import android.app.Application
import android.app.KeyguardManager
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
import com.umamit.core.timer.UnlockedDevice
import com.umamit.core.timer.UnlockedDeviceRule
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
 * it (D-117 build note „Jank gate"). From API 36 on the gate is on by default ([JankGate]): it fails a
 * test whose reference is not usable or whose gated transition is over the relative limit; the
 * transitions in [JankGate.REPORT_ONLY], and every transition on API 35, only report.
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
 * a cold activity start) The locked close of the timer is measured report-only ([timerToLockScreen]).
 * A UI step that fails fails a gated test once the gate is on; otherwise (API 35, the gate switched
 * off, or a test with only [JankGate.REPORT_ONLY] transitions) it skips the test with the reason.
 *
 * The turns of the phone (D-136 build note „Portrait timer", SCR-05, NFR-COMP-02) are the exception: their checks (no
 * new activity, the timer upright, Journal turned) fail on API 35 and 36 alike, whatever the gate; their frames are
 * reported only ([rotation]). Still no alert rings here: the alert turned with the phone is in
 * [TimerAlertOverLockScreenTest].
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
    private val rotation = DisplayRotation(::shell)
    private val creations = MainActivityCreations()

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
        dismissForeignAnr()
    }

    @After
    fun tearDown() {
        creations.stop()
        rotation.restore()
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
    fun coldStartToStart() = measured(gated = false) {
        repeat(JankGate.runs) {
            scenario?.close()
            scenario = null
            Thread.sleep(PAUSE_MS)
            dismissForeignAnr()
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
    fun tabSwitches() = measured(gated = false) {
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
        // The loaded page is the hero layout (SCR-16, D-112) without a „Session" title (that shows only while it
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
            val start = find(By.text(context.getString(BrewR.string.brew_setup_start)), "Session starten")
            // The session waits for its first step: nothing is scheduled and no alert can come (rule 8).
            record("setup → timer") { start.click() }
            // Fails loudly should *Session starten* ever start the step: a step here would arm a real alert.
            val phase = (controller.state.value as? TimerUiState.Active)?.phase
            check(phase == TimerPhase.READY) { "the session after Session starten is $phase, not waiting (READY)" }
            // Discarding the waiting session closes the timer route (TimerRoute's onClose) into the shell.
            record("timer → shell") { endTimer() }
            // Not the „Start" text: the waiting timer shows a Start button too.
            awaitStart()
        }
    }

    /**
     * The timer over the keyguard, closed with back (#294, NFR-PERF-12, audit A-17 „Measurable"): reported only,
     * never gated. A waiting session (no alert, rule 8), the swipe keyguard on, `KEYCODE_SLEEP` and `KEYCODE_WAKEUP`
     * (the timer shows over the keyguard), then back is the locked close. Where the keyguard cannot be reached the
     * step fails and, being report-only, the test is skipped with the reason. The device is left unlocked.
     */
    @Test
    fun timerToLockScreen() = measured(gated = false) {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val lockWasDisabled = shell("locksettings get-disabled").trim() == "true"
        shell("locksettings set-disabled false")
        try {
            launchOnStart()
            repeat(JankGate.runs) {
                openSetup()
                await(By.text(context.getString(BrewR.string.brew_setup_start)), "Session starten").click()
                // The session appears asynchronously after the click: poll for READY instead of reading once.
                var phase: TimerPhase? = null
                val ready = waitFor {
                    phase = (controller.state.value as? TimerUiState.Active)?.phase
                    phase == TimerPhase.READY
                }
                check(ready) { "the session after Session starten is $phase, not waiting (READY)" }
                Thread.sleep(SETTLE_MS)
                shell("input keyevent KEYCODE_SLEEP")
                if (!waitFor {
                        keyguard.isKeyguardLocked
                    }
                ) {
                    throw AssertionError("no keyguard after sleep on this image")
                }
                shell("input keyevent KEYCODE_WAKEUP")
                device.wait(Until.hasObject(By.pkg(context.packageName)), UI_TIMEOUT_MS)
                Thread.sleep(SETTLE_MS)
                if (!keyguard.isKeyguardLocked) throw AssertionError("the keyguard is gone after wake-up")
                shell("dumpsys gfxinfo com.android.systemui reset")
                record("timer → lock screen") { device.pressBack() }
                logSystemUiFrames()
                UnlockedDevice.unlock()
                endTimer()
                awaitStart()
            }
        } finally {
            if (lockWasDisabled) shell("locksettings set-disabled true")
            runCatching { UnlockedDevice.unlock() }
        }
    }

    /**
     * The phone turned with the timer on top (D-136 Q5, build note „Portrait timer"): the timer stays upright and the
     * activity is not created again, on every turn; the session is untouched. Then the control, with the same turns:
     * once the timer is closed, the shell does turn to landscape, in place too, so the upright timer was the app's
     * request and not a display that never turned.
     */
    @Test
    fun rotateWithTimerOnTop() = rotation {
        launchOnStart()
        openSetup()
        await(By.text(context.getString(BrewR.string.brew_setup_start)), "Session starten").click()
        val session = awaitWaitingSession()
        awaitTimer()
        val created = creations.count
        repeat(JankGate.runs) {
            record("timer turned to landscape") { rotation.landscape() }
            checkUpright("the timer after the turn to landscape")
            record("timer turned to portrait") { rotation.portrait() }
            checkUpright("the timer after the turn back")
        }
        checkCreations(created, "turning the phone with the timer on top")
        checkSession(session)

        rotation.landscape()
        checkUpright("the timer held in landscape")
        endTimer()
        awaitTurned("the shell after the timer closed in landscape")
        // Not *Schnelltimer* (awaitStart): in landscape it can sit below the fold; *Aufguss starten* is at the top.
        await(text(context.getString(HomeR.string.home_start_brew)), "Start in landscape after the timer")
        checkCreations(created, "leaving the timer in landscape")
    }

    /**
     * The timer entered from Journal held in landscape, through the active brew bar, and left again (D-136 Q5, build
     * note „Portrait timer"): it comes up upright, Journal turns back to landscape, and neither creates the activity
     * again; the waiting session is the same throughout.
     */
    @Test
    fun timerFromLandscapeJournal() = rotation {
        launchOnStart()
        openSetup()
        await(By.text(context.getString(BrewR.string.brew_setup_start)), "Session starten").click()
        val session = awaitWaitingSession()
        awaitTimer()
        // Back keeps the session (03 §2.3): the shell with the bar.
        device.pressBack()
        awaitStart()
        tab(context.getString(Tab.Journal.label)).click()
        await(text(sessionTeaName), "the session in the journal")
        rotation.landscape()
        awaitTurned("Journal in landscape")
        val created = creations.count
        repeat(JankGate.runs) {
            // The bar is one element with a spoken description (its texts are cleared); Journal's running row has the
            // tea's name as text, so the text alone would match that row instead.
            val bar = await(desc(teaName), "the active brew bar on Journal")
            record("landscape Journal → timer") { bar.click() }
            awaitTimerOverJournal()
            checkUpright("the timer entered from Journal in landscape")
            checkSession(session)
            record("timer → landscape Journal") { device.pressBack() }
            await(text(sessionTeaName), "the session in the journal after back")
            awaitTurned("Journal after the timer")
        }
        checkCreations(created, "entering and leaving the timer from Journal in landscape")
        checkSession(session)
    }

    /**
     * Journal still turns (D-136 Q5: the rest of the app rotates, NFR-COMP-02), now in place: the display and the
     * activity's configuration go to landscape and back, Journal stays shown, and no activity is created again.
     */
    @Test
    fun journalStillRotates() = rotation {
        launchOnStart()
        tab(context.getString(Tab.Journal.label)).click()
        await(text(sessionTeaName), "the session in the journal")
        val created = creations.count
        repeat(JankGate.runs) {
            record("Journal turned to landscape") { rotation.landscape() }
            awaitTurned("Journal after the turn to landscape")
            await(text(sessionTeaName), "the session in the journal in landscape")
            record("Journal turned to portrait") { rotation.portrait() }
            awaitUpright("Journal after the turn back")
        }
        checkCreations(created, "turning the phone on Journal")
    }

    /**
     * The turns of the phone: the reference and the frames as every test measures them, reported only (all in
     * [JankGate.REPORT_ONLY]); but every check in [block] and every UI step fails the test, on API 35 and 36 alike,
     * never skips it (D-136 build note „Portrait timer").
     */
    private fun rotation(block: () -> Unit) {
        rotation.save()
        creations.start()
        runCatching(::measureReference).onFailure { Log.w(TAG, "reference not measured: ${it.message}", it) }
        try {
            block()
        } finally {
            val windows = recorder.windowCount
            reference?.let { Log.i(TAG, it.line(budgetNs, windows)) }
            reports.forEach { Log.i(TAG, it.line(budgetNs, windows, reference)) }
        }
        check(
            reports.all {
                it.transition in JankGate.REPORT_ONLY
            },
        ) { "a turn of the phone measured a gated transition" }
    }

    /** The waiting session (no step runs, so no alert can come, rule 8); its id. */
    private fun awaitWaitingSession(): String {
        var active: TimerUiState.Active? = null
        val ready = waitFor {
            active = controller.state.value as? TimerUiState.Active
            active?.phase == TimerPhase.READY
        }
        check(ready) { "the session after Session starten is ${active?.phase}, not waiting (READY)" }
        return checkNotNull(active?.sessionId) { "the waiting session has no id: $active" }
    }

    /** The timer on top of Journal: the shell is not composed under it, so Journal's finished session is gone. */
    private fun awaitTimerOverJournal() {
        awaitGone(text(sessionTeaName), "Journal under the timer")
        await(text(teaName), "the tea on the timer")
    }

    /** The timer on top: the shell is not composed under it, so Start's *Schnelltimer* is gone. */
    private fun awaitTimer() {
        awaitGone(text(context.getString(HomeR.string.home_quick_timer)), "Start under the timer")
        await(text(teaName), "the tea on the timer")
    }

    private fun checkSession(id: String) {
        val active = controller.state.value as? TimerUiState.Active
        check(active?.sessionId == id && active.phase == TimerPhase.READY) {
            "the session changed: ${controller.state.value}, expected $id waiting (READY)"
        }
    }

    private fun checkCreations(before: Int, what: String) {
        check(creations.count == before) {
            "${creations.count - before} new MainActivity after $what; the rotation must be handled in place\n" +
                screenState()
        }
    }

    /** Still upright after a moment: the display must not turn at all (a turn would take less than [SETTLE_MS]). */
    private fun checkUpright(what: String) {
        Thread.sleep(SETTLE_MS)
        val upright = Upright.read()
        check(upright?.portrait == true) { "$what is not upright: $upright\n${rotation.state()}\n${screenState()}" }
    }

    private fun awaitUpright(what: String) {
        check(waitFor { Upright.read()?.portrait == true }) {
            "$what is not upright: ${Upright.read()}\n${rotation.state()}"
        }
    }

    private fun awaitTurned(what: String) {
        check(waitFor { Upright.read()?.landscape == true }) {
            "$what did not turn to landscape: ${Upright.read()}\n${rotation.state()}\n${screenState()}"
        }
    }

    /** The system UI's own frame summary after the locked close, if the image reports it; logged only. */
    private fun logSystemUiFrames() {
        val summary = shell("dumpsys gfxinfo com.android.systemui").lines()
            .filter { it.trim().startsWith("Total frames rendered") || it.trim().startsWith("Janky frames") }
            .joinToString(" | ") { it.trim() }
        Log.i(TAG, "${JankReport.LINE_PREFIX} systemui after timer → lock screen: $summary")
    }

    private fun waitFor(timeoutMs: Long = UI_TIMEOUT_MS, condition: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (condition()) return true
            Thread.sleep(POLL_MS)
        }
        return condition()
    }

    /**
     * Measures the reference transition, runs the test's transitions, reports each against the reference, then
     * applies the relative gate: with the gate on, an unusable reference fails every test, and a transition outside
     * [JankGate.REPORT_ONLY] over the limit fails its test. [gated] is false for a test whose transitions are all in
     * [JankGate.REPORT_ONLY]: a UI step that fails in it then skips it even with the gate on.
     */
    private fun measured(gated: Boolean = true, block: () -> Unit) {
        val referenceFailure = runCatching(::measureReference).exceptionOrNull()
        val failure = runCatching(block).exceptionOrNull()
        val windows = recorder.windowCount
        reference?.let { Log.i(TAG, it.line(budgetNs, windows)) }
        reports.forEach { Log.i(TAG, it.line(budgetNs, windows, reference)) }
        check(gated || reports.all { it.transition in JankGate.REPORT_ONLY }) {
            "a test with gated = false measured a gated transition"
        }
        referenceFailure?.let { uiStepFailed(it, gated = true) }
        failure?.let { uiStepFailed(it, gated) }
        if (JankGate.active && reference?.usable != true) {
            throw AssertionError("NFR-PERF-12: the reference is not usable: ${reference?.line(budgetNs, windows)}")
        }
        val over = reports.filter { JankGate.gates(it.transition) && !it.passes(reference) }
        if (over.isNotEmpty()) {
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

    /**
     * A UI step that failed fails the test when the gate is on and the step's test is [gated]; otherwise it skips the
     * test, with the reason.
     */
    private fun uiStepFailed(failure: Throwable, gated: Boolean): Nothing {
        val gate = if (gated) JankGate.describe else "report"
        val line = "${JankReport.LINE_PREFIX} UI step failed (gate=$gate): ${failure.message}"
        Log.e(TAG, line, failure)
        if (JankGate.active && gated) throw failure
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
        if (scenario == null) {
            dismissForeignAnr()
            scenario = ActivityScenario.launch(MainActivity::class.java)
        }
        awaitStart()
        readBudget()
        Thread.sleep(SETTLE_MS)
    }

    /**
     * Another app's „isn't responding" dialog answered with „Wait" before a launch, so it does not cover the app's
     * screen ([ForeignAnrDialog]); an ANR of UmamiT fails the test.
     */
    private fun dismissForeignAnr() =
        ForeignAnrDialog.dismiss(device, ::shell, setOf(context.packageName, instrumentation.context.packageName))

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

    private fun desc(value: String): BySelector =
        By.desc(Pattern.compile(".*${Pattern.quote(value)}.*", Pattern.DOTALL))

    private fun text(value: String): BySelector =
        By.text(Pattern.compile(".*${Pattern.quote(value)}.*", Pattern.DOTALL))

    private fun await(selector: BySelector, what: String): UiObject2 =
        device.wait(Until.findObject(selector), UI_TIMEOUT_MS)
            ?: throw AssertionError("$what is not shown\n${screenState()}")

    /** What the screen shows instead, for a failed wait: the resumed activities, the focused window, the keyguard. */
    private fun screenState(): String = listOf(
        Dumpsys.matchingLines(shell("dumpsys activity activities"), listOf("ResumedActivity", "mFocusedApp")),
        Dumpsys.matchingLines(shell("dumpsys window"), listOf("mCurrentFocus", "mKeyguardShowing", "mDreaming")),
    ).filter { it.isNotBlank() }.joinToString("\n")

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
        const val POLL_MS = 250L
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val DEFAULT_BUDGET_NS = 16_666_667L

        val ANIMATION_SCALES = listOf("animator_duration_scale", "transition_animation_scale", "window_animation_scale")
    }
}
