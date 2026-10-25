# M1 probe: Android 16 API notes (for M1-11)

> Historical record. The probe (`ProbeTimer`, `ProbeTimerService`,
> `ProbeStepTest` and the rest) was removed in M1-18 (#54); the patterns below
> live on in the real timer (`LiveUpdateNotification`, `TimerStateNotifications`,
> `TimerNotifications`) and its emulator tests (`TimerNotificationsDeviceTest`).

Recorded in M1-03a (#15). These names were checked with `javap` against the
compile SDK (`platforms/android-36/android.jar`, Platform 16 rev 2) and
AndroidX Core 1.18.0, and against the Android 16 framework that Robolectric
4.17 runs (`android-all-instrumented:16-robolectric-13921718`). The probe
release checks how the PO's Galaxy behaves (09 §4); add those results here.

The behaviour below was verified in the AOSP sources (2026-10-22, after the
API 36 emulator job failed twice; #15):

- SDK sources `sources;android-36` rev 1 (client side only);
- the GitHub mirror `aosp-mirror/platform_frameworks_base`, branches
  `android16-release` (the first Android 16 release) and
  `android16-qpr1-release` (Android 16 QPR1), and `android15-release` for
  comparison; `aosp-mirror/platform_packages_apps_settings` for Settings.

Line numbers refer to those branches.

## What the probe uses

| Purpose | API used (code in `core:timer`) |
|---|---|
| Request promotion (Live Update) | `NotificationCompat.Builder.setRequestPromotedOngoing(true)`, **always on API 36**, whether or not the user allows promotion at the moment. It writes the extra `NotificationCompat.EXTRA_REQUEST_PROMOTED_ONGOING` = `"android.requestPromotedOngoing"`. Read back with `NotificationCompat.isRequestPromotedOngoing(n)`. |
| May the app promote? | `NotificationManagerCompat.canPostPromotedNotifications()` (platform: `NotificationManager.canPostPromotedNotifications()`, API 36; the compat version returns false below 36). Used only for the status row and the banner. |
| Is the notification promotable? | `Notification.hasPromotableCharacteristics()` (API 36; compat: `NotificationCompat.hasPromotableCharacteristics(n)`). Its rules depend on the Android 16 build (below). |
| Was it promoted? | `Notification.FLAG_PROMOTED_ONGOING` = `0x00040000` (262144), set by the system. `Notification.flagsToString` prints it as `PROMOTED_ONGOING` |
| Progress bar with segments and points | `NotificationCompat.ProgressStyle` with `Segment(length).setColor()`, `Point(position).setColor()`, `setProgress()` and `setStyledByProgress(true)`. On API 36 it builds the platform `Notification.ProgressStyle`; below that it falls back to a plain `setProgress(max, progress)` bar |
| Status-bar chip while running | `setUsesChronometer(true)`, `setChronometerCountDown(true)`, `setWhen(deadlineWall)`, `setShowWhen(true)` |
| Status-bar chip otherwise | `NotificationCompat.Builder.setShortCriticalText(…)` (platform `Notification.Builder.setShortCriticalText`, API 36; extra `"android.shortCriticalText"`) |
| Promotion settings page | `Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS` (`"android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS"`). `ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS` in 04 §8.3 does not exist. AOSP Settings only handles it when the flag `android.app.ui_rich_ongoing` is on (its intent filter has `android:featureFlag`); otherwise the status row falls back to the app's notification settings |
| Full-screen intent allowed? | `NotificationManagerCompat.canUseFullScreenIntent()`; settings page `Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` |
| Alert while alerting | The alert (ID 2) **replaces the Live Update as the foreground-service notification** (`startForeground(2, alert)`); the Live Update (ID 1) is removed until the next step. See „Full-screen alert" below |
| Exact alarms | `AlarmManager.setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP, …)`, `canScheduleExactAlarms()`. Lint's `MissingPermission` check only knows `SCHEDULE_EXACT_ALARM`, so the call carries `@SuppressLint("MissingPermission")` (the app holds `USE_EXACT_ALARM`, D-013) |
| Foreground service | `ServiceCompat.startForeground(service, id, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)` with `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` in the manifest (04 §13) |

## Not in the SDK 36 stubs

- `Notification.Builder.setRequestPromotedOngoing` and
  `Notification.EXTRA_REQUEST_PROMOTED_ONGOING`: use the AndroidX builder,
  which writes the same extra.
- `android.permission.POST_PROMOTED_NOTIFICATIONS` is not in the SDK 36
  stubs, but it is **not** harmless: from Android 16 QPR1 (flag
  `android.app.ui_rich_ongoing` on) it is a real permission, and promotion
  needs it (next section). Keep the declaration in both manifests.
- `NotificationManager.setCanPostPromotedNotifications(pkg, uid, allowed)`
  is a `@TestApi` (`NotificationManager.java:1163` in the SDK 36 sources).
  The instrumented test calls it (see „Test setup").

## Promotion on Android 16: two rule sets

Everything below is switched by the aconfig flag `android.app.ui_rich_ongoing`
(„[RONs] Guards new promotion logic and UI"). `api_rich_ongoing` (the APIs,
`ProgressStyle`) is on in both.

### Which notifications are promotable

`Notification.hasPromotableCharacteristics()`:

- **Flag off** (`android16-release`, `Notification.java:3297`; the same code
  is in the SDK 36 sources and in Robolectric 4.17): ongoing **and** not a
  group summary **and** no custom views **and** a title **and** (an ongoing
  `CallStyle` **or** (colorized **and** a style of none / `BigTextStyle` /
  `CallStyle` / `ProgressStyle`)). The request extra is not read.
- **Flag on** (`android16-qpr1-release`, `Notification.java:3388`): the
  request extra **and** ongoing **and** a title **and** a promotable style
  (none / `BigTextStyle` / `CallStyle` / `MetricStyle` / `ProgressStyle`)
  **and** not a group summary **and** no custom views **and not colorized**.
  This matches the published Live Update rules.

`LiveUpdateNotification.build` therefore always sets the request on API 36
and posts the plain variant if the platform finds it promotable, otherwise the
colorized one (`setColorized(true)`, matcha; allowed because it is the FGS
notification). The choice does not depend on `canPostPromotedNotifications()`,
so the posted notification is always the promotable one. Logcat tag
`UmamiTLiveUpdate` says when the colorized variant is used.

The system sets `FLAG_PROMOTED_ONGOING` when the notification is enqueued if
promotion is allowed **and** `hasPromotableCharacteristics()` **and** the
channel importance is above MIN (`android16-release`
`NotificationManagerService.java:8491–8498`; QPR1
`fixNotificationWithChannel`, `:9038`). An app cannot set the flag itself.
When the user changes the toggle, `setCanBePromoted` re-checks the app's
enqueued **and posted** notifications at once and re-posts them (`:4602`
ff.). That is why the request must always be present: before this fix the
Live Update carried `requestPromotedOngoing=false` whenever promotion was
off, so a later „allow" could not promote it (on QPR1 rules).

`Notification.isPromotedOngoing()`, which System UI uses for the Live Update
UI, also requires the flag (`Notification.java:8114`). With the flag off the
system may set `FLAG_PROMOTED_ONGOING`, but AOSP shows no chip and no Live
Update UI.

### Where „promotion allowed" is stored

`NotificationManager.canPostPromotedNotifications()` calls
`INotificationManager.canBePromoted(pkg)`:

- **Flag off:** a per-app preference in NotificationManagerService,
  `PreferencesHelper.PackagePreferences.canHavePromotedNotifs`, saved as the
  attribute `promote` in the notification policy XML. Its default is the
  flag value, so **off** (`PreferencesHelper.java:3340`, comment „Until we
  enable the UI, we should return false"). It is changed only through
  `setCanBePromoted(pkg, uid, promote, fromUser)`
  (`NotificationManagerService.java:4602`), which requires the system, System
  UI or shell uid, or `STATUS_BAR_SERVICE` (`:11750`). There is no appop and
  no `cmd notification` command for it (`NotificationShellCmd.java` has no
  promotion command).
- **Flag on** (QPR1, `NotificationManagerService.java:4719`, `:9126`): the
  permission `android.permission.POST_PROMOTED_NOTIFICATIONS`
  (`protectionLevel="normal|appop"`, `featureFlag="android.app.ui_rich_ongoing"`,
  `core/res/AndroidManifest.xml:8269`) with the appop
  `android:post_promoted_notifications`. The appop's default mode is
  `MODE_DEFAULT`, which falls back to the permission grant
  (`PermissionManagerService.java:1217`); a declared normal permission is
  granted at install, so **promotion is allowed by default**. The user's
  toggle (`setCanBePromoted(…, fromUser = true)`) sets the appop to
  `MODE_ALLOWED` / `MODE_IGNORED` (`:4739` ff.). Here
  `adb shell appops set <pkg> POST_PROMOTED_NOTIFICATIONS allow|ignore` works
  too.

### What the CI emulator showed

The API 36 `google_apis` image reported `canPostPromotedNotifications=false`
before any test changed it. With flag-on rules our declared permission would
make it true, so the image runs the flag-off rules. The probe test logs the
flags (`aflags list`) and the posted variant (`Promotion rule set:` in
logcat) to confirm this in every run.

## Full-screen alert on Android 16: force grouping silenced it

The API 36 failure (`ProbeAlertActivity` never shown) had the alert posted
with full-screen intent, `canUseFullScreenIntent=true`, but the flags
`AUTO_CANCEL|HIGH_PRIORITY|SILENT`. The app never calls `setSilent`. The
chain in the sources (`android16-release`):

1. `config_autoGroupAtCount` is **2** (`core/res/res/values/config.xml:5220`;
   also 2 in Android 15 and QPR1).
2. With `notification_force_grouping` on, `GroupHelper.maybeGroupWithSections`
   (`GroupHelper.java:585`) counts a package's **ungrouped** notifications per
   section. The sections are by importance: `AlertingSection` (≥ DEFAULT) and
   `SilentSection` (< DEFAULT) (`:154`). With 2 in one section it
   force-groups them (`:628`). The Live Update (`brew_timer`, DEFAULT) and the
   alert (`steep_finished`, HIGH) were both in `AlertingSection`, so posting
   the alert grouped both.
3. `NotificationManagerService.addAutogroupKeyLocked` sets `FLAG_SILENT` on
   every force-grouped notification (`:7466`; QPR1 `:7643`).
4. System UI's `FullScreenIntentDecisionProvider.makeDecisionWithoutDnd`
   returns `NO_FSI_SUPPRESSIVE_SILENT_NOTIFICATION` when
   `notification_silent_flag` is on and `notification.isSilent` (`:160`),
   before it looks at the screen state. `HunSilentNotificationSuppressor`
   (`CommonVisualInterruptionSuppressors.kt:229`) also suppresses the
   heads-up. Both are the same in QPR1.

Android 15 also groups at 2, but has neither `FLAG_SILENT` nor the silent
check (`android15-release`), so the API 35 job passed. This is not an
emulator artefact: on any Android 16 build with these flags (the Galaxy
included, unless Samsung changed them) the alert would neither take over
the screen nor show as a heads-up while a Live Update is shown, that is,
during every step.

Grouping exemptions (`GroupHelper.isNotificationGroupable`, `:2010`): a
colorized FGS notification (importance above MIN), `CallStyle` and media
notifications. The colorized Live Update of the flag-off rules is therefore
never grouped, but the plain one of the flag-on rules is.

**Fix:** at the deadline the service calls `startForeground(ID_ALERT, alert)`.
When the FGS notification ID changes, `ActiveServices` cancels the old one
before it posts the new one (`ActiveServices.java:2622`; both run in order on
the activity manager handler; NotificationManagerService then removes the
Live Update, and updates its grouping state, before it posts the alert). So
the alert is the app's only notification of importance DEFAULT or higher, it
is not grouped and not silenced, and it works on both rule sets. A Live Update
refresh that is still in flight (the 15 s refresh of a 60 s step falls on the
deadline) is removed with it: the refresh is enqueued before the switch,
because both run on the service's main thread, and the cancel removes the
posted and the enqueued record (`removeFromNotificationListsLocked`,
`NotificationManagerService.java:10824`). After the switch the service posts
no more Live Updates. A Live Update posted without the service (fallback) is
cancelled explicitly. A new step
makes the Live Update the FGS notification again, which removes the alert.
The alert carries `FOREGROUND_SERVICE_IMMEDIATE`.

Alternatives considered:

- `brew_timer` at importance LOW would put the Live Update into
  `SilentSection`. But silent notifications can be hidden from the status bar
  and the lock screen, which the Live Update fallback (04 §8.3) needs, and it
  changes 04 §8.1.
- An explicit group with a summary for the alert avoids the grouping today,
  but it adds a notification and depends on the sparse-group rules.
- The colorized-FGS exemption alone does not work with flag-on rules
  (colorized notifications are not promotable there).

**For M1-11:** the rule is „the alert is the only notification of the app
with importance DEFAULT or higher". A long step, where the Live Update is
posted without the service (04 §6.1), must cancel ID 1 before it posts the
alert. The LOW channels (`sessions`, `reminders`, `catalog`) are in
`SilentSection` and do not affect the alert.

The full-screen intent itself needs no other change. The intent is sent by
System UI, which has background-activity-start rights; the same APK works on
API 35. `ProbeAlertActivity` calls `setShowWhenLocked(true)` and
`setTurnScreenOn(true)` in `onCreate`, before the window is shown.

## Test setup on the emulator (`ProbeStepTest`)

- **Live Update toggle:** the API 36 image has promotion off (flag-off
  rules). Like the PO in Settings, the test turns it on: it calls the
  `@TestApi` `setCanPostPromotedNotifications(pkg, myUid, true)` by
  reflection, with `STATUS_BAR_SERVICE` borrowed from the shell
  (`UiAutomation.adoptShellPermissionIdentity`). Instrumented processes may
  call test APIs (`am instrument` allows them by default). The test asserts
  that `canPostPromotedNotifications()` is then true, and restores the old
  value afterwards. The check itself is unchanged: `FLAG_PROMOTED_ONGOING` on
  the posted Live Update. The test also asserts that the request extra is
  set.
- **Full-screen alert:** the test now also asserts that the alert has no
  `FLAG_SILENT`, that it is the FGS notification, and that the Live Update is
  gone while it is shown. On a timeout it reports the alert activity's
  lifecycle stages, `dumpsys activity activities` (resumed activity,
  keyguard), `dumpsys power` (wakefulness) and System UI's interruption log
  (`NotifInterruptLog`: `FSI allowed/suppressed: <reason>`). On success it
  logs System UI's decision.

## Other observations

- System-set flags (`FLAG_FOREGROUND_SERVICE`, `FLAG_PROMOTED_ONGOING`) are
  visible in `NotificationManager.getActiveNotifications()` from the app's
  own process, so tests can check promotion without parsing `dumpsys`. The
  probe test checks both.
- A full-screen intent launches the activity only when the screen is off or
  locked. With the phone in use it shows as a heads-up (04 §8.2). The
  instrumented test therefore turns the screen off (`input keyevent
  KEYCODE_SLEEP`) before the deadline.
- `ShadowAlarmManager.canScheduleExactAlarms()` is false by default in
  Robolectric. The tests set it to true, as `USE_EXACT_ALARM` does on a device.
- Robolectric's SDK 36 sandbox on JDK 21 needs
  `--add-exports=java.base/jdk.internal.access=ALL-UNNAMED` and
  `--add-opens=java.base/java.io=ALL-UNNAMED`.
- Robolectric's `startForeground` does not cancel the previous FGS
  notification. The explicit cancel of ID 1 (for the fallback) makes the
  Robolectric tests see the device result.

## What the PO's Galaxy will show (probe checklist, 09 §4)

- **Flag-on rules** (Android 16 QPR1 behaviour): promotion is allowed after
  install. The plain Live Update is promoted: status-bar chip and lock
  screen. The status row „Live-Benachrichtigungen" opens the promotion page,
  where the PO can switch it off and on.
- **Flag-off rules** (first Android 16 release): promotion is off and AOSP
  has neither a Live Update UI nor a promotion page. The row falls back to
  the app's notification settings. The colorized (matcha) Live Update is
  posted, so if One UI offers its own switch („Live-Benachrichtigungen"),
  turning it on promotes it at once. Whether One UI shows a chip then is what
  the probe finds out (D-040: Now Bar best effort; chip and lock screen are
  checklist item 3).
- **Full-screen alert:** with the screen off or locked, the screen turns on
  and „Jetzt abgießen!" is shown over the lock screen. While the phone is in
  use it is a heads-up. This needs „Vollbild-Benachrichtigungen" allowed
  (status row). During the alert the Live Update chip is gone, because the
  alert has replaced it.

## Proposed spec changes (for the orchestrator to record)

- **04 §8.1, `brew_timer` row, Purpose:** „Live Update / ongoing (not while
  ALERTING, §8.2)".
- **04 §8.2, after „Notification IDs: …":** „While ALERTING the alert (ID 2)
  is the app's only notification of importance DEFAULT or higher: it
  replaces the Live Update as the foreground-service notification, and a Live
  Update posted without the service is cancelled first. The Live Update
  returns with the next state. Reason: Android 16 force-groups two ungrouped
  notifications of an app in the same importance section
  (`config_autoGroupAtCount` = 2) and marks them `FLAG_SILENT`; System UI
  then suppresses the full-screen intent and the heads-up
  (`docs/plans/M1-android16-api-notes.md`)."
- **04 §8.3, second bullet, replace „The system promotes it only if …":**
  „On Android 16 the request is always set, also while the user does not
  allow promotion, so the system promotes the posted notification as soon as
  the user allows it. The system promotes it only if it has promotable
  characteristics (`hasPromotableCharacteristics()`, whose rules differ
  between Android 16 builds: colorized before QPR1, not colorized from QPR1)
  and the user allows it; the resulting flag is `FLAG_PROMOTED_ONGOING`
  (0x40000)."
- **04 §8.3, fallback bullet:** „If promotion is not allowed
  (`canPostPromotedNotifications()` false), the system shows it as a normal
  ongoing notification (the request stays set) and the reliability banner
  deep-links to … (D-043)."
- **04 §13 and 06 §5.7, `POST_PROMOTED_NOTIFICATIONS` comment:** „not in the
  SDK 36 stubs; from Android 16 QPR1 a normal|appop permission that promotion
  requires (granted at install, the user's Live Update toggle sets the
  appop). Must stay declared."
- **D-043, third bullet:** amend „harmless, forward compatible" to „required
  from Android 16 QPR1, where promotion is the `POST_PROMOTED_NOTIFICATIONS`
  permission plus its appop (allowed by default); on the first Android 16
  release promotion is a per-app NotificationManagerService preference that
  is off by default."

## Alert player (#16) and what M1-10 extends

The probe plays the alert through `RingerModeAlertPlayer`
(`core:timer`, package `com.umamit.core.timer.alert`), built by
`AlertPlayers.ringerMode(context)` in `ProbeTimerService.createAlertPlayer()`.

- **Pure logic, injected seams:** `AlertEnvironment` (ringer mode, call state,
  DND), `AlertSoundOutput` (play/stop, audio focus), `AlertHaptics` and
  `AlertScheduler`; the settings come from a `() -> AlertSettings` lambda.
  The Android adapters in `AndroidAlertOutputs.kt` hold no decisions:
  `AudioManager.ringerMode` / `.mode`, `NotificationManager.currentInterruptionFilter`,
  `MediaPlayer.create(context, resId, attributes, sessionId)` with
  `USAGE_NOTIFICATION_EVENT` + `CONTENT_TYPE_SONIFICATION`,
  `AudioFocusRequest(AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)`,
  `VibratorManager.defaultVibrator.vibrate(createWaveform(…, -1), VibrationAttributes.createForUsage(USAGE_NOTIFICATION))`.
- **Matrix and rhythm:** `AlertMatrix` (04 §9.1). The alert runs in cycles of
  the tone length (Klangschale 3.5 s; at least pattern + 1 s); every cycle
  re-reads the conditions, plays the tone and starts the vibration pattern.
  Normal = 4 cycles (0–14 s).
- **Sounds:** `scripts/sounds/generate.sh` writes `core:content`
  `res/raw/klangschale.ogg` and `tick.ogg`; `AlertSound.lengthMs` must match
  the file (`BundledSoundsTest`).

M1-10 extends this rather than replacing it:

1. Add the four other tones to `AlertSound` and `AlertSound.rawRes`, and their
   synthesis to `generate.sh` (same loudness rules); the test covers every
   entry.
2. Feed `AlertSettings` from DataStore (SET-11 vibration, SET-14 length, tone
   picker) through the `settings` lambda; `BrewTimerService` builds the player
   the same way.
3. Wire *Alarm testen* (SET-31) to `startAlert()` / `stopAlert()`.
4. Keep the Robolectric matrix tests; add any new seam as a fake.
