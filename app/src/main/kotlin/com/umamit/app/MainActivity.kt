package com.umamit.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.umamit.app.timer.TimerActiveBrewSource
import com.umamit.app.timer.lockScreenFlags
import com.umamit.core.common.MonotonicClock
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.feature.brew.timer.BrewTimer
import com.umamit.feature.brew.timer.alertKey
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Single activity (06 §2): edge-to-edge, hosts the navigation shell and the
 * timer. While the timer is visible it shows over the lock screen; while the
 * alert plays it also turns the screen on (SCR-05, TMR-06).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var brewTimer: BrewTimer

    @Inject
    lateinit var clock: MonotonicClock

    private val timerVisible = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val activeBrewSource = TimerActiveBrewSource(brewTimer, clock, resources, lifecycleScope)
        val alertKey = brewTimer.state.map { it.alertKey() }.stateIn(lifecycleScope, SharingStarted.Eagerly, null)
        lifecycleScope.launch {
            combine(brewTimer.state, timerVisible, ::lockScreenFlags).distinctUntilChanged().collect {
                setShowWhenLocked(it.showWhenLocked)
                setTurnScreenOn(it.turnScreenOn)
            }
        }
        setContent {
            UmamiTheme {
                UmamitRoot(
                    activeBrewSource = activeBrewSource,
                    alertKey = alertKey,
                    onTimerVisibleChange = { timerVisible.value = it },
                )
            }
        }
    }
}
