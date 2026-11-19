package com.umamit.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.umamit.core.backup.recipe.RecipeFile
import com.umamit.core.backup.recipe.RecipeFileResult
import com.umamit.core.backup.recipe.SharedRecipe
import com.umamit.core.backup.recipe.SharedTea
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.StepKind
import com.umamit.core.model.TeaCategory
import com.umamit.core.timer.UnlockedDeviceRule
import com.umamit.feature.collection.receive.ReceiveRecipeTags
import com.umamit.feature.collection.recipe.SharedRecipeFiles
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.OffsetDateTime

/**
 * Receiving a recipe file on the device (PRF-08 (a) and (b), AC5, 06 §5.7): `VIEW` and `SEND` with each of the two
 * MIME types reach SCR-32 „Rezept übernehmen" through ReceiveRecipeActivity and MainActivity; a 65 KB stream and a
 * `file://` URI are refused with the reader's message; nothing is written before *Rezept übernehmen*. The files go
 * through the app's own FileProvider (`cache/shared/`). Runs only in the CI emulator job (no KVM here). Synthetic
 * recipe (D-016).
 */
@RunWith(AndroidJUnit4::class)
class ReceiveRecipeDeviceTest {
    @get:Rule(order = 0)
    val unlocked = UnlockedDeviceRule()

    @get:Rule(order = 1)
    val setupDone = SetupDoneRule()

    @get:Rule(order = 2)
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val teas = EntryPointAccessors
        .fromApplication(context.applicationContext as Application, TestDataEntryPoint::class.java)
        .teas()

    private val recipe = SharedRecipe(
        tea = SharedTea(name = "Erfundener Bancha", category = TeaCategory.GREEN),
        name = "Becher",
        method = BrewMethod.WESTERN,
        leafGrams = 2.5,
        waterMl = 250,
        baseTempC = 80,
        steps = listOf(RecipeStep(StepKind.INFUSION, 120)),
    )

    private fun shared(bytes: ByteArray, name: String = "Erfundener Bancha"): Uri {
        val file = SharedRecipeFiles.write(context.cacheDir, name, bytes)
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    private fun receive(intent: Intent) {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun teaCount(): Int = runBlocking { teas.observeTeas().first().size }

    /**
     * The hand-over starts MainActivity outside any scenario of the rule: first wait until it is resumed (so its
     * `setContent` has run), then for SCR-32 in it. Until a compose hierarchy exists the rule throws instead of
     * waiting, so that counts as „not yet".
     */
    private fun awaitScreen() {
        awaitResumedMainActivity()
        awaitNodes { compose.onAllNodesWithTag(ReceiveRecipeTags.SCREEN).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun awaitText(text: String, partial: Boolean = false) = awaitNodes {
        compose.onAllNodesWithText(text, substring = partial).fetchSemanticsNodes().isNotEmpty()
    }

    private fun awaitNodes(found: () -> Boolean) = compose.waitUntil(TIMEOUT_MS) {
        try {
            found()
        } catch (_: IllegalStateException) {
            // „No compose hierarchies found": MainActivity's content is not attached yet.
            false
        }
    }

    private fun awaitResumedMainActivity() {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
        while (mainActivities(Stage.RESUMED).isEmpty()) {
            check(SystemClock.uptimeMillis() < deadline) { "MainActivity was not resumed by the hand-over" }
            SystemClock.sleep(POLL_MS)
        }
    }

    private fun mainActivities(vararg stages: Stage): List<MainActivity> {
        var found = emptyList<MainActivity>()
        instrumentation.runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            found = stages.flatMap { monitor.getActivitiesInStage(it) }.filterIsInstance<MainActivity>()
        }
        return found
    }

    /** Each test starts without a MainActivity, so no screen of an earlier test can answer its waits. */
    @Before
    @After
    fun finishMainActivities() {
        instrumentation.runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            Stage.entries.flatMap { monitor.getActivitiesInStage(it) }
                .filterIsInstance<MainActivity>()
                .forEach { it.finish() }
        }
        instrumentation.waitForIdleSync()
    }

    @Test
    fun viewAndSendWithEitherTypeReachRezeptUebernehmenAndWriteNothing() {
        val before = teaCount()
        val ways = listOf(RecipeFile.MIME_TYPE, "application/json").flatMap { type ->
            listOf(
                { uri: Uri -> ReceiveRecipeActivity.view(context, uri, type) },
                { uri: Uri -> ReceiveRecipeActivity.send(context, uri, type) },
            )
        }
        ways.forEachIndexed { index, way ->
            // A tea name of its own per way, so each wait sees its own file on SCR-32.
            val name = "Erfundener Bancha $index"
            val file = recipe.copy(tea = recipe.tea.copy(name = name))
            receive(way(shared(RecipeFile.write(file, OffsetDateTime.now()), name)))
            awaitScreen()
            awaitText(name, partial = true)
        }
        assertEquals(before, teaCount())
    }

    @Test
    fun aStreamOver64KbIsRefused() {
        val bytes = ByteArray(65 * 1024) { ' '.code.toByte() }
        val uri = shared(bytes, name = "Zu gross")

        receive(ReceiveRecipeActivity.send(context, uri))

        awaitScreen()
        awaitText(refusalOf(bytes))
    }

    @Test
    fun aFileUriIsRefused() {
        val file = File(context.cacheDir, "direkt.umamit-rezept").apply {
            writeBytes(RecipeFile.write(recipe, OffsetDateTime.now()))
        }
        val before = teaCount()

        receive(ReceiveRecipeActivity.view(context, Uri.fromFile(file)))

        awaitScreen()
        // Handed on empty: whatever the reader says to that.
        awaitText(refusalOf(ByteArray(0)))
        assertEquals(before, teaCount())
    }

    /** The reader's own message for [bytes]. */
    private fun refusalOf(bytes: ByteArray): String =
        context.getString((RecipeFile.read(bytes) as RecipeFileResult.Refused).refusal.messageRes)

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val POLL_MS = 50L
    }
}
