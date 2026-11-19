package com.umamit.app

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.umamit.core.backup.recipe.RecipeFile
import java.io.IOException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * A recipe file opened or shared to UmamiT (PRF-08 ways (a) and (b), 06 §5.7, D-130 Q1, item 18). Its manifest entry
 * is exported for `ACTION_VIEW` and `ACTION_SEND` with `application/vnd.umamit.recipe+json` and `application/json`
 * only, and it needs no permission. Like [ShareTeaLinkActivity] it shows nothing: it copies the file and hands the
 * bytes to [MainActivity] in the app's own task, which shows SCR-32, then finishes. Nothing is written.
 *
 * The file is untrusted input. An explicit intent reaches an exported activity with any action or type, so both are
 * checked again; only a `content://` URI is read (`getData()` for `VIEW`, `EXTRA_STREAM` for `SEND`), at most
 * 64 KB + 1 byte ([RecipeFile.MAX_BYTES]), so a larger file is refused by the reader. A `file://` URI, or one that
 * can't be read, is handed on empty: the reader refuses it („Die Rezeptdatei ist beschädigt."). The copy runs off the
 * main thread; a `NoDisplay` activity must finish before it resumes, and the URI grant lasts only while it runs, so
 * `onCreate` waits for it, at most [READ_TIMEOUT_MS].
 */
class ReceiveRecipeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A recreation has handed the file over already.
        if (savedInstanceState == null) {
            receivedUri(intent) { contentResolver.getType(it) }?.let { uri ->
                startActivity(MainActivity.openRecipe(this, copyOffMainThread(contentResolver, uri)))
            }
        }
        finish()
    }

    companion object {
        /** The MIME types the filters list (D-130 item 18); nothing for `application/octet-stream`. */
        val MIME_TYPES = setOf(RecipeFile.MIME_TYPE, "application/json")

        /** The longest wait for the copy: a provider that hangs never keeps the activity. */
        const val READ_TIMEOUT_MS = 2_000L

        /**
         * The file's URI of a `VIEW` or `SEND` with one of [MIME_TYPES]; null for any other intent. A `VIEW` without
         * a type takes the provider's ([resolveType]), as the system matched it. The URI may still be a `file://` one,
         * which [copy] refuses.
         */
        fun receivedUri(intent: Intent?, resolveType: (Uri) -> String? = { null }): Uri? = try {
            val uri = when (intent?.action) {
                Intent.ACTION_VIEW -> intent.data
                Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else -> null
            }
            val type = (intent?.type ?: uri?.takeIf { intent?.action == Intent.ACTION_VIEW }?.let(resolveType))
                ?.substringBefore(';')?.trim()?.lowercase()
            uri?.takeIf { type in MIME_TYPES }
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

        /** [copy] on a worker thread, waited for at most [READ_TIMEOUT_MS]; empty when it takes longer. */
        fun copyOffMainThread(resolver: ContentResolver, uri: Uri): ByteArray {
            val executor = Executors.newSingleThreadExecutor()
            return try {
                executor.submit<ByteArray> { copy(resolver, uri) }.get(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
                ByteArray(0)
            } catch (_: ExecutionException) {
                ByteArray(0)
            } finally {
                executor.shutdownNow()
            }
        }

        /** A `VIEW` of [uri] as a file manager sends it, for tests. */
        fun view(context: Context, uri: Uri, type: String = RecipeFile.MIME_TYPE): Intent =
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
