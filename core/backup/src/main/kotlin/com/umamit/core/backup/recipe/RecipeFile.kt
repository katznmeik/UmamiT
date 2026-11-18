package com.umamit.core.backup.recipe

import com.umamit.core.backup.R
import java.io.InputStream
import java.time.OffsetDateTime

/**
 * The recipe file `umamit-recipe` v1 (PRF-08, 05 §8a, D-130 Q2, Q3, item 17): one small UTF-8 JSON object, written
 * by [write] and read by [read]. A received file is untrusted input, whichever way it came (D-130 item 18): it goes
 * through [read] only, which writes nothing.
 */
object RecipeFile {
    /** The format id in `format`. */
    const val FORMAT = "umamit-recipe"

    /** The newest `formatVersion` this app reads and the one it writes. */
    const val FORMAT_VERSION = 1

    /** The file's MIME type, also its `FileProvider` share type (PRF-08 AC3). */
    const val MIME_TYPE = "application/vnd.umamit.recipe+json"

    /** The file name's extension, without the dot. */
    const val EXTENSION = "umamit-rezept"

    /** The largest file read (64 KB); a larger one is refused before it is parsed. */
    const val MAX_BYTES = 64 * 1024

    /** The longest tea name in [fileName], before the extension. */
    const val MAX_FILE_NAME_LENGTH = 60

    private const val FALLBACK_FILE_NAME = "Rezept"
    private val FILE_NAME_FORBIDDEN = Regex("""[/\\:*?"<>|\p{Cntrl}]""")

    /** `<Tee>.umamit-rezept` (05 §8a): [teaName] without `/\:*?"<>|`, at most 60 characters. */
    fun fileName(teaName: String): String {
        val cleaned = teaName.replace(FILE_NAME_FORBIDDEN, "").trim()
        val cut = if (cleaned.length > MAX_FILE_NAME_LENGTH) {
            // Never cut a surrogate pair in half.
            val end = if (cleaned[MAX_FILE_NAME_LENGTH - 1].isHighSurrogate()) {
                MAX_FILE_NAME_LENGTH - 1
            } else {
                MAX_FILE_NAME_LENGTH
            }
            cleaned.substring(0, end).trimEnd()
        } else {
            cleaned
        }
        return "${cut.ifEmpty { FALLBACK_FILE_NAME }}.$EXTENSION"
    }

    /** The file's bytes for [recipe], stamped [createdAt] (to the second). */
    fun write(recipe: SharedRecipe, createdAt: OffsetDateTime): ByteArray = RecipeFileWriter.write(recipe, createdAt)

    /**
     * [bytes] as a recipe file: [RecipeFileResult.Valid] when every check of 05 §8a passes, else the one
     * [RecipeFileResult.Refused]. More than [MAX_BYTES] is refused unread.
     */
    fun read(bytes: ByteArray): RecipeFileResult = RecipeFileReader.read(bytes)

    /**
     * Reads at most [MAX_BYTES] + 1 bytes of [input], so a larger file is refused without reading it to the end.
     * The caller opens and closes [input]; an [java.io.IOException] from it is the caller's.
     */
    fun read(input: InputStream): RecipeFileResult = read(input.readNBytes(MAX_BYTES + 1))
}

/** What [RecipeFile.read] made of a file. */
sealed interface RecipeFileResult {
    /** A recipe file that passed every check; [createdAt] is when it was shared. */
    data class Valid(val recipe: SharedRecipe, val createdAt: OffsetDateTime) : RecipeFileResult

    /** Not taken: the UI shows [refusal]'s text ([RecipeRefusal.messageRes]) and nothing changes. */
    data class Refused(val refusal: RecipeRefusal) : RecipeFileResult
}

/** Why a file is refused (05 §8a, PRF-08 AC5); [messageRes] is its German text in this module's `strings.xml`. */
enum class RecipeRefusal(val messageRes: Int) {
    /** „Das ist keine UmamiT-Rezeptdatei.": another format id, no format id, or larger than 64 KB. */
    NOT_A_RECIPE_FILE(R.string.recipe_file_refused_not_a_recipe),

    /** A `formatVersion` newer than [RecipeFile.FORMAT_VERSION] (D-130 Q3, the text of 05 §8 „Versions"). */
    NEWER_VERSION(R.string.recipe_file_refused_newer_version),

    /** „Die Rezeptdatei ist beschädigt.": broken JSON or UTF-8, a field out of range, an unknown name or field. */
    DAMAGED(R.string.recipe_file_refused_damaged),
}
