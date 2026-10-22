# 10 · Open Questions & Decision Log

> Status: **Living document** · Last updated: 2026-10-22

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

<a id="d-059"></a>
### D-059 — Production alert player, four more tones, *Alarm testen* (M1-10, #44) · *Accepted* (Claude, 2026-10-22)

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
  and the caption keep full contrast (≥ 4.5:1, tested).
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
  background (mockup v2). The screen takes it as a parameter: the session's
  photo reaches the timer with M1-13, so the route passes none until then.
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
  must stay byte-identical (the probe tags contain none).
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
| 2026-10-22 | D-059: production alert player (#44): `AlertPlayback` with a main-thread command queue and settings per alert (300 ms wait, defaults on failure), `ControllerAlertPort` reporting `AlertCompleted` once after the full alert length, `AlertTester` for *Alarm testen* (tick, alert 1.5 s later), a tick never cuts an alert; four synthesised tones Glocke, Holzblock, Glockenspiel, Sanft at -16 LUFS with Klangschale and tick byte-identical. 04 §9.1, §9.2 updated. |
| 2026-10-22 | D-058: timer screen SCR-05 (#45), amended by its motion review (no route transitions or menu exit around the alert, settings popped on alert, paused ring contrast, stable pill, fresh first frame, glow per step, saveable overlays, timer-only lock screen, dim never brighter than the phone): `BrewTimer` seam with `NoBrewTimer` until #42, `TimerUiState.Active.preAlert`, step label and dots, READY/ALERTING layout, finish-would-discard question, every overlay removed on ALERTING, no recipe save on SCR-05, dimming rule, photo slot, TalkBack live region, lock-screen flags, top-level timer route, bar fed by the controller. 04 §4 updated. |
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
