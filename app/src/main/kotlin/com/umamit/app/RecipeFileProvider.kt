package com.umamit.app

import android.net.Uri
import androidx.core.content.FileProvider
import com.umamit.core.backup.recipe.RecipeFile

/**
 * The `FileProvider` of *Rezept teilen* (PRF-08 AC3, 06 §5.7): not exported, `grantUriPermissions`, only
 * `cache/shared/` (`res/xml/recipe_share_paths.xml`). It names a recipe file's (`.umamit-rezept.json`) type
 * [RecipeFile.SHARE_MIME_TYPE], `application/json` (D-134 item 5); the stock provider would say the same through the
 * system's MIME map, this keeps it independent of the device's map.
 */
class RecipeFileProvider : FileProvider() {
    override fun getType(uri: Uri): String? = if (isRecipeFile(uri)) RecipeFile.SHARE_MIME_TYPE else super.getType(uri)

    override fun getTypeAnonymous(uri: Uri): String? =
        if (isRecipeFile(uri)) RecipeFile.SHARE_MIME_TYPE else super.getTypeAnonymous(uri)

    private fun isRecipeFile(uri: Uri): Boolean = uri.lastPathSegment?.endsWith(".${RecipeFile.EXTENSION}") == true
}
