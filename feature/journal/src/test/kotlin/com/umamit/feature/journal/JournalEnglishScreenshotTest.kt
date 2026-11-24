package com.umamit.feature.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.SessionStatus
import com.umamit.feature.journal.JournalSessions.TODAY
import com.umamit.feature.journal.JournalSessions.session
import com.umamit.feature.journal.JournalSessions.step
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * SCR-15 in English (GEN-10, #359), light and dark at font scale 1.0: the journal with sessions (running, today,
 * yesterday, an older date), the filter chips and the empty journal. Photos are the placeholder illustration only.
 * Goldens: `src/test/screenshots/`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w411dp-h891dp-xhdpi")
class JournalEnglishScreenshotTest(private val dark: Boolean) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun parameters(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))

        private val PLACEHOLDER_PHOTO = File("placeholder.jpg")
    }

    @get:Rule
    val compose = createComposeRule()

    private val variant get() = if (dark) "dark" else "light"

    private fun at(date: LocalDate, hour: Int, minute: Int) = LocalDateTime.of(date, LocalTime.of(hour, minute))

    private val sessions = listOf(
        session(
            "running",
            at(TODAY, 10, 5),
            teaName = "Bio Sencha Tanabata",
            status = SessionStatus.IN_PROGRESS,
            steps = listOf(step(0, 60), step(1, 20, actualSeconds = null), step(2, 60, actualSeconds = null)),
            photo = PLACEHOLDER_PHOTO,
        ),
        session(
            "hisui",
            at(TODAY, 9, 12),
            photo = PLACEHOLDER_PHOTO,
            rating = 4.5,
            brewingRating = 3.5,
            notes = "Very fresh, grassy finish",
        ),
        session(
            "early",
            at(TODAY, 7, 21),
            teaName = "Gyokuro Asahi",
            styleKey = "gyokuro",
            baseTempC = 50,
            steps = listOf(step(0, 120), step(1, 20)),
            plannedStepCount = 3,
            rating = 4.0,
        ),
        session(
            "yesterday",
            at(TODAY.minusDays(1), 18, 40),
            teaName = "Hojicha Kaga",
            styleKey = "hojicha",
            baseTempC = 95,
            brewingRating = 3.5,
        ),
        session("older", at(LocalDate.of(2026, 10, 12), 6, 48), steps = listOf(step(0, 90))),
    )

    private fun capture(name: String, list: JournalList, filter: JournalFilter = JournalFilter()) {
        compose.setContent {
            UmamiTheme(darkTheme = dark, reduceMotion = true) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    JournalScreen(
                        list = list,
                        onOpenSettings = {},
                        onOpenTimer = {},
                        filter = filter,
                        thumbnail = { painterResource(UmamiIcons.LandscapePlaceholder) },
                    )
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/JournalEn_${name}_$variant.png")
    }

    @Test
    fun withSessions() = capture("withSessions", journalList(sessions, TODAY))

    @Test
    fun filterChips() = capture(
        "filterChips",
        journalList(sessions, TODAY),
        filter = JournalFilter(teaKey = "t-hisui", minRating = 4.0, withNotes = true),
    )

    @Test
    fun empty() = capture("empty", JournalList.EMPTY)
}
