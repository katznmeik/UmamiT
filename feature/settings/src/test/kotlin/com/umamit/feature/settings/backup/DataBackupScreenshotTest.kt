package com.umamit.feature.settings.backup

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.backup.archive.BackupEstimate
import com.umamit.core.backup.archive.BackupRefusal
import com.umamit.core.backup.restore.RestoreRefusal
import com.umamit.core.backup.restore.UndoPreview
import com.umamit.core.designsystem.theme.UmamiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZoneId

/**
 * *Daten & Sicherung* (SCR-26) in the states of #338's acceptance criteria, in light and dark at font scale 1.0 and
 * 2.0 (03 §6), on synthetic data. Goldens: `src/test/screenshots/`.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class DataBackupScreenshotTest(private val dark: Boolean, private val fontScale: Float) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}, fontScale={1}")
        fun parameters(): List<Array<Any>> = listOf(false, true).flatMap { dark ->
            listOf(1f, 2f).map { scale -> arrayOf<Any>(dark, scale) }
        }

        private val BERLIN = ZoneId.of("Europe/Berlin")

        /** 3 Nov 2026, 10:00 in Berlin, with 12 sessions since. */
        private val SAVED = LastBackup(atMs = 1_793_696_400_000L, newSessions = 12)

        /** Until 25 Nov 2026, 08:30 in Berlin. */
        private const val UNTIL_MS = 1_795_591_800_000L

        private val ESTIMATE = BackupEstimate(sessions = 142, teas = 23, photos = 61, bytes = 18L * 1024 * 1024)
    }

    @get:Rule
    val compose = createComposeRule()

    private val variant get() = "${if (dark) "dark" else "light"}_font${fontScale.toString().replace('.', '_')}"

    private fun capture(name: String, state: DataBackupUiState, snackbar: Boolean = false) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            Themed {
                DataBackupScreen(state, alerting = false, onBack = {}, actions = DataBackupActions(), zone = BERLIN)
            }
        }
        if (snackbar) {
            // The snackbar stays while the clock stands (it would time out under the auto-advancing clock).
            compose.mainClock.autoAdvance = false
            compose.mainClock.advanceTimeBy(1_000)
        }
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/DataBackup_${name}_$variant.png")
    }

    @Composable
    private fun Themed(content: @Composable () -> Unit) =
        UmamiTheme(darkTheme = dark, reduceMotion = true, content = content)

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
    fun sizeNote() = capture("sizeNote", DataBackupUiState(dialog = BackupDialog.SizeNote(ESTIMATE)))

    @Test
    fun progress() = capture("progress", DataBackupUiState(lastBackup = SAVED, export = ExportProgress(12, 61)))

    @Test
    fun preview() = capture("preview", DataBackupUiState(dialog = BackupDialog.Replace(FakeDataBackup.PREVIEW)))

    /** *Ersetzen* enabled: exactly „ERSETZEN" typed (BAK-02 AC3). */
    @Test
    fun previewTyped() = capture(
        "previewTyped",
        DataBackupUiState(dialog = BackupDialog.Replace(FakeDataBackup.PREVIEW), confirmText = "ERSETZEN"),
    )

    @Test
    fun undoWithCounts() = capture(
        "undoCounts",
        DataBackupUiState(
            undo = UndoPreview(3, 1, UNTIL_MS),
            dialog = BackupDialog.Undo(UndoPreview(sessions = 3, teas = 1, availableUntil = UNTIL_MS)),
        ),
    )

    @Test
    fun undoWithNone() = capture(
        "undoNone",
        DataBackupUiState(
            undo = UndoPreview(0, 0, UNTIL_MS),
            dialog = BackupDialog.Undo(UndoPreview(sessions = 0, teas = 0, availableUntil = UNTIL_MS)),
        ),
    )

    /** *Sicherung einspielen* and *Import rückgängig machen* greyed out while a timer runs (BAK-02 AC4). */
    @Test
    fun timerActive() = capture(
        "timerActive",
        DataBackupUiState(lastBackup = SAVED, timerActive = true, undo = UndoPreview(0, 0, UNTIL_MS)),
    )

    @Test
    fun importing() = capture("importing", DataBackupUiState(busy = BackupBusy.IMPORTING))

    private fun refused(name: String, message: Int) =
        capture("refused_$name", DataBackupUiState(message = message), snackbar = true)

    // Each refusal of 03 §5 as the screen shows it (BAK-02 AC2, AC4, AC5, AC6).

    @Test
    fun refusedNotABackup() = refused("notABackup", BackupRefusal.NOT_A_BACKUP.messageRes)

    @Test
    fun refusedNewerVersion() = refused("newerVersion", BackupRefusal.NEWER_VERSION.messageRes)

    @Test
    fun refusedDamaged() = refused("damaged", BackupRefusal.DAMAGED.messageRes)

    @Test
    fun refusedTimerActive() = refused("timerActive", RestoreRefusal.TIMER_ACTIVE.messageRes)

    @Test
    fun refusedCatalogRefresh() = refused("catalogRefresh", RestoreRefusal.CATALOG_REFRESH.messageRes)

    @Test
    fun refusedSafetyCopy() = refused("safetyCopy", RestoreRefusal.SAFETY_COPY_FAILED.messageRes)

    @Test
    fun refusedReplaceFailed() = refused("replaceFailed", RestoreRefusal.REPLACE_FAILED.messageRes)
}
