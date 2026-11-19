package com.umamit.app

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import com.umamit.core.backup.recipe.RecipeFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/** A received recipe file (PRF-08): its [bytes] as copied, at most 64 KB + 1 byte, and a [token] per arrival. */
class ReceivedRecipe(val token: Long, val bytes: ByteArray)

/**
 * Where a handed-over recipe file is read (PRF-08, D-133 Q4): the bytes of [uri], or null when it is not a recipe file
 * ([type] null: the provider's type decides). Blocking I/O, called off the main thread.
 */
fun interface RecipeFileSource {
    fun read(uri: Uri, type: String?): ByteArray?

    companion object {
        /**
         * The app's: the URI's provider through the grant [ReceiveRecipeActivity] passed on, copied as
         * [ReceiveRecipeActivity.copy] copies. A URI of the app's own providers is read as nothing („beschädigt"):
         * MainActivity is exported, so another app could point it at them.
         */
        fun of(context: Context): RecipeFileSource {
            val app = context.applicationContext
            val resolver = app.contentResolver
            return RecipeFileSource { uri, type ->
                val own = uri.scheme == ContentResolver.SCHEME_CONTENT &&
                    uri.authority?.let { app.packageManager.resolveContentProvider(it, 0)?.packageName } ==
                    app.packageName
                when {
                    own -> ByteArray(0)
                    !ReceiveRecipeActivity.isRecipeType(type ?: typeOf(resolver, uri)) -> null
                    else -> ReceiveRecipeActivity.copy(resolver, uri)
                }
            }
        }

        private fun typeOf(resolver: ContentResolver, uri: Uri): String? = try {
            resolver.getType(uri)
        } catch (
            // Another app's provider may throw anything: no type, so no recipe file.
            @Suppress("TooGenericExceptionCaught") _: RuntimeException,
        ) {
            null
        }
    }
}

/**
 * Recipe files received by the app (PRF-08's three ways), as [MainActivity] keeps them: the latest file not shown yet
 * ([pending]), with a new token each, and the one SCR-32 shows ([shown]), until it closes. A newer file replaces an
 * older one. Nothing here reads or writes the collection: SCR-32's reader decides.
 *
 * MainActivity keeps it in a view model, so it outlives a rotation, and the copies run in [scope] (the view model's),
 * not in the composition: a copy that a rotation interrupts still arrives (D-133 item 13). [save] and [restore] carry
 * it over a process death.
 */
class ReceivedRecipeFiles(
    private val newToken: () -> Long = SystemClock::elapsedRealtimeNanos,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob()),
    private val io: CoroutineContext = Dispatchers.IO,
) {
    private val waiting = MutableStateFlow<ReceivedRecipe?>(null)

    @Volatile
    private var open: ReceivedRecipe? = null

    /** The file the shell has not shown yet; null for none. */
    val pending: StateFlow<ReceivedRecipe?> = waiting.asStateFlow()

    /**
     * Takes the file [intent] carries ([MainActivity.openRecipe]) and copies it from [source] off the main thread;
     * returns whether it carried one. A task reopened from the recent apps brings its first intent again: that file
     * was handled then.
     */
    fun offer(intent: Intent?, source: RecipeFileSource): Boolean {
        val fresh = intent?.action == ACTION_OPEN_RECIPE &&
            intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0
        // MainActivity is exported: the URI and its type are untrusted here too, and the bytes capped again.
        val file = if (fresh) fileOf(intent) else null
        file?.let { scope.launch(io) { source.read(it.uri, it.type)?.let(::receive) } }
        return file != null
    }

    /**
     * A file picked with *Rezept importieren* (SCR-08): [read] copies it as [ReceiveRecipeActivity.copy] copies, in
     * [scope], so a rotation does not lose it.
     */
    fun receiveCopy(read: () -> ByteArray): Job = scope.launch(io) { receive(read()) }

    /** A file's [bytes], capped at 64 KB + 1 byte, as the pending one. */
    fun receive(bytes: ByteArray) {
        waiting.value = ReceivedRecipe(newToken(), bytes.copyOf(minOf(bytes.size, RecipeFile.MAX_BYTES + 1)))
    }

    /** The shell has shown the file [token] on SCR-32. */
    fun shown(token: Long) {
        val file = waiting.value?.takeIf { it.token == token } ?: return
        if (waiting.compareAndSet(file, null)) open = file
    }

    /** The bytes of the file [token] SCR-32 shows; null once it closed (or for another token). */
    fun bytes(token: Long): ByteArray? = (
        open?.takeIf { it.token == token }
            ?: waiting.value?.takeIf { it.token == token }
        )
        ?.bytes

    /** SCR-32 of [token] closed: its bytes go. */
    fun closed(token: Long) {
        if (open?.token == token) open = null
    }

    fun save(outState: Bundle) {
        waiting.value?.let {
            outState.putLong(KEY_PENDING_TOKEN, it.token)
            outState.putByteArray(KEY_PENDING_BYTES, it.bytes)
        }
        open?.let {
            outState.putLong(KEY_OPEN_TOKEN, it.token)
            outState.putByteArray(KEY_OPEN_BYTES, it.bytes)
        }
    }

    /**
     * The files of [savedState], after a process death. An instance that kept its files (a recreation: the view model
     * survived) keeps them, so a copy that arrived after the save is not replaced by the older state.
     */
    fun restore(savedState: Bundle?) {
        savedState ?: return
        if (waiting.value != null || open != null) return
        savedState.getByteArray(KEY_PENDING_BYTES)?.let {
            waiting.value = ReceivedRecipe(savedState.getLong(KEY_PENDING_TOKEN), it)
        }
        savedState.getByteArray(KEY_OPEN_BYTES)?.let { open = ReceivedRecipe(savedState.getLong(KEY_OPEN_TOKEN), it) }
    }

    private fun fileOf(intent: Intent): ReceivedFile? = try {
        intent.data?.let { ReceivedFile(it, intent.getStringExtra(EXTRA_TYPE)) }
    } catch (
        // A malformed parcel (BadParcelableException, …) carries no file.
        @Suppress("TooGenericExceptionCaught") _: RuntimeException,
    ) {
        null
    }

    companion object {
        /** [MainActivity]'s own action for a file handed over by [ReceiveRecipeActivity]. */
        const val ACTION_OPEN_RECIPE = "com.umamit.app.action.OPEN_RECIPE"

        /** The type the sender stated, if any ([ReceivedFile.type]); the file's URI is the intent's data. */
        const val EXTRA_TYPE = "com.umamit.app.extra.RECIPE_TYPE"

        private const val KEY_PENDING_TOKEN = "com.umamit.app.PENDING_RECIPE_TOKEN"
        private const val KEY_PENDING_BYTES = "com.umamit.app.PENDING_RECIPE_BYTES"
        private const val KEY_OPEN_TOKEN = "com.umamit.app.OPEN_RECIPE_TOKEN"
        private const val KEY_OPEN_BYTES = "com.umamit.app.OPEN_RECIPE_BYTES"
    }
}
