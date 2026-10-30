package com.umamit.core.data.catalog

import android.app.Application
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Looper
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPowerManager
import java.time.Duration

/** An app that runs the refresh, for the service the system creates (like the timer's TimerHost, D-057). */
class TestCatalogRefreshHost :
    Application(),
    CatalogRefreshHost {
    val port = FakeServicePort()

    override val catalogRefresh: CatalogRefreshServicePort get() = port
}

/** The refresh as the service sees it: tests drive [state]; counts *Abbrechen* and keeps the log. */
class FakeServicePort : CatalogRefreshServicePort {
    val mutableState = MutableStateFlow<CatalogRefreshState>(CatalogRefreshState.Idle)

    /** Following the state fails, as a bug in the service's notification code would. */
    var failToFollow = false

    @OptIn(ExperimentalForInheritanceCoroutinesApi::class)
    override val state: StateFlow<CatalogRefreshState> = object : StateFlow<CatalogRefreshState> by mutableState {
        override suspend fun collect(collector: FlowCollector<CatalogRefreshState>): Nothing {
            check(!failToFollow) { "following failed" }
            mutableState.collect(collector)
        }
    }
    var cancels = 0
        private set
    val logs = mutableListOf<String>()

    override fun cancel() {
        cancels++
    }

    override fun log(message: String) {
        logs += message
    }
}

/**
 * The refresh's `dataSync` foreground service (#65; CAT-07 AC1, 06 §5.3): it comes up in the
 * foreground with a progress notification on its own `catalog` channel, cancels from that
 * notification, keeps the CPU awake while the refresh runs, and leaves with a result notification
 * when the refresh ends. It stays apart from the timer (rule 8, CAT-07 AC8): own channel, own IDs,
 * at most one progress update every two seconds, and no refusal or time limit crashes the process.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestCatalogRefreshHost::class)
class CatalogRefreshServiceTest {
    private val app: TestCatalogRefreshHost = ApplicationProvider.getApplicationContext()
    private val port = app.port
    private val notifications = app.getSystemService(NotificationManager::class.java)

    private fun running(done: Int, total: Int, products: Int) =
        CatalogRefreshState.Running(CatalogRefreshProgress(pagesDone = done, pagesTotal = total, products = products))

    private fun service(): ServiceController<CatalogRefreshService> =
        Robolectric.buildService(CatalogRefreshService::class.java, CatalogRefreshService.runIntent(app)).create()

    private fun ServiceController<CatalogRefreshService>.run(startId: Int = 1) = apply {
        get().onStartCommand(CatalogRefreshService.runIntent(app), 0, startId)
        idle()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun advance(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

    private fun ServiceController<CatalogRefreshService>.foreground(): Notification? =
        shadowOf(get()).lastForegroundNotification

    private fun posted(id: Int): Notification? = shadowOf(notifications).getNotification(id)

    private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

    private fun Notification.title() = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()

    private fun wakeLock(): PowerManager.WakeLock? = ShadowPowerManager.getLatestWakeLock()

    @Test
    fun aRunComesUpInTheForegroundAsDataSyncWithItsProgressOnTheCatalogChannel() {
        port.mutableState.value = running(done = 3, total = 10, products = 2)

        val service = service().run()

        val shadow = shadowOf(service.get())
        assertThat(shadow.lastForegroundNotificationId).isEqualTo(CatalogNotifications.ID_PROGRESS)
        assertThat(service.get().foregroundServiceType).isEqualTo(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        val progress = checkNotNull(service.foreground())
        assertThat(progress.channelId).isEqualTo("catalog")
        assertThat(progress.title()).isEqualTo("Katalog wird geladen")
        assertThat(progress.text()).isEqualTo("Seiten: 3 von 10 · Produkte: 2")
        assertThat(progress.extras.getInt(Notification.EXTRA_PROGRESS)).isEqualTo(3)
        assertThat(progress.extras.getInt(Notification.EXTRA_PROGRESS_MAX)).isEqualTo(10)
        assertThat(progress.flags and Notification.FLAG_ONGOING_EVENT).isNotEqualTo(0)
        assertThat(progress.actions.map { it.title.toString() }).containsExactly("Abbrechen")
        // Its own quiet channel: never in the alerting section next to the step alert (D-044).
        val channel = notifications.getNotificationChannel("catalog")
        assertThat(channel.name.toString()).isEqualTo("Katalog")
        assertThat(channel.importance).isEqualTo(NotificationManager.IMPORTANCE_LOW)
        assertThat(channel.sound).isNull()
        assertThat(CatalogNotifications.ID_PROGRESS).isAtLeast(TIMER_IDS_END)
        assertThat(CatalogNotifications.ID_RESULT).isAtLeast(TIMER_IDS_END)
    }

    @Test
    fun whileTheSitemapIsReadTheProgressIsIndeterminate() {
        port.mutableState.value = CatalogRefreshState.Running(CatalogRefreshProgress.STARTING)

        val progress = checkNotNull(service().run().foreground())

        assertThat(progress.text()).isEqualTo("Sitemap wird gelesen …")
        assertThat(progress.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)).isTrue()
    }

    @Test
    fun theProgressFollowsTheRunAtMostEveryTwoSeconds() {
        port.mutableState.value = running(done = 3, total = 10, products = 2)
        service().run()

        port.mutableState.value = running(done = 4, total = 10, products = 2)
        idle()
        port.mutableState.value = running(done = 5, total = 10, products = 3)
        idle()
        // Android sheds an app's updates beyond five a second, the timer's included: the
        // refresh waits its turn (rule 8).
        assertThat(posted(CatalogNotifications.ID_PROGRESS)?.text()).isNotEqualTo("Seiten: 4 von 10 · Produkte: 2")
        assertThat(posted(CatalogNotifications.ID_PROGRESS)?.text()).isNotEqualTo("Seiten: 5 von 10 · Produkte: 3")

        advance(CatalogRefreshService.PROGRESS_INTERVAL_MS)

        assertThat(posted(CatalogNotifications.ID_PROGRESS)?.text()).isEqualTo("Seiten: 5 von 10 · Produkte: 3")
    }

    @Test
    fun abbrechenInTheNotificationCancelsTheRun() {
        port.mutableState.value = running(done = 1, total = 10, products = 0)
        val service = service().run()
        val cancel = checkNotNull(service.foreground()).actions.single().actionIntent
        val sent = shadowOf(cancel)
        assertThat(sent.isServiceIntent).isTrue()

        service.get().onStartCommand(sent.savedIntent, 0, 2)
        idle()

        assertThat(port.cancels).isEqualTo(1)
        // The service stays until the run has ended.
        assertThat(shadowOf(service.get()).isStoppedBySelf).isFalse()
        port.mutableState.value = CatalogRefreshState.Finished(CatalogRefreshOutcome.Cancelled)
        idle()
        assertThat(shadowOf(service.get()).isStoppedBySelf).isTrue()
        assertThat(posted(CatalogNotifications.ID_RESULT)).isNull()
    }

    @Test
    fun theCpuStaysAwakeWhileTheRunLastsAndNotAfter() {
        port.mutableState.value = running(done = 1, total = 10, products = 0)
        service().run()

        val lock = checkNotNull(wakeLock())
        assertThat(lock.isHeld).isTrue()
        assertThat(shadowOf(lock).tag).isEqualTo("UmamiT:CatalogRefresh")

        port.mutableState.value = CatalogRefreshState.Finished(CatalogRefreshOutcome.Success(teas = 274, newTeas = 12))
        idle()
        assertThat(lock.isHeld).isFalse()
    }

    @Test
    fun whenTheRunSucceedsTheServiceLeavesWithAQuietResult() {
        port.mutableState.value = running(done = 9, total = 10, products = 270)
        val service = service().run()

        port.mutableState.value = CatalogRefreshState.Finished(
            CatalogRefreshOutcome.Success(
                teas = 276,
                newTeas = 12,
                changedTeas = 5,
                discontinuedTeas = 2,
                recipesToCheck = 3,
            ),
        )
        idle()

        val shadow = shadowOf(service.get())
        assertThat(shadow.isForegroundStopped).isTrue()
        assertThat(shadow.notificationShouldRemoved).isTrue()
        assertThat(shadow.isStoppedBySelf).isTrue()
        val result = checkNotNull(posted(CatalogNotifications.ID_RESULT))
        assertThat(result.channelId).isEqualTo("catalog")
        assertThat(result.title()).isEqualTo("Katalog geladen")
        // The summary of CAT-07 AC3, as the refresh sheet shows it (#70).
        assertThat(result.text())
            .isEqualTo(
                "276\u00A0Grüntees\u00A0· 12\u00A0neu\u00A0· 5\u00A0geändert\u00A0· " +
                    "2\u00A0nicht\u00A0mehr\u00A0im\u00A0Sortiment\u00A0· 3\u00A0Rezepte\u00A0prüfen",
            )
        assertThat(result.flags and Notification.FLAG_AUTO_CANCEL).isNotEqualTo(0)
        assertThat(result.contentIntent).isNotNull()
    }

    // From v0.2.2 a tap opens Sammlung › Katalog with the refresh sheet (SCR-22, 03 §2.4, D-097).
    @Test
    fun aTapOnTheProgressOrTheResultAsksTheAppForTheRefreshSheet() {
        port.mutableState.value = running(done = 3, total = 10, products = 2)
        val service = service().run()
        val progress = checkNotNull(service.foreground())
        port.mutableState.value = CatalogRefreshState.Finished(CatalogRefreshOutcome.Success(teas = 276, newTeas = 12))
        idle()
        val result = checkNotNull(posted(CatalogNotifications.ID_RESULT))

        for (notification in listOf(progress, result)) {
            val tap = shadowOf(notification.contentIntent)
            assertThat(tap.isActivity).isTrue()
            assertThat(tap.flags and PendingIntent.FLAG_IMMUTABLE).isNotEqualTo(0)
            val intent = tap.savedIntent
            assertThat(CatalogRefreshIntents.isOpenRefresh(intent)).isTrue()
            // Only this app's own non-exported trampoline: never an intent another app could answer (#121).
            assertThat(intent.`package`).isEqualTo(app.packageName)
            assertThat(intent.component?.packageName).isEqualTo(app.packageName)
            assertThat(intent.component?.className).isEqualTo(CatalogRefreshIntents.TAP_ACTIVITY)
            assertThat(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)
        }
        // *Abbrechen* still goes to the service.
        assertThat(shadowOf(progress.actions.single().actionIntent).isService).isTrue()
    }

    @Test
    fun aFailureSaysWhyAndThatTheCatalogIsKept() {
        port.mutableState.value = running(done = 9, total = 10, products = 270)
        service().run()

        port.mutableState.value = CatalogRefreshState.Finished(CatalogRefreshOutcome.Offline("details"))
        idle()

        val result = checkNotNull(posted(CatalogNotifications.ID_RESULT))
        assertThat(result.title()).isEqualTo("Katalog nicht geladen")
        assertThat(result.text())
            .isEqualTo(
                "Keine Verbindung zum Shop – bitte später erneut versuchen. Dein bisheriger Katalog bleibt erhalten.",
            )
    }

    @Test
    fun aRunThatEndedBeforeTheServiceCameUpStillEntersTheForegroundFirst() {
        // startForegroundService obliges the service to call startForeground before it stops,
        // or Android crashes the process, the timer's included.
        port.mutableState.value = CatalogRefreshState.Finished(CatalogRefreshOutcome.Success(teas = 2, newTeas = 2))

        val service = service().run()

        val shadow = shadowOf(service.get())
        assertThat(shadow.lastForegroundNotificationId).isEqualTo(CatalogNotifications.ID_PROGRESS)
        assertThat(shadow.isForegroundStopped).isTrue()
        assertThat(shadow.isStoppedBySelf).isTrue()
        // A first load: every tea is new, so the summary doesn't say so.
        assertThat(posted(CatalogNotifications.ID_RESULT)?.text()).isEqualTo("2\u00A0Grüntees")
    }

    @Test
    fun aResultAlreadySeenInTheAppGetsNoNotification() {
        port.mutableState.value = running(done = 9, total = 10, products = 270)
        service().run()

        port.mutableState.value = CatalogRefreshState.Idle
        idle()

        assertThat(posted(CatalogNotifications.ID_RESULT)).isNull()
    }

    @Test
    fun aRefusedForegroundStartLeavesTheRunGoingAndStopsQuietly() {
        port.mutableState.value = running(done = 1, total = 10, products = 0)
        val service = service()
        shadowOf(service.get()).setThrowInStartForeground(ForegroundServiceStartNotAllowedException("time limit"))

        service.run()

        assertThat(port.cancels).isEqualTo(0)
        assertThat(shadowOf(service.get()).isStoppedBySelf).isTrue()
        assertThat(wakeLock()?.isHeld ?: false).isFalse()
        assertThat(port.logs.single()).contains("ForegroundServiceStartNotAllowedException")
    }

    @Test
    fun theDataSyncTimeLimitCancelsTheRunAndStopsAtOnce() {
        port.mutableState.value = running(done = 1, total = 10, products = 0)
        val service = service().run()

        service.get().onTimeout(1, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        idle()

        assertThat(port.cancels).isEqualTo(1)
        val shadow = shadowOf(service.get())
        assertThat(shadow.isForegroundStopped).isTrue()
        assertThat(shadow.isStoppedBySelf).isTrue()
        assertThat(wakeLock()?.isHeld).isFalse()
        assertThat(port.logs).containsExactly(
            "catalog refresh: service in the foreground",
            "catalog refresh: dataSync time limit reached, cancelled",
            "catalog refresh: service left the foreground",
        ).inOrder()
    }

    @Test
    fun aNewRunStartedBeforeTheServiceSawTheEndKeepsItForTheNewRun() {
        port.mutableState.value = running(done = 1, total = 10, products = 0)
        val service = service().run(startId = 1)

        // The run ends, and the user starts a new one at once: its start (2) comes in before the
        // service has seen the end.
        port.mutableState.value = CatalogRefreshState.Finished(CatalogRefreshOutcome.Success(teas = 2, newTeas = 0))
        port.mutableState.value = running(done = 0, total = 0, products = 0)
        service.get().onStartCommand(CatalogRefreshService.runIntent(app), 0, 2)
        idle()

        assertThat(shadowOf(service.get()).isStoppedBySelf).isFalse()
        assertThat(shadowOf(service.get()).isForegroundStopped).isFalse()
        assertThat(wakeLock()?.isHeld).isTrue()
    }

    @Test
    fun aFailureWhileFollowingStopsTheServiceNotTheProcess() {
        port.mutableState.value = running(done = 1, total = 10, products = 0)
        port.failToFollow = true

        val service = service().run()

        val shadow = shadowOf(service.get())
        assertThat(shadow.isForegroundStopped).isTrue()
        assertThat(shadow.isStoppedBySelf).isTrue()
        assertThat(wakeLock()?.isHeld).isFalse()
        assertThat(port.cancels).isEqualTo(0)
        assertThat(port.logs.last())
            .isEqualTo("catalog refresh: service failed (IllegalStateException), stopped; the refresh goes on")
    }

    private companion object {
        /** The timer's notification IDs are 1 to 4 (04 §8.2); the catalog's must not touch them. */
        const val TIMER_IDS_END = 10
    }
}

/** Without an app that hands over the refresh, a start still enters the foreground before it stops (no crash). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UnwiredCatalogRefreshServiceTest {
    @Test
    fun aStartWithoutTheRefreshEntersTheForegroundAndStops() {
        val app: Context = ApplicationProvider.getApplicationContext()
        val service = Robolectric.buildService(CatalogRefreshService::class.java, CatalogRefreshService.runIntent(app))
            .create()
            .startCommand(0, 1)
        shadowOf(Looper.getMainLooper()).idle()

        val shadow = shadowOf(service.get())
        assertThat(shadow.lastForegroundNotificationId).isEqualTo(CatalogNotifications.ID_PROGRESS)
        assertThat(shadow.isStoppedBySelf).isTrue()
    }
}
