package com.umamit.core.designsystem.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.pluralStringResource
import com.umamit.core.designsystem.R
import java.util.Locale

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3_600

/** A duration as the timer shows it (04 §5): „m:ss", and „h:mm:ss" from one hour on. */
fun clockText(totalSeconds: Int): String {
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = totalSeconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return if (hours > 0) {
        "%d:%02d:%02d".format(Locale.GERMAN, hours, minutes, seconds)
    } else {
        "%d:%02d".format(Locale.GERMAN, minutes, seconds)
    }
}

/** A rating with one decimal and a German comma: „4,5". */
fun ratingText(rating: Float): String = "%.1f".format(Locale.GERMAN, rating)

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
