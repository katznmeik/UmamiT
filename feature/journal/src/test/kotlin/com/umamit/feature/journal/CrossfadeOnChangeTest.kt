package com.umamit.feature.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The old frame fades out in 200 ms (100 ms under reduce motion) and is gone after (D-117, NFR-PERF-12). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w100dp-h100dp-xhdpi")
class CrossfadeOnChangeTest {
    @get:Rule
    val compose = createComposeRule(
        effectContext = object : MotionDurationScale {
            override val scaleFactor = 1f
        },
    )

    private var changes by mutableIntStateOf(0)
    private var view: android.view.View? = null
    private var color by mutableStateOf(Color.Red)

    private fun setUp(reduceMotion: Boolean) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            view = LocalView.current
            UmamiTheme(darkTheme = false, reduceMotion = reduceMotion) {
                Box(Modifier.size(50.dp).crossfadeOnChange(changes)) {
                    Box(Modifier.size(50.dp).background(color))
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun change() {
        color = Color.Blue
        changes = 1
        compose.mainClock.advanceTimeByFrame()
    }

    private fun redOfFrame(): Int {
        val bitmap = android.graphics.Bitmap.createBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888)
        view!!.draw(android.graphics.Canvas(bitmap))
        return android.graphics.Color.red(bitmap.getPixel(10, 10))
    }

    @Test
    fun `the old frame is on top at the change and gone after 200 ms`() {
        setUp(reduceMotion = false)
        change()
        assertThat(redOfFrame()).isEqualTo(255)
        compose.mainClock.advanceTimeBy(220)
        assertThat(redOfFrame()).isEqualTo(0)
    }

    @Test
    fun `under reduce motion the old frame is on top at the change and gone after 100 ms`() {
        setUp(reduceMotion = true)
        change()
        assertThat(redOfFrame()).isEqualTo(255)
        compose.mainClock.advanceTimeBy(120)
        assertThat(redOfFrame()).isEqualTo(0)
    }
}
