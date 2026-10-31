package com.umamit.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.umamit.app.timer.TimerActiveBrewSource
import com.umamit.app.timer.TimerScreenRequests
import com.umamit.app.timer.lockScreenFlags
import com.umamit.core.common.MonotonicClock
import com.umamit.core.data.SettingsRepository
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.timer.controller.alertKey
import com.umamit.core.timer.notification.TimerScreenIntents
import com.umamit.feature.brew.timer.BrewTimer
import com.umamit.feature.journal.JournalSources
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
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
 * the flags cannot wait for the navigation (D-064).
 *
 * A link shared to the app reaches it from [ShareTeaLinkActivity] ([openTeaLink], CAT-17): the
 * shell shows Sammlung › Katalog, which loads it. The timer is never covered for it: while the
 * timer or another screen above the tabs is shown, the share waits until the tabs are back (rule 8).
 *
 * The catalog refresh's notifications open it with [CatalogRefreshRequests] (SCR-22, D-097): the
 * root shows Sammlung › Katalog with the refresh sheet above what was shown, never over an alert.
 *
 * The daily low-stock reminder opens it with [StartRequests] (INV-11, D-098): the root shows Start at its
 * root, after a running alert.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var brewTimer: BrewTimer

    @Inject
    lateinit var clock: MonotonicClock

    @Inject
    lateinit var journalSources: JournalSources

    @Inject
    lateinit var settings: SettingsRepository

    private val timerVisible = MutableStateFlow(false)

    /** The notifications' requests to open the timer; a pending one survives a recreation. */
    private val timerRequests = TimerScreenRequests()

    /** Links shared to the app (CAT-17); a pending one survives a recreation. */
    private val teaLinkShares = TeaLinkShares()

    /** Taps on the catalog refresh's notifications (SCR-22, D-097); a pending one survives a recreation. */
    private val catalogRefreshRequests = CatalogRefreshRequests()

    /** Taps on the daily low-stock reminder (INV-11, D-098); a pending one survives a recreation. */
    private val startRequests = StartRequests()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A recreated activity has seen its intent already, but keeps a request not handled yet (S1, #47).
        if (savedInstanceState == null) {
            openTimerIfAsked(intent)
            teaLinkShares.offer(intent)
            catalogRefreshRequests.offer(intent)
            startRequests.offer(intent)
        } else {
            if (timerRequests.restore(savedInstanceState, intent)) showTimerOverLockScreen()
            teaLinkShares.restore(savedInstanceState)
            catalogRefreshRequests.restore(savedInstanceState)
            startRequests.restore(savedInstanceState)
        }
        val activeBrewSource = TimerActiveBrewSource(brewTimer, clock, resources, lifecycleScope)
        val onboardingCompleted = settings.onboardingCompleted()
            .stateIn(lifecycleScope, SharingStarted.Eagerly, null)
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
                    onboardingCompleted = onboardingCompleted,
                    timerRequest = timerRequests.request,
                    onConsumeTimerRequest = timerRequests::handled,
                    onTimerVisibleChange = { timerVisible.value = it },
                    teaLinkShare = teaLinkShares.share,
                    onTakeTeaLinkShare = teaLinkShares::taken,
                    catalogRefreshRequest = catalogRefreshRequests.token,
                    onConsumeCatalogRefreshRequest = catalogRefreshRequests::handled,
                    startRequest = startRequests.token,
                    onConsumeStartRequest = startRequests::handled,
                    startScreen = { StartTabContent(it) },
                    catalogScreen = { CatalogTabContent(it) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openTimerIfAsked(intent)
        teaLinkShares.offer(intent)
        catalogRefreshRequests.offer(intent)
        startRequests.offer(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        timerRequests.save(outState)
        teaLinkShares.save(outState)
        catalogRefreshRequests.save(outState)
        startRequests.save(outState)
    }

    private fun openTimerIfAsked(intent: Intent?) {
        if (intent == null || !TimerScreenIntents.isOpenTimer(intent)) return
        showTimerOverLockScreen()
        timerRequests.request(intent)
    }

    /** The timer is about to show: over the lock screen at once, before any frame (D-058, D-064). */
    private fun showTimerOverLockScreen() {
        timerVisible.value = true
        val flags = lockScreenFlags(brewTimer.state.value, timerVisible = true)
        setShowWhenLocked(flags.showWhenLocked)
        setTurnScreenOn(flags.turnScreenOn)
    }

    companion object {
        /**
         * Opens the app's own task with the shared [text] (CAT-17), as the timer's notifications
         * open it (`NEW_TASK | SINGLE_TOP`): a running activity gets it in [onNewIntent].
         */
        fun openTeaLink(context: Context, text: String): Intent = Intent(context, MainActivity::class.java)
            .setAction(TeaLinkShares.ACTION_OPEN_TEA_LINK)
            .putExtra(TeaLinkShares.EXTRA_TEXT, text)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}

/**
 * Whether the first-start setup is done, for the start destination (SCR-01, D-066); null until
 * read. An unreadable settings file never keeps the app on the setup or a blank screen: the
 * repository reads it as the setup done (D-068), and any other error opens on Start too.
 */
internal fun SettingsRepository.onboardingCompleted(): Flow<Boolean?> =
    settings.map<_, Boolean?> { it.onboardingCompleted }.catch { emit(true) }
