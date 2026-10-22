package com.umamit.feature.settings.diagnostics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.diagnostics.DiagnosticsLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

/** The *Diagnose* screen's state follows the real log: newest first, and new entries appear. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DiagnosticsViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the state shows the log newest first and follows new entries`() = runBlocking {
        val log = DiagnosticsLog(folder.newFolder("diagnostics"), DiagnosticsLog.MAX_BYTES, Dispatchers.IO)
        log.append(0, ZoneId.of("UTC"), "first")
        val viewModel = DiagnosticsViewModel(log, context)

        withTimeout(5_000) { viewModel.state.first { it.entries?.size == 1 } }
        log.append(1, ZoneId.of("UTC"), "second")
        val state = withTimeout(5_000) { viewModel.state.first { it.entries?.size == 2 } }

        assertThat(state.entries!!.map { it.message }).containsExactly("second", "first").inOrder()
        assertThat(state.header).startsWith("UmamiT ")
        assertThat(state.header).contains("API 36")
    }
}
