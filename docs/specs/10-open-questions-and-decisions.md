# 10 · Open Questions & Decision Log

> Status: **Living document** · Last updated: 2026-10-30

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

<a id="d-098"></a>
### D-098 — v0.3.0: quick timer alongside a brew, long hot steps, the PO's feedback on v0.2.2-rc.1 (TMR-18, TMR-21, TMR-22, TMR-23, TMR-31, BRW-11, INV-07, INV-11, CAT-10, SET-27–SET-29, SCR-02, SCR-04, SCR-05, SCR-07, SCR-19, SCR-25; amends D-093 point 1 and Q2, TMR-18, 04 §9.1) · *Accepted* (PO, 2026-10-30, grilled in the session Q1–Q8, R2-Q1–R2-Q3 and scenarios S1–S4; details marked „Claude" are Claude's)

**Context.** v0.3.0 bundles three sources (spec #124, tickets #125–#139):
- D-093 point 1: quick timers alongside a brew session;
- #122: hot steps of 10 min and more lost their Vorwarnung, because TMR-18
  treated every step ≥ 10 min as a long step (written for cold brew);
- #123: the PO's five points after passing the v0.2.2-rc.1 checklist (alarm
  volume, setup → catalog, daily low-stock reminder, foldable catalog
  groups, stock on READY).

The PO's animation review of v0.2.2 adds two polish items. The glossary
(„Active timer", „Quick timer", „Short / long step", „Low-stock card /
reminder") was updated with the grilling.

**Amends.**
- **D-093 point 1 and the „one active timer" rule** of M1 (D-047, D-048,
  D-054, 04 §10): from v0.3.0 at most one active brew session **and** at
  most one active quick timer.
- **D-093 Q2:** the Vorwarnung and the alarm no longer play on the
  notification volume (`USAGE_NOTIFICATION_EVENT`, 04 §9.1, §9.2) but on
  the alarm volume (`USAGE_ALARM`).
- **TMR-18 and 04 §1:** the long-step threshold moves from 10 min (600 s) to
  60 min (3600 s); cold and ice brew stay long steps.
- **04 §9.1:** the Do Not Disturb rows change (see the DND matrix below).

**Decisions (PO).**
1. **Two active timers (Q1, R2-Q1).** At most one active brew session and
   one active quick timer at the same time. Either starts while the other
   runs, without a question.
2. **Alarm order (Q2, R2-Q2, S1).** When both are due, the brew alarm comes
   first. The quick timer's alarm waits and rings after *Abgegossen*, with
   its overtime. A quick alarm that is ringing when a brew step ends gives
   way at once and comes back after *Abgegossen*. Never two sounds at once.
   A waiting quick alarm always rings; its 10-min silent end counts from
   when it starts ringing.
3. **Seeing both (Q3).** The brew timer shows a slim row „⏱ Schnelltimer
   2:14" with its own *Pause*; a tap switches screens. The bar on the other
   screens shows both timers.
4. **Starting alongside (Q4).** A quick timer starts alongside a brew only
   from Start's *Schnelltimer* (and its pre-start screen), not from the
   timer's ⋮ and not from a notification.
5. **Second quick timer (R2-Q3).** Asks „Schnelltimer beenden und neu
   starten?"; the brew is never touched. A second brew session still asks
   as TMR-21 does.
6. **Lock screen (Q5).** One Live Update, for the timer due next; the other
   timer is a quiet normal notification.
7. **Long steps (Q6, #122).** Hot steps of 10–59 min behave like every
   short step: countdown, running service, automatic or explicit
   Vorwarnung. Cold and ice brew, and steps of 60 min and more, stay long
   steps: „Fertig um 14:30", no Vorwarnung.
8. **Sound (Q7, Q8, S2, S3; #123 point 1).** Vorwarnung and alarm play on the
   phone's alarm volume. One in-app *Lautstärke* setting for both, 10–100 %,
   default 100 %, heard with *Alarm testen*. Silent and vibrate mode still
   silence both. DND „alarms only" lets both ring. When the phone's alarm
   volume is 0, the reliability banner and the settings warn
   „Alarmlautstärke ist aus" with *Beheben*.
9. **Setup → catalog (#123 point 2).** A ⋮ in the brew setup's top bar with
   *Im Katalog ansehen*, which opens the tea's SCR-21; back returns to the
   unchanged setup. Only for teas with a catalog entry.
10. **Daily low-stock reminder (#123 point 3, S4).** Once a day at a set
    time (default 18:00) one quiet notification lists every *Knapp* or
    *Leer* tea, including teas hidden with *Ausblenden* until restocked.
    Removed (archived) teas never; no per-tea switch. On/off and time in
    *Einstellungen › Brühen & Vorrat*, default on. A tap opens Start.
11. **Foldable catalog groups (#123 point 4).** A tap on a *Sorte* header
    folds it („Bancha · 12" with a chevron); remembered; a search shows
    everything unfolded; ⋮ *Alle einklappen* / *Alle ausklappen*.
12. **Stock on READY (#123 point 5).** „38 g übrig" (or „· knapp", „Leer")
    under „4,0 g Tee · 250 ml Wasser" on every READY of a brew.

**Details (Claude).**
- **Two controller slots.** The pure engine stays a single-timer state
  machine. The controller runs two slots, `BREW` and `QUICK`, each with its
  own engine state, due times, exact alarms, snapshot and watchdog, under
  one event lock. A slot accepts only its own kind: `startSession` meets the
  brew slot, `startQuickTimer` the quick slot. `ActiveTimerExists` is raised
  only by a second start in the same slot, and *Beenden & neu starten* acts
  within that slot only (TMR-21 for the brew, „Schnelltimer beenden und neu
  starten?" for the quick timer). Every user event names its slot (screen,
  notification action, bar); a time-based event comes from its slot's
  alarm.
- **Request codes.** The brew slot keeps 101–107 (04 §6.2). The quick slot
  uses 201–207 for the same fields. A quick timer running during the update
  from v0.2.2 holds alarms under 101–107: the first turn of v0.3.0 restores
  it into the quick slot, and the per-slot reconcile cancels the brew
  codes (no brew is active) and schedules 201–207. An old alarm that fires
  first reaches `onAlarm` as today, whose recovery runs before it, so it is
  harmless (§3.2 guards).
- **Notification IDs.** By role, not by slot: 1 the Live Update (the FGS
  notification), 2 the alert, 4 the quiet notification without the service,
  and **5** the other timer's notification (new). Each carries its slot in
  its actions. Moving the Live Update to the other slot updates 1 and 5 in
  one turn; the service never switches its foreground ID for it. This
  refines #125's „notification ids per slot".
- **Alert arbitration (one alert player).** The controller decides which
  slot may sound:
  - **Priority:** brew over quick. A brew alert never waits.
  - **Held quick alert:** a quick deadline reached while the brew alerts
    (from its deadline until the brew slot leaves ALERTING: *Abgegossen*,
    *Sitzung beenden*, *verwerfen* or the brew's own overtime timeout) puts
    the quick slot into ALERTING **held**: no sound, no alert notification,
    no full-screen intent. Its overtime runs from its own deadline.
  - **Release:** when the brew slot leaves ALERTING, a held quick alert
    starts ringing in the same turn (sound, notification 2, full-screen
    intent to its screen), showing its overtime („+12:00").
  - **Preemption:** a brew deadline while the quick alert rings stops the
    quick sound and starts the brew alert in the same turn; the quick slot
    goes back to held.
  - **Silent end:** the quick slot's overtime timeout is 10 min after it
    **last started ringing** (`ringingSince`), not after its deadline. While
    held it has none, so a held alert can wait any length and still rings.
    The brew slot is never held, so its timeout stays 10 min after the
    deadline.
  - **Vorwarnung:** a Vorwarnung never cuts an alarm (unchanged). One due
    while the other slot's alert sound plays, or while the other slot's
    Vorwarnung still rings, waits until that sound ends and plays only if at
    least 1 s of its step is left, else it is consumed silently (04 §5). An
    alarm cuts the other slot's ringing Vorwarnung, as it cuts its own.
  - The engine gets the held state as data: ALERTING carries `ringingSince`
    (null = held) and two controller events, `alertHold` and
    `alertRingStart`, which are not interactions. `alertCompleted` and the
    at-least-once replay (D-047) apply per slot; a held alert is never
    reported completed.
- **Recovery with two slots.** Both snapshots are restored in the first
  turn; the brew slot's `recover` runs first. If the brew slot is then
  ALERTING, a quick timer past its deadline becomes held, however long ago
  its deadline was. Otherwise a quick timer follows 04 §7.2, with its
  timeout counted from `ringingSince` when it had rung. A held alert
  survives process death, reboot and app update.
- **umamit.db v4 (per-slot `active_brew`).** The table's key becomes
  `slot` (TEXT, `BREW` or `QUICK`) in place of `id` = 1; two columns are
  added, `alert_ringing_since_elapsed_ms` and `alert_ringing_since_wall_ms`
  (null = held or not alerting; the wall value rebases it after a reboot).
  `MIGRATION_3_4` recreates the table in one transaction and copies the
  existing row into the slot of its kind (`is_quick_timer` 1 → `QUICK`, else
  `BREW`), every value kept; an ALERTING row gets `ringing_since` = its
  deadline, which is how it rang in v0.2.2. A running timer keeps running
  and alerts on time. v1–v3 stay frozen (D-054).
- **Service and background.** One foreground service while any slot is
  active. The partial wake lock is held while either slot needs it (a
  running short step or an alert), with the longer of the two timeouts. Idle
  demote (TMR-14) and the midnight timeout apply to the brew slot only; a
  demoted brew does not stop the service while the quick slot needs it, it
  only becomes notification 5. The quick timer keeps its 30-min cancel.
- **Live Update for the next due slot.** Notification 1 shows the slot that
  ends next: a running slot beats a waiting one; of two running, the earlier
  deadline; of two waiting, the brew. It switches whenever that order
  changes (start, pause, resume, a step's end, a timer's end). The other
  slot is notification 5 on a new channel `timer_other` („Weiterer Timer",
  importance LOW, silent, never promoted), with its own actions
  (*Pause*/*Weiter*, *Abbrechen* or *Sitzung beenden* as by state). LOW keeps
  it out of the importance section of the alert, so the alert stays the
  app's only notification of importance DEFAULT or higher (D-044). While a
  slot alerts, notification 2 belongs to it; the other slot shows only as
  notification 5 until the alert ends. A held quick alert shows as
  notification 5 („Schnelltimer · Fertig, wartet auf den Aufguss") without full-screen intent.
- **Long-step threshold 3600 s.** `isLongStep` = cold or ice brew, or
  planned ≥ 3600 s; one pure function in `core:timer-engine`, which the
  Schritte-Sheet and the setup call (the sheet's own copy goes). The
  duration rounding of PRF-04 and TMR-10 (5 s below 10 min, whole minutes
  from 10 min) is a separate constant and does not move. A hot step of
  10–59 min now keeps the service and the wake lock for its length; the ring
  updates per frame like any short step.
- **USAGE_ALARM and gain.** Both cues use `AudioAttributes.USAGE_ALARM`
  (alarm stream) and `VibrationAttributes.USAGE_ALARM`. The player's gain is
  *Lautstärke* / 100 (setting `alertVolumePercent`, 10–100 in steps of 10,
  default 100; a stored value outside is clamped), applied as the player
  volume on top of the alarm stream; it is read with the other settings at
  the start of every cue, so a change applies to the next cue. The app
  never changes the system volume. *Alarm testen* plays with the same usage
  and gain. Loading ahead (D-092, D-096) is unchanged.
- **Ringer and DND matrix.** The app keeps reading the ringer mode on every
  cycle, since the alarm stream ignores it: normal → sound (+ vibration per
  SET-11); vibrate → vibration only; silent → visual only. Do Not Disturb:
  „alarms only" and priority mode → as the ringer mode (alarms pass);
  „total silence" → visual only. A call still means vibration only.
- **Alarm volume 0.** A new reliability check (TMR-31):
  `AudioManager.getStreamVolume(STREAM_ALARM) == 0` → „Alarmlautstärke ist
  aus" with *Beheben* (`Settings.ACTION_SOUND_SETTINGS`), in the banner on
  Start and the timer and as a row of the settings' *Timer-Zuverlässigkeit*
  card. It is read whenever the other checks are (app start, resume, each
  session start); it never blocks a timer.
- **Daily reminder (INV-11).** A unique WorkManager one-time job
  (`LowStockReminderWorker`), enqueued for the next set time and enqueued
  again by each run, at app start, after a change of the setting and after
  an app update; WorkManager re-arms it after a reboot. Inexact by design
  (up to about 15 min late; no exact alarm, no wake-up of its own). A run
  posts at most once per local date (`lowStockReminderLastDate`) and, when
  it runs more than 30 min before the set time (a clock or DST change),
  only re-enqueues. The query uses the Start card's stock levels and
  thresholds (a tea's own threshold first), non-archived teas with stock
  events, without the `lowStockHidden` filter, least stock first. Nothing is
  posted when the list is empty. The notification is ID 12 on the new
  channel `stock` („Vorrat", importance LOW, no sound, no vibration, no
  full-screen intent): title „3 Tees werden knapp" („Tanabata wird knapp"
  for one), inbox lines „Tanabata · 14 g" / „Hisui · leer". Its tap opens
  Start through the launcher activity (`com.umamit.action.OPEN_START`, no
  intent filter); during an alert the tap waits like the catalog tap
  (D-097). Switching the setting off cancels the job and the notification.
- **WorkManager in the manifest.** This is the app's first WorkManager job.
  Its library manifest adds `ACCESS_NETWORK_STATE`, which 06 §5.7 does not
  allow: it is removed with `tools:node="remove"` (the job has no network
  constraint), so `checkManifestPermissions` stays unchanged.
  `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK` and `FOREGROUND_SERVICE` are already
  on the list.
- **Foldable groups setting.** `catalogFolded` (Set<String>, entries
  `<view>/<sorte key>`, view `green` or `herbal`; „Ohne Sorte" is
  `<view>/-`). A key whose *Sorte* no longer exists is ignored. Folding
  applies only to the grouped default and Kräutertee views; *Gemerkt* and
  the flat sort orders have none; a non-empty search ignores it. No fold
  animation: the chevron turns, the content snaps.
- **READY stock from inventory in the UI layer.** The timer view model reads
  the session tea's stock (amount, *Knapp*/*Leer* by `StockLevel`, the tea's
  own threshold first) as a flow from the inventory repository and adds it to
  the UI state; never through the engine, the controller or the alarm path.
  Null for the quick timer, a tea not in the collection and a failed read,
  which shows no line and is logged. The line counts as content above the
  control in the reserved space (D-096, D-097).
- **Setup ⋮.** Shown only when the tea has a `catalog_key` whose catalog tea
  exists (discontinued counts); otherwise no ⋮ at all. It navigates to SCR-21
  above the setup; the setup's state survives in its back-stack entry.
- **Polish from the v0.2.2 animation review.**
  1. SCR-07's *Vorwarnung* row is anchored at its label: „Vorwarnung" keeps
     its x position for every choice and the selector grows to its right.
  2. The quick timer's screen before *Start* reserves the running screen's
     band and control height (as the brew timer's `reservedControls`), so on
     a short screen at a large font (384 × 832 dp, font 2.0) the ring and the
     pill do not move at *Start*.
- **Release.** `v0.3.0-rc.N` = versionCode 30001 …, `v0.3.0` = 30099. Timer
  code changes, so every rc and the final release need a PASS of both
  accuracy variants, the standard run and `alongside` (a quick timer
  running alongside the brew steps; 09 §4a).

**Out of scope.** More than one quick timer or two brew sessions; logging the
quick timer; a per-tea reminder switch; reminders other than low stock;
changing the system alarm volume from the app; M3 (now v0.4.0).

**Build notes (Claude, 2026-10-30, #126: umamit.db v4).**
- **Slot key.** `active_brew.slot` is the enum `TimerSlot` of `core:model`,
  stored by name (`BREW`, `QUICK`) like `phase`; a name never changes its
  meaning and is never renamed (05 §5). A row's slot is derived from its kind
  (`ActiveBrewSnapshot.slot`, from `is_quick_timer`), so it is never chosen
  apart from it.
- **Store per slot.** `ActiveBrewStore` gains `read(slot)`, `persist(slot,
  snapshot)` and `clear(slot)`, which touch only their slot (the brew slot
  also its session's step rows). `persist(slot, …)` refuses a snapshot of the
  other kind (`IllegalArgumentException`) and writes nothing. A damaged row
  whose `slot` and `is_quick_timer` disagree is read as stored; the controller
  refuses it like a snapshot `restore` refuses (#125, #134).
- **Single-timer methods kept** for the controller until v0.3.0-03: `read()`
  returns the brew slot's row, else the quick slot's; `persist(snapshot)`
  writes the snapshot in its kind's slot and clears the other slot in the same
  transaction, as v3 replaced its single row; `clear()` clears both. The
  controller harness's `ObservedStore` passes the slot methods to the real
  store unobserved.
- **Ringing start.** `ActiveBrewSnapshot` carries
  `alertRingingSinceElapsedMs` / `alertRingingSinceWallMs`, and the store
  round-trips them in either slot (null = held or not alerting). Until the
  held alert lands (#134) the engine neither sets nor reads them, so the first
  transition after the migration writes them back as null; nothing reads them
  before #134.
- **Migration.** `MIGRATION_3_4` creates the new table with Room's v4
  `createSql` (identical but for the table name), copies the v3 columns but
  `id`, drops, renames and recreates `index_active_brew_session_id`;
  `created_at` stays per row. `Migration3To4Test` covers a running brew, a
  running quick timer with its own 20 s, an alerting quick timer with *Aus*,
  an alerting brew whose alert played, and no timer.

**Build notes (Claude, 2026-10-30, #128: alarm volume and *Lautstärke*).**
- **DND under „alarms only" (S2, S3; the check 04 §9.1 asked for).** The
  matrix reads `getRingerMode()` and `getCurrentInterruptionFilter()` as
  before. AOSP's zen mode forces the ringer to silent under „alarms only"
  and total silence (`ZenModeHelper.applyZenToRingerMode` sets the internal
  ringer mode to silent, and the reported one follows), so under „alarms
  only" a silent report says nothing about the user's choice. The app reads
  it as normal there: „alarms only" lets both cues ring (decision 8, rule 8);
  vibrate is kept as reported. Priority mode leaves the ringer mode alone in
  AOSP, so it plays as the ringer mode, and total silence stays visual only.
  The open risk is a phone that reports the user's own silent mode under
  „alarms only": the cue then rings although the PO chose silent. One UI's
  DND is priority mode with exceptions (alarms allowed by default), where
  the ringer mode is used as reported. No emulator runs here and Robolectric
  does not model zen, so the device answer comes from the rc: each cue's
  *Diagnose* line now names the ringer mode, the DND mode („DND off /
  priority / alarms only / total silence"), the alarm volume („alarm volume
  on / 0") and the gain („gain 60 %"); the 09 §4 DND item asks the PO to
  copy it after the DND checks.
- **Gain.** `MediaPlayer.setVolume(gain, gain)` right before `start()`, so a
  sound loaded ahead takes the cue's own gain, and a player loaded on the
  spot (D-092 fallback) too. A change of *Lautstärke* loads nothing (the
  sounds of `keepReady` are unchanged), so the first cue still starts
  without a load. The gain is `alertVolumePercent` clamped to 10–100 on
  read (`AlertSettings.of`), so a damaged file never silences a cue; the
  repository snaps a written value to 10 (half up) and clamps it.
- **Settings file.** `alertVolumePercent` (Int, default 100) needs no
  settings migration: a v0.2.2 file has no key and reads 100 %; settings
  `schemaVersion` stays 2 (05 §6).
- ***Anhören*** (SET-09, SET-26) plays at the stored *Lautstärke* too, on
  the alarm stream like *Alarm testen*, so the PO hears what will ring.
- **The slider.** M3 `Slider`, 10–100 with 8 steps, the value („60 %", with
  a no-break space) at the title's end and as TalkBack's state. A drag
  writes once, when it ends; TalkBack's adjustments write at once. The
  inactive track is `surfaceContainerHighest` (the washi
  `secondaryContainer` read like a warning on the dark theme).
- **The check.** `ReliabilityCheck.AlarmVolume`, last in the card's order,
  so a missing permission's *Beheben* comes first; `PermissionStates` gains
  `alarmVolumeOn` (`getStreamVolume(STREAM_ALARM) > 0`; 0 also for a muted
  stream), logged at process start. The banner says „Alarmlautstärke ist
  aus" only when the volume is the first missing check (the one *Beheben*
  opens), else the general message. *Beheben* opens
  `Settings.ACTION_SOUND_SETTINGS`, then the settings (not the app's
  details, which have no volume). The card's row shows ✓ like its
  neighbours when the volume is up and „Ist aus" with *Beheben* when it is
  0; the setup page (ONB-02) does not list it.

<a id="d-097"></a>
### D-097 — v0.2.2: the PO's feedback on v0.2.1 (BRW-11, BRW-08, SCR-02, SCR-03, SCR-05, SCR-07, SCR-19, SCR-21, SCR-22, INV-05, INV-10, SET-16, TMR-22, CAT-18) · *Accepted* (PO, 2026-10-30, grilled in the session Q1–Q9; details marked „Claude" are Claude's)

**Context.** After using v0.2.1 the PO sent seven points (2026-10-30). They
ship as **v0.2.2**, before v0.3.0 (quick timers alongside a brew, D-093
point 1), which stays as planned.

**Decisions (PO).**
1. **Grams on READY (BRW-11, SCR-05).** Every READY state of a brew shows,
   next to the kettle hint, the leaf and water of the session: „4,0 g Tee ·
   250 ml Wasser", however the timer was reached (*Nochmal brühen*, setup,
   *Weiterer Aufguss*). The quick timer has no tea and shows none.
2. **Catalog notifications open the catalog (SCR-22, 03 §2.4; amends
   D-079).** A tap on the refresh's progress notification opens *Sammlung ›
   Katalog* with the *Katalog aktualisieren* sheet showing the progress; a
   tap on the result notification opens the same screen with the sheet
   showing the summary.
3. **Tea groups in the picker (SCR-03).** *Zuletzt* and *Favoriten* stay on
   top; *Alle* is split into groups under a heading each, „Grüntees" and
   „Kräutertees" (later more, e.g. own teas of other kinds in M3), A–Z
   within a group. Search results are grouped the same way.
4. **Low-stock reminder (INV-05, INV-10, SET-16, SCR-02, SCR-04).** *Knapp*
   starts at **15 g** (was 10 g), changeable in the settings. Start shows a
   quiet card „*Tanabata* wird knapp · 14 g übrig" with *Nachbestellen*
   (INV-09's link) and *Ausblenden* (hidden until the tea's next purchase);
   the brew setup marks „14 g übrig · knapp".
5. **Pre-alert of the quick timer (TMR-22, SCR-07).** The PO saw no
   pre-alert mark on a running quick timer of 2:00 and more and heard no
   pre-alert, with *Vorwarnung* set to a value: a bug, to be reproduced on
   the real path and fixed. The engine computes and schedules the lead
   correctly (a probe test on 2026-10-30: 2:00 with B = 10 → lead 10 s, due
   at 1:50), so the fault lies between the controller, the alarms, the
   screen and the alert player. In addition the screen before *Start* gets
   the *Vorwarnung* selector of the brew setup (*Automatisch*, *Aus*, 5–30 s)
   with the mark on its ring, and remembers the last choice.
6. **8 minutes (TMR-22, SCR-07).** The chips become 0:30 · 1:00 · 1:30 · 2:00
   · 3:00 · 5:00 · 8:00; they wrap onto a second row when needed.
7. **Merkliste (CAT-18, SCR-19, SCR-21).** Catalog teas can be marked for
   later without adding them: *Merken* with a bookmark icon (not „Favorit",
   which belongs to the own teas). A *Gemerkt* chip in the catalog shows
   them; nothing new on Start. The mark goes away when the tea is added to
   the collection. A marked tea that leaves the range stays, marked „Nicht
   mehr im Sortiment". The marks are personal data (umamit.db, backup).

**Details (Claude).**
- **Grams line.** The line shows the session's leaf and water as planned;
  a session without water (*Ohne Gefäß*) shows only the leaf, one without
  leaf only the water, one without either nothing. Matcha shows the same
  line. On a short screen it counts as content above the control (D-096),
  so the band gives way first.
- **Notification taps.** The tap opens the catalog on the Sammlung tab
  above whatever was shown; back returns there. An alert still wins (the
  sheet stays closed during it, D-077) and a timer on screen stays where it
  is in the back stack. Before the setup is finished (ONB-03), the tap opens
  the app where it was, as before. A result notification tapped after the
  app was closed shows the summary of the last refresh from the refresh log.
- **Picker groups.** A group is the tea's kind from its style: `herbal` →
  „Kräutertees", every green style → „Grüntees". A heading only shows when
  the picker holds teas of more than one group; with one group the list
  looks as before. *Zuletzt* and *Favoriten* stay ungrouped.
- **Threshold 15 g.** SET-16 had no settings row before v0.2.2, so a stored
  10,0 g is the old default, never the PO's choice: the first start of
  v0.2.2 changes a stored 10,0 to 15,0 once (settings `schemaVersion` 1 → 2).
  The row sits in the settings section *Brühen & Vorrat* (SET-30), new in
  v0.2.2: „Knapp ab" 0–500 g in 5 g steps.
  A tea's own `low_stock_grams` (M3) still wins over it.
- **Low-stock card.** One card at a time (SCR-02's single nudge slot); when
  several teas are *Knapp*, the one with the least stock left. Empty teas
  (*Leer*) count too („*Tanabata* ist leer"). Only teas with stock events and
  not archived. *Nachbestellen* only when INV-09 offers the link (a catalog
  tea still in the range); otherwise the card offers only *Ausblenden*.
  *Ausblenden* stores the tea with its latest INITIAL/PURCHASE event (setting
  `lowStockHidden`); a newer purchase shows it again, and meanwhile the next
  most urgent tea takes the slot. The card never covers or delays an alert; it is a plain card in the
  list.
- **Quick timer selector.** The engine's `startQuickTimer` takes the step's
  choice (null = *Automatisch* with B = the global setting, 0 = *Aus*, an
  explicit 5–30 s as set, with the skip rules of 04 §5). The choice is stored
  in a new `active_brew` column so a restore keeps it, and the last choice
  in the setting `quickTimerPreAlert`. The ring before *Start* shows the mark
  for the chosen lead; the selector's labels follow SCR-31 („Auto · 10 s",
  „20 s", „Aus", „· zu kurz").
- **Merkliste.** A table `catalog_bookmark` (catalog key, created at) in
  umamit.db **v3**, in the backup's `data.json`. *Merken* is offered on
  every catalog tea not in the collection (an archived tea counts as not in
  it); adding it (SCR-23) deletes its mark in the same transaction. The
  refresh keeps a discontinued catalog tea while it is marked, as it does
  for an owned one (CAT-07 AC4). With *Gemerkt* on, the list shows every
  marked tea, green and herbal, sold out or discontinued, grouped as the
  default view; the other chips are hidden and only the search applies.
  Empty: „Noch nichts gemerkt · Tippe bei einem Tee auf das Lesezeichen.".
- **One schema step.** The new `active_brew` column and `catalog_bookmark`
  share one migration, v2 → v3, with a test on a populated v2 database;
  v1 and v2 stay frozen (D-054).
- **Timer code changes** (quick timer start, its pre-alert), so every
  `v0.2.2-rc.N` needs a passing accuracy run (09 §4a), and the 04 §12 tests
  are extended by the quick timer's pre-alert (rule 8).

**Build notes (Claude, 2026-10-30, PR #121).** Details decided while building
#109–#117:
- **Silent quick-timer pre-alert (#109).** Not reproduced on any seam: the
  controller through the real starter (5–30 s × 0:30–5:00), the alert
  playback, the service port with the notifier on SDK 36, and the full screen
  path all mark and play it. The PO later found it working on the phone; the
  likeliest cause is the global *Vorwarnung* at *Aus* at the time. The tests
  stay as regression tests; no production code changed. The quick timer's
  start is logged with its Vorwarnung in *Diagnose* („pre-alert auto, base
  10 s, lead 10 s").
- **umamit.db v3 (#110, #116).** A second mark keeps the first `created_at`;
  the list is ordered by key. `quick_pre_alert_seconds` is only valid as 0 or
  5–30 on a quick timer; a value on a brew session, or out of range, makes the
  snapshot invalid. A choice is valid by the same test as the global setting.
- **Quick timer screen (#116, #111).** On a short screen at a large font the
  band before *Start* gives way so *Start* fits (where it fits it stays
  220 dp; only then the ring moves at *Start*). *Start* passes the choice
  shown; if storing it fails, this timer still uses it. A stored value the
  selector doesn't offer reads as *Automatisch*. The chips wrap 5 + 2 at font
  1.0 and 4 + 3 at font 2.0.
- **READY line (#112).** The amount line has the kettle hint's main style (the
  PO called it crucial). The space below the ring reserves it in every phase
  of a brew with leaf or water, so the pill never moves at *Start* or at the
  alert. FINISHED also lets the band give way until *Weiterer Aufguss* and the
  bottom margin fit; the reliability banner sits after the controls and
  scrolls. Leaf and water come from the start's snapshot or the session row
  read on recovery, never from the engine's timing state.
- **Low stock (#115).** Stock amounts in the setup and on the card drop a
  trailing „,0" („14 g", „12,5 g"); the stock sheet keeps „100,0". *Leer* is
  below 0,05 g; *Knapp* includes the threshold. The card is placed after
  *Schnelltimer*, starts empty and swallows read errors, so Start's state
  (which carries the alert) is never held up. A tea with a catalog key but no
  loaded catalog reorders via its own `product_url`. *Ausblenden* keeps one
  entry per tea (`<id>/` for a tea without INITIAL/PURCHASE).
- **Notification taps (#113).** The catalog opens as a second shell route
  above what was shown; back from a tab's root there leaves the route. A tap
  during an alert waits until the alert ends; an alert starting while it is
  open removes it, and it does not reopen. During the first-start setup the
  tap is discarded. After a restart a failure's kind comes from the first
  line of the logged error (*Details kopieren* copies that line); a cancelled
  last refresh shows no sheet. The notification's tap goes through a
  non-exported trampoline activity (`CatalogRefreshTapActivity`, no intent
  filter, no UI) that hands the main activity a one-time in-process ticket;
  without it the action opens nothing, so no other app can open the sheet
  (06 §5.7). There is at most one refresh shell: a new tap updates the one on
  top, or pops an older one lower down (a timer above it comes back; a setup
  or settings opened from it closes), and each shell keeps its own tap.
- **Review fixes (PR #121).** A failed *Ausblenden* write is caught and
  logged, never thrown (rule 8). Every brew phase reserves FINISHED's two
  controls and FINISHED keeps the last step's header line invisibly, so the
  pill never moves at the last *Abgegossen* (D-058). On the 384 × 832 dp
  screen at font 2.0 with a two-line name, *Weiterer Aufguss* keeps 15,5 of
  its 24 dp margin and the page scrolls by the rest (D-096). An alert removes
  SCR-07's *Vorwarnung* menu in its first frame.
- **Picker groups (#114).** With two or more kinds the group headers replace
  the *Alle* header. Kräutertee = style `herbal`, or no style and category
  herbal; every other tea is under „Grüntees".
- **Merkliste (#117).** While *Gemerkt* is on, *Filter* is hidden too and the
  other filters are kept for later. The empty state reads „Noch nichts gemerkt
  · Tippe bei einem Tee auf das Lesezeichen." (a colour 🔖 clashed with the
  palette). A discontinued tea kept only because it is marked shows the
  bookmark button and no shop button. Add and repurchase delete the mark in
  their own transaction.

<a id="d-096"></a>
### D-096 — v0.2.1 build decisions (#83–#92, #97, #98, #100, #102; TMR-23, SET-26, PRF-03, SCR-04, SCR-05, SCR-07, SCR-19, SCR-31, CAT-10, CAT-16, CAT-17, TEA-04, INV-03, WARE-06) · *Accepted* (Claude, 2026-10-29)

Details the specs left open, decided while building v0.2.1 (PR #106). Each
favours rule 8 (timer reliability) and rule 7 (fewer requests, refuse on
doubt) where it touches them.

**Pre-alert per step (#83, #90, #92).**
- An explicit value applies to its step as set, never capped by B or the
  automatic third; the E7 rules (N + 5 s, a rinse under 15 s, a long step)
  still apply. The automatic rule covers rinses too: a 15 s rinse with
  B = 10 s gets 5 s.
- `brew_session.pre_alert_seconds` records B. The migration does not rewrite
  old rows. *Im Rezept speichern* writes each step's value and leaves the
  recipe's B alone.
- **Migration 1 → 2** gives an active session's steps that have not ended
  (the running one too, 05 §9) the lead v1 gave them, as an explicit value:
  its override, or else its inherited B (`COALESCE`). So a session running
  during the update behaves exactly as in v1: a 15–29 s step keeps B
  instead of the automatic 5 s, and the running step's ring and Live Update
  point match its stored tick. Nothing is delayed or silenced.
- **Schritte-Sheet:**
  - Each option previews its effect („Automatisch · 5 s", „10 s · zu kurz",
    „10 s · keine" on a long step); the button reads „Auto · 5 s".
  - „zu kurz" also covers a rinse under 15 s.
  - A change applies on *Fertig*.
  - The running step's selector stays editable after its tick has played;
    the change then has no effect for that step (04 §5).
- **Setup row:** „–" when no step has a pre-alert, and TalkBack reads the
  row in words.

**Sounds (#84).**
- *Alarm testen* starts the alert 0.7 s after the pre-alert sound ends:
  1.5 s for „Ting (leise)", 3.9 s for a 3.2 s sound.
- A tone or *Vorwarnton* changed while a timer is active is loaded at once
  (D-092 said "at the next turn"). The loads ahead run on their own loader
  thread (`UmamiTAlertLoad`), not on `UmamiTAlert`, so no load ever sits
  ahead of a cue (rule 8; D-092's "a cue queued behind a load waits at most
  for that load" no longer applies). A cue whose sound has not loaded yet
  still loads it on the spot.
- A real pre-alert keeps the audio focus until its sound has rung out; an
  alert still cuts it at once.
- „Ting (leise)" is stored as `ting`, and its file stays `tick.ogg`.
- The *Vorwarnton* row stays enabled when *Vorwarnung* is „Aus".
- The loudness check of `generate.sh --verify` runs locally, not in CI.

**Timer screen (#85, #102).**
- On the alert surface ← and ⋮ sit on opaque ink circles with a washi icon,
  like *Abgegossen*. Over a pure white photo the 60 % Steeping circle gives
  3.3:1.
- **Seal:** a 1 dp ink ring at 30 %, the photo in a 76 dp circle, and the
  leaf in the style's colour (`TeaTypeColors.forStyle`, the journal's
  mapping).
- The band starts behind the status bar, and the seal's centre always sits
  on its lower edge. The quick timer's title starts 8 dp below the band.
- On a short screen a brew's band gets shorter, down to the status bar and
  ← ⋮, until the primary control and the bottom margin fit without
  scrolling (rule 8). Its height follows the screen, the insets and the
  content above the control only, so nothing moves when the alert starts.
  The band scrolls with the page. The quick timer's band keeps 220 dp.

**Kräutertees (#89, #91).**
- The whole shop section `/tee/kraeutertee/` is in scope, Mate and Lapacho
  included, because the shop files them there. Teaware filed there (a gourd,
  a bombilla, a filter, a tin, a pot, a sieve …, by its name or a
  `zubehoer`/`geschirr` segment) is no tea and stays out (08 §2.4 item 4;
  code review).
- The one-time `NOT_GREEN` reset is keyed on the parser version of the last
  successful refresh, not on a DataStore marker. A failed refresh keeps the
  old list, and the next one tries again.
- No second western infusion for Kräutertees. The cold-brew badge alone adds
  no cold brew.
- *Sorte* is the sub-section's title; a tea filed in the section itself
  shows under „Ohne Sorte".
- **Herbal view:**
  - Umami is ignored there, like *Pur*.
  - Switching views clears the chosen *Sorten*.
  - *Alle zeigen* switches view when every hidden hit is of the other kind.
  - The *Kräutertee* chip sits right after *Filter*.
- **Herbal filter sheet:**
  - It keeps *Sortierung* and *Sorte* and drops Aroma and Umami (Q19).
  - *Zurücksetzen* resets the filters but stays in the Kräutertee view.
- **The Q20 hint** shows in the whole Kräutertee view until the first
  successful refresh after the update. While a refresh runs its button reads
  *Fortschritt anzeigen*.

**Sold-out teas (#97).**
- The re-check also covers owned teas already „Nicht mehr im Sortiment",
  because v0.2.0 marked sold-out ones that way. A green or herbal page
  brings such a tea back; any other answer leaves it as it was, and it isn't
  counted again.
- Redirects are judged by `Location` alone and never followed:
  - one leading away from the product means discontinued;
  - one to the tea's own page, or one without `Location`, keeps the tea as
    it was;
  - other 4xx answers keep the tea too.
- A 200 page must name one of the tea's numbers (canonical or own number). A
  page without a product or a breadcrumb keeps the tea and counts as
  defective in the sanity check.
- A re-checked page out of scope goes on the skip list.
- There is no cap on re-checks.
- A kept tea has only the page's sizes: its own size plus the configurator's.
  Only the page's own size knows its stock, so only that size reads „nicht
  vorrätig" (SCR-21's "every size" is narrowed). The other sizes have no size
  link, so their price stays an estimate.
- Sold-out teas show with the *Vorrätig* filter off (CAT-10's default
  hides them).
- For a tea already „Nicht mehr im Sortiment", a plain 401/403 on its old
  page (no challenge or block page) is „not read this time": the tea stays as
  it was and the refresh goes on, so one broken old page can't block every
  refresh. A 5xx, 429, `Retry-After` or a challenge still stops the refresh
  (08 §2.8); a tea not yet discontinued keeps the 401/403 stop (code review).

**Shop link (#98).**
- **Accepted:**
  `https://www.tee-kontor-kiel.de/<slug>/<Artikelnummer>[-<grams>]`.
  - Scheme and host in any case; no userinfo, port or trailing dot.
  - Exactly two path segments of `[A-Za-z0-9-]`, ≤ 300 characters. A share
    of ≤ 2 000 characters must hold exactly one distinct link.
  - Refused: query, fragment, `%`, `/tee-info/`.
  - `…SET` and `…SALE` numbers are out of scope and send no request.
- **Messages:**
  - „Unter diesem Link gibt es im Shop keinen Tee.": 404/410, a redirect,
    another 4xx, or a page with no product or another product.
  - „Diese Shop-Seite kann die App nicht lesen.": robots.txt disallows it,
    over 5 MB, no breadcrumb, or an unexpected error.
- **Fetching:**
  - The link's session never retries, and any stop, the photo's included,
    stores nothing.
  - A linked tea is never „Neu".
  - A number merged into another tea (D-081) opens that tea without a
    request.
- **Order:** `CatalogJobs` makes a refresh wait for a link in progress; a
  link never waits and answers „Gerade läuft ein Abruf". The sanity check
  treats the first refresh as the first import even after a link.
- **Share target:** a separate invisible `ShareTeaLinkActivity` (exported,
  `ACTION_SEND` `text/plain` only). A share never closes the timer; it waits
  until the tabs show. The link sheet hides during an alert (as D-090).
- **Paste field:** it is filled from the clipboard only with an accepted
  link.
- **A tea already in the catalog** (CAT-17 AC6, code review): when its page
  can't refresh it without a stop (no tea page, an unreadable page, offline),
  its detail opens as it was and a toast shows the message. A stop answer
  (challenge, 429, `Retry-After`, 401/403/5xx) shows the message alone.
- Storing a tea from a link deletes its skip entry, so a product skipped
  less than 90 days ago is read by the next refresh, not deleted (code
  review). The parser version of the latest successful refresh is the last
  logged row's, not the latest by the clock.
- A link may be used before the first catalog load.

**Collection and setup (#86, #87, #88, #100).**
- **Vessels:**
  - A fresh install seeds the M1 vessels under their new names.
  - The rename checks only the name.
  - A database restored without the seed markers gets no vessels (D-075).
- **Stock correction:**
  - Input takes a comma or a dot, at most one decimal, 0–9 999,9 g. Finer
    input is refused, not rounded, and „1.000" is refused.
  - An unchanged total writes no event.
  - „Leer" shows below 0,05 g.
  - The sheet opens at 0,0 for a negative stock, with no autofocus. An alert
    removes it unsaved.
  - While a session of this tea is active (also its *Brühen anpassen*), the
    sheet adds „Die laufende Sitzung zieht beim Beenden noch 5,0 g ab." with
    its leaf, while stock deduction is on. The arithmetic is unchanged: the
    session still deducts its leaf at *Sitzung beenden* (code review).
- **Archiving:**
  - „im Besitz" counts only teas that aren't archived. The refresher keeps a
    discontinued catalog tea while any tea, archived ones too, links to it.
  - A removed tea's catalog detail offers *In Sammlung aufnehmen*, which
    repurchases the same tea.
  - The active session (E15) is `active_brew`'s session, READY and FINISHED
    included; a quick timer blocks nothing.
  - Undo lasts 8 s, or longer when accessibility recommends it. Closing the
    picker drops the undo.
  - TalkBack's custom action shows only for removable teas.
  - Start's „Zuletzt gebrüht" keeps the removed tea's photo.
- **Images:** they are in `drawable-nodpi`, picked by the app theme, as for
  A2, A3 and A5. A8 also fills the catalog's other empty states.

<a id="d-095"></a>
### D-095 — Timer top: ink-wash band and tea seal (#102; SCR-05, SCR-07; amends D-058, D-069, D-093 point 2) · *Accepted* (PO, 2026-10-28; details marked „Claude" are Claude's)

**Context.** The Tee-Kontor photos are product shots on a white background.
As the full-width band at the top of the always-dark timer they look like a
pasted-in rectangle (PO feedback, 2026-10-28).

**Decision (PO).**
- **Band and seal.** The top of the timer shows our own dark ink-wash
  picture, a new asset **A13** (`A13-timer-band-dark.png`, dark only,
  delivered by the PO), instead of the tea photo. The tea photo becomes a
  small round **seal** overlapping the band's lower edge.
- **Quick timer.** SCR-07 gets the band too, without a seal (amends D-069
  „no photo").
- **Release.** In v0.2.1, as ticket #102; the PO uploads A13 before it is
  built.

**Details (Claude).**
- **Band.** Full width, about 220 dp, A13 centre-cropped and fading into
  the Steeping background like today's photo (`PHOTO_FADE_START`). ← and ⋮
  keep their 40 dp circles from D-093 point 2.
- **Seal.** An 88 dp washi disc (`#F3F0E9`) with a thin ring, centred, half
  over the band's lower edge. The photo fills the inner circle (crop) and is
  drawn with a multiply blend, so a white background turns into the disc's
  cream and the leaves seem to lie in a bowl. Without a photo, or until it is
  read, the disc shows the type-coloured leaf drawn in code (as the tea
  thumbnails). Decorative: the title names the tea.
- **A4 retires.** The timer fallback photo A4 is no longer shown; its
  resource leaves the app (the source stays in `docs/design/assets/source/`).
- **Alert.** As today, the band and the seal are hidden on the alert surface
  and their space stays, so nothing moves when the alert starts (D-058).
- **Not the timer.** Pure layout and drawing in `feature:brew`; no engine,
  service or alarm code, so the accuracy run is not touched by it (rule 8).

<a id="d-094"></a>
### D-094 — Sold-out teas and adding a tea from a shop link (#97, #98; CAT-07, CAT-16, CAT-17, GEN-01, SCR-19, SCR-21, NFR-PRIV-02; amends D-036) · *Accepted* (PO, 2026-10-28; details marked „Claude" are Claude's)

**Context.** Checked live on 2026-10-28: the shop (Shopware) leaves sold-out
products out of its sitemap. „Bio Sencha Kirishima Yabukita" (TG0170) and
„Bio Sencha Gabalong" (TG0577) show every size as unavailable; their pages
are normal indexable green-tea pages (breadcrumb Grüner Tee › Japan ›
Sencha), but neither is in the sitemap. On page 1 of the Sencha listing, 3
of 24 products were missing from the sitemap. The app discovers products
only through the sitemap (CLAUDE.md rule 7, 08 §2.4), so such a tea never
appears, and a known tea that sells out is counted as discontinued.

**Decision (PO).** (a) Sharing or pasting a shop link fetches that one tea;
(b) teas that drop out of the sitemap are re-checked.

**Details (Claude).**
- **CAT-16 — Sold-out teas stay in the catalog (`Must`, v0.2.1, #97, T4,
  blocked by #89).** During a refresh, every catalog tea whose number is
  missing from the sitemap gets its own page fetched once, within the
  refresh's limits and stop rules. 200, the canonical is that product page
  and the breadcrumb is still green or herbal → kept with the page's stock
  („nicht vorrätig" when no size is available), never counted as
  discontinued. 404/410, a redirect away from the product, or a breadcrumb
  out of scope → discontinued as today (CAT-07 AC4). Not readable this once
  without a stop (08 §2.8) → kept as it was. A challenge, 429 or
  `Retry-After` stops the whole refresh as today. Expected ≈ 5–20 extra
  requests per refresh, inside the existing budget. This is part of network
  use (a), not a new use.
- **CAT-17 — Add a tea from a shop link (`Must`, v0.2.1, #98, T4, blocked
  by #97).**
  - Entry points: the Android share target (`ACTION_SEND`, `text/plain`),
    so UmamiT appears in the browser's *Teilen* sheet, and *Link einfügen*
    in the catalog's overflow menu (a paste sheet, SCR-19).
  - Accepted only: `https://www.tee-kontor-kiel.de/<slug>/<Artikelnummer>`
    with an optional `-<grams>` size suffix. Anything else (another or
    look-alike host, `http`, userinfo, a query string, a category or
    `/tee-info/` URL) → „Das ist kein Tee-Link von Tee-Kontor Kiel." with no
    request.
  - The app fetches only that one product page through the shared polite
    shop session (robots.txt, ≤ 2 requests/s, stop on a challenge, 429 or
    `Retry-After`; offline → message).
  - Green or herbal → stored in catalog.db as a regular entry (photo per
    SET-22), and its detail (SCR-21) opens with *In Sammlung aufnehmen* /
    *Nachkauf erfassen* as usual; the exact price is fetched on adding
    (GEN-01 (c)). Sold out → „nicht vorrätig". Not marked „Neu".
  - Not green or herbal → „Dieser Tee gehört nicht zum Katalog (nur Grün-
    und Kräutertees)." and nothing stored.
  - Already in the catalog → its detail opens, and the page refreshes the
    entry. While a refresh runs → „Gerade läuft ein Abruf – versuch es
    danach."
  - A tea fetched this way that is not in the sitemap is kept by CAT-16's
    re-check on later refreshes.
- **GEN-01: four network uses.** New use (d): the user-triggered fetch of
  one product page the user shares or pastes. Host allow-list, ≤ 2
  requests/s, one connection, robots.txt and the stop rules are unchanged.
  CLAUDE.md rule 7, 02 GEN-01, 06 §4 and §5.7, 07 NFR-PRIV-02 updated.
- **Manifest.** A share-target intent filter for `ACTION_SEND` +
  `text/plain`, exported only for that; no new permission (06 §5.7).

**Consequences.** A refresh makes a few more requests. The shared or pasted
text is untrusted input and is checked before any request. Sold-out teas are
visible and addable, shown „nicht vorrätig". A v0.2.1 checklist item covers
both (09 §4).

**Rejected.**
- *Reading the paginated category listings (`?p=N`)*: `robots.txt` has
  `Disallow: */?` (08 §2.2), so this is off-limits.
- *Leaving it as is*: sold-out teas would never appear, and known ones would
  be counted as discontinued whenever they sell out.
- *Re-check only*: it keeps known teas but can't bring in a tea the catalog
  never had (e.g. one that was sold out at the first load).

02 GEN-01, CAT-07, CAT-16 (new), CAT-17 (new); 03 SCR-19, SCR-21, §5; 06 §4,
§5.7; 07 privacy row, NFR-PRIV-02; 08 §2.2, §2.4, §2.8; 09 §2, §4; CLAUDE.md
rule 7 updated.

<a id="d-093"></a>
### D-093 — v0.2.1 and v0.3: the PO's feedback on `v0.2.0-rc.1` (#79; TMR-21, TMR-22, TMR-23, SCR-05, SCR-31, SET-10, SET-26, PRF-03, BRW-02, WARE-06, INV-03, TEA-04, SCR-03, CAT-01, CAT-10, SCR-19, NFR-PERF-11) · *Accepted* (PO, 2026-10-26, in the session and the grilling of this draft, Q1–Q14; details marked „Claude" are Claude's)

After testing `v0.2.0-rc.1` the PO gave nine feedback points (#79). Asked
as selectable questions, then grilled on the draft (Q1–Q14), the PO
decided (PO, 2026-10-26, unless marked „Claude"):

- **Cut (PO).** `v0.2.1` gets points 2–6 and 9, plus simple versions of 7
  and 8. `v0.3.0` gets point 1, a deep timer change with its own tests. M3
  comes after that, so M3 now releases `v0.4.0` and M4 `v0.5.0` (09 §2).
  Building starts after the PO's OK on the v0.2.1 spec and tickets (rule 1).
  v0.2.1 changes timer code, so each of its release candidates needs the
  accuracy run (09 §4a, D-067, D-073).
- **1 — Quick timers alongside a brew (PO; v0.3, planned).** One brew
  session as today, plus quick timers (TMR-22) that can **always** start
  alongside it. A second brew session still asks „Erst die … -Sitzung
  beenden?" (TMR-21). This partly revives the removed TMR-40. Nothing
  changes in v0.2.1: one active timer, `ActiveTimerExists` as today. The
  engine, `active_brew`, alarms, the foreground service, the Live Update
  and the alert player for two timers are specified with v0.3's plan; its
  timer tests (04 §12) are extended then (rule 8).
- **2 — Legible top bar on the timer (PO: must stay visible; SCR-05).** The
  ← and ⋮ icons sit on a filled circle (Claude: 40 dp, Steeping surface at
  60 % opacity), so they stay visible on bright, white-backed tea photos;
  the icons keep at least 3:1 against the circle whatever the photo.
  (Since [D-095](#d-095) they sit on the ink-wash band; the circles stay.)
- **3 — Pre-alert per infusion (TMR-23, SCR-31, PRF-03, 04 §5).**
  - Each step carries its own pre-alert: *Automatisch* (the default), *Aus*
    or 5–30 s in 5 s steps (PO).
  - **Automatic rule (PO, Q1/Q10):** with B = the recipe's pre-alert ?: the
    global setting (SET-10), frozen at the session's start, and d = the
    step's planned seconds, **N = min(B, 5 · ⌊d / 15⌋)**, i.e. a third of
    the step rounded down to whole 5 s, never more than B. N = 0 means no
    pre-alert. So a step under 15 s never has one (the old rinse rule, now
    for every step), 0:20 → 5 s, 0:30 → 10 s, 1:00 → min(B, 20 s). Because
    N ≤ d/3, the existing rule „none when d < N + 5 s" (E7) never removes an
    automatic pre-alert; it still applies to an explicit value. Long steps
    (≥ 10 min) never have one (TMR-18). The quick timer uses the same
    automatic rule with the global setting (Claude).
  - **Session first, recipe only on request (PO, Q9).** The Schritte-Sheet
    sets the pre-alert per step, including the running step's (its other
    fields stay read-only; Claude); the session-wide selector goes. A change
    applies to **this session**; it goes into the recipe **only** via *Im
    Rezept speichern*, like every other step change.
  - **umamit.db v2.** `brew_step` (frozen schema v1) has no pre-alert, so
    v0.2.1 ships **umamit.db version 2** with a tested migration (05 §9):
    `brew_step.pre_alert_seconds` and `session_step.pre_alert_seconds`
    (INTEGER, null = *Automatisch*, 0 = *Aus*, 5–30); the migration copies
    an active session's `active_brew.pre_alert_override_seconds` into its
    unstarted steps and drops that column. `brew_profile.pre_alert_seconds`
    stays the recipe's base B; `brew_session.pre_alert_seconds` keeps B as
    frozen at the start (Claude).
- **4, 5 — Pre-alert sounds (04 §9.2, SET-26).**
  - PO (Q2, Q3): the pre-alert keeps `USAGE_NOTIFICATION_EVENT`, so it
    follows the notification volume, and the ringer-mode rules (vibrate:
    short vibration; silent: nothing) are unchanged. Four new sounds,
    **mastered much louder**, with a **fall-off of about 3 s** (the old tick:
    0.8 s). The PO picks the default via *Alarm testen*; the others stay
    selectable in the settings (*Vorwarnton*, SET-26).
  - Claude: names Zimbel, Glöckchen, Klangstab, Tiefe Schale; loudness
    -18 LUFS (6 LU above the old tick, 2 LU below the alert; same -1.5 dBTP
    ceiling); today's tick stays selectable as „Ting (leise)"; Zimbel is the
    default until the PO's pick. The chosen sound is loaded ahead like the
    tick (D-092).
- **6 — More vessels (PO; WARE-06).** New seed vessels, as usable volume:
  Kyusu 120, 180, 300 and 360 ml, Becher 250, 300 and 350 ml. Today's
  „Kyusu" (250 ml usable) and „Becher mit Sieb" (400 ml) stay. Free-size
  vessels come with M3's editor (WARE-01). Claude: capacities 150, 220,
  350, 420 ml and 300, 350, 400 ml; fixed IDs like the M1 seed, added once
  to an existing install at the first start of v0.2.1
  (`seedVesselsV2Added`, inserted by ID, so never twice).
- **7 — Remaining amount (PO, Q8; INV-03, BRW-02).** In the brew setup a
  tap on „… g übrig" opens a small sheet „Wie viel ist noch da?": the user
  enters the **new total** in g (0 allowed = leer). The difference to the
  current stock is stored as an `ADJUSTMENT` inventory event (no schema
  change: the type exists in v1). M3's history (SCR-29) shows it as
  „Bestand korrigiert".
- **8 — Removing a tea (TEA-04, SCR-03).**
  - PO: a long press on a tea in the tea picker offers *Aus Sammlung
    entfernen*, with undo („*Tanabata* aus der Sammlung entfernt" ·
    *Rückgängig*). The tea is **archived**, not deleted.
  - PO (Q7): not possible while that tea is in the active brew session: the
    item is greyed out with „Erst die Sitzung beenden".
  - PO (Q13): archived teas disappear from Start (*Nochmal brühen*, the
    recently brewed teas) and from the tea picker; the journal keeps their
    entries with name and photo.
  - PO (Q6): adding it again from the catalog **un-archives the same tea**
    and records the package as a PURCHASE event, as D-085 does for a
    repurchase of an archived tea. Claude: its earlier stock events stay
    (a wrong amount is fixed with point 7); the catalog row of an archived
    tea shows no „im Besitz" badge and its detail offers *In Sammlung
    aufnehmen*.
- **9 — Kräutertees in the catalog (CAT-01, CAT-10, SCR-19, 08 §2.4, §3).**
  - PO (Q4): only the shop's **Kräutertees**, not fruit tea or rooibos (the
    section's path is read from the live breadcrumb when built; Claude).
  - PO (Q5): style `herbal`, default recipe 2 g / 250 ml in the Becher,
    100 °C, one infusion of 6:00, no cold brew. A shop brewing grid or text
    still wins (08 §2.5); „Rezept prüfen" only when the shop gives no values
    (style default), as for green teas.
  - PO (Q11, Q14): hidden by default; the filter chip *Kräutertee* switches
    the list to the Kräutertees only (grouped by *Sorte*); tapping it again
    returns to the green teas. The *Pur* filter does not apply in the
    Kräutertee view.
  - PO (Q12): the first load gets about 1–2 min longer (their pages are
    already fetched for classification today; their photos are new);
    NFR-PERF-11 stays ≤ 20 min. `catalog_skip` keeps no rules version, so
    v0.2.1 clears its `NOT_GREEN` rows once and the first refresh after the
    update re-classifies everything (≈ 16 min, once); the PO accepts this.
    No catalog.db schema change: a herbal tea is told apart by its style
    (Claude).

- **Spec grilling (PO, Q15–Q21, 2026-10-26; spec #81).**
  - Q15: journal entries of an archived tea show no *Nochmal brühen*; to
    brew it again, add it back from the catalog.
  - Q16: no downgrades. The app never opens the database destructively;
    going back to `v0.2.0` would need an uninstall. Fixes always ship as a
    newer version.
  - Q17: the Schritte-Sheet shows *Automatisch* with the value it will use
    („Auto · 5 s", „Auto · keine").
  - Q18: the two M1 seed vessels are renamed „Kyusu 250 ml" and „Becher mit
    Sieb 400 ml", only if their names are still the seed names.
  - Q19: the Umami filter is hidden in the Kräutertee view; the filter sheet
    there offers Herkunft, Bio, Neu, im Besitz and Vorrätig.
  - Q20: before the first refresh after the update, the empty Kräutertee
    view shows „Kräutertees kommen mit dem nächsten Abruf (≈ 16 min,
    einmalig)" with a *Jetzt laden* button.
  - Q21: removing a tea shows a snackbar of about 8 s with *Rückgängig*;
    afterwards the tea can come back via the catalog.

02 TMR-02, TMR-11, TMR-21, TMR-22, TMR-23, SET-10, SET-26 (new), SET-31,
PRF-03, BRW-02, BRW-11, WARE-06, INV-03, TEA-04, CAT-01, CAT-10 and the
removed TMR-40; 03 SCR-03, SCR-04, SCR-05, SCR-19, SCR-25, SCR-31; 04
header, §3.2, §5, §9.1, §9.2, §10, §11 (E7, E15), §12; 05 §3.3, §3.6, §3.7,
§3.10, §3.12, §6, §9; 07 NFR-PERF-11; 08 §2.4, §2.5a, §2.6, §3; 09 §2 and
§4; `docs/design/assets.md` updated.

<a id="d-092"></a>
### D-092 — The first cue of a process starts as fast as the others: sounds loaded ahead, the alert player on its own thread (TMR-23, TMR-30, rule 8; amends D-059) · *Accepted* (Claude, 2026-10-26)

The rc gate's accuracy run on 83f2c5c (API 36, `v0.2.0-rc.1`) failed on one
cue: the pre-alert of step 1 started its sound 1178 ms late (handled 26 ms,
by the alarm), over the 1 s maximum. The alert of step 1 took 311 ms; steps
2–50 stayed at 90 ms or less (p95 61 ms and 39 ms). M1's rc run had its
maximum, 910 ms, very likely on the same first step. Decided:

- **Cause** (from the code and the AOSP sources; the run logged no split).
  Step 1 is the first cue of a new process: the test kills the timer process
  before it starts. Each cue loaded its sound inside its own command, on the
  main thread, between the handled event and the „alert" log entry that
  marks the sound's start. `MediaPlayer.create` prepares synchronously. On
  first use in a process this means:
  - the `MediaPlayer` class initialises `libmedia_jni`, unless the zygote already did;
  - the player registers with AudioService (`trackPlayer`);
  - it opens a session with the media server, which opens and parses the
    file through `media.extractor`.

  Warm, this costs about 20 ms (steps 2–50). In the fresh process the cue
  spent about 1.15 s between the handled event and its sound, which this
  load explains; the rest of the command is the settings cache and a few
  binder calls (ringer mode, vibration, audio focus). The alert's 311 ms is
  the same load for its own file. Very likely the main thread also first ran
  the service's command for the deadline: its first `startForeground` of the
  alert.
- **Sounds loaded ahead.** While a timer is active, the controller keeps the
  alert player's sounds ready. At the end of every turn, after the turn's
  own cues, it calls the alert port's `keepReady` with „a timer is active";
  idle releases them. The player keeps the tick and the current tone loaded
  (`AlertSoundOutput.prepare`), so a cue only starts its loaded player. A
  used sound is loaded again right after its cue (posted, so after the cue
  has started).
  - A session starts in READY, so its sounds are loaded before *Start*. A
    step's first cue comes at least 5 s after the start (E7).
  - Loading never waits for the settings file. It uses the tone known at
    that moment, and a changed tone is loaded at the next turn.
  - A failure never costs the sound (rule 8). A sound that does not load
    plays as before, loaded on play. A loaded player that broke while it
    waited (the media server died) is dropped. A loaded player whose start
    throws or reports an error is replaced once by one loaded on the spot.
  - Cost: two prepared players while a session is active, none when idle.
    The media server holds no codec and no audio track for them until they
    start.
- **The alert player's own thread (amends D-059).** The player's commands,
  repeats, sound callbacks, focus changes and loads run on the `UmamiTAlert`
  thread (a `HandlerThread`), no longer on the main thread. So a busy main
  thread no longer delays a cue: the service posting its notification, an
  activity starting from the full-screen intent, the timer screen drawing.
  A load no longer blocks the UI either.
  - Loading on the player's thread keeps the order simple. A cue queued
    behind a load waits at most for that load, which is what it would have
    spent loading its own sound. Recovery replays an alert before its turn's
    `keepReady`.
  - The thread keeps the default priority. A raised priority needs a
    privilege the app may not have, and a failed `setThreadPriority` would
    end the thread.
  - The tone preview (SET-09) stays on the main thread with its own output.
- **Where the time went is logged** (06 §5.6). The entry of a step's tick or
  alert now has a part before its scheduled and actual time: `queued 2 ms,
  settings 0 ms, outputs 15 ms, sound ready` (or `sound loaded on play`,
  `no sound`). It gives the time from the controller's call to the start of
  the command, the wait for the settings, and the start of the outputs
  (matrix, vibration, focus, sound). `FireTime.parsePlayed` reads it. The
  accuracy run logs it for each step, and its summary names each event's
  slowest start with this split. The targets are unchanged.
- **Expected:** step 1 now does what steps 2–50 do: the first vibration and
  focus request of the process (binder calls) and the player's start, with
  no load. Expected: about 100 ms or less. If a later run still shows a slow
  first cue, the split names the phase.
- **Tests:**
  - `AndroidAlertOutputsTest`: a loaded sound starts without a load and is
    loaded again after its cue; a sound not loaded loads on play; sounds no
    longer wanted are released and not loaded again. A player broken while
    it waited, one that reports an error when started and one whose start
    throws: each still sounds.
  - `FirstCueTest`: the production player with the real sound output loads
    nothing for the first tick and alert after `keepReady`.
  - `AlertPlaybackTest`: the tick and the stored tone are loaded, a changed
    tone is followed, loading never waits for the settings, the split is
    logged.
  - `AlertDeliveryTest`: ready from a session's start to its end, for a
    quick timer, and again after recovery in a new process.
    `ProductionAlertPortTest`: a session's first tick and alert start loaded
    sounds.
  - Format and report tests for the new log part and summary lines. The
    timer tests of 04 §12 are unchanged and pass.

  04 §4 and §9.1, 06 §5.6 and 09 §4a updated.

<a id="d-091"></a>
### D-091 — Fixes from the first full live run of the catalog refresh (spec #62, PR #78; CAT-01, CAT-04, CAT-06 – CAT-09, NFR-PERF-11; amends D-074, D-087, D-090; extends D-089) · *Accepted* (Claude, 2026-10-26; the `hojicha_powder` default confirmed by the PO, 2026-10-26)

Full live run 1 against the shop (1,747 requests, no stop, 261 teas; its
report gives counts and article numbers only) took 1,207 s, over
NFR-PERF-11's 20 min, and found PNG photos, sets, sale duplicates and two
style gaps. Fixed test-first with synthetic fixtures (D-016); parser
version 6. Decided:

- **Processing overlaps the request gaps (NFR-PERF-11, CAT-07):** of the
  1,207 s, ≈ 874 s were the 500 ms gaps; the rest was parsing, re-encoding
  and storing between the requests. Product pages, photos, guide pages and
  guide images are now fetched one after another while the previous answer
  is processed (`fetchOverlapped`): a rendezvous hands each answer over, so
  at most the next answer waits while one is processed (≤ 2 held at once).
  The wire is unchanged: the same requests in the same order through the
  one shop session, one connection, ≤ 2 requests/s; after a stop nothing
  more is fetched. A cancel, or a failure while processing (storage, a bug),
  cancels the request in flight — possibly the next page's — and drops its
  answer; nothing is sent after it. The sanity check still runs before the
  first photo request, the photos before the guides, and the swap stays
  atomic. A test with a 200 ms gap, 150 ms latency and 150 ms processing
  measures ≈ 201 ms per page and 203 ms per photo (≈ 326 ms before); the
  first refresh is expected at ≈ 15–16 min (the gaps alone ≈ 14.6 min),
  later ones at ≈ 3 min (06 §5.3, 07, 08 §2.4).
- **PNG and WebP sources (CAT-08, CAT-09; amends D-074 and D-087's JPEG
  only):** every one of the 27 teas without a photo had a PNG. The
  transcoder reads complete JPEG, PNG and WebP images (a JPEG ends with EOI,
  a PNG with IEND, a WebP is as long as its RIFF header says), decoded once
  with sampling, never larger than the largest target needs; the output is
  unchanged (≤ 1200 px WebP and thumbnail, the cover JPEG). Transparency is
  flattened onto white, as the shop shows it (JPEG has no alpha).
- **Image body limit 16 MB (amends D-090):** photos and guide images are read
  up to 16 MiB (`BodyLimit.IMAGE`); pages, robots.txt and sitemaps stay at
  5 MiB (`BodyLimit.PAGE`). Run 1's largest image answers declared
  9.3–11.7 MB. A larger answer still leaves only its URL out. The seed photo
  download and the exact price fetch keep the page limit (06 §4, 08 §2.8).
- **No endless re-fetching (CAT-08, CAT-09):** a photo or guide image that
  failed for good — over the image limit, or no decodable JPEG, PNG or WebP
  within the transcoder's limits — is remembered by its URL in the new
  `catalog_image_failure` (05 §4.2b) and not asked for again while the URL
  (with the shop's `?ts=`) stays the same. A 404, a robots.txt rule or a
  network error is no failure for good. The swap replaces the table with the
  failures the run met, remembered or new (a tea kept unread keeps its
  photo's), so a failed run remembers nothing and a URL no longer met is
  forgotten; `rules_version` (now 1) is raised whenever the readable formats
  or limits change, so every failure is tried once more. This saves the 45
  requests run 1 repeated on every later refresh. `catalog.db` v1 changes in
  place: it is frozen only from `v0.2.0-rc.1`.
- **Sets (CAT-01):** set numbers count in any case and with letters after
  „SET" (`AT0635Set`, `AT0636Set`, `…SETc`, `…SETSALE`, a variant `-SET`); a
  product of the green sections whose name says set („Set" as a word,
  „3er-Set", „Probierset", „Geschenkset", „Jubiläumsset", …; not „Setouchi"
  or „Sunset") or whose breadcrumb is a set category (`…-sets`, `teesets`) is
  skipped with reason `SET` (`TG0182`) (08 §2.2, §2.4).
- **Sale duplicates (CAT-01, CAT-02):** a number ending in „SALE" in any case
  (`AT0516SALE`, `AT0810SALE`) is a reduced duplicate of a regular tea and is
  left out without a request, like a set; the regular number stays the
  catalog entry. A sale-only listing is a clearance, so its tea leaves the
  range (kept hidden while owned, D-086). The log and the details count the
  sale duplicates (08 §2.4).
- **Mizucha (CAT-06, extends D-089):** „Mizucha" in the name brews cold by
  default like a Mizudashi, under any breadcrumb (the style from breadcrumb
  or name, else `green_generic`, not flagged); its form is TEA_BAG with bag
  words in the name, else loose leaf (`AT0549`, `AT0675`; 08 §2.6).
- **Hōjicha powder (CAT-04, CAT-06):** a new built-in style
  `hojicha_powder` (08 §3: LOW umami, 30 days, 2,0 g/TL, Usucha 2 g / 70 ml /
  80 °C with a 0:20 whisk, no cold brew). „Pulver"/„Powder" with hōjicha in
  the breadcrumb or the name maps to it under any breadcrumb, form POWDER;
  its grid rows become one whisked Usucha recipe like a matcha's, never a
  leaf recipe or a cold brew (`AT622`, `AT0845`; 08 §2.5, §2.6). The
  journal's placeholder colour is hōjicha's.
- **Parser version 6** (the scope and style rules changed): content hashes of
  v5 don't count as changes (D-086).

Left as they are: run 1's other observations (the cold-brew grid values of
`AT0380`, `AT0384`, `AT0429`, hot grid temperatures, 4 g packages, the
`AT0429-50g` package number, the ten non-tea pages without a breadcrumb that
are fetched on every refresh) are what the shop states or minor; they stay
for later.

<a id="d-090"></a>
### D-090 — M2 milestone review fixes (spec #62, PR #78; CAT-07, CAT-10, CAT-12, CAT-15; amends D-086) · *Accepted*, the images' 5 MB limit amended by D-091 (Claude, 2026-10-26)

The T4 milestone review of M2 (areas A: network, refresh, data; B: UI,
navigation, strings) found three Should items in each area and a set of
Optional ones. Fixed test-first; decided:

- **Oversized answers (A-S1):** a 2xx answer over the client's 5 MB body
  limit is not read and becomes `ShopFetch.TooLarge`: only that URL is left
  out (a photo or guide image is missing, a product page counts as
  unavailable, the sitemap means „shop changed"), never a stop, never
  retried, logged with its declared size. A non-2xx answer over the limit
  counts by its status (a 503 still stops). Before, one oversized photo ended
  every refresh as „offline" (08 §2.8, 06 §4). Aligning the limit with the
  40 MP source limit waits for the full live run's largest photo.
- **CAT-15 per seed tea and part (A-S2, amends D-086):** the single
  `seedTeasLinked` marker is replaced by `seedTeaLinksDone` (05 §6, never
  released): per seed tea, the *description* is done once the tea has one
  (linked, or its own, which is never overwritten) and the *cover* once it
  has one (linked, from the seed photo download or its own) or the refresh
  loaded no photos (SET-22 off). A part not done — the tea's page unread on
  the first refresh, no description or photo in the catalog, a seed created
  later by the D-075 retry — is tried again after every later successful
  refresh; a done part never again, so a description the PO empties later
  stays empty. A seed tea with a description of its own still gets the
  catalog photo when it has no cover. Each tea is still one transaction that
  checks again, and the parts are marked after it (idempotent).
- **No crash from a live page outside the refresh (A-S3, rule 8):** the exact
  price fetch catches everything but a cancellation (the parser's stack
  overflow included), keeps the estimate and logs one line; *In Sammlung
  aufnehmen* maps an unexpected `RuntimeException` to „Failed", writing
  nothing.
- **The two flaky tests (T1):** `CatalogRefresherTest.eachStopKeepsTheOldCatalog`
  ran six refreshes on the real 500 ms request gap (≈ 15 s) and exceeded
  `runTest`'s 60 s under load; it is split into one test per stop, and the
  refresh tests that don't measure the rate use a client with a shortened
  gap (`ShopClient.forTesting(requestIntervalMillis)`), so no test waits on
  the wall clock (only `requestsGoOutAtMostTwicePerSecondOnOneConnection`
  keeps the real limiter). `ExactPricesTest.onlineTheSizesPageGivesTheExactPrice`
  passed 120 of 120 runs here (60 under CPU load, 60 inside the whole suite),
  so its cause is inferred, not reproduced: the same real-time dependence. It
  was the only plain-JVM shop test, so it loaded the client and the server
  cold in the app class loader of the overloaded test JVM in which the test
  above timed out, and with `NetworkRetry.NONE` one stall past OkHttp's 10 s
  read timeout gives no price. It now runs under Robolectric
  like the other shop tests (warm, shared sandbox), on the shortened gap, and
  its assertions name the session's diagnostics. The shop client's dispatcher
  thread is a daemon, so test clients never keep a JVM alive.
- **Backs and taps act only on the resumed screen (B-S1, B-O1):** every
  on-screen back/up in the tab stacks and the root (setup, quick timer,
  settings, *Über*, diagnostics, tea detail, guides) is wrapped in
  `dropUnlessResumed`, and a catalog row, the *Ratgeber* row and a guide's
  links act only while their screen is resumed; a quick double tap no longer
  pops a tab's start screen (a blank tab or app) or pushes a screen twice. A
  guide's link to itself opens nothing.
- **Secondary name in the catalog rows (B-S2):** CAT-10 and spec #62
  story 17 stand; the row shows it on one line under the name (cut off when
  longer), and 03 SCR-19 says so again.
- **Refresh runner (A-O1):** a start while a cancelled refresh still cleans up
  starts nothing, and an ended run's completion never overwrites the next
  run's state.
- **Stale connections (A-O6):** the client keeps an idle connection for 30 s
  instead of 5 min, since its connection retries are off.
- **Setup page 3 (B-O8):** *Katalog jetzt laden* ends a seed photo download
  still running before the refresh starts, so two shop jobs never overlap and
  a stop of one never leaves the other sending (rule 7); CAT-15 gives the
  covers.
- **Add sheet (B-O2, B-O3):** while it saves, neither a swipe nor back
  closes it; an alert still removes it at once (rule 8), and a save that
  fails meanwhile brings it back with its input and the message after the
  alert; a started save runs to its end. The editable price is written
  without thousands separators („1234,50"), and „1.234,50" or „1.000" g typed
  the German way are read.
- **Texts (B-O9, B-O10):** counts and units keep a no-break space („276
  Grüntees", „keine Änderungen", „100 g", „30 MB"); Settings says the first
  load takes about a quarter of an hour and later ones a few minutes.
- **On-device checklist (B-S4):** 09 §4 M2 gains installing over `v0.1.0`
  with data kept, the seed teas after the first load (as far as M2 shows
  them) and *Nachkauf erfassen* on Tanabata.

<a id="d-089"></a>
### D-089 — Parser fixes from live sample 2 (spec #62; CAT-03, CAT-05, CAT-06, CAT-09) · *Accepted* (Claude, 2026-10-25; tea recipes stay out of the Ratgeber, confirmed by the PO, 2026-10-26)

A second live sample of the shop (24 product pages, 4 guides; no stop
signal) found products the scope dropped and smaller derivation and guide
defects. Fixed test-first with synthetic pages of the same structure (D-016);
parser version 5. Decided:

- **Japan teas without a type level (D1):** 5 of 22 sampled green teas had
  only a tea family's page (`/tee/japantee/teefamilien/<family>/`) as
  breadcrumb and were skipped as not green. A product of `/tee/japantee/`
  without a type level is now green when its name names a green type (or
  tencha, aracha, „Grüntee", …) or it carries a steaming or shading badge, and
  neither its name nor its path names another tea (Koucha/Wakoucha, Oolong,
  Schwarz-, Weiß-, Kräutertee) or teaware (Kyusu, Kanne, Chawan, Set, …).
  The positive signal is required, so a Japanese black tea or a potter's
  teaware on a family page stays skipped. Its *Sorte* is the type its name
  names (the shop's type-page title; „Fukamushi" → „Sencha"), so the style
  follows (08 §2.3, §2.4 item 3).
- **Style keywords (D2):** „Bi Lo Chun", „Pi Lo Chun" → `bi_luo_chun`; the
  Korean grades Ujeon/Woojeon, Sejak, Jungjak/Joongjak, Daejak →
  `korean_green`, and *Anbaugebiete* Korea without a keyword too (08 §2.6).
- **Gongfu row (D3):** the grid's `st-zubereitung-china` row is a „Gongfu"
  recipe (`GONGFU`); a non-Japanese tea's default is the row of its style's
  default method (Gongfu for the Chinese styles), else the first row. Cells
  „~80°" (no C) and „~20(30) Sek" (the first value) are read. The catalog
  detail labels the row „Gongfu" (08 §2.5 steps 1–2).
- **Cold-brew bags (D4):** „Mizudashi"/„Kaltaufgussbeutel" in the name makes
  the Mizudashi mapping (TEA_BAG, cold-brew default) under any breadcrumb, the
  style from breadcrumb or name; „Aufgussbeutel"/„Teebeutel" alone make the
  form TEA_BAG. A grid row at „kalt" is the cold brew's row (its amount and
  every given infusion), never a hot recipe. Bags per volume („1Stk./500ml")
  weigh the package's grams over the bags the name counts; unknown, the
  style's cold-brew ratio brews the stated water, flagged as style default
  (08 §2.5 steps 2, 6; §2.6).
- **Brewing prose (D5):** a paragraph naming a video is never brewing prose;
  bags per volume count as an amount, so „ein Beutel auf 0,3–0,5 Liter …
  zweimal aufgießen" is (08 §2.3).
- **Harvest year (D6):** only from the *Ernte* badge and the title, never from
  the prose (travel reports' and founding years, link texts) (08 §2.3).
- **Matcha cold brew (D7):** a powder gets no cold brew, badge or not; 08 §3's
  matcha has none and the sencha fallback (10 g / 1 L, 4 h) is no powder recipe.
- **Guides (G1–G4):** the comment section inside the article block, the
  blog's date list, a video intro without a video and a closing tag line are
  left out; images are told apart by their decoded path (raw spaces vs `%20`)
  and stored encoded; promotions, events and recipes (`sonstiges-`,
  `teeverkostungen-`, `aktionstees-`, `blog-`, `teerezepte-` and recipe or
  event words in the slug) are no guides, so `teerezepte-matcha-eistee`
  leaves 08 §2.7's list; ten green-type keywords join the selection, within
  D-087's caps (08 §2.7).

<a id="d-088"></a>
### D-088 — Brewing a new tea from the picker, „Rezept passt" (#72) · *Accepted* (Claude, 2026-10-25)

The ninth M2 slice (spec #62, #72) lets the PO brew a tea just bought
(D-073, CAT-12, CAT-05). Decided while building it:

- **Where the flow runs:** *Aus dem Katalog* in the tea picker (SCR-03)
  closes the picker and opens Sammlung › Katalog in the Sammlung tab, at its
  root on the *Katalog* segment: the same SCR-19 → SCR-21 → SCR-23 as from
  Sammlung, with the navigation bar and the active brew bar where they always
  are. No separate full-screen copy of the catalog.
- **The save:** an add or a repurchase saved in this flow puts the shell back
  on Start and opens the setup (SCR-04) of that tea above it, so back from
  the setup returns to Start like any brew from the picker. An add from
  Sammlung itself stays in Sammlung.
- **Leaving without a save:** back from the catalog list, or a tap on Start,
  returns to Start with the picker open again; a tap on Journal or Statistik
  ends the flow quietly. Starting and leaving the flow pop the Sammlung stack
  to its root, so none of its screens stays behind; nothing is written until
  the save. The flow survives a configuration change and process death.
  A tea opened from a *Ratgeber* article during the flow (SCR-20, D-087) is
  the same detail, so its save opens the setup too; backing out through the
  guides still returns to the picker.
- **Alerts first (rule 8):** an alert removes the add sheet (D-085) and the
  picker (D-062); the picker does not come back while an alert plays, and the
  setup is never opened during one (the alerting timer opens instead).
- **SCR-04:** the flag moves from under the tea name to above the steps,
  with its reason, as on SCR-21: one shared `RecipeCheckNote` in `core:ui`,
  whose reason strings move there from `feature:catalog`.
- **SCR-06:** *Rezept passt* shows for a session whose tea is flagged when
  the sheet is due (the tea read then, not the session's snapshot). It clears
  `recipe_check_reason` of that tea only (`TeaRepository.clearRecipeCheck`, one
  `UPDATE`, no schema change), then reads „„Rezept prüfen“ ist entfernt.“; the
  sheet keeps its 10 s and its alert rules (D-061). The write is never
  cancelled by an alert starting right after the tap.

<a id="d-087"></a>
### D-087 — *Ratgeber*: Tee-Info guides offline (#76, CAT-09) · *Accepted*, JPEG-only images amended by D-091 (Claude, 2026-10-25)

The last M2 slice (spec #62, #76) brings Tee-Kontor's green-tea guides into
the catalog (CAT-09, SCR-20, 08 §2.7). Decided while building it:

- **Staging and swap:** `catalog_guide_staging` is a real table of
  `catalog.db` with exactly `catalog_guide`'s columns (embedded, like the
  teas' staging, D-077), part of the schema frozen from `v0.2.0-rc.1`. The
  refresh stages the guides after the photos and the swap replaces
  `catalog_guide` in the same transaction as the teas, so a refresh that
  stops, is cancelled or fails keeps the old guides (05 §4.5). `catalog_guide`
  gets `published_at` (JSON-LD `datePublished`, epoch ms) for the list and the
  article's source line.
- **Which guides:** the sitemap's `/tee-info/<slug>` URLs whose slug contains
  one of 08 §2.7's keywords, at most 40 per refresh; with more, the most
  relevant are kept: the slugs naming the most keywords, then the shorter
  slug (a general guide before a narrow recipe), then the slug. An article that is gone
  (404, robots.txt) is left out; one that no longer reads (no article block)
  keeps its current version; without any selected article in the sitemap
  every current guide is kept, so a renamed blog never empties *Ratgeber*.
- **Conversion** (08 §2.7, §2.8): the article block without its title;
  headings, paragraphs (a bold first line becomes a heading), line breaks,
  bold, italics, absolute http(s) links, lists, tables as lists of rows
  („Menge · Zeit"), images; scripts, styles, forms, staff tips, tracking
  pixels and embeds are left out, a player becomes a plain link; control
  characters become spaces. Title, date and hero come from the JSON-LD
  `BlogPosting` (title else the article's `h1`); the body doesn't repeat the
  hero. `body_markdown` is the app's own small Markdown subset
  (`GuideMarkdown`, `core:domain`), rendered by the app, not by a library
  (D-008's in-house option).
- **Images:** through the refresh's shop session (robots.txt, the 2
  requests/s, the stops), only while SET-22 *Katalog: Fotos laden* is on, at
  most 4 per article (the hero first), JPEG sources only (D-074), re-encoded
  to ≤ 1200 px WebP in `filesDir/catalog/guides/`. Each file is named after a
  hash of its URL (the shop's `?ts=` included), so an unchanged image is never
  fetched again and a failed refresh never overwrites a file the stored guides
  show; after every run the files no stored guide shows are deleted. An image
  that is missing or doesn't decode is left out of the text.
- **Links:** a link to a product page of the shop whose base number is a tea
  of the new catalog (merged split numbers included, D-081) becomes an in-app
  link to that tea, a link to a stored guide one to that guide
  (`umamit://catalog/<key>`, `umamit://guide/<slug>`: markers in the stored
  text, no registered links); `linked_catalog_keys` lists the teas. Every
  other link opens the browser, as does a link to a guide the catalog no
  longer holds (its shop page) and *Im Browser öffnen*; none of them is a
  request of the app (GEN-01).
- **Request budget** (D-076, NFR-PERF-11): at most 40 guide pages and 160
  images, so ≤ 200 requests (≈ 100 s at 2 requests/s) on a first refresh,
  inside 08 §2.4's G; later refreshes fetch the ≤ 40 pages again and only new
  or changed images (≈ 0–40 more). `GuideLimits` holds the caps; a refresher
  test checks the requests stay within them on a first and a later refresh.
  The refresh sheet counts the guides read („Ratgeber: 12"), the developer
  details and the diagnostics log their counts.
- **SCR-19 / SCR-20:** the catalog list's *Ratgeber* row (only while guides
  exist) opens the list: the A11 header (placeholder landscape until the PO's
  image), a line on what it is, the guides by title with hero thumbnail and
  date. The article: hero, serif title, „Tee-Kontor Kiel · Tee-Info · 12. März
  2024", the text, *Im Browser öffnen*. Everything is read from `catalog.db`
  and local files, so it works offline after a refresh.

<a id="d-086"></a>
### D-086 — Later catalog refreshes: summary, discontinued teas, seed teas linked (#70) · *Accepted*, CAT-15 part amended by D-090 (Claude, 2026-10-25)

The seventh M2 slice (spec #62, #70) makes a refresh of an existing catalog
cheaper and tells what changed (CAT-07 AC3/AC4, CAT-08, CAT-15). Decided
while building it; `umamit.db` stays at version 1, `catalog.db` needs no
new column:

- **Which pages** (08 §2.4 item 5): the known green parents are the
  catalog's keys and the base numbers merged into them (D-081), read back
  from their packages' article numbers (each page's own size keeps its
  number), so a split tea's second number counts as known, not new. With the
  base numbers new in the sitemap and the skip entries due again (90 days)
  that is every product not freshly skipped; the diagnostics log counts the
  three groups.
- **Discontinued** means missing from the sitemap or now out of scope. A
  known tea whose numbers are still in the sitemap but whose page couldn't
  be read this time (robots.txt, 404, a page without product) is copied into
  the new catalog unchanged, so one bad answer neither hides a tea nor makes
  it „Neu" again later. A tea that left the range stays, hidden, with
  `discontinued = 1` while a collection tea links to it (the owned keys read
  from `umamit.db` before the swap, D-076); otherwise it is deleted with its
  packages and photos. Back in the shop, it is in the range again and keeps
  its `first_seen_at`.
- **Photos** (CAT-08): `photo_url` holds the URL the stored photo came
  from; a photo is downloaded only when the page's URL differs (the shop's
  media URL carries the image's version, `?ts=`) or no photo is stored. A
  changed photo that can't be fetched leaves the old one and its old URL,
  so the next refresh asks again. `photo_etag` stays empty: an ETag needs a
  conditional request, which still costs one.
- **„geändert"**: `content_hash` is `<parser version>:<SHA-256>` of what the
  shop shows of a tea (page fields, photo URL, packages with prices and
  stock), not of what the app derives (style, recipes, flag, plain text).
  Hashes count only under the same parser version, so an app update with a
  new parser does not report every tea as changed; a tea back in the range
  counts as changed too.
- **Summary** (CAT-07 AC3), in the sheet and the result notification: „276
  Grüntees · 12 neu · 5 geändert · 2 nicht mehr im Sortiment · 3 Rezepte
  prüfen", each part only above 0 and never broken across lines
  (non-breaking spaces). „Rezepte prüfen" counts the teas flagged
  since this refresh (new ones, or known ones not flagged before), not every
  flagged tea, which D-082's `flagged_count` did; the log stores the same
  counts. The first load leaves out „neu" (every tea is, as with the badge,
  D-083); a later refresh without change says „keine Änderungen".
- **SCR-21:** a discontinued tea kept for the collection shows the tag
  „Nicht mehr im Sortiment" and no *Im Shop öffnen* (as INV-09 AC2).
- **CAT-15:** after each successful swap, until done: the seed teas
  Tanabata and Hisui (by id, CAT-13) whose `catalog_key` the catalog holds
  and which have no description get the catalog's plain-text description
  and, only without a cover, its photo re-encoded as their own JPEG
  (D-076). Each tea is one `umamit.db` transaction that checks again; the
  marker `seedTeasLinked` (DataStore, 05 §6) is set after the first run
  that found a seed tea, so a description emptied later is never filled in
  again, and a run cut short before the marker changes nothing the second
  time. Before the seed exists (D-075) nothing is marked. A failure is
  logged and never fails the refresh, whose swap is in; the steps after the
  swap are not cancellable.

<a id="d-085"></a>
### D-085 — Adding and repurchasing a catalog tea (#71) · *Accepted* (Claude, 2026-10-25)

The eighth M2 slice (spec #62, #71) adds a catalog tea to the collection or
records a repurchase (CAT-12, CAT-13, TEA-01, INV-01). Decided while building
it:

- **Duplicate guard:** the collection tea linked to a catalog key is the one
  with that `catalog_key` that is not archived, else the oldest. An owned key,
  Tanabata's `TG0213` and Hisui's `TG0557` included, always records a
  PURCHASE event, never a second tea; the guard is read again inside the
  write's transaction. A repurchase of an archived tea brings it back
  (TEA-04: archived when used up). A repurchase changes nothing else.
- **The copy** (`AddFromCatalog`, one transaction in `umamit.db`): the derived
  recipes in their order with the default first and exactly one default (the
  style's recipes when the catalog has none), each keeping its source; the
  tea's „Rezept prüfen" reason; an INITIAL event with the grams, the price in
  cents (or none) and `package_label` „250 g"; `catalog_key`, `product_url`,
  vendor „Tee-Kontor Kiel", *Sorte*, style, form (08 §2.6), umami level
  (the shop's scale, else the style's), the plain-text description, and the
  fields of 08 §2.3's conversion table. The country becomes its ISO code
  from a list of the shop's country names (unknown: empty). A bare season
  („Sommer") counts only in the *Ernte* badge, never in the prose; prose
  shading counts only in a sentence about shading. Nothing links the copy to
  the catalog but `catalog_key`, so no refresh changes it (CAT-13).
- **Cover:** the catalog's own `<key>.webp` is re-encoded with the cover
  target (≤ 2048 px JPEG 85) into `photos/<id>.jpg` with a `CATALOG_COPY` row
  (D-076); only the app's own WebP is accepted there, the shop's images stay
  JPEG only. Without a usable photo the tea has no cover.
- **Purchase date → `occurred_at`:** today is now; an earlier day is noon of
  that day in the phone's zone. The date can't be after today.
- **Exact price** (GEN-01 (c), D-074): one `ShopSession` (`NetworkRetry.NONE`)
  per open sheet, so robots.txt is read once however many sizes are priced.
  Only the chosen size is fetched, once per sheet, and only when its price is
  an estimate or missing and it has its own page; the page's own size from the
  refresh is the shop's price already and sends nothing. The page must show
  that size (its unit content), else there is no price. Offline, on any stop
  or for another size the estimate (or no price) stays. A price typed before
  the exact one arrives is kept; saving never waits.
- **SCR-23:** the sizes smallest first with their prices, *Andere Menge* with
  an amount field, the price field with a line saying where the price comes
  from, *Kaufdatum* with Material's date picker, *Aufnehmen* / *Nachkauf
  erfassen*. The sheet opens on the size bought last (repurchase), else the
  size with the shop's own price, else the first priced one. A failed save
  keeps the sheet open with a message. An alert removes the sheet in the same
  frame and it stays closed; it can't be opened during one (rule 8). SCR-21's
  primary action is *In Sammlung aufnehmen*, or *Nachkauf erfassen* for an
  owned tea. The route reports the saved tea for the picker flow (#72).

<a id="d-084"></a>
### D-084 — A killed process's stale foreground notification is taken over and removed (TMR-41, D-044; corrects D-080) · *Accepted* (Claude, 2026-10-25)

`ProcessDeathTest` (CI emulator, API 35) failed again with D-080 in place:
after the kill, notification 1 (`ONGOING_EVENT|NO_CLEAR|FOREGROUND_SERVICE`)
was still posted next to the new process's alert. Root cause from the AOSP
sources (`NotificationManagerService`, `ActiveServices`, `ServiceRecord`):

- **D-080 was wrong.** A killed service does not keep its notification:
  `killServicesLocked` brings it down (`START_NOT_STICKY`, no start pending)
  and `cancelForegroundNotificationLocked` cancels the notification as the
  system. The restarted service gets a new record with no foreground
  notification. An app's cancel never removes a notification flagged
  `FLAG_FOREGROUND_SERVICE` (`cancelNotificationWithTag` refuses it for callers
  other than the system), before `startForeground` or after it, so D-080's
  second cancel changed nothing. It is removed.
- **What leaves notification 1:** the process dies while it posts an update of
  its foreground notification. In the test this is D-071's re-post, 0.5–0.8 s
  after the start, about when the test kills. `enqueueNotificationInternal`
  flags the update as the foreground notification on the binder thread (the
  service is still in the foreground) and only later queues it on the
  NotificationManager's handler. The system's cancel for the dead service can
  come in between. The update is then posted after the cancel, as a new
  notification that keeps the flag but belongs to no service. The app cannot
  cancel it, and the new process's `startForeground(2)` does not touch it, so
  it stays next to the alert, which Android 16 would group and silence (D-044).
  D-071's re-post stays: any update of the foreground notification can meet a
  kill, and the re-post only makes the test's kill meet one more often.
- **Decided:** when the service starts in the foreground while it is not
  there yet (its first start, or one after a refusal), it first looks among
  its active notifications for the other timer ID with
  `FLAG_FOREGROUND_SERVICE`. If it finds one, it calls
  `startForeground(other, copy)` with a copy of it (only-alert-once, no
  full-screen intent) and then `startForeground(id)` as before. The switch
  makes the system cancel the old ID (`setServiceForegroundInnerLocked` →
  `cancelForegroundNotificationLocked`). The copy's post, that cancel and the
  new notification's post go through the ActivityManager's handler in this
  order, so the stale notification is gone before the new one is queued. It
  works both ways: a stale notification 1 before the alert, a stale alert
  before notification 1. The diagnostics log records the takeover (06 §5.6).
- **Never at the alert's cost:** a failure of the lookup or of the extra call
  is logged, and the normal start follows. The cost is one
  `getActiveNotifications` call per such start, plus one `startForeground`
  call when a stale notification exists. After a takeover, the new ID's
  `startForeground` is the service's second one, which Android checks again
  against the background-start rules. The exact alarm's allowance and the
  battery exemption (ONB-02) allow it. If it is refused, the existing
  fallback (04 §6.3) leaves the foreground, which also removes the
  taken-over notification, and posts the alert alone.
- **Limits:** a stale notification stays until the service next starts in the
  foreground. The paths without the service (a long step, a demoted session,
  the end of the session) cannot remove it. A notification whose system
  cancel is still on its way (a stop right before the start) can be taken
  over too; it goes all the same. So far a stale notification has been seen
  only in the emulator test.
- **Tests:** `StaleForegroundNotificationTest` (Robolectric) uses shadows that
  add the AOSP rules Robolectric leaves out: an app's cancel is refused for a
  flagged notification, an update keeps the flag, and a foreground switch or a
  removing stop cancels as the system. They do not model the race itself.
  `ProcessDeathTest` keeps its assertion. Its failure message now adds the
  kill time and the device state, and it logs whether the kill left
  notification 1. `timer-stress.yml` (dispatch only; it must be on `main`
  before its first dispatch, D-039) repeats one instrumented test with
  `scripts/stress-instrumented.sh`. It can sweep the kill moment across the
  re-post (`umamitKillAfterStartMs`). 04 §6.3 and §12 and 06 §6 updated.

<a id="d-083"></a>
### D-083 — Finding teas in the catalog: search, filters, „Neu" (#69) · *Accepted* (Claude, 2026-10-25)

The catalog list (SCR-19) gets CAT-10's search, filters and sort. Decided
while building it:

- **„Neu"** is a tea whose `first_seen_at` is the start of the latest
  successful refresh, and only when an earlier successful refresh exists: the
  first refresh finds every tea, so 274 „Neu" badges would say nothing. A
  refresh keeps a known tea's `first_seen_at` at the swap (D-077); a refresh
  that fails or is cancelled ends nobody's „Neu". The badge stays until the
  next successful refresh.
- **Filters** live in a pure `CatalogBrowser` (`core:domain`) over the
  current range; the search runs on `catalog_fts`. Chips and sheet as in
  03 SCR-19 (D-076). „Rezept prüfen" is a row badge, not a filter (CAT-10 has
  none). *Umami* filters by the shop's umami scale (08 §2.6: 0 none … 4–5
  very high); a tea without a scale passes only „Egal". Several origins of
  one tea („Japan, Korea") count each. Stock unknown counts as available.
- **Search:** every word of the search text is a prefix, all required, any
  other character only separates words (so no FTS syntax reaches the
  query). `catalog_fts` uses the `unicode61` tokenizer, so „älpler" finds
  „Älpler" and „goko" finds „Gokō" (05 §4.1; `catalog.db` is not frozen
  before `v0.2.0-rc.1`). The filters apply to the hits too; the hits they
  hide are counted with *Alle zeigen*, so a flavoured tea is „searchable but
  hidden" (D-073) without vanishing silently.
- **„im Besitz"** is a catalog key some collection tea links to
  (`tea.catalog_key`, archived teas included), read from `umamit.db` by a
  new query; no schema change.
- **Not stored:** search, filters and sort live as long as the screen; the
  list always opens in the default view (D-073), so 05 §6's `catalogSort`
  setting is dropped.
- **One badge slot:** `TeaListItem`'s `badges` is a wrapping row
  (`FlowRowScope`); the catalog rows put „Neu", „im Besitz", Bio and
  aromatisiert there next to D-082's `CheckRecipeBadge`.

<a id="d-082"></a>
### D-082 — Timer recipes from the catalog and „Rezept prüfen" (#68) · *Accepted* (Claude, 2026-10-25)

The fifth M2 slice (spec #62, #68) derives each catalog tea's timer recipes
(CAT-04) and flags those to check (CAT-05). Decided while building it:

- **Where the code lives:** 08 §3's green styles are `BuiltInStyles` in
  `core:domain` (D-076), so the collection editor (M3) can use them too;
  `StyleMapper`, `RecipeDeriver` and `CatalogRecipes` (page → style, recipes,
  reason) are in `core:catalog-parser`. A recipe without a tea is a
  `RecipeTemplate` (`core:model`) with the timer's own `RecipeStep`s; it becomes
  a `Recipe` when the tea is added (#71). Tests check every derived recipe
  against the engine's step model and PRF-03/04.
- **Derivation details** (08 §2.5, listed there): a recipe's source is its
  least certain field; grid columns by header label; the prose split into hot
  and Kaltaufguss paragraphs; all steps from the style when the first is;
  style follow-ups keep the style's temperature rise; 5 s / 1 min / 5 ml /
  0,1 g grids; a conflict only against every grid row; one reason per tea by
  priority (implausible, conflict, style default, text, unmapped style). The
  PO's second western infusion keeps the recipe's source (the shop's grid
  sits next to it in the detail).
- **Style mapping** (08 §2.6): the name and the *Sorte* are searched with the
  more specific keywords first; a flavoured tea no keyword names is
  `flavored_green`, a Mizudashi bag without a style name `green_generic`, both
  unflagged; a flavoured sencha stays Japanese, so its Kyusu row is the default
  (08 example `TG0569`).
- **Refresh:** each green tea's `style_key`, `derived_recipes_json` (05 §4.1)
  and `recipe_check_reason` are written with its row; the log's
  `flagged_count` counts the flagged teas of a swapped-in catalog (for #70's
  summary; D-086 counts only those flagged since the refresh). The sanity check's brewing threshold (08 §2.9) is on: a green tea
  lacks brewing information with neither a grid row nor brewing prose.
- **Parser fixes from live sample 1** (parser version 4, after #67's 3): the
  brewing prose is only paragraphs that are instructions (two of temperature, time and amount
  per volume, or one with a brewing word); tasting notes on the liquor, video
  pointers, package sizes and storage hints are not, because a wrong value from
  the text is worse than the style default. Each multi-value property span's
  own leading „, " is dropped before joining; taste-scale labels lose their
  colon („Umami:"), so the Umami level maps; control characters become spaces
  in every text read (08 §2.3, §2.8).
- **SCR-21:** *Abgeleitete Rezepte* follows *Zubereitung*: „Rezept prüfen"
  with its reason when flagged, then each recipe (name, „Standard", „4,0 g ·
  200 ml · 65 °C" or „kalt", the steps with a hotter step's temperature,
  „Aufschlagen 0:20" for matcha) and the source of a non-default recipe not
  from the table. **SCR-19:** a flagged tea's row carries the
  `CheckRecipeBadge` chip (03 §3.8), a slot of `TeaListItem` that the other
  chips (#69) can use too.

<a id="d-081"></a>
### D-081 — Catalog package sizes and prices, split numbers (#67) · *Accepted* (Claude, 2026-10-25)

The catalog shows each tea once with all its sizes (CAT-02, CAT-11, 08 §2.4
items 6–7). Decided while building it:

- **No size page is fetched** (D-076's budget): the sizes are the union of
  the page's configurator options („250g 3% gespart¹") and the sitemap's
  `<base>-<grams>` URLs. The fetched page's own size (its unit content, else
  the checked option) has the page's price and stock; a larger size is
  estimated by 08 §2.4's formula (savings from its option, else 0 %), half up
  to the cent; a smaller size has no price („Preis beim Hinzufügen"); the
  other sizes' stock is unknown. Each size keeps its sitemap URL as
  `variant_url` (the product page for the page's own size; none for a size
  only the configurator names, whose article number is then `<base>-<grams>`).
  `label` is „250 g · 3 % gespart" as in 05 §4.2. Parser version 3.
- **Split numbers** are merged after all pages are read, before the staging
  tables are filled: same normalised name (lower case, sizes such as „30g" and
  punctuation removed; the URL slug when a page has no name), only when the
  pages' own sizes differ. Two pages with the same name and the same size are
  two teas (e.g. two harvests), and a size can be a package only once
  (primary key `catalog_key`, `grams`). The lowest number is the key and its
  page gives the details; a size's own page wins over another page's
  estimate; the tea is in stock when any of its pages is; the price per 100 g
  is the key page's. Merges are written to the diagnostics log (numbers only).
- **SCR-21 *Packungen*:** after *Zubereitung*, one row per size, smallest
  first: „12,40 €", „≈ 30,07 € · 3 % gespart", „Preis beim Hinzufügen", with
  „nicht vorrätig" for a size known to be out of stock and a note under the
  rows explaining „≈" when an estimate is shown. Numbers and units are joined
  by non-breaking spaces so a row never breaks inside „3 % gespart" at large
  font sizes. The list keeps the price per 100 g (CAT-10, #64).

<a id="d-080"></a>
### D-080 — The other timer notification is cancelled again after `startForeground` (TMR-41, D-044) · *Accepted*, corrected by D-084 (Claude, 2026-10-25)

The CI emulator test `ProcessDeathTest` (API 35) once found notification 1
still posted, with its foreground flag, next to the alert of a step whose
process had been killed. The service cancels the other notification before
`startForeground`. After a process death, though, the system can still hold
notification 1 as the restarted service's foreground notification, and it
ignores a cancel of the current foreground notification. The service now
cancels the other notification once more right after `startForeground`, when
it is no longer the foreground one. Only one of notifications 1 and 2 is
shown at a time again (D-044). Robolectric cannot reproduce the system's
refusal, so the emulator test stays the check.

*Corrected by D-084:* the system does not keep a killed service's
notification, and it refuses the app's cancel of a flagged notification
after `startForeground` too, so the second cancel had no effect and is
removed. The notification is a stale one that the kill left behind; the
service takes it over and so removes it.

<a id="d-079"></a>
### D-079 — Catalog refresh in the background: service, notifications, entry points (#65) · *Accepted* (Claude, 2026-10-25)

The refresh keeps running with the phone locked (CAT-07 AC1, 06 §5.3) and
gets its three entry points. Decided while building it:

- **Hosting:** the refresh job stays in the app's own scope
  (`AppCatalogRefreshRunner`, D-077). `CatalogRefreshService`, the `dataSync`
  foreground service, keeps the process running while it lasts and follows
  the runner's state for its notification; it has no refresh logic, so the
  screens keep observing the same `CatalogRefreshRunner`, and *Abbrechen* in
  the sheet and in the notification cancel the same job. The system creates
  the service; the app hands it the refresh through `CatalogRefreshHost`
  (the timer's `TimerHost` pattern, D-057).
- **The timer first (rule 8, CAT-07 AC8):**
  - own service, channel `catalog` (LOW, so Android 16 sorts it into the
    silent section and never groups it with the alert, D-044), own
    notification IDs (10 progress, 11 result), own wake lock, no alarms;
  - the progress notification changes at most every 2 s: Android drops an
    app's notification updates beyond five a second, and the timer's Live
    Update must never be one of them;
  - every start calls `startForeground` before anything else, and only the
    latest start stops the service (`stopSelfResult`): a service started with
    `startForegroundService` that stops without it crashes the process;
  - a refused foreground start (Android 15+'s six-hour daily `dataSync`
    budget) is logged, and the refresh then runs while the app lives, as in
    #64; at the end of the budget (`onTimeout`) the refresh is cancelled and
    the service stops at once;
  - an error the refresher does not turn into an outcome itself (e.g. a stack
    overflow on a page) ends the run as „shop changed" instead of the process.
- **Locked phone:** a foreground service alone does not keep the CPU awake.
  The service holds a partial wake lock of its own (`UmamiT:CatalogRefresh`),
  renewed with each progress update and expiring 10 min after the last one, so
  a stuck refresh never drains the battery; it is released at the end
  (`WAKE_LOCK` was declared for the timer already; 06 §5.7 updated).
- **Notifications:** the progress „Katalog wird geladen · Seiten: 120 von 1560
  · Produkte: 40" with *Abbrechen*; at the end a quiet result („Katalog
  geladen · 274 Grüntees · 12 neu", or „Katalog nicht geladen" with the
  reason), none after *Abbrechen* or when the result was seen in the app
  already; seeing the result in the app or starting the next refresh removes
  it. A tap opens the app where it was, like its launcher icon. A deep link
  into SCR-19 would be a navigation of its own that must give way to alerts
  like the timer's; it is left out for now (03 §2.4 updated).
- **Entry points:**
  - Sammlung › Katalog's ⋮ menu: *Katalog aktualisieren* starts the refresh
    and opens the sheet (during an alert it starts without the sheet);
  - Settings, section *Katalog* (SET-23): „Letzter Abruf" with the date of
    the last successful refresh from the refresh log („21. Okt. 2026", or
    „Noch nie") and *Jetzt aktualisieren*, which waits while a refresh runs;
    progress and result show in the notification;
  - the setup's third page (ONB-03): *Katalog jetzt laden* shows the progress
    inline and *Weiter* finishes the setup while the load goes on; *Später*
    loads nothing. The page belongs to every run of the setup (also
    *Einrichtung erneut zeigen*); a phone set up before M2 never sees it
    (D-073).

<a id="d-078"></a>
### D-078 — Catalog tea detail: parser rules, photos, shop link (#66) · *Accepted* (Claude, 2026-10-25)

The second M2 slice (spec #62, #66) fills every CAT-03 field, downloads the
photos and shows a catalog tea's detail (SCR-21). Decided while building it:

- **Parser rules** (08 §2.3, parser version 2): every detail field is read
  from a copy of the page without the other products it embeds (D-076).
  Several values of one property are joined with „, ". The region is the
  first of a fixed list of growing regions (Japan, China, Taiwan, Korea)
  named as a whole word in the title, else in the description. *Flavoured*:
  an *Aroma* other than „naturbelassen", an ingredient other than tea, or the
  flavoured section; roasted rice (genmaicha) is no flavouring, and footnotes
  („*aus kontrolliert ökologischem Anbau") are no ingredients. The brewing
  prose is the description's paragraphs about brewing (Zubereitung, Aufguss,
  amounts in g or ml, °C), verbatim; `brew_table_raw` holds one object per
  grid row, `{"kind":"japan","cells":[{"label":"Menge","text":"~4g/100ml"},…]}`,
  the labels from the grid's header.
- **Description** (08 §2.8): staff tips, scripts, styles, forms, images and
  embeds are removed (a video player becomes a plain link); headings become
  bold paragraphs; only paragraphs, line breaks, bold, italics, lists and
  absolute http(s) links remain, without attributes but `href`.
  `description_text` is its plain text, one paragraph per block.
- **Photos** (CAT-08): the sanity check runs before the photos, so a broken
  parser costs no photo request. Each green tea's photo goes through the
  refresh's shop session; a photo that is missing, disallowed or doesn't
  decode leaves that tea with the photo it had (or none), a stop ends the run
  like any stop. `photo_file` stores the file name (`<key>.webp`). After
  every run, successful or not, the photo files of teas the catalog doesn't
  hold are deleted. With SET-22 off, a refresh fetches no photo and keeps the
  photos already stored. Until #70 every photo is downloaded again on each
  refresh (no change detection yet; D-086 adds it).
- ***Im Shop öffnen*** (D-072): the size of the latest INITIAL or PURCHASE
  event is matched by grams, else by its package label; without a match the
  product page opens. The page goes to the browser as a `VIEW` intent
  (`CATEGORY_BROWSABLE`); without a browser a short message says so.
- **SCR-21 layout:** the photo full width (4:3, the type-coloured leaf
  without one) with the back button over it; name, secondary name, tags
  (*Sorte*, Bio, aromatisiert, Coldbrew geeignet, nicht vorrätig), price per
  100 g and *Im Shop öffnen*; then the sections *Steckbrief*, *Geschmack*
  (scales as five leaves, aroma, taste), *Beschreibung* and *Zubereitung*
  (the shop's grid per method and its text, verbatim), each only when the
  shop fills it. The derived recipe, the packages and *In Sammlung
  aufnehmen* follow with #68, #67 and #71.

<a id="d-077"></a>
### D-077 — Catalog refresh tracer bullet: staging, skip list, parser rules, sheet (#64) · *Accepted* (Claude, 2026-10-25)

The first M2 slice (spec #62, #64) runs *Katalog laden* from the empty
Katalog tab to a filled list. Decided while building it:

- **Staging tables** (05 §4.5): `catalog_tea_staging` and
  `catalog_package_staging` are real tables of `catalog.db` (part of its
  frozen schema from `v0.2.0-rc.1`) with exactly the columns of `catalog_tea`
  and `catalog_package` in the same order (the staging entities embed the
  live ones; a test holds them equal), so the swap is `INSERT … SELECT *` in
  one transaction. Each refresh empties them first and after any end without
  a swap. `catalog_package` is keyed by (`catalog_key`, `grams`).
- **The skip list changes only with a successful swap**, in the same
  transaction. A refresh that stops, is cancelled or fails the sanity check
  keeps no classification, so a broken parser or a renamed shop section can
  never hide teas for 90 days; the price is that an interrupted first
  refresh classifies again next time. A product page without a breadcrumb is
  neither kept nor skipped and counts as defective for 08 §2.9.
- **Sanity check** (08 §2.9): defective pages count among the products and
  as lacking name, price and article number; the brewing threshold is judged
  only once the parser reads the brewing grid and prose (#68).
- **Parser rules** (08 §2.3): the secondary name is the Japanese or Chinese
  text in the title (kana, kanji, CJK brackets), the name the rest without the
  size suffix; *Sorte* is the title of the breadcrumb's green type level
  (`…/japan/<type>/`, `/tee/japantee/<type>/`, `matcha-teepulver`), else
  empty; the price per 100 g comes from the unit price, else from price and
  content; stock is unknown (null) when the buy box shows neither a buy
  button with delivery information nor a stock notification. Only the
  product's own buy box is read. The live sample run checks these rules.
- **Discovery** (08 §2.4): a product URL is `/<slug>/<article number>`; per
  base number the parent's page is fetched, else its `M` page, else the
  smallest size. A sitemap that is missing, disallowed, broken or lists no
  product is a structural failure.
- **Outcomes:** success; cancelled; blocked (challenge, Cloudflare block,
  429, `Retry-After`); offline; shop unavailable (401/403/5xx without
  Cloudflare); shop changed (structural, also for an unexpected error, so a
  refresh bug never crashes the process the timer runs in); storage failed.
  Each failure carries technical details for *Details kopieren* (counts and
  article numbers, no shop content) and is logged (`catalog_refresh_log`,
  diagnostics log).
- **Hosting until #65:** an app-scoped job (`AppCatalogRefreshRunner`) runs
  the Android-independent `CatalogRefresher` while the app's process lives;
  #65 moves it into the `dataSync` foreground service behind the same
  `CatalogRefreshRunner`. Closing the sheet never stops the refresh; the empty
  state then offers *Fortschritt anzeigen*. An alert removes the sheet in the
  same frame (rule 8).
- **Sammlung › Katalog** is a slot of `CollectionScreen` that the app fills
  with `feature:catalog` (no feature-to-feature dependency, 06 §3); the
  catalog's lazy list scrolls itself below the segmented control. Until #69
  the list shows every tea of the current range by name, with *Sorte* and
  price per 100 g; until the PO's A8 arrives, the empty state shows the
  placeholder landscape.

<a id="d-076"></a>
### D-076 — M2 implementation clarifications (exploration of the shop and the code) · *Accepted* (PO for the request budget, Claude for the rest, 2026-10-25)

An exploration before the first M2 slices (spec #62) found contradictions
between the specs, the tickets and the code. Resolved:

- **Request budget (PO):** the live sitemap lists ~1,560 distinct base
  article numbers across all shop categories, not ~700. The first refresh
  classifies every unclassified parent once, so it takes ≈ 15–18 min
  (background service, phone may be locked); later refreshes stay ≈ 3–4 min.
  No prefix shortcut, so no green tea is missed. NFR-PERF-11 first refresh
  ≤ 20 min; 06 §5.3 and 08 §2.4 updated.
- **Retries:** CAT-07 AC2's „≤ 3 retries" applies only to timeouts and
  connection errors of a refresh request. Each retry is a new call through
  `ShopClient` (rate-limited, allow-listed), after 2/4/8 s. Stop signals
  (challenge, block, 429, `Retry-After`) and HTTP errors are never retried.
  This keeps D-050 (one network attempt per call). Seed photos (run once)
  and the exact price fetch (falls back to the estimate) don't retry.
- **Structural failure:** the refresh aborts by the three thresholds of
  08 §2.9, not by a single „< 50 % parsed" rule (#64's wording).
- **Manifest:** `FOREGROUND_SERVICE_DATA_SYNC` is already in the allowed
  list of the manifest check (06 §5.7); M2 adds the permission and the
  service to the manifest only.
- **Filters:** CAT-10 is the source of truth. SCR-19 shows the frequent
  filters as chips and the rest in a filter sheet; #69 aligns 03.
- **catalog.db columns:** a plain-text `description_text` for search (FTS),
  and the thumbnail as `<key>_thumb.webp` next to the photo (05 §4.1).
- **Discontinued teas kept when owned:** the refresh reads the owned
  `catalog_key`s from `umamit.db` before the swap (two databases, no shared
  transaction).
- **Built-in styles:** 08 §3's style table is code for the first time
  (pure module). Styles without a cold-brew default use sencha's (08 §2.5
  step 6).
- **Photos copied into the collection** (*In Sammlung aufnehmen*, CAT-15)
  are re-encoded to the collection's JPEG format, so restore detection
  keeps them.
- **Page parsing:** selectors are scoped to the product's own sections
  (product pages embed other products); the nested brewing grid is
  de-duplicated; staff tip blocks are stripped from the description; the
  sitemap `.xml.gz` is decompressed by the app (the shop sends no
  `Content-Encoding`). Challenge markers are checked only on refusals, as
  `ShopClient` already does, because normal pages contain the marker text.

<a id="d-075"></a>
### D-075 — Seed retry at app start (#58 D-O1, M2-11) · *Accepted* (Claude, 2026-10-25)

A seed that failed on the first start (D-066: logged, the download left due)
was never retried after *Weiter*, so Start and the tea picker stayed empty.

- **Every app start retries the seed** while `seedDataCreated` is false:
  `UmamitApplication` calls `FirstStart.retrySeedAtAppStart`, once per
  process, in the first start's IO scope. It runs only once the setup is done
  (`onboardingCompleted`); before that the setup page's first start seeds. The
  seeder's own rules stay: one transaction, nothing on a database that already
  holds personal data (a restore), the marker set afterwards. A failure is
  logged and retried at the next start; nothing is shown.
- **Only the seed, never the photo download.** The retry sends no request,
  so the one-time seed photo download (ONB-06, D-050) stays a first-start
  setup thing: it is never repeated, and it is not started outside the setup
  either (GEN-01, NFR-PRIV-02). The covers of seed teas created by a retry
  come with the first catalog refresh (CAT-15).
- Instrumented tests mark the seed done before the setup (`SetupDoneRule`),
  so the retry never seeds in the emulator.
- **The #58 fixes of M2-11** ship with it (recorded here since D-090): the
  navigation labels stay on one line and shrink to fit at a large font scale
  („Sammlung" at 200 %, 03 §6), the tea picker sheet uses the page's
  `surface` colour, a no-break space joins a number and its unit („80 °C",
  „4,0 g") with „Std."/„Min." everywhere, and list separators come from
  `strings.xml`.

<a id="d-074"></a>
### D-074 — Shared shop session and image targets (M2 prefactor, #63) · *Accepted*, JPEG-only sources amended by D-091 (Claude, 2026-10-25)

**Shop session (`core:network`, rule 7):** the robots.txt handling and the
stop mapping move out of the seed photo download into `ShopSession`, which
the catalog refresh (CAT-07) and the exact price fetch (CAT-12) reuse. One
session = one job; every request still goes through the one `ShopClient`.
- `ShopSession.open` reads robots.txt once with D-050's status rules: 2xx
  parsed; 404/410 → everything allowed; a stop answer → the session starts
  stopped; any other answer → everything disallowed.
- `fetch` returns `Fetched`, `Disallowed` (robots; nothing sent),
  `Unavailable(code)` (3xx, 404, other 4xx: only this URL is left out) or
  `Stopped(cause)`. The causes: network, challenge, Cloudflare block page,
  429, `Retry-After`, 401/403/5xx.
- Retries per D-076 are the session's `NetworkRetry`: `REFRESH` retries a
  timeout or connection error after 2, 4 and 8 s (robots.txt included), each
  a new call; `NONE` (seed photos, exact price) never retries. An oversized
  answer or a refused second attempt is not retried; when the retries fail,
  the session stops with `NETWORK`.
- A stop is **sticky**: every later `fetch` returns the same stop without a
  request. Fetches of one session are serialised, so a fetch queued behind
  the stopping one is never sent. A cancellation is no stop.
- A foreign host still fails with `HostNotAllowedException` (a programming
  error). The seed photo download behaves exactly as before (D-050); its
  `FailureCause` maps one-to-one from the session's `StopCause` plus
  `STORAGE`.
- Not in this step: `Crawl-delay` (the shop sets none, 08 §2.2) and a stop
  shared across concurrent sessions.

**Image targets (`PhotoTranscoder`, CAT-08):** re-encoding takes a target
(long-side cap, optional short-side cap, format, quality) and never
upscales or changes the aspect ratio. The source is decoded once, sampled for
the largest target, and re-encoded without metadata.
- Collection photos and covers: ≤ 2048 px JPEG 85 (05 §3.9, unchanged).
- Catalog photo: ≤ 1200 px WebP (lossy, quality 80, D-073).
- Catalog list thumbnail: WebP quality 80, short side 256 px (a round list
  thumbnail up to 64 dp at 4× density), never larger than the photo. The
  thumbnail size was not fixed before; it adds roughly 10 KB per tea to
  CAT-08's 25–35 MB.
- Sources stay JPEG only, as the shop's images are (08 §2.2); truncated,
  oversized (> 8192 px per side or 40 MP) or undecodable images give no photo.

<a id="d-073"></a>
### D-073 — M2 cut, behaviour and process (grilling with the PO) · *Accepted* (PO, 2026-10-25)

Grilled with the PO before the M2 spec. The catalog itself stays as
specified (CAT-01 … CAT-15, 08 §2); these answers settle what the specs left
open or what M1 changed.

**Scope of M2**
- *In Sammlung aufnehmen* and *Nachkauf erfassen* (CAT-12, SCR-23) ship with
  M2, not M3. A new tea is immediately in the tea picker and brewable with
  its derived recipe and stock deduction. M3 adds the *Meine Tees* screens,
  the tea detail (with INV-09) and the recipe editor.
- The tea picker's *Aus dem Katalog* leads through the catalog to the add
  sheet and back to the brew setup with that tea selected. A catalog tea is
  never brewed without adding it (stock and journal stay correct).
- A tea flagged „Rezept prüfen" is adjusted per brew in the Schritte-Sheet;
  the „Gespeichert" sheet then offers *Rezept passt*, which clears the flag
  (CAT-05, SCR-06). The full editor stays in M3.
- The Tee-Info guides (CAT-09) are the last slice of M2; if they hold up the
  first rc, they follow in a later rc.
- From #58, M2 also takes: the seed retry at app start (D-O1), the °C spacing
  and abbreviations (U-O7), the navigation label at font 2.0 (U-O1), the tea
  picker's surface colour (U-O8) and the unused imports (U-O9).

**Behaviour**
- On a phone whose first-start setup is done, the catalog is first loaded
  from the empty state of Sammlung › Katalog (*Katalog laden*) or Settings;
  ONB-03 shows only on fresh installs. No extra card on Start.
- A refresh may run during a brew session; the timer always has priority
  (CAT-07 AC8, rule 8).
- Teas first seen in the latest refresh get a „Neu" badge and a *Neu* filter
  (CAT-10).
- The catalog list opens grouped by *Sorte*, available teas only, with the
  „Pur" filter on: flavoured teas are loaded and searchable but hidden until
  the filter is changed (CAT-10).
- Photos: max 1200 px WebP plus a list thumbnail, about 25–35 MB (CAT-08;
  was 1600 px).

**Process**
- Claude tests the parser against the live shop from the cloud session:
  samples of about 20 teas during development and one full run before each
  rc, with the app's own limits (≤ 2 requests/s, one connection, robots.txt,
  stop on a challenge or 429). Nothing from the shop is committed (D-016).
- The accuracy run (09 §4a, D-067) is required from M2 on only for an rc whose
  changes touch timer code, and always before the final release.
- An early design check of the catalog list, tea detail and add sheet
  follows the first UI slice (like #39); the final design review comes with
  the release candidate.
- Test plan (agreed with the PO for the M2 spec, kept lean): parser and
  recipe rules on synthetic pages; the whole refresh against a simulated shop
  (rate limit, stop signals, variant merge, atomic swap); adding and
  repurchase on a test database; screenshots of the catalog list, tea detail,
  add sheet and refresh sheet (light and dark, font 2.0 for list and detail);
  Claude's live-shop runs; the PO's checklist. No emulator test of the
  refresh: the PO checks a refresh during a brew on the phone (09 §4 M2).
- The PO generates the illustrations A8 (empty catalog) and A11 (Ratgeber
  header) now, for the first M2 rc.

<a id="d-072"></a>
### D-072 — One-tap reorder from the tea detail (CAT-11, INV-09) · *Accepted* (PO, 2026-10-25)

The PO wants ordering a particular tea to be as easy as possible. The specs
had *Im Shop öffnen* on the catalog tea (SCR-21) but hid it in the overflow
menu of the collection tea (SCR-09), where reordering starts. Decided:

- SCR-09 shows „Beim Tee-Kontor nachbestellen" in VORRAT & KOSTEN for every
  catalog-linked tea (new INV-09); it leaves the overflow menu. It is a filled
  button when the stock is *Knapp* or *Leer* (INV-05), otherwise a text link.
- The link opens the shop page of the **package size bought last**: every size
  has its own URL (08 §2, `catalog_package.variant_url`). The size comes from
  the grams of the tea's latest INITIAL or PURCHASE event; without a match, or
  when the size is gone, the product page (`product_url`) opens. SCR-21's
  *Im Shop öffnen* follows the same rule for teas already in the collection.
- No schema change: v1 is frozen (D-054), and the existing columns
  (`inventory_event.delta_grams`, `catalog_package.grams`/`variant_url`,
  `tea.product_url`) are enough.
- Opening the browser is the user's own navigation, not one of the app's
  three network uses (GEN-01, D-036): the app sends no request.
- Teas without a `catalog_key` and discontinued catalog teas show no link.
  It ships with M3 (SCR-09); SCR-21's rule ships with M2.

<a id="d-071"></a>
### D-071 — A Live Update overtaken by the foreground start's late post is posted again (TMR-41, #54) · *Accepted* (Claude, 2026-10-23)

CI on API 35 showed notification 1 still with READY's text more than 10 s
after *Start* (`TimerNotificationsDeviceTest`, intermittent). `BrewTimerService`
shows a notification in two ways: the first command calls `startForeground`,
a later one with the same ID calls `notify`. In AOSP the notification of
`startForeground` is not posted synchronously: `ServiceRecord.postNotification`
queues it on the ActivityManager's handler, which later hands it to the
NotificationManager, while the app's `notify` is a direct binder call. An
update within milliseconds of the start can therefore reach the
NotificationManager first and be overwritten by the start's older content:
for up to 15 s while RUNNING (the refresh, 04 §8.3, repairs it) and for good
in READY, PAUSED or FINISHED. On the phone the likely trigger is a
notification action that cold-starts the process (the recovery's
`startForeground`, then the action's update). Decided:

- The service remembers when its last `startForeground` succeeded
  (`SystemClock.uptimeMillis`). A same-ID update posted with `notify` within
  `REPOST_WINDOW_MS` (1 s) of it is posted once more after `REPOST_DELAY_MS`
  (0.5 s) on the main looper, with `FLAG_ONLY_ALERT_ONCE` added, so the copy
  never rings or vibrates again.
- At most one re-post is pending; any newer command replaces it. It is
  dropped on `stopForeground` and in `onDestroy`, and it posts only while
  that ID is still the foreground notification.
- Same-ID updates do not switch to `startForeground`: every later call is
  checked again against the background-start rules and could be refused.
- The port and the controller do not change. The service logs which path
  each notification took (`startForeground`, `notify`, re-post); the device
  test's failure dump adds `dumpsys activity services`, and CI's logcat
  filter adds `NotificationService`, `Shedding`, `enqueue rate` and
  `ActiveServices`.

Consequences: a phase change is no longer left stale behind the start's
late post, as long as that post lands within 0.5 s of the update; a later
one is still repaired by the 15 s refresh while RUNNING. The device test
keeps its 10 s timeout: 04 §8.3 tolerates 15 s only for progress within a
state. Unit test: `LateForegroundPostTest` (Robolectric). 04 §6.3 and §8.3
updated.

<a id="d-070"></a>
### D-070 — Release gate and workflow hardening (M1 milestone review, #54) · *Accepted* (Claude, 2026-10-23)

The M1 milestone review (data S1–S3 and O2, timer S3) found that a release
could be built from a commit whose CI or schema checks were red, that the job
holding the signing key gave a tag-pinned third-party action a write token,
and that nothing forced a schema version bump to ship its migration. Decided:

- **Release only a green commit.** `release.yml` requires a successful
  `ci.yml` run on the exact commit it builds (GitHub API, `actions: read`;
  runs are matched by the workflow path, not by job names, so renaming a CI
  job does not break it). It also repeats CI's schema freshness check and
  `scripts/check-frozen-schemas.sh` after its unit tests. The rc flow is
  therefore: push the bump, wait for CI, dispatch.
- **Signing and publishing are separate jobs.** The `build` job holds the
  keystore with `contents: read` (plus `actions: read`). It runs only
  GitHub's own actions (`checkout`, `setup-java`, `upload-artifact`) and no
  longer `gradle/actions/setup-gradle`, which injects init scripts into every
  Gradle build of its job, the signing build included; a read-only cache
  saved little for a handful of releases. It verifies the APK
  (`verify-release-apk.sh`, fingerprint against
  `signing/release-cert-sha256.txt`) and uploads it as an artifact. The
  `publish` job has `contents: write` and `actions: read` and contains no
  `uses:` at all: it downloads the artifact with `gh run download`, checks its
  SHA-256 against the build job's output, runs the verify script again with
  the script and fingerprint file of the built commit, and creates tag and
  release with `gh release create`. The workflow-level permissions are `{}`.
- **Actions pinned to commit SHAs** in `release.yml`, `ci.yml` and
  `accuracy.yml`, with the version in a comment (replaces D-042's major-tag
  pinning). The SHAs were resolved from the action repositories' tags on
  2026-10-23 and equal what the major tags pointed to, so nothing changed in
  behaviour: `actions/checkout` v7.0.1, `actions/setup-java` v6.1.0,
  `gradle/actions/setup-gradle` v6.4.0, `actions/upload-artifact` v7.0.2,
  `actions/cache` v6.1.0, `reactivecircus/android-emulator-runner` v2.38.0.
  Updates are made by hand: resolve the new tag to its commit, replace SHA
  and comment in all three files.
- **The freeze compares what Room compares.** `check-frozen-schemas.sh`
  passes a frozen schema file that is byte-identical or that keeps its
  `database.version` and `database.identityHash`; a formatting-only
  difference (a Room upgrade writing the JSON differently) passes with a
  warning. A byte comparison would make every such Room upgrade impossible,
  because the freshness check requires the regenerated file to be committed.
  A changed version or identity hash, a deleted file or an unreadable one
  fails (replaces D-054's "byte-identical").
- **Every committed schema version opens with the production builder.**
  `core:database` keeps its migrations in one list, `UMAMI_MIGRATIONS`, which
  `umamiDatabaseBuilder` registers. `SchemaVersionsTest` (Robolectric)
  creates a database of every committed `N.json` with `MigrationTestHelper`,
  opens it with `umamiDatabaseBuilder` and expects `UmamiDatabase.VERSION`,
  runs `runMigrationsAndValidate` with the same list, and checks that the
  committed files are exactly versions 1 to `VERSION`. At version 1 it only
  reopens v1; a bump to 2 without a `Migration(1, 2)` in the list fails it
  (tried locally).
- **Dispatch-only workflows on `main`.** D-039's exception covers both
  `release.yml` and `accuracy.yml`. The file only has to exist on `main`;
  the version at the dispatch's workflow ref runs, so both are dispatched
  with the PR branch as the ref, and the M1 branch's versions are copied to
  `main` unchanged by a housekeeping PR.
- **M1 checklist (09 §4) gains the missing rule-8 cases:** a long step in
  Doze (30-min quick timer, not charging), the quick timer (1 min and a
  12-min long step), *Beenden & neu starten* during a running step, and a
  best-effort refused background start whose result is read in *Diagnose*
  (the emulator cannot refuse a start, 04 §12; otherwise the Robolectric
  tests cover it). The reboot item now names the real notice text.

<a id="d-069"></a>
### D-069 — M1 review fixes for the app and the UI (#54) · *Accepted* (Claude, 2026-10-23)

The M1 milestone review (#54) found these in the app; decided while fixing:

- **A bar that arrives alerting snaps in (rule 8, 03 §3.8).** A new activity
  (rotation, a light/dark switch, reopening from the launcher) built the active
  brew bar's source with `null`, so the matcha bar grew and faded in over about
  0.4 s while the step already alerted. `TimerActiveBrewSource` now starts
  with a synchronous value from the controller's current state, and
  `ActiveBrewBar` enters with `EnterTransition.None` when the brew it arrives
  with is alerting. Both are tested against the old behaviour.
- **System bars with one owner.** `enableEdgeToEdge` picks the bar icons from
  the phone's theme, which made the clock, the battery and the gesture handle
  invisible on the always-dark timer in the light theme. The root
  (`UmamitRoot`) is the only place that sets the appearance, derived from the
  destination on top of the back stack (as the lock-screen flags): the timer
  gets light icons on the Steeping surface and dark ones on the washi/matcha
  alert, the quick timer (SCR-07) light ones, everything else the app theme's
  own. A first version let each screen set and restore it; SCR-07 and the
  timer overlap during the crossfade and SCR-07 restored last, leaving the
  running quick timer and then the shell with the wrong icons (re-review of
  #54). Renders have no system bars, so this is a Robolectric test of
  quick timer → timer → alert → back, plus a 09 §4 checklist item.
- **Honest placeholders (GEN-09).** *Meine Tees* no longer says the shelf is
  empty (the seed teas exist) and *Statistik* no longer waits for a first
  brew: both say the area follows with a later update and where to go now
  (Start, the journal). The segments of *Sammlung* stay at the top. Renders in
  light, dark and font 2.0 are in `docs/design/review/M1/`.
- **The tea photo reaches the timer (amends D-058).** `TimerViewModel` reads
  the session's tea and its cover photo file. A brew always reserves the
  photo's space and draws the landscape placeholder until the file is read,
  when the tea has none, or when it cannot be loaded; so nothing moves when the
  photo arrives, and the renders show what the phone shows. The quick timer has
  no photo.
- **No stale question after a double tap on *Nochmal brühen* (TMR-21).**
  `HomeViewModel` ignores taps while a start is in flight (as D-065 does for
  the quick timer) and treats a refusal within 1 s by the session it has just
  started as the same gesture: no „Erst die …-Sitzung beenden?" is left on
  Start. A deliberate tap later still asks.

<a id="d-068"></a>
### D-068 — M1 review fixes in the timer area: deadline guard, accuracy up to the sound, recovery-aware timer route (#54) · *Accepted* (Claude, 2026-10-23)

The M1 milestone review (#54) found these in the timer area; all are fixed
on the M1 branch. Amends D-059, D-063, D-064 and D-067.

- **Deadline guard (amends D-063).** A long step has its deadline alarm as
  the only wake-up. Android skips every broadcast to a process while Auto
  Backup runs the app's full backup (`BroadcastQueueModernImpl`,
  "isInFullBackup"), counts it as delivered, and then kills the process; the
  backup runs when the phone is idle and charging, the conditions of an
  overnight cold brew. Recovery the next morning would acknowledge the step
  silently and record it as poured on time. So the alert watchdog alarm
  (request code 107, exact, `ELAPSED_REALTIME_WAKEUP`) is also set **while a
  step runs, 90 s after its deadline**. In a live process the deadline turn
  replaces it by the alert's 30 s cadence (the first one 30 s after the alert
  started), and `StartAlert` never restarts the playing alert, so an alert
  never plays twice. A process the guard starts recovers the step with the
  deadline passed less than 10 min ago and alerts, at most 90 s late (the
  exact-alarm delivery allows the FGS start). Pausing or ending the step
  cancels it. 90 s lies in the review's 60–120 s window: late enough that a
  backup that swallowed the deadline has ended and killed the process,
  early enough for the alert. Declaring `PROPERTY_USE_RESTRICTED_BACKUP_MODE`
  instead was rejected: backing up `umamit.db` and its WAL while the app
  writes risks an inconsistent copy. Cost: one alarm set and cancelled per
  step. Tests: the guard is set while RUNNING and goes with the step; a long
  step whose deadline alarm never came alerts when only the guard starts a
  new process; the deadline and the guard in a live process give one alert,
  in either order.
- **Accuracy up to the sound (corrects D-067).** The accuracy gate and
  *Diagnose* measured up to the controller's handling of the event, before
  the persist, the alarms, the service command and the player's main-thread
  queue, so a late sound could pass. The alert port now hands the player the
  scheduled time of the tick (the event's due time) and of the alert (the
  step's deadline); the player's „alert" log entry, written after the sound
  started, carries scheduled vs actual time and the delta like an event
  entry. `AccuracyReport` pairs each step's accepted event with that entry
  (`CueTimes`, same kind and scheduled time) and **gates on the sound
  start**; the handled delay stays as a second column (CSV, summary), so a
  slow persist or queue shows where the time went. *Diagnose* shows both
  lines; the „alert" line is the one the PO checks (09 §4).
- **Accuracy run reset.** `AccuracyRunTest` resets like `ProcessDeathTest`:
  alarms and the alert watchdog cancelled, process killed, database deleted,
  killed again, so a run that fails mid-alert leaves nothing to replay.
- **Alert settings after a failed read (amends D-059).** One failed read of
  the settings file ended the player's settings cache, which then kept the
  defaults until the process restarted (SET-09, SET-11, SET-14 ignored).
  The cache now keeps the last settings read (or the defaults before the
  first), logs the failure and reads the file again after 1 s, doubling up to
  a minute, so a later alert follows the stored tone, length and vibration.
  The alert still never waits for the file longer than 300 ms (rule 8).
- **An unreadable settings file never crashes a screen.**
  `DataStoreSettingsRepository.settings` turns an `IOException` of the file
  (DataStore corruption, a disk error) into the defaults before the first
  value, logs it and reads again with the same backoff; any other error
  still fails. So the timer, setup and settings screens read the defaults
  instead of crashing; `MainActivity`'s own read for the onboarding decision
  stays. *Amended by the re-review (R2-1):* the fallback is the defaults
  with the first-start setup counted as done (`UNREADABLE_SETTINGS`), since
  the total flow no longer reaches `MainActivity`'s catch: otherwise a
  corrupt file showed the setup on every launch and kept Start and the timer
  out of reach (D-066: an unreadable file opens on Start). A fresh install
  has no file and still gets the setup. The full-screen op read also falls
  back to the permission check on any `RuntimeException`, since the timer
  screen's view model reads it unguarded.
- **The timer route waits for recovery (GEN-07, 03 §2.4).** The controller
  publishes `recovered` (`BrewTimer.recovered`, true by default for fakes)
  after the first recovery of the process has published its state, or when
  the stored timer could not be read (no screen waits for a broken
  database). The timer route closes on `Idle` only once recovered, checking
  the current state, so the route restored from recents or opened from the
  quiet READY notification after process death lands on the recovered timer
  instead of closing itself.
- **Cold and ice brew in the notification (BRW-10).** In READY the Live
  Update's text and chip name the water at most 10 °C as SCR-05 does:
  „Bereit · 4:00:00 · Kaltes Wasser" and the chip „Kaltes Wasser", or
  „Eiswürfel" for an ice brew (#49's `iceBrew`). The chip may be shortened
  by the system; it stays the same word as the screen. `KettleWater` (the
  ≤ 10 °C rule) moved from `core:designsystem` to `core:model`, so the setup,
  the timer screen and the notifications share it.
- **The full-screen check reads the app op.** On the CI emulator (API 35
  and 36), `canUseFullScreenIntent()` still answered true after
  `appops set … USE_FULL_SCREEN_INTENT deny`, so a revoked full-screen alert
  would never reach the reliability card or *Beheben* (TMR-31). The reader
  now also reads the op's mode (`unsafeCheckOpNoThrow("android:use_full_screen_intent")`):
  denied or ignored counts as missing, „default" leaves the answer to the
  permission check. `PermissionStatesDeviceTest` proves it on both levels.
- **Wording (rule 2).** 04 §4 and `TimerServicePort.stopForeground` now say
  that `StopForeground` removes notifications 1, 2 and 4; D-064's notes on
  two-turn *Beenden & neu starten* and on resending a stale refused start are
  marked as amended by D-063.

<a id="d-067"></a>
### D-067 — Accuracy workflow before each release candidate (TMR-30, #52) · *Accepted* (Claude, 2026-10-23)

#52 built the accuracy run of [04 §12](04-brew-timer.md#12-testing)
as the manually dispatched workflow the PO chose in spec #40 (seam 6). How to
run and read it: [09 §4a](09-release-plan.md#4a-accuracy-run-before-each-release-candidate).
Decided while building it:

- **Own workflow, own test.** `.github/workflows/accuracy.yml`
  (`workflow_dispatch`: `ref`, `api-level` 36 / 35 / both, `steps`,
  `inject-delay-ms`) runs `AccuracyRunTest` in `core:timer`'s instrumented
  tests. The regular emulator job skips that test (it runs only with the
  instrumentation argument `umamitAccuracy=true`), so CI stays fast. The
  emulator setup is copied from `ci.yml` with a keep-in-sync comment rather
  than moved into a composite action, so the green CI job stays untouched; both
  use the same cache keys. Like `release.yml` it must be on `main` before
  the first dispatch (D-039).
- **Run with `am instrument`, not Gradle** (`scripts/accuracy-run.sh`): a
  Gradle connected run uninstalls the test APK at the end, and the report is
  in the test app's private files, which the script copies out with `run-as`.
- **The run:** 50 steps of 60 s with a 10 s pre-alert in five sessions of
  ten, through the test APK's real controller, alarms, service and
  notifications (#42, #47). Between steps the test taps *Abgegossen* in the
  alert and *Start* in the Live Update, and ends each session with
  *Sitzung beenden*, as the PO does on the lock screen. The screen is
  switched off (`KEYCODE_SLEEP`, checked) from each *Start* until the alert
  turns it on.
- **What is measured** (*corrected by D-068*): the time from the scheduled
  time to the moment the alert player **started the sound** of the tick or the
  alert, on the monotonic clock: the player's „alert" entry of the diagnostics
  log (D-060), paired with the accepted `PreAlertReached` or `DeadlineReached`
  entry, which says whether the exact alarm or the in-process wake-up
  delivered it (the CSV says which) and whose own delay, the moment the
  controller handled the event, is shown next to it. ~~The moment the
  controller handled the event and started the tick or the alert~~ came
  before the persist and the player's queue, so it could not show a late
  sound. It does not include the audio output itself (the CI emulator has no
  audio).
  `FireTime.parse` is the inverse of `TimerDiagnosticFormat`, and a round-trip
  unit test keeps the two together. Parser and report (`AccuracyReport`) live
  in `core:timer` next to the format; nothing in the app calls them, so R8
  removes them from the release.
- **Verdict:** the absolute delays, nearest-rank p50 / p95 and the max per
  event. The run passes only when every planned step completed and both
  events have p95 ≤ 250 ms and max ≤ 1 s. A step that times out still writes
  the report of the steps before it.
- **Gate check:** `inject-delay-ms` makes the test app set the pre-alert and
  deadline alarms, and arm the wake-up, that many ms late (`InjectedDelay`,
  test APK only). A run with, say, 1500 ms must fail, which proves the gate
  bites without changing and reverting app code.
- **Result:** `accuracy.md` goes into the job summary together with the ref
  and the commit. The artifact `accuracy-api-<level>` (30 days) holds
  `accuracy.md`, `accuracy.csv` (one row per fire time: step, event, source,
  scheduled and actual wall and monotonic time, delta), the diagnostics log
  lines, the instrumentation output and logcat.
- **Scope:** the emulator proves the timer's own path (controller, alarms,
  wake-up, service) with the screen off. It does not doze within a 60 s step,
  and it is not a Samsung phone. The device numbers still come from
  *Diagnose* in the 09 §4 checklist.

<a id="d-066"></a>
### D-066 — First-start setup, timer settings, the reliability card and the banner (M1-15, #50) · *Accepted* (Claude, 2026-10-23)

#50 built the first-start setup (SCR-01), the timer settings (SCR-25), *Über*
(SCR-27) and the reliability banner (TMR-31)
([03 §4](03-ux-ui.md#scr-01-einrichtung-first-start)). Decided while building
them:

- **One reliability seam in `core:timer`.** `TimerReliability` (`status()`,
  `settingsIntents(check)`) replaces the timer screen's own interface and the probe-backed
  `PermissionTimerReliability`. It maps the permission states #43 already reads
  for *Diagnose* (`PermissionStatesReader`, the probe's proven checks of #14) to
  five `ReliabilityCheck`s: notifications, Live Updates, the full-screen alert,
  the battery and exact alarms, each ✓, ! or not on this platform (Live Updates
  before API 36, never a problem). *Beheben* opens the check's own page, the
  probe's proven intents, with the app's details page last; the banner opens
  the first missing check in the card's order. The screen starts the pages
  from its Activity, as the probe did, so they open in the app's task and
  back returns to the app. The probe keeps its own copy
  until #54 removes it. The setup, the settings card, Start and the timer read
  the checks again on every resume.
- **The notifications row** asks for `POST_NOTIFICATIONS` while it is not
  granted, and opens the app's notification settings once Android shows no
  dialog any more or the permission is granted but notifications are off
  (`rememberNotificationPermissionAction` in `core:ui`, the probe's flow).
- **The setup is a root route.** While `onboardingCompleted` is false it is
  the start destination, so back on the welcome leaves the app and it shows
  again on the next start (ONB-04); *Weiter* writes the marker, then Start
  replaces the setup. Nothing is drawn until the marker is read, and no
  navigation (alert, notification request) runs before the graph exists. *Über
  › Einrichtung erneut zeigen* opens it above the shell like the other routes,
  so it gives way to a new alert (D-062, D-064); *Weiter* there returns to
  *Über*. An unreadable settings file opens on Start. Instrumented tests mark
  the setup done first through the app's settings store (`SettingsEntryPoint`),
  so the emulator never runs the first start or sends the seed photo requests.
- **First start in the background.** `FirstStart` (app scope, IO) runs
  `SeedDataSeeder.seedIfNeeded`, then `SeedPhotoDownloader.downloadIfNeeded`,
  once per process, as soon as the setup opens; it goes on after *Weiter*.
  A failed seed is logged and leaves the download due (D-054). The page shows
  „Teefotos werden geladen …" with *Überspringen* only while photos are
  fetched, **below** *Weiter*, so *Weiter* never moves under a finger when the
  line goes. No error is shown (ONB-06).
- **Setup pages.** Welcome: placeholder ink wash (A1 later), leaf, „UmamiT",
  „Aus Liebe zum Tee", お茶で、いい時間を。, *Los geht's*. Reliability: the four
  rows of ONB-02 with their action while missing (*Erlauben*, *Freigeben*,
  *Einschalten*), the Samsung hint with *Gerätewartung öffnen* (Device care,
  else the settings; checked on the Galaxy, 09 §4), *Weiter* always enabled.
  The pages change with the tab fade-through (`tabSwitch`); back on the second
  page returns to the welcome.
- **Settings (SCR-25), M1 rows only.** The *Timer-Zuverlässigkeit* card first
  (all five checks, *Beheben* on a missing one, a summary line), then *Timer &
  Alarm*: Alarmton (Klangschale, Glocke, Holzblock, Glockenspiel, Sanft),
  Vorwarnung (Aus, 5 … 30 s), Vibration, Alarmdauer (Kurz einmal, Normal bis
  15 s, Lang bis zur Bestätigung, höchstens 2 Min), Bildschirm beim Brühen
  anlassen, Beim Ziehen abdunkeln, *Alarm testen*; then *Über*. Every row
  writes the settings at once. A failed write (disk full) never throws into
  the app, whose process may carry a running timer: it is logged and the
  screen says „Konnte nicht gespeichert werden"; on the setup *Weiter* then
  tries again. The tone picker stores a pick at once; *Anhören* plays that
  tone once (`TonePreview`: the alert's sound output and ringer rule, no tick,
  no vibration, no alert length) and stops when the picker closes, when
  another tone is picked, or when an alert removes the picker. *Alarm testen*
  (#44's `AlertTester`) plays tick and alert with the settings; *Stopp* next to
  it ends it, and so does leaving the screen. The other sections of SET-30
  come with their milestones.
- **Alerts first (rule 8).** No settings dialog is composed while an alert
  plays, and an alert removes an open one in its first frame without
  animation. Leaving the settings stops a running test (`stopTestAlert`); a
  real alert plays on (D-059). The banner on the timer stays hidden while it
  alerts.
- **Über (SCR-27).** Leaf, „UmamiT", the version, the Tee-Kontor source note,
  *Einrichtung erneut zeigen*, *Diagnose* (moved here from the settings
  placeholder of #43), and a short licence text (Cormorant Garamond OFL 1.1,
  Material Symbols Apache 2.0, the synthesised tones, the libraries).
  AboutLibraries (06 §4) comes with 1.0 (M5).
- **Accessibility.** The switch rows are `toggleable` (TalkBack says „an" or
  „aus"); a status row reads its label, explanation and state as one node.
  From font scale 1.5 a row's action moves below its text, so no word breaks.
- **Motion.** The only new motion is the page fade-through. Status rows, the
  banner and the photo line change without animation: they change on resume,
  not while the user looks. Dialogs are Material's.

<a id="d-065"></a>
### D-065 — Quick timer SCR-07 (M1-14, #49) · *Accepted* (Claude, 2026-10-23)

#49 built the quick timer (SCR-07, TMR-22, 04 §10) and its two entries, Start's
*Schnelltimer* and the tea picker's *Ohne Tee (Schnelltimer)*. Decided while
building it:

- **Two surfaces, one timer.** SCR-07 is its own route only before *Start*:
  the ring with the chosen duration, the chips and *Start*. Once the quick
  timer runs, the timer (SCR-05) replaces the route and shows it, because
  the notification, the active brew bar and the full-screen intent open the
  timer (D-058). Back from the running quick timer returns to Start. The
  ring sits at the same place on both, so the route's `fade` only crossfades
  the chips into the pill.
- ***Start* runs at once.** The start seam gains `startQuickTimer` and
  `finishActiveAndStartQuickTimer` (`BrewStarter`, D-062): the controller's
  `startQuickTimer` reduces `startQuickTimer` and `start` in one lock turn,
  so no other input lands between them and a cancelled caller never leaves
  a waiting quick timer behind. A second tap on *Start* while one is in
  flight is ignored. *Beenden & neu starten* into the quick timer is one
  turn too (`finishActiveAndStartQuickTimer`, through the controller's
  `startQuickTimer(finishActive = true)`, the quick-timer variant of D-063's
  operation): the active timer ends, the finish's sheet becomes
  „Sitzung gespeichert" (D-054), and the quick timer starts and runs. Opening SCR-07
  starts nothing, so it never blocks another session (TMR-21).
- **On the timer.** The quick timer has its title and no step label, dots or
  kettle hint; *Fertig* (`poured`) on the alert; the overflow holds
  *Abbrechen* (`cancel`, READY/RUNNING/PAUSED only, as 04 §10 allows) and
  *Timer-Einstellungen* (for the global pre-alert it uses). TalkBack says
  „Schnelltimer läuft".
- **Chips and picker.** SCR-07 opens with 1:00; the chosen duration survives
  process death (GEN-07), not app restarts. The selected chip has no check
  mark, so no chip widens under the finger; unselected chips have a border
  of ≥ 3:1 (WCAG 1.4.11). A tap on the ring opens „Dauer
  wählen": hours, minutes and seconds with − and +, each move kept within
  5 s – 24 h and rounded to PRF-04's increments (5 s below 10 min, whole
  minutes from 10 min on; the seconds do not move there). A move that would
  leave the bounds is disabled. *Übernehmen* applies, *Abbrechen* keeps the
  duration.
- **Second timer (TMR-21).** *Start* while a session is active asks
  „Erst die … -Sitzung beenden?" like the setup; while it alerts, the
  alerting timer opens instead.
- **Alerts first (rule 8).** SCR-07 is a route above the shell, so a new
  alert replaces it with the timer at once (D-062). During an alert,
  *Schnelltimer* and *Ohne Tee (Schnelltimer)* open the alerting timer
  instead of SCR-07, like a tea tap (D-062). The picker and the
  question remember the alert they were opened in and are removed when
  another one starts, without animation. The only motion of its own is
  Material's chip colour and the pill's press scale.
- **Kettle hint of the timer (BRW-10).** `TimerUiState.Active.iceBrew`
  carries an ice brew from the start, and from the session row after
  recovery, so a cold step's READY reads „Kaltes Wasser" or „Eiswürfel"
  like the setup, not „Wasserkocher auf 5 °C" (the follow-up of D-062).
  It goes through every recovery of D-063 (app start, reboot, update): the
  restored state takes it from the session row like the tea's name.

<a id="d-064"></a>
### D-064 — Notifications, the promoted Live Update and the full-screen alert on the real timer (M1-11, #47) · *Accepted* (Claude, 2026-10-22)

#47 replaced the interim notification content and `TimerAlertActivity` of #42
with the real ones ([04 §8](04-brew-timer.md#8-notifications); TMR-06,
TMR-14, TMR-18, TMR-41) and bound the notifier, the controller's last port.
Decided while building them:

- **One place shows notifications 1, 2 and 4.** `AndroidTimerNotifier` (the
  notifier port) hands `UpdateNotification` to `BrewTimerServicePort`, which
  also handles `StartForeground` and `StopForeground`. One lock orders the
  controller's commands and the 15 s refresh, and a command is sent while it is
  held, so the service receives them in that order.
  - While the service runs or starts, every change goes to the service, which
    shows it as its foreground notification. The ID changes only there, and the
    notifier never posts notification 2 next to the service's notification 1
    (D-044, D-057). The same state is not sent twice in one transition
    (`StartForeground` and `UpdateNotification` of the deadline), so the alert
    takes over the screen once.
  - Without the service the notification is posted directly, after the other
    two timer notifications are cancelled.
- **The quiet notification has its own ID 4.** A long running step („Fertig um
  14:30", TMR-18, a Live Update) and a demoted session (TMR-14, a regular
  ongoing notification without the promotion request, with *Start* or *Weiter*
  and *Sitzung beenden*) run without the service. When the service stops, the
  system removes its foreground notification asynchronously, which could remove
  a notification 1 posted right after; ID 4 avoids that race. ID 4 is never
  posted while alerting and is cancelled before every start of the service and
  every alert, so the alert stays the app's only notification of importance
  DEFAULT or higher (D-044). `StopForeground` removes 1, 2 and 4.
- **Content per phase** (04 §8.2): the title is „Gyokuro · Aufguss 2 von 3".
  The step word follows the step kind, as on SCR-05 (D-058). The quick timer is
  „Schnelltimer"; the alert text is „Gyokuro · Aufguss 2". The status-bar chip
  is the chronometer while running, otherwise the short critical text
  („60 °C", „Pause", „Fertig"). A long step keeps its step title (the state does
  not know the brew method, so not „Kaltaufguss").
- **Actions:**
  - *Start*, *Pause* or *Weiter* by phase;
  - *Abgegossen* (*Weggegossen* for a rinse, *Fertig* for the quick timer);
  - *Sitzung beenden* in FINISHED and on a demoted session;
  - *Abbrechen* for a waiting or running quick timer.

  *Sitzung beenden* is left out where it would discard the session: only the
  app may ask first (TMR-12 AC1). `TimerActionReceiver` (non-exported) sends
  each action to the controller with `EventSource.NOTIFICATION` through
  `TimerHost`. It then re-sends a refused service start within the tap's
  exemption, as the alarm receiver does.
- **ProgressStyle** (04 §8.3): one segment per step (at most 30) in tenths of a
  second. Each segment is at least a twentieth of the plan, and a rinse is
  drawn in a muted colour. The point marks the current step's pre-alert, and
  the progress is the session's elapsed share on that scale. Refreshed every
  15 s while running. The refresh runs only while the process lives and never
  after a newer command.
- **Dismissal** (04 §8.3): the delete intent records the dismissed state (session,
  step, phase, pre-alert, demoted). That state is not posted again by an update
  or the refresh; the next state change posts it. The alert ignores it. The
  record dies with the process.
- **Full-screen target: the app's timer route; `TimerAlertActivity` is removed.**
  - The content intent of every timer notification and the alert's full-screen
    intent open `MainActivity` with `com.umamit.action.OPEN_TIMER`, naming the
    app's launcher activity explicitly, so no intent filter is needed. A thin
    launcher activity was rejected: it would add a hop and a second activity
    over the lock screen.
  - `MainActivity` sets `showWhenLocked` (and `turnScreenOn` while alerting)
    at once, in `onCreate` or `onNewIntent`, while a session is active. Behind
    the keyguard with the screen off no frame is drawn, so the flags cannot
    wait for the navigation.
  - `UmamitRoot` pops whatever lies above the timer, or opens it. Until the
    timer is on top it draws the timer's dark background over the shell and
    reports the timer as visible, so the shell never shows over the lock screen
    (D-058).
  - A request the navigation has not handled yet is saved with the activity's
    state. A full-screen intent reaches a sleeping activity at once, and a
    configuration change when it becomes visible (dark mode, rotation, a fold)
    recreates it without delivering the intent again; the new activity
    restores the request, sets the flags and opens the timer.
- **„Sitzung gespeichert"** (ID 3, channel `sessions`, LOW, auto-cancel) names
  the session's tea as the notifier last showed it („Gyokuro · im Journal").
  It opens the app; the journal detail SCR-16 comes with M4.
- **The app runs the real timer.** With every port bound, `TimerAppModule`
  binds `ControllerBrewTimer` instead of `NoBrewTimer` (removed), and
  `UmamitApplication` is the `TimerHost` with the injected controller (`Lazy`,
  built on first use; D-056, D-057). `ControllerBrewTimer` is also the
  `BrewStarter` (D-062), so Start, *Nochmal brühen* and the setup start real
  brews. *Beenden & neu starten* still runs as two turns (`finishSession`,
  then `startSession`); the one-turn form of 04 §7.2 (D-054) is #46's.
  *Amended by D-063:* it is one turn since #46.
- **One place puts the timer on top** (`UmamitRoot.rememberTimerFirst`, with
  D-062): a new alert makes a route above the shell or the timer give way, and
  a notification's request brings the timer up. Both pop back to the timer when
  it is on the stack, else replace what lies above the shell. A tea tap on
  Start during an alert opens the timer instead of a setup, like a refused
  start (D-062 S3).
- **Tests.**
  - Robolectric:
    - content, actions, promotion, segments, intents and the delete intent
      per phase;
    - the notifier and the service port: through the service while it runs,
      directly without it, ID 4, the refresh and the dismissal;
    - D-044 through the real controller, service port, notifier and service:
      after `deadlineReached`, `addStep` while alerting, `recover` of an
      alerting state and a long step's deadline;
    - action round trips into the real controller.
  - Emulator (API 35/36):
    - the promoted Live Update (`FLAG_PROMOTED_ONGOING` on API 36, a normal
      ongoing notification on API 35);
    - *Pause* and *Weiter* from the notification;
    - the alert alone, not `FLAG_SILENT`, with the timer screen over the lock
      screen and *Abgegossen* from the notification (`core:timer`);
    - a cold brew's quiet notification (ID 4) giving way to the alert alone,
      without an autogroup summary (`core:timer`);
    - the app's own timer route over the lock screen through the app's
      controller, found by the ring's description and *Abgegossen* (`app`).
  - Not done (review of #47, open): a notification action can resend a stale
    refused start (needs refused exact alarms and no battery exemption;
    *amended by D-063:* closed, a refused start is resent from the current
    state); *Sitzung
    beenden* from the notification shows the „Gespeichert" sheet when the app
    is next opened (03 SCR-06 to decide); „Sitzung gespeichert" after recovery
    straight into the midnight timeout names no tea (the port would need the
    tea name).

<a id="d-063"></a>
### D-063 — Recovery after process death, reboot, app update and device restore (M1-09 part 3, #46) · *Accepted* (Claude, 2026-10-22)

#46 completed recovery ([04 §7.2–§7.5](04-brew-timer.md#72-recovery-on-process-start);
E2, E11; TMR-14, TMR-16, TMR-21) with the scope the #42 review handed
over (R2-1, R2-2, R2-3). Decided while building it:

- **Triggers.** `TimerInputs.recover(trigger)` is one controller turn. The
  first turn of a process recovers the persisted timer (D-057); in a process
  that already did, nothing changes. The triggers:
  - `APP_START`: the app's process start. The app calls it from
    `Application.onCreate`, off the main thread, once it hosts the controller
    (hand-off below).
  - `BOOT_COMPLETED` and `PACKAGE_REPLACED` come from `TimerRecoveryReceiver`.
    One receiver takes both broadcasts. It is not exported: the system sends
    both, and system broadcasts reach a receiver that is not exported (04 §13
    had an exported `BootReceiver`). `RECEIVE_BOOT_COMPLETED` is already in
    06 §5.7.
  - An app without a `TimerHost` runs no timer, so the receiver only logs to
    logcat. Unlike the alarm receiver, it never fails a debuggable build,
    because every boot reaches it.
  - A process that an alarm started may recover in the app start's turn. The
    alarm's log entry then takes its scheduled time from the restored state
    (06 §5.6).
- **Reboot notice (04 §7.3).** A session that survives a reboot (the boot
  count changed) gets „Dein Timer läuft weiter", with the tea below it,
  through the notifier port
  (`notifyTimerResumed`; its content comes with #47). While alerting there is
  no notice: the alert is the notice (D-044).
- **Device restore (E11).** Restore detection clears `active_brew` (D-052).
  Recovery then ends every `IN_PROGRESS` session other than the active one:
  - it is saved (`completeSession`, `auto_finished` = 1, as the app's own
    finish, 05 §3.6) when it has a completed infusion, i.e. a non-rinse step
    with recorded values that was not skipped;
  - otherwise it is discarded;
  - the same covers the session of a stored timer cleared as unreadable;
  - each one is logged (`OrphanSessionEnded`). A failure is logged and tried
    again at the next process start. `SessionRepository.inProgressSessionIds()`
    is new.
- ***Beenden & neu starten* (TMR-21, D-054).**
  `BrewSessionController.finishActiveAndStart(request)` is one turn:
  - it ends the active timer: *Sitzung beenden* for a brew session; for the
    quick timer *Abgegossen* while alerting, else *Abbrechen*;
  - then it starts the request;
  - the finish's `ShowSavedSheet` runs as `NotifySessionSaved`;
  - a start from elsewhere waits for the whole turn.

  `ControllerBrewStarter.finishActiveAndStart` (#48) calls it, in place of
  #48's two turns (D-062).
- **The recovery retries back off and stop (R2-1).** Each turn still tries
  the read three times (D-057). A turn whose reads all fail comes again after
  5 s during the first 30 s of failures, every 30 s up to 5 min, then every
  2 min; the alarm retry and the in-process wake-up use the same delay.
  - After 11 min of failed reads the process stops retrying, once
    (`RecoveryRetriesStopped`), so a database that stays broken does not
    wake the phone forever.
  - Only the retries stop (#46 review S1). The stored timer may well be
    valid: a cold brew can be hours from its deadline when the reads begin to
    fail. So its row, its alarms and its session stay. Its own alarms, a tap
    or the next process start read again, each with its three tries.
  - A snapshot that `restore` refuses is truly unreadable and is still
    cleared at once (D-057). Its alarms are cancelled, and any notification
    the old process posted without the service (a long step, a demoted
    session, a refused start) goes too, because it outlives that process.
  - Not done (#46 review O2): the 11 min count per process. A process that
    keeps dying between its retries starts again at 5 s; only the retry
    wake-ups continue, nothing is lost.
  - Every turn now re-arms the wake-up at its end, so a turn that only
    recovers retries too.
- **The refused start goes again from the current state (R2-2, amends
  D-057).** The controller sends it inside its turn:
  - at the end of a turn that may start the service from the background (an
    exact alarm, a notification action, a tap) and that sent the service no
    command itself; never after the in-process wake-up or the alert player's
    report;
  - with the notification of the state of that moment, which does not alert
    again (`FLAG_ONLY_ALERT_ONCE`). The alert keeps its full-screen intent
    while it plays;
  - a state that needs no service drops the refused start
    (`BrewTimerEngine.needsService`, new);
  - a start that is refused again is logged (`ServiceStartRefused`, *again*);
  - so the next interaction also restarts a service that recovery could not
    start (04 §7.2).

  The alarm receiver no longer resends on its own, and `ForegroundServiceState`
  only keeps whether a start was refused.
- **The alert watchdog for a kill during ALERTING (R2-3).**
  - *The gap:* any kill during ALERTING leaves only the overtime alarm
    (deadline + 10 min), however the deadline was handled. Its recovery then
    acknowledges the step silently.
  - *Decided:* while the alert plays, an exact `ELAPSED_REALTIME_WAKEUP` alarm
    (`TimerAlarmKind.ALERT_WATCHDOG`, request code 107) comes every 30 s. It
    ends with the alert: its cycle, *Abgegossen* or any other end.
  - In a new process, its turn recovers the state and replays the alert that
    had not run its cycle (at least once, D-047). In a live process it finds
    nothing due and is set again.
  - It is not an engine due time. That would need the alert's start in
    `active_brew`, a schema change right before the v1 freeze (D-054), and a
    due time must be consumed by an event that changes the state. The
    watchdog's only job is to start a process; recovery decides the rest.
  - `ScheduleAlarms` reconciles only the six due kinds
    (`TimerAlarmKind.DUE`); the watchdog has its own port call (`watchAlert`).
    A new process cancels a watchdog the old one left set when nothing plays.
  - Cost: at most four extra wake-ups per alert (Lang, 2 min), while the
    screen is on for the alert anyway.
- **Diagnostics (D-060).** New entries:
  - `RecoveryRequested`: the trigger, and whether the process had recovered
    already;
  - `Recovered`: the stored and the resulting phase, a reboot, the row of the
    table that applied (restored, alert replayed, overtime acknowledged,
    demoted, session ended), and whether the state runs with the service;
  - `OrphanSessionEnded`, `OrphanSessionsFailed` and `RecoveryRetriesStopped`;
  - *again* on `ServiceStartRefused`;
  - the watchdog alarm logs its scheduled time.
- **Tests.**
  - At the controller seam: the 04 §7.2 table (`RecoveryTableTest`), the
    retries and the backoff (`ProcessRestartTest`), *Beenden & neu starten*
    (`FinishAndRestartTest`), the refused start (`RefusedServiceStartTest`) and
    the watchdog (`AlertWatchdogTest`).
  - On Robolectric: the receiver, the alarm adapter and the service port.
  - In the CI emulator job, `ProcessDeathTest` also kills a step, reopens the
    app, checks the restored state and the alert within 2 s of the deadline.
    The runner's instrumentation lets Android allow every foreground start of
    the app's uid, so the emulator cannot show a refused start; those paths
    rest on Robolectric and the PO's device (#46 review O3).
- **Hand-off, done at the merge with #47 (D-064).** #47 bound the notifier,
  the last port, and made `UmamitApplication` the `TimerHost`. The merge
  adds:
  - `UmamitApplication.onCreate` calls `recover(APP_START)` off the main
    thread; an `:app` test fails without the call (the compiler cannot see
    it);
  - `AndroidTimerNotifier.notifyTimerResumed` posts the reboot notice on the
    `sessions` channel (LOW), so it never sits next to the alert (D-044);
  - the notification action no longer resends a refused start itself: the
    controller's turn does it from the current state (closes D-064's open
    point on a stale resend);
  - the device tests' resets cancel the watchdog alarm too.

<a id="d-062"></a>
### D-062 — Start, tea picker and brew setup (M1-13, #48) · *Accepted* (Claude, 2026-10-22)

#48 built Start (SCR-02), the tea picker (SCR-03) and the setup (SCR-04)
([03 §4](03-ux-ui.md#scr-02-start-home)). Decided while building them:

- **One start seam in `core:timer`.** Features depend only on `core:*`
  (06 §3), so Start (`feature:home`) and the setup (`feature:brew`) start a
  brew through `BrewStarter` (`state`, `startSession`,
  `finishActiveAndStart`); `ControllerBrewStarter` is the controller behind
  it, and `ControllerBrewTimer` implements both seams. The app binds
  `BrewStarter` to whatever `BrewTimer` is bound to, so it follows the
  controller when #47 binds it; until then `NoBrewTimer` ignores starts and
  the screen stays. *Beenden & neu starten* runs as two controller turns
  (finish, or cancel a quick timer, then start); #46 replaces it with the
  one-turn operation of D-054 behind the same seam (*done in D-063*:
  `BrewSessionController.finishActiveAndStart`). `alertKey()` moved to
  `core:timer` so every screen can give way to the alert.
- ***Nochmal brühen* (BRW-11).** The three most recently brewed distinct
  teas of the last 50 finished sessions; archived or deleted teas are left
  out. One tap starts the tea's last session again: its snapshot (recipe,
  vessel, leaf, water, method, base temperature), its step rows as planned,
  and its effective pre-alert frozen like a recipe value. A session finished
  early keeps only its started steps (TMR-12), so those are brewed again.
  The recent sessions come from `SessionRepository.observeRecentSessions`
  on the existing `observeFinished` query (no schema change).
- ***Zuletzt gebrüht*.** The last three sessions as `TeaListItem` rows:
  „21. Oktober · 1 Min · 65 °C" (the first brewed step's planned duration:
  whole hours „4 Std", whole minutes „1 Min", else „0:45"; its temperature).
  The rating comes with M4. *Alle anzeigen* opens the Journal tab.
- **Start layout.** Title „UmamiT", the placeholder landscape as the header
  (A1 later), *Aufguss starten* (or *Aufguss fortsetzen* with the tea and a
  countdown that ticks at each whole second while Start is visible), the
  *Schnelltimer* entry, then the three lists. With an empty collection the
  empty state replaces the header and the card; *Katalog öffnen* switches to
  *Sammlung › Katalog*. *Schnelltimer* and *Ohne Tee (Schnelltimer)* are wired
  to SCR-07 by #49. Photos are app-private files loaded with Coil
  (`core:ui`); without one, the leaf placeholder.
- **Tea picker.** Material 3's modal sheet, fully expanded, like the
  Schritte-Sheet (D-051), not the `sheet` token; under reduce motion it
  appears without the slide. A pick opens the setup in the tap's frame and
  closes the sheet; the setup's fade is the only transition (no sheet exit
  first, so no 600 ms of motion in a row), and no navigation can follow an
  alert that started after the tap. The search is GEN-10's: word prefixes of name and type, case- and
  diacritics-insensitive („kor" finds „Kōridashi"), debounced 150 ms, and
  forgotten when the sheet closes. *Aus dem Katalog* is hidden until M2.
- **Setup scaling (BRW-03).** The lock keeps the recipe's ratio; every
  rescale starts from that ratio, so steps never compound rounding errors
  and undoing a change by hand gives the recipe's values back. Re-locking
  takes the current values as the ratio. The steppers move the leaf by
  0,1 g and the water to the next multiple of 5 ml (the rounding units of
  05 §7). A vessel sets the water to its usable volume, else its capacity.
  Times and temperatures never scale. A tea without a recipe starts with
  5,0 g / 250 ml, one minute at 70 °C, and has no *Im Rezept speichern*.
- ***Im Rezept speichern* (BRW-02).** Shown only while vessel, water, leaf,
  steps or pre-alert differ from the selected recipe; it writes all of them
  (a step at the base temperature keeps none of its own) and the session then
  inherits the recipe's pre-alert. After the save its slot keeps its height
  and shows „Im Rezept gespeichert" (a polite live region), so *Brühen
  starten* never moves under a second tap (D-058). The Schritte-Sheet's own button writes
  only steps and pre-alert (SCR-31). The setup's values survive process
  death in the `SavedStateHandle` (GEN-07).
- **Kettle hint (BRW-10).** At ≤ 10 °C the setup reads „Kaltes Wasser", or
  „Eiswürfel" for an ice brew (`KettleWater`); the timer's READY does the
  same since #49 (D-065).
- ***Brühen anpassen*.** The timer's menu item opens the setup of the waiting
  session in place of the timer, filled with that session's values.
  *Brühen starten* there replaces the session without asking (no step has
  started, so finishing it discards it); the timer then replaces the setup.
- **Alerts first (rule 8).**
  - **Routes give way.** Every full-screen route above the shell or the timer
    (the setup, *Timer-Einstellungen*, *Diagnose* and any later one)
    remembers the alert it was opened in. When another alert starts, it gives
    way to the **timer** in that frame, without a transition: popped back to
    the timer when the timer is under it, else replaced by it (back then
    returns to the shell). The timer, not the shell's alerting bar, because
    it is the alert's full surface with *Abgegossen* (SCR-05), as the
    full-screen intent opens it (D-058). Unsaved setup values are dropped.
  - **Overlays.** The picker and the Schritte-Sheet are removed in the frame
    another alert starts (`AlertGate`, D-051), without animation.
  - **The second-session question** is never composed while an alert plays
    (gated in the UI, and closed by the view model). A start refused while
    the active step alerts opens the timer instead of asking.
  - **Motion.** Nothing on these screens is animated into or over an alert.
    The only own motion is *Im Rezept speichern* growing from its top edge
    (`standard`/`fade`, leaving with `exit`/`exitFade`), its crossfade to the
    confirmation, and the press scale of the cards and pills.
- **TalkBack.** The ratio lock is one switch node („Verhältnis halten,
  2,0 g / 100 ml", on or off). Each stepper row's label and value are one
  node that announces its new value.

<a id="d-061"></a>
### D-061 — Minimal journal and the „Gespeichert" sheet (M1-16, #51) · *Accepted* (Claude, 2026-10-22)

#51 built the read-only journal (SCR-15 minimal, JRN-01, JRN-08) and the
saved sheet (SCR-06, TMR-12). Decided while building them:

- **Read side.** `JournalRepository.observeSessions()` (core:data) emits every
  session, in progress or finished, newest start first, with its steps and
  its tea's cover photo file. One Room query with a join on `tea`; no schema
  change. It never writes: stock deduction and the opened date stay with
  `SessionRepository` (#36). The list follows the database, so a finished
  brew appears at once.
- **Day groups (E13).** A session belongs to the local date of its start in
  its own time zone, also when it ran past midnight, and shows its time of
  day in that zone. „Heute"/„Gestern" are relative to the phone's date now
  (re-read on resume and at midnight); older days read „Montag, 12. Oktober",
  with the year only outside the current year.
- **Meta line.** „55 °C · 1:00 · 3 Aufgüsse" is the base temperature (or the
  first infusion's, without one), the *planned* time of the first infusion
  and the count of poured infusions: ended steps that are neither a rinse nor
  skipped. When fewer were poured than the plan's non-rinse steps, the count
  reads „2 von 3 Aufgüssen" (a skipped step counts as not poured). Rating and
  the no-notes dot of JRN-08 come with M4, since M1 has neither.
- **Pinned session.** Every `IN_PROGRESS` session sits under „Läuft gerade"
  above the days, with its count so far („1 von 3 Aufgüssen"); a tap opens
  the timer. Finished rows are not tappable until the session detail (M4).
- **Saved sheet port.** `SavedSheetRequests` (core:timer, singleton) is the
  controller's `SavedSheetPort`: it only stores the session id and the
  monotonic time of the request, so the call in the controller's lock never
  blocks. The shell shows the sheet over the bottom of the tab content (above
  the brew bar and the navigation), without a scrim, on the `surface` token
  of sheets (03 §3.1) with a hairline; *Sitzung beenden* closes the timer
  route, so it appears over Start or wherever the user returns. Its text:
  „Gespeichert im Journal" and „3 Aufgüsse · 2:20" (poured infusions and
  their actual steeping time).
- **Leaving.** After 10 s (TalkBack's recommended timeout if longer), on a
  swipe down past 48 dp or a fling, or with TalkBack's *Schließen* action. The
  10 s count from the request, also while no shell shows the sheet (the app in
  the background, the next brew's timer covering the shell): a sheet comes
  back only for the rest of its time and never once it has run out (review of
  #51). A finger on the sheet holds it from the press on, also when it rests
  without moving; the full 10 s start again on release. A newer session
  replaces the sheet.
- **Rule 8.** While an alert plays, the sheet is not composed at all, so no
  exit animation runs over it, and a sheet due then is dropped, never shown
  afterwards.
- **Motion.** In and out with the `sheet` spring (critically damped,
  interruptible; a short drag springs back with its velocity, a fling carries
  its velocity out, and an upward drag meets friction instead of a wall),
  fading in with `fade` and out with `exitFade`; under reduce motion the slide
  is instant and the fades are short.
- **TalkBack.** The meta line is spoken with the duration in words („55 °C, 1
  Minute, 3 Aufgüsse"), so „1:00" is never read as a time of day.
- **Plumbing.** The app injects `JournalSources` (journal, sessions, saved
  sheet requests, wall clock) and passes it to the shell, like the active
  brew source, so the shell tests run on the fakes of core:testing
  (`FakeJournalRepository`). The tab keeps its last read, so a returning tab
  keeps its list height and scroll position. `ScreenScaffold` gained a
  `verticalArrangement`; `TeaThumbnail` is public for rows that are not a
  `TeaListItem`.

<a id="d-060"></a>
### D-060 — Diagnostics log and the *Diagnose* screen (M1-20, #43) · *Accepted* (Claude, 2026-10-22)

#43 built the rolling diagnostics log of [06 §5.6](06-architecture.md#56-error-handling--logging)
and *Diagnose* (SCR-27; TMR-30). Decided while building it:

- **Storage** (`DiagnosticsLog`, `core:data`): plain text in
  `Context.noBackupFilesDir/diagnostics/`, which no backup or device transfer
  includes (05 §8; `BackupRulesTest` checks that no rule includes it). Two
  files of 100 KB each, so the log never exceeds 200 KB: when the newer file
  is full it replaces the older one, so the oldest entries go first, in
  blocks. One line per entry: the local time to the millisecond with the zone
  offset, a tab, the message; line breaks in a message become spaces.
  - `core:data`, not `core:timer`: the catalog refresh writes to the same log
    from M2, and `core:data` cannot depend on `core:timer`.
- **Never in the timer's way** (rule 8): `append` only queues the line and
  returns; one coroutine on the IO dispatcher writes the queue in order. An
  I/O or security error loses that entry only. A read is queued behind the
  writes before it. The timer's port (`LogTimerDiagnostics`) formats and
  queues; it catches anything a broken entry throws. The alert player logs
  after it has started the alert, the service through a guarded reporter.
- **Entries in technical English, not localised:** they are pasted into
  issues and read by the accuracy workflow (04 §12). Only plain values are
  written, never a model's `toString()`, so no note text can reach the log;
  tea names are not logged either (allowed, but not needed). The screen
  around the entries is German.
- **Scheduled vs actual** (TMR-30): a time-based event and its alarm log
  `PRE_ALERT scheduled 10:00:50.000 (mono 5050000) actual 10:00:50.080 (mono
  5050080) delta +80 ms`. The kind's own clock gives the scheduled time; the
  other clock's is derived from the delay. Every other timer entry ends with
  the monotonic time and the boot count.
- **What is logged now:** every event the controller handles (source,
  phases, outcome), each alarm received, persist and effect failures, stale
  alert ends, a stored timer that is unreadable or cannot be read (recovery),
  inexact alarms, the service's foreground starts, its end and refused
  starts, the ringer mode, call and Do Not Disturb state with the outputs and
  tone at each pre-alert, alert and *Alarm testen*, and the permission
  states (notifications, Live Updates, full-screen alert, exact alarms,
  battery) at every process start, read off the main thread.
  `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED` are logged by the controller when
  their receiver asks it to recover, with each recovery decision (D-063).
- ***Diagnose***: newest first, the time in monospace above the message (also
  monospace), *Kopieren* in the top bar. It copies a header line (app version
  and versionCode, Android version and API level, device maker and model)
  and the entries as shown, newest first. Android 13+ confirms the copy
  itself, so the app shows no message. An empty log shows an empty state and
  disables *Kopieren*. The settings placeholder has a *Diagnose* button until
  M1-15 moves it under *Über*.

<a id="d-059"></a>
### D-059 — Production alert player, four more tones, *Alarm testen* (M1-10, #44) · *Accepted*, its main-thread queue amended by D-092 (Claude, 2026-10-22)

#44 turned the ringer-mode player of #16 into the controller's alert port
([04 §9](04-brew-timer.md#9-alerts)). Decided while building it:

- **Two classes, one player.** `AlertPlayback` (singleton) owns the one
  `RingerModeAlertPlayer` with the device outputs. `ControllerAlertPort` is
  the controller's `TimerAlertPort` on top of it, and `AlertPlayback` itself
  is the `AlertTester` (*Alarm testen*) for the settings screen. The tester
  does not depend on the controller, so #50 can inject it before every
  controller port is bound (D-056). Hilt binds both in `TimerModule`.
- **Calls never block.** Every port call only queues a command; the
  commands run in order on the main thread, which the player and its
  Android outputs need. So a stop can never overtake its start.
  *Amended by D-092:* they run in order on the player's own thread
  (`UmamiTAlert`), not on the main thread.
- **Settings per alert.** Tone (SET-09), vibration (SET-11) and alert length
  (SET-14) are read at the start of every alert, tick and test from a cache
  of the settings file. Right after the process starts, a command waits at
  most 300 ms for the first value. An unreadable or late file plays the
  defaults (Klangschale, Normal, vibration on): a late alert is worse than a
  default tone (rule 8). The stored `alertSound` key is the file name; an
  unknown key plays the Klangschale. The timer's own `AlertLength` was
  replaced by the `core:model` enum of the setting.
- **End report.** When an alert has run its full length (Kurz one cycle,
  Normal up to 15 s, Lang up to 2 min; also when it was visual only), the
  port calls `alertCompleted` with that `StepAlert` once, from its own
  coroutine and never inside the controller's turn. The controller is
  reached through a `Provider`, which breaks the dependency cycle. An alert
  stopped (*Abgegossen*) or replaced first is not reported. If the report
  never arrives (process death), recovery replays the alert (at least once,
  D-047). A command that throws is logged, and the next alert still plays.
- **Tick at most once.** The engine plays the pre-alert once per step; the
  player also ignores a pre-alert while an alert plays, so a tick can never
  cut an alert. A controller test delivers the pre-alert alarm repeatedly
  and hears one tick.
- ***Alarm testen*** plays the tick, then the alert 1.5 s later (0.8 s tick,
  0.7 s pause), with the current settings including the alert length, through
  the same player and matrix. It is ignored while a real alert or tick plays;
  a real alert or tick replaces a running test. `stopTestAlert` stops only a
  test, e.g. when the settings screen closes.
- **Four tones** from `scripts/sounds/generate.sh`, each with its own
  character (04 §9.2): Glocke (small bell struck twice, 660 Hz bell
  partials, 3.0 s), Holzblock (two wood blocks, „tok-tok … tok-tok", noise
  burst plus short modes, band-passed, 1.2 s), Glockenspiel (rising arpeggio
  C6–E6–G6–C7, 2.2 s), Sanft (two soft tones rising a fourth, 3.0 s). Like
  the Klangschale: mono, 48 kHz, Ogg Vorbis q6, -16 LUFS, true peak ≤ -1.5
  dBTP, 9–16 KB. The wood block's noise comes from ffmpeg's seeded
  `random()`, so the file is reproducible. The generator gained an optional
  filter chain per sound, which leaves the Klangschale's and the tick's
  commands unchanged: both stay byte-identical (D-045), and `--check`
  compares all six files. A tone shorter than 3 s still repeats every 3 s
  (the vibration pattern plus 1 s), so Normal is 5 × 3 s.
- **The PO listens** to the four tones before the M1 checklist (samples on
  #44) and may pick another default or replace a tone (Q-15 procedure). The
  German tone names for the picker come with the settings screen (#50).

<a id="d-058"></a>
### D-058 — Timer screen SCR-05 (M1-12, #45) · *Accepted* (Claude, 2026-10-22)

#45 built the timer screen in `feature:brew` on the controller's state
([03 SCR-05](03-ux-ui.md#scr-05-timer)). Decided while building it:

- **One seam to the controller.** `BrewTimer` (`state`, `send`) is all the
  screen, its view model and the active brew bar use; `ControllerBrewTimer`
  is the controller behind it. Until #42 binds the remaining ports the
  controller cannot be injected (D-056), so the app binds `NoBrewTimer`
  (always idle, events ignored): nothing runs, the bar stays hidden and the
  timer route closes at once. Switching is one `@Binds` line in
  `TimerAppModule`. `TimerUiState.Active` gains `preAlert` (the session's
  own option) for the Schritte-Sheet.
- **Step label.** „Aufguss 2 von 4" numbers the position in the whole plan;
  the word follows the step kind („Spülaufguss 1 von 4", „Aufschlagen 1 von
  1"). Dots: done and current filled, pending outlined, a rinse a small square.
- **READY** keeps the digits in the ring with the caption „Bereit"; *Start* is
  the cream pill below and the ring is tappable too. The kettle hint carries
  „Aufguss 2 · 0:20" as its second line. **ALERTING** shows 0:00 in the ring
  with „Jetzt abgießen!" and the overtime below it, without the pre-alert
  marker. **PAUSED** dims only the ring, the liquor and the marker; the digits
  and the caption keep full contrast (≥ 4.5:1, tested). The dim never reaches
  the alert: PAUSED → ALERTING (the step resumed from the Live Update while the
  app was stopped) draws the ring at full strength in its first frame (tested).
- **The pill never moves under the finger.** The kettle hint and the overtime
  share one slot, sized by invisible copies of its tallest contents (a two-line
  kettle hint, the large overtime), so it holds under non-linear font scaling;
  the photo's space stays on the alert surface (the photo is not drawn there),
  and the reliability banner sits below the controls (hidden while the alert
  plays). Tested at *Start*, at the alert and at 15 s overtime, at font scale
  1.0 and 2.0.
- **First frame of a new state.** The screen reads the clock in the first
  composition of every new state, so *Start* or *Weiter* after a long wait
  never shows the stale time for a frame. The pre-alert glow is keyed by the
  step, so *Weiter* after the pre-alert does not replay it (D-046).
- **Overlays survive a configuration change** (the menu, the question and the
  sheet; GEN-07).
- **Finishing without a completed step** asks with the same „Sitzung
  verwerfen? Nichts wird gespeichert." (*Verwerfen* / *Weiter brühen*), because
  that is what it does; *Verwerfen* sends `finishSession`, which discards
  (TMR-12 AC1).
- **Alerts first for every overlay and route (motion reviews of #45).** Each
  alert has an identity (`alertKey`: session, step and deadline). A menu, a
  question or *Timer-Einstellungen* remembers the alert it was opened in, and
  only that alert may play under it: whatever was opened before an alert, or
  during an earlier one, is removed when a new alert starts. The menu is then
  not composed at all, so Material's exit animation cannot show over the alert.
  The settings route is popped back to the timer for every new alert, checked
  on each new alert and on resume, so an alert that started while the app was
  in the background is caught too. No route transition runs into, out of or
  onto an alerting timer (`EnterTransition.None`/`ExitTransition.None`);
  Navigation settles that swap within about five frames. The timer's body has
  one movable call site, so it keeps its state when the surface changes.
- **No *Im Rezept speichern* on SCR-05.** The screen calls only the
  controller, so the sheet opens without a recipe (`hasRecipe = false`); the
  recipe save stays on the setup (SCR-04). Steps without their own temperature
  show the plan's first temperature.
- **Dimming (TMR-17, SET-25).** The window brightness drops to 0.2 after 10 s
  untouched while RUNNING, but never brighter than the phone's own brightness
  setting (`min(0.2, system level)`; on the on-device checklist). The
  pre-alert brightens it until the step ends; the alert and the pre-alert are
  bright in their first frame. A new phase or step, a press, and closing the
  menu, a question or the sheet count as a touch, and it never dims while one
  of them is open. The rule is the pure `ScreenDimming`, tested with a moved
  clock; the screen re-checks only when the result can change.
- **Photo and liquor.** The tea photo sits at the top and fades into the
  background (mockup v2; since [D-095](#d-095) an ink-wash band with the
  photo as a small seal). The screen takes it as a parameter; since #54 the
  route passes the session tea's cover photo, or the placeholder, for every
  brew ([D-069](#d-069)).
  The liquor fill is the green liquor until the tea's style is in the state.
- **TalkBack.** One live region announces the state („Aufguss 2 von 3
  läuft", „Pausiert" …), the pre-alert („Gleich fertig"), „Noch 10 Sekunden"
  (only for steps longer than 10 s) and the alert (assertive). A 10 s pre-alert
  and the 10 s mark are one announcement („Gleich fertig, noch 10 Sekunden").
- **Lock screen.** Only the timer shows over the lock screen: `MainActivity`
  sets `showWhenLocked` while the timer is the current route with an active
  session, and `turnScreenOn` while it alerts there (`lockScreenFlags`). The
  route is read from the back stack, so the flags change when navigation
  starts, not after its fade. #47's full-screen intent opens the timer first,
  so an alert under the keyguard never shows the shell.
- **Navigation.** The timer is a top-level route above the shell; a tap on the
  active brew bar opens it, back keeps the session. *Timer-Einstellungen*
  opens the settings on the timer's stack; *Brühen anpassen* closes the timer
  until the setup exists (M1-13).
- **Active brew bar.** Fed by the controller's state; it ticks at each second
  of the countdown or the overtime while collected (not in the background),
  and a long step's progress moves only every 10 s (D-055 S9, tested).

<a id="d-057"></a>
### D-057 — Exact alarms, BrewTimerService and recovery in the first turn (M1-09 part 2, #42) · *Accepted* (Claude, 2026-10-22)

#42 added the Android adapters of the alarm and service ports, the alarm
receiver and the foreground service ([04 §4](04-brew-timer.md#4-components),
§6, §7.2; E3, E4; TMR-15, TMR-16, TMR-18). Decided while building them:

- **Alarm adapter** (`AndroidTimerAlarms`). Every `ScheduleAlarms` reconciles
  all six `TimerAlarmKind`s:
  - a kind with a time is set with its request code (101–106) and intent, so
    it replaces the earlier one; a kind without a time is cancelled;
  - pre-alert, deadline and overtime timeout:
    `setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP)`; idle demote and the
    quick-timer cancel: the same on `RTC_WAKEUP`; midnight: `set(RTC)`;
  - if exact alarms are refused, the alarm is set with `setAndAllowWhileIdle`
    and logged (`AlarmNotExact`);
  - the broadcast's action names the kind, so `dumpsys alarm` shows it.
- **Recovery in the first turn moves from #46 to #42** (amends D-056). E3
  needs it: in a process that an alarm started after a kill, the controller
  is idle.
  - The first turn of a process, whatever its input, reads `active_brew`,
    restores it and sends `recover` inside the event lock, once, before its
    own input (`EventSource.RECOVERY`). So a start in a new process is still
    refused while the persisted session is active (TMR-21).
  - A snapshot that `restore` refuses is cleared, its alarms are cancelled,
    and it is logged (`SnapshotUnreadable`).
  - A failed read is logged (`RecoveryFailed`) and tried twice more in the
    same turn (after 250 ms and 1 s; an alarm's turn stays awake). If every
    try fails, the alarm of the turn is set again 5 s out
    (`TimerAlarmPort.retry`, which leaves the other kinds alone) and the
    in-process wake-up comes back in 5 s, so the step's only alarm can never
    be used up by a passing database error.
  - The recovered session's tea name comes from its session row.
  - An alarm that started the process is logged with its scheduled time in
    the recovered state, so the log still shows scheduled vs actual (06 §5.6).
  - #46 keeps the rest: recovery at app start, `BOOT_COMPLETED` and
    `MY_PACKAGE_REPLACED`, the device-restore `IN_PROGRESS` row,
    *Beenden & neu starten*, and the tests of the whole 04 §7.2 table.
- **Receiver** (`TimerAlarmReceiver`, non-exported). It hands each alarm to
  `onAlarm(kind)` in one turn, kept alive with `goAsync`. The `StartForeground`
  of recovery or of the transition starts the service, within the exemption
  of the exact alarm.
  - An extra "ensure the service" after every alarm was rejected. The deadline
    alarm usually arrives just after the in-process wake-up handled the
    deadline; showing the alert again then would drop its full-screen intent.
  - Instead, the start Android refused last (kept until a newer command or a
    stop replaces it) is sent once more after the alarm's turn, with the same
    notification, full-screen intent included, and `FLAG_ONLY_ALERT_ONCE`. This
    covers the in-process wake-up winning the deadline race without the
    battery-optimisation exemption. *Amended by D-063:* the controller sends it
    inside its turn, with the notification of the state of that moment, also
    after a notification action or a tap.
- **The receiver and the full-screen alert find the controller through the
  app** (`TimerHost` with `TimerInputs`: `onAlarm` and `send`), not through
  Hilt, for two reasons:
  - the controller cannot be injected until every port has its adapter
    (D-056);
  - the app of the instrumented test cannot be a Hilt root: `@HiltAndroidApp`
    must sit in an application module.
  The ticket that binds the last port makes `UmamitApplication` the host with
  the injected controller. Until then the receiver drops an alarm; there is
  none, since nothing can start the controller. A debuggable build that gets
  an alarm without a host fails at once; a release build logs an error.
- **Service port** (`BrewTimerServicePort`) **and `BrewTimerService`**
  (`specialUse`, no timer logic):
  - Each command carries the notification of the state, its ID (2 while
    alerting, else 1) and the wake-lock time. The service shows it as its
    foreground notification and holds the partial wake lock: running, the
    remaining time + the longest alert (2 min) + 30 s; alerting, 2 min + 30 s;
    waiting states release it.
  - `startForeground(state, fullScreen)` now takes the full-screen flag, like
    `UpdateNotification`. So the alert that the service posts first at the
    deadline has the full-screen intent, and a later re-show has none.
  - Not yet in the foreground: `startForegroundService`. A
    `ForegroundServiceStartNotAllowedException` is logged
    (`ServiceStartRefused`) and the notification is posted without the
    service; an alert cancels notification 1 first.
  - Already in the foreground: the command goes with `startService`, which a
    running foreground service allows from the background, so the in-process
    wake-up can switch it to the alert. The same ID only updates the
    notification; a new ID takes `startForeground`. If that is refused, the
    service leaves the foreground (which removes notification 1), posts the
    alert alone (D-044) and logs it.
  - A stop never reaches the service before a pending start has called
    `startForeground`, which would crash the app; the service stops itself
    after the last pending command instead, with `stopSelfResult(startId)`,
    so Android ignores the stop when a newer start is already queued. The
    port and the service share this in a process-wide state, like the service
    itself.
  - The service creates the notification channels in `onCreate`, and the
    port before posting without the service: a foreground notification on a
    missing channel crashes the app (fresh install, cleared data).
  - A command takes a 10 s partial wake lock that the service releases once
    it holds its own, so the CPU stays awake between the alarm's broadcast
    and the service.
- **Notification content** (`TimerStateNotifications`) is minimal until #47.
  - Live Update: the tea or „Schnelltimer", the phase, the countdown chip
    while running, one ProgressStyle segment for the step with the pre-alert
    point.
  - Alert: „Jetzt abgießen!" on `steep_finished`, `CATEGORY_ALARM`.
  - Channels and IDs as verified (D-043, D-044).
- **Full-screen target until SCR-05 (#47; replaced by the timer route, D-064):** `TimerAlertActivity`. It shows
  over the lock screen and turns the screen on, set from code only while it is
  shown (04 §13). *Abgegossen* sends `poured` to the controller and closes it;
  a later alert updates its tea (`onNewIntent`).
- **For #47:** while the service runs, notification 1 or 2 is its foreground
  notification.
  - When the ID changes, the notifier changes it through the service, not
    with `notify`: the system ignores `cancel` of a foreground notification.
  - It never posts notification 2 itself while notification 1 is the
    foreground notification (D-044).
- **Probe:** it keeps its own scheduler, receiver and service until it is
  removed (#54), so the probe screen keeps working. The real classes reuse its
  verified patterns, channels and notification IDs.
- **Instrumented test** (`core:timer` androidTest, CI emulator job, API 35/36):
  - The main process of the test APK runs the real controller, alarm adapter,
    receiver, service, alert player (#44) and Room database, wired by hand in
    the test app. The ports of later tickets are logcat doubles; each ticket
    swaps in its adapter.
  - The test runner runs in its own process (`android:targetProcesses`).
  - A 25 s step without a pre-alert starts from an activity in the main
    process, like a tap. The test kills that process with SIGKILL from the same
    uid, as the low-memory killer does. `am kill` would not kill it: it spares
    processes with a foreground service and instrumented ones.
  - The deadline alarm starts a new process. The test asserts: the full-screen
    alert activity is resumed over the lock screen; the alert notification is
    the foreground notification, not `FLAG_SILENT`, and names the recovered
    tea; notification 1 is gone.
  - It starts from a clean state (process, alarms, database), fails fast when
    the start is refused, and checks that at least 5 s of the step are left
    at the kill.
- **Open for #46:** a process killed during ALERTING has only the overtime
  alarm left, however its deadline was handled (the alarm or the in-process
  wake-up; 04 §7.2). *Decided in D-063:* an alert watchdog alarm while the
  alert plays, not an engine due time.

<a id="d-056"></a>
### D-056 — BrewSessionController core (M1-09 part 1, #41) · *Accepted* (Claude, 2026-10-22)

#41 built the app-scoped controller in `core:timer` on the unchanged engine
([04 §4](04-brew-timer.md#4-components), §7.1). Decided while building it:

- **Inputs are suspend functions, outputs are ports.** Inputs:
  `startSession(BrewStart)`, `startQuickTimer(seconds)`, `send(event, source)`,
  `onAlarm(kind)` and `alertCompleted(alert)`. `send` takes only user events;
  the starts, `recover`, `AlertCompleted` and the time-based events are
  refused with `IllegalArgumentException`. Outputs are seven ports: alarms
  (`reconcile(DueTimes)`), service, notifier (also notification 3), alert
  player, saved sheet, diagnostics, and the in-process wake-up. A port never
  calls back into the controller synchronously. An adapter that reports back
  calls a suspend input instead (the alert player calls `alertCompleted`), so
  there are no callbacks and no dependency cycles.
- **Alarm kinds.** `TimerAlarmKind` holds the 04 §6.2 table: request code,
  clock, exactness, wake-up and event. The alarm port gets the full due set
  and reconciles it per kind (#42). `onAlarm(kind)` sends every event due at
  that moment (`dueEvents`), not only the kind's own. It logs the alarm with
  its scheduled time.
- **The in-process wake-up is a port.** The controller re-arms it after every
  handled event, accepted or not, for the earliest due time of a state of the
  current boot. Elapsed fields count on the elapsed clock and wall fields on
  the wall clock. In production, `CoroutineTimerWakeUp` detaches a fired
  wake-up before its callback runs. So the controller can re-arm from inside
  that turn without cancelling it. A port was chosen because Room's
  compatibility-mode `withTransaction` cannot run on a virtual-time
  dispatcher: the controller tests drive the wake-up with the fake clock
  instead.
- **A started turn runs to its end.** Once a turn holds the event lock, it is
  non-cancellable. Before this, a caller cancelled mid-turn (a screen closing
  right after *Abgegossen*) rolled back the transaction and skipped
  `StopAlert`, so the alert kept playing (rule 8). A caller cancelled while
  it waits for the lock still drops its event; the state is then unchanged.
- **Failures never block the alert (04 §4, D-054).**
  - A failed transaction (data effects + `Persist`) is logged (`PersistFailed`,
    no retry) and `Persist` alone is retried once; a failed retry is logged
    too.
  - The retried store write still records the step, because the store's
    write is authoritative (D-052).
  - Each side effect runs on its own: a port that throws is logged
    (`EffectFailed`) and the next effect still runs.
  - A diagnostics port that throws only logs to logcat.
  - An ignored event does not retry a failed persist; the next accepted
    transition persists again (04 §7.1).
- **One alert per deadline.** `StepAlert(kind, stepIndex, deadlineWallMs)`
  names an alert. `StartAlert` is skipped while the same alert plays;
  `StopAlert` and the player's end report clear it. An end report for any
  other alert is ignored and logged (`StaleAlertCompletion`). Otherwise it
  could mark a later step's alert as played, and recovery would not replay
  it. `UpdateNotification` is full-screen exactly when `StartAlert` is in the
  same transition.
- **UI state.** `TimerUiState` is `Idle` or `Active`. `Active` holds the kind,
  the tea name, the phase, the step, the plan with each step's status, the
  deadline on both clocks (a long step's end follows the wall clock), the
  paused remaining time, the pre-alert lead, due time and played flag, the
  long step, the kettle temperature, `demoted` and `finishWouldDiscard`. The
  screens compute the remaining time and the overtime with
  `display(nowElapsedMs)`, which rounds as in 04 §5; a test checks it against
  the engine. The state is published after the persist and before the side
  effects.
- **Pre-alert at the start.** The recipe's value, or else the global setting
  (an unreadable settings file counts as the defaults), is frozen as the
  inherited value. The Schritte-Sheet's choice from the setup
  (`BrewStart.preAlert`) becomes the session override.
- **Hilt.** The controller is a `@Singleton` that takes `TimerClocks` and
  `TimerPorts`. `TimerModule` binds `AndroidMonotonicClock`
  (`elapsedRealtime` and `BOOT_COUNT`, read once per process) and
  `CoroutineTimerWakeUp`. The other ports stay unbound until their adapters
  land (#42, #43, #44, #47, M1-16). Until then nothing can inject the
  controller, so no build ships a silent stand-in.
- **Left to #46:** `recover` at process start, the restored `IN_PROGRESS`
  row and *Beenden & neu starten*. A turn can already send several events.
  The engine was not changed.

<a id="d-055"></a>
### D-055 — Spec #27 slice review — UI · *Accepted* (Claude, 2026-10-22)

The T4 review of the UI slice of spec #27 (#29, #33, #34, #38) found no
blocking issue and eleven should-fix items. Decided and done:

- **Alert-safe children (S1, rule 8).** `AlertSurface` provides
  `LocalOnAlertSurface`; under it `TimerRingDefaults.colors()` returns the
  alert colours (ink, no liquor) and `PillButtonDefaults.colors()` an ink pill
  with washi text. A host that forgets them no longer makes the digits (1.08:1)
  or the cream pill (1.17:1) vanish in the washi phase. Contrast at both ends of
  the pulse: text 13.5:1 (washi) and 4.55:1 (matcha); the pill 13.5:1 / 4.55:1
  against the surface and 13.5:1 for its label. A test computes them from the
  colours the components resolve, in both themes, and a render test checks ink
  at the washi phase.
- **Overtime on the alert surface (S2a).** „+2:30 überzogen" is ink; the warm
  accent `#D9A35F` is only a non-text mark (a small dot), because as text it is
  1.97:1 on washi and 1.51:1 on matcha (03 §3.1, SCR-05, TMR-06).
- **No Schritte-Sheet in ALERTING (S2b).** *Schritte bearbeiten* and *Weiterer
  Aufguss* are not offered in ALERTING (D-051 keeps the sheet hidden there, and
  rule 8); they return after *Abgegossen* (03 SCR-05, SCR-31; 02 TMR-10, TMR-11).
- **Active brew bar in ALERTING (S3).** `ActiveBrew.alerting` and
  `overtimeSeconds`: the bar snaps to static matcha `#6B9845` with ink content
  (4.55:1), „Jetzt abgießen!" in place of the step and „+0:07" counting up; the
  mini ring is full, in ink, without liquor. It does not pulse: the full alert
  is SCR-05, which a tap opens. It never navigates by itself (03 §3.8, GEN-06).
- **Unsaved edits in the Schritte-Sheet (S4, GEN-08).** Swipe, fling, scrim,
  handle and back with unsaved edits keep the sheet and ask „Änderungen
  verwerfen?" (*Weiter bearbeiten* / *Verwerfen*, the new
  `DiscardChangesDialog`); without edits, or with edits undone by hand, the
  sheet closes at once. *Fertig* counts its edits as applied. *Verwerfen*
  resets the edits after the sheet has left. Back with edits is handled before
  Material's predictive close, so a veto never leaves the sheet half-shrunk.
  The alert path is unchanged: `alerting = true` removes the sheet and the
  question in the same frame, without asking. Amends D-051 „Closing applies
  nothing".
- **Scroll-to-top one-shot (S5, GEN-07, D-049).** A screen's handled count is
  no longer saveable: a screen composed again (rotation, return from a pushed
  screen, tab switch) adopts the current count, so a reset counter never
  swallows re-selects and a pop never replays a scroll. The shell remembers
  the back stack entry a re-select scrolled, so a screen pushed again later
  scrolls first instead of popping.
- **Bar text contrast (S6).** The bar's step line uses `onSurface`
  (10.8:1 light, 10.1:1 dark) instead of `onSurfaceVariant` (4.21:1 light on
  `surfaceContainerHigh`). A unit test checks every text/background token pair
  the components use in both schemes.
- **Reduce motion on the device (S7).** Reduce motion is animator scale 0, and
  Compose then ends every scaled animation on its first frame: on the phone
  every transition is instant, the 100 ms fades included. Only the alert and
  glow rules depend on the flag there; the in-app `reduceMotion` path stays as
  it is (tests, previews). Corrects D-046 „reduce motion keeps short fades" and
  03 §3.6. The bar leaves with `exitFade` like the routes.
- **Drawing without per-frame allocations (S8).** Ring and mini ring build
  strokes, the glow's gradient and the marker's place once per size
  (`drawWithCache`); the liquor is an arc closed by its chord (no path, no
  clip), still linear in height.
- **Mini ring long-step mode (S9).** `ActiveBrew.longStep`: the mini ring glides
  like `TimerRing`, and `ActiveBrewSource.progress` must hold still between the
  coarse updates of a long step (M1-09 tests the bound source).
- **Rail insets (S10).** `ScreenScaffold` also takes the bottom safe-drawing
  inset; the bar layout has consumed it already, and in the rail layout the
  shell consumes it above the content while the brew bar applies it.
- **Tests (S11).** The reduce-motion long-step test uses a level below 1; the
  shell tests observe real scrolls in a short window, rotation, return without
  replay, a re-pushed screen and per-tab scroll positions; the shell has
  screenshots at font scale 2.0 and with the alerting bar.

Optional items: only `exitFade` in the 03 §3.6 table and the 03 date were taken;
the others stay open for the milestone's design pass.

<a id="d-054"></a>
### D-054 — Spec #27 slice review — engine and data · *Accepted* (Claude, 2026-10-22)

The T4 slice review of the timer engine (#28, #31, #32) and of the data layer
(#30, #35, #36, #37) for spec #27, with the orchestrator's decisions on it
([04 §3, §4, §5, §6.2, §7.1, §11, §12](04-brew-timer.md#4-components);
[05 §3.6, §8, §9](05-data-model.md#9-migrations); 02 TMR-14, TMR-23;
08 §2.5a). The UI part of the review is recorded separately.

**Engine**

- **An edit never consumes a due pre-alert (B1).** `addStep` and
  `editSteps` recomputed the running step's pre-alert from now, which erased
  one that had come due but whose `preAlertReached` was still queued (ms, up
  to 1 s, longer after a process restart), so its tick was lost. Now an
  unplayed, already due pre-alert stays due while the step still has one,
  and `preAlertReached` decides by the 1-s rule. Only *Aus* (or N that leaves
  the step without a pre-alert, E7) removes it. The seeded test checks "due
  before an edit ⇒ due after".
- **Stale readings (S1).** A reading older than the state produced states
  that `restore` refuses (a negative paused total, more remaining than
  planned), so the next process start would have cleared `active_brew` and
  lost the alert. The engine clamps: `pause` keeps 1 ms – planned, and a
  negative open pause counts as 0 in `resume` and in the recorded paused
  time. Clamping was chosen over rejecting: a rejected *Pause* or *Weiter*
  would drop a user action, a clamped one keeps it at most a few ms off. The
  controller rules are now in 04 §4: read the clocks inside the event lock,
  one reading per `reduce`; a failed transaction is logged, `Persist` is
  retried alone once, and the side effects (`StartAlert`, `StopAlert`,
  `PlayPreAlert`) run anyway. The seeded test feeds readings older than the
  last accepted one.
- **`restore` refuses what the engine never produces (O2):** a demoted
  Running or Alerting state, a pre-alert on a long step, a played pre-alert
  that is still due. Tests that built such states by hand now drive the
  reducer (O7).
- **Notification per state (S2, D-044).** `StartForeground` and
  `UpdateNotification` use the new state's notification: the alert (ID 2) is
  the foreground notification while alerting and ID 1 is never posted then;
  ID 1 in every other active state (04 §4, `TimerEffect` KDoc).
- **`CompleteSession(sessionId, autoFinished)` (S3, data S5).** True only for
  the midnight timeout; `SessionRepository.completeSession` takes it without
  a default, so `brew_session.auto_finished` is set (05 §3.6).
- **Alarm kinds (S4).** 04 §6.2 has one row per `DueTimes` field: pre-alert,
  deadline and overtime timeout exact `ELAPSED_REALTIME_WAKEUP`; idle demote
  and the quick-timer cancel exact `RTC_WAKEUP`; midnight inexact `RTC`
  without wake-up; fixed request codes 101–106 (101 and 102 as in
  `ExactAlarmScheduler`). `BrewTimerEngine.dueEvents(state,
  now)` maps the due set to events in apply order, for the wake-up and the
  receivers alike.
- **Beenden & neu starten (S5, TMR-21).** No new engine event: the
  controller sends the ending event (`finishSession` for a brew session,
  `cancel` or `poured` for the quick timer) and then the start in one
  event-lock turn, and executes the finish's `ShowSavedSheet(id)` as
  `NotifySessionSaved(id)` („Sitzung gespeichert", TMR-14). The TMR-21
  dialog is the confirmation, so a session without a completed non-rinse
  step is discarded without a second question; it is not auto-finished.
- **Seeded service oracle (S6).** The invariant test now checks the D-053
  core rule against an oracle written from the spec (every active state
  needs the service except a demoted session and a running long step):
  entering such a state ensures it before the notification, leaving it for
  another active state stops it, recovery ensures it exactly when needed. It
  also checks the pre-alert tick (once per step, ≥ 1 s before the deadline,
  never on a long step) and that no due event is ignored after an ignored
  event too.
- **Smaller points.** A tick that plays ensures the service first (O3), so
  after a refused restart the exact pre-alert alarm can start it.
  `preAlertLeadSeconds(state)` gives the ring and the progress point the
  current step's N without re-implementing the skip rules (O6). The overtime
  auto-ack takes only states with a deadline (`TimerState.WithDeadline`), so
  `reduce` has no `error(...)` branch left (O1). Spec drift fixed: E2 (no
  alert after 10 min of overtime at recovery), E6 (skipped inside the window),
  TMR-14 idle time, TMR-23 session override, the §3 diagram and §3.1 table,
  §6.2 reconciliation.
- **Not done:** O4 (validating `PlannedStep` and `editSteps` input ranges),
  O5 (an engine signal for the reboot notice) and the optional
  `notificationSlot(state)`; the controller and the UI validate their input
  in M1-09/M1-11.

**Data**

- **Seed photo download waits for the seed teas (S1).** Before a seed tea
  exists it sends nothing, leaves its once-only marker unset and returns the
  current progress, so a call that races the seeder does not use up the run.
  A settings or database read failure no longer escapes as an exception
  (O3 of the data review).
- **Schema v1 freezes with the first rc (S2).** From `v0.1.0-rc.1` on, every
  schema change bumps the version and ships a tested migration (05 §9); this
  replaces D-052's "changed in place until the first release". CI runs
  `scripts/check-frozen-schemas.sh`: every schema JSON a `v*` tag contains
  must stay byte-identical (the probe tags contain none). *D-070 compares the
  version and identity hash instead and also runs it in the release
  workflow.*
- **No new tables before the freeze (S3, declined).** `tag`, `tea_tag`,
  `custom_descriptor`, `session_descriptor`, `tea_fts` and `session_fts`
  arrive with M3 and M4 through tested migrations, as 05 §9 plans. Adding
  unused tables now would freeze their design before the features that need
  them are specified in detail, and M3 needs the migration machinery anyway.
- **Data effects are total (S4).** Inside the controller's transition
  transaction, a `RecordStep` without its row changes nothing (logged; the
  `Persist` re-syncs the rows), an unreadable settings file counts as the
  defaults for *Bestand abziehen*, and a session zone this device does not
  know counts as the device's zone. A throw there would roll back `Persist`
  and leave a zombie `active_brew`.
- **Restore sweeps photo rows (S6).** In the restore branch of the open
  callback, `photo` rows whose file is missing are deleted and dangling
  covers cleared, in the same transaction as the `active_brew` clear, so
  CAT-15 can refill the seed covers in M2. Rows whose file exists stay (a
  false-positive restore loses nothing).
- **Exact session cost (O1).** Leaf × Σ price ÷ Σ grams in `BigDecimal`,
  rounded half up once: 25 g Hisui is 448 ct, not 447.
- **Tests (S7):** re-saving a tea, vessel, recipe or photo keeps everything
  that hangs off it (fails with REPLACE); half-up costs through
  `completeSession`; download before seeding; the real DataStore read inside
  the transaction.
- **One HTTP client (#37 O6).** Detekt `ForbiddenImport` forbids
  `okhttp3.OkHttpClient`, `okhttp3.Request`, `java.net.URL`,
  `URLConnection`, `HttpURLConnection` and `HttpsURLConnection` outside
  `core/network`, so GEN-01 AC1 is structural.

<a id="d-053"></a>
### D-053 — Timer time rules and the foreground-service contract (M1-08 part 2, #31) · *Accepted* (Claude, 2026-10-22)

#31 added the time-based rules to the pure engine, with S5 of the #28 review
and the notes of the #32 review (O6, O7, the consume rule of D-048)
([04 §3.2](04-brew-timer.md#32-events-and-transitions), §4, §5, §6.1, §7.2,
§8.2, §10; [05 §3.12](05-data-model.md#312-activebrew)):

- **Due set.** `DueTimes` has six fields: pre-alert, deadline and overtime
  timeout on the elapsed clock; idle demote, midnight and the quick-timer
  cancel on the wall clock. Each time-based event (`preAlertReached`,
  `deadlineReached`, `overtimeTimeout`, `idleDemote`, `midnightTimeout`,
  `quickTimerTimeout`) is accepted only when due, and a due one is always
  accepted. The seeded invariant test checks this after every accepted
  event: each due event is accepted by the new state.
- **Pre-alert.** N = session override ?: inherited (recipe ?: global). A
  step has none when N = 0, it is shorter than N + 5 s, it is a rinse
  shorter than 15 s, or it is a long step. `start` schedules it at
  deadline − N. `resume` and `editSteps` recompute an unplayed one from the
  deadline and the current N, and skip it when that time is not after now
  (exactly N left counts as past). A played one never comes back (E6).
  `preAlertReached` plays the tick while **≥ 1 s** is left before the
  deadline (the tick is 0.8 s, D-045), and later only marks it played. Both
  clear the due time and update the notification.
- **Overtime timeout.** It is due at deadline + 10 min in Alerting and uses
  the shared transition: actual = planned, overtime 0, ended at the
  deadline's wall time, auto-ack; a quick timer ends. A live
  `deadlineReached` that late applies the timeout instead of alerting, and
  `poured`, `endStepNow` or `finishSession` that late record the same
  auto-ack (O6), so live and recovered sessions agree.
- **Idle time.** A waiting state is idle since its last interaction or since
  it started waiting (`readySince`, `pausedSince`), whichever is later. The
  overtime timeout is not an interaction. A cold brew started at 18:00 that
  times out at 01:10 would otherwise be demoted, and finished at midnight,
  the moment it starts waiting. The wall clock stays the measure (D-048).
- **Idle demote.** For brew sessions only; the quick timer's 30-min cancel
  comes at the same time. It is due 30 min after the idle start while the
  session is not demoted. It sets `demoted` and emits `StopForeground` and
  `UpdateNotification` (the regular ongoing notification). Any user event
  clears `demoted`.
- **Foreground-service contract (S5).** Every active state runs with the
  service except a demoted session and a long running step.
  - `start` and `resume` emit `StartForeground` for a short step and
    `StopForeground` for a long one.
  - `deadlineReached` ensures the service before `StartAlert`.
  - `idleDemote` and every way to Idle emit `StopForeground`.
    `StopForeground` removes notifications 1 and 2 also when they were
    posted without the service, so Idle never emits `UpdateNotification`.
  - Any other accepted transition from a state without the service to one
    with it adds `StartForeground`: a user event on a demoted session, a
    pause or early end of a long step.
  - `recover` restores the service only where the state needs it (O7).
- **Midnight timeout.** For brew sessions in Ready, Paused or Finished,
  demoted or not. It is due at the first start of a local day in the
  session's zone at or after idle start + 6 h. The 6 h are real hours, and
  `LocalDate.atStartOfDay` handles a day whose midnight a DST change skips.
  It reuses the finish rules without confirmation. If a non-rinse step was
  completed (a paused step counts), the session is completed with the new
  side effect `NotifySessionSaved` (notification 3, „Sitzung gespeichert")
  and no saved sheet. Otherwise it is discarded silently. A paused step ends
  where its pause began; the open pause is not recorded (#28 review,
  decision 9).
- **Recovery.** A waiting session applies the timeouts that came while the
  process was dead, in this order: quick-timer cancel, midnight, idle
  demote. A demoted session and a long running step are restored without
  `StartForeground`.
- **Long steps.** A step is long when it is planned for ≥ 10 min, or when
  the session is a cold or ice brew (`StartSession.longStepsOnly`, from
  `BrewTimerEngine.longStepsOnly(method)`). The flag is not an `active_brew`
  column: `TimerSnapshot.longStepsOnly` is filled from
  `brew_session.method`. It is false for the quick timer, and `restore`
  refuses a quick timer that has it. `isLongStep(state)` and
  `longStepEndWallMs(state, now)` (now + the remaining time on the
  monotonic clock, so it follows a wall-clock change, E1) feed
  „Fertig um 14:30".

<a id="d-052"></a>
### D-052 — Session lifecycle, active brew store, device backup and restore detection (M1-06, #36) · *Accepted* (Claude, 2026-10-22)

[05 §3.6, §3.7, §3.12, §8, §9](05-data-model.md#37-sessionstep):

- **API follows the engine's effects.** `SessionRepository` has one operation
  per data effect (`startSession`, `replacePendingSteps`, `recordStep`,
  `completeSession`, `discardSession`) and takes the engine's `PlannedStep`,
  `StepRecord` and `TimerSnapshot`, so `core:data` depends on
  `core:timer-engine` (pure). `ActiveBrewStore.persist(snapshot?)` is
  `Persist` (null clears). `DatabaseTransactions` lets the controller run a
  transition's data effects and `Persist` in one Room transaction; the
  repositories' own transactions join it.
- **The store's write is authoritative.** Persisting a snapshot also brings
  the session's step rows to its plan (rows keep their id, note and
  `created_at`; missing rows are inserted, extra positions deleted). A
  persisted state restores equal even if a data effect was missed.
  Temperatures are snapped to 5 °C at every write (GEN-12), so the round trip
  is lossless for every state whose temperatures are multiples of 5, which
  the recipes and the Schritte-Sheet guarantee.
- **Schema v1 changed in place** (unreleased; frozen from `v0.1.0-rc.1`,
  D-054): `active_brew` gains
  `pre_alert_override_seconds`, `pre_alert_inherited_seconds` and `zone_id`
  and loses `active_elapsed_ms` (D-048); `(session_id, position)` in
  `session_step` becomes unique.
- **Completion details.**
  - `ended_at` is the end of the last ended step (the completion time if no
    step has one), so a late *Sitzung beenden* or a recovery does not stretch
    the session.
  - Cost = leaf × cost per gram, rounded half up, null without a tea, leaf
    amount or priced stock.
  - The BREW event is dated `ended_at`, needs a tea and a leaf amount > 0, and
    is skipped with *Bestand abziehen* off.
  - INV-06 is implemented in full: the opened date is set when it is empty,
    and also when the latest INITIAL/PURCHASE is on a later local day than
    `opened_date` and the stock before it was ≤ 0.001 g. The session's local
    date (of `started_at`, E13) is used.
  - A session that is no longer `IN_PROGRESS` is not completed again, so a
    repeated `CompleteSession` adds no second deduction.
- **Device backup: includes only.** The rules include exactly `umamit.db`,
  `-wal`, `-shm` and `datastore/` instead of "the whole database domain
  except `catalog.db*`" (05 §8 v0.2). With includes, everything else is out
  by construction, including databases that libraries put there (LeakCanary's
  `leaks.db` in debug builds). `fullBackupContent` carries the same set, so
  no backup path falls back to "everything".
- **Restore detection in Room's callbacks.** `onCreate` writes the marker;
  `onOpen` without the marker clears `active_brew`, then writes the marker.
  Room runs `onOpen` before serving any query, so nothing reads the restored
  row. A marker that cannot be written (disk full) is logged and retried on
  the next start, which then clears `active_brew` once more; failing the
  database open would stop the whole app. A restored `IN_PROGRESS` session
  row is left for M1-09 to finish or discard.
- **CI schema check.** After the quality gate, CI fails on
  `git diff --exit-code core/database/schemas` and on an uncommitted schema
  file, since `ExportedSchemaTest` compares with the freshly generated copy.
- **Fakes.** `FakeSessions` (session repository, store, transactions) shares
  rows like the database; it has no rollback and implements only the
  "empty opened date" part of INV-06.

<a id="d-051"></a>
### D-051 — Schritte-Sheet details (M1-19) · *Accepted* (Claude, 2026-10-22)

#38 settled these points of SCR-31 that the specs left open
([03 SCR-31](03-ux-ui.md#scr-31-schritte-sheet-session-steps)):

- **Material 3's `ModalBottomSheet`, with its own motion.** The sheet runs
  in its own window, so TalkBack and keyboard focus stay inside it, and
  Material brings predictive back, drag and fling to dismiss with the
  gesture's velocity, nested scrolling of the step list and clean reopening.
  It always opens fully expanded (`skipPartiallyExpanded`). Its show and hide
  motion is Material's (calm, no overshoot), not the `sheet` token of
  03 §3.6: Material 3 1.4 keeps the sheet's motion scheme internal. A first
  version with an own sheet drawn in the host window applied the token, but
  the motion and accessibility review of #38 blocked it (no focus trap, a
  fling lost its velocity, a reopen during the exit stayed displaced, no
  predictive back). Accessibility and reliability come before matching the
  token; the PO judges the motion from the GIF in #39. Under reduce motion
  the sheet opens already expanded and closes without the slide.
- **Alerts first (rule 8).** The sheet never covers the alert. When the timer
  enters ALERTING, the host sets `alerting = true` and `visible = false`: the
  sheet's window is removed at once, without the close animation, and the
  unapplied edits are dropped. While `alerting` is true the sheet stays
  hidden.
- **Closing applies nothing.** Only *Fertig* sends `editSteps`; a swipe,
  back, the scrim or the handle close the sheet without changing the session.
  With unsaved edits they first ask „Änderungen verwerfen?" (GEN-08,
  amended by [D-055](#d-055)).
  *Im Rezept speichern* saves the recipe and keeps the sheet open, so the
  session still changes only with *Fertig*.
- **Stepper rules.** A tap moves to the next multiple of 5 s below 10 min and
  of 1 min from 10 min on (lengthen: 9:55 → 10:00 → 11:00; shorten: 11:00 →
  10:00 → 9:55), within 5 s – 24 h. Temperatures move to the next multiple of
  5 °C within 5–100 °C (05 §3.2). A step without its own temperature shows the
  recipe's base temperature and stays without one until it is changed; a step
  without any (quick timer) has no temperature stepper.
- **Delete** is disabled when it would leave no non-rinse step in the whole
  plan, done steps included (D-048). Done (✓), running („läuft") and skipped
  („übersprungen") steps are read-only.
- **Recipe save.** The whole plan as shown (done steps included) replaces the
  recipe's steps (step notes arrive in M4). *Wie Einstellung* keeps the
  recipe's own pre-alert, *Aus* stores 0, a lead time stores its seconds.
  The confirmation snackbar „Im Rezept gespeichert" shows on the tap, above
  the buttons, once (a reopened sheet does not replay it); the host does the
  write.
- **Step kinds in German:** Aufguss (INFUSION), Spülaufguss (RINSE),
  Aufschlagen (WHISK).

<a id="d-050"></a>
### D-050 — Shop client and seed photo download (M1-07, #37 and its T4 review) · *Accepted* (Claude, 2026-10-22)

**Shop client (`core:network`, rule 7):**
- One `ShopClient` singleton for all three network uses. Allow-list = scheme,
  host and port of `https://www.tee-kontor-kiel.de`, checked before any
  connection and again on the network; redirects are never followed; the
  base URL can be changed only by a `@VisibleForTesting` factory.
- **Direct connection** (`Proxy.NO_PROXY`): a system, Wi-Fi or PAC proxy is
  not used, so the shop is the real TCP destination of every request.
- **One connection, one request per call:** one dispatcher thread,
  `maxRequests = maxRequestsPerHost = 1`, a pool of one connection, no fast
  fallback (no parallel connects), no connection retries. OkHttp repeats a
  503 with `Retry-After: 0` by itself, so the client hides `Retry-After` from
  OkHttp (it is kept under an internal header for the stop rule) and refuses
  any second network attempt of a call before it is written.
- **≤ 2 requests/s on the wire:** the limiter runs as a network interceptor,
  so requests are written ≥ 500 ms apart however long the connection setup
  takes.
- **Stop signals** (08 §2.8): `cf-mitigated: challenge`; a 401/403/503 with
  challenge markers (incl. `challenges.cloudflare.com`, `cf-turnstile`) or
  served by Cloudflare (`CF-RAY`, `Server: cloudflare`, a block page); HTTP
  429; any `Retry-After`. Bodies over 5 MB are refused.
- **robots.txt:** RFC 9309 with a backtracking-free wildcard matcher (runs
  of `*` collapsed), only the first 512 KiB read, a leading BOM ignored, a
  `User-agent` product token matched without its version. The parser lives
  in `core:network` from M1 on (06 §3 had it in M2). `Crawl-delay` is not
  yet honoured (M2).

**Seed photo download (`core:data`, ONB-06):**
- Runs once: the marker `seedPhotosAttempted` (05 §6) is set **before the
  first request**, so a skip, a failure or a crash never repeats it. It is in
  DataStore and so in the device backup: a restored phone doesn't download
  again (CAT-15 fills in photos).
- robots.txt first. 404/410 → everything allowed; 401/403 or 5xx → stop;
  any other failure (3xx, other 4xx) → everything disallowed.
- Progress: `Idle`, `Running(current, total)` (photo being fetched, robots.txt
  counts as photo 1), `Done(stored)`, `Skipped` (*Überspringen* or the
  caller's cancellation), `Failed(cause)`.
- `Failed` = the run stopped: a stop signal, a 401/403/5xx, a network error
  (offline), or a storage error (disk full, database). Nothing is shown and
  nothing escapes as an exception. Photos stored before stay.
- Per photo, without stopping: a robots-disallowed path, a 404 or other 4xx,
  or an answer that is no complete JPEG is left out (placeholder), and so is
  a seed tea that is gone or already has a cover.
- Images are decoded and **re-encoded** (05 §3.9, 06 §5.2): sources larger
  than 8192 px per side or 40 MP and truncated images (no EOI) are refused;
  the stored JPEG is at most 2048 px, quality 85, without metadata.

<a id="d-049"></a>
### D-049 — Navigation shell details (M1-05, #34) · *Accepted* (Claude, 2026-10-22)

- One navigation controller per tab; Settings and the probe sit on the stack
  of the tab they were opened from.
- Re-selecting a tab (03 §2.2): the first tap scrolls the screen on top to the
  top; a second tap pops a deeper stack to the tab root. On a root screen every
  tap scrolls. The scroll is a one-shot event, never replayed on return
  (how, after the slice review: [D-055](#d-055)).
- Tab switches use a fade-through (`tabSwitch`, 03 §3.6); back from another
  tab returns to Start with a predictive-back preview.
- The active brew bar applies the system bar insets itself in the rail layout.

<a id="d-048"></a>
### D-048 — Step edits, quick timer, recovery and the `active_brew` snapshot · *Accepted* (Claude, 2026-10-22)

#32 settled these engine points, from the T4 review of #28 (S6, S7) and the
spec gaps it left ([04 §3.2](04-brew-timer.md#32-events-and-transitions),
§5, §7, §10; [05 §3.12](05-data-model.md#312-activebrew)):

- **`addStep` default.** The base is the last non-rinse step of the plan (the
  last step if all are rinses), so a trailing rinse never sets the next
  infusion. The new step keeps the base's kind (a whisk session adds a whisk;
  a rinse base gives an infusion) and temperature. The duration is
  1.5 × the base, rounded half up to 5 s when the unrounded value is below
  10 min and to whole minutes from 10 min on, then clamped to 5 s – 24 h. It
  is the public pure `defaultAddedStep(plan)`, which the Schritte-Sheet also
  uses.
- **`editSteps`.** The request carries the whole plan, so the sheet sends
  what it shows. The started or ended prefix must come back unchanged, else
  `Rejected(StartedStepChanged)`. At least one non-rinse step must remain in
  the whole plan, done steps included, else `Rejected(NoBrewStepLeft)`.
  Rejections keep the state and have no effects. A Ready whose step was
  removed re-points to the first pending step, or goes to Finished; Finished
  goes to Ready when steps are added. `addStep` and `editSteps` emit
  `ReplacePendingSteps(sessionId, fromIndex, steps)`, which replaces the
  unstarted rows from `fromIndex` on (05 §3.7). Both are user interactions.
- **Pre-alert values.** `PreAlertSettings(sessionOverrideSeconds,
  inheritedSeconds)`: `startSession` freezes recipe ?: global, and the
  effective value is session override ?: inherited. `editSteps` carries a
  `PreAlertChoice` (`Inherit` = *Wie Einstellung* clears the override, `Off`
  = 0, `Seconds(n)`). The quick timer inherits the global value.
- **Quick timer.** `startQuickTimer(duration, globalPreAlert)` starts it with
  `SessionKind.Quick`. `cancel` works in Ready, Running and Paused but not
  while alerting, where *Fertig* ends it. `quickTimerTimeout` is due 30 min
  after the last interaction in Ready or Paused, on the wall clock like the
  other waiting timeouts (`DueTimes.quickCancelAtWallMs`). `editSteps`,
  `addStep`, `endStepNow`, `skipStep`, `finishSession` and `discard` are
  ignored, and `finishWouldDiscard` is false. A start of either kind while
  one is active is `Rejected(ActiveTimerExists(kind))`, also before
  `recover`. A duration outside 5 s – 24 h or a pre-alert other than 0 or
  5–30 s is `Rejected(InvalidQuickTimer)`; `reduce` never throws.
- **Recovery in the engine.** The clock reading carries the boot count, and
  the session records the boot of its elapsed values. `recover` is an event
  sent on every process start:
  - after a reboot, every elapsed value is first moved to the new elapsed
    clock at the same distance from now as its wall value, clamped to
    physical bounds: a passed deadline and a pause start ≤ 0, a running
    deadline ≤ the planned time (every old-boot instant lies before
    elapsed 0). A state impossible on the same boot count (an alert deadline
    or a pause start after now, more than the planned time left) is treated
    as a reboot. This catches a wall clock set back across the reboot. A
    wall clock set forward cannot be detected: the step counts as longer
    off and past 10 min is auto-acknowledged silently, an accepted limit;
  - waiting states and a running step with its deadline ahead are restored;
  - a deadline passed less than 10 min ago alerts with overtime;
  - from 10 min on (`≥`, the same instant at which the #31 timeout event
    becomes due) the step is poured on time with `overtimeAutoAck`, ended at
    the deadline's wall time, silently; a quick timer just ends;
  - every row emits `StartForeground` and `UpdateNotification`, and the
    alert is replayed while `alertPlayed` is false;
  - an unplayed pre-alert stays due even if its time has passed, so a
    pre-alert alarm that restarts the process can still tick. #31 implements
    the rule: a due `preAlertReached` is never ignored. It plays the tick and
    sets `preAlertPlayed` while there is still time before the deadline;
    otherwise, and always at or after the deadline, it only sets
    `preAlertPlayed` (accepted, no `PlayPreAlert`). In general a time-based
    event that is due is applied or consumed, never `Ignored`, so the
    controller's wake-up cannot spin;
  - a quick timer whose 30-min cancel passed while the process was dead
    ends silently;
  - the midnight row comes with #31's midnight rule.
  Any other event whose reading comes from another boot is
  `Rejected(BootChanged)` until `recover` ran. The controller sends
  `recover` exactly once per process, first, inside the lock of the restore,
  and re-arms alarms only from a recovered state. `StartAlert` is
  idempotent ("ensure the alert of the alerting step plays"), so a deadline
  event before `recover`, or a second `recover`, gives one alert. The
  overtime-timeout transition is shared, so #31 only adds the event and its
  due time. For #31: a live `poured`/`endStepNow`/`finishSession` at
  ≥ deadline + 10 min should record the timeout's auto-ack like `recover`,
  and recovery must not restart the service for a `demoted` session or a
  long step.
- **Snapshot.** `ActiveBrewSnapshot` (`core:model`) mirrors 05 §3.12 and
  adds `pre_alert_override_seconds`, `pre_alert_inherited_seconds` and
  `zone_id`. It carries `alert_played`, `demoted` and `boot_count`.
  `SessionStepRow` mirrors the timer columns of `session_step`.
  `active_elapsed_ms` is dropped, because it is derived. A step's status is
  derived, not stored. In FINISHED, `step_index` is the number of steps. The
  quick timer has no step rows. `restore` refuses an inconsistent snapshot
  with `IllegalArgumentException` and nothing else: a missing or
  out-of-range value (planned ≤ 0, a pause with nothing or more than planned
  left, a negative paused total, a FINISHED index other than the step
  count), a field of another phase, a recorded field on an unrecorded row,
  or a plan that is not ended steps, the current step, then pending steps
  (a READY needs a pending step). The controller then clears `active_brew`.
  The Room entity follows in #36.
- **Idle time on the wall clock.** The quick-timer cancel, #31's idle demote
  and the midnight rule measure from `last_interaction_wall_ms` on the wall
  clock. It survives a reboot without a rebase, the midnight rule needs the
  wall clock anyway, and no extra `active_brew` column is needed. A manual
  wall-clock jump forward can therefore cancel or demote early; that is
  accepted, because the waiting timeouts are housekeeping, not timing.
- **`demoted`** is a field of the session, false until #31's idle rules set
  it.

<a id="d-047"></a>
### D-047 — Timer engine design (T4 review of #28) · *Accepted* (Claude, 2026-10-22)

The T4 design review of the pure `BrewTimerEngine` (#28) settled these points
before #31, #32 and M1-09 build on the engine
([04 §3.2](04-brew-timer.md#32-events-and-transitions), §4, §5, §5.1):

- **Due-time set.** A public, pure `dueTimes(state)` holds every alarm time of
  a state. Today that is the pre-alert, until it has played, and the deadline.
  #31 only adds fields: overtime timeout, idle demote, midnight and the
  quick-timer cancel.
  - Every accepted transition emits `ScheduleAlarms(dueTimes(newState))`. The
    controller reconciles it per alarm type, and `CancelAlarms` is gone.
  - Time-based events carry no payload and are accepted only when they are
    due. The controller re-arms its in-process wake-up after every `reduce`,
    and recovery uses the same function.
- **Delivery.**
  - The alert is at-least-once. `Alerting.alertPlayed` is set by the
    `AlertCompleted` event from the AlertPlayer, and recovery replays
    `StartAlert` while it is false.
  - The pre-alert is at-most-once: `preAlertPlayed` is set in the transition
    that plays it (#31).
- **Outcome.** `TimerTransition` reports `Accepted`, `Ignored` or
  `Rejected(reason)`. `RejectionReason.ActiveTimerExists` refuses a second
  start (GEN-03, TMR-21); #32 adds the `editSteps` refusals. A non-accepted
  event always keeps the state and has no effects.
- **Session rows.** `CreateSession` is a data effect of an accepted start, so a
  refused start leaves no orphaned `brew_session` or `session_step` rows.
- **Effect order.** Data effects, then one `Persist` (one Room transaction),
  then one `ScheduleAlarms`, then the side effects. `StartForeground` means
  "ensure it runs" and is also emitted before `StartAlert`. `StopForeground`
  removes the timer notifications.
- **Session identity.** `SessionKind` is `Brew(sessionId)` or `Quick`, so a
  brew session without an id cannot exist.
- **Recorded data.**
  - Durations are rounded half up; the display and the journal may differ by
    1 s.
  - A skipped step records zeros and null start/end times.
  - A Running step ended at or after the deadline, before `deadlineReached`
    was processed, counts as poured, with overtime.
- **Finish.**
  - A finish that falls back to discard emits only `DiscardSession`.
  - A started step counts as completed.
  - `finishWouldDiscard(state)` lets the UI confirm in advance (TMR-12 AC1).
- **Small API changes.**
  - `StepStatus.RUNNING` is renamed to `STARTED`.
  - The state, step and `StartSession` constructors have no defaults.
  - The display rounding uses plain arithmetic instead of `Math.ceilDiv`.

These review points move to other tickets: service and notification rules for
long steps, idle demote and recovery (S5 → #31); recovery and the boot count in
the engine (S6 → #32); pre-alert and zone columns in `active_brew` and
*Wie Einstellung* in `editSteps` (S7 → #32/#36).

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
  rows; reduce motion keeps short fades (on the device they are instant:
  corrected by [D-055](#d-055)).
- **Alert motion (motion review):** entering ALERTING is always instant
  (`alertEnter`); the pulse starts at full matcha; the pulse and the pre-alert
  glow ignore developer animator scales (`UnscaledMotion`), while scale 0 still
  means reduce motion.
- **Timer components (#33):** the alert surface pulses between washi
  `#F3F0E9` and a lighter matcha `#6B9845` with ink `#1F271B` content (≥ 4.5:1
  on both ends; the type colour Matcha `#5F8C3E` would give only 3.9:1). The ring
  shows no liquor while alerting. The liquor fill is drawn at 32 % opacity, its
  level linear in height. Ring captions use `onSurface`, not `onSurfaceVariant`,
  because they sit on the liquor. The pre-alert glow is keyed on the tick's
  identity (`preAlertGlowTick`), not on a level: it plays once per tick, is never
  cut short and never replays when the ring comes back. Progress is a lambda read
  in the draw phase; a long step's update to 1 is drawn without a glide. The
  alert pulse restarts at full matcha when the app returns to the foreground.
  The active brew bar grows from its bottom edge, so the content above does not
  jump. Motion review findings for #33: M1 and S1–S5 applied, plus O3 and O5. The motion GIFs come from Roborazzi's
  experimental `recordRoboVideo` (record mode only), shrunk with ffmpeg.
- **PO check (#39, 2026-10-22):** the pre-alert glow, the alert pulse
  (matcha ↔ washi, 1.5 s, ink content) and the light-green
  `PrimaryActionCard` in the dark theme are approved unchanged.

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
sources (details and source references: `docs/plans/M1-android16-api-notes.md`):

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
  `basic` cache provider). *Since D-070 pinned to the commit SHAs of these
  versions.*

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
- **Removed (#54):** the probe screen, its classes and its tests were removed
  in M1-18 (#54); the real timer's emulator tests (#42, #46, #47) and
  `PermissionStatesDeviceTest` cover what the probe tests checked.

<a id="d-039"></a>
### D-039 — `release.yml` on `main` before the first dispatch · *Accepted* (PO, 2026-10-21)

GitHub only dispatches a `workflow_dispatch` workflow that exists on the
default branch. So `release.yml` (M1-03) reaches `main` through a small
housekeeping PR before the probe release `v0.0.1-probe` is dispatched from
the M1 PR branch; release candidates are dispatched the same way. This is an
exception to „one PR per milestone" limited to the release workflow; later
changes to `release.yml` within M1 travel with the milestone PR.
*Extended by D-070:* the exception covers the dispatch-only workflows
`release.yml` and `accuracy.yml`. Only the file's existence on `main` is
required; a dispatch runs the version at its workflow ref, so both are
dispatched with the PR branch as the ref. Agreed with
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
  06 §4/§5.7, 08 §2.5a. *Amended by D-094:* a fourth use, (d) the fetch of
  one product page from a shop link the user shares or pastes (CAT-17).
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
| 2026-10-30 | D-098: v0.3.0 (spec #124, grilling Q1–Q8, R2-Q1–Q3, S1–S4) — one brew session and one quick timer at once in two controller slots, brew alarm first with a held quick alert, one Live Update for the timer due next, umamit.db v4 (`active_brew` per slot); long steps from 60 min (hot 10–59 min get countdown and Vorwarnung); Vorwarnung and alarm on the alarm volume (`USAGE_ALARM`) with *Lautstärke* 10–100 %, DND „alarms only" rings, „Alarmlautstärke ist aus" check; setup ⋮ *Im Katalog ansehen*; daily low-stock reminder (INV-11, channel „Vorrat"); foldable catalog groups; stock on READY; two SCR-07 polish items. Amends D-093 point 1 and Q2, TMR-18, 04 §9.1. 02, 03, 04, 05, 06, 09 updated; plan `docs/plans/v0.3.0.md`. |
| 2026-10-30 | D-097: v0.2.2, the PO's feedback on v0.2.1 — grams and water on every READY; catalog notifications open the catalog with the refresh sheet; tea groups in the picker; *Knapp* from 15 g with a Start card and a setup marker (INV-10); the quick timer's silent pre-alert as a bug, plus a *Vorwarnung* selector and 8:00 chip; *Merken* for catalog teas (CAT-18, umamit.db v3). 02, 03, 04, 05, 09 updated. |
| 2026-10-29 | D-096: v0.2.1 build decisions (#83–#92, #97, #98, #100, #102): per-step pre-alert details and migration of a running session, *Alarm testen* waits for the Vorwarnton, sounds load at once on change, timer band and seal details, Kräutertee scope (Mate/Lapacho), one-time reset by parser version, herbal view rules, sold-out re-check incl. owned discontinued teas, redirect and page rules, the strict shop-link grammar and its messages, `CatalogJobs` ordering, vessel seed, stock input, archiving rules. 09 §4 updated. |
| 2026-10-28 | D-095: timer top as an ink-wash band (new asset A13, dark) with the tea photo as a small round seal on a washi disc (multiply blend, type leaf as fallback); the quick timer gets the band without a seal; A4 retires; in v0.2.1 as #102. 03 SCR-05, SCR-07, 09 §4 and `docs/design/assets.md` updated. |
| 2026-10-28 | D-094: sold-out teas and adding a tea from a shop link (#97, #98) — the shop's sitemap leaves sold-out products out, so known teas missing from it are re-checked once per refresh and kept while their page is still a green or herbal product (CAT-16), and a tea can be fetched from a shop link shared to the app or pasted via *Link einfügen* (CAT-17, share target `ACTION_SEND` `text/plain`); GEN-01 now has four network uses; paginated listings rejected (robots `*/?`). 02, 03, 06, 07, 08, 09 and CLAUDE.md rule 7 updated. |
| 2026-10-26 | D-093 amended with the PO's spec grilling Q15–Q21 (archived tea in the journal, no downgrades, „Auto · N s", vessel names, herbal filter sheet, empty Kräutertee view, undo snackbar). |
| 2026-10-26 | D-093 amended after the PO's grilling (Q1–Q14): a step's pre-alert goes into the recipe only via *Im Rezept speichern*; the pre-alert keeps `USAGE_NOTIFICATION_EVENT`, sounds mastered much louder (-18 LUFS) with ≈ 3 s fall-off; Kräutertees only (no fruit tea or rooibos), style `herbal` 2 g / 250 ml, Becher, 100 °C, 6:00, *Pur* not applied in the Kräutertee view, the one-time re-classification (≈ 16 min) accepted; no *Aus Sammlung entfernen* for the active session's tea; correction = new total; archived teas leave Start and the picker; v0.2.1 rcs need the accuracy run. PO's and Claude's parts marked. |
| 2026-10-26 | D-093: the PO's feedback on `v0.2.0-rc.1` (#79) — v0.2.1: legible timer top bar on bright photos, a pre-alert per infusion saved in the recipe (automatic N = min(B, 5 · ⌊d / 15⌋), umamit.db v2 with a tested migration), four new louder pre-alert sounds with a longer fall-off (*Vorwarnton*, SET-26), seven more seed vessels added once, stock correction from „… g übrig" (`ADJUSTMENT`), *Aus Sammlung entfernen* from the tea picker (archive with undo; re-adding un-archives), Kräutertees in the catalog (style `herbal`, hidden by default, chip *Kräutertee*); v0.3.0: quick timers alongside one brew session (planned); M3 → v0.4.0, M4 → v0.5.0. 02, 03, 04, 05, 07, 08, 09 and `docs/design/assets.md` updated. |
| 2026-10-26 | The PO approved the early design check #77 without changes and confirmed two choices: tea recipes stay out of the Ratgeber (D-089) and the `hojicha_powder` default (D-091). |
| 2026-10-26 | D-092: the first pre-alert of a process (accuracy run on 83f2c5c, step 1: 1178 ms) loaded its sound inside the cue; while a timer is active the alert player now keeps the tick and the tone loaded (`keepReady` at the end of every turn) and a cue only starts its sound, with fallbacks that never cost the sound; the player runs on its own thread; the log splits each cue's start (queue, settings, outputs, sound loaded or not) and the accuracy summary names the slowest start; amends D-059. 04 §4, §9.1, 06 §5.6, 09 §4a updated. |
| 2026-10-26 | D-091: fixes from full live run 1 — pages, photos and guides fetched while the previous answer is processed (same wire, expected first refresh ≈ 15–16 min), PNG and WebP photo sources with transparency on white, a 16 MB body limit for images (pages 5 MB), photos and guide images that failed for good remembered by URL (`catalog_image_failure`), sets in any case and by name or breadcrumb, `…SALE` duplicates left out, Mizucha brewing cold by default, the `hojicha_powder` style (whisked, no leaf recipe or cold brew); parser v6; 02 CAT-01, CAT-08, 05 §4.2b, §4.5, 06 §4, §5.2, §5.3, 07 NFR-PERF-11 and 08 §2.2, §2.4–§2.8, §3 updated. |
| 2026-10-26 | D-090: M2 milestone review fixes — an answer over 5 MB leaves only its URL out (no stop), CAT-15 per seed tea and part with retries (`seedTeaLinksDone`, amends D-086), the exact price and the add never crash on a bad page, the two flaky tests made clock-independent (split, shortened request gap, Robolectric), on-screen backs and row/link taps only while resumed, the secondary name back in the catalog rows, no refresh start during a cancelled one's cleanup, a 30 s keep-alive, seed photos ended before a setup refresh, the add sheet's save outcome kept and prices from 1.000 €, no-break spaces in counts, the first-load duration in Settings, three M2 checklist items; 02 CAT-15, 03 SCR-01, SCR-19, SCR-23, 05 §6, 06 §4, 08 §2.8 and 09 §4 updated. |
| 2026-10-25 | D-089: parser fixes from live sample 2 — Japan teas on tea-family pages kept by name or badge (Sorte from the name), Bi Lo Chun and Korean grade keywords, the China grid row as Gongfu, Mizudashi bags by name with the „kalt" row as cold brew and bag weights, video pointers never brewing prose, harvest year only from badge and title, no cold brew for powder, guides without comments, date list, empty video intros and tag lines, images de-duplicated by decoded URL, promotions/events/recipes not selected and ten more green-type keywords; parser v5; 05 §4.1 and 08 §2.3–§2.7 updated. |
| 2026-10-25 | D-088: brewing a new tea from the picker (#72) — *Aus dem Katalog* runs through Sammlung › Katalog in the Sammlung tab, a save there opens the setup of that tea (a repurchase too), back returns to the picker, an alert wins; the setup shows „Rezept prüfen" with its reason above the steps; the „Gespeichert" sheet's *Rezept passt* clears the flag of that tea only; 03 SCR-03, SCR-04, SCR-06 updated. |
| 2026-10-25 | D-087: *Ratgeber* (#76, CAT-09) — guides staged in `catalog_guide_staging` and swapped with the teas, `published_at` added, keyword-selected articles converted to the app's Markdown subset, images through the refresh's session (SET-22, ≤ 40 guides with ≤ 4 images each, ≤ 200 requests on a first refresh, named by URL hash, unchanged ones not fetched again), tea and guide links resolved in the app, list with A11 placeholder and article view; 03 SCR-19/SCR-20, 05 §4.3/§4.5, 06 §5.3 and 08 §2.4/§2.7 updated. |
| 2026-10-25 | D-086: later catalog refreshes (#70) — merged numbers known through their packages, unread known teas kept, discontinued teas kept hidden while owned and deleted otherwise, photos fetched only when new or their URL changed, `content_hash` per parser version for „geändert", the CAT-07 AC3 summary in the sheet and the notification („Rezepte prüfen" = flagged since this refresh), SCR-21's „Nicht mehr im Sortiment", CAT-15 seed linking once with the `seedTeasLinked` marker; 02 CAT-07, CAT-15, 03 SCR-21, SCR-22, 05 §4.1, §4.4, §4.5, §6, 06 §5.2, §5.3 and 08 §2.4 updated. |
| 2026-10-25 | D-085: adding and repurchasing a catalog tea (#71) — duplicate guard on `catalog_key` (seed teas included), the independent copy with recipes, flag, INITIAL stock and the cover re-encoded to JPEG, purchase date to `occurred_at`, the exact price of the chosen size once per sheet through one shop session, SCR-23 and SCR-21's primary action; 03 SCR-21/SCR-23 and 08 §2.3 updated. |
| 2026-10-25 | D-084 (corrects D-080): a killed process can leave notification 1 (or the alert) flagged as a foreground notification that no service owns and the app cannot cancel; starting in the foreground while not yet there, the timer service takes the other ID over with `startForeground` and switches, so the system removes it before the new notification appears; D-080's second cancel removed; `StaleForegroundNotificationTest`, a more diagnostic `ProcessDeathTest`, `timer-stress.yml` and `scripts/stress-instrumented.sh`. 04 §6.3 and §12 and 06 §6 updated. |
| 2026-10-25 | D-083: finding teas (#69) — „Neu" only after a second successful refresh, CAT-10's filters as chips plus a filter sheet, prefix search over `catalog_fts` with the `unicode61` tokenizer and a count of hits the filters hide, „im Besitz" from `tea.catalog_key`, filters not stored (`catalogSort` dropped); 03 SCR-19 and 05 §4.1, §6 updated. |
| 2026-10-25 | D-082: timer recipes from the catalog (#68) — 08 §3's green styles in `core:domain`, style mapping and recipe derivation with their details, recipes and „Rezept prüfen" stored by the refresh, the brewing sanity threshold on, the reason in the detail and the chip in the list; parser fixes from live sample 1 (brewing prose, property separators, scale labels, control characters); 03, 05 and 08 updated. |
| 2026-10-25 | D-081: catalog package sizes and prices (#67) — sizes from the configurator and the sitemap without fetching size pages, the page's own size at its price, larger sizes estimated, smaller ones priced when added, each with its own `variant_url`; split numbers merged by normalised name only when their own sizes differ; SCR-21 *Packungen*; parser v3; 03 SCR-21, 05 §4.2, 06 §2, §3 and 08 §2.4 updated. |
| 2026-10-25 | D-080: the timer service cancels the other notification again after `startForeground`, so a killed step's notification 1 never stays next to the alert. |
| 2026-10-25 | D-079: catalog refresh in the background (#65) — the job in the app's scope kept alive by the `dataSync` service, which follows it; own channel, IDs 10/11 and wake lock; progress at most every 2 s; `startForeground` first and `stopSelfResult`; refusal and time-limit handling; errors end the run, not the process; quiet result notification opening the app; entry points in the catalog menu, Settings (SET-23) and the setup's third page (ONB-03); 03, 04, 06 and 09 updated. |
| 2026-10-25 | D-078: catalog tea detail (#66) — full 08 §2.3 field map (parser v2: joined properties, region list, flavoured rule, brewing prose and grid JSON), sanitised description, photos after the sanity check with orphan cleanup and SET-22, *Im Shop öffnen* by grams then label, SCR-21 layout; 02 CAT-08, 03 SCR-21, 05 §4.1, 06 §5.3 and 08 §2.3, §2.8 updated. |
| 2026-10-25 | D-077: catalog refresh tracer bullet (#64) — staging tables with the live columns, skip list written only with a successful swap, sanity-check counting, parser rules for secondary name, *Sorte*, unit price and stock, product page choice, typed outcomes with details, app-scoped job until #65, catalog as a slot of Sammlung; 03, 05 and 08 updated. |
| 2026-10-25 | D-076: M2 implementation clarifications — first refresh ≈ 15–18 min (~1,560 products, PO), retries only for timeouts/connection errors, 08 §2.9 abort thresholds, FTS text and thumbnail columns, owned keys read before the swap, style table and cold-brew fallback, JPEG copies, page parsing notes; 02, 05, 06, 07, 08 updated. |
| 2026-10-25 | D-075: a failed first seed is retried at every app start once the setup is done, without the seed photo download; plus the #58 fixes of M2-11 (one-line nav labels at font 2.0, the picker sheet on `surface`, no-break space before units and „Std."/„Min.", separators from `strings.xml`). 05 §6, §7 and 08 §2.5a updated. |
| 2026-10-25 | D-074: shared shop session (`ShopSession`: robots.txt once per job with the RFC 9309 status rules, typed fetch results, sticky stop, serialised fetches, D-076 retries for the refresh only) and image targets for re-encoding (catalog photo ≤ 1200 px WebP 80, list thumbnail 256 px short side, covers unchanged) from #63; 02 CAT-08, 06 §4 and §5.2, 08 §2.8 updated. |
| 2026-10-25 | D-073: M2 grilled with the PO: CAT-12 add and repurchase in M2 with the picker flow, *Rezept passt*, guides last, small #58 items; catalog first loaded from the Katalog tab; refresh allowed during a brew (CAT-07 AC8); „Neu" badge; list grouped by Sorte, available only, „Pur" on; photos 1200 px plus thumbnails; live-shop tests by Claude; accuracy run only for rcs that touch timer code (09 §4a); early design check; A8/A11 now. |
| 2026-10-25 | D-072: one-tap reorder: „Beim Tee-Kontor nachbestellen" visible on SCR-09 (new INV-09; filled when the stock is low), opening the shop page of the size bought last; CAT-11 / SCR-21 follow the same rule; checks added to 09 §4 M2/M3. Requested by the PO. |
| 2026-10-23 | D-071: `BrewTimerService` posts a same-ID update made within 1 s of `startForeground` once more after 0.5 s (only-alert-once, only while still the foreground ID, newest wins), because AOSP posts the start's notification asynchronously and it can overwrite the update; service path logging, `dumpsys activity services` in the device test dump, wider CI logcat filter. 04 §6.3 and §8.3 updated. |
| 2026-10-23 | D-070: release gate and workflow hardening (#54 milestone review): `release.yml` builds only a commit with a successful `ci.yml` run and repeats the schema checks; signing job (read-only token, only GitHub's own actions, no setup-gradle) and publish job (`contents: write`, no actions, re-verifies the APK) split; all actions in `release.yml`, `ci.yml`, `accuracy.yml` pinned to commit SHAs; the schema freeze compares version and identity hash; `SchemaVersionsTest` opens every committed schema version with the production builder and `UMAMI_MIGRATIONS`; D-039 covers `accuracy.yml`; 09 §4 gains Doze, quick timer, *Beenden & neu starten* and refused-start items. 05 §9, 06 §6, 09 §1 and §4, `docs/release.md` updated. |
| 2026-10-23 | D-069: M1 review fixes (#54): the active brew bar snaps in when it arrives alerting (synchronous first value, no enter motion); system-bar icons per timer surface with the root as their one owner; honest *Sammlung*/*Statistik* placeholders with renders; the tea photo (or placeholder) on every brew timer, D-058 amended; double-tap guard on *Nochmal brühen*. 03 SCR-05, SCR-08, SCR-18, §1.1, §3.8 and 09 §4 updated. |
| 2026-10-23 | D-068: M1 review fixes in the timer area (#54): deadline guard (alarm 107 at deadline + 90 s while a step runs), accuracy gate and *Diagnose* measured up to the start of the sound (D-067 corrected), accuracy run reset with the watchdog, alert settings read again after a failed read, settings flow total on an unreadable file, timer route waits for recovery, cold and ice READY text and chip, full-screen check reads the app op, wording of `StopForeground` and D-064. 04 §4, §6.1, §6.2, §7.2, §8.2, §8.3, §11, §12, 06 §5.6, 09 §4, §4a updated. |
| 2026-10-23 | D-067: accuracy workflow (#52): `accuracy.yml` (`workflow_dispatch`, on `main` per D-039) runs `AccuracyRunTest` (skipped in the regular emulator job) with `am instrument`; 50 × 60 s steps with the screen off in sessions of ten, driven through the notification actions; delays from the diagnostics log's accepted pre-alert and deadline events (parser round-trip tested against the format); nearest-rank p95 ≤ 250 ms and max ≤ 1 s for both, every step required; injected-delay gate check in the test APK; summary in the job summary, CSV and log lines as an artifact. 04 §12, 09 §1 and §4a updated. |
| 2026-10-23 | D-066: first-start setup, timer settings, reliability card and banner (#50): `TimerReliability` in `core:timer` over the probe-proven permission states, *Beheben* to the check's own page, the setup as the root start destination until completed and above the shell when shown again, `FirstStart` seeding then downloading the seed photos in the background, the photo line below *Weiter*, the M1 settings rows with the German tone names and *Anhören* through `AlertTester`, no settings dialog over an alert, *Über* with *Diagnose*. 03 SCR-01, SCR-25, SCR-27 updated. |
| 2026-10-23 | D-065: quick timer SCR-07 (#49): chips 0:30–5:00 and „Dauer wählen" (5 s – 24 h, PRF-04 steps), *Start* as one controller turn, *Beenden & neu starten* into a quick timer as one turn, *Fertig* and *Abbrechen* (no session, step or stock record), every entry opens the alerting timer during an alert; `iceBrew` in the timer state for the cold and ice READY hint, carried through recovery. 03 SCR-07, SCR-05 updated. |
| 2026-10-22 | D-063: recovery after process death, reboot, app update and device restore (#46): recovery triggers through one non-exported receiver, reboot notice, device restore finishes or discards `IN_PROGRESS` rows, one-turn *Beenden & neu starten*, recovery retries back off and only stop (never clear a valid session), refused starts resent from the current state, alert watchdog alarm 107 while ALERTING. 04 §7.2, §11, §12 updated. |
| 2026-10-22 | D-064: notifications, the promoted Live Update and the full-screen alert (#47): `AndroidTimerNotifier` through `BrewTimerServicePort`, the one ordered place for notifications 1, 2 and the quiet ID 4 (long step, demoted session); content and actions per phase, ProgressStyle segments with the pre-alert point, 15 s refresh, dismissal kept until the next state; actions to the controller with source NOTIFICATION; the full-screen and content intents open the app's timer route over the lock screen (`TimerAlertActivity` removed); „Sitzung gespeichert"; the app runs the real controller (`TimerHost`). 04 §8.2, §8.3, §13 updated. |
| 2026-10-22 | D-062: Start, tea picker and setup (#48): `BrewStarter` seam following the `BrewTimer` binding, *Nochmal brühen* from the last session, *Zuletzt gebrüht* row format, picker as Material sheet with GEN-10 search and an immediate pick, scaling from the locked ratio (0,1 g / 5 ml steppers), *Im Rezept speichern* only when changed with an in-place confirmation, cold/ice kettle hint, *Brühen anpassen* through the setup; every route above the shell or timer gives way to the timer on a new alert, overlays and the TMR-21 question never over an alert. 03 SCR-02, SCR-03, SCR-04 updated. |
| 2026-10-22 | D-061: minimal journal and the „Gespeichert" sheet (#51): `JournalRepository` read side with cover photos, day groups by the start's local date in the session's zone (E13), meta line with planned first infusion and poured count („2 von 3 Aufgüssen"), pinned in-progress session opening the timer, `SavedSheetRequests` as the saved sheet port, sheet without scrim over the tab content, 10 s / swipe / hold / TalkBack action, never composed during an alert, `sheet` spring motion. 03 SCR-06, SCR-15 updated. |
| 2026-10-22 | D-060: diagnostics log and *Diagnose* (#43): two-file rolling log ≤ 200 KB in the no-backup storage, written in order on the IO dispatcher and never blocking or throwing into the timer; technical English entries with scheduled vs actual times on both clocks and the delta; service starts and stops, ringer mode at each alert and test, permission states at each process start; *Diagnose* newest first with *Kopieren* (header + entries). 06 §5.6, 03 SCR-27 updated. |
| 2026-10-22 | D-059: production alert player (#44): `AlertPlayback` with a main-thread command queue and settings per alert (300 ms wait, defaults on failure), `ControllerAlertPort` reporting `AlertCompleted` once after the full alert length, `AlertTester` for *Alarm testen* (tick, alert 1.5 s later), a tick never cuts an alert; four synthesised tones Glocke, Holzblock, Glockenspiel, Sanft at -16 LUFS with Klangschale and tick byte-identical. 04 §9.1, §9.2 updated. |
| 2026-10-22 | D-058: timer screen SCR-05 (#45), amended by its motion review (no route transitions or menu exit around the alert, settings popped on alert, paused ring contrast, stable pill, fresh first frame, glow per step, saveable overlays, timer-only lock screen, dim never brighter than the phone): `BrewTimer` seam with `NoBrewTimer` until #42, `TimerUiState.Active.preAlert`, step label and dots, READY/ALERTING layout, finish-would-discard question, every overlay removed on ALERTING, no recipe save on SCR-05, dimming rule, photo slot, TalkBack live region, lock-screen flags, top-level timer route, bar fed by the controller. 04 §4 updated. |
| 2026-10-22 | D-057: exact alarms, BrewTimerService and recovery in the first turn (#42): alarm adapter per kind with request codes 101–106 and the inexact fallback logged; recovery once per process in the first controller turn (moved from #46, amends D-056); receiver and full-screen alert via the app's `TimerHost`; failed recovery reads retried and the alarm set again; service port with the full-screen flag, channels created, `startService` while in the foreground, deferred stop with `stopSelfResult`, handover wake lock, wake lock up to the longest alert, refusals logged and resent by the next alarm; minimal notification content and `TimerAlertActivity` until #47; process-death test with its runner in its own process. 04 §4, §6.2, §6.3, §7.2, §12, §13 updated. |
| 2026-10-22 | D-056: BrewSessionController core (#41): suspend inputs and seven ports, alarm-kind table, in-process wake-up port, non-cancellable lock turns, persist retry and guarded side effects, one alert per deadline with stale end reports ignored, `TimerUiState`, pre-alert frozen at the start, Hilt bindings without stand-ins. 04 §4 updated. |
| 2026-10-22 | D-055: spec #27 slice review, UI: alert-safe children on the alert surface (ink ring and pill), overtime in ink with a warm-accent mark, no Schritte-Sheet entries in ALERTING, alerting active brew bar, GEN-08 guard in the Schritte-Sheet, scroll-to-top one-shot keyed by entry, bar step text contrast, reduce motion instant on the device, allocation-free ring drawing, mini ring long-step mode, rail bottom inset; 02 GEN-06, TMR-06, TMR-10, TMR-11 and 03 §2.3, §3.1, §3.6, §3.8, SCR-05, SCR-31 updated. |
| 2026-10-22 | D-054: spec #27 slice review, engine and data: edits keep a due pre-alert; stale readings clamped and controller rules (clocks inside the lock, failed persist never blocks an alert); `restore` refuses unreachable states; notification per state while alerting; `CompleteSession.autoFinished`; alarm kind per due field and `dueEvents`; *Beenden & neu starten* sequence; seeded service oracle; seed photo download waits for the seed teas; schema v1 frozen from rc.1 with a CI guard; S3 tables declined; total data effects; restore photo sweep; exact cost; one HTTP client (Detekt). 02 TMR-14, TMR-23; 04 §3, §4, §5, §6.2, §7.1, §11, §12; 05 §3.6, §8, §9; 06 §3; 08 §2.5a updated. |
| 2026-10-22 | D-053: timer time rules from #31: pre-alert source order, skip rules, E6/E7 and the 1-s late rule; overtime timeout event and live auto-ack; idle time from the later of last interaction and waiting start; idle demote with `demoted`; the foreground-service contract (S5); midnight timeout in the session zone with `NotifySessionSaved`; recovery order; long steps incl. cold/ice brew; six-field due set. 04 §3.2, §4, §5, §6.1, §7.2, §8.2, §10, §12 and 05 §3.12 updated. |
| 2026-10-22 | D-052: session lifecycle per engine effect, authoritative active brew store, `active_brew` columns of D-048 in schema v1, completion details (end time, cost, BREW event, full INV-06), include-only device backup rules with `fullBackupContent`, restore detection in Room's callbacks, CI schema-staleness check (#36); 05 §3, §6, §8, §9 updated with the #30 decisions. |
| 2026-10-22 | D-051: Schritte-Sheet details (#38): Material 3 modal sheet with its own motion instead of the `sheet` token, closed at once on ALERTING, closing applies nothing, stepper and temperature rules, recipe save mapping; 03 SCR-31 updated. |
| 2026-10-22 | D-050: shop client (allow-list, direct single connection, one request per call, rate limit on the wire, stop signals incl. Cloudflare block pages, hardened robots.txt) and the run-once seed photo download (marker before the first request, stop rules, re-encoded covers) from #37 and its T4 review; 05 §6 and 08 §2.5a updated. |
| 2026-10-22 | D-049: navigation shell details (re-select order, tab fade-through, insets of the active brew bar); 03 §3.6 `tabSwitch` token. |
| 2026-10-22 | D-048: `addStep` default, `editSteps` with `PreAlertChoice` and its rejections, quick timer, boot-aware `recover` in the engine with physical bounds for the reboot rebase, the consume rule for due events, idempotent `StartAlert`, idle time on the wall clock, and the lossless, validated `active_brew` snapshot (#32 and its T3 review); 04 §3.2, §4, §5, §7.1, §7.2, §10 and 05 §3.12 updated. |
| 2026-10-22 | D-047: timer engine design from the T4 review of #28 (due-time set, alert at-least-once with `AlertCompleted`, transition outcome, `CreateSession`, `SessionKind`, ending after the deadline counts as poured, `finishWouldDiscard`); 04 §3.2, §4, §5, §5.1 updated. |
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
