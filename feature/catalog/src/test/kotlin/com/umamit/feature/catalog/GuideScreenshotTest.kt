package com.umamit.feature.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.GuideBlock
import com.umamit.core.model.GuideDetail
import com.umamit.core.model.GuideDocument
import com.umamit.core.model.GuideInline.LineBreak
import com.umamit.core.model.GuideInline.Span
import com.umamit.core.model.GuideSummary
import com.umamit.core.testing.FakeClock
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The *Ratgeber* (CAT-09, SCR-20; #76) in light and dark, at font scales 1.0 and 2.0: the list
 * under the A11 header, its empty state, an article (top and end). Invented guides (D-016);
 * the images are placeholder gradients, never shop images. Goldens: `src/test/screenshots/`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class GuideScreenshotTest(private val dark: Boolean, private val fontScale: Float) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}, fontScale={1}")
        fun parameters(): List<Array<Any>> = listOf(false, true).flatMap { dark ->
            listOf(1f, 2f).map { scale -> arrayOf<Any>(dark, scale) }
        }

        private val GUIDES = listOf(
            GuideSummary("ein-guide-fuer-sencha", "Ein Guide für Sencha", 1_710_232_200_000, "hero"),
            GuideSummary("kaltaufguss-im-sommer", "Kaltaufguss im Sommer: so gelingt er", 1_690_000_000_000, null),
            GuideSummary("matcha-erklaert", "Matcha erklärt – vom Tencha zum Pulver", null, "matcha"),
            GuideSummary("umami-was-ist-das", "Umami – was ist das?", 1_650_000_000_000, "umami"),
        )

        private val ARTICLE = GuideDetail(
            slug = "ein-guide-fuer-sencha",
            title = "Ein Guide für Sencha",
            url = "https://shop.example/tee-info/ein-guide-fuer-sencha",
            publishedAtMillis = 1_710_232_200_000,
            heroPath = "hero",
            body = GuideDocument(
                listOf(
                    GuideBlock.Paragraph(
                        listOf(
                            Span("Ein erfundener Einstieg in einen erfundenen Tee: "),
                            Span("frisch", bold = true),
                            Span(", grasig und "),
                            Span("mild", italic = true),
                            Span(". Probier zum Beispiel den "),
                            Span("Morgennebel Sencha", link = "umamit://catalog/XY0100"),
                            Span("."),
                        ),
                    ),
                    GuideBlock.Heading("Wie du ihn aufgießt"),
                    GuideBlock.ListBlock(
                        ordered = true,
                        items = listOf(
                            listOf(Span("Wasser auf 70 °C abkühlen lassen.")),
                            listOf(Span("4 g auf 100 ml, 60 Sekunden ziehen lassen.")),
                            listOf(Span("Weitere Aufgüsse kürzer.")),
                        ),
                    ),
                    GuideBlock.Image("kanne"),
                    GuideBlock.Paragraph(
                        listOf(
                            Span("Mehr dazu in "),
                            Span("Matcha erklärt", link = "umamit://guide/matcha-erklaert"),
                            LineBreak,
                            Span("oder auf einer "),
                            Span("fremden Seite", link = "https://example.org/tee"),
                            Span("."),
                        ),
                    ),
                ),
            ),
        )
    }

    @get:Rule
    val compose = createComposeRule()

    private val variant get() = "${if (dark) "dark" else "light"}_font${fontScale.toString().replace('.', '_')}"

    private fun capture(name: String, scrollToTag: String? = null, content: @Composable () -> Unit) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            UmamiTheme(darkTheme = dark, reduceMotion = true) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
            }
        }
        compose.waitForIdle()
        if (scrollToTag != null) {
            compose.onNodeWithTag(scrollToTag).performScrollTo()
            compose.waitForIdle()
        }
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/Guide_${name}_$variant.png")
    }

    @Test
    fun list() = capture("list") {
        GuideListScreen(
            state = GuideListUiState(loading = false, guides = GUIDES),
            onBack = {},
            onOpenGuide = {},
            zone = FakeClock.BERLIN,
            image = { path -> path?.let { placeholderImage(it) } },
        )
    }

    /** Before the first refresh with guides, at font scale 1.0 only. */
    @Test
    fun listEmpty() {
        assumeTrue(fontScale == 1f)
        capture("listEmpty") {
            GuideListScreen(state = GuideListUiState(loading = false), onBack = {}, onOpenGuide = {})
        }
    }

    @Test
    fun article() = capture("article") { Article() }

    /** The article scrolled to its end: the image, the links and *Im Browser öffnen*. */
    @Test
    fun articleEnd() = capture("articleEnd", scrollToTag = GUIDE_OPEN_BROWSER_TAG) { Article() }

    @Composable
    private fun Article() {
        GuideArticleScreen(
            state = GuideUiState(loading = false, guide = ARTICLE, storedSlugs = setOf("matcha-erklaert")),
            onBack = {},
            onOpenTea = {},
            onOpenGuide = {},
            onOpenWeb = {},
            zone = FakeClock.BERLIN,
            image = { path -> path?.let { placeholderImage(it) } },
        )
    }

    /** A placeholder image of a known size (3:2) in place of a guide's image (D-016). */
    private fun placeholderImage(seed: String): Painter = object : Painter() {
        override val intrinsicSize = Size(1200f, 800f)

        override fun DrawScope.onDraw() {
            val colors = listOf(Color(0xFF7C9A5B), Color(0xFFC9D58F), Color(0xFF4A5643))
            drawRect(Brush.linearGradient(if (seed.length % 2 == 0) colors else colors.reversed()))
        }
    }
}
