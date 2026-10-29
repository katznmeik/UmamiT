package com.umamit.app

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import com.umamit.core.data.catalog.TeaLinkLoader
import com.umamit.feature.catalog.TeaLinkShare
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Links shared to the app (CAT-17 AC1), as [MainActivity] keeps them: the latest share not yet
 * taken by the catalog, with a new token each, so it is loaded once. A share pending when the
 * activity is recreated (a rotation, dark mode) is kept with its state. A newer share replaces
 * an older one not taken yet.
 */
class TeaLinkShares(private val newToken: () -> Long = SystemClock::elapsedRealtimeNanos) {
    private val pending = MutableStateFlow<TeaLinkShare?>(null)

    /** The share the catalog has not taken yet; null for none. */
    val share: StateFlow<TeaLinkShare?> = pending.asStateFlow()

    /**
     * Takes the share [intent] carries ([MainActivity.openTeaLink]); returns whether it carried
     * one. A task reopened from the recent apps brings its first intent again: that share was
     * handled then, and fetches nothing now.
     */
    fun offer(intent: Intent?): Boolean {
        val fresh = intent?.action == ACTION_OPEN_TEA_LINK &&
            intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0
        // MainActivity is exported: the text is untrusted here too, and is only ever read as text.
        val text = if (fresh) textOf(intent) else null
        if (text != null) pending.value = TeaLinkShare(newToken(), text)
        return text != null
    }

    private fun textOf(intent: Intent): String? = try {
        intent.getStringExtra(EXTRA_TEXT)
    } catch (
        // A malformed parcel (BadParcelableException, …) carries no share.
        @Suppress("TooGenericExceptionCaught") _: RuntimeException,
    ) {
        null
    }?.take(TeaLinkLoader.MAX_TEXT_LENGTH + 1)?.takeIf { it.isNotBlank() }

    /** The catalog has started to load the share [token]. */
    fun taken(token: Long) {
        if (pending.value?.token == token) pending.value = null
    }

    fun save(outState: Bundle) {
        pending.value?.let {
            outState.putLong(KEY_TOKEN, it.token)
            outState.putString(KEY_TEXT, it.text)
        }
    }

    fun restore(savedState: Bundle?) {
        val text = savedState?.getString(KEY_TEXT) ?: return
        pending.value = TeaLinkShare(savedState.getLong(KEY_TOKEN), text)
    }

    companion object {
        /** [MainActivity]'s own action for a share handed over by [ShareTeaLinkActivity]. */
        const val ACTION_OPEN_TEA_LINK = "com.umamit.app.action.OPEN_TEA_LINK"

        /** The shared text, as [ShareTeaLinkActivity] read it. */
        const val EXTRA_TEXT = "com.umamit.app.extra.TEA_LINK_TEXT"

        private const val KEY_TOKEN = "com.umamit.app.PENDING_TEA_LINK_TOKEN"
        private const val KEY_TEXT = "com.umamit.app.PENDING_TEA_LINK_TEXT"
    }
}
