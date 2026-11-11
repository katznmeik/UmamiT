package com.umamit.feature.journal.session

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.junit4.createComposeRule
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

/**
 * The session page's hero (SCR-16, JRN-07, D-112, #229) in light and dark at font scale 1.0 and 2.0 (03 §6): the
 * pager over three session photos with its dots, the tea's cover without session photos, the type leaf without
 * either, and *+ Foto* greyed at 6 with „Höchstens 6 Fotos". Flat placeholder colours stand in for the photos
 * (D-016). Goldens: `src/test/screenshots/`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class SessionHeroScreenshotTest(private val dark: Boolean, private val fontScale: Float) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}, fontScale={1}")
        fun parameters(): List<Array<Any>> = listOf(false, true).flatMap { dark ->
            listOf(1f, 2f).map { scale -> arrayOf<Any>(dark, scale) }
        }

        private const val COVER_PATH = "/placeholder/cover.jpg"

        /** Placeholder images (D-016): the cover in one flat colour, the session's photos in another. */
        private val COVER = ColorPainter(Color(0xFFB9A77A))
        private val SESSION_PHOTO = ColorPainter(Color(0xFF7E9A6B))

        private fun photos(count: Int) = List(count) { SessionHeroPhoto("p$it", "/placeholder/p$it.jpg") }
    }

    @get:Rule
    val compose = createComposeRule()

    private val variant get() = "${if (dark) "dark" else "light"}_font${fontScale.toString().replace('.', '_')}"

    private fun capture(name: String, hero: SessionHero) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            UmamiTheme(darkTheme = dark, reduceMotion = true) {
                SessionPageScreen(
                    state = SessionPageUiState.Ready(SessionPageScreenshotTest.page),
                    hero = hero,
                    photo = { path -> if (path == COVER_PATH) COVER else SESSION_PHOTO },
                )
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/SessionHero_${name}_$variant.png")
    }

    /** Three session photos: the pager with its dots, ⋮ and *+ Foto*. */
    @Test
    fun sessionPhotos() = capture("sessionPhotos", SessionHero(photos = photos(3), coverPath = COVER_PATH))

    /** No session photos: the tea's cover, with *+ Foto* and no photo menu. */
    @Test
    fun teaCover() = capture("teaCover", SessionHero(coverPath = COVER_PATH))

    /** Neither session photos nor a cover: the type leaf. */
    @Test
    fun leaf() = capture("leaf", SessionHero())

    /** Six photos: *+ Foto* greyed out with „Höchstens 6 Fotos". */
    @Test
    fun limitReached() = capture("limitReached", SessionHero(photos = photos(6)))
}
