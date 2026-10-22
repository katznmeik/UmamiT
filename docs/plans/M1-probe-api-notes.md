# M1 probe: Android 16 API notes (for M1-11)

Recorded in M1-03a (#15). These names were checked with `javap` against the
compile SDK (`platforms/android-36/android.jar`, Platform 16 rev 2) and
AndroidX Core 1.18.0, and against the Android 16 framework that Robolectric
4.17 runs (`android-all-instrumented:16-robolectric-13921718`). The probe
release checks how the PO's Galaxy behaves (09 §4); add those results here.

## What the probe uses

| Purpose | API used (code in `core:timer`) |
|---|---|
| Request promotion (Live Update) | `NotificationCompat.Builder.setRequestPromotedOngoing(true)`. It writes the extra `NotificationCompat.EXTRA_REQUEST_PROMOTED_ONGOING` = `"android.requestPromotedOngoing"`. Read back with `NotificationCompat.isRequestPromotedOngoing(n)`. |
| May the app promote? | `NotificationManagerCompat.canPostPromotedNotifications()` (platform: `NotificationManager.canPostPromotedNotifications()`, API 36; the compat version returns false below 36) |
| Is the notification promotable? | `Notification.hasPromotableCharacteristics()` (API 36; compat: `NotificationCompat.hasPromotableCharacteristics(n)`) |
| Was it promoted? | `Notification.FLAG_PROMOTED_ONGOING` = `0x00040000` (262144), set by the system. `Notification.flagsToString` prints it as `PROMOTED_ONGOING` |
| Progress bar with segments and points | `NotificationCompat.ProgressStyle` with `Segment(length).setColor()`, `Point(position).setColor()`, `setProgress()` and `setStyledByProgress(true)`. On API 36 it builds the platform `Notification.ProgressStyle`; below that it falls back to a plain `setProgress(max, progress)` bar |
| Status-bar chip while running | `setUsesChronometer(true)`, `setChronometerCountDown(true)`, `setWhen(deadlineWall)`, `setShowWhen(true)` |
| Status-bar chip otherwise | `NotificationCompat.Builder.setShortCriticalText(…)` (platform `Notification.Builder.setShortCriticalText`, API 36; extra `"android.shortCriticalText"`) |
| Promotion settings page | `Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS` (`"android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS"`). `ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS` in 04 §8.3 does not exist |
| Full-screen intent allowed? | `NotificationManagerCompat.canUseFullScreenIntent()`; settings page `Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` |
| Exact alarms | `AlarmManager.setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP, …)`, `canScheduleExactAlarms()`. Lint's `MissingPermission` check only knows `SCHEDULE_EXACT_ALARM`, so the call carries `@SuppressLint("MissingPermission")` (the app holds `USE_EXACT_ALARM`, D-013) |
| Foreground service | `ServiceCompat.startForeground(service, 1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)` with `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` in the manifest (04 §13) |

## Not in the SDK 36 stubs

- `Notification.Builder.setRequestPromotedOngoing` and
  `Notification.EXTRA_REQUEST_PROMOTED_ONGOING`: use the AndroidX builder,
  which writes the same extra.
- `android.permission.POST_PROMOTED_NOTIFICATIONS` is not a public
  permission in the stubs. The manifests declare it anyway (06 §5.7); this is
  harmless.

## Promotion rules differ between Android 16 builds

In the Android 16 framework that Robolectric ships,
`hasPromotableCharacteristics()` is:

> ongoing **and** not a group summary **and** no custom views **and** a title
> **and** (an ongoing `CallStyle` **or** (colorized **and** a style of
> `BigTextStyle` / `CallStyle` / `ProgressStyle`)).

The extra `android.requestPromotedOngoing` is not read there, and a
notification that is **not** colorized is never promotable. The published
Live Update rules for later builds are the other way round: promotion needs
the request extra, and colorized notifications are not promoted.

So `LiveUpdateNotification.build` first builds the plain (not colorized)
Live Update. On API 36, if `hasPromotableCharacteristics()` is false, it
builds a colorized variant (`setColorized(true)`, matcha background; allowed
because it is the FGS notification) and uses that one if the platform finds
it promotable. Logcat tag `UmamiTLiveUpdate` says which variant was used.
M1-11 keeps this fallback unless the probe on the Galaxy shows that one
variant is enough.

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
