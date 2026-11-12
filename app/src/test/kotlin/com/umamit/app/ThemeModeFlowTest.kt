package com.umamit.app

import androidx.datastore.core.DataStoreFactory
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.DataStoreSettingsRepository
import com.umamit.core.datastore.SettingsDataSource
import com.umamit.core.datastore.SettingsSerializer
import com.umamit.core.model.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The app theme's choice on the real settings repository (SET-05, D-114): read from the file, changes follow. */
class ThemeModeFlowTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() = scope.cancel()

    private fun repository(): DataStoreSettingsRepository = DataStoreSettingsRepository(
        SettingsDataSource(
            DataStoreFactory.create(SettingsSerializer, scope = scope) {
                folder.root.resolve("s.json")
            },
        ),
    )

    @Test
    fun `a fresh install reads System`() = runBlocking {
        assertThat(repository().themeMode().first()).isEqualTo(ThemeMode.SYSTEM)
    }

    @Test
    fun `a stored choice is read and a change follows`() = runBlocking {
        val repository = repository()
        repository.update { it.copy(theme = ThemeMode.DARK) }
        assertThat(repository.themeMode().first()).isEqualTo(ThemeMode.DARK)
        repository.update { it.copy(theme = ThemeMode.LIGHT) }
        assertThat(repository.themeMode().first()).isEqualTo(ThemeMode.LIGHT)
    }
}
