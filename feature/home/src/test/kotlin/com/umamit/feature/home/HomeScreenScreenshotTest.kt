package com.umamit.feature.home

import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.StepKind
import com.umamit.core.model.TimerPhase
import com.umamit.core.model.TimerSlot
import com.umamit.core.testing.lowStockCard
import com.umamit.core.timer.controller.TimerToEnd
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.controller.TimerUiStep
import com.umamit.core.timer.engine.SessionKind
import com.umamit.core.timer.engine.StepStatus
import com.umamit.core.timer.reliability.ReliabilityProblem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Start (SCR-02) with the seed teas, empty, and with an active session, in
 * light and dark at font scale 1.0 and 2.0 (03 §6). Photos are the leaf
 * placeholders only (no shop images, D-016). Goldens: `src/test/screenshots/`.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class HomeScreenScreenshotTest(private val dark: Boolean, private val fontScale: Float) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}, fontScale={1}")
        fun parameters(): List<Array<Any>> = listOf(false, true).flatMap { dark ->
            listOf(1f, 2f).map { scale -> arrayOf<Any>(dark, scale) }
        }

        private const val NOW = 1_000_000L

        /** 21. Oktober 2026, 09:12 in Berlin. */
        private const val OCT_21 = 1_792_566_720_000L
        private const val DAY_MS = 86_400_000L

        /** „Welchen Timer beenden?" with a brew and the quick timer active (TMR-21, D-108). */
        val TWO_TIMERS_QUESTION = HomeQuestion(
            listOf(TimerToEnd(TimerSlot.BREW, "Bio Sencha Hisui"), TimerToEnd(TimerSlot.QUICK, null)),
        )

        val seeded = HomeUiState(
            loading = false,
            // Invented teas besides the two seed teas (D-016): six rows, the most *Zuletzt gebrüht* shows (D-110).
            recent = listOf(
                RecentBrew("h", "s6", "Bio Sencha Hisui", null, OCT_21, "Europe/Berlin", 60, 65, 3),
                RecentBrew("t", "s5", "Bio Sencha Tanabata", null, OCT_21 - DAY_MS, "Europe/Berlin", 45, 65, 3),
                RecentBrew("k", "s4", "Kabuse Morgennebel", null, OCT_21 - 2 * DAY_MS, "Europe/Berlin", 14_400, 5, 1),
                RecentBrew("g", "s3", "Gyokuro Schattenhain", null, OCT_21 - 3 * DAY_MS, "Europe/Berlin", 120, 50, 3),
                RecentBrew("b", "s2", "Bancha Herbstlaub", null, OCT_21 - 5 * DAY_MS, "Europe/Berlin", 30, 80, 2),
                RecentBrew("m", "s1", "Kukicha Mondstängel", null, OCT_21 - 9 * DAY_MS, "Europe/Berlin", 60, 70, 2),
            ),
            favorites = listOf(HomeTea("h", "Bio Sencha Hisui", null)),
        )

        val running = TimerUiState.Active(
            kind = SessionKind.Brew("s4"),
            teaName = "Bio Sencha Hisui",
            phase = TimerPhase.RUNNING,
            stepIndex = 1,
            steps = listOf(
                TimerUiStep(StepKind.INFUSION, 60, 65, StepStatus.DONE),
                TimerUiStep(StepKind.INFUSION, 10, 65, StepStatus.STARTED),
                TimerUiStep(StepKind.INFUSION, 10, 65, StepStatus.PENDING),
            ),
            deadlineElapsedMs = NOW + 7_400,
            deadlineWallMs = OCT_21,
            pausedRemainingMs = null,
            preAlertLeadSeconds = null,
            preAlertAtElapsedMs = null,
            preAlertPlayed = false,
            longStep = false,
            kettleTempC = 65,
            demoted = false,
            finishWouldDiscard = false,
            preAlertBaseSeconds = 10,
        )
    }

    @get:Rule
    val compose = createComposeRule()

    private val variant get() = "${if (dark) "dark" else "light"}_font${fontScale.toString().replace('.', '_')}"

    private fun capture(name: String, state: HomeUiState) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            UmamiTheme(darkTheme = dark, reduceMotion = true) {
                HomeScreen(state = state, nowElapsedMs = { NOW })
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/Home_${name}_$variant.png")
    }

    @Test
    fun seeded() = capture("seeded", seeded)

    @Test
    fun longNames() {
        val long = "Bio Gyokuro Asahi Premium aus Yame, Frühjahrsernte"
        capture(
            "longNames",
            seeded.copy(
                recent = listOf(RecentBrew("l", "s3", long, null, OCT_21, "Europe/Berlin", 60, 65, 3)),
                favorites = listOf(HomeTea("l", long, null)),
            ),
        )
    }

    /** The quiet low-stock card in the nudge slot (INV-10, D-097), with a long name that wraps. */
    @Test
    fun lowStock() = capture(
        "lowStock",
        seeded.copy(lowStock = lowStockCard(teaName = "Bio Sencha Tanabata", grams = 14.0)),
    )

    /** An empty tea without INV-09's link: only *Ausblenden*. */
    @Test
    fun lowStockEmpty() = capture(
        "lowStockEmpty",
        seeded.copy(lowStock = lowStockCard(teaName = "Bio Sencha Tanabata", grams = 0.0, reorderUrl = null)),
    )

    /** The reliability banner when the phone's alarm volume is 0: „Alarmlautstärke ist aus" (TMR-31, D-098). */
    @Test
    fun alarmVolumeOff() = capture(
        "alarmVolumeOff",
        seeded.copy(reliabilityProblem = ReliabilityProblem.AlarmVolumeOff),
    )

    @Test
    fun empty() = capture("empty", HomeUiState(loading = false, collectionEmpty = true))

    @Test
    fun activeSession() = capture("activeSession", seeded.copy(active = running))

    @Test
    fun question() = capture(
        "question",
        seeded.copy(active = running, twoTimersActive = true, question = TWO_TIMERS_QUESTION),
    )
}
