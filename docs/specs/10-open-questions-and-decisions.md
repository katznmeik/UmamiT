# 10 · Open Questions & Decision Log

> Status: **Living document** · Last updated: 2026-10-21

## Open questions

| ID | Question | Proposed default | Needed by |
|---|---|---|---|
| Q-01 | Final **application ID**? It must never change, or updates can't install over the old app. | `com.umamitea.app` | M1 |
| Q-04 | Source of the **ink-wash illustrations** and logo (the mockup's images may not be licensed for reuse)? | Simple original illustrations created for the app (vector), logo redrawn from the mockup | M5 (logo: M1) |
| Q-05 | **Serif display font**? | Cormorant Garamond (OFL) | M1 |
| Q-11 | Is the GitHub repository **private**? (Relevant for parser test fixtures excerpted from the shop.) | Keep private | M2 |
| Q-15 | Default **alert sound**: is „Klangschale" (singing bowl) right? | Yes | M1 |
| Q-18 | Do you use a **Galaxy Watch**? (Decides whether NOT-04 is tested.) | — | M1 |

### Resolved questions

| ID | Resolution (2026-10-21) |
|---|---|
| Q-02 Monetisation | Not applicable: personal use only. |
| Q-03 Languages | German only (D-017). |
| Q-06 Content review | Tee-Kontor is the content source; built-in styles are fallbacks only. |
| Q-07 Navigation | Start · Journal · Sammlung · Statistik (D-018). |
| Q-08 Tablet layouts | Phone-first; wider layouts best effort. |
| Q-09 Play account | Not applicable: no store (D-013). |
| Q-10 Min Android | Android 15 / API 35 (D-023). |
| Q-12 Sample teas | Dropped; catalog replaces them. |
| Q-13 Brand mockup | Added at `docs/design/brand-mockup.png`. |
| Q-14 Support/privacy policy hosting | Not applicable. |

## Decision log

Status: *Accepted* (by the PO), *Proposed* (default unless the PO objects),
*Superseded*.

<a id="d-001"></a>
### D-001 — No accounts; personal data local only · *Accepted*, amended by D-014

Originally "offline only, no accounts" (2026-10-21). Still true for all
personal data; the catalog refresh is the one network exception (D-014).

<a id="d-002"></a>
### D-002 — Concept: tea brewing companion · *Accepted*

Collection + brewing guidance + steep timer + journal, umami-focused.

<a id="d-003"></a>
### D-003 — Kotlin, Jetpack Compose, Material 3 · *Proposed*

Min SDK changed by D-023.

<a id="d-004"></a>
### D-004 — Timer background strategy · *Superseded by D-019 / [04 §6](04-brew-timer.md#6-background-execution)*

v0.1 avoided exact-alarm permissions because of Play policy. As a sideloaded
personal app (D-013), v0.2 uses `USE_EXACT_ALARM` + foreground service +
battery-optimisation exemption together.

<a id="d-005"></a>
### D-005 — Room + DataStore, UUIDs, metric storage · *Proposed*

<a id="d-006"></a>
### D-006 — No analytics or tracking SDKs · *Proposed* (network part superseded by D-014)

<a id="d-007"></a>
### D-007 — Navigation Compose, type-safe routes · *Proposed*

<a id="d-008"></a>
### D-008 — Guides rendered from Markdown · *Proposed* (now Tee-Info guides converted to Markdown)

<a id="d-009"></a>
### D-009 — Brand theme by default, dynamic colour opt-in · *Proposed*

<a id="d-010"></a>
### D-010 — Stats inside Journal · *Superseded by D-018*

<a id="d-011"></a>
### D-011 — Photo handling · *Proposed*

Photo picker/camera intent, resized, EXIF stripped, app-private storage;
excluded from device backup; own photos included in manual export.

<a id="d-012"></a>
### D-012 — Timer screen always dark · *Proposed*

<a id="d-013"></a>
### D-013 — Personal app, sideloaded, no store · *Accepted* (PO, 2026-10-21)

- No Play Store or other store; one user, one device (Samsung Galaxy,
  Android 16 / One UI 8).
- Consequences: no store policies, listings or beta. The app may use
  `USE_EXACT_ALARM`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` and a
  `specialUse` foreground service without review.

<a id="d-014"></a>
### D-014 — In-app catalog refresh over the internet · *Accepted* (PO, 2026-10-21)

- The app declares `INTERNET` and fetches tee-kontor-kiel.de when the user
  taps *Katalog aktualisieren*.
- Alternatives rejected: bundling the catalog only in each APK (needs a
  developer for every refresh), one-time snapshot.
- Consequences: parser maintenance when the shop changes; host allow-list;
  atomic refresh with sanity checks.

<a id="d-015"></a>
### D-015 — Tee-Kontor catalog scope and independence · *Accepted* (PO, 2026-10-21)

- All green teas incl. flavoured teas, matcha and powders; one entry per tea;
  product photos and *Tee-Info* guides imported.
- Catalog lives in *Sammlung › Katalog*; adding a tea creates an independent
  copy; refresh updates only the catalog; manual *Aus Katalog zurücksetzen*.
- Adding asks for the package size; stock and price are prefilled.

<a id="d-016"></a>
### D-016 — Handling of scraped content · *Proposed*

Scraped texts and photos are stored only on the phone for personal use and are
not committed to the repository, except minimal HTML excerpts needed as
parser test fixtures (repository kept private, Q-11). No bundled snapshot
unless generated at build time and not committed (CAT-14).

<a id="d-017"></a>
### D-017 — German only, metric · *Accepted* (PO, 2026-10-21)

German UI and content; °C, g, ml; German formats.

<a id="d-018"></a>
### D-018 — Navigation: Start · Journal · Sammlung · Statistik · *Accepted* (PO, 2026-10-21)

As in the brand mockup. Learn tab and own guides/tools dropped; Tee-Kontor
guides live in the catalog.

<a id="d-019"></a>
### D-019 — Timer behaviour · *Accepted* (PO, 2026-10-21)

- Supports all styles (kyusu, mug, gongfu, cold brew).
- READY always waits for the user's tap between infusions.
- Running controls: Pause only (others in the overflow menu); no ±10 s.
- Pre-alert tick before the end: global setting (Aus, 5–30 s in 5 s steps;
  default 5 s), overridable per recipe.
- Alert follows the ringer mode (sound / vibration / silent), repeats ~15 s.
- Live Update on lock screen, status bar and Now Bar.
- One timer at a time.
- Samsung reliability: exact alarms, FGS, battery exemption (see D-013).

<a id="d-020"></a>
### D-020 — Automatic journaling · *Accepted* (PO, 2026-10-21)

Sessions are logged automatically without a summary screen; a short sheet
offers *Notizen hinzufügen* and, if times changed, *Zeiten ins Rezept
übernehmen* (ask, never automatic).

<a id="d-021"></a>
### D-021 — Recipe derivation from Tee-Kontor data · *Accepted* (PO, 2026-10-21)

Ranges → midpoint; temperature snapped to 5 °C, time to 5 s; brewing table
wins over prose, prose over style defaults; non-table recipes flagged
„Rezept prüfen". Details: [08 §2.5](08-content.md#25-recipe-derivation).

<a id="d-022"></a>
### D-022 — Backup via Android device backup · *Accepted* (PO, 2026-10-21)

Database and settings in device backup; manual export file as extra safety;
photos only in the manual export.

<a id="d-023"></a>
### D-023 — Min SDK 35, target SDK 36 · *Proposed*

PO's phone runs Android 16; one version of slack.

<a id="d-024"></a>
### D-024 — Delivery: Claude builds, PR per milestone, GitHub Releases · *Accepted* (PO, 2026-10-21)

- Claude develops in cloud sessions; one PR per milestone into `main`.
- Signed APK published on GitHub Releases per milestone; PO installs on the
  phone.
- Timer first (M1).
- **No development starts before the PO's explicit go.**

<a id="d-025"></a>
### D-025 — Temperatures in 5 °C steps; no cooling helper · *Accepted* (PO, 2026-10-21)

The PO's kettle is adjustable in 5 °C steps; the timer shows a kettle hint per
step.

## Change log

| Date | Change |
|---|---|
| 2026-10-21 | v0.1: initial specification set; D-001, D-002 accepted; brand mockup incorporated. |
| 2026-10-21 | Brand mockup committed; Q-13 resolved. |
| 2026-10-21 | v0.2: PO interview — personal sideloaded app, German, Samsung/Android 16, Tee-Kontor catalog with in-app refresh, timer details (pre-alert, ringer-mode alert, Live Update), auto-journal, Stats tab, delivery plan. D-013 … D-025 added; D-004, D-010 superseded; D-001, D-006 amended. |
