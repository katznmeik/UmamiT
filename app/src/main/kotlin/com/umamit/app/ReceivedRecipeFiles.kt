package com.umamit.app

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import com.umamit.core.backup.recipe.RecipeFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A received recipe file (PRF-08): its [bytes] as copied, at most 64 KB + 1 byte, and a [token] per arrival. */
class ReceivedRecipe(val token: Long, val bytes: ByteArray)

/**
 * Recipe files received by the app (PRF-08's three ways), as [MainActivity] keeps them: the latest file not shown yet
 * ([pending]), with a new token each, and the one SCR-32 shows ([shown]), until it closes. Both survive the
 * activity's recreation. A newer file replaces an older one. Nothing here reads or writes the collection: SCR-32's
 * reader decides.
 */
class ReceivedRecipeFiles(private val newToken: () -> Long = SystemClock::elapsedRealtimeNanos) {
    private val waiting = MutableStateFlow<ReceivedRecipe?>(null)
    private var open: ReceivedRecipe? = null

    /** The file the shell has not shown yet; null for none. */
    val pending: StateFlow<ReceivedRecipe?> = waiting.asStateFlow()

    /**
     * Takes the file [intent] carries ([MainActivity.openRecipe]); returns whether it carried one. A task reopened
     * from the recent apps brings its first intent again: that file was handled then.
     */
    fun offer(intent: Intent?): Boolean {
        val fresh = intent?.action == ACTION_OPEN_RECIPE &&
            intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0
        // MainActivity is exported: the bytes are untrusted here too, and capped again.
        val bytes = if (fresh) bytesOf(intent) else null
        if (bytes != null) receive(bytes)
        return bytes != null
    }

    /** A file picked with *Rezept importieren* (SCR-08), copied as [ReceiveRecipeActivity.copy] copies. */
    fun receive(bytes: ByteArray) {
        waiting.value = ReceivedRecipe(newToken(), bytes.copyOf(minOf(bytes.size, RecipeFile.MAX_BYTES + 1)))
    }

    /** The shell has shown the file [token] on SCR-32. */
    fun shown(token: Long) {
        val file = waiting.value?.takeIf { it.token == token } ?: return
        open = file
        waiting.value = null
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

    fun restore(savedState: Bundle?) {
        savedState ?: return
        savedState.getByteArray(KEY_PENDING_BYTES)?.let {
            waiting.value = ReceivedRecipe(savedState.getLong(KEY_PENDING_TOKEN), it)
        }
        savedState.getByteArray(KEY_OPEN_BYTES)?.let { open = ReceivedRecipe(savedState.getLong(KEY_OPEN_TOKEN), it) }
    }

    private fun bytesOf(intent: Intent): ByteArray? = try {
        intent.getByteArrayExtra(EXTRA_BYTES)
    } catch (
        // A malformed parcel (BadParcelableException, …) carries no file.
        @Suppress("TooGenericExceptionCaught") _: RuntimeException,
    ) {
        null
    }

    companion object {
        /** [MainActivity]'s own action for a file handed over by [ReceiveRecipeActivity]. */
        const val ACTION_OPEN_RECIPE = "com.umamit.app.action.OPEN_RECIPE"

        /** The file's bytes, as [ReceiveRecipeActivity] copied them. */
        const val EXTRA_BYTES = "com.umamit.app.extra.RECIPE_BYTES"

        private const val KEY_PENDING_TOKEN = "com.umamit.app.PENDING_RECIPE_TOKEN"
        private const val KEY_PENDING_BYTES = "com.umamit.app.PENDING_RECIPE_BYTES"
        private const val KEY_OPEN_TOKEN = "com.umamit.app.OPEN_RECIPE_TOKEN"
        private const val KEY_OPEN_BYTES = "com.umamit.app.OPEN_RECIPE_BYTES"
    }
}
