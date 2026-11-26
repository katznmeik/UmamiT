package com.umamit.feature.onboarding.howto

import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Anleitungen (SCR-35, SCR-36; ONB-08) in English (GEN-13), light and dark: the list and each of the eight topic
 * pages, with the real English names, the numbers in English and the mock-ups in English. Goldens:
 * `src/test/screenshots/HowToEn_*`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-$TALL_PAGE")
class HowToEnglishScreenshotTest(private val dark: Boolean) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun parameters(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }

    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = "en-rUS-$PHONE")
    fun list() = compose.captureHowTo("HowToEn", null, dark)

    @Test
    fun withoutCatalog() = compose.captureHowTo("HowToEn", HowToTopic.WithoutCatalog, dark)

    @Test
    fun shopLink() = compose.captureHowTo("HowToEn", HowToTopic.ShopLink, dark)

    @Test
    fun stockAndCost() = compose.captureHowTo("HowToEn", HowToTopic.StockAndCost, dark)

    @Test
    fun recipes() = compose.captureHowTo("HowToEn", HowToTopic.Recipes, dark)

    @Test
    fun brewing() = compose.captureHowTo("HowToEn", HowToTopic.Brewing, dark)

    @Test
    fun journalAndTaste() = compose.captureHowTo("HowToEn", HowToTopic.JournalAndTaste, dark)

    @Test
    fun dataAndBackup() = compose.captureHowTo("HowToEn", HowToTopic.DataAndBackup, dark)

    @Test
    fun reliableAlarm() = compose.captureHowTo("HowToEn", HowToTopic.ReliableAlarm, dark)
}
