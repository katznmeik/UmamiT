package com.umamit.app

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.umamit.app.timer.TimerActiveBrewSource
import com.umamit.app.timer.TimerScreenRequests
import com.umamit.app.timer.lockScreenFlags
import com.umamit.core.common.MonotonicClock
import com.umamit.core.data.FsiPromptRepository
import com.umamit.core.data.SettingsRepository
import com.umamit.core.designsystem.theme.UmamiAppTheme
import com.umamit.core.model.ThemeMode
import com.umamit.core.timer.controller.alertKey
import com.umamit.core.timer.notification.TimerScreenIntents
import com.umamit.core.timer.reliability.TimerReliability
import com.umamit.feature.brew.timer.BrewTimer
import com.umamit.feature.journal.JournalSources
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
 * The timer screens (SCR-05, SCR-07) stay portrait, also over the lock screen (D-136 Q5, build note „Portrait timer"):
 * while the root reports one on top ([requestTimerOrientation]) the activity asks for portrait, and for the system's
 * choice again when it leaves; a request to open the timer asks at once, before the lock-screen flags. The activity
 * handles a rotation itself (`configChanges`), so neither the request nor a turn of the phone re-creates it.
 *
 * A link shared to the app reaches it from [ShareTeaLinkActivity] ([openTeaLink], CAT-17): the
 * shell shows Sammlung › Katalog, which loads it. The timer is never covered for it: while the
 * timer or another screen above the tabs is shown, the share waits until the tabs are back (rule 8).
 *
 * A recipe file opened or shared to the app reaches it from [ReceiveRecipeActivity] ([openRecipe], PRF-08), and one
 * picked with *Rezept importieren* from the shell ([ReceivedRecipeFiles.receive]): the shell shows SCR-32 in Sammlung.
 * Like a shared link it waits while the timer or another screen above the tabs is shown (rule 8, PRF-08 AC9).
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

    @Inject
    lateinit var reliability: TimerReliability

    @Inject
    lateinit var fsiPrompted: FsiPromptRepository

    private val timerVisible = MutableStateFlow(false)

    /** Whether the phone is locked, for the close of the timer over the keyguard (SCR-05, A-17/A-18). */
    private val keyguard = KeyguardLock { getSystemService(KeyguardManager::class.java).isKeyguardLocked }

    /** Unlocked while the app is shown (over the keyguard): the root's back stops taking the locked path. */
    private val userPresent = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            keyguard.read()
        }
    }

    /** The notifications' requests to open the timer; a pending one survives a recreation. */
    private val timerRequests = TimerScreenRequests()

    /** Links shared to the app (CAT-17); a pending one survives a recreation. */
    private val teaLinkShares = TeaLinkShares()

    /** Recipe files received (PRF-08); kept in a view model, so a copy running during a re-creation still arrives. */
    private val receivedRecipesModel: ReceivedRecipesModel by viewModels()

    /** Recipe files received (PRF-08); a pending or shown one survives a recreation. */
    private val receivedRecipes: ReceivedRecipeFiles get() = receivedRecipesModel.files

    /** Where a handed-over recipe file is copied from, off the main thread (D-133 Q4). */
    private val recipeSource by lazy { RecipeFileSource.of(this) }

    /** Taps on the catalog refresh's notifications (SCR-22, D-097); a pending one survives a recreation. */
    private val catalogRefreshRequests = CatalogRefreshRequests()

    /** Taps on the daily low-stock reminder (INV-11, D-098); a pending one survives a recreation. */
    private val startRequests = StartRequests()

    /** Taps on the backup reminder (BAK-06, D-130 Q11); a pending one survives a recreation. */
    private val backupRequests = BackupRequests()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A recreated activity has seen its intent already, but keeps a request not handled yet (S1, #47).
        if (savedInstanceState == null) {
            openTimerIfAsked(intent)
        } else if (timerRequests.restore(savedInstanceState, intent)) {
            requestTimerOrientation(timerScreenOnTop = true)
            showTimerOverLockScreen()
        }
        takeTaps(intent, savedInstanceState)
        // Exported: `ACTION_USER_PRESENT` is a protected broadcast that only the system (SystemUI) can send, so no app
        // can spoof it; a not-exported receiver is not guaranteed to get a broadcast sent from another process.
        registerReceiver(userPresent, IntentFilter(Intent.ACTION_USER_PRESENT), RECEIVER_EXPORTED)
        val activeBrewSource = TimerActiveBrewSource(brewTimer, clock, resources, lifecycleScope, lifecycle)
        val collectionSegments = SettingsCollectionSegments(settings, lifecycleScope)
        val onboardingCompleted = settings.onboardingCompleted()
            .stateIn(lifecycleScope, SharingStarted.Eagerly, null)
        val themeMode = themeModeState(settings)
        val fsiPrompt = AndroidFsiPrompt.of(this, reliability, fsiPrompted, lifecycleScope)
        // Seeded with the controller's current alert, so a re-created activity's first frame already knows it (A-07).
        val alertKey = brewTimer.state.map { it.alertKey() }
            .stateIn(lifecycleScope, SharingStarted.Eagerly, brewTimer.state.value.alertKey())
        lifecycleScope.launch {
            // A close waiting over the keyguard counts as visible once an alert rings, in the alert's turn (rule 8).
            combine(brewTimer.state, timerVisible, keyguard.closePending) { state, visible, closePending ->
                val shown = timerVisibleForFlags(visible, closePending, alerting = state.alertKey() != null)
                lockScreenFlags(state, timerVisible = shown)
            }.distinctUntilChanged().collect {
                setShowWhenLocked(it.showWhenLocked)
                setTurnScreenOn(it.turnScreenOn)
            }
        }
        setContent {
            val mode by themeMode.collectAsStateWithLifecycle()
            UmamiAppTheme(mode ?: ThemeMode.SYSTEM) {
                WindowBackground()
                UmamitRoot(
                    activeBrewSource = activeBrewSource,
                    journalSources = journalSources,
                    alertKey = alertKey,
                    onboardingCompleted = onboardingCompleted,
                    fsiPrompt = fsiPrompt,
                    timerRequest = timerRequests.request,
                    onConsumeTimerRequest = timerRequests::handled,
                    onTimerVisibleChange = ::onTimerVisibleChange,
                    onTimerScreenOnTopChange = { requestTimerOrientation(it) },
                    keyguard = keyguard,
                    teaLinkShare = teaLinkShares.share,
                    onTakeTeaLinkShare = teaLinkShares::taken,
                    receivedRecipes = receivedRecipes,
                    catalogRefreshRequest = catalogRefreshRequests.token,
                    onConsumeCatalogRefreshRequest = catalogRefreshRequests::handled,
                    startRequest = startRequests.token,
                    onConsumeStartRequest = startRequests::handled,
                    backupRequest = backupRequests.token,
                    onConsumeBackupRequest = backupRequests::handled,
                    startScreen = { StartTabContent(it) },
                    statsScreen = { StatsTabContent(it) },
                    catalogScreen = { CatalogTabContent(it) },
                    collectionScreens = CollectionStackScreens(myTeas = { MyTeasTabContent(it) }),
                    collectionSegments = collectionSegments,
                )
            }
        }
        holdFirstDrawUntil(ready = { onboardingCompleted.value != null }, never = { alertKey.value != null })
    }

    /**
     * A cold start draws its first frame only once the root has something to show (A-04, 03 §3.6): at most
     * [FIRST_DRAW_HOLD_MS], so the starting window goes straight to the app, not through an empty frame. Never while
     * the timer opens or an alert plays ([never], `timerVisible`): the full-screen intent's first frame is never
     * delayed (rule 8, D-064).
     */
    private fun holdFirstDrawUntil(ready: () -> Boolean, never: () -> Boolean) {
        val content = findViewById<View>(android.R.id.content)
        val start = SystemClock.uptimeMillis()
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    val draw = ready() || never() || timerVisible.value ||
                        SystemClock.uptimeMillis() - start >= FIRST_DRAW_HOLD_MS
                    if (draw) content.viewTreeObserver.removeOnPreDrawListener(this)
                    return draw
                }
            },
        )
    }

    /** Back in front (the keyguard went away, or the bouncer closed): the phone is read again (A-17). */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) keyguard.read()
    }

    override fun onDestroy() {
        unregisterReceiver(userPresent)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openTimerIfAsked(intent)
        takeTaps(intent, null)
    }

    /**
     * The taps other than the timer's (a shared link, a recipe file, the catalog refresh, Start, the backup
     * reminder): the intent's when there is no saved state, else the ones still pending when the activity was
     * recreated.
     */
    private fun takeTaps(intent: Intent?, savedState: Bundle?) {
        if (savedState == null) {
            teaLinkShares.offer(intent)
            receivedRecipes.offer(intent, recipeSource)
            catalogRefreshRequests.offer(intent)
            startRequests.offer(intent)
            backupRequests.offer(intent)
        } else {
            teaLinkShares.restore(savedState)
            receivedRecipes.restore(savedState)
            catalogRefreshRequests.restore(savedState)
            startRequests.restore(savedState)
            backupRequests.restore(savedState)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        timerRequests.save(outState)
        teaLinkShares.save(outState)
        receivedRecipes.save(outState)
        catalogRefreshRequests.save(outState)
        startRequests.save(outState)
        backupRequests.save(outState)
    }

    private fun openTimerIfAsked(intent: Intent?) {
        if (intent == null || !TimerScreenIntents.isOpenTimer(intent)) return
        // Portrait before the flags: the window shows over the lock screen already upright (D-136 Q5).
        requestTimerOrientation(timerScreenOnTop = true)
        // A close waiting over the keyguard gives way to the request, as to a new alert: no pop at `ON_STOP` leaves
        // `showWhenLocked` set over the shell. Reported first, so the flags are never cleared in between (rule 8).
        showTimerOverLockScreen()
        keyguard.setClosePending(false)
        timerRequests.request(intent)
    }

    /**
     * The window behind the app takes the theme's background, so nothing of the phone's theme shows through. It
     * follows a night switch in place (A-08): the themes.xml window background is resolved only when the window is
     * made, so this replaces it on every change of the scheme. The system-bar scrims need nothing: `enableEdgeToEdge`
     * runs once and leaves them transparent (the system keeps the contrast), and [SystemBarIcons] owns the icons.
     */
    @Composable
    private fun WindowBackground() {
        val view = LocalView.current
        val background = MaterialTheme.colorScheme.background.toArgb()
        SideEffect { view.rootView.setBackgroundColor(background) }
    }

    /** The timer is about to show: over the lock screen at once, before any frame (D-058, D-064). */
    private fun showTimerOverLockScreen() = onTimerVisibleChange(true)

    /**
     * The root's report, applied to the window flags in the same turn (the collector above follows the timer's state):
     * a close over the keyguard clears them before the next frame, a new alert sets them again at once (A-17, rule 8).
     */
    private fun onTimerVisibleChange(visible: Boolean) {
        timerVisible.value = visible
        val state = brewTimer.state.value
        val shown = timerVisibleForFlags(visible, keyguard.closePending.value, alerting = state.alertKey() != null)
        val flags = lockScreenFlags(state, timerVisible = shown)
        setShowWhenLocked(flags.showWhenLocked)
        setTurnScreenOn(flags.turnScreenOn)
    }

    companion object {
        /** The longest a cold start holds its first frame for the root's content (03 §3.6: ≈ 150 ms, A-04). */
        private const val FIRST_DRAW_HOLD_MS = 150L

        /**
         * Opens the app's own task with the shared [text] (CAT-17), as the timer's notifications
         * open it (`NEW_TASK | SINGLE_TOP`): a running activity gets it in [onNewIntent].
         */
        fun openTeaLink(context: Context, text: String): Intent = Intent(context, MainActivity::class.java)
            .setAction(TeaLinkShares.ACTION_OPEN_TEA_LINK)
            .putExtra(TeaLinkShares.EXTRA_TEXT, text)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        /**
         * Opens the app's own task with a received recipe [file] (PRF-08), as [openTeaLink] does: its URI as the
         * intent's data with the read grant passed on, so MainActivity copies it off the main thread (D-133 Q4).
         */
        fun openRecipe(context: Context, file: ReceivedFile): Intent = Intent(context, MainActivity::class.java)
            .setAction(ReceivedRecipeFiles.ACTION_OPEN_RECIPE)
            .setData(file.uri)
            .putExtra(ReceivedRecipeFiles.EXTRA_TYPE, file.type)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
    }
}

/** Holds [MainActivity]'s [ReceivedRecipeFiles] over a re-creation; their copies run in its scope. */
class ReceivedRecipesModel : ViewModel() {
    val files = ReceivedRecipeFiles(scope = viewModelScope)
}

/**
 * Whether the first-start setup is done, for the start destination (SCR-01, D-066); null until
 * read. An unreadable settings file never keeps the app on the setup or a blank screen: the
 * repository reads it as the setup done (D-068), and any other error opens on Start too.
 */
internal fun SettingsRepository.onboardingCompleted(): Flow<Boolean?> =
    settings.map<_, Boolean?> { it.onboardingCompleted }.catch { emit(true) }

/** The stored *Design* (SET-05); an unreadable settings file reads as *System* (D-068). */
internal fun SettingsRepository.themeMode(): Flow<ThemeMode> =
    settings.map { it.theme }.catch { emit(ThemeMode.SYSTEM) }.distinctUntilChanged()

/**
 * The app theme's choice as state, following the settings at once; null until the file is read, and the app draws
 * *System* until then. Nothing waits for it (rule 8: a start into the timer or the alert never blocks on the theme; the
 * timer, its cover and the window background follow the app's theme from v0.8.0, SET-05, D-128 Q20). No flash of the
 * other theme still: the settings hand the stored choice to the system too (`UiModeManager.setApplicationNightMode`,
 * SET-05, D-114) when the *Design* dialog closes and whenever they show with no alert, so the starting window and the
 * configuration *System* reads are already in the chosen theme. The activity handles the night switch in place
 * (`configChanges="uiMode"`, D-117 build note A-08): Compose reads the new configuration and recolours in one frame.
 */
internal fun ComponentActivity.themeModeState(settings: SettingsRepository): StateFlow<ThemeMode?> =
    settings.themeMode().stateIn(lifecycleScope, SharingStarted.Eagerly, null)
