package com.umamit.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.component.ActiveBrew
import com.umamit.core.designsystem.theme.UmamiTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.umamit.core.timer.R as TimerR

/**
 * The shell with the bottom bar (phone) and the rail (wide window), light and
 * dark, with and without the active brew bar (M1-05). Goldens: `src/test/screenshots/`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class ShellScreenshotTest(private val dark: Boolean, private val wide: Boolean) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}, wide={1}")
        fun parameters(): List<Array<Any>> = listOf(false, true).flatMap { dark ->
            listOf(false, true).map { wide -> arrayOf<Any>(dark, wide) }
        }
    }

    @get:Rule
    val compose = createComposeRule()

    private val variant get() = "${if (dark) "dark" else "light"}_${if (wide) "rail" else "bar"}"

    @Test
    fun shell() {
        RuntimeEnvironment.setQualifiers(if (wide) "w900dp-h500dp-xhdpi" else "w411dp-h891dp-xhdpi")
        val brew = MutableStateFlow<ActiveBrew?>(ActiveBrew("Gyokuro", "Aufguss 2/3", 45, Color(0xFF8DA36B)))
        compose.setContent {
            UmamiTheme(darkTheme = dark, reduceMotion = true) {
                UmamitApp(
                    activeBrewSource = object : ActiveBrewSource {
                        override val activeBrew: StateFlow<ActiveBrew?> = brew
                        override val progress: () -> Float = { 0.4f }
                    },
                    layoutType = if (wide) NavigationSuiteType.NavigationRail else NavigationSuiteType.NavigationBar,
                )
            }
        }
        compose.onRoot().captureRoboImage("${roborazziSystemPropertyOutputDirectory()}/Shell_$variant.png")
    }

    @Test
    fun icons() {
        org.junit.Assume.assumeFalse("one icon sheet per colour scheme", wide)
        RuntimeEnvironment.setQualifiers("w411dp-h891dp-xhdpi")
        compose.setContent {
            UmamiTheme(darkTheme = dark, reduceMotion = true) {
                Row(
                    modifier = Modifier
                        .testTag("icons")
                        .background(MaterialTheme.colorScheme.background)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Launcher icon: the adaptive layers on a circular mask.
                    val washi = colorResource(R.color.ic_launcher_background)
                    Box(Modifier.size(96.dp).clip(CircleShape).background(washi)) {
                        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null)
                    }
                    // Themed (monochrome) launcher icon.
                    Box(
                        Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Image(
                            painterResource(R.drawable.ic_launcher_monochrome),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer),
                        )
                    }
                    // Status-bar small icon on a dark bar.
                    Box(Modifier.size(48.dp).background(Color.Black).padding(12.dp)) {
                        Image(painterResource(TimerR.drawable.ic_stat_umamit), contentDescription = null)
                    }
                }
            }
        }
        compose.onNodeWithTag("icons")
            .captureRoboImage("${roborazziSystemPropertyOutputDirectory()}/Icons_$variant.png")
    }
}
