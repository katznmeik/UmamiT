package com.umamit.feature.teaware

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.VesselMaterial
import com.umamit.core.model.VesselType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Teegeschirr (SCR-13) with favourites first and an archived vessel, empty, and the vessel editor
 * (SCR-14) for a stored vessel, a new one with its errors and an archived one, in light and dark at
 * font scale 1.0 and 2.0 (03 §6). Goldens: `src/test/screenshots/`.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TeawareScreenshotTest(private val dark: Boolean, private val fontScale: Float) {

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

    private fun capture(name: String, content: @Composable () -> Unit) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent { UmamiTheme(darkTheme = dark, reduceMotion = true) { content() } }
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/${name}_$variant.png")
    }

    @Test
    fun list() = capture("Teaware_list") {
        TeawareScreen(state = TeawareStates.list, onBack = {}, onOpenVessel = {}, onAddVessel = {}, onRestore = {})
    }

    @Test
    fun empty() = capture("Teaware_empty") {
        TeawareScreen(
            state = TeawareUiState.Ready(emptyList(), emptyList()),
            onBack = {},
            onOpenVessel = {},
            onAddVessel = {},
            onRestore = {},
        )
    }

    @Test
    fun editor() = capture("VesselEditor_edit") {
        VesselEditorScreen(state = TeawareStates.editor, actions = VesselEditorActions())
    }

    @Test
    fun newWithErrors() = capture("VesselEditor_newErrors") {
        VesselEditorScreen(state = TeawareStates.newWithErrors, actions = VesselEditorActions())
    }

    @Test
    fun archived() = capture("VesselEditor_archived") {
        VesselEditorScreen(
            state = TeawareStates.editor.copy(isArchived = true),
            actions = VesselEditorActions(),
        )
    }
}

/** Synthetic states of SCR-13 and SCR-14 for the goldens. */
internal object TeawareStates {
    val list = TeawareUiState.Ready(
        vessels = listOf(
            VesselRow("k", "Kyusu 250 ml", VesselType.KYUSU, 300, 250, isFavorite = true),
            VesselRow("b", "Becher mit Sieb 400 ml", VesselType.MUG_INFUSER, 400, null, isFavorite = true),
            VesselRow("g", "Gaiwan", VesselType.GAIWAN, 120, null, isFavorite = false),
            VesselRow("h", "Houhin für Gyokuro aus Tokoname", VesselType.HOUHIN, 150, 120, isFavorite = false),
            VesselRow("c", "Cold-Brew-Flasche", VesselType.COLD_BREW_BOTTLE, 1000, 900, isFavorite = false),
        ),
        archived = listOf(VesselRow("a", "Alte Teekanne", VesselType.WESTERN_TEAPOT, 1200, null, isFavorite = false)),
    )

    val editor = VesselEditorUiState.Ready(
        form = VesselForm(
            name = "Kyusu 250 ml",
            type = VesselType.KYUSU,
            capacity = "300",
            usable = "250",
            material = VesselMaterial.UNGLAZED_CLAY,
            notes = "Aus Tokoname, feines Sieb.",
            isFavorite = true,
        ),
        isNew = false,
        isArchived = false,
        archiveBlocked = false,
        hasChanges = false,
        errors = emptySet(),
    )

    val newWithErrors = VesselEditorUiState.Ready(
        form = VesselForm(capacity = "5", usable = "250"),
        isNew = true,
        isArchived = false,
        archiveBlocked = false,
        hasChanges = true,
        errors = setOf(VesselFieldError.NameEmpty, VesselFieldError.CapacityRange, VesselFieldError.UsableRange),
    )
}
