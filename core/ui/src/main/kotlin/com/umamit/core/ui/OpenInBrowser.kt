package com.umamit.core.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.net.toUri

/** Opens [url] in the browser; without one, says so with the caller's [noBrowserMessage] (D-123). */
fun Context.openInBrowser(url: String, @StringRes noBrowserMessage: Int) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, noBrowserMessage, Toast.LENGTH_SHORT).show()
    }
}
