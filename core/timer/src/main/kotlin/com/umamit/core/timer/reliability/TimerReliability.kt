package com.umamit.core.timer.reliability

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.umamit.core.timer.diagnostics.PermissionStates
import com.umamit.core.timer.diagnostics.PermissionStatesReader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/**
 * What the timer's reliability depends on (TMR-31, SET-24, ONB-02), in the order of the
 * *Timer-Zuverlässigkeit* card (SCR-25). The setup page (SCR-01) shows the first four in its own
 * order; exact alarms are always granted through `USE_EXACT_ALARM` and only checked.
 */
enum class ReliabilityCheck {
    /** `POST_NOTIFICATIONS` granted and notifications on (04 §8). */
    Notifications,

    /** Live Update promotion, `canPostPromotedNotifications()` (04 §8.3). */
    LiveUpdates,

    /** The full-screen alert, `canUseFullScreenIntent()` (04 §8.2, TMR-06). */
    FullScreenAlert,

    /** Exempt from battery optimisation, One UI's *Akku › Uneingeschränkt* (04 §6.4). */
    Battery,

    /** Exact alarms, `canScheduleExactAlarms()` (04 §6.2). */
    ExactAlarms,
}

/** A check's row: ✓, ! (missing), or not on this platform (no row problem, e.g. Live Updates before API 36). */
enum class CheckState { Ok, Missing, Unavailable }

/** The state of every [ReliabilityCheck] at one moment. */
data class ReliabilityStatus(private val states: Map<ReliabilityCheck, CheckState>) {
    fun stateOf(check: ReliabilityCheck): CheckState = states[check] ?: CheckState.Unavailable

    /** The first missing check in the card's order: the one the banner's *Beheben* opens (TMR-31). */
    val firstMissing: ReliabilityCheck? get() = ReliabilityCheck.entries.firstOrNull {
        stateOf(it) == CheckState.Missing
    }

    /** A check is missing: the banner shows (TMR-31). */
    val hasProblem: Boolean get() = firstMissing != null

    companion object {
        /** Every check passing; the state before the first read. */
        val AllOk = ReliabilityStatus(ReliabilityCheck.entries.associateWith { CheckState.Ok })
    }
}

/** The [ReliabilityStatus] of these permission states (verified on the Galaxy, #14). */
fun PermissionStates.toReliabilityStatus(): ReliabilityStatus = ReliabilityStatus(
    mapOf(
        ReliabilityCheck.Notifications to notifications.toCheckState(),
        ReliabilityCheck.LiveUpdates to (promotedNotifications?.toCheckState() ?: CheckState.Unavailable),
        ReliabilityCheck.FullScreenAlert to fullScreenIntent.toCheckState(),
        ReliabilityCheck.Battery to batteryUnrestricted.toCheckState(),
        ReliabilityCheck.ExactAlarms to exactAlarms.toCheckState(),
    ),
)

private fun Boolean.toCheckState() = if (this) CheckState.Ok else CheckState.Missing

/**
 * The live reliability checks (TMR-31): the setup page, the settings card and the banner on Start
 * and the timer read them again on every resume, since the user may come back from the system
 * settings.
 */
interface TimerReliability {
    /** Reads every check now (a binder call each; quick, read on resume). */
    fun status(): ReliabilityStatus

    /**
     * *Beheben* / the row's action: the system pages of [check] (03 §2.4), most specific first.
     * The screen starts them from its Activity ([startFirstIntent]), so the
     * page opens in the app's task and back returns to the app.
     */
    fun settingsIntents(check: ReliabilityCheck): List<Intent>
}

/** Whether a check is missing now. */
fun TimerReliability.hasProblem(): Boolean = status().hasProblem

/** The banner's *Beheben*: the pages of the first missing check, or none. */
fun TimerReliability.firstMissingIntents(): List<Intent> = status().firstMissing?.let(::settingsIntents).orEmpty()

/** The checks on the device: the [PermissionStatesReader] and the settings pages of [reliabilitySettingsIntents]. */
class AndroidTimerReliability(private val reader: PermissionStatesReader, private val packageName: String) :
    TimerReliability {

    @Inject
    constructor(@ApplicationContext context: Context, reader: PermissionStatesReader) : this(
        reader = reader,
        packageName = context.packageName,
    )

    override fun status(): ReliabilityStatus = reader.read().toReliabilityStatus()

    override fun settingsIntents(check: ReliabilityCheck): List<Intent> =
        reliabilitySettingsIntents(check, status().stateOf(check), packageName)
}

/**
 * The settings pages of [check], most specific first (04 §6.2, §6.4, §8.2, §8.3; verified
 * on the Galaxy, #14). The last one is always the app's details page, so a tap opens something
 * where a page is missing (One UI, emulators). The battery asks for the exemption while the app
 * is restricted, and once it is exempt opens the app's details, where One UI keeps *Akku ›
 * Uneingeschränkt*.
 */
// BatteryLife: sideloaded, never reviewed by Play (D-013, 04 §6.4). InlinedApi: the promotion
// page (API 36) is a plain string constant, used only from API 36.
@SuppressLint("BatteryLife", "InlinedApi")
fun reliabilitySettingsIntents(
    check: ReliabilityCheck,
    state: CheckState,
    packageName: String,
    sdkInt: Int = Build.VERSION.SDK_INT,
): List<Intent> {
    val packageUri = Uri.fromParts("package", packageName, null)
    val notificationSettings = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    val specific = when (check) {
        ReliabilityCheck.Notifications -> listOf(notificationSettings)

        ReliabilityCheck.LiveUpdates -> if (sdkInt >= Build.VERSION_CODES.BAKLAVA) {
            listOf(
                Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
                notificationSettings,
            )
        } else {
            listOf(notificationSettings)
        }

        ReliabilityCheck.FullScreenAlert -> listOf(
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri),
        )

        ReliabilityCheck.Battery -> if (state == CheckState.Ok) {
            emptyList()
        } else {
            listOf(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            )
        }

        ReliabilityCheck.ExactAlarms -> listOf(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri))
    }
    return specific + Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
}

/**
 * The link of the Samsung hint (ONB-02 item 5, 04 §7.3): One UI's *Akku und Gerätewartung*
 * (Device care), where *Automatische Optimierung › Automatisch neu starten* lives, else the
 * settings. The PO checks the link on the Galaxy (09 §4).
 */
fun samsungAutoRestartIntents(): List<Intent> = listOf(
    Intent().setComponent(
        ComponentName(SAMSUNG_DEVICE_CARE, "com.samsung.android.sm.ui.dashboard.SmartManagerDashBoardActivity"),
    ),
    Intent(Settings.ACTION_SETTINGS),
)

private const val SAMSUNG_DEVICE_CARE = "com.samsung.android.lool"

/**
 * Starts the first of [intents] an activity handles; returns whether one started. Trying them in
 * turn (instead of resolving first) works without package-visibility queries.
 */
fun Context.startFirstIntent(intents: List<Intent>): Boolean = intents.any { intent ->
    if (this !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

/** Binds the device's [TimerReliability] for the setup, the settings, Start and the timer (M1-15). */
@Module
@InstallIn(SingletonComponent::class)
abstract class ReliabilityModule {
    @Binds
    abstract fun timerReliability(impl: AndroidTimerReliability): TimerReliability
}
