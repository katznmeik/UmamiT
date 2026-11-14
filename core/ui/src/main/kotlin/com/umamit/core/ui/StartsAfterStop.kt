package com.umamit.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * How often the lifecycle started again after a stop (unlock, the screen turned on, back from recents): [onStart]
 * runs in that `ON_START`, which comes before the first frame, so a clock read there makes that frame fresh (A-01,
 * D-117 Q19). The start this is composed in does not count, so a first composition is unchanged.
 */
@Composable
fun rememberStartsAfterStop(onStart: () -> Unit): Int {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnStart by rememberUpdatedState(onStart)
    var starts by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycle) {
        var stopped = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> stopped = true

                Lifecycle.Event.ON_START -> if (stopped) {
                    stopped = false
                    currentOnStart()
                    starts++
                }

                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return starts
}
