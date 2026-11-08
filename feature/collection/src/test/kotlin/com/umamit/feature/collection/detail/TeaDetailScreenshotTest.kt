package com.umamit.feature.collection.detail

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.Freshness
import com.umamit.core.model.StockLevel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * The tea detail (SCR-09, D-100) in light and dark at font scale 1.0 and 2.0 (03 §6), with the placeholder leaf
 * instead of a photo and an invented tea (D-016): the top of the page with the figures and REZEPTE, and the tea
 * *Knapp* and archived, scrolled to VORRAT (the filled reorder button, *Brühen* greyed out); the photo pager
 * (SCR-28) over placeholder photos and its limit of 10, the photo's ⋮ and „Foto löschen?" (#186); FRISCHE with
 * *Bald genießen* (INV-06).
 * Goldens: `src/test/screenshots/`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TeaDetailScreenshotTest(private val dark: Boolean, private val fontScale: Float) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}, fontScale={1}")
        fun parameters(): List<Array<Any>> = listOf(false, true).flatMap { dark ->
            listOf(1f, 2f).map { scale -> arrayOf<Any>(dark, scale) }
        }
    }

    @get:Rule
    val compose = createComposeRule()

    private val variant get() = "${if (dark) "dark" else "light"}_font${fontScale.toString().replace('.', '_')}"

    private fun capture(
        name: String,
        tea: TeaDetail,
        scrollToTag: String? = null,
        scrollToText: String? = null,
        then: () -> Unit = {},
    ) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            UmamiTheme(darkTheme = dark, reduceMotion = true) {
                TeaDetailScreen(TeaDetailUiState.Ready(tea), TeaDetailActions(), photo = { PLACEHOLDER_PHOTO })
            }
        }
        compose.waitForIdle()
        scrollToTag?.let { compose.onNodeWithTag(it).performScrollTo() }
        scrollToText?.let { compose.onNodeWithText(it).performScrollTo() }
        compose.waitForIdle()
        then()
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/TeaDetail_${name}_$variant.png")
    }

    @Test
    fun ready() = capture("ready", TeaDetailScreenTest.sample())

    /** FRISCHE (INV-06, D-104): *Bald genießen*, opened 8 Oct 2026. */
    @Test
    fun freshness() = capture(
        "freshness",
        TeaDetailScreenTest.sample().copy(
            freshness = DetailFreshness(Freshness.DRINK_SOON, LocalDate.of(2026, 10, 8), null),
        ),
        scrollToTag = TEA_DETAIL_FRESHNESS_TAG,
    )

    /** DETAILS with the opening and best-before dates (TEA-02). */
    @Test
    fun dates() = capture(
        "dates",
        sampleWithDates(),
        scrollToText = "Haltbar bis",
    )

    /** The pager over three placeholder photos, the cover shown (SCR-28). */
    @Test
    fun photos() = capture("photos", TeaDetailScreenTest.sample().copy(photos = placeholderPhotos(3), photoCount = 3))

    /** The second photo's ⋮ open: *Als Titelbild* and *Löschen* (#186). */
    @Test
    fun photoMenu() = capture(
        "photoMenu",
        TeaDetailScreenTest.sample().copy(photos = placeholderPhotos(3), photoCount = 3),
    ) {
        compose.onNodeWithTag(TEA_DETAIL_PHOTOS_TAG).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithTag(TEA_DETAIL_PHOTO_MORE_TAG).performClick()
    }

    /** *Löschen* on the cover: „Foto löschen?" says the next photo becomes the cover (#186). */
    @Test
    fun photoDelete() = capture(
        "photoDelete",
        TeaDetailScreenTest.sample().copy(photos = placeholderPhotos(3), photoCount = 3),
    ) {
        compose.onNodeWithTag(TEA_DETAIL_PHOTO_MORE_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Löschen").performClick()
    }

    /** Ten photos: *+ Foto* greyed out with „Höchstens 10 Fotos" (TEA-05). */
    @Test
    fun photoLimit() = capture(
        "photoLimit",
        TeaDetailScreenTest.sample().copy(photos = placeholderPhotos(10), photoCount = 10),
    )

    @Test
    fun lowAndArchived() = capture(
        "lowArchived",
        TeaDetailScreenTest.sample().copy(
            stockGrams = 12.0,
            stockLevel = StockLevel.LOW,
            reorder = Reorder(TeaDetailScreenTest.URL, prominent = true),
            isArchived = true,
            isFavorite = true,
        ),
        scrollToTag = TEA_DETAIL_REORDER_TAG,
    )
}

/** SCR-09 on a short screen (384 × 832 dp) at font scale 2.0: *Brühen* and the FAB stay reachable. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w384dp-h832dp-xhdpi")
class TeaDetailShortScreenScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun ready() {
        RuntimeEnvironment.setFontScale(2f)
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                TeaDetailScreen(TeaDetailUiState.Ready(TeaDetailScreenTest.sample()), TeaDetailActions(), photo = {
                    null
                })
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/TeaDetail_short_ready_font2_0.png")
    }

    /** DETAILS with both dates on a short screen at font scale 2.0. */
    @Test
    fun dates() {
        RuntimeEnvironment.setFontScale(2f)
        val tea = sampleWithDates()
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                TeaDetailScreen(TeaDetailUiState.Ready(tea), TeaDetailActions(), photo = { null })
            }
        }
        compose.onNodeWithText("Haltbar bis").performScrollTo()
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/TeaDetail_short_dates_font2_0.png")
    }

    /** The photo limit on a short screen at font scale 2.0: „Höchstens 10 Fotos" stays inside the hero. */
    @Test
    fun photoLimit() {
        RuntimeEnvironment.setFontScale(2f)
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                TeaDetailScreen(
                    TeaDetailUiState.Ready(
                        TeaDetailScreenTest.sample().copy(photos = placeholderPhotos(10), photoCount = 10),
                    ),
                    TeaDetailActions(),
                    photo = { PLACEHOLDER_PHOTO },
                )
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/TeaDetail_short_photoLimit_font2_0.png")
    }
}

/** A plain washi-green square in place of a photo (D-016: no real photos in renders). */
private val PLACEHOLDER_PHOTO = ColorPainter(Color(0xFF8A9A7B))

/** [count] placeholder photos, the first the cover. */
private fun placeholderPhotos(count: Int) = List(count) { DetailPhoto("p$it", "/placeholder/p$it.jpg", it == 0) }

/** The sample tea with the opening and best-before dates (TEA-02). */
private fun sampleWithDates(): TeaDetail = TeaDetailScreenTest.sample().let {
    val facts = it.details.copy(openedDate = LocalDate.of(2026, 10, 8), bestBefore = LocalDate.of(2027, 3, 1))
    it.copy(details = facts)
}
