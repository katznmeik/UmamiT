package com.umamit.feature.collection.recipe

import android.content.Intent
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** *Rezept teilen*'s intent (PRF-08 AC3, SCR-12): the share sheet with the text and one readable file. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecipeShareIntentTest {
    private val uri = Uri.parse("content://com.umamit.app.files/shared/Morgennebel.umamit-rezept.json")

    private fun inner(chooser: Intent): Intent =
        requireNotNull(chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java))

    @Test
    fun `the chooser wraps one send with the text and the file`() {
        val chooser = RecipeShareIntent.chooser(uri, "Morgennebel - Rezept", "Rezept teilen")

        assertThat(chooser.action).isEqualTo(Intent.ACTION_CHOOSER)
        assertThat(chooser.getCharSequenceExtra(Intent.EXTRA_TITLE)).isEqualTo("Rezept teilen")
        val send = inner(chooser)
        assertThat(send.action).isEqualTo(Intent.ACTION_SEND)
        assertThat(send.type).isEqualTo("application/json")
        assertThat(send.getStringExtra(Intent.EXTRA_TEXT)).isEqualTo("Morgennebel - Rezept")
        assertThat(send.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)).isEqualTo(uri)
    }

    @Test
    fun `only read access to that one file is granted`() {
        val send = inner(RecipeShareIntent.chooser(uri, "Text", "Rezept teilen"))

        assertThat(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)
        assertThat(send.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION).isEqualTo(0)
        val clip = requireNotNull(send.clipData)
        assertThat(clip.itemCount).isEqualTo(1)
        assertThat(clip.getItemAt(0).uri).isEqualTo(uri)
    }
}
