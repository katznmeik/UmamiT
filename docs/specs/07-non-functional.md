# 07 · Non-Functional Requirements

> Status: **Draft v0.1** · Last updated: 2026-10-21

IDs: `NFR-<area>-NN`. Reference device for performance budgets: a mid-range
phone from ~2023 (e.g. Pixel 7a / Galaxy A54 class) unless noted. "Low-end"
means a device with 3–4 GB RAM running Android 10–12.

## 1. Summary

| Area | Headline target |
|---|---|
| Performance | Cold start ≤ 1.0 s TTID; smooth 60/120 Hz scrolling |
| Timer | Alert within 1 s of deadline (p95 ≤ 250 ms) |
| Reliability | ≥ 99.5 % crash-free users; < 0.2 % ANR |
| Accessibility | WCAG 2.2 AA-equivalent; full TalkBack support; 200 % font |
| Privacy | No network, no data collection, EXIF stripped |
| Size | Download ≤ 15 MB |
| Compatibility | Android 8.0+ phones, tablets, foldables |

## 2. Performance

| ID | Requirement |
|---|---|
| NFR-PERF-01 | Cold start time-to-initial-display ≤ 1.0 s and time-to-full-display (Home with data) ≤ 1.5 s on the reference device (Macrobenchmark median, with Baseline Profile). Low-end: ≤ 2.0 s / 3.0 s. |
| NFR-PERF-02 | Warm start ≤ 400 ms on reference device. |
| NFR-PERF-03 | Scrolling Collection (1,000 teas) and Journal (20,000 sessions): < 1 % janky frames, p95 frame time < 16 ms (60 Hz) on reference device. |
| NFR-PERF-04 | Screen transitions start within 100 ms of tap; local data appears within 300 ms (no loading spinners for normal datasets). |
| NFR-PERF-05 | Search results update within 100 ms after the debounce for the stress dataset. |
| NFR-PERF-06 | Insights screen computes within 500 ms for 20,000 sessions (computed off the main thread, cached per range). |
| NFR-PERF-07 | **Timer accuracy:** for steps < 30 min, alert fires within 1 s of the deadline (p95 ≤ 250 ms), screen on or off, app foreground or background, on all devices of the test matrix. Displayed remaining time never deviates more than 100 ms from the monotonic deadline. |
| NFR-PERF-08 | Timer screen renders at full frame rate while running; ring animation runs only while visible. |
| NFR-PERF-09 | Backup of 2,000 photos (≈ 1 GB) completes in ≤ 2 min on reference device with visible progress; does not block the UI; cancellable. |
| NFR-PERF-10 | Photo processing ≤ 1.5 s per 12 MP image on reference device. |

## 3. Reliability & data integrity

| ID | Requirement |
|---|---|
| NFR-REL-01 | Crash-free users ≥ 99.5 %, ANR rate < 0.2 % (Play vitals, 28-day). |
| NFR-REL-02 | No data loss: every user-confirmed write is committed to the database before the UI confirms success. Multi-table changes are transactional. |
| NFR-REL-03 | Timer state survives process death and reboot (TMR-16, [04 §7](04-brew-timer.md#7-persistence--recovery)). |
| NFR-REL-04 | Database migrations are tested from every released schema version; a failed migration must never silently wipe data. |
| NFR-REL-05 | Restore is atomic (BAK-02 AC3) and preceded by a safety backup. |
| NFR-REL-06 | SQLite runs in WAL mode; database integrity check (`PRAGMA quick_check`) is run before creating a backup; a failure blocks backup with an explanatory message. |
| NFR-REL-07 | Low storage: writes that fail due to full storage show "Your phone is out of space" and leave data consistent. |

## 4. Accessibility

| ID | Requirement |
|---|---|
| NFR-A11Y-01 | All functionality is operable with TalkBack and Switch Access; logical focus order; headings marked as headings. |
| NFR-A11Y-02 | Text contrast ≥ 4.5:1 (normal) and ≥ 3:1 (large ≥ 18 sp or 14 sp bold, and non-text UI components) in light, dark and the timer's Steeping scheme. |
| NFR-A11Y-03 | Supports font scale up to 200 % and the largest display size without clipped or overlapping essential content; text fields grow; no fixed-height text containers. |
| NFR-A11Y-04 | Touch targets ≥ 48 × 48 dp; timer primary action ≥ 72 dp high and ≥ 50 % of screen width. |
| NFR-A11Y-05 | Information is never conveyed by colour alone (category chips have labels; stock/freshness chips have text). |
| NFR-A11Y-06 | **Timer announcements:** TalkBack announces the state ("Infusion 2 of 4, steeping, 1 minute 20 seconds remaining") on focus and on state changes; live-region (polite) announcements at 10 s remaining; assertive announcement "Pour now" at alert. No per-second announcements. |
| NFR-A11Y-07 | Custom controls expose semantics: rating (adjustable, "4.5 of 5 stars", increment/decrement actions), taste sliders (range info), timer ring (progress range info), radar chart (summary + table alternative). |
| NFR-A11Y-08 | Animations honour the system "Remove animations" setting; no flashing > 3 times per second. |
| NFR-A11Y-09 | Alerts are multi-modal: sound + vibration + visual; users with hearing loss can rely on vibration and heads-up notification; users with visual impairments on sound + TalkBack. |
| NFR-A11Y-10 | Accessibility Scanner shows no errors on any screen; Compose UI tests include semantics assertions for critical controls. |
| NFR-A11Y-11 | Reading content (guides) supports text scaling and has a max line length for readability on large screens. |

## 5. Localisation & internationalisation

| ID | Requirement |
|---|---|
| NFR-I18N-01 | Launch language: English (en). Candidate additional languages: Japanese, German (Q-03). |
| NFR-I18N-02 | 100 % of user-facing strings in resources, with translator context comments; no string concatenation for sentences (use placeholders). |
| NFR-I18N-03 | Plurals, number, date, time and currency formatting are locale-aware. 12/24-hour time follows the device setting. |
| NFR-I18N-04 | Layouts handle +40 % text expansion and RTL. |
| NFR-I18N-05 | Bundled content (styles, descriptors, guides) is structured for translation (per-locale asset files with English fallback). |
| NFR-I18N-06 | Unit defaults derived from locale (ONB-02) but always user-changeable. |
| NFR-I18N-07 | Japanese terms in English UI are written in Hepburn romanisation with macrons where standard (kōridashi), plus kanji in secondary names. |

## 6. Privacy

| ID | Requirement |
|---|---|
| NFR-PRIV-01 | The app declares no `INTERNET` permission and makes no network requests (GEN-01). |
| NFR-PRIV-02 | No analytics, advertising, attribution, or crash-reporting SDKs. Play Console vitals are the only diagnostics. |
| NFR-PRIV-03 | Google Play Data safety form: "No data collected" and "No data shared". |
| NFR-PRIV-04 | Photos: EXIF metadata (including GPS location) is removed on import; originals are not retained. |
| NFR-PRIV-05 | No permissions beyond those listed in [04 §13](04-brew-timer.md#13-manifest-declarations-timer-related); no storage, camera, location, contacts or microphone permissions. |
| NFR-PRIV-06 | Backup files are unencrypted ZIPs; SCR-26 states this clearly ("Anyone with the file can read your journal"). Optional password-encrypted backups are a post-1.0 idea. |
| NFR-PRIV-07 | Android Auto Backup includes the database and settings only (no photos); users can disable it via system settings. Rules in `data_extraction_rules.xml` (API 31+) and `backup_rules.xml` (API ≤ 30). |
| NFR-PRIV-08 | A privacy policy is bundled in the app (SCR-27) and published at a public URL for the Play listing. |
| NFR-PRIV-09 | Feedback is sent only via the user's e-mail app (`ACTION_SENDTO mailto:`) with an optional, user-visible diagnostics block (app version, Android version, device model) that the user can edit before sending. |

## 7. Security

| ID | Requirement |
|---|---|
| NFR-SEC-01 | App components are `exported="false"` except the launcher activity, the boot receiver, and future widget / Quick Settings tile components. Exported library components must be protected by a system permission (e.g. WorkManager's diagnostics and profileinstaller receivers use `DUMP`). |
| NFR-SEC-02 | `PendingIntent`s are `FLAG_IMMUTABLE` unless mutability is required, and always explicit. |
| NFR-SEC-03 | `FileProvider` exposes only `cacheDir/capture/` and `cacheDir/share/`; shared files are deleted after use (or on next start). |
| NFR-SEC-04 | Backup import is treated as untrusted input: zip-slip protection, size and count limits, schema & constraint validation, image decoding with bounds ([05 §8.4](05-data-model.md#84-validation-on-restore)). |
| NFR-SEC-05 | No dynamic code loading, no WebView, no JavaScript. |
| NFR-SEC-06 | Release builds are minified (R8), not debuggable, and `android:allowBackup` / data extraction rules configured as in NFR-PRIV-07. |
| NFR-SEC-07 | Dependencies scanned for known vulnerabilities (GitHub Dependabot alerts). |
| NFR-SEC-08 | Signing keys never in the repository; CI uses encrypted secrets; Play App Signing holds the app signing key. |

## 8. Compatibility & device matrix

| ID | Requirement |
|---|---|
| NFR-COMP-01 | Android 8.0 (API 26) through the latest Android release; tested on the oldest and newest. |
| NFR-COMP-02 | Phones, tablets, foldables, ChromeOS devices with Play Store; portrait and landscape; multi-window; no orientation lock. |
| NFR-COMP-03 | Display sizes from 320 dp width (small phones) to 1280+ dp (tablets landscape). |
| NFR-COMP-04 | Works with gesture and 3-button navigation, display cutouts, and edge-to-edge. |
| NFR-COMP-05 | Light and dark system themes; high-contrast text setting. |

**Manual test matrix (release candidates):**

| Device class | Example | Android | Focus |
|---|---|---|---|
| Google flagship | Pixel 9/10 | Latest | Baseline, new platform features |
| Google mid-range | Pixel 7a | 15–16 | Performance reference |
| Samsung | Galaxy A-series & S-series | One UI current | OEM battery management, timer reliability |
| Xiaomi / Redmi | Redmi Note series | HyperOS | Aggressive background killing |
| Oppo / OnePlus | OnePlus Nord | ColorOS / OxygenOS | Background restrictions |
| Old / low-end | Any 3 GB RAM device | 8.0–10 | Min SDK, performance |
| Tablet | Galaxy Tab / Pixel Tablet | Recent | Adaptive layouts |
| Foldable | Galaxy Z Fold / Pixel Fold | Recent | Posture, continuity |
| Emulators | API 26, 30, 34, latest | — | Automated tests |

## 9. Battery & resource usage

| ID | Requirement |
|---|---|
| NFR-BAT-01 | No background work outside: active brew sessions, scheduled long-step alarms, and WorkManager jobs listed in [06 §5.4](06-architecture.md#54-background-work-workmanager). |
| NFR-BAT-02 | Partial wake locks only during short running steps, always with timeout ([04 §6.2](04-brew-timer.md#62-short-steps-foreground-service)); total wake-lock time well below Android vitals' excessive wake-lock threshold. |
| NFR-BAT-03 | A 15-minute session with the screen off consumes < 1 % battery on the reference device. |
| NFR-BAT-04 | With the timer screen visible and "keep screen on", the UI avoids unnecessary recomposition (only the time text and ring redraw). |
| NFR-BAT-05 | Memory: no leaks (LeakCanary clean in debug E2E runs); peak heap < 150 MB during photo processing. |

## 10. Size

| ID | Requirement |
|---|---|
| NFR-SIZE-01 | Play download size ≤ 15 MB for a typical phone configuration. |
| NFR-SIZE-02 | Bundled imagery ≤ 4 MB total (WebP/vector); fonts ≤ 600 KB (subset); sounds ≤ 300 KB. |
| NFR-SIZE-03 | Installed size (excluding user photos and caches) ≤ 50 MB. Coil disk cache capped at 100 MB. |

## 11. Maintainability

| ID | Requirement |
|---|---|
| NFR-MAINT-01 | Code style, static analysis and test coverage as per [06 §7](06-architecture.md#7-quality-gates). |
| NFR-MAINT-02 | Specifications in `docs/specs/` are updated in the same PR as behavioural changes. |
| NFR-MAINT-03 | Architecture decisions are recorded in [10](10-open-questions-and-decisions.md). |
| NFR-MAINT-04 | Bundled content is data (JSON/Markdown), editable without code changes. |

## 12. Store & policy compliance

| ID | Requirement |
|---|---|
| NFR-POL-01 | Target API level meets Google Play's current requirement at every release. |
| NFR-POL-02 | Foreground service type `specialUse` declared and justified in Play Console with a demo video ([04 §6.2](04-brew-timer.md#62-short-steps-foreground-service)). |
| NFR-POL-03 | No health or medical claims in the app, store listing or guides (e.g. about caffeine, L-theanine or "detox"). |
| NFR-POL-04 | Content rating questionnaire: suitable for all ages (IARC). Target audience: adults/teens (not designed for children; avoid Families policy obligations). |
| NFR-POL-05 | Store listing assets (icon 512 px, feature graphic 1024 × 500, phone/tablet screenshots) follow the brand design system. |
| NFR-POL-06 | All third-party assets (fonts, photos, illustrations, sounds) are licensed for commercial redistribution, with credits in About. |
