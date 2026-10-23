package com.umamit.core.data

import androidx.datastore.core.DataStoreFactory
import com.google.common.truth.Truth.assertThat
import com.umamit.core.datastore.SettingsDataSource
import com.umamit.core.datastore.SettingsSerializer
import com.umamit.core.model.UserSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

/**
 * An unreadable settings file (DataStore corruption, a disk error) never fails the settings flow
 * (#54, D-068): the timer, setup and settings screens read the defaults, the failure is logged,
 * and the file is read again, so it is followed once it reads.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UnreadableSettingsTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun aFileThatCannotBeReadReadsAsTheDefaults() = runTest {
        // A directory where the file should be: DataStore's read throws an IOException.
        val path = File(folder.root, "s.json").apply { mkdirs() }
        val repository = DataStoreSettingsRepository(
            SettingsDataSource(DataStoreFactory.create(SettingsSerializer, scope = backgroundScope) { path }),
        )

        assertThat(repository.settings.first()).isEqualTo(UserSettings())
    }

    @Test
    fun aFailedReadGivesTheDefaultsThenTheFileOnceItReadsAgain() = runTest {
        var reads = 0
        val stored = UserSettings(alertSound = "glocke", vibration = false)
        val source: Flow<UserSettings> = flow {
            if (reads++ < 2) throw IOException("settings file unreadable (test)")
            emit(stored)
        }
        val failures = mutableListOf<IOException>()
        val seen = mutableListOf<UserSettings>()
        backgroundScope.launch { source.readable { failures += it }.toList(seen) }

        runCurrent()
        assertThat(seen).containsExactly(UserSettings())

        advanceTimeBy(FIRST_SETTINGS_RETRY_MS + 1)
        assertThat(seen).containsExactly(UserSettings())
        advanceTimeBy(2 * FIRST_SETTINGS_RETRY_MS + 1)

        assertThat(seen).containsExactly(UserSettings(), stored).inOrder()
        assertThat(failures).hasSize(2)
    }

    @Test
    fun otherErrorsAreNotHidden() = runTest {
        val broken: Flow<UserSettings> = flow { error("a bug, not the file (test)") }

        val failure = runCatching { broken.readable {}.first() }.exceptionOrNull()

        assertThat(failure).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun theRetryBacksOffUpToAMinute() {
        assertThat((0L..7L).map(::settingsRetryDelayMs))
            .containsExactly(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L, 60_000L).inOrder()
    }
}
