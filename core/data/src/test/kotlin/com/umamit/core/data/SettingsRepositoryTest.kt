package com.umamit.core.data

import androidx.datastore.core.DataStoreFactory
import com.google.common.truth.Truth.assertThat
import com.umamit.core.datastore.SettingsDataSource
import com.umamit.core.datastore.SettingsSerializer
import com.umamit.core.model.UserSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Settings through the repository (05 §6, SET-10). */
class SettingsRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.repository() = DataStoreSettingsRepository(
        SettingsDataSource(
            DataStoreFactory.create(SettingsSerializer, scope = backgroundScope) { File(folder.root, "s.json") },
        ),
    )

    @Test
    fun freshInstallReadsTheSpecDefaultsAndNoSeedMarker() = runTest {
        val repository = repository()

        assertThat(repository.settings.first()).isEqualTo(UserSettings())
        assertThat(repository.seedDataCreated.first()).isFalse()
        assertThat(repository.seedPhotosAttempted.first()).isFalse()
        assertThat(repository.seedVesselsV2Added.first()).isFalse()
    }

    @Test
    fun seedVesselsV2MarkerStaysSetAfterOtherChanges() = runTest {
        // WARE-06 (D-093): the seven v0.2.1 vessels are added once; a settings change must never undo the marker.
        val repository = repository()
        repository.markSeedVesselsV2Added()
        repository.update { it.copy(onboardingCompleted = true) }

        assertThat(repository.seedVesselsV2Added.first()).isTrue()
        assertThat(repository.seedDataCreated.first()).isFalse()
    }

    @Test
    fun seedPhotoMarkerStaysSetAfterOtherChanges() = runTest {
        val repository = repository()
        repository.markSeedPhotosAttempted()
        repository.update { it.copy(onboardingCompleted = true) }

        assertThat(repository.seedPhotosAttempted.first()).isTrue()
        assertThat(repository.seedDataCreated.first()).isFalse()
    }

    @Test
    fun seedMarkerStaysSetAfterOtherChanges() = runTest {
        val repository = repository()
        repository.markSeedDataCreated()
        repository.update { it.copy(onboardingCompleted = true) }

        assertThat(repository.seedDataCreated.first()).isTrue()
        assertThat(repository.settings.first().onboardingCompleted).isTrue()
    }

    @Test
    fun preAlertIsKeptInZeroOrFiveToThirtyInStepsOfFive() = runTest {
        val repository = repository()

        repository.update { it.copy(preAlertSeconds = 12) }
        assertThat(repository.settings.first().preAlertSeconds).isEqualTo(10)
        repository.update { it.copy(preAlertSeconds = 45) }
        assertThat(repository.settings.first().preAlertSeconds).isEqualTo(30)
        repository.update { it.copy(preAlertSeconds = -5) }
        assertThat(repository.settings.first().preAlertSeconds).isEqualTo(0)
    }

    @Test
    fun theQuickTimersChoiceIsKeptInAutomatischAusOrFiveToThirty() = runTest {
        // TMR-22 (v0.2.2, D-097): like SET-10, snapped to 5 s and clamped to 0 or 5…30; null stays *Automatisch*.
        val repository = repository()
        assertThat(repository.settings.first().quickTimerPreAlert).isNull()

        val stored = listOf(20, 12, 45, -5, 0, null).map { choice ->
            repository.update { it.copy(quickTimerPreAlert = choice) }
            repository.settings.first().quickTimerPreAlert
        }

        assertThat(stored).containsExactly(20, 10, 30, 0, 0, null).inOrder()
    }
}
