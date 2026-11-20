package com.umamit.app

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.umamit.core.backup.recipe.RecipeFile
import java.io.IOException

/** A recipe file a `VIEW` or `SEND` carries: its [uri], and the [type] the intent states (null: the provider tells). */
class ReceivedFile(val uri: Uri, val type: String?)

/**
 * A recipe file opened or shared to UmamiT (PRF-08 ways (a) and (b), 06 §5.7, D-130 Q1, item 18). Its manifest entry
 * is exported for `ACTION_VIEW` and `ACTION_SEND` with `application/vnd.umamit.recipe+json` and `application/json`
 * only, and it needs no permission. Like [ShareTeaLinkActivity] it shows nothing: it hands the file on to
 * [MainActivity] in the app's own task, which shows SCR-32, then finishes. Nothing is written.
 *
 * The file is untrusted input. An explicit intent reaches an exported activity with any action or type, so both are
 * checked again (`getData()` for `VIEW`, `EXTRA_STREAM` for `SEND`). It reads nothing itself and makes no call into the
 * sender's provider on the main thread (D-133 Q4): it passes its read grant on with the URI
 * (`FLAG_GRANT_READ_URI_PERMISSION`), and [MainActivity] copies the file off the main thread
 * ([ReceivedRecipeFiles.offer]), so a slow source neither freezes the app nor reads as damaged. A `VIEW` without a
 * type is checked there against the provider's type, as the system matched it.
 */
class ReceiveRecipeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A recreation has handed the file over already.
        if (savedInstanceState == null) {
            receivedFile(intent)?.let { startActivity(MainActivity.openRecipe(this, it)) }
        }
        finish()
    }

    companion object {
        /** The MIME types the filters list (D-130 item 18); nothing for `application/octet-stream`. */
        val MIME_TYPES = setOf(RecipeFile.MIME_TYPE, "application/json")

        /** Whether [type] (a stated or a provider's type) is one of [MIME_TYPES], parameters ignored. */
        fun isRecipeType(type: String?): Boolean = type?.substringBefore(';')?.trim()?.lowercase() in MIME_TYPES

        /**
         * The file of a `VIEW` or `SEND` with one of [MIME_TYPES]; null for any other intent. A `VIEW` without a type
         * is handed on with none, for the copy to check the provider's. The URI may still be a `file://` one, which
         * [copy] refuses.
         */
        fun receivedFile(intent: Intent?): ReceivedFile? = try {
            val uri = when (intent?.action) {
                Intent.ACTION_VIEW -> intent.data
                Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else -> null
            }
            val type = intent?.type
            val accepted = if (type == null) intent?.action == Intent.ACTION_VIEW else isRecipeType(type)
            uri?.takeIf { accepted }?.let { ReceivedFile(it, type) }
        } catch (
            // A malformed parcel from another app (BadParcelableException, …) carries no file.
            @Suppress("TooGenericExceptionCaught") _: RuntimeException,
        ) {
            null
        }

        /**
         * At most [RecipeFile.MAX_BYTES] + 1 bytes of the `content://` [uri]; empty for another scheme or a file that
         * can't be read. Blocking I/O: call it off the main thread.
         */
        fun copy(resolver: ContentResolver, uri: Uri): ByteArray {
            if (uri.scheme != ContentResolver.SCHEME_CONTENT) return ByteArray(0)
            return try {
                resolver.openInputStream(uri)?.use { it.readNBytes(RecipeFile.MAX_BYTES + 1) } ?: ByteArray(0)
            } catch (_: IOException) {
                ByteArray(0)
            } catch (_: SecurityException) {
                ByteArray(0)
            } catch (
                // Another app's provider may throw anything (IllegalArgumentException, …): no file.
                @Suppress("TooGenericExceptionCaught") _: RuntimeException,
            ) {
                ByteArray(0)
            }
        }

        /** A `VIEW` of [uri] as a file manager sends it, for tests; [type] null sends none. */
        fun view(context: Context, uri: Uri, type: String? = RecipeFile.MIME_TYPE): Intent =
            Intent(context, ReceiveRecipeActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setDataAndType(uri, type)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        /** A `SEND` of [uri] as a messenger sends it, for tests. */
        fun send(context: Context, uri: Uri, type: String = RecipeFile.MIME_TYPE): Intent =
            Intent(context, ReceiveRecipeActivity::class.java)
                .setAction(Intent.ACTION_SEND)
                .setType(type)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
