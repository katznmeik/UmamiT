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
}
