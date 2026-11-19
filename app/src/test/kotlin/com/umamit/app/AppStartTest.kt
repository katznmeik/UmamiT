package com.umamit.app

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.app.AppStart.launchGuarded
import com.umamit.core.backup.restore.ImportRecovery
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

/**
 * The guarded process start (D-133 item 13, findings 4 and 12 of the T4 review): the import's start gate runs only
 * with a marker, and its false or its throw (the graph's `get()` included) never stops the start; the start-up launches
 * other than the timer's recovery log a failure instead of ending the process. And the gate as `onCreate` wires it,
 * on the app's own folders.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppStartTest {
    private val app: UmamitApplication = ApplicationProvider.getApplicationContext()

    private fun failWith(e: Exception): Boolean = throw e

    @Test
    fun theGateRunsOnlyWithAMarker() {
        var recovered = false

        AppStart.importGate(isNeeded = { false }) { true.also { recovered = true } }

        assertThat(recovered).isFalse()
    }

    @Test
    fun aGateThatCannotFinishOrThrowsLetsTheStartGoOn() {
        val after = mutableListOf<String>()

        AppStart.importGate(isNeeded = { true }) { false }
        after += "false"
        AppStart.importGate(isNeeded = { true }) { failWith(IOException("disk")) }
        after += "IOException"
        AppStart.importGate(isNeeded = { true }) { failWith(IllegalStateException("graph")) }
        after += "graph"
        AppStart.importGate(isNeeded = { failWith(SecurityException("no folder")) }) { true }
        after += "isNeeded"

        assertThat(after).containsExactly("false", "IOException", "graph", "isNeeded").inOrder()
    }

    @Test
    fun aFailingStartUpLaunchIsLoggedAndNeverReachesTheProcessHandler() = runBlocking {
        val crashes = mutableListOf<Throwable>()
        val handler = CoroutineExceptionHandler { _, e -> crashes += e }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + handler)
        var sibling = false

        scope.launchGuarded("graph") { error("the graph could not be built") }.join()
        scope.launchGuarded("io", Dispatchers.IO) { throw IOException("disk") }.join()
        scope.launchGuarded("next") { sibling = true }.join()

        assertThat(crashes).isEmpty()
        assertThat(sibling).isTrue()
        // A cancellation still cancels.
        val waiting = scope.launchGuarded("waiting") { awaitCancellation() }
        waiting.cancelAndJoin()
        assertThat(waiting.isCancelled).isTrue()
    }

    @Test
    fun onCreateRunsTheGateBeforeTheStartGoesOn() {
        // A crash before an import's commit: the marker (a token umamit.db does not hold) and its staged photos.
        val staged = File(app.filesDir, "photos.new").apply { mkdirs() }
        File(staged, "imported.jpg").writeBytes(byteArrayOf(4, 5, 6))
        File(app.noBackupFilesDir, "import/marker.json").apply {
            parentFile!!.mkdirs()
            writeText("""{"v":1,"kind":"IMPORT","token":424242,"phase":"PREPARED"}""")
        }

        app.onCreate()

        assertThat(ImportRecovery.isNeeded(app)).isFalse()
        assertThat(staged.exists()).isFalse()
    }
}
