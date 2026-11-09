# UmamiT history

> Finished releases, past plans and the session log, moved out of
> `PROJECT_STATE.md` on 2026-11-02 (D-101) so that every session start reads
> only the current state. Newest first. Read this only when you need an
> earlier decision's context; the decisions themselves are in
> `docs/specs/10-open-questions-and-decisions.md`.
>
> When a release is done, move its section from `PROJECT_STATE.md` to the
> top of "Releases and plans" and add a row to the session log.

## Releases and plans

### v0.4.1: released (2026-11-08)

- PR #191 merged into `main` with a merge commit (`2b5735fc`), CI green on it (run 42517586167 on the branch tip, `main`'s run on the merge commit).
- **Accuracy on `2b5735fc` (09 §4a):** standard PASS (pre-alert p95 41 / max 244 ms, alert 21 / 30 ms; run 42518987907, attempt 1 lost its hosted runner, the rerun passed); alongside PASS (brew pre-alert 19 / 150, alert 38 / 43 ms; quick timer pre-alert 20 / 159, alert 28 / 329 ms; run 42518990703).
- **Release:** run 42530943942, [`v0.4.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.4.1) from `main` `2b5735fc` (versionCode 40199, APK SHA-256 `26eb607e…2a99`), certificate `a500e2c6…2f67` = `signing/release-cert-sha256.txt`, marked latest.
- #171, #175–#189 closed. Working branch restarted from `main`.

### v0.4.1: approved, final (2026-11-08)

- **PO (in the session, 2026-11-08):** „all test passed, please release": every rc.1 checklist item passed; that is the approval of the checklist and the design review (rule 9).
- **#189:** `umamit.version=0.4.1` (versionCode 40199) on the branch → CI green → merge PR #191 into `main` with a merge commit → both accuracy variants on the merge commit (09 §4a, required for the final) → `v0.4.1` from `main` → close #171 and its tickets, restart the branch from `main`.

### v0.4.1: rc.1 out (2026-11-08)

- **Version:** `0.4.1-rc.1` (versionCode 40101) in `f34c237e`. CI green on it (run 42471069183; Checks failed once on the external `kotlin-compiler-embeddable` 504 from GitHub releases and passed on the rerun of the failed job).
- **Accuracy on `f34c237e` (D-073, #180):** standard PASS (pre-alert p95 50 / max 153 ms, alert 33 / 141 ms; run 42471222819); alongside PASS (brew pre-alert 26 / 123, alert 45 / 248 ms; quick timer pre-alert 21 / 502, alert 36 / 135 ms; run 42471225121). The first attempts failed before building: `services.gradle.org` and GitHub release downloads answered 502/504/timeouts for about 45 min; reruns only.
- **Release:** run 42478612894, pre-release `v0.4.1-rc.1` (APK SHA-256 `a364a5cc…3673`), certificate `a500e2c6…2f67` = `signing/release-cert-sha256.txt`.
- **Checklist:** 09 §4 v0.4.1 block, from #171, D-104 and D-107; posted on #188 and linked from PR #191.
- **Renders:** `docs/design/review/v0.4.1/` (110 goldens v0.4.1 added or changed, 600 px, placeholder photos), README with the points to judge.
- **Tickets:** the acceptance checkboxes of #175–#187 ticked.
- **Next:** the PO's results on #188. Findings → fix round (timer code never below T3) → `-rc.2`, with both accuracy variants if timer code changed. After the PO's explicit approval of checklist and design review: #189 (merge PR #191 with a merge commit, both accuracy variants on `main`'s merge commit, `v0.4.1` from `main`, restart the branch from `main`).

### v0.4.1: review and fix round (2026-11-08)

- **Reviews:** T3 (standards and spec) and T4 (timer path: #180's completion, #184's reset during a brew) on `aa1c110d..HEAD`: no must-fix finding; T4 verdict „safe for an rc". The motion review's S1–S5, N2 and N3 fixed; N1 left (harmless).
- **PO answers (D-107):** a purchase restores an archived tea; the first package stays a deletable *Kauf*; the reset stays offered for discontinued, archived and brewing teas; „Vorrat" stays; the opening date only in FRISCHE.
- **Fixes, three implementers (A T3 data, B1 and B2 T2 screens), each verified by the orchestrator:** guarded „Gespeichert" sheet; forget on a completion without a date; tests for a rolled-back completion and a brew completed after a reset; photo cap 40 MB, one buffer, OOM as unreadable; reset rolls back on a refused write and keeps the tea's description when the catalog has none; vessel pick with a persistable grant and a toast when unreadable; price 0 refused; detail stock writes show a toast on failure; one `DateField`; `WallClock.today()`; photo crossfade over a tint; *+ Foto* animates to the new page; purchase sheet slides out; editor Save always composed.
- **Goldens re-recorded:** 5 tea detail `dates` (DETAILS without the opening date), 8 teaware photo goldens (1 px corner anti-aliasing from the tint).
- **Not done:** ticking the tickets' checkboxes on #175–#187 (the GitHub write was refused in this session); the rc session or the final can do it.
- **#180 and accuracy:** completion's SQL unchanged, the code around it changed → the rc runs both accuracy variants (D-073).
- **Verification on `6d6af31a`:** local `gate.sh` PASS (format, Detekt, manifest permissions, all unit tests, Lint, screenshot tests, androidTest build). CI: build and both emulator jobs green; **Checks failed three times on an external error**, not code: spotless's `kotlin-compiler-embeddable-2.2.21.jar` is redirected from Maven Central to GitHub releases, which answered 504 (run 42464239574, attempts 1–3). The rc session's version bump runs CI again; if Checks still fails the same way, rerun later (it is outside the repo).
- **Environment notes:** the Robolectric SDK 35 jar had to be fetched by hand again (`v0.4.0-resume.md` §3); a Gradle run that failed while the network was blocked cached "plugin not found", cleared once with `--refresh-dependencies`.

### v0.4.1: build phase (2026-11-07/08)

- **Merged into PR #191**, each verified by the orchestrator (module tests, screenshot tests, lint, Detekt, the diff): #180 (-06, T4) opening-date *Rückgängig* · #175 (-01) `GermanFormat` in `core:ui` · #177 (-03) Settings closes on a tab switch · #183 (-10) own tea photos · #176 (-02) `EditorScaffold` · #178 (-04) freshness labels and `FreshnessChip` · #187 (-12) vessel photo · #179 (-05) dates in the editor · #181 (-07) *Nachkauf erfassen* · #186 (-11) *Als Titelbild* / *Löschen* · #185 (-08) stock history · #184 (-13) *Aus Katalog zurücksetzen* · #182 (-09) cost.
- **D-106** (own photos: accepted input, EXIF reader, pager order). The motion review ran: `docs/plans/v0.4.1-motion-review.md`.
- **#180 and accuracy:** completion's SQL is unchanged, but its code changed (the opened-date change is recorded after the inner block) → the rc runs both accuracy variants (D-073).
- **Full gate on the build tip `4063c488`:** local `gate.sh` PASS, and CI green on it (all four jobs, both emulators).
- Known points for the reviewers and five PO questions: `docs/plans/v0.4.1-resume.md` §3–§4.

### v0.4.0: released (2026-11-07)

- The PO passed every rc.2 item and approved the checklist and design review (#160). One change went straight into the final: the *Grüntee* chip before *Kräutertee* (#168, D-103).
- #162 merged into `main` with a merge commit (`a2ff115`), CI green on it, `v0.4.0` released from `main`. The PO waived the accuracy runs on `main` for this release (D-103).
- CI notes: two timing-dependent tests failed once each and passed on the rerun: `NotificationActionsTest` (#168's checks job, no timer change) and `UnreadableSettingsStartTest` (the final release job's unit tests, a lifecycle error). Watch for them in later runs; a repeat is worth a look.
- #145 and its tickets #147–#161 closed.

### v0.4.0: rc.2 out (2026-11-07)

- **rc.1 results (PO, 2026-11-07):** every checklist item passed; five points, recorded as **D-102**: (1) the journal row showed the base temperature, not the first infusion's (bug); (2) journal entries must be deletable; (3) the FAB *Tee hinzufügen* covers the rows → into Sammlung's ⋮; (4) the keyboard: an empty band above it on *Meine Tees*, and the editor's fields vanished behind it (every field); (5) own photos for *Ohne Katalog* teas → v0.4.1 (already planned, D-100).
- **Fixes (this session, on branch `claude/quirky-ptolemy-ld5oet` → PR into the milestone branch):** `JournalList` takes the first infusion's `tempC ?: baseTempC`; `JournalRepository.deleteSession` → `SessionDao.deleteFinished` (never `IN_PROGRESS`), `SessionPageViewModel.delete` + `SessionPageEvent.Deleted`, ⋮ and „Sitzung löschen?" on `SessionPageScreen`, the app pops the page; `CollectionScreen` gets `onAddFromCatalog` / `onAddWithoutCatalog` menu items on *Meine Tees*, `MyTeasContent` lost the FAB and `snackbarShowing`; `MainActivity` `adjustResize`, `Modifier.consumeCoveredBelow()` in `UmamitApp`, no `imePadding()` in the tea, recipe and vessel editors and *Sorte wählen*. Specs 02, 03, 09, 10 and the plan updated. Collection goldens re-recorded (no FAB) and the review renders refreshed.
- **Released (2026-11-07):** fix-round PR #166 merged into the milestone branch (`27ad3de`), CI green (all four jobs), `v0.4.0-rc.2` published from `27ad3de` (versionCode 40002, certificate verified); the rc.2 checklist is on #160.
- **Tests:** `KeyboardInsetsTest` dispatches an IME inset to the Compose view and checks the shell's content edge and the editor's fields; the keyboard on the phone itself is the PO's rc.2 check.

### v0.4.0: rc.1 out (2026-11-05)

- **rc phase (2026-11-05):** `5240a57` version bump; accuracy on `5240a57`: standard PASS (pre-alert p95 40 ms / max 53 ms, alert 15 / 19 ms), alongside PASS (brew 29 / 33 and 37 / 58 ms, quick timer 10 / 275 and 12 / 56 ms); CI green on `5240a57` and `cc20e1b` (renders, docs only). The first two release dispatches were refused („No successful CI run"): `release.yml` accepted only `push` CI runs, which a PR branch no longer has since step 0 (D-101). Fixed in `fe1d925` (also this repository's `pull_request` runs, never a fork's; `docs/release.md`). rc.1 published from `5240a57`, certificate checked; checklist on #160 and linked from PR #162.

- **Fix round (2026-11-05, Opus `medium` orchestrator):** step 0 (`ci.yml` runs `push` only on `main`; a push to the PR runs 4 checks, not 8). Then:
  - A, F1 (T3): one `AlertGate` in `core:ui` for every menu, dialog, sheet and picker; the four copies deleted.
  - C, the brew path (T3): F3, F4, F10, F11 and F12 as decided, plus the `ProcessDeathTest` gap log. Session completion untouched.
  - E, the motion review (T2, read-only), whose findings went to B.
  - B, the screens (T2): F2, F5–F9, F13, F15, the nits, and E's drag-reorder and list motion.
  - D, F14 (T2): `GermanNumber` in `core:domain`.

  The full local gate passed on A+C and on B+D. The orchestrator did not take E's reserved *Im Rezept speichern* slot: it left an empty band on every unchanged setup, against SCR-04 (`380d992`). All of this is recorded in D-100.
- **Goldens:** B re-recorded 32 in `feature:catalog` (F9's back arrow). The brew setup goldens are unchanged after the slot revert.

**Next** (`v0.4.0-resume.md` §5 steps 7–8):
1. Wait for the PO's results on #160. rc findings are fixed on the branch (timer code never below T3) → `0.4.0-rc.2`, with both accuracy variants again if timer code changed (09 §4a).
2. After the PO's explicit approval of the checklist and the design review: #161 (merge PR #162 with a merge commit, both accuracy variants on `main`'s merge commit, `v0.4.0` from `main`, restart the branch from `main`).
3. The workflow-dispatch runs (accuracy, release) don't wake a session; check them yourself. A release needs a green CI run on the exact commit it builds, so don't push while that CI runs (a newer push cancels it).

### v0.4.0 specified (2026-10-31)

- The PO grilled M3 (R1–R5, the test seams, and a glossary grilling G1–G12), recorded as **D-100**: M3 splits into
  **v0.4.0** (brewing: *Meine Tees* with in-memory search, *Kategorie*/*Sorte* filters and the remembered sort; tea detail;
  tea editor and *Sorte wählen*; recipe editor with drag, three step kinds, per-step Vorwarnung and several recipes; teaware
  in *Einstellungen* and *Neues Gefäß …*; *Archivieren* everywhere, *Löschen*; BRW-08; the read-only session page with
  *So nochmal brühen*; TEA-17 fix; SET-15 row; the accuracy test's waits) and **v0.4.1** (stock history, freshness, cost per
  cup, own photos, *Aus Katalog zurücksetzen*), whose spec follows v0.4.0. No DB migration in v0.4.0.
- After the docs review the PO settled Q-19 (never-started steps are kept at completion from v0.4.0, ticket -15, T4) and
  Q-20 (values of the generic style rows), and per-step Vorwarnung in the recipe editor.
- Spec **#145**, glossary 11 (c52b0a5), specs 02, 03, 04, 05, 08, 09, 10, 12 and the plan `docs/plans/v0.4.0.md` on docs PR #146;
  tickets **#147–#161** (v0.4.0-01 … -15, sub-issues of #145). **Next:** the PO approves the tickets; nothing is built
  before the PO starts `/implement-spec #145`.
- **Decision numbers:** up to D-100 used; the next free is **D-101**.

### v0.3.0 released (2026-10-31)

- [`v0.3.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.3.0) from `main` 688a23a (PR #143, merge commit), versionCode 30099, certificate verified. #124, #125–#139, #141, #122 and #123 closed.
- rc.1 (ef62744): every checklist item passed, design review approved; two findings. The *Lautstärke* slider jumped on release (fixed, c5b5121). One UI reports the ringer as silent under its DND, so the cues stayed visual: **D-099**, ring when DND lets alarms through, with the switch *Bei „Nicht stören" klingeln* (SET-32, default on). rc.2 (70ef390) re-checked by the PO: passed.
- Accuracy on `main`: both variants PASS on their one re-run; the first attempts failed in the test harness (a 5 s screen-off wait at step 11, a 10 s session-start wait), not in timing.
- **Follow-ups for a later release:** make the waits in `AccuracyRunTest` robust (resend `KEYCODE_SLEEP` until the screen stays off, as `ProcessDeathTest` does since 42bad49; a longer start wait). The design-review points the PO accepted (two *Pause* buttons, the shortened tea name in the brew row at font 2.0) can be revisited with M3.
- **Next:** M3 (collection) → v0.4.0 (specified, see above).

### v0.3.0 specified (2026-10-30)

- The PO grilled v0.3.0 (Q1–Q8, R2-Q1–R2-Q3, scenarios S1–S4), recorded as **D-098**: one brew session and one Schnelltimer
  at once (two controller slots), the brew alarm first and a held Schnelltimer alarm that rings after *Abgegossen*, a row for
  the other timer and both in the bar, one Live Update for the timer due next, umamit.db v4 (`active_brew` per slot); long
  steps only for cold/ice brew and ≥ 60 min; Vorwarnung and alarm on the alarm volume (`USAGE_ALARM`) with *Lautstärke*
  10–100 % and the „Alarmlautstärke ist aus" check; setup ⋮ *Im Katalog ansehen*; a daily low-stock reminder (INV-11,
  channel „Vorrat", WorkManager); foldable catalog groups; stock on READY; two SCR-07 polish items.
- Spec #124, tickets #125–#139 and #141, plan `docs/plans/v0.3.0.md`. Specs 02, 03, 04, 05, 06, 09 updated (11 before).
- **Built** 2026-10-30/31 on PR #143: all tickets #125–#137 and #141 merged, a T4 fix for notification updates Android drops under its rate limit (found by the new emulator test), a two-axis code review (Standards, Spec T4/T3) with every finding fixed or recorded. `v0.3.0-rc.1` released 2026-10-31 (versionCode 30001, ef62744).
- **Working notes for the session:** the container restarted twice from memory with two Gradle builds at once; since then every Gradle command goes through a machine-wide `flock` (one build at a time) and Kotlin compiles in-process (`~/.gradle/gradle.properties`).
- **Decision numbers:** up to D-099 used; the next free is **D-100**.

### v0.2.2 specified (2026-10-30)

- The PO's seven points on v0.2.1, grilled (Q1–Q9) and recorded as **D-097**: grams and water on READY; catalog notifications open the
  refresh sheet; tea groups in the picker; *Knapp* from 15 g with a Start card and setup marker; the Schnelltimer's silent Vorwarnung
  (bug: no mark and no sound on 2:00+, though the engine schedules it) plus its own selector; an 8:00 chip; *Merken* for catalog
  teas (umamit.db v3).
- Spec #108, tickets #109–#119 (approved), plan `docs/plans/v0.2.2.md`. Specs 02, 03, 04, 05, 09, 11 updated.
- **Decision numbers:** up to D-097 used; the next free is **D-098**.

### v0.2.1 done (2026-10-29)

- Built on PR #106 with `/implement-spec #81` (go 2026-10-29): 14 tickets (#83–#92, #97, #98, #100, #102), at most two
  implementers in worktrees, each verified on the combined tree and by CI before the next.
- Three parallel code reviews (T4 timer/migration, T4 catalog/network, T3 UI): no blockers; two fix rounds (alert sounds
  load on a separate loader thread, short-screen timer layout, migration keeps v1 leads, look-alike hosts refused, …).
- Build decisions recorded as **D-096**; design renders in `docs/design/review/v0.2.1/`.
- `v0.2.1-rc.1` (f88e371): accuracy PASS; the PO passed the whole checklist, kept Zimbel as *Vorwarnton* and approved.
  Merged (9a73b19), accuracy PASS on `main`, `v0.2.1` released; #81 and all its tickets closed.
- Known follow-ups (not planned): Robolectric fetches its runtime jar at test time and has failed CI twice on a network
  hiccup (an offline/cached setup would remove that); `ProcessDeathTest` flaked once on API 36 (passed on rerun).
- Decision numbers then: up to D-096.

### v0.2.1 planned (2026-10-26)

- The PO tested `v0.2.0-rc.1` and approved M2; the feedback (#79, 9 points) goes into a
  v0.2.1 patch, except quick timers alongside a brew (v0.3.0). M3 becomes v0.4.0, M4 v0.5.0.
- Grilled in four rounds (Q1–Q21) and recorded as **D-093** (#80, merged): legible timer top
  bar; Vorwarnung per step (`min(B, 5·⌊d/15⌋)`, session-only unless *Im Rezept speichern*,
  umamit.db v2); four louder Vorwarnung sounds (SET-26); seven more seed vessels; stock
  correction; archiving a tea from the picker; Kräutertees in the catalog.
- Spec #81 written with `/to-spec` (lean tests, 6 seams); the PO confirmed it is tight.
- Tickets via `/to-tickets` (started by the PO), approved by the PO as proposed and
  published as sub-issues of #81:
  #83 automatic Vorwarnung per infusion, T4 · #90 per-step Vorwarnung stored, umamit.db v2,
  T4 (←#83) · #92 per-step selector in the Schritte-Sheet, T4 (←#90) · #84 new Vorwarnung
  sounds and SET-26 *Vorwarnton*, T4 · #85 legible timer top bar, T2 · #86 more vessels, T2 ·
  #87 stock correction, T3 · #88 removing (archiving) a tea, T3 · #89 Kräutertees in the
  catalog, T3 · #91 Kräutertee view: filter sheet and hint, T2 (←#89) · #93 rc and checklist,
  PO picks the Vorwarnton (←all) · #94 merge and `v0.2.1` (←#93).
- Frontier at the go: #83, #84, #85, #86, #87, #88, #89; the two T4 timer tickets (#83, #84)
  first, at most 2 implementers at a time.
- **Started 2026-10-29** on the PO's go (`/implement-spec`). Frontier also has #100; #102 follows #85.
- **Handover:** `docs/plans/v0.2.1.md` has the task graph, process, local gate, the guards
  that bite (frozen schemas, accuracy run, 04 §12) and the release steps (rc versionCode 20101).
- **D-094 (2026-10-28):** the PO found Bio Sencha Kirishima Yabukita (TG0170) missing from the catalog. The shop's sitemap leaves sold-out products out, and paginated listings are off-limits (robots `*/?`). The PO chose:
  - #97 (T4, ←#89): a refresh re-checks known teas missing from the sitemap (CAT-16);
  - #98 (T4, ←#97): a tea from a shared or pasted shop link (CAT-17), the fourth network use GEN-01 (d), CLAUDE.md rule 7 updated.
- **D-095 (2026-10-28):** the PO found the Tee-Kontor photos unattractive as the timer's top band. The PO chose an ink-wash band (new asset **A13**, dark only, the PO uploads it) with the tea photo as a small round seal; the quick timer gets the band without a seal; in v0.2.1 as #102 (T3, ←#85; approved by the PO on 2026-10-28); A13 delivered the same day. Prompt and filename in `docs/design/assets.md`.
- **Decision numbers:** see the v0.2.1 done section (next free D-097).

### M2 done (2026-10-26)

- The PO approved `v0.2.0-rc.1` (#74, #77). #78 merged into `main` (7157cc4); accuracy run on
  7157cc4 passed; `v0.2.0` released from `main` (versionCode 20099, certificate verified).
  Spec #62 and its tickets closed.

### Earlier

Spec [#6](https://github.com/katznmeik/UmamiT/issues/6) (M1-00 … M1-03a,
tickets #8–#17) is done: SDK hook, Gradle skeleton, CI gate with emulator
job, signing, release workflow, probe screen, exact-alarm/FGS/Live Update/
full-screen probe and synthesised sounds; `v0.0.2-probe` passed on the
Galaxy (D-040 gate open for M1-09/M1-11).

Spec [#27](https://github.com/katznmeik/UmamiT/issues/27) (M1 waves 2–3,
tickets #28–#38) is implemented and merged on the M1 branch (PR #25):
`BrewTimerEngine` (all of 04 §3–§5, §7.2, §10; 196 tests, 100 % line
coverage), Room schema v1 (frozen from `v0.1.0-rc.1`), repositories,
settings, session lifecycle, active-brew store, backup rules and restore
detection, seed teas/vessels/stock, allow-listed shop client and one-time
seed photo download, design system with motion tokens, TimerRing/alert
surface/active brew bar, navigation shell, icons, Schritte-Sheet, motion
GIFs. Per-ticket reviews (T4 design review of the engine, T3 recovery
review, T4 security review of the network client, motion reviews of every UI
ticket) and a T4 review of the whole slice were applied (D-046 … D-055).

1. Design and motion check #39: **approved by the PO** on 2026-10-22 (glow,
   pulse and the light-green dark-mode card unchanged).
2. Spec [#40](https://github.com/katznmeik/UmamiT/issues/40) for waves 4–7 is
   written (six test seams agreed with the PO; accuracy run as a manual CI
   workflow before each rc) and broken into tickets #41–#56 (frontier: #41
   controller core; then #42 alarms/service, #43 diagnostics, #44 alerts,
   #45 timer screen; #55 is the PO's checklist and design review, #56 the
   merge and `v0.1.0`). Next: `/implement-spec` on #40. Scope: M1-09 background integration,
   M1-10 alerts, M1-11 Live Update, M1-12 timer screen, M1-13 Start and
   setup, M1-14 quick timer, M1-15 Einrichtung and settings, M1-16 journal,
   M1-17 renders, M1-18 review and releases, M1-20 diagnostics. Notes carried
   into it: hosts pass `alerting` to the Schritte-Sheet
   (D-051); the controller follows 04 §4/§7.1 (read `now` in the event lock,
   one `Recover` per process, idempotent `StartAlert`, `ShowSavedSheet` as
   `NotifySessionSaved` on *Beenden & neu starten*); after a device restore an
   `IN_PROGRESS` session row must be finished or discarded (#36);
   `SeedDataSeeder` and `SeedPhotoDownloader` are called from first start
   (M1-15); rail-layout insets go on the on-device checklist.
2. The next APK for the PO is `v0.1.0-rc.1`. The probe screen, its classes
   and tests were removed in #54 (D-040).

### M2 planned (2026-10-25)

- D-072 (reorder link, INV-09, CAT-11 *Im Shop öffnen*) merged in #60.
- The PO grilled M2 as a whole: 16 decisions plus a lean test plan recorded as
  D-073 (#61). Key points: add, repurchase and the picker flow ship in M2; refresh allowed during a
  brew with the timer first; *Neu* badge; default view by Sorte, available, pure;
  1200 px photos; accuracy run only for rcs touching timer code and before the
  final; no emulator test of the refresh; fewer screenshots.
- Spec #62 via `/to-spec` (4 test seams: parser/recipes, refresh vs. simulated
  shop, add/repurchase on in-memory Room, reduced screenshots; plus Claude's
  live-shop runs and the PO's checklist). Correction posted on #62: robots.txt
  matching already exists in the network module since M1.
- Tickets #63–#77 approved by the PO, re-sliced as tracer bullets per
  `/to-tickets` after a first layer-by-layer breakdown:
  #63 prefactor: shared polite shop session and image re-encoding · #64 tracer
  bullet *Katalog laden* fills the list, T4 (←#63) · #65 background refresh
  service, T4 (←#64) · #66 tea detail, photos, *Im Shop öffnen* (←#64) · #67 sizes
  and prices (←#66) · #68 recipes and „Rezept prüfen" (←#66) · #69 search, filters,
  default view, „Neu" (←#64) · #70 later refreshes, summary, seed linking, T4
  (←#66, #68) · #71 add and repurchase (←#67, #68) · #72 picker flow and *Rezept
  passt* (←#71) · #76 Ratgeber (←#66) · #73 #58 fixes and seed retry · #77 early
  design check, PO (←#66, #69, #71) · #74 rc and checklist, PO · #75 merge and
  `v0.2.0`.
- **Decision numbers:** M2 used up to D-092 (see the v0.2.1 section for the current next free number).

### M1 done (2026-10-25)

- The PO tested `v0.1.0-rc.1` on the Galaxy: every 09 §4 item, the rail
  layout and the design review passed (#55). The explicit approval was given
  in the session.
- #25 merged into `main` with a merge commit (04a22a5); specs #6, #27, #40 and
  their tickets closed. `v0.1.0` released from `main` (versionCode 10099,
  certificate fingerprint verified).
- The rc accuracy run (3900689, API 36, 50 × 60 s): pre-alert p50 41 /
  p95 113 / max 910 ms, alert p50 25 / p95 34 / max 136 ms.
- **Carried into M2:** #58 (optional review findings); the D-071 note (a
  `startForeground` post later than 0.5 s is repaired only by the 15 s
  refresh); the animation pass (Schritte-Sheet rows, status mark, kettle
  hint fade).

### Done (M0)

- Full specification v0.2 (01–12), reviewed twice for consistency.
- Tee-Kontor site structure verified live (V-01); catalog spec rewritten.
- M1 seed data verified live: Bio Sencha Tanabata (TG0213) and Bio Sencha
  Hisui (TG0557), plus the PO's vessels (Kyusu 250 ml usable, Becher mit Sieb
  400 ml).
- Design: brand mockup, mockups v2, mockup prompt, asset list with prompts.
- Subagent distribution plan (12).
- App renamed to **UmamiT** (D-033), application ID `com.umamit.app`.
- `CLAUDE.md`, this file, PR template and the M1 plan (`docs/plans/M1-timer.md`).
- Environment checked: Android SDK, Maven, Gradle and font hosts are
  reachable; JDK 21 and Gradle are preinstalled; the Android SDK is not; no
  KVM (emulator tests run in CI).
- Spec review (Opus xhigh) and PO decisions applied as D-036: three network
  uses, synthesised sounds, full-screen alert, simplified backup, Schritte-Sheet,
  CI emulator job, release candidates, probe release, catalog hardening.

### Working agreements (M1 start, grilled with the PO on 2026-10-21)

| Topic | Agreed |
|---|---|
| Start | 2026-10-22 09:05 Europe/Berlin via a scheduled task in session `session_01TEa8Scj1c19uHy7ruNYZYJ`; `/implement-spec` on spec #6 (tickets #8–#17) |
| Parallelism | At most **2** implementer subagents at a time |
| Signing key | A fresh key, generated by the orchestrator (the one sent on 2026-10-21 was discarded); the PO adds the secrets during the day |
| End of the run | Stop after #17 (probe released and tested). The PO starts the next spec (waves 2–3) with `/to-spec` |
| `release.yml` PR (D-039) | Its own branch `claude/umamit-release-yml`, deleted after the merge |
| Reporting | Milestones and blockers in the session; **push notifications only when the PO must act** (key files ready, probe ready, blocking decisions) |
| Probe results | The PO replies in the session; Claude transfers them to #17 and this file |
| Next spec (waves 2–3) | Started by the PO; include the design/motion skills per `docs/plans/M1-timer.md` §3.2 (motion section in 03, `/review-animations` on UI tickets, worst-case renders in M1-17) |
| Probe gate | D-040: all four checklist items must pass before M1-09/M1-11; Now Bar best effort; fix loop with `0.0.N-probe`; runtime fallbacks stay |

## Session log

| Date | Summary |
|---|---|
| 2026-11-09 | **PO feedback after v0.4.1** (four points) grilled in two rounds into **D-108**: v0.5.0 before M4 (M4 → v0.6.0): any two active timers, every tea section of the shop with one category chip at a time, per-tea stock warning with its own threshold and a global reminder interval, a flavour filter (taste notes and scales). 01 NG6/NG7 retired, 09 §2 row added. Spec #194 published with `/to-spec` (six existing test seams: timer controller harness, catalog parser, catalog refresher, `CatalogBrowser`, low-stock repository and schedule, umamit.db v5 migration; PO confirmed). Next: tickets. |
| 2026-11-08 | **v0.4.1 done:** PO approved rc.1 (checklist and design review); #191 merged (`2b5735fc`), both accuracy variants PASS on `main`, `v0.4.1` released (versionCode 40199, certificate verified); #171, #175–#189 closed. Next: M4 → v0.5.0. |
| 2026-10-29 | **v0.2.1 done:** #106 merged (9a73b19), accuracy PASS on `main`, `v0.2.1` released (versionCode 20199, certificate verified); #81, #83–#94, #97, #98, #100, #102 closed. Next: v0.3.0. |
| 2026-10-29 | **PO approved v0.2.1-rc.1** (all checklist items, design review; Vorwarnton Zimbel stays default); version 0.2.1; merge of #106 and the final release follow. |
| 2026-10-29 | `v0.2.1-rc.1` released (f88e371, versionCode 20101, certificate verified); accuracy run PASS; checklist posted on #93; waiting for the PO. |
| 2026-10-29 | v0.2.1 built on PR #106: all 14 tickets merged and verified (#83–#92, #97, #98, #100, #102), three parallel code reviews (T4 timer/migration, T4 catalog/network, T3 UI) with two fix rounds, D-096 recorded, design renders in `docs/design/review/v0.2.1/`, version `0.2.1-rc.1`; next: accuracy run, rc dispatch, PO test (#93). |
| 2026-10-29 | **Go for v0.2.1:** the PO ran `/implement-spec`; building spec #81 started with #83 and #84 (T4). |
| 2026-10-28 | The PO updated the Galaxy to One UI 9 (Android 17) and re-checked the timer with `v0.2.0`: reliability settings, *Alarm testen*, a locked brew with full-screen alert, Live Update / Now Bar, quick timer — all passed. Device references in CLAUDE.md and specs 01, 04, 07 updated; the app keeps target SDK 36. |
| 2026-10-28 | D-095: timer top as an ink-wash band with the tea photo as a seal; #102 added to v0.2.1; A13 prompted, delivered and checked (1536 × 1024, no text or frame, calm bottom centre). Nested sessions considered and not used (subagents in worktrees stay). |
| 2026-10-28 | The PO uploaded A6–A11 (light and dark, 1536 × 1024), checked: complete, no text, frames or dark corners; #100 builds A8 and A11 into v0.2.1. |
| 2026-10-28 | D-094: sold-out teas missing from the shop's sitemap (TG0170); re-check (#97) and a tea from a shop link (#98) added to v0.2.1; specs, CLAUDE.md rule 7 and the plan updated. Image prompts A6–A11 sent to the PO. |
| 2026-10-26 | Handover plan `docs/plans/v0.2.1.md` for the next session; paused until the PO's usage limit resets. |
| 2026-10-26 | v0.2.1 tickets #83–#94 via `/to-tickets`, approved by the PO and published under #81; building waits for the PO's go. |
| 2026-10-26 | **M2 done:** the PO approved rc.1; #78 merged; accuracy on 7157cc4 passed; `v0.2.0` released from `main`. PO feedback #79 grilled (Q1–Q21) into D-093 (#80, merged); spec #81 for v0.2.1 approved by the PO; tickets next. |
| 2026-10-26 | Parser fixes from live sample 2 (D-089); T4 milestone review (2 areas, no Block) fixed (D-090); two full live runs (run 1: 20.1 min, PNG photos missing, sets/SALE in → D-091 fixes; run 2: 15.7 min, 255 teas, all photos, no stop); the PO approved #77 and confirmed D-089/D-091 choices; accuracy run failed on step 1 (1178 ms) → D-092 keeps alert sounds loaded (rerun PASS, max 643 ms); `v0.2.0-rc.1` released (versionCode 20001, cert verified), checklist on #74. ProcessDeathTest made robust against a sticky service restart. |
| 2026-10-25 | M2 tickets #63–#73, #76 merged on PR #78 (D-074 … D-088), each gated by the orchestrator; timer fix D-084 (stale foreground notification after a process death, corrects D-080); two live samples of the shop (59 requests, no stops); early design check #77 sent to the PO. |
| 2026-10-25 | **M2 started:** the PO invoked `/implement-spec` on spec #62 (frontier #63 prefactor and #73 #58 fixes). |
| 2026-10-25 | M2 planned: D-072 reorder link (#60); M2 grilled with the PO (D-073, #61); spec #62 written and broken into tickets #63–#77 (approved by the PO). Implementation waits for the PO's start. |
| 2026-10-25 | **M1 done.** The PO approved the checklist and the design review with `v0.1.0-rc.1` (#55); version 0.1.0; #25 merged into `main`; `v0.1.0` released from `main`; the working branch restarted from `main`. |
| 2026-10-23 | #54 finished: review fixes (UI S1–S7, R2-1, D-068 … D-071), the PO's images A1–A5 built in, version `0.1.0-rc.1`, accuracy run passed, `v0.1.0-rc.1` released; checklist posted on #55. A committed merge conflict in 09 §4 was resolved. |
| 2026-10-22 | Spec #40 (M1 waves 4–7) written via `/to-spec` and broken into 16 tickets #41–#56 via `/to-tickets` (granularity and blockers approved by the PO). CI: build-logic plugin repositories fixed (failed with an empty Gradle cache). |
| 2026-10-22 | PO approved the design and motion check #39 (pre-alert glow, alert pulse, light-green dark-mode card) without changes. Spec #27 complete; next is the waves 4–7 spec. |
| 2026-10-22 | Spec #27 implemented via `/implement-spec`: tickets #28–#38 merged on PR #25 with per-ticket reviews; T4 slice review (engine, data/network/backup, UI) fixed (D-054, D-055); CI hardened (emulator/system-image cache with retries, schema freshness and frozen-schema checks); flaky probe and network tests fixed. #39 (PO design and motion check) open. |
| 2026-10-22 | Spec #27 broken into 12 tickets #28–#39 via `/to-tickets` (granularity, blockers and the PO ticket #39 approved by the PO). Spec #27 for M1 waves 2–3 written via `/to-spec` (4 test seams agreed with the PO: engine reducer, data layer with MockWebServer, Compose rendering incl. record-only motion GIFs, CI pipeline). |
| 2026-10-22 | #14–#16 merged (probe screen, Android 16 timer probe, sounds; T4 fix D-044); `v0.0.2-probe` released and **passed** all four probe items on the Galaxy (#17). Q-15 feedback: Klangschale not bright enough, tick should sound more like a bowl → variants A/B/C sent; the PO chose C + C (D-045, committed). The countdown also appeared in the Now Bar. Run stopped after #17 as agreed. |
| 2026-10-22 | #8 (SDK hook), #9 (signing key + secrets), #10 (skeleton), #11 (CI gate) and #13 (release workflow, registered on main via #26; negative dispatch test passed) merged into the M1 branch; D-042 tooling decisions. |
| 2026-10-22 | M1 started (go in effect); `/implement-spec` on spec #6, frontier #8 and #9. The scheduled 09:05 start had not fired by 09:08, so it was disabled and M1 started directly in the session. |
| 2026-10-21 | Grilled motion & design with the PO: D-041 motion guardrails; 03 §3.6 rewritten; GIF motion recordings added to the design review (M1 plan §3.2, M1-17, 09 §1). |
| 2026-10-21 | Removed 7 web/iOS-only Emil Kowalski skills; noted how the remaining design/motion skills enter the waves 2–3 spec (M1 plan §3.2). |
| 2026-10-21 | Grilled the M1 start with the PO: working agreements recorded above; D-040 (probe gate, fix loop); 09:05 start re-created as a scheduled task in this session (the earlier one had disappeared). |
| 2026-10-21 | Emil Kowalski's design skills installed (`npx skills add emilkowalski/skills`, MIT): 15 skills in `.claude/skills/`, pinned in `skills-lock.json`. Useful here: `emil-design-eng`, `apple-design`, `review-animations`, `improve-animations`, `find-animation-opportunities`, `animation-vocabulary`; the rest target web/React/Swift. |
| 2026-10-21 | Project skill `/implement-spec` added (vendored beta from mattpocock/skills, MIT, with UmamiT rules); usable for spec #6 once the go is in effect. |
| 2026-10-21 | Spec #6 broken into 10 tickets (#8–#17, sub-issues of #6); frontier: #8 (SDK hook) and #9 (signing key). The PO created the missing triage labels; #9 and #17 are labelled `ready-for-human`. |
| 2026-10-21 | Spec issue [#6](https://github.com/katznmeik/UmamiT/issues/6) for M1-00 … M1-03a (`ready-for-agent`); M1 plan updated (test seams §3.1, task details); D-039 recorded. |
| 2026-10-21 | Matt Pocock skills set up: GitHub Issues as tracker, default triage labels, domain docs mapped to specs 10/11 (`docs/agents/`). An early M1 start was stopped by the PO; the go stays 2026-10-22 09:05. |
| 2026-10-21 | PO gave the go for development, effective 2026-10-22 09:05 Europe/Berlin (D-038); a scheduled check-in starts M1 then. |
| 2026-10-21 | PR #2 merged by Claude; rule D-037 (Claude merges all PRs) added. |
| 2026-10-21 | PR #1 (M0 specs) merged; repository renamed to `katznmeik/UmamiT`; references updated. |
| 2026-10-21 | Opus xhigh spec review; fixes applied (D-036). |
| 2026-10-21 | Grill round 4 (D-034) recorded; PO asked for an M1 walkthrough before deciding on the go. |
| 2026-10-21 | Specs v0.1 → v0.2 after PO interviews (grill rounds 1–3), live Tee-Kontor verification, seed teas, agent plan, rename to UmamiT, project docs. |
