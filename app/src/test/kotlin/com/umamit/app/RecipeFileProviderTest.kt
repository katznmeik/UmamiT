package com.umamit.app

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.feature.collection.recipe.AndroidRecipeSharer
import com.umamit.feature.collection.recipe.SharedRecipeFiles
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The recipe file's `FileProvider` in the merged manifest (PRF-08 AC3, 06 §5.7): not exported, no permission,
 * `grantUriPermissions`, and it serves `cache/shared/` only, with the recipe file's share type `application/json`
 * (D-134 item 5).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecipeFileProviderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val authority = AndroidRecipeSharer.authority(context)

    @Test
    fun theProviderIsNotExportedAndOnlyGrantsUriPermissions() {
        val info =
            requireNotNull(context.packageManager.resolveContentProvider(authority, PackageManager.GET_META_DATA))

        assertThat(info.name).isEqualTo(RecipeFileProvider::class.java.name)
        assertThat(info.authority).isEqualTo("${context.packageName}.files")
        assertThat(info.exported).isFalse()
        assertThat(info.grantUriPermissions).isTrue()
        assertThat(info.readPermission).isNull()
        assertThat(info.writePermission).isNull()
    }

    /**
     * `FileProvider` keeps an authority's roots in a static cache for the process, while Robolectric gives each test
     * its own data dir: the cache is cleared first, so the roots are this test's.
     */
    @Before
    fun forgetCachedRoots() {
        FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
            .let { (it.get(null) as MutableMap<*, *>).clear() }
    }

    @Test
    fun onlyFilesInCacheSharedGetAContentUriWithTheRecipeType() {
        val file = SharedRecipeFiles.write(context.cacheDir, "Morgennebel", byteArrayOf(1, 2, 3))

        val uri = FileProvider.getUriForFile(context, authority, file)

        assertThat(uri.scheme).isEqualTo("content")
        assertThat(uri.authority).isEqualTo(authority)
        assertThat(context.contentResolver.getType(uri)).isEqualTo("application/json")

        val elsewhere = listOf(
            File(context.cacheDir, "andere.txt"),
            File(context.filesDir, "photos/foto.jpg"),
            File(context.cacheDir.parentFile, "databases/umamit.db"),
        )
        elsewhere.forEach { other ->
            other.parentFile?.mkdirs()
            other.writeText("x")
            assertThrows(IllegalArgumentException::class.java) {
                FileProvider.getUriForFile(context, authority, other)
            }
        }
    }
}
