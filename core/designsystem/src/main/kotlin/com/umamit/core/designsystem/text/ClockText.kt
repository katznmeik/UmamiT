package com.umamit.core.designsystem.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.pluralStringResource
import com.umamit.core.common.AppLanguage
import com.umamit.core.designsystem.R
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3_600

/**
 * A duration as the timer shows it (04 §5): „m:ss", and „h:mm:ss" from one hour on. Digits and colons only, the same in
 * both app languages (GEN-10), so the locale is fixed to [Locale.ROOT].
 */
fun clockText(totalSeconds: Int): String {
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = totalSeconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return if (hours > 0) {
        "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, seconds)
    } else {
        "%d:%02d".format(Locale.ROOT, minutes, seconds)
    }
}

/** A rating with one decimal in the app's language (GEN-10): „4,5" (English „4.5"). */
fun ratingText(rating: Float): String = "%.1f".format(AppLanguage.current().locale, rating)

/**
 * A tea's average rating to one decimal, rounded half up from the [average] itself and not from a float of it:
 * 4,35 is „4,4", 4,45 „4,5", 3,25 „3,3" (TEA-08, TEA-11); in English with a decimal point (GEN-10).
 */
fun averageRatingText(average: Double): String =
    "%.1f".format(AppLanguage.current().locale, BigDecimal.valueOf(average).setScale(1, RoundingMode.HALF_UP))

/** A duration for TalkBack: „1 Minute 30 Sekunden" instead of „1:30". */
@Composable
@ReadOnlyComposable
fun spokenDuration(totalSeconds: Int): String {
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = totalSeconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    val parts = buildList {
        if (hours > 0) add(pluralStringResource(R.plurals.ds_hours, hours, hours))
        if (minutes > 0) add(pluralStringResource(R.plurals.ds_minutes, minutes, minutes))
        if (seconds > 0 || isEmpty()) add(pluralStringResource(R.plurals.ds_seconds, seconds, seconds))
    }
    return parts.joinToString(" ")
}
