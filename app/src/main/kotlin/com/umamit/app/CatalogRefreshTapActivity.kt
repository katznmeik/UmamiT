package com.umamit.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.umamit.core.data.catalog.CatalogRefreshIntents

/**
 * The catalog refresh notifications' tap (SCR-22, D-097; #113, #121, 06 §5.7): the trampoline
 * between the notification and [MainActivity]. Its manifest entry is not exported and has no
 * intent filter, so only the app's own notification can start it (`CatalogRefreshIntents.TAP_ACTIVITY`
 * names it). It shows nothing: it lets the tap through [CatalogRefreshTapGate] in this process,
 * hands it to MainActivity in the app's own task, as the timer's notifications open it
 * (`NEW_TASK | SINGLE_TOP`), and finishes. A tap after the app's process was killed starts the new
 * process here first, so MainActivity takes it there too.
 */
class CatalogRefreshTapActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A recreation has handed the tap over already.
        if (savedInstanceState == null) handOver(this, intent)?.let(::startActivity)
        finish()
    }

    private companion object {
        /** MainActivity's intent for the notification's tap [intent], let through the gate; null for anything else. */
        fun handOver(context: Context, intent: Intent?): Intent? {
            if (!CatalogRefreshIntents.isOpenRefresh(intent)) return null
            return Intent(context, MainActivity::class.java)
                .setAction(CatalogRefreshIntents.ACTION_OPEN_REFRESH)
                .putExtra(CatalogRefreshRequests.EXTRA_TICKET, CatalogRefreshTapGate.pass())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
    }
}
