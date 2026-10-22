# 10 · Open Questions & Decision Log

> Status: **Living document** · Last updated: 2026-10-21

## Open questions

| ID | Question | Proposed default | Needed by |
|---|---|---|---|
| — | None open. | — | — |

### Notes on Q-04 — illustration and imagery options

| Option | Quality | Effort / cost | Notes |
|---|---|---|---|
| **A. Commission an illustrator** (sumi-e / ink-wash artist) | Highest, unique, consistent | Highest (typically several hundred €; weeks) | Best if the look should be truly one-of-a-kind |
| **B. Licensed stock art** (e.g. Adobe Stock, Shutterstock, Creative Market ink-wash packs) | High, professional | Low–medium (per image or pack) | Check the licence covers use in an app; style consistency across images can vary |
| **C. AI-generated, curated and post-processed** (ChatGPT / similar, same session as the mockups) | High if curated: generate many, pick the best, colour-match to the palette, clean up, export light and dark variants | Low | Matches the mockup look exactly; fine for a personal app; check the tool's terms |
| **D. Public-domain museum art** (CC0 open-access collections, e.g. Met Museum, Rijksmuseum, Smithsonian: historical Japanese ink landscapes) | Very high, authentic | Low (curation and cropping) | Real historical artworks; need toning to the washi palette; fewer tea-specific motifs |
| **E. Generated in code** (Compose canvas "ink" mountains) | Medium | Medium (development time) | Tiny size and infinitely scalable, but less rich than painted art |

Recommendation: **C** for illustrations (fast, on-brand, matches the
mockup), optionally mixed with **D** for one or two hero landscapes;
logo redrawn as a clean vector from the mockup. Tea photos come from
Tee-Kontor (catalog) and the PO's own photos.

### Notes on Q-11 — public vs private repository

A public repository is fine **as long as no Tee-Kontor content is committed**
(D-016): parser tests use synthetic HTML fixtures that mimic the page
structure without the shop's texts or images. Other aspects:
- Signing keys are GitHub secrets and never visible, public or not.
- Public GitHub Releases mean anyone could download the APK. It contains no
  personal data and no shop content, so this is harmless.
- The specs and the brand mockup would be visible to anyone.
- Private repositories have a limited free GitHub Actions quota; public ones
  have unlimited minutes for standard runners.

### Resolved questions

| ID | Resolution (2026-10-21) |
|---|---|
| Q-04 Illustrations | Option C (AI-generated, curated), optionally with D (CC0 museum art) — D-029; asset list in `docs/design/assets.md`. |
| Q-11 Repository visibility | Public — D-030. |
| Q-01 Application ID | `com.umamit.app` (originally `com.umamitea.app`; changed with the rename, D-033). |
| Q-05 Serif font | Cormorant Garamond (accepted). |
| Q-18 Galaxy Watch | Not used; no watch support needed (NOT-04 → Won't). |
| Q-15 Default alert sound (2026-10-22) | Klangschale variant C and the bowl-like tick variant C, chosen by the PO after the probe — D-045. The M1 checklist still listens to all tones via *Alarm testen*. |
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
personal data; the network exceptions to the shop host are listed in D-014
and D-036.

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
  taps *Katalog aktualisieren* (the complete list of network uses is in
  D-036).
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
### D-016 — Handling of scraped content · *Accepted* (PO, 2026-10-21)

Scraped texts and photos are stored only on the phone for personal use and are
**never committed** to the repository. Parser tests use **synthetic HTML
fixtures** that reproduce the page structure (element classes, table layout)
with invented text. A manual "live parser check" task runs the parser against
the real site from a cloud session when needed. No bundled catalog snapshot
in the APK (CAT-14 is `Won't`, D-036): the APK is public.

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
  default 10 s since D-035), overridable per recipe.
- Alert follows the ringer mode (sound / vibration / silent), repeats ~15 s.
- Live Update on lock screen, status bar and Now Bar (Now Bar best effort,
  D-036).
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
photos only in the manual export. In-app restore is post-1.0 (D-036).

<a id="d-023"></a>
### D-023 — Min SDK 35, target SDK 36 · *Accepted* (PO, 2026-10-21)

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

<a id="d-029"></a>
### D-029 — Imagery: AI-generated and curated, optional CC0 museum art · *Accepted* (PO, 2026-10-21)

Illustrations are generated (ChatGPT) in one consistent style, curated and
post-processed (palette match, light/dark variants, WebP). Hero landscapes may
use public-domain (CC0) museum ink paintings instead. The logo is redrawn as a
vector in M1. Asset list and prompts: [`docs/design/assets.md`](../design/assets.md).

<a id="d-030"></a>
### D-030 — Public repository · *Accepted* (PO, 2026-10-21)

The repository is public. Consequences: no Tee-Kontor content is ever
committed (D-016, synthetic test fixtures); secrets only in GitHub Actions
secrets; the APK on GitHub Releases is publicly downloadable (contains no
personal data or shop content).

<a id="d-031"></a>
### D-031 — Grill round 2 decisions · *Accepted* (PO, 2026-10-21)

- Catalog refresh only on user request, on any connection (CAT-07 AC7).
- Discontinued catalog teas are hidden; kept only if owned (CAT-07 AC4).
- Empty teas stay in the collection, marked „Leer", sorted last (INV-05).
- Finishing early is a normal session (TMR-12).
- One poured infusion = one cup for cost and statistics (INV-08, INS-05).
- Manual tea entry stays comfortable (PO sometimes buys elsewhere).
- *Nochmal brühen*: 3 most recently brewed teas; tap → timer READY with the
  last parameters (BRW-11).
- Rinse steps never added automatically; matcha keeps the whisk timer.
- Screen dims while steeping (TMR-17, SET-25); streaks shown neutrally; theme
  follows the system.
- M1 ships the PO's two teas (Bio Sencha Tanabata, Bio Sencha Hisui) as seed
  data with 100 g stock each ([08 §2.5a](08-content.md#25a-m1-seed-teas-pos-current-teas)).

<a id="d-032"></a>
### D-032 — Grill round 3: seed teaware, waiting sessions, review process · *Accepted* (PO, 2026-10-21)

- Seed vessels: Kyusu 300 ml (brews 250 ml, home) and Becher mit Sieb 400 ml
  (work); seed recipes scaled to them (WARE-06, 08 §2.5a).
- *Nochmal brühen* keeps the last session's parameters even across vessels;
  switching via ⋮ *Brühen anpassen*.
- Westlich recipes with one infusion get a second (0,25 × the first duration,
  rounded to 5 s, +5 °C; set by the PO from experience).
- Leaf in grams with 0,1 g; Google device backup is on.
- Waiting sessions live until midnight; Live Update demoted after 30 min
  (TMR-14).
- Milestone done = on-device checklist + design review of rendered
  screenshots in the PR (09 §1).

<a id="d-033"></a>
### D-033 — App renamed to „UmamiT" · *Accepted* (PO, 2026-10-21)

- The app is called **UmamiT** everywhere (app name, wordmark, docs).
- Application ID `com.umamit.app`; backup extension `.umamit`; database
  `umamit.db`; user agent `UmamiT/<version>`.
- The GitHub repository was renamed to `katznmeik/UmamiT` by the PO on
  2026-10-21 (GitHub redirects the old `UmamiTea` URLs).
- The existing mockup images still show „UmamiTea"; the wordmark in the app
  and in new mockups is „UmamiT".

<a id="d-034"></a>
### D-034 — Grill round 4 · *Accepted* (PO, 2026-10-21)

- Silent ringer mode stays visual-only (no vibration override); the PO keeps
  the phone on vibrate or ring when brewing.
- Claude generates the release keystore and hands file and passwords to the
  PO, who stores them offline and adds the GitHub secrets.
- ~~Alert sounds come from free CC0 recordings, credited in About.~~
  *Amended by D-036:* the tones are synthesised first; the PO may replace
  any of them later with CC0 recordings (credited in About).
- New APKs are announced by Claude in the session/PR; no in-app update check.
- Seed teas are linked to the catalog and filled with description and photo on
  the first refresh (CAT-15).
- Starting a second tea keeps the confirmation dialog (TMR-21).
- Overtime ≥ 15 s is shown prominently (TMR-06).

<a id="d-035"></a>
### D-035 — Welcome text, no home greeting, 10 s pre-alert, signing process · *Accepted* (PO, 2026-10-21)

- Welcome page text: **„Aus Liebe zum Tee"**.
- The Start screen shows no greeting text (header illustration only).
- Pre-alert default **10 s** before the end of a step (SET-10). Steps shorter
  than N + 5 s get no pre-alert (15 s with the default 10 s).
- Signing: Claude generates the key, the backup file and the fingerprint
  check; the PO only saves the backup and adds two GitHub secrets
  ([06 §6.1](06-architecture.md#61-signing-process-d-035)).

<a id="d-046"></a>
### D-046 — Design system details from M1-04 · *Accepted* (Claude, 2026-10-22)

Settled while building the theme and the simple components (#29); the PO
reviews them with the screenshots and motion recordings.

- **Colour schemes:** generated once from `#4A5643` with material-color-utilities
  (tonal spot, spec 2021) and committed as constants (03 §3.1). Where 03 §3.1
  overrides a role, its twin follows: `onBackground` = `onSurface`,
  `surfaceTint` = `primary`. All other unlisted roles keep the generated value.
- **Fonts:** static instances of the Cormorant Garamond variable font; `lnum`
  and `tnum` survived subsetting, so the 03 §3.2 fallback is not used. Every
  serif style sets `lnum` (the font's default figures are old-style).
- **German text:** all text styles carry the German locale and automatic
  hyphenation, so long compounds break at syllables at font scale 2.0; ß in
  overlines is uppercased to ẞ.
- **Icons:** the Material Symbols (Apache 2.0) are the only third-party assets
  besides the font; their licence is committed next to the OFL.
- **Motion tokens:** 03 §3.6 „Motion tokens", with the reasons. Press feedback
  (scale 0.97, faster spring release) is added to pills and cards, not list
  rows; reduce motion keeps short fades.
- **Alert motion (motion review):** entering ALERTING is always instant
  (`alertEnter`); the pulse starts at full matcha; the pulse and the pre-alert
  glow ignore developer animator scales (`UnscaledMotion`), while scale 0 still
  means reduce motion.

<a id="d-045"></a>
### D-045 — Default alert sound and pre-alert tick (Q-15) · *Accepted* (PO, 2026-10-22)

After the probe (`v0.0.2-probe`), the PO found the draft Klangschale (440 Hz)
not bright enough and wanted the pre-alert tick to sound more like a singing
bowl. From a listening round of three variants each, the PO chose **C + C**:

- **Klangschale** „hell und klar": 880 Hz fundamental with the bowl partials,
  3.5 s, -16 LUFS. It stays the default alert (04 §9.2).
- **Tick** „warm lang": a softly struck small bowl at 990 Hz, about **0.8 s**
  (no longer a 90 ms wooden tick), -24 LUFS, 8 LU below the alert.

Both come from `scripts/sounds/generate.sh` and are byte-identical to the files
the PO heard. The other tones (Glocke, Holzblock, Glockenspiel, Sanft) follow
with SET-31 in M1.

<a id="d-044"></a>
### D-044 — Alert replaces the Live Update while ALERTING; promotion always requested · *Accepted* (Claude, 2026-10-22)

Root-caused at T4 from the CI emulator (API 36) and the AOSP Android 16
sources (details and source references: `docs/plans/M1-probe-api-notes.md`):

- **Full-screen alert:** Android 16 force-groups an app's ungrouped
  notifications once two share an importance section and marks them
  `FLAG_SILENT`; System UI then suppresses the full-screen intent and the
  heads-up. The Live Update (DEFAULT) and the alert (HIGH) were grouped, so
  the alert never turned the screen on (also on a real phone). **While
  ALERTING, the alert becomes the foreground notification and the Live Update
  is removed** (04 §8.1, §8.2). Android 15 groups too but has no silencing.
- **Promotion:** the probe requested promotion only while
  `canPostPromotedNotifications()` was true. The request is now **always**
  set on Android 16, so the system promotes as soon as the user allows it
  (04 §8.3). The promotability rules differ between Android 16 builds (flag
  `android.app.ui_rich_ongoing`): before QPR1 a per-app preference that is
  off by default and needs a colorized notification; from QPR1 the
  `POST_PROMOTED_NOTIFICATIONS` permission plus appop (allowed by default)
  and a non-colorized notification. The probe posts whichever variant the
  platform accepts. `POST_PROMOTED_NOTIFICATIONS` must stay declared
  (amends D-043).
- **Fix loop (D-040):** `v0.0.1-probe` was published before this fix and is
  superseded by `v0.0.2-probe`; the PO tests only the latter.

<a id="d-043"></a>
### D-043 — Android 16 Live Update API names corrected · *Accepted* (Claude, 2026-10-22)

Verified against the compileSdk 36 `android.jar` and AndroidX core 1.18 during
M1-03a (#14, #15); 04 §8.3, §13, 02 ONB-02, 03 and 06 §5.7 corrected:

- the settings page for Live Updates is
  `Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS` (with
  `EXTRA_APP_PACKAGE`); `ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS` does not
  exist;
- promotion is requested with AndroidX
  `NotificationCompat.Builder.setRequestPromotedOngoing(true)`; the platform
  builder has no such method;
- `android.permission.POST_PROMOTED_NOTIFICATIONS` is not a public SDK 36
  permission. It stays declared (*amended by D-044:* required from Android 16
  QPR1, see there); what counts is
  `NotificationManager.canPostPromotedNotifications()`, which the user
  controls.

<a id="d-042"></a>
### D-042 — Build tooling choices from M1-01/M1-02 · *Accepted* (Claude, 2026-10-22)

Technical decisions made while building the skeleton and the CI gate
(M1-01, M1-02); no PO-visible behaviour.

- **Detekt 2.0 alpha:** Detekt `2.0.0-alpha.6` (plugin `dev.detekt`)
  instead of 1.23.x. Detekt 1.23 embeds Kotlin 2.0 and cannot load the
  Compose rules (0.5+ need the Detekt 2 API; 0.4.x crash on Detekt 1.23's
  Kotlin runtime). Detekt 2 is built with Kotlin 2.4 like the app. Move to
  the stable 2.0 release when it ships.
- **compileSdk 36 limits library versions:** many current AndroidX releases
  require compileSdk 37. compileSdk stays pinned at 36 (D-023), so the
  catalog uses the newest versions that build against 36 (comment in
  `gradle/libs.versions.toml`).
- **Gradle configuration cache off:** it replays dependency-resolution
  failures (HTTP 429 from Maven Central in cloud sessions) instead of
  retrying. Gradle's network retries are raised instead.
- **Lint exceptions:** `MissingApplicationIcon` until M1-21 and
  `DataExtractionRules` until M1-06 (TODOs in `config/lint/lint.xml`).
  Network- and date-dependent checks are off. No baseline.
- **GitHub Actions:** both workflows use the same major versions
  (`checkout@v7`, `setup-java@v6`, `setup-gradle@v6` with the open-source
  `basic` cache provider).

<a id="d-041"></a>
### D-041 — Motion guardrails · *Accepted* (PO, 2026-10-21)

Grilled with the PO; details in 03 §3.6:

- **Style:** calm, ritual-like motion.
- **Signature moments:** functional motion plus exactly two of them, the
  liquor fill and the pre-alert glow. The liquor fill replaces the
  „optional" fill of v0.2.
- **Long steps:** coarse updates.
- **Alert:** deliberately strong but non-flashing surface pulse.
- **Reduce motion:** the system setting only; under it the alert is static
  and high-contrast.
- **Review:** the PO judges motion on the device (release candidates) and
  in short motion recordings. These are GIFs made with Roborazzi's
  experimental, record-only `recordRoboVideo`, committed next to the
  screenshots in `docs/design/review/<milestone>/`.
- **Details:** curves, durations, the fill direction and the pulse rhythm
  are worked out in the waves 2–3 spec with `/animate` and `/apple-design`.
- **Skills:** the design skills are part of Claude's workflow (M1 plan
  §3.2); the PO does not need to invoke them.

<a id="d-040"></a>
### D-040 — The probe gates the real timer; fix loop until it passes · *Accepted* (PO, 2026-10-21)

Grilled with the PO before the M1 start:

- **Gate:** all four items of the probe checklist (09 §4) must pass on the
  PO's Galaxy before M1-09 and M1-11 are finished:
  1. the APK installs;
  2. the alert follows the ringer mode, with the full-screen alert over the
     lock screen;
  3. the status-bar chip and the lock-screen Live Update are shown;
  4. all status rows show ✓.
- **Now Bar:** stays best effort (04 §8.3). Its presence is recorded but
  never blocks.
- **Fix loop:** a failing item is fixed and re-tested with a new probe:
  `v0.0.2-probe` (versionCode 201), `v0.0.3-probe` (301) and so on, per the
  06 §6 rule. If the same item fails twice and the diagnosis points outside
  the app (system setting, One UI behaviour, platform bug), Claude brings it
  to the PO with the analysis and they decide together.
- **Fallbacks stay:** the runtime fallbacks of 04 §8 (normal ongoing
  notification, heads-up instead of full-screen) stay in the real timer
  (M1-11) even if the probe passes, because permissions can be revoked and
  One UI can change later.

<a id="d-039"></a>
### D-039 — `release.yml` on `main` before the first dispatch · *Accepted* (PO, 2026-10-21)

GitHub only dispatches a `workflow_dispatch` workflow that exists on the
default branch. So `release.yml` (M1-03) reaches `main` through a small
housekeeping PR before the probe release `v0.0.1-probe` is dispatched from
the M1 PR branch; release candidates are dispatched the same way. This is an
exception to „one PR per milestone" limited to the release workflow; later
changes to `release.yml` within M1 travel with the milestone PR. Agreed with
the PO as part of the spec issue
[katznmeik/UmamiT#6](https://github.com/katznmeik/UmamiT/issues/6), which
also fixes the M1-00 … M1-03a test seams (M1 plan §3.1).

<a id="d-038"></a>
### D-038 — Go for development · *Accepted* (PO, 2026-10-21)

The PO gave the go for development, effective **2026-10-22 at 09:05
Europe/Berlin** (after the PO's weekly usage limit resets), unless the PO
says otherwise before then. Development starts with M1 per
[`docs/plans/M1-timer.md`](../plans/M1-timer.md).

<a id="d-037"></a>
### D-037 — Claude merges all pull requests · *Accepted* (PO, 2026-10-21)

The PO never merges. Claude merges documentation and housekeeping PRs after
checking the diff (and green CI, if any). Milestone PRs are merged by Claude
once the done criteria are met, including the PO's explicit approval of the
on-device checklist and the design review. Merge commits; the working branch
is restarted from `main` afterwards. Recorded as rule 9 in `CLAUDE.md`.

<a id="d-036"></a>
### D-036 — Spec review fixes and PO decisions · *Accepted* (PO, 2026-10-21)

Result of an Opus `xhigh` review of spec v0.2 plus the PO's answers.

**PO decisions**
- **Three network uses**, all only to the allow-listed host
  `www.tee-kontor-kiel.de`: (a) the user-triggered catalog refresh (CAT-07);
  (b) a **one-time download of the two seed teas' product photos** at first
  start (during the first-start setup, visible progress, skippable,
  placeholder fallback; ONB-06); (c) the on-demand exact variant price fetch
  when the user adds a package (CAT-12). Updates GEN-01, NFR-PRIV-02,
  06 §4/§5.7, 08 §2.5a.
- **Sounds:** synthesised first (ffmpeg in the cloud container): Klangschale
  (default), Glocke, Holzblock, Glockenspiel, Sanft and the pre-alert tick.
  The PO may replace any tone later with uploaded CC0 recordings (licence in
  `docs/design/assets/source/CREDITS.md`). Amends D-034.
- **Screen wake:** at the end of every step (short and long) the alert is a
  **full-screen intent** on `steep_finished`: the screen turns on and SCR-05
  in ALERTING is shown over the lock screen, also in silent mode (then
  visual only). Needs `USE_FULL_SCREEN_INTENT`; checked in ONB-02, SET-24
  and the reliability banner (TMR-06, 04 §8–9).
- **Backup simplified:** Android device backup (BAK-05) plus a simple manual
  export ZIP (BAK-01). In-app restore with validation (BAK-02) is `Won't`
  for 1.x (post-1.0 backlog).
- Tee-Info guides import (CAT-09) and wider layouts stay (cuts declined).

**Review fixes** (IDs from the review)
- R-02: session-only **Schritte-Sheet** (SCR-31) for SCR-04 *Bearb.*,
  TMR-10 and TMR-11; executable M1 checklist.
- R-03: the cloud container has no emulator; instrumented tests run in a
  **CI emulator job** on GitHub Actions (API 35 and 36).
- R-04: `release.yml` runs via `workflow_dispatch` and creates tag and
  release; release candidates `vX.Y.Z-rc.N` (pre-releases from the PR
  branch) and the final `vX.Y.Z` from `main`; versionCode scheme
  ([06 §6](06-architecture.md#6-build--distribution)).
- R-05: keystore hand-off as session file attachments; fallback commands
  for the PO ([06 §6.1](06-architecture.md#61-signing-process-d-035)).
- R-07: Live Update corrections; Now Bar is best effort; a **probe release**
  `v0.0.1-probe` is tested on the Galaxy before the full timer is built
  ([04 §8.3](04-brew-timer.md#83-live-update)).
- R-09: recovery tries to restart the FGS immediately; app-update receiver;
  midnight passed while killed → `midnightTimeout`
  ([04 §7](04-brew-timer.md#7-persistence--recovery)).
- R-10: minimal diagnostics log already in M1.
- R-11: Material 3 schemes generated from seed `#4A5643` with overrides;
  extended tokens; timer digit font features ([03 §3](03-ux-ui.md#3-visual-design-system)).
- R-17: CAT-14 (bundled snapshot) → `Won't`.
- R-18: CI also builds `assembleRelease` (R8).
- R-19: `planned_step_count`, session-step lifecycle, midnight rule
  (first local midnight ≥ 6 h after the last interaction), device restore
  clears `active_brew`, backup rules include all of `umamit.db*`.
- R-20: catalog hardening (Cloudflare detection, stop on HTTP 429, own
  `dataSync` foreground service instead of WorkManager).
- R-21: app icon and status-bar icon in M1; Samsung auto-restart hint.
- R-23: all module stubs before parallel work; PR template sections.
- R-12…R-16: consistency fixes (mockup notes, seed descriptions via CAT-15,
  worked examples, glossary, single source of M1 scope).

<a id="d-026"></a>
### D-026 — Subagent distribution: model and effort per task · *Accepted* (PO, 2026-10-21)

Development tasks are delegated to subagents by tier: T1 simple → Sonnet,
low effort; T2 standard → Opus, medium; T3 complex → Opus, high; T4 critical →
Opus, extra high (`xhigh`, the ceiling; `max` not used). Details, task
catalogue and escalation rules: [12 · Subagent Distribution Plan](12-agent-plan.md).

<a id="d-027"></a>
### D-027 — Application ID and display font · *Accepted* (PO, 2026-10-21)

Application ID `com.umamit.app`; serif display font Cormorant Garamond (OFL).

<a id="d-028"></a>
### D-028 — No smartwatch support · *Accepted* (PO, 2026-10-21)

No Galaxy Watch integration or testing; notification bridging may happen by
default but is not a requirement.

## Change log

| Date | Change |
|---|---|
| 2026-10-22 | D-046: design system details from M1-04 (#29): generated schemes with twin roles, font subset keeps `lnum`/`tnum`, German hyphenation, Material Symbols licence, motion tokens in 03 §3.6. |
| 2026-10-22 | Q-15 → D-045: Klangschale variant C (880 Hz) and a bowl-like 0.8 s pre-alert tick; probe `v0.0.2-probe` passed, the Live Update also appeared in the Now Bar. |
| 2026-10-22 | D-044: alert replaces the Live Update while ALERTING (Android 16 auto-group silencing); promotion always requested; D-043 amended; probe → v0.0.2-probe. |
| 2026-10-22 | D-043: Android 16 Live Update API names corrected (promotion settings action, NotificationCompat request, POST_PROMOTED_NOTIFICATIONS not public). |
| 2026-10-22 | D-042: build tooling choices (Detekt 2 alpha, compileSdk-36 library limits, configuration cache off, lint exceptions, action versions). |
| 2026-10-21 | D-041: motion guardrails (calm style, liquor fill + pre-alert glow, strong non-flashing alert, reduce motion, GIF recordings for review); 03 §3.6 rewritten. |
| 2026-10-21 | D-040: the probe gates M1-09/M1-11; fix loop with `0.0.N-probe`; Now Bar best effort; runtime fallbacks stay. |
| 2026-10-21 | D-039: `release.yml` on `main` before the first dispatch; spec issue #6 and test seams for M1-00 … M1-03a. |
| 2026-10-21 | D-036: spec review (Opus xhigh) fixes and PO decisions — three network uses incl. one-time seed photo download, synthesised sounds, full-screen alert, simplified backup (no in-app restore in 1.x), Schritte-Sheet, CI emulator job, release candidates, probe release, catalog hardening; D-016 accepted. |
| 2026-10-21 | v0.1: initial specification set; D-001, D-002 accepted; brand mockup incorporated. |
| 2026-10-21 | Brand mockup committed; Q-13 resolved. |
| 2026-10-21 | D-038: go for development, effective 2026-10-22 09:05 Europe/Berlin. |
| 2026-10-21 | D-037: Claude merges all PRs; PO only approves milestone PRs. |
| 2026-10-21 | Repository renamed to `katznmeik/UmamiT` (D-033 updated); PR #1 merged. |
| 2026-10-21 | D-035: welcome text „Aus Liebe zum Tee", no home greeting, pre-alert default 10 s, signing process with 2 secrets. |
| 2026-10-21 | Grill round 4 (D-034): silent stays visual-only, Claude-generated signing key, CC0 sounds, releases announced by Claude, seed teas linked on first refresh, prominent overtime. |
| 2026-10-21 | App renamed to UmamiT (D-033); application ID `com.umamit.app`. |
| 2026-10-21 | Second mug steep set to 0,25 × first duration (+5 °C) per PO experience (D-032). |
| 2026-10-21 | Grill round 3 (D-032): seed teaware, second mug steep, waiting sessions until midnight, screenshot design review per milestone. |
| 2026-10-21 | Grill round 2 (D-031): manual-only refresh on any network; discontinued teas hidden unless owned; empty teas stay marked „Leer"; early finish = normal session; one infusion = one cup; *Nochmal brühen* (3 last teas) opens the timer directly; no automatic rinse; screen dims while steeping; M1 seed teas Tanabata & Hisui with live data; small-size prices not estimated. |
| 2026-10-21 | Mockups v2 added (`docs/design/mockup-v2-*.png`) and reviewed in 03 §1.1; journal row format adopted from them. |
| 2026-10-21 | Q-04 → D-029 (imagery), Q-11 → D-030 (public repo); asset list `docs/design/assets.md`. |
| 2026-10-21 | V-01: Tee-Kontor structure verified live (Shopware 6.7, Cloudflare, sitemap discovery, Kyusu/Westlich brewing grid, variants per URL, shop taste scales); 08 §2 rewritten; rate limit 2 req/s; estimated package prices. |
| 2026-10-21 | Agent plan (12, D-026); Q-01, Q-05, Q-18 resolved (D-027, D-028); Q-04 options and Q-11 notes added; D-016 switched to synthetic fixtures; tee-kontor-kiel.de reachable from the cloud environment. |
| 2026-10-21 | v0.2: PO interview — personal sideloaded app, German, Samsung/Android 16, Tee-Kontor catalog with in-app refresh, timer details (pre-alert, ringer-mode alert, Live Update), auto-journal, Stats tab, delivery plan. D-013 … D-025 added; D-004, D-010 superseded; D-001, D-006 amended. |
