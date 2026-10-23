package com.umamit.core.designsystem.motion

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Whether motion is reduced. `UmamiTheme` provides it from the system setting
 * ([rememberSystemReduceMotion]); tests force it through `UmamiTheme(reduceMotion = …)`.
 */
val LocalReduceMotion = compositionLocalOf { false }

/**
 * Reads the system animator duration scale and follows changes while the app
 * runs, so switching „Animationen entfernen" applies without a restart.
 */
@Composable
fun rememberSystemReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    var reduceMotion by remember(resolver) { mutableStateOf(resolver.readReduceMotion()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduceMotion = resolver.readReduceMotion()
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        reduceMotion = resolver.readReduceMotion()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduceMotion
}

private fun ContentResolver.readReduceMotion(): Boolean =
    isReduceMotion(Settings.Global.getFloat(this, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
