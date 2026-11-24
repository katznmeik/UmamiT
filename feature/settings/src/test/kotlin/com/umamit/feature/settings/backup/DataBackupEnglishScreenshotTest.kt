package com.umamit.feature.settings.backup

import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.backup.restore.UndoPreview
import com.umamit.core.designsystem.theme.UmamiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZoneId

/**
 * *Data & backup* (SCR-26) in English (GEN-10, #359), light and dark at font scale 1.0, on synthetic data: never
 * saved, saved with an undo, the restore dialog with its typed word, and the delete dialog. Goldens:
 * `src/test/screenshots/`.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w411dp-h891dp-xhdpi")
class DataBackupEnglishScreenshotTest(private val dark: Boolean) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun parameters(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))

        private val BERLIN = ZoneId.of("Europe/Berlin")

        /** 3 Nov 2026, 10:00 in Berlin, with 12 sessions since. */
        private val SAVED = LastBackup(atMs = 1_793_696_400_000L, newSessions = 12)

        /** Until 25 Nov 2026, 08:30 in Berlin. */
        private const val UNTIL_MS = 1_795_591_800_000L
    }

    @get:Rule
    val compose = createComposeRule()

    private val variant get() = if (dark) "dark" else "light"

    private fun capture(name: String, state: DataBackupUiState) {
        // A standing clock, as the German goldens: the snackbar stays and a spinner never settles.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            UmamiTheme(darkTheme = dark, reduceMotion = true) {
                DataBackupScreen(state, alerting = false, onBack = {}, actions = DataBackupActions(), zone = BERLIN)
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/DataBackupEn_${name}_$variant.png")
    }

    @Test
    fun neverSaved() = capture("neverSaved", DataBackupUiState())

    @Test
    fun savedWithUndo() = capture(
        "saved",
        DataBackupUiState(
            lastBackup = SAVED,
            undo = UndoPreview(sessions = 3, teas = 1, availableUntil = UNTIL_MS),
            reminderEnabled = false,
        ),
    )

    @Test
    fun restoreDialogTyped() = capture(
        "restoreTyped",
        DataBackupUiState(
            dialog = BackupDialog.Replace(FakeDataBackup.PREVIEW),
            confirmText = DataBackupUiState.confirmWord(),
        ),
    )

    @Test
    fun wipeDialog() = capture("wipeDialog", DataBackupUiState(dialog = BackupDialog.Wipe(safetyCopy = false)))
}
