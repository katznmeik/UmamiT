package com.umamit.feature.journal.session

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.StepKind
import com.umamit.core.model.TasteProfile
import com.umamit.core.ui.INFUSION_TASTE_RESET_TAG
import com.umamit.feature.journal.RECIPE_OFFER_TAG
import com.umamit.feature.journal.RecipeOfferState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime

/**
 * The session page (SCR-16, D-100, D-112) in light and dark at font scale 1.0 and 2.0 (03 §6): a tea of the
 * collection (unrated, empty note, GESCHMACK as its quiet row; folded with a radar or the summary line, unfolded, and
 * *Alle*, D-114)
 * with a rinse, an infusion run into overtime, a skipped one and one never started;
 * the same rated 3,5 with a long note; an archived tea (the
 * button greyed out with *Wiederherstellen*); a deleted tea (no link, no button). Goldens: `src/test/screenshots/`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class SessionPageScreenshotTest(private val dark: Boolean, private val fontScale: Float) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}, fontScale={1}")
        fun parameters(): List<Array<Any>> = listOf(false, true).flatMap { dark ->
            listOf(1f, 2f).map { scale -> arrayOf<Any>(dark, scale) }
        }

        /** A synthetic session (D-016): a rinse, an overtime infusion, a skipped one and one never started. */
        val page = SessionPage(
            sessionId = "past",
            teaId = "morgentau",
            teaName = "Sencha Morgentau",
            teaSecondaryName = "Frühlingsernte",
            styleKey = "sencha",
            tea = SessionTea.AVAILABLE,
            startedAt = LocalDateTime.of(2026, 10, 22, 9, 12),
            method = BrewMethod.KYUSU,
            vesselName = "Kyusu 250 ml",
            leafGrams = 4.5,
            waterMl = 180,
            steps = listOf(
                SessionPageStep(StepKind.RINSE, null, 10, 10, 80, skipped = false),
                SessionPageStep(StepKind.INFUSION, 1, 75, 95, 65, skipped = false),
                SessionPageStep(StepKind.INFUSION, 2, 20, 0, 70, skipped = true),
                SessionPageStep(StepKind.INFUSION, 3, 30, null, 75, skipped = false),
            ),
            infusions = listOf(SessionInfusion(position = 1, number = 1, kind = StepKind.INFUSION)),
        )
    }

    @get:Rule
    val compose = createComposeRule()

    private val variant get() = "${if (dark) "dark" else "light"}_font${fontScale.toString().replace('.', '_')}"

    /**
     * The steps of the taste renders: as [page]'s, but the 3rd infusion poured (0:32), so its chip „3." matches the
     * step table (a step never brewed has no chip, D-114).
     */
    private val tastedSteps = page.steps.map { if (it.infusionNumber == 3) it.copy(actualSeconds = 32) else it }

    /** The 1st infusion with [first] and [note], and the 3rd (the skipped 2nd has no chip) with [second]. */
    private fun infusions(first: TasteProfile, note: String? = null, second: TasteProfile = TasteProfile.EMPTY) =
        listOf(
            SessionInfusion(position = 1, number = 1, kind = StepKind.INFUSION, taste = first, note = note),
            SessionInfusion(position = 3, number = 3, kind = StepKind.INFUSION, taste = second),
        )

    private fun capture(
        name: String,
        state: SessionPageUiState,
        scrollToTag: String? = null,
        click: String? = null,
        thenScrollTo: String? = null,
    ) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent { UmamiTheme(darkTheme = dark, reduceMotion = true) { SessionPageScreen(state) } }
        compose.waitForIdle()
        if (scrollToTag != null) compose.onNodeWithTag(scrollToTag).performScrollTo()
        if (click != null) compose.onNodeWithTag(click).performClick()
        compose.waitForIdle()
        if (thenScrollTo != null) compose.onNodeWithTag(thenScrollTo).performScrollTo()
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/SessionPage_${name}_$variant.png")
    }

    @Test
    fun available() = capture("available", SessionPageUiState.Ready(page))

    /** Rated 3,5 (a half star) with a long note that wraps (JRN-02, JRN-05). */
    @Test
    fun ratedWithLongNote() = capture(
        "ratedWithLongNote",
        SessionPageUiState.Ready(
            page.copy(
                rating = 3.5,
                notes = "Mild und cremig, kaum Bitterkeit. Beim zweiten Aufguss kommt eine leichte Süße dazu, " +
                    "beim dritten wird er grasiger. Nächstes Mal etwas kühler und kürzer ziehen lassen, " +
                    "dann bleibt der Umami-Körper länger erhalten.",
            ),
        ),
    )

    /** GESCHMACK without any value: the quiet row „Geschmack hinzufügen" (D-114). */
    @Test
    fun tasteNoValues() = capture("tasteNoValues", SessionPageUiState.Ready(page), scrollToTag = SESSION_TASTE_ADD_TAG)

    /** Folded on the 1st infusion with three axes: the radar, the note in one line, *Anpassen* ⌄ (D-114 Q7). */
    @Test
    fun tasteFoldedRadar() = capture(
        "tasteFoldedRadar",
        SessionPageUiState.Ready(
            page.copy(
                steps = tastedSteps,
                infusions = infusions(
                    TasteProfile(umami = 5, sweetness = 3, bitterness = 1, astringency = 2, body = 4, aroma = 3),
                    note = "Weich, nach Nori, mit einer leichten Süße im Abgang und kaum Bitterkeit",
                ),
            ),
        ),
        scrollToTag = SESSION_TASTE_ADJUST_TAG,
    )

    /** Folded below three axes: the summary line „Umami 4 · Süße 3". */
    @Test
    fun tasteFoldedSummary() = capture(
        "tasteFoldedSummary",
        SessionPageUiState.Ready(
            page.copy(steps = tastedSteps, infusions = infusions(TasteProfile(umami = 4, sweetness = 3))),
        ),
        scrollToTag = SESSION_TASTE_ADJUST_TAG,
    )

    /** Unfolded: the radar, the six sliders, the note field and *Zurücksetzen*. */
    @Test
    fun tasteUnfolded() = capture(
        "tasteUnfolded",
        SessionPageUiState.Ready(
            page.copy(
                steps = tastedSteps,
                infusions = infusions(TasteProfile(umami = 5, sweetness = 3, body = 4), note = "Weich"),
            ),
        ),
        scrollToTag = SESSION_TASTE_ADJUST_TAG,
        click = SESSION_TASTE_ADJUST_TAG,
        thenScrollTo = INFUSION_TASTE_RESET_TAG,
    )

    /** *Alle*: the radar averaged per axis over the infusions, no *Anpassen* (D-114 Q18). */
    @Test
    fun tasteAll() = capture(
        "tasteAll",
        SessionPageUiState.Ready(
            page.copy(
                steps = tastedSteps,
                infusions = infusions(
                    TasteProfile(umami = 5, sweetness = 3, body = 4),
                    second = TasteProfile(umami = 4, sweetness = 2, body = 3, aroma = 2),
                ),
            ),
        ),
        scrollToTag = SESSION_TASTE_ADJUST_TAG,
        click = SESSION_TASTE_ALL_TAG,
    )

    /** The recipe offer applies: *Zeiten ins Rezept übernehmen* under the step table, scrolled into view (PRF-07). */
    @Test
    fun recipeOffer() = capture(
        "recipeOffer",
        SessionPageUiState.Ready(page.copy(recipeOffer = RecipeOfferState.OFFERED)),
        scrollToTag = RECIPE_OFFER_TAG,
    )

    /** ⋮ open for a catalog tea: *Tee ansehen*, *Im Katalog ansehen*, a divider, *Bearbeiten*, *Löschen*. */
    @Test
    fun menuCatalogOpen() = capture(
        "menuCatalogOpen",
        SessionPageUiState.Ready(page.copy(catalogKey = "TG0557")),
        click = SESSION_MENU_TAG,
    )

    /** ⋮ open for a deleted tea: *Bearbeiten* and *Löschen* alone. */
    @Test
    fun menuDeletedOpen() = capture(
        "menuDeletedOpen",
        SessionPageUiState.Ready(page.copy(teaId = null, tea = SessionTea.DELETED)),
        click = SESSION_MENU_TAG,
    )

    @Test
    fun archivedTea() = capture("archivedTea", SessionPageUiState.Ready(page.copy(tea = SessionTea.ARCHIVED)))

    @Test
    fun deletedTea() =
        capture("deletedTea", SessionPageUiState.Ready(page.copy(teaId = null, tea = SessionTea.DELETED)))
}
