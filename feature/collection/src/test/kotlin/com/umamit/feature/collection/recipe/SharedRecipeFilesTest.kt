package com.umamit.feature.collection.recipe

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The shared recipe files in `cacheDir/shared/` (PRF-08, 06 §5.7): their names and the day-old cleanup. */
class SharedRecipeFilesTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val cacheDir: File get() = temp.root

    @Test
    fun `a recipe is written to shared under the tea name`() {
        val file = SharedRecipeFiles.write(cacheDir, "Morgennebel Sencha", byteArrayOf(1, 2, 3))

        assertThat(file).isEqualTo(File(cacheDir, "shared/Morgennebel Sencha.umamit-rezept.json"))
        assertThat(file.readBytes()).isEqualTo(byteArrayOf(1, 2, 3))
    }

    @Test
    fun `a hostile tea name stays inside shared`() {
        val file = SharedRecipeFiles.write(cacheDir, "../../Nebel/..\\Tau:*?", byteArrayOf(7))

        assertThat(file.parentFile).isEqualTo(File(cacheDir, "shared"))
        assertThat(file.name).isEqualTo("....Nebel..Tau.umamit-rezept.json")
        assertThat(file.readBytes()).isEqualTo(byteArrayOf(7))
    }

    @Test
    fun `sharing the same tea again replaces the file`() {
        SharedRecipeFiles.write(cacheDir, "Morgennebel", byteArrayOf(1, 2, 3, 4))
        val file = SharedRecipeFiles.write(cacheDir, "Morgennebel", byteArrayOf(9))

        assertThat(file.readBytes()).isEqualTo(byteArrayOf(9))
        assertThat(File(cacheDir, "shared").list()).asList().containsExactly("Morgennebel.umamit-rezept.json")
    }

    @Test
    fun `files older than a day are deleted and newer ones stay`() {
        val now = 1_800_000_000_000L
        val old = SharedRecipeFiles.write(cacheDir, "Alt", byteArrayOf(1)).apply { setLastModified(now - DAY - 1_000) }
        val fresh = SharedRecipeFiles.write(cacheDir, "Neu", byteArrayOf(2)).apply {
            setLastModified(now - DAY + 60_000)
        }
        val outside = File(cacheDir, "andere.txt").apply {
            writeText("bleibt")
            setLastModified(now - 10 * DAY)
        }

        SharedRecipeFiles.deleteStale(cacheDir, now)

        assertThat(old.exists()).isFalse()
        assertThat(fresh.exists()).isTrue()
        assertThat(outside.exists()).isTrue()
    }

    @Test
    fun `cleanup without a shared folder does nothing`() {
        SharedRecipeFiles.deleteStale(cacheDir, 1_800_000_000_000L)

        assertThat(File(cacheDir, "shared").exists()).isFalse()
    }

    private companion object {
        const val DAY = 24 * 60 * 60 * 1_000L
    }
}
