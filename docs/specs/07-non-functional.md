# 07 · Non-Functional Requirements

> Status: **Draft v0.2** · Last updated: 2026-10-21
> v0.2: single user, single device (Samsung Galaxy, Android 16 / One UI 8),
> no store; catalog network access.

Reference device: **the PO's Samsung Galaxy (Android 16 / One UI 8)**.
Automated instrumented checks run in the CI emulator job on GitHub Actions
(API 35 and 36); the cloud container has no emulator ([06 §7](06-architecture.md#7-quality-gates)).

## 1. Summary

| Area | Target |
|---|---|
| Timer | Pre-alert and alert within 1 s of target (p95 ≤ 250 ms) |
| Performance | Cold start to Home ≤ 1 s; smooth scrolling |
| Reliability | No data loss; no crashes during sessions; missed alerts = 0 |
| Accessibility | Large fonts, TalkBack basics, contrast AA |
| Privacy | Personal data never leaves the phone; network only to tee-kontor-kiel.de (three uses, GEN-01) |
| Size | APK ≤ 20 MB |

## 2. Performance

| ID | Requirement |
|---|---|
| NFR-PERF-01 | Cold start to interactive Home ≤ 1.0 s on the reference device (with Baseline Profile). |
| NFR-PERF-03 | Scrolling Journal (10,000 sessions), Meine Tees and Katalog (500 entries with photos): no visible jank; p95 frame time within the display's frame budget. |
| NFR-PERF-04 | Local data appears within 300 ms of navigation. |
| NFR-PERF-05 | Search results update within 100 ms after debounce. |
| NFR-PERF-06 | Statistics compute within 500 ms for 10,000 sessions. |
| NFR-PERF-07 | **Timer accuracy:** pre-alert and alert within 1 s of target (p95 ≤ 250 ms), screen on or off, app foreground, background or killed. Displayed time never deviates > 100 ms from the deadline. |
| NFR-PERF-08 | Timer screen animates smoothly while visible; no animation work when not visible. |
| NFR-PERF-11 | Catalog refresh on Wi-Fi without blocking the UI: first refresh ≤ 20 min (D-076; expected ≈ 15–16 min, since parsing and re-encoding overlap the request gaps, D-091), later refreshes ≤ 5 min (rate-limited to 2 req/s, [08 §2.4](08-content.md#24-discovery-variants-and-request-budget)). |

## 3. Reliability & data integrity

| ID | Requirement |
|---|---|
| NFR-REL-01 | Zero crashes in timer flows during acceptance; any crash is a release blocker. |
| NFR-REL-02 | Confirmed writes are committed before UI success; multi-table changes are transactional. |
| NFR-REL-03 | Timer survives process death and reboot ([04 §7](04-brew-timer.md#7-persistence--recovery)). |
| NFR-REL-04 | Every schema migration tested; no destructive migrations of personal data. |
| NFR-REL-05 | The catalog refresh is atomic; a failure leaves the previous catalog intact. (In-app restore is post-1.0, BAK-02.) |
| NFR-REL-06 | Network failures, timeouts and shop-structure changes during refresh never corrupt or delete the existing catalog and never affect personal data. |
| NFR-REL-07 | A local diagnostic log (timer, alarms, catalog) is available for bug reports ([06 §5.6](06-architecture.md#56-error-handling--logging)). |

## 4. Accessibility

| ID | Requirement |
|---|---|
| NFR-A11Y-02 | Text contrast ≥ 4.5:1 (normal) / ≥ 3:1 (large, UI components) in light, dark and timer schemes. |
| NFR-A11Y-03 | Usable at 200 % font scale without losing essential information. |
| NFR-A11Y-04 | Touch targets ≥ 48 dp; timer primary button ≥ 72 dp high. |
| NFR-A11Y-05 | Information never by colour alone. |
| NFR-A11Y-06 | TalkBack: German content descriptions; timer announces state changes, pre-alert, 10 s remaining and alert (no per-second announcements). |
| NFR-A11Y-08 | Honour "Remove animations"; no flashing. |
| NFR-A11Y-09 | Alerts are multi-modal (sound/vibration per ringer mode + full-screen alert that turns the screen on over the lock screen + Live Update), also visual-only in silent mode. |

## 5. Language & formats

| ID | Requirement |
|---|---|
| NFR-I18N-01 | German only; all strings in resources (no hard-coded text). |
| NFR-I18N-03 | German formats: decimal comma, „21. Okt. 2026", 24-hour time, „15,90 €". |
| NFR-I18N-07 | Japanese terms in Hepburn with macrons where standard; kanji as secondary names. |

## 6. Privacy & network

| ID | Requirement |
|---|---|
| NFR-PRIV-01 | Personal data (collection, journal, stock, settings, photos) is never transmitted. |
| NFR-PRIV-02 | The only network traffic goes to the allow-listed host `www.tee-kontor-kiel.de` for exactly three uses (GEN-01, D-036): the user-initiated catalog refresh, the one-time seed photo download at first start, and the exact price fetch when the user adds a package. No other background network activity. |
| NFR-PRIV-03 | No analytics, ads, crash reporting or tracking SDKs. |
| NFR-PRIV-04 | Own photos have EXIF (incl. GPS) removed. |
| NFR-PRIV-07 | Android device backup includes database and settings only (photos/catalog excluded). |
| NFR-PRIV-10 | Scraped Tee-Kontor content (texts, photos) is used for personal use only: it is stored on the phone, not redistributed, and never committed to the repository; tests use synthetic fixtures (D-016). |

## 7. Security

| ID | Requirement |
|---|---|
| NFR-SEC-01 | Components `exported="false"` except launcher and boot receiver (library components protected by system permissions are acceptable; the `MY_PACKAGE_REPLACED` receiver stays non-exported). |
| NFR-SEC-02 | Immutable, explicit `PendingIntent`s. |
| NFR-SEC-03 | `FileProvider` limited to capture/share cache folders. |
| NFR-SEC-04 | HTML parsing (and a future backup import, BAK-02) treat input as untrusted: size limits, zip-slip protection, HTML sanitised (no scripts, no remote content in rendered guides/descriptions; images only from local storage). |
| NFR-SEC-05 | No WebView; HTTPS only (cleartext disabled). |
| NFR-SEC-08 | Release keystore only in GitHub secrets and the PO's offline backup. |

## 8. Compatibility

| ID | Requirement |
|---|---|
| NFR-COMP-01 | Android 15–16+ (API 35+); primary: Samsung Galaxy on One UI 8. |
| NFR-COMP-02 | Portrait and landscape; split screen; Samsung DeX and foldables should not break layouts (best effort). |
| NFR-COMP-04 | Edge-to-edge, gesture and 3-button navigation. |
| NFR-COMP-05 | Light and dark system themes. |

## 9. Battery & resources

| ID | Requirement |
|---|---|
| NFR-BAT-01 | Background activity only for active sessions, scheduled exact alarms, the user-started catalog refresh and the daily maintenance/reminder workers. |
| NFR-BAT-02 | Wake locks only during short running steps and alerts, always with timeout. |
| NFR-BAT-03 | A 15-minute session with screen off uses < 1 % battery. |
| NFR-BAT-05 | No memory leaks (LeakCanary clean). |

## 10. Size

| ID | Requirement |
|---|---|
| NFR-SIZE-01 | Release APK ≤ 20 MB (no catalog photos bundled). |
| NFR-SIZE-03 | On-device storage: catalog photos ≤ ~40 MB; image cache capped at 100 MB. |

## 11. Maintainability

| ID | Requirement |
|---|---|
| NFR-MAINT-01 | Quality gates per [06 §7](06-architecture.md#7-quality-gates). |
| NFR-MAINT-02 | Specs are updated in the same PR as behavioural changes. |
| NFR-MAINT-04 | Parser selectors are isolated in one place with a `parserVersion`; a shop layout change should need changes only in `core:catalog-parser` and its fixtures. |
