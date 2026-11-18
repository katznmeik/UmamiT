package com.umamit.feature.collection.recipe

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.umamit.core.backup.recipe.RecipeFile
import com.umamit.core.backup.recipe.RecipeShareText
import com.umamit.core.backup.recipe.ResourceRecipeShareLabels
import com.umamit.core.backup.recipe.SharedRecipe
import com.umamit.feature.collection.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.OffsetDateTime

/**
 * The recipe files *Rezept teilen* writes (PRF-08, 06 §5.7): `cacheDir/shared/<Tee>.umamit-rezept`, the only folder
 * the app's `FileProvider` serves, and its cleanup at app start.
 */
object SharedRecipeFiles {
    /** The folder under the cache dir, the `FileProvider`'s only path (`res/xml` of `app`). */
    const val FOLDER = "shared"

    /** Files in [FOLDER] older than this are deleted at app start. */
    const val MAX_AGE_MS = 24 * 60 * 60 * 1_000L

    /**
     * Writes [bytes] to [FOLDER] under [teaName]'s sanitised file name ([RecipeFile.fileName]), replacing a file of
     * the same name. Blocking I/O: call it off the main thread.
     */
    fun write(cacheDir: File, teaName: String, bytes: ByteArray): File {
        val folder = File(cacheDir, FOLDER).apply { mkdirs() }
        val target = File(folder, RecipeFile.fileName(teaName))
        val partial = File.createTempFile("partial", ".tmp", folder)
        try {
            partial.writeBytes(bytes)
            Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            partial.delete()
        }
        return target
    }

    /** Deletes the files in [FOLDER] last changed more than [MAX_AGE_MS] before [nowMs]. Blocking I/O. */
    fun deleteStale(cacheDir: File, nowMs: Long) {
        File(cacheDir, FOLDER).listFiles()
            ?.filter { it.isFile && it.lastModified() < nowMs - MAX_AGE_MS }
            ?.forEach { it.delete() }
    }
}

/** The share sheet's intent of *Rezept teilen* (PRF-08 AC3, SCR-12). */
object RecipeShareIntent {
    /**
     * `ACTION_SEND` with [text] and the recipe file at [uri] (type [RecipeFile.MIME_TYPE]), granting read access to
     * that one URI, through `Intent.createChooser` with [title].
     */
    fun chooser(uri: Uri, text: String, title: String): Intent {
        val send = Intent(Intent.ACTION_SEND)
            .setType(RecipeFile.MIME_TYPE)
            .putExtra(Intent.EXTRA_TEXT, text)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // The grant reaches the chosen app through the clip (the chooser carries it on).
        send.clipData = ClipData.newRawUri(null, uri)
        return Intent.createChooser(send, title)
    }
}

/** Turns a [SharedRecipe] into the share sheet's intent: the text, the file in `cache/shared/`, its URI. */
fun interface RecipeSharer {
    /** Throws an [java.io.IOException] when the file can't be written. */
    suspend fun chooser(recipe: SharedRecipe): Intent
}

/**
 * The app's [RecipeSharer]: the share text from `strings.xml` (D-130 item 20), the file through the app's
 * `FileProvider` (authority `<applicationId>.files`, 06 §5.7), written off the main thread.
 */
class AndroidRecipeSharer(private val context: Context, private val now: () -> OffsetDateTime = OffsetDateTime::now) :
    RecipeSharer {
    override suspend fun chooser(recipe: SharedRecipe): Intent = withContext(Dispatchers.IO) {
        val text = RecipeShareText.build(recipe, ResourceRecipeShareLabels(context.resources))
        val file = SharedRecipeFiles.write(context.cacheDir, recipe.tea.name, RecipeFile.write(recipe, now()))
        val uri = FileProvider.getUriForFile(context, authority(context), file)
        RecipeShareIntent.chooser(uri, text, context.getString(R.string.recipe_editor_share))
    }

    companion object {
        /** The `FileProvider`'s authority, `com.umamit.app.files` in a release build. */
        fun authority(context: Context): String = "${context.packageName}.files"
    }
}

/**
 * Opens the share sheet for [event]'s recipe; false (logged) when the file can't be written (a full cache) or no app
 * takes the share, and the editor then shows „Das Rezept konnte nicht geteilt werden." (PRF-08).
 */
internal suspend fun Context.share(sharer: RecipeSharer, event: RecipeEditorEvent.Share): Boolean {
    val chooser = try {
        sharer.chooser(event.recipe)
    } catch (e: IOException) {
        Log.w(LOG_TAG, "recipe file not written", e)
        return false
    }
    return try {
        startActivity(chooser)
        true
    } catch (e: ActivityNotFoundException) {
        Log.w(LOG_TAG, "no share sheet", e)
        false
    }
}

private const val LOG_TAG = "UmamiT-RecipeShare"

/** The app's [RecipeSharer] for this composition. */
@Composable
fun rememberRecipeSharer(): RecipeSharer {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidRecipeSharer(context) }
}
