package com.umamit.feature.settings

import android.app.UiModeManager
import android.content.Context
import androidx.core.content.edit
import com.umamit.core.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Hands the *Design* (SET-05, D-114) to the system, which keeps it for the app across starts: the starting
 * window and the app's configuration (`isSystemInDarkTheme`) then follow it, so a start shows the chosen
 * theme from the very first window with no read of the settings first.
 *
 * Only the settings screen calls it ([SettingsViewModel.handOverTheme]): when the *Design* dialog closes, and
 * whenever the screen shows with no alert playing, so a pick an alert interrupted or a theme a backup restore
 * brought is handed over then (PO, motion review M21). A change re-creates the activity (no
 * `configChanges="uiMode"`), which restores its state (S1, #47); it is never called while an alert plays (rule 8).
 *
 * The system has no getter for the app's night mode, so the mode last handed over is kept here, in the app's
 * private preferences, which no backup includes (05 §8): after a restore, or on a new install, nothing is recorded
 * and the system follows the phone, as *System* does.
 */
class AppNightMode internal constructor(private val context: Context, private val io: CoroutineDispatcher) {
    @Inject constructor(@ApplicationContext context: Context) : this(context, Dispatchers.IO)

    private val preferences by lazy { context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE) }

    /** The choice last handed to the system; *System* when none was (the system then follows the phone). */
    suspend fun handedOver(): ThemeMode = withContext(io) {
        preferences.getString(KEY_HANDED, null)?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
            ?: ThemeMode.SYSTEM
    }

    /** Hands [mode] to the system and records it; on the main thread, right after the check for an alert. */
    fun apply(mode: ThemeMode) {
        context.getSystemService(UiModeManager::class.java)?.setApplicationNightMode(mode.uiNightMode())
        preferences.edit { putString(KEY_HANDED, mode.name) }
    }

    private companion object {
        const val PREFERENCES = "umamit_night_mode"
        const val KEY_HANDED = "handed_over"
    }
}

/** The [UiModeManager] night mode for [this] choice: *System* is `MODE_NIGHT_AUTO`, which follows the phone. */
internal fun ThemeMode.uiNightMode(): Int = when (this) {
    ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
    ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
    ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
}
