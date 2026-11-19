package com.umamit.feature.settings.backup

import android.app.ActivityManager
import android.content.Context
import android.database.SQLException
import android.util.Log
import com.umamit.core.backup.restore.TimerActivity
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.controller.BrewSessionController
import com.umamit.core.timer.controller.TimerUiState
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** How [AppDataWipe.wipe] ends. */
sealed interface WipeResult {
    /** The system takes the data and ends the app; nothing in it runs much longer. */
    data object Wiped : WipeResult

    /** A timer is active after all: nothing was deleted. */
    data object TimerActive : WipeResult

    /** The system refused: nothing was deleted (D-131 build notes, #346 answer 5). */
    data object Failed : WipeResult
}

/** *Alle Daten löschen* (SET-33): everything of the app, like a fresh install (D-131 Q1). */
fun interface AppDataWipe {
    suspend fun wipe(): WipeResult
}

/**
 * [AppDataWipe] through `ActivityManager.clearApplicationUserData()` (D-131 build notes): all app storage, its jobs,
 * alarms and notifications, then the process ends. A timer is checked again first, in memory (the controller's slot
 * states, read only) and in `active_brew`, like the import's [TimerActivity]. No timer code.
 */
class SystemAppDataWipe @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val timer: BrewSessionController,
    private val db: UmamiDatabase,
) : AppDataWipe {
    private val inMemory = TimerActivity { timer.slotStates.values.any { it.value !is TimerUiState.Idle } }

    override suspend fun wipe(): WipeResult = withContext(Dispatchers.IO) {
        val active = try {
            inMemory.isActive() || TimerSlot.entries.any { db.activeBrewDao().get(it) != null }
        } catch (e: SQLException) {
            Log.w(TAG, "active_brew not readable; nothing deleted", e)
            return@withContext WipeResult.Failed
        }
        if (active) return@withContext WipeResult.TimerActive
        val requested = try {
            context.getSystemService(ActivityManager::class.java)?.clearApplicationUserData() == true
        } catch (e: SecurityException) {
            Log.w(TAG, "app data not cleared", e)
            false
        }
        if (requested) WipeResult.Wiped else WipeResult.Failed
    }

    private companion object {
        const val TAG = "UmamiTWipe"
    }
}

/** The app's [AppDataWipe]. */
@Module
@InstallIn(SingletonComponent::class)
internal interface AppDataWipeModule {
    @Binds
    fun appDataWipe(impl: SystemAppDataWipe): AppDataWipe
}
