package com.umamit.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import org.junit.Assert.assertEquals
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

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
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

    private fun awaitScreen() = compose.waitUntil(TIMEOUT_MS) {
        compose.onAllNodesWithTag(ReceiveRecipeTags.SCREEN).fetchSemanticsNodes().isNotEmpty()
    }

    private fun awaitText(text: String) = compose.waitUntil(TIMEOUT_MS) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
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

    private fun awaitText(text: String, partial: Boolean) = compose.waitUntil(TIMEOUT_MS) {
        compose.onAllNodesWithText(text, substring = partial).fetchSemanticsNodes().isNotEmpty()
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
