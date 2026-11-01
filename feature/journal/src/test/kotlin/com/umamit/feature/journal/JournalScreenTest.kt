package com.umamit.feature.journal

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.SessionStatus
import com.umamit.feature.journal.JournalSessions.TODAY
import com.umamit.feature.journal.JournalSessions.session
import com.umamit.feature.journal.JournalSessions.step
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** The journal list (SCR-15, JRN-08): rows as in mockup v2, day headers, the pinned session, the empty state. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class JournalScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var timerOpened = 0
    private val sessionsOpened = mutableListOf<String>()

    private fun at(date: LocalDate, hour: Int, minute: Int = 0) = LocalDateTime.of(date, LocalTime.of(hour, minute))

    private fun show(list: JournalList) = compose.setContent {
        UmamiTheme(darkTheme = false, reduceMotion = true) {
            JournalScreen(
                list = list,
                onOpenSettings = {},
                onOpenTimer = { timerOpened++ },
                onOpenSession = { sessionsOpened += it },
            )
        }
    }

    @Test
    fun `an empty journal says the first cup is waiting`() {
        show(JournalList.EMPTY)

        compose.onNodeWithText("Deine erste Tasse wartet.").assertIsDisplayed()
    }

    @Test
    fun `a row shows the tea, the time of day and the meta line under its day`() {
        show(journalList(listOf(session("s1", at(TODAY, 9, 12), baseTempC = 55)), TODAY))

        compose.onNodeWithText("HEUTE").assertIsDisplayed()
        compose.onNodeWithText("Bio Sencha Hisui").assertIsDisplayed()
        compose.onNodeWithText("09:12").assertIsDisplayed()
        compose.onNodeWithText("55 °C · 1:00 · 3 Aufgüsse").assertIsDisplayed()
    }

    @Test
    fun `TalkBack hears the first infusion as a duration, not a clock time`() {
        show(journalList(listOf(session("s1", at(TODAY, 9, 12), steps = listOf(step(0, 90)))), TODAY))

        compose.onNodeWithContentDescription("55 °C, 1 Minute 30 Sekunden, 1 Aufguss").assertExists()
    }

    @Test
    fun `an early finish reads „2 von 3 Aufgüssen"`() {
        show(
            journalList(
                listOf(session("s1", at(TODAY, 9), steps = listOf(step(0, 60), step(1, 20)), plannedStepCount = 3)),
                TODAY,
            ),
        )

        compose.onNodeWithText("55 °C · 1:00 · 2 von 3 Aufgüssen").assertIsDisplayed()
    }

    @Test
    fun `one infusion is singular`() {
        show(journalList(listOf(session("s1", at(TODAY, 9), steps = listOf(step(0, 90)))), TODAY))

        compose.onNodeWithText("55 °C · 1:30 · 1 Aufguss").assertIsDisplayed()
    }

    @Test
    fun `older days are headed by weekday and date`() {
        show(
            journalList(
                listOf(
                    session("y", at(TODAY.minusDays(1), 7)),
                    session("m", at(LocalDate.of(2026, 10, 12), 7)),
                    session("old", at(LocalDate.of(2025, 12, 31), 7)),
                ),
                TODAY,
            ),
        )

        compose.onNodeWithText("GESTERN").assertExists()
        compose.onNodeWithContentDescription("Montag, 12. Oktober").assertExists()
        compose.onNodeWithContentDescription("Mittwoch, 31. Dezember 2025").assertExists()
    }

    @Test
    fun `the in-progress session is pinned on top and opens the timer`() {
        show(
            journalList(
                listOf(
                    session("done", at(TODAY, 10)),
                    session(
                        "running",
                        at(TODAY, 11),
                        teaName = "Bio Sencha Tanabata",
                        status = SessionStatus.IN_PROGRESS,
                        steps = listOf(step(0, 60), step(1, 20, actualSeconds = null)),
                    ),
                ),
                TODAY,
            ),
        )

        val pinned = compose.onNodeWithText("LÄUFT GERADE").fetchSemanticsNode().boundsInRoot.top
        val today = compose.onNodeWithText("HEUTE").fetchSemanticsNode().boundsInRoot.top
        assertThat(pinned).isLessThan(today)
        compose.onNodeWithText("55 °C · 1:00 · 1 von 2 Aufgüssen").assertIsDisplayed()

        compose.onNodeWithText("Bio Sencha Tanabata").performClick()

        assertThat(timerOpened).isEqualTo(1)
    }

    @Test
    fun `a finished row opens its session page, not the timer (SCR-15, D-100)`() {
        show(journalList(listOf(session("s1", at(TODAY, 9))), TODAY))

        compose.onNodeWithText("Bio Sencha Hisui").performClick()

        assertThat(sessionsOpened).containsExactly("s1")
        assertThat(timerOpened).isEqualTo(0)
    }
}
