package com.umamit.app

import android.net.Uri
import androidx.core.content.FileProvider
import com.umamit.core.backup.recipe.RecipeFile

/**
 * The `FileProvider` of *Rezept teilen* (PRF-08 AC3, 06 §5.7): not exported, `grantUriPermissions`, only
 * `cache/shared/` (`res/xml/recipe_share_paths.xml`). It names a recipe file's type
 * `application/vnd.umamit.recipe+json`, which the stock provider can't tell from the unknown `.umamit-rezept`
 * extension (it would say `application/octet-stream`, D-130 item 18's risk).
 */
class RecipeFileProvider : FileProvider() {
    override fun getType(uri: Uri): String? = if (isRecipeFile(uri)) RecipeFile.MIME_TYPE else super.getType(uri)

    override fun getTypeAnonymous(uri: Uri): String? =
        if (isRecipeFile(uri)) RecipeFile.MIME_TYPE else super.getTypeAnonymous(uri)

    private fun isRecipeFile(uri: Uri): Boolean = uri.lastPathSegment?.endsWith(".${RecipeFile.EXTENSION}") == true
}
