package com.umamit.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.umamit.core.backup.recipe.RecipeFile
import com.umamit.core.backup.recipe.SharedRecipe
import com.umamit.core.backup.recipe.SharedTea
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.StepKind
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.VesselType
import com.umamit.feature.collection.recipe.AndroidRecipeSharer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * *Rezept teilen* on a device (PRF-08 AC1, AC3; ticket #341): the chooser's inner `ACTION_SEND` carries the share
 * text and a readable `content://` stream of the app's `FileProvider` with the recipe type and the file's bytes.
 * Invented recipe (D-016).
 */
@RunWith(AndroidJUnit4::class)
class RecipeShareDeviceTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val createdAt = OffsetDateTime.of(2026, 10, 21, 8, 30, 0, 0, ZoneOffset.ofHours(2))

    private val recipe = SharedRecipe(
        tea = SharedTea(name = "Morgennebel", category = TeaCategory.GREEN, secondaryName = "Probe"),
        name = "Kyusu",
        method = BrewMethod.KYUSU,
        leafGrams = 4.0,
        waterMl = 120,
        baseTempC = 60,
        steps = listOf(RecipeStep(StepKind.RINSE, 10), RecipeStep(StepKind.INFUSION, 90)),
        vesselType = VesselType.KYUSU,
    )

    @Test
    fun theChoosersInnerIntentHasTheTextAndAReadableRecipeStream() {
        val chooser = runBlocking { AndroidRecipeSharer(context) { createdAt }.chooser(recipe) }

        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = requireNotNull(chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java))
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals(RecipeFile.MIME_TYPE, send.type)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)

        val expected = listOf(
            "Morgennebel (Probe) – Rezept „Kyusu“",
            "Kyusu · 4,0${NBSP}g auf 120${NBSP}ml · 60$NBSP°C · Gefäß: Kyusu",
            "1. Spülen · 60$NBSP°C · 0:10",
            "2. Aufguss · 60$NBSP°C · 1:30",
            "Geteilt aus UmamiT – öffne die angehängte Datei in UmamiT, um das Rezept zu übernehmen.",
        ).joinToString("\n")
        assertEquals(expected, send.getStringExtra(Intent.EXTRA_TEXT))

        val stream = send.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)!!
        assertEquals("content", stream.scheme)
        assertEquals(AndroidRecipeSharer.authority(context), stream.authority)
        assertEquals("Morgennebel.umamit-rezept", stream.lastPathSegment)
        assertEquals(RecipeFile.MIME_TYPE, context.contentResolver.getType(stream))
        val bytes = context.contentResolver.openInputStream(stream)!!.use { it.readBytes() }
        assertArrayEquals(RecipeFile.write(recipe, createdAt), bytes)
    }

    private companion object {
        /** The no-break space between a number and its unit (05 §7), as in `core/backup`'s share-text golden. */
        const val NBSP = "\u00A0"
    }
}
