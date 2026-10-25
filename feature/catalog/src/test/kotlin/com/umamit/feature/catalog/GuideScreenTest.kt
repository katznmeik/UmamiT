package com.umamit.feature.catalog

import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performFirstLinkClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.CatalogOverview
import com.umamit.core.model.GuideBlock
import com.umamit.core.model.GuideDetail
import com.umamit.core.model.GuideDocument
import com.umamit.core.model.GuideInline.Span
import com.umamit.core.testing.FakeCatalogRefreshRunner
import com.umamit.core.testing.FakeCatalogRepository
import com.umamit.core.testing.FakeGuideRepository
import com.umamit.core.testing.catalogTea
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The *Ratgeber* (SCR-20, CAT-09; #76) on fake data: links to catalog teas and stored guides
 * open inside the app, every other link and *Im Browser öffnen* in the browser. Invented
 * guides (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class GuideScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val guides = FakeGuideRepository(listOf(SENCHA_GUIDE, MATCHA_GUIDE))
    private val openedTeas = mutableListOf<String>()
    private val openedGuides = mutableListOf<String>()
    private var backs = 0

    private fun str(@StringRes id: Int, vararg args: Any): String =
        ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

    private fun showArticle(slug: String = SENCHA_GUIDE.slug) {
        val viewModel = GuideViewModel(SavedStateHandle(mapOf(GuideViewModel.SLUG to slug)), guides)
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                GuideArticleRoute(
                    onBack = { backs++ },
                    onOpenTea = { openedTeas += it },
                    onOpenGuide = { openedGuides += it },
                    viewModel = viewModel,
                )
            }
        }
        compose.waitForIdle()
    }

    private fun clickLinkIn(text: String) {
        compose.onNodeWithText(text, substring = true).performScrollTo().performFirstLinkClick()
        compose.waitForIdle()
    }

    private fun browser(): Intent? = shadowOf(compose.activity).nextStartedActivity

    @Test
    fun theArticleShowsItsTitleSourceAndText() {
        showArticle()

        compose.onNodeWithText("Ein Guide für Sencha").assertIsDisplayed()
        compose.onNodeWithText(str(R.string.guide_source_dated, "12. März 2024")).assertIsDisplayed()
        compose.onNodeWithText("Wie man ihn aufgießt").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aTeaLinkOpensTheCatalogTeaInTheApp() {
        showArticle()

        clickLinkIn("Probier den Morgennebel")

        assertThat(openedTeas).containsExactly("XY0100")
        assertThat(browser()).isNull()
    }

    @Test
    fun aGuideLinkOpensThatGuideInTheApp() {
        showArticle()

        clickLinkIn("Lies auch Matcha erklärt")

        assertThat(openedGuides).containsExactly(MATCHA_GUIDE.slug)
        assertThat(browser()).isNull()
    }

    @Test
    fun anyOtherLinkOpensTheBrowser() {
        showArticle()

        clickLinkIn("Eine fremde Seite")

        val intent = requireNotNull(browser())
        assertThat(intent.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(intent.dataString).isEqualTo("https://example.org/tee")
        assertThat(openedTeas).isEmpty()
        assertThat(openedGuides).isEmpty()
    }

    @Test
    fun aLinkToAGuideTheCatalogNoLongerHoldsOpensItOnTheShop() {
        showArticle()

        clickLinkIn("Ein verschwundener Guide")

        assertThat(requireNotNull(browser()).dataString).isEqualTo("https://shop.example/tee-info/verschwunden")
        assertThat(openedGuides).isEmpty()
    }

    @Test
    fun openInBrowserOpensTheArticleOnTheShop() {
        showArticle()

        compose.onNodeWithTag(GUIDE_OPEN_BROWSER_TAG).performScrollTo().performClick()
        compose.waitForIdle()

        assertThat(
            requireNotNull(browser()).dataString,
        ).isEqualTo("https://shop.example/tee-info/ein-guide-fuer-sencha")
    }

    @Test
    fun theCatalogsRatgeberRowOpensTheGuides() {
        var guideListOpened = 0
        val catalog = FakeCatalogRepository(CatalogOverview(listOf(catalogTea("XY0100", "Morgennebel Sencha")), null))
        val viewModel = CatalogViewModel(catalog, FakeCatalogRefreshRunner(), guides)
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                CatalogRoute(alerting = false, onOpenTea = {
                }, onOpenGuides = { guideListOpened++ }, viewModel = viewModel)
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("2 Guides von Tee-Kontor Kiel, offline lesbar").assertIsDisplayed()
        compose.onNodeWithTag(CATALOG_GUIDES_ROW_TAG).performClick()

        assertThat(guideListOpened).isEqualTo(1)
    }

    @Test
    fun withoutGuidesTheCatalogHasNoRatgeberRow() {
        val catalog = FakeCatalogRepository(CatalogOverview(listOf(catalogTea("XY0100", "Morgennebel Sencha")), null))
        val viewModel = CatalogViewModel(catalog, FakeCatalogRefreshRunner(), FakeGuideRepository())
        compose.setContent {
            UmamiTheme(reduceMotion = true) { CatalogRoute(alerting = false, onOpenTea = {}, viewModel = viewModel) }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Morgennebel Sencha").assertIsDisplayed()
        compose.onNodeWithTag(CATALOG_GUIDES_ROW_TAG).assertDoesNotExist()
    }

    @Test
    fun theListOpensAGuide() {
        val viewModel = GuideListViewModel(guides)
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                GuideListRoute(onBack = { backs++ }, onOpenGuide = { openedGuides += it }, viewModel = viewModel)
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Matcha erklärt").performScrollTo().performClick()

        assertThat(openedGuides).containsExactly(MATCHA_GUIDE.slug)
    }

    private companion object {
        val SENCHA_GUIDE = GuideDetail(
            slug = "ein-guide-fuer-sencha",
            title = "Ein Guide für Sencha",
            url = "https://shop.example/tee-info/ein-guide-fuer-sencha",
            publishedAtMillis = 1_710_232_200_000, // 12 March 2024
            heroPath = null,
            body = GuideDocument(
                listOf(
                    GuideBlock.Heading("Wie man ihn aufgießt"),
                    GuideBlock.Paragraph(
                        listOf(Span("Probier den "), Span("Morgennebel", link = "umamit://catalog/XY0100"), Span(".")),
                    ),
                    GuideBlock.Paragraph(
                        listOf(Span("Lies auch "), Span("Matcha erklärt", link = "umamit://guide/matcha-erklaert")),
                    ),
                    GuideBlock.Paragraph(listOf(Span("Eine fremde Seite", link = "https://example.org/tee"))),
                    GuideBlock.Paragraph(
                        listOf(Span("Ein verschwundener Guide", link = "umamit://guide/verschwunden")),
                    ),
                ),
            ),
        )
        val MATCHA_GUIDE = GuideDetail(
            slug = "matcha-erklaert",
            title = "Matcha erklärt",
            url = "https://shop.example/tee-info/matcha-erklaert",
            publishedAtMillis = null,
            heroPath = null,
            body = GuideDocument(listOf(GuideBlock.Paragraph(listOf(Span("Ein erfundener Absatz."))))),
        )
    }
}
