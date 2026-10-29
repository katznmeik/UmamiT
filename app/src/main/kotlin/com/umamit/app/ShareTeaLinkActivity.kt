package com.umamit.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.umamit.core.data.catalog.TeaLinkLoader

/**
 * The share target (CAT-17 AC1, 06 §5.7, D-094): UmamiT in the browser's *Teilen* sheet. Its
 * manifest entry is exported for `ACTION_SEND` with `text/plain` only, and it needs no
 * permission. It shows nothing: it hands the shared text to [MainActivity] in the app's own task
 * (so the app never opens a second time inside the browser's task) and finishes.
 *
 * The text is untrusted input. An explicit intent can reach an exported activity with any action
 * or type, so both are checked again here; only the text itself is read ([Intent.EXTRA_TEXT],
 * never a stream or another extra), cut to one character over what the app reads at all, so an
 * overlong text is refused there. Whether it holds a tea link, and anything sent to the shop,
 * is decided in the catalog (`TeaLinkLoader`).
 */
class ShareTeaLinkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A recreation has handed the text over already.
        if (savedInstanceState == null) sharedText(intent)?.let { startActivity(MainActivity.openTeaLink(this, it)) }
        finish()
    }

    companion object {
        private const val TEXT_PLAIN = "text/plain"

        /** The text of a `text/plain` share, at most one character over what the app reads; null for anything else. */
        fun sharedText(intent: Intent?): String? {
            val plainTextShare = intent?.action == Intent.ACTION_SEND &&
                intent.type?.substringBefore(';')?.trim().equals(TEXT_PLAIN, ignoreCase = true)
            val text = if (plainTextShare) {
                try {
                    intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
                } catch (
                    // A malformed parcel from another app (BadParcelableException, …) is no share.
                    @Suppress("TooGenericExceptionCaught") _: RuntimeException,
                ) {
                    null
                }
            } else {
                null
            }
            return text?.take(TeaLinkLoader.MAX_TEXT_LENGTH + 1)?.toString()?.takeIf { it.isNotBlank() }
        }

        /** A share of [text] as the browser sends it, for tests and previews. */
        fun share(context: Context, text: String): Intent = Intent(context, ShareTeaLinkActivity::class.java)
            .setAction(Intent.ACTION_SEND)
            .setType(TEXT_PLAIN)
            .putExtra(Intent.EXTRA_TEXT, text)
    }
}
