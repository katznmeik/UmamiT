package com.umamit.feature.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.RecipeCheckReason
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeSessions
import com.umamit.core.timer.controller.SavedSheetRequests
import com.umamit.feature.journal.JournalSessions.TODAY
import com.umamit.feature.journal.JournalSessions.session
import com.umamit.feature.journal.JournalSessions.step
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * SCR-15 and SCR-06 in light and dark at font scale 1.0 and 2.0 (03 §6): the
 * journal with sessions (pinned, today, yesterday, an older date, an early
 * finish, a long German name, a photo and the leaf placeholder), the empty
 * journal, and the „Gespeichert" sheet over it. Photos are the placeholder
 * illustration only. Goldens: `src/test/screenshots/`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class JournalScreenshotTest(private val dark: Boolean, private val fontScale: Float) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}, fontScale={1}")
        fun parameters(): List<Array<Any>> = listOf(false, true).flatMap { dark ->
            listOf(1f, 2f).map { scale -> arrayOf<Any>(dark, scale) }
        }

        /** Any path: the screenshot's thumbnail loader draws the placeholder illustration for it. */
        private val PLACEHOLDER_PHOTO = File("placeholder.jpg")
    }

    @get:Rule
    val compose = createComposeRule()

    private val variant get() = "${if (dark) "dark" else "light"}_font${fontScale.toString().replace('.', '_')}"

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
        session("hisui", at(TODAY, 9, 12), photo = PLACEHOLDER_PHOTO),
        session(
            "early",
            at(TODAY, 7, 21),
            teaName = "Gyokuro Asahi aus der Präfektur Kyōto, Schattentee der ersten Pflückung",
            styleKey = "gyokuro",
            baseTempC = 50,
            steps = listOf(step(0, 120), step(1, 20)),
            plannedStepCount = 3,
        ),
        session("yesterday", at(TODAY.minusDays(1), 18, 40), styleKey = "hojicha", baseTempC = 95),
        session("older", at(LocalDate.of(2026, 10, 12), 6, 48), steps = listOf(step(0, 90))),
    )

    private fun capture(name: String, list: JournalList, sheetFor: String? = null, flaggedTea: Boolean = false) {
        RuntimeEnvironment.setFontScale(fontScale)
        val collection = FakeCollection()
        val data = FakeSessions(collection)
        val requests = SavedSheetRequests(FakeClock())
        val teaId = if (flaggedTea) "flagged" else null
        if (teaId != null) {
            runBlocking {
                collection.teas.saveTea(
                    Tea(
                        teaId,
                        "Bio Sencha Hisui",
                        category = TeaCategory.GREEN,
                        form = TeaForm.LOOSE_LEAF,
                        recipeCheckReason = RecipeCheckReason.FROM_TEXT,
                    ),
                )
            }
        }
        if (sheetFor != null) {
            FakeBrewController(data.sessions, requests).brew(sheetFor, 80, 20, 40, teaId = teaId)
        }
        compose.setContent {
            UmamiTheme(darkTheme = dark, reduceMotion = true) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    JournalScreen(
                        list = list,
                        onOpenSettings = {},
                        onOpenTimer = {},
                        thumbnail = { painterResource(UmamiIcons.LandscapePlaceholder) },
                    )
                    SavedSheetHost(
                        requests = requests,
                        sessions = data.sessions,
                        teas = collection.teas,
                        alerting = false,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/Journal_${name}_$variant.png")
    }

    @Test
    fun withSessions() = capture("withSessions", journalList(sessions, TODAY))

    @Test
    fun empty() = capture("empty", JournalList.EMPTY)

    @Test
    fun savedSheet() = capture("savedSheet", journalList(sessions.drop(1), TODAY), sheetFor = "sheet")

    /** A session of a tea flagged „Rezept prüfen": the sheet offers *Rezept passt* (CAT-05, D-073). */
    @Test
    fun savedSheetRecipeCheck() = capture(
        "savedSheetRecipeCheck",
        journalList(sessions.drop(1), TODAY),
        sheetFor = "sheet",
        flaggedTea = true,
    )
}
