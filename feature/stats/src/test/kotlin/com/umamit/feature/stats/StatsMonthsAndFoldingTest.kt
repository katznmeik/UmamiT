package com.umamit.feature.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.MonthSpan
import com.umamit.core.model.StatsRange
import com.umamit.core.model.StatsTab
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.YearMonth

/**
 * The shell's *Monate* segment and sheet (INS-07, R7) and the foldable sections (INS-08, R8): selection, what is
 * reported for persistence, and the TalkBack states. What is stored is the view model's test (StatsViewModelTest).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class StatsMonthsAndFoldingTest {
    @get:Rule
    val compose = createComposeRule()

    private fun str(id: Int, vararg args: Any) = RuntimeEnvironment.getApplication().getString(id, *args)

    private val bounds = MonthSpan(YearMonth.of(2025, 11), YearMonth.of(2026, 11))
    private var state by mutableStateOf(StatsUiState.Data(StatsRange.DAYS_90, StatsTab.OVERVIEW, months = bounds))
    private val customCalls = mutableListOf<Pair<YearMonth, YearMonth>>()
    private val foldCalls = mutableListOf<Pair<String, Boolean>>()

    /** The shell with a store that answers: a range or a fold reported shows at once, as the view model does. */
    private fun showShell(reduceMotion: Boolean = true) = compose.setContent {
        UmamiTheme(reduceMotion = reduceMotion) {
            StatsContent(
                state = state,
                onRangeChange = {},
                onTabChange = {},
                onOpenSettings = {},
                onCustomRange = { from, to ->
                    customCalls += from to to
                    state = state.copy(range = StatsRange.CUSTOM, custom = MonthSpan(from, to))
                },
                onFoldedChange = { key, folded ->
                    foldCalls += key to folded
                    state = state.copy(folded = if (folded) state.folded + key else state.folded - key)
                },
            ) { _, _, _ ->
                Column {
                    StatsSection(key = "test.one", title = "Eins", summary = "42") { Text("Inhalt eins") }
                    StatsSection(key = "test.two", title = "Zwei") { Text("Inhalt zwei") }
                }
            }
        }
    }

    private fun openMonths() =
        compose.onNodeWithContentDescription(str(R.string.stats_range_months_description)).performClick()

    @Test
    fun `the fifth segment reads Monate until months are chosen`() {
        showShell()

        compose.onNodeWithText(str(R.string.stats_range_months)).assertExists()
        compose.onNodeWithTag(STATS_MONTHS_SHEET_TAG).assertDoesNotExist()
    }

    @Test
    fun `a tap on Monate opens the sheet and applies the last three months`() {
        showShell()

        openMonths()

        compose.onNodeWithTag(STATS_MONTHS_SHEET_TAG).assertExists()
        assertThat(customCalls).containsExactly(YearMonth.of(2026, 9) to YearMonth.of(2026, 11))
        compose.onNodeWithTag(STATS_MONTH_FROM_TAG).assertTextEquals("Sept. 2026")
        compose.onNodeWithTag(STATS_MONTH_TO_TAG).assertTextEquals("Nov. 2026")
    }

    @Test
    fun `the remembered months are offered again and not replaced`() {
        state = state.copy(range = StatsRange.CUSTOM, custom = MonthSpan(YearMonth.of(2026, 1), YearMonth.of(2026, 5)))
        showShell()

        compose.onNodeWithText("Jan.–Mai").performClick()

        assertThat(customCalls).isEmpty()
        compose.onNodeWithTag(STATS_MONTH_FROM_TAG).assertTextEquals("Jan. 2026")
        compose.onNodeWithTag(STATS_MONTH_TO_TAG).assertTextEquals("Mai 2026")
    }

    @Test
    fun `the chosen months read on the segment, with the years across years`() {
        state = state.copy(range = StatsRange.CUSTOM, custom = MonthSpan(YearMonth.of(2025, 12), YearMonth.of(2026, 8)))
        showShell()

        compose.onNodeWithText("Dez.\u00A025–Aug.\u00A026").assertExists()
        compose.onNodeWithContentDescription(
            str(R.string.stats_range_months_chosen_description, "Dez. 2025", "Aug. 2026"),
        )
            .assertExists()
    }

    @Test
    fun `stepping a picker reports the new months and the segment follows`() {
        showShell()
        openMonths()
        customCalls.clear()

        compose.onNodeWithContentDescription(str(R.string.stats_months_earlier, "Von")).performClick()
        compose.onNodeWithContentDescription(str(R.string.stats_months_earlier, "Bis")).performClick()

        assertThat(customCalls).containsExactly(
            YearMonth.of(2026, 8) to YearMonth.of(2026, 11),
            YearMonth.of(2026, 8) to YearMonth.of(2026, 10),
        ).inOrder()
        compose.onNodeWithText("Aug.–Okt.").assertExists()
    }

    @Test
    fun `two quick taps on a stepper both count before the store answers`() {
        // A store that is slow: it reports the months but never shows them back.
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                MonthsSheet(
                    bounds = bounds,
                    selected = MonthSpan(YearMonth.of(2026, 9), YearMonth.of(2026, 11)),
                    onChange = { customCalls += it.from to it.to },
                    onDismissRequest = {},
                )
            }
        }

        val earlier = compose.onNodeWithContentDescription(str(R.string.stats_months_earlier, "Von"))
        earlier.performClick()
        earlier.performClick()

        assertThat(customCalls).containsExactly(
            YearMonth.of(2026, 8) to YearMonth.of(2026, 11),
            YearMonth.of(2026, 7) to YearMonth.of(2026, 11),
        ).inOrder()
    }

    @Test
    fun `two quick taps on a heading fold and open it again without waiting for the store`() {
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                // A store that never answers: the persisted set stays empty.
                CompositionLocalProvider(
                    LocalStatsFolds provides StatsFolds(emptySet()) { k, f ->
                        foldCalls += k to f
                    },
                ) {
                    StatsSection(key = "test.one", title = "Eins") { Text("Inhalt eins") }
                }
            }
        }

        compose.onNodeWithTag(statsSectionTag("test.one")).performClick()
        compose.onNodeWithTag(statsSectionTag("test.one")).performClick()

        assertThat(foldCalls).containsExactly("test.one" to true, "test.one" to false).inOrder()
        compose.onNodeWithText("Inhalt eins").assertExists()
    }

    @Test
    fun `the segment reads Monate while another range is selected`() {
        state =
            state.copy(range = StatsRange.DAYS_30, custom = MonthSpan(YearMonth.of(2025, 12), YearMonth.of(2026, 8)))
        showShell()

        compose.onNodeWithText(str(R.string.stats_range_months)).assertExists()
    }

    @Test
    fun `Bis is never before Von and neither leaves the first session's month and the current month`() {
        state =
            state.copy(range = StatsRange.CUSTOM, custom = MonthSpan(YearMonth.of(2025, 11), YearMonth.of(2025, 11)))
        showShell()
        compose.onNodeWithText("Nov.–Nov.").performClick()

        // Von = Bis = the first month: Von cannot go earlier or past Bis, Bis cannot go before Von.
        compose.onNodeWithContentDescription(str(R.string.stats_months_earlier, "Von")).assertIsNotEnabled()
        compose.onNodeWithContentDescription(str(R.string.stats_months_later, "Von")).assertIsNotEnabled()
        compose.onNodeWithContentDescription(str(R.string.stats_months_earlier, "Bis")).assertIsNotEnabled()
        compose.onNodeWithContentDescription(str(R.string.stats_months_later, "Bis")).assertIsEnabled()
        // Bis can run up to the current month and no further.
        repeat(12) { compose.onNodeWithContentDescription(str(R.string.stats_months_later, "Bis")).performClick() }
        compose.onNodeWithContentDescription(str(R.string.stats_months_later, "Bis")).assertIsNotEnabled()
        assertThat(customCalls.last()).isEqualTo(YearMonth.of(2025, 11) to YearMonth.of(2026, 11))
    }

    @Test
    fun `Fertig closes the sheet and keeps the months`() {
        showShell()
        openMonths()

        compose.onNodeWithText(str(R.string.stats_months_done)).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(STATS_MONTHS_SHEET_TAG).assertDoesNotExist()
        assertThat(state.custom).isEqualTo(MonthSpan(YearMonth.of(2026, 9), YearMonth.of(2026, 11)))
    }

    @Test
    fun `a section starts open, folds on a tap on its heading and reports it for persistence`() {
        showShell()

        compose.onNodeWithText("Inhalt eins").assertExists()
        compose.onNodeWithTag(statsSectionTag("test.one")).performClick()

        assertThat(foldCalls).containsExactly("test.one" to true)
        compose.onNodeWithText("Inhalt eins").assertDoesNotExist()
        compose.onNodeWithText("Inhalt zwei").assertExists()
    }

    @Test
    fun `a folded section is one line with its key number, a second tap opens it`() {
        state = state.copy(folded = setOf("test.one"))
        showShell()

        compose.onNodeWithText("Inhalt eins").assertDoesNotExist()
        compose.onNodeWithTag(
            statsSectionTag("test.one"),
        ).assert(hasText("EINS · 42", substring = false, ignoreCase = false))
        compose.onNodeWithTag(statsSectionTag("test.one")).performClick()

        assertThat(foldCalls).containsExactly("test.one" to false)
        compose.onNodeWithText("Inhalt eins").assertExists()
        // Open, the heading is just the title.
        compose.onAllNodesWithText("EINS · 42").assertCountEquals(0)
    }

    @Test
    fun `TalkBack hears eingeklappt or ausgeklappt on the heading and the action`() {
        showShell()
        val heading = compose.onNodeWithTag(statsSectionTag("test.one"))

        heading.assert(stateIs(str(R.string.stats_section_unfolded)))
        assertThat(str(R.string.stats_section_unfolded)).isEqualTo("ausgeklappt")
        heading.performClick()
        heading.assert(stateIs(str(R.string.stats_section_folded)))
        assertThat(str(R.string.stats_section_folded)).isEqualTo("eingeklappt")
        heading.assert(
            androidx.compose.ui.test.SemanticsMatcher.expectValue(
                SemanticsProperties.ContentDescription,
                listOf("Eins · 42"),
            ),
        )
        heading.assert(
            androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading),
        )
    }

    @Test
    fun `without the shell a section keeps its own fold`() {
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                StatsSection(key = "alone", title = "Allein") { Text("Inhalt allein") }
            }
        }

        compose.onNodeWithTag(statsSectionTag("alone")).performClick()
        compose.onNodeWithText("Inhalt allein").assertDoesNotExist()
        compose.onNodeWithTag(statsSectionTag("alone")).performClick()
        compose.onNodeWithText("Inhalt allein").assertExists()
    }

    @Test
    fun `the body folds in about 200 ms, and at once with reduce motion`() {
        compose.mainClock.autoAdvance = false
        showShell(reduceMotion = false)
        compose.mainClock.advanceTimeBy(500)

        compose.onNodeWithTag(statsSectionTag("test.one")).performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(60)
        compose.onNodeWithText("Inhalt eins").assertExists()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Inhalt eins").assertDoesNotExist()
    }

    @Test
    fun `provided folds win over the section's own state`() {
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                CompositionLocalProvider(
                    LocalStatsFolds provides StatsFolds(setOf("k")) { key, folded ->
                        foldCalls += key to folded
                    },
                ) {
                    StatsSection(key = "k", title = "Titel") { Text("Inhalt k") }
                }
            }
        }

        compose.onNodeWithText("Inhalt k").assertDoesNotExist()
        compose.onNodeWithTag(statsSectionTag("k")).performClick()
        assertThat(foldCalls).containsExactly("k" to false)
    }

    private fun stateIs(expected: String) = androidx.compose.ui.test.SemanticsMatcher.expectValue(
        SemanticsProperties.StateDescription,
        expected,
    )
}
