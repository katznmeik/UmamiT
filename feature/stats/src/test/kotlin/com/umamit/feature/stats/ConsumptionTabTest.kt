package com.umamit.feature.stats

import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.domain.collection.LiterAverage
import com.umamit.core.domain.stats.ConsumptionStats
import com.umamit.core.model.StatsRange
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** *Verbrauch* (INS-05) with fixed results: gram and euro formats, the hidden cost line, the running-out order. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class ConsumptionTabTest {
    @get:Rule
    val compose = createComposeRule()

    private val opened = mutableListOf<String>()

    private fun show(stats: ConsumptionStats?, range: StatsRange = StatsRange.DAYS_90) {
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                ConsumptionTab(
                    figures = stats?.let { TabFigures(range, it) },
                    scrollState = rememberScrollState(),
                    onOpenTea = { opened += it },
                    thumbnail = { null },
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `grams and euros read in German`() {
        show(ConsumptionFixtures.data())

        compose.onNodeWithText("38 g").assertExists()
        compose.onNodeWithText("64,80 €").performScrollTo().assertExists()
        assertThat(compose.onAllNodesWithText("im Zeitraum").fetchSemanticsNodes()).hasSize(2)
    }

    @Test
    fun `GETRUNKEN sits below AUSGABEN with total, sessions and cost per litre`() {
        show(ConsumptionFixtures.data())

        compose.onNodeWithText("23,40 €").performScrollTo().assertExists()
        compose.onNodeWithText("aus 20 Sitzungen mit Preis").performScrollTo().assertExists()
        compose.onNodeWithText("Ø 2,70 € pro Liter").performScrollTo().assertExists()
        val money = compose.onNodeWithTag(statsSectionTag("consumption.money")).fetchSemanticsNode().boundsInRoot.top
        val drunk = compose.onNodeWithTag(statsSectionTag("consumption.drunk")).fetchSemanticsNode().boundsInRoot.top
        assertThat(drunk).isGreaterThan(money)
    }

    @Test
    fun `GETRUNKEN without a priced session says so`() {
        show(ConsumptionFixtures.data(drunkSessions = 0, costPerLiter = null))

        compose.onNodeWithText("Keine Sitzungen mit Preis").performScrollTo().assertExists()
    }

    @Test
    fun `grams get one decimal only below ten`() {
        assertThat(consumptionGrams(6.55)).isEqualTo("6,6")
        assertThat(consumptionGrams(9.94)).isEqualTo("9,9")
        assertThat(consumptionGrams(38.4)).isEqualTo("38")
        assertThat(consumptionGrams(1234.0)).isEqualTo("1.234")
        show(ConsumptionFixtures.data(grams = 6.5))

        compose.onNodeWithText("6,5 g").assertExists()
    }

    @Test
    fun `one session is singular`() {
        show(ConsumptionFixtures.data(costPerLiter = LiterAverage(450, 1), drunkSessions = 1))

        compose.onNodeWithText("aus 1 Sitzung mit Preis").performScrollTo().assertExists()
        compose.onNodeWithText("Ø 4,50 € pro Liter").performScrollTo().assertExists()
    }

    @Test
    fun `without a priced purchase the money section says so`() {
        show(ConsumptionFixtures.data(hasPurchases = false))

        compose.onNodeWithText("Keine Käufe mit Preis").performScrollTo().assertExists()
        assertThat(compose.onAllNodesWithText("0,00 €", substring = true).fetchSemanticsNodes()).isEmpty()
    }

    @Test
    fun `the cost line is hidden without a session with a cost`() {
        show(ConsumptionFixtures.data(costPerLiter = null))

        assertThat(compose.onAllNodesWithText("KOSTEN PRO LITER").fetchSemanticsNodes()).isEmpty()
        assertThat(compose.onAllNodesWithText("pro Liter", substring = true).fetchSemanticsNodes()).isEmpty()
    }

    @Test
    fun `running out lists Leer first and a tap opens the tea`() {
        show(ConsumptionFixtures.data())

        compose.onNodeWithText("leer").performScrollTo().assertExists()
        compose.onNodeWithText("4,5 g · knapp").performScrollTo().assertExists()
        compose.onNodeWithText("12 g · knapp").performScrollTo().assertExists()
        val titles = listOf("Hisui Gyokuro", "Morgentau Sencha", "Röstduft Hojicha")
        val tops = titles.map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top }
        assertThat(tops).isInStrictOrder()
        compose.onNodeWithText("Morgentau Sencha").assertHasClickAction()
        compose.onNodeWithText("Morgentau Sencha").performClick()
        assertThat(opened).containsExactly("t2")
    }

    @Test
    fun `the running-out section is hidden when nothing runs out`() {
        show(ConsumptionFixtures.data(runningOut = emptyList()))

        assertThat(compose.onAllNodesWithText("GEHT ZUR NEIGE").fetchSemanticsNodes()).isEmpty()
    }

    @Test
    fun `an empty range reads the quiet line but running out stays`() {
        show(ConsumptionFixtures.emptyRange())

        // Gramm, Tee; Ausgaben and Getrunken say „Keine … mit Preis".
        assertThat(compose.onAllNodesWithText("In diesem Zeitraum nichts").fetchSemanticsNodes()).hasSize(2)
        compose.onNodeWithText("Keine Käufe mit Preis").performScrollTo().assertExists()
        compose.onNodeWithText("Keine Sitzungen mit Preis").performScrollTo().assertExists()
        compose.onNodeWithText("Hisui Gyokuro").performScrollTo().assertExists()
    }

    @Test
    fun `nothing is drawn before the first read`() {
        show(null)

        assertThat(compose.onAllNodesWithText("GRAMM").fetchSemanticsNodes()).isEmpty()
    }
}
