package com.umamit.feature.onboarding.howto

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.theme.UmamiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A phone-wide, tall screen, so a whole topic page shows without scrolling. */
internal const val TALL_PAGE = "w411dp-h2200dp-xhdpi"

/** Taller still, for a page at font 2.0. */
internal const val TALLER_PAGE = "w411dp-h3600dp-xhdpi"

/** An ordinary phone, for the list. */
internal const val PHONE = "w411dp-h891dp-xhdpi"

/** Captures the list (SCR-35) or one topic (SCR-36) into `src/test/screenshots/<prefix>_<name>_<theme><font>.png`. */
@OptIn(ExperimentalRoborazziApi::class)
internal fun ComposeContentTestRule.captureHowTo(
    prefix: String,
    topic: HowToTopic?,
    dark: Boolean,
    fontScale: Float = 1f,
) {
    RuntimeEnvironment.setFontScale(fontScale)
    setContent {
        UmamiTheme(darkTheme = dark, reduceMotion = true) {
            if (topic == null) {
                HowToListScreen(onOpenTopic = {}, onBack = {})
            } else {
                HowToTopicScreen(topic = topic, onBack = {})
            }
        }
    }
    waitForIdle()
    val theme = if (dark) "dark" else "light"
    val font = if (fontScale == 1f) "" else "_font${fontScale.toString().replace('.', '_')}"
    val name = topic?.name ?: "List"
    captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/${prefix}_${name}_$theme$font.png")
}

/**
 * The Anleitungen (SCR-35, SCR-36; ONB-08) in German, light and dark: the list and each of the eight topic pages,
 * and one page at font 2.0 (the mock-ups keep their size, the text wraps). Goldens: `src/test/screenshots/HowTo_*`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = TALL_PAGE)
class HowToScreenshotTest(private val dark: Boolean) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun parameters(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }

    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = PHONE)
    fun list() = compose.captureHowTo("HowTo", null, dark)

    @Test
    fun withoutCatalog() = compose.captureHowTo("HowTo", HowToTopic.WithoutCatalog, dark)

    @Test
    fun shopLink() = compose.captureHowTo("HowTo", HowToTopic.ShopLink, dark)

    @Test
    fun stockAndCost() = compose.captureHowTo("HowTo", HowToTopic.StockAndCost, dark)

    @Test
    fun recipes() = compose.captureHowTo("HowTo", HowToTopic.Recipes, dark)

    @Test
    fun brewing() = compose.captureHowTo("HowTo", HowToTopic.Brewing, dark)

    @Test
    fun journalAndTaste() = compose.captureHowTo("HowTo", HowToTopic.JournalAndTaste, dark)

    @Test
    fun dataAndBackup() = compose.captureHowTo("HowTo", HowToTopic.DataAndBackup, dark)

    @Test
    fun reliableAlarm() = compose.captureHowTo("HowTo", HowToTopic.ReliableAlarm, dark)

    @Test
    @Config(qualifiers = TALLER_PAGE)
    fun stockAndCostAtFont2() = compose.captureHowTo("HowTo", HowToTopic.StockAndCost, dark, fontScale = 2f)

    @Test
    @Config(qualifiers = TALLER_PAGE)
    fun shopLinkAtFont2() = compose.captureHowTo("HowTo", HowToTopic.ShopLink, dark, fontScale = 2f)
}
