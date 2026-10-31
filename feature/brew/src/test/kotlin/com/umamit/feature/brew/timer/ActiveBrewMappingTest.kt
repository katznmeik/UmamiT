package com.umamit.feature.brew.timer

import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.component.ActiveBrew
import com.umamit.core.model.TimerPhase
import com.umamit.core.timer.engine.SessionKind
import com.umamit.feature.brew.timer.TimerStates.NOW
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The active brew bar fed by the controller's state (GEN-06, 03 §3.8, D-055). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ActiveBrewMappingTest {
    private val resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources
    private val liquor = Color(0xFFC9D58F)

    @Test
    fun `a running step shows the tea, the step and the remaining time`() {
        val brew = activeBrewOf(TimerStates.running(remainingMs = 47_400, stepIndex = 1), NOW, resources, liquor)

        assertThat(brew).isEqualTo(
            ActiveBrew(
                title = "Bio Uji Gyokuro Gokō",
                step = "Aufguss 2/3",
                remainingSeconds = 48,
                liquorColor = liquor,
                stepDescription = "Aufguss 2 von 3",
            ),
        )
    }

    @Test
    fun `the alert shows the overtime counting up`() {
        val brew = activeBrewOf(TimerStates.alerting(overtimeMs = 7_400), NOW, resources, liquor)

        assertThat(brew.alerting).isTrue()
        assertThat(brew.overtimeSeconds).isEqualTo(7)
    }

    @Test
    fun `a held quick alert is fertig, not alerting, until it rings (D-098)`() {
        val held = TimerStates.quick(TimerPhase.ALERTING, overtimeMs = 12_000).copy(alertHeld = true)

        val brew = activeBrewOf(held, NOW, resources, liquor)

        assertThat(brew.held).isTrue()
        assertThat(brew.alerting).isFalse()
        assertThat(activeBrewOf(held.copy(alertHeld = false), NOW, resources, liquor).alerting).isTrue()
    }

    @Test
    fun `the quick timer is called Schnelltimer and has no step`() {
        val quick = TimerStates.running().copy(kind = SessionKind.Quick, teaName = null)

        val brew = activeBrewOf(quick, NOW, resources, liquor)

        assertThat(brew.title).isEqualTo("Schnelltimer")
        assertThat(brew.step).isNull()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `the ticks follow the second boundaries of the countdown`() = runTest {
        val start = currentTime
        val ticks = TimerStates.running(remainingMs = 47_400).displayTicks { NOW + currentTime - start }

        assertThat(ticks.take(3).toList()).containsExactly(NOW, NOW + 400, NOW + 1_400).inOrder()
    }
}
