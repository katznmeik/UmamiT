package com.umamit.app

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.umamit.app.timer.TimerActiveBrewSource
import com.umamit.app.timer.lockScreenFlags
import com.umamit.core.common.MonotonicClock
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.timer.notification.TimerScreenIntents
import com.umamit.feature.brew.timer.BrewTimer
import com.umamit.feature.brew.timer.alertKey
import com.umamit.feature.journal.JournalSources
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
 *
 * The timer's notifications open it with [TimerScreenIntents.ACTION_OPEN_TIMER]
 * (their content intent and the alert's full-screen intent, #47): the timer route
 * opens, and the lock-screen flags are set at once, before the first frame. Behind
 * the keyguard with the screen off no frame is drawn until the screen turns on, so
 * the flags cannot wait for the navigation (D-NNN).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var brewTimer: BrewTimer

    @Inject
    lateinit var clock: MonotonicClock

    @Inject
    lateinit var journalSources: JournalSources

    private val timerVisible = MutableStateFlow(false)

    /** A new token for each request to open the timer; 0 for none. */
    private val timerRequest = MutableStateFlow(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A recreated activity has handled its intent already.
        if (savedInstanceState == null) openTimerIfAsked(intent)
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
                    journalSources = journalSources,
                    alertKey = alertKey,
                    timerRequest = timerRequest,
                    onTimerVisibleChange = { timerVisible.value = it },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openTimerIfAsked(intent)
    }

    private fun openTimerIfAsked(intent: Intent?) {
        if (!TimerScreenIntents.isOpenTimer(intent)) return
        timerVisible.value = true
        val flags = lockScreenFlags(brewTimer.state.value, timerVisible = true)
        setShowWhenLocked(flags.showWhenLocked)
        setTurnScreenOn(flags.turnScreenOn)
        timerRequest.value = SystemClock.elapsedRealtimeNanos()
    }
}
