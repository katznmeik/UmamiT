package com.umamit.feature.settings

import android.app.UiModeManager
import android.content.Context
import com.umamit.core.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Hands the *Design* (SET-05, D-114) to the system, which keeps it for the app across starts: the starting
 * window and the app's configuration (`isSystemInDarkTheme`) then follow it, so a start shows the chosen
 * theme from the very first window with no read of the settings first.
 *
 * Only a pick in the settings calls it ([SettingsViewModel.setTheme]), after the pick is stored, so the system
 * and the stored `theme` stay in step. A change re-creates the activity (no `configChanges="uiMode"`), which
 * restores its state (S1, #47); it is never called while an alert plays (rule 8).
 */
class AppNightMode @Inject constructor(@ApplicationContext private val context: Context) {
    fun apply(mode: ThemeMode) {
        context.getSystemService(UiModeManager::class.java)?.setApplicationNightMode(mode.uiNightMode())
    }
}

/** The [UiModeManager] night mode for [this] choice: *System* is `MODE_NIGHT_AUTO`, which follows the phone. */
internal fun ThemeMode.uiNightMode(): Int = when (this) {
    ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
    ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
    ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
}
