package com.umamit.feature.settings.backup

import android.content.Context
import android.content.ContextWrapper
import com.google.common.truth.Truth.assertThat
import com.umamit.core.database.entity.ActiveBrewEntity
import com.umamit.core.model.TimerPhase
import com.umamit.core.model.TimerSlot
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * *Alle Daten löschen*'s adapter (SET-33, D-131, D-133 item 13): it checks the timer again in `active_brew` before
 * the system wipe, and a refused wipe is a failure with nothing deleted. Synthetic data (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SystemAppDataWipeTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val harness by lazy { BackupAdapterHarness(folder.newFolder("app")) }

    @After
    fun tearDown() = harness.close()

    @Test
    fun `with no timer the system wipes`() = runBlocking {
        val wipe = SystemAppDataWipe(harness.context, harness.timer, harness.db)

        assertThat(wipe.wipe()).isEqualTo(WipeResult.Wiped)
    }

    @Test
    fun `a timer in active_brew stops the wipe`() = runBlocking {
        harness.db.activeBrewDao().upsert(quickTimerRow())
        val wipe = SystemAppDataWipe(harness.context, harness.timer, harness.db)

        assertThat(wipe.wipe()).isEqualTo(WipeResult.TimerActive)
    }

    @Test
    fun `a system that gives no wipe is a failure`() = runBlocking {
        // No ActivityManager: the request cannot be made, nothing is deleted.
        val noSystem = object : ContextWrapper(harness.context) {
            override fun getSystemService(name: String): Any? =
                if (name == Context.ACTIVITY_SERVICE) null else super.getSystemService(name)
        }
        val wipe = SystemAppDataWipe(noSystem, harness.timer, harness.db)

        assertThat(wipe.wipe()).isEqualTo(WipeResult.Failed)
    }

    private fun quickTimerRow() = ActiveBrewEntity(
        slot = TimerSlot.QUICK,
        sessionId = null,
        isQuickTimer = true,
        quickDurationSeconds = 120,
        stepIndex = 0,
        phase = TimerPhase.RUNNING,
        deadlineElapsedMs = 60_000,
        deadlineWallMs = T0 + 60_000,
        preAlertElapsedMs = null,
        preAlertPlayed = false,
        remainingMs = null,
        pausedTotalMs = 0,
        pausedSinceElapsedMs = null,
        pausedSinceWallMs = null,
        readySinceWallMs = null,
        stepStartedWallMs = T0,
        alertPlayed = false,
        bootCount = 1,
        lastInteractionWallMs = T0,
        demoted = false,
        preAlertInheritedSeconds = 10,
        zoneId = "Europe/Berlin",
        createdAt = T0,
        updatedAt = T0,
        quickPreAlertSeconds = null,
        alertRingingSinceElapsedMs = null,
        alertRingingSinceWallMs = null,
    )

    private companion object {
        const val T0 = 1_794_992_400_000L
    }
}
