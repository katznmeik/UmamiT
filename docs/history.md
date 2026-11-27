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

### v0.11.0: rc session, #370 (2026-11-26)

- **Setup.** Orchestrator on Opus `medium`; assigned `claude/dreamy-shannon-qjyv00`, worked on `claude/inspiring-ritchie-8hfw00` as asked. Mirror init script, `gw.sh`/`gate.sh` and the Robolectric SDK 35 jar rebuilt; the container restarted once mid-session, with the scratchpad and `~/.m2` intact.
- **PO answers** (as options): the four build questions (as built; mock-up size judged on the Galaxy), four review points, the collection menu below the segment row, and two `/review-animations` findings, all recorded in D-137.
- **Fixes:** `bf4a0992` mock-up kit (T3), `2391a6ab` topic title and tour scroll (T2), `1e9153a1` tour fades (T2); each diff read, goldens looked at, the full local gate on `df439e23` and `1e9153a1`.
- **CI:** `savedSheetInAndOut` (API 36) needed two reruns on `df439e23`; the reference-derived allowance dropped, the sheet did not change.
- **Release:** `v0.11.0-rc.1` (110001) from `1e9153a1`, APK and certificate verified by download.

### v0.11.0: build session, #366–#369 (2026-11-26)

- **Setup.** Orchestrator on Opus `medium`, started by the PO with `/implement-spec #365`. The session was assigned `claude/adoring-goldberg-x08zku` and worked on `claude/inspiring-ritchie-8hfw00` as asked. Tickets #366–#371 labelled `ready-for-agent`; the mirror init script rebuilt.
- **Specify session (same day, before):** the PO's tour feedback grilled into D-137 Q1–Q16; spec #365, tickets #366–#371, plan `docs/plans/v0.11.0.md`, draft PR #372; the PO approved all („Approve all").
- **Build** (D-137 build notes for each):
  - `b0f6ab7e` #366 the mock-up kit and six tour mock-ups (T3).
  - `be015ef9` #367 the Anleitungen, eight topics, 16 more mock-ups (T2); sent back once so German quotes close with “ as everywhere in the app.
  - `566500e2` #368 the tour rework (T2); sent back once so the button fits all four labels, not only the current language's two.
  - `17e9c75d` #369 the entry points (T2), in parallel with #368 in its own worktree; „Mehr dazu" stays inside `feature:onboarding`, so the two did not overlap.
- **Verification:** per ticket the diff, the copy against 03 by script (every topic text key by key, de and en), goldens looked at, and the cross-module check on the merged head. CI green on all five jobs on `b0f6ab7e`, `be015ef9` (one API 35 rerun: Maven could not resolve `junit:4.13.2`) and `17e9c75d`. Protected modules and manifests unchanged against `1933bc15`.

### v0.10.0: released (2026-11-25)

- **Setup.** The final session (#363) was assigned `claude/intelligent-knuth-3fje0d` and worked on `claude/inspiring-ritchie-8hfw00` as asked.
- **PO approval (2026-11-25, in session):** all of 09 §4 „v0.10.0" and the design review on rc.1. The PO ran `/review-animations` in the session (scope: the SCR-29 sheets, SCR-34, the SCR-33 tour, *Sprache*, rotation in place): approved, its two tour polish findings deferred to the tour rework (asked as options).
- **The PO's tour feedback:** too broad, should describe more and harder features (e.g. adding a tea without the Katalog); the pages look empty. Recorded as feedback in D-136; the PO chose to ship and rework the tour in the next release, starting with a grilling round.
- **Release.**
  - `3a98a3ef` on the branch: 09 §4 ticked, `umamit.version=0.10.0`, D-136 final-session notes, CLAUDE.md rule 4 current. CI green after one rerun of „Checks" (dependency resolution: ktlint and junit not resolved).
  - PR #364 merged (merge commit `1933bc15`, the same tree as `3a98a3ef`). CI on `main`: the API 35 emulator job failed twice behind the launcher's ANR window, green on the third run.
  - All three accuracy variants PASS on `1933bc15` (runs 45633063252, 45633068880, 45633075837).
  - `release.yml` dispatch answered HTTP 500/502 four times without a run; the fifth queued run 45652763878.
  - [`v0.10.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.10.0) from `main` `1933bc15`, not a pre-release, versionCode 100099, APK SHA-256 `ed5d151b…142c`, certificate the same as v0.9.0's (checked on the downloaded APK with `apksigner`).
  - #353–#363 closed by the merge; the branch restarted from `main`.

### v0.10.0: rc session #362, rc.1 published (2026-11-24/25)

- **Setup.** Orchestrator on Opus `medium`. The session was assigned `claude/eager-albattani-6m7yn8` and worked on `claude/inspiring-ritchie-8hfw00` as asked. No PO answers had arrived on PR #364.
- **Fix: GEN-13 AC1, texts without an activity.**
  - T3 (Opus `medium`): `core:data` `AppLanguageContext` for the catalog, low-stock and backup notifications and their channels, and `stockGramsText(grams, language)`.
  - T4 (Opus `high`): the timer channel names.
  - New tests set the process in one language and the app in the other; they were red before the fix.
- **Jank calibration (D-117).** The eight turn lines were read from three API 36 runs (`b40aef15`, `16a7eab7`, `abf629ce`).
  - Only *timer → landscape Journal* never exceeded the limit, so it gates now. `TransitionJankTest.rotation()` applies the gate.
  - It passed at 1.7× on its first gated run (`bd91d48c`).
  - The other seven stay report-only: 0 frames, NOREF, always over, or flipping around the limit.
- **Reviews.**
  - The T3 milestone review: no blocker, three should-fix, plus the duplicate GEN-10.
  - The T4 timer review: no blocker, no rule-8 risk. Its named question had these numbers: 6 `LocaleManager` lookups per alert post (14 when the service starts at the alert); the `close()` cause is test tooling.
  - `/review-animations` is user-invoke only. The PO chose to run it and invoked it without a scope, so it has not run yet.
- **Fix rounds.**
  - T3 (`1523f2ef`): the stock edit sheets on float noise, the English decimal mark, the confirm word from the hint's resources, one language snapshot per `core:data` text, the full format-specifier check.
  - T4 (`23ea46e8`): one language source and one lookup per notification (the timer's copy removed); the FSI check off the alert's main-thread path; `BuildConfig.VERSION_CODE`; two English no-break spaces; the `close()` comments.
  - T3 (`961f1a64`, `99afbbdc`): the app language renumbered to **GEN-13**, and 21 English no-break spaces.
- **CI.**
  - Green on `bd91d48c`, `1523f2ef`, `b6f093fb` and `5978b729`.
  - On `00c3327e`, API 35 failed in every `TransitionJankTest` case behind a launcher ANR window (foreign). API 36 was green on the same code, and the next head was green.
- **Accuracy.**
  - All three variants on `b6f093fb` failed before step 1: `scripts/accuracy-run.sh` still named `AndroidJUnitRunner`, but since #358 the `core:timer` APK declares `GermanLocaleRunner`.
  - A T3 fix (`5978b729`) reads the runner from the device; `stress-instrumented.sh` gets the same fix.
  - Then all three PASS on `5978b729`.
- **Release.**
  - Full local gate PASS, and renders in `docs/design/review/v0.10.0/` (138, light and dark, de and en).
  - `v0.10.0-rc.1` (100001) from `5978b729`: the first release run failed on a Maven Central HTTP 429 in its unit-test step, and the rerun passed.
  - APK SHA-256 `7fea0c3b…f7b9`, certificate the same as v0.9.0's.

### v0.10.0: build session 2 (2026-11-24)

- Orchestrator on Opus `medium`; the session was assigned `claude/focused-cannon-n1p2q6` and worked on `claude/inspiring-ritchie-8hfw00` as asked. No PO answers to build session 1's questions had arrived (PR #364 had no comments).
- **Step 3: #358** (T3, Opus `medium`): `AppLanguage` (`core:common`), locale-aware `GermanFormat` and the other display formatters (German byte for byte), *Sprache* (`AppLocales` around `LocaleManager`), `locales_config.xml`, `localeFilters` de + en, the German test pin (`config/test-locale` Robolectric config plugin; `GermanLocaleRunner` for device tests), `LanguageSwitchDeviceTest`. CI on `43f4e6c6`: Checks and Build green; both emulator jobs failed in every `TransitionJankTest` case that opens the Teeauswahl. A T3 diagnosis found Compose's process-wide animation scale stuck at the emulator's 0 (the setting was written with no window listening); `ComposeAnimationScale` (test only) makes it follow. The run also caught `RuntimeEnvironment.setQualifiers` bypassing the locale pin (`TeaPickerScreenshotTest`, `ShellScreenshotTest`).
- **Step 4: #360** (T3, Opus `medium`): the CSV follows the app language (English `,`, decimal point, ISO dates; English words in Kotlin, `csv_*` not translatable). **#359 run 1** (T2, Sonnet `medium`): `values-en` for app and eleven non-timer modules, the AC2 vocabulary `translatable="false"`, `TranslationCompletenessTest`, the typed words REPLACE / DELETE, English goldens for six screens. Combined check green; **CI green on all five jobs on `b40aef15`**.
- **Step 5: #359 run 2** (T3, Opus `medium`): `values-en` for `feature:brew`, `core:timer`, `core:backup` (resources only on the timer path), the English share text (the recipe file unchanged, tested), the completeness check over every module, English goldens for the setup, SCR-05 and SCR-07. Pushed alone after the combined check and the 04 §12 tests (`16a7eab7`). **#361** (T2, Sonnet `medium`): the six-page tour (`TourScreen.kt`), first start welcome → tour → reliability, *Einstellungen* › *Hilfe* › *Einführung*; four settings goldens re-recorded for the new section. CI on `16a7eab7` (run 2 alone): Checks and Build green, both emulator jobs red: German device-test assertions met English (`RecipeShareDeviceTest`, `ProcessDeathTest`, `TimerNotificationsDeviceTest`). A T4 fix (Opus `high`, escalated from #358's T3) found that Android 15/16 drops a process's per-app language when its last activity goes; `AppLanguageContext` makes the timer notifications read the app language from `LocaleManager`, and `GermanLocaleRunner` re-pins German before every test and waits. Combined check and 04 §12 tests green on the merged head; **CI green on all five jobs on `abf629ce`**. The same cause leaves the low-stock reminder, worker-posted catalog notifications and the channel names in the phone's language: an AC1 defect for #362.
- Not changed: umamit.db v9, `checkManifestPermissions`, the German goldens (except Settings for *Sprache* and *Hilfe*).

### v0.10.0: build session 1 (2026-11-24)

- Orchestrator on Opus `medium`; the session was assigned `claude/kind-brown-eyjcmo` and worked on `claude/inspiring-ritchie-8hfw00` as asked. Draft milestone PR [#364](https://github.com/katznmeik/UmamiT/pull/364) opened (closes #353–#363).
- **Step 1:**
  - **#354** (T3, Opus `medium`): `StockRepository.updatePurchase` / `updateCorrection`; edit or delete of a purchase and the session cost recompute in one Room transaction (`InventoryDao.editEvent`, `deleteAndRecost`); SCR-29 rows open „Kauf bearbeiten" / „Korrektur bearbeiten". `StockEditTest` (15 cases). New goldens only. No migration.
  - **#355** (T3, Opus `medium`, no app code): `docs/plans/v0.10.0-fsi-research.md`. The system package installer sets `USE_FULL_SCREEN_INTENT` to DENIED on every sideloaded install, updates included; from Android 15 that DENIED overrides the user's choice. Claude checked the installer lines on AOSP `main` and `android15-release`. Android 17 / One UI 9 is unverified. Probe steps use the rcs; options A–D go to the PO.
  - CI on `2d3d9f76`: green on all five jobs.
- **Step 2:**
  - **#357** (T4, Opus `high`): design note first, recorded as the D-136 build note „Portrait timer" (`59aecb41`). The first draft left four once-per-path re-creations, so Claude sent it back. The chosen variant lets `MainActivity` handle `orientation|screenLayout|screenSize|smallestScreenSize` itself and asks for portrait while a timer screen is on top, so no path re-creates. Tests: `TimerScreenOnTopTest` (JVM); rotate cases in `TransitionJankTest` and `TimerAlertOverLockScreenTest` (CI only); the new jank lines are report-only.
  - **#356** (T2, Sonnet `medium`): the SCR-34 sheet (`FsiPrompt.kt`), `fsiPromptedVersionCode` in `core:datastore`, one device-bound backup key, `FsiPromptRepository`.
  - Merge fix: one `isTimerScreen()`. The combination broke detekt `LongMethod` in `MainActivity.onCreate`; the T4 implementer extracted `followLockScreenFlags` without changing behaviour.
  - `feature/brew`, `core/timer`, `core/timer-engine` and the schemas are unchanged since `f663318b`.
  - CI on `97ad2b9b`: Checks and Build green; both emulator jobs failed in #357's four new cases only. Verdicts (T4 implementer): the two alert cases' `ActivityScenario.close()` after an alert never reached DESTROYED (test artefact, close dropped, `tearDown` finishes); `timerFromLandscapeJournal` on API 36 looked for the bar's text, which `ActiveBrewBar` clears (test bug); `rotateWithTimerOnTop` looked for *Schnelltimer* below the fold in the landscape rail (test artefact, not proven). `timerFromLandscapeJournal`, Journal stayed at rotation 0 after back from the timer: the diagnostics added in `f238a673` showed `requested=-1` (released) and `user_rotation=0`. With rotation locked, WindowManager writes the forced portrait into `user_rotation`, an artefact of the rotation stand-in; the test re-applies „held sideways" (`4379b120`). All fixes were in the tests. **CI green on all five jobs on `4379b120`.**

### v0.10.0: specify session (2026-11-24)

- The PO chose a **feedback round** on v0.9.0 (not the D-112 ideas, quick access or M5) and gave five points: edit everything on the tea detail (purchases), the Vollbild-Benachrichtigung reset by every update, a glitchy timer when turning the locked phone, English, a tutorial. Another feedback round follows.
- Grilled in two rounds of four options: **D-136** Q1–Q9 (every stock event editable, past costs recomputed; research + a reminder sheet; timer screens portrait; English via the per-app language, tea vocabulary stays German; a swipe-through tour; one release v0.10.0; Claude drafts copy, the PO approves). Items 10–16 are Claude's defaults (timer-code release, the recompute rule, SCR-34, portrait mechanism, locale, tour pages).
- Specs: 02, 03 (new SCR-33, SCR-34), 05 §6, 07, 09 §2/§4, 10, 11, CLAUDE.md rule 4. Spec issue #353, tickets #354–#363 (`needs-triage`), plan `docs/plans/v0.10.0.md`. **The PO approved all of it the same day** („Approve all"); tickets relabelled `ready-for-agent`.
- The session was assigned `claude/elegant-galileo-46r9pb` and worked on `claude/inspiring-ritchie-8hfw00` as asked.

### v0.9.0: released (2026-11-20)

- **PO approval (2026-11-20, in session):** „All approved" for the rc.2 items that Claude had posted on PR #345 (comments 7036535012 and 7036569672). Both ways of receiving a recipe file worked on rc.2: (a) tapping `<Tee>.umamit-rezept.json` in My Files and (b) sharing it from My Files or a messenger (asked as options). This is recorded in D-134's and D-130's build notes. Everything else on 09 §4 „v0.9.0" and the design review had been approved on rc.1.
- **Version:** 09 §4 ticked, What's new folded into the main bullets (New, Changed, Fixed), `umamit.version=0.9.0` (90099) in `c7750b45`. CI run 44729897307 green on all five jobs on the first attempt.
- **Merge:** PR #345 merged into `main` with a merge commit (`f663318b`). CI run 44733973161 went green on attempt 2: the first „Checks" failed at configuration when Maven Central did not serve `kotlin-stdlib:2.4.10`, a transient resolution failure (the other jobs resolved it).
- **No accuracy runs and no T4 timer review:** the timer diff against `aed2b877` stayed empty (D-130 Q16).
- **Release:** run 44742421717 with `RELEASE_TOKEN` (D-118) passed on the first attempt.
  - [`v0.9.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.9.0) is built from `main` `f663318b`, not a pre-release, with the tag on the merge commit and the What's new on the page (D-132).
  - versionCode 90099, APK SHA-256 `6dd9f74f…4f4b`, the release digest of the downloaded APK.
  - `scripts/verify-release-apk.sh` on that APK: the signature is valid, the certificate `a500e2c6…2f67` matches `signing/release-cert-sha256.txt`, `com.umamit.app` 0.9.0 / 90099, not debuggable. The APK came through the REST assets API, because `gh release download` uses GraphQL, which is blocked here.
- **Issues:** the merge closed #333's tickets (#330, #334–#343, #346, #349, #350) and #351, #352; #344 was closed by hand.
- **Branch:** `claude/inspiring-ritchie-8hfw00` was restarted from `main` (fast-forward). The session was assigned `claude/exciting-franklin-5bibtm` and did not use it.
- **Final session (Opus `medium` orchestrator, no subagents):** mostly waiting on CI and the release run.

### v0.9.0: rc.2 round after the PO's check of rc.1 (2026-11-20)

- **The PO's check of `v0.9.0-rc.1`:** everything approved except receiving a recipe file: on One UI 9, neither (a) tapping a `.umamit-rezept` in My Files nor (b) sharing it from My Files or a messenger offered UmamiT; (c) *Rezept importieren* worked. After *Alle Daten löschen* the full-screen alarm and Live notifications stayed allowed, while exact alarms and the battery exemption were off. The PO also did not want the two seed teas that came back after the wipe.
- **Decisions:** D-134: the recipe file becomes `<Tee>.umamit-rezept.json`, shared as `application/json`. D-135: no seed teas or vessels and no seed photo download, so a fresh install and the wipe start empty, existing data stays, and there are three network uses (CLAUDE.md rule 7). Both are in the specs, with D-130 and D-131 build notes and 09 §4 items for rc.2.
- **Tickets:** #351 (T3, `.json` ending) and #352 (T3, no seed data: `FirstStart`, `SeedPhotoDownloader` and `SeedPhotoProgress` removed; `SeedDataSeeder` kept as a test fixture; onboarding photo line gone; only the onboarding reliability goldens at font 1.0 changed). The timer diff against `aed2b877` is empty.
- **Gate:** the full local gate passed and CI was green on `f243d7fd`; `v0.9.0-rc.2` (90002) was dispatched from `main`'s `release.yml`, and the What's new has a „Since rc.1" part.

### v0.9.0: rc session #343, rc.1 published (2026-11-19/20)

- **Branch:** worked on `claude/inspiring-ritchie-8hfw00` (assigned: `ccr-9405e06c-35haf0`, not used). PR #345 stays draft.
- **Reviews:** the T3 milestone review (standards + spec; no blockers, 5 should-fix, 6 nits), the T4 review of the import path (#335, #336, #342's `ReceiveRecipeActivity`, the start gate; no blockers, 5 should-fix incl. a pending marker wiping a staged import, a stuck late failure, photo files lost to the swap, no start-up guard, a hostile JSON's memory), and `/review-animations` (run from its file at T2: the slash command does not load in cloud sessions; the PO started it). Motion blocked on A-13 (SCR-32's two sheets vanished).
- **PO answers (3 rounds, options) → D-133 Q1–Q12:** the typed-word hint above the field; SCR-26 dialogs stay closed after an alert; the late-failure undo offered and refused; the recipe file handed to the main app (no 2 s main-thread wait); the busy cover for the snackbar's undo; the check cover after 0,25 s for at least 0,4 s; the wipe notice stays ~1 s in one window; SCR-32 rows fade and expand; the counts by `created_at`; the import waits for the seed photos; the progress eases 0,3 s; the text is enough under reduce motion. Specs 03 SCR-26/SCR-12, 05 §6/§8, 09 §4 amended.
- **Built (tickets #349 T4, #350 T3) and follow-ups:** the import path fixes, `ImportLock` (core:database) shared by import, undo, start gate, expiry and seed download, `AppStart` guards (the timer's recovery launch unchanged), the grant hand-over to `MainActivity`, reader caps, the fixture checked against raw JSON; SCR-26/SCR-32/snackbar UI and motion, tests for `UseCaseDataBackup` and `SystemAppDataWipe`, the package sheet golden. Then: the settings harness after the merge, typographic quotes in the share title, a race in `ImportGateTest` (the gate now holds the lock; `deleteTree` tolerates vanishing files), and three androidTest rounds for `ReceiveRecipeDeviceTest` (a test-APK sender provider in its own process) and `TransitionJankTest` (a system UI ANR dialog on the API 36 emulator covered Start; foreign ANR dialogs are now answered „Wait" and logged, an UmamiT ANR still fails; its cause was not found, the full logcat artifact is not reachable from the container).
- **What's new:** `docs/whats-new/v0.9.0.md` finished (6 New, 1 Fixed). **Renders:** `docs/design/review/v0.9.0/` (78, light and dark; README).
- **Verified:** the timer diff against `aed2b877` empty throughout; the full local gate (`gate.sh`) passed on `8cfa6a91`; CI run 44635481612 green on all five jobs.
- **Release:** [`v0.9.0-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.9.0-rc.1) (pre-release, versionCode 90001, from `8cfa6a91`, APK SHA-256 `dd961af8…e852`, certificate verified by the workflow), run 44640618013, first attempt.

### v0.9.0: build session 3, #346 Alle Daten löschen (2026-11-19)

- **Branch:** worked on `claude/inspiring-ritchie-8hfw00` (assigned: `claude/youthful-bardeen-0yai1v`, not used). PR #345 stays draft.
- **#346 (T3, SET-33, SCR-26, D-131):** the implementer first proposed the wipe's mechanism (no code): `ActivityManager.clearApplicationUserData()`, its effects sourced from AOSP (the notification grant is revoked, so onboarding asks again; channels, `USE_EXACT_ALARM` and the full-screen app-op stay; the device backup is not restored until a reinstall). The PO answered seven points as options (the system call with grants kept; a ~1 s notice „Daten werden gelöscht. UmamiT schließt sich, …"; greyed out also during an export or import/undo; the safety-copy sentence; a failure snackbar; no device-backup hint; TalkBack „LÖSCHEN eintippen"), then accepted the export's disabled text. Recorded as D-131 build notes; 02 SET-33, 03 SCR-26 and a 09 §4 „v0.9.0" checklist item updated. Built by a second T3 implementer (the first was lost in a container restart after its proposal): the `AppDataWipe` seam, the row, the dialog and the notice, 11 ViewModel tests, 24 new goldens (32 SCR-26 font-1.0 goldens re-recorded for the new row), `EmptyStorageFirstStartTest` in `:app` (it proves only „empty storage → first start"; the wipe itself is checked on the Galaxy).
- **Verified:** the orchestrator's diff review; the `:feature:settings` tests and screenshots, the `:app` test, `spotlessCheck detekt :feature:settings:lint :app:assembleDebug :app:assembleDebugAndroidTest :app:compileDebugUnitTestKotlin checkManifestPermissions` (unchanged); the timer diff against `aed2b877` empty; CI run 44547281859 green on `0053c733` (the „Checks" job after one rerun: `JournalPerformanceTest`'s wall-clock budget, the known flake).
- **For the rc review:** at font 2.0 the typed field's two-line label crowds the body's last line in the delete dialog, as in the existing ERSETZEN dialog.

### v0.9.0: build session 2, steps 4–5 (2026-11-18/19)

- **Branch:** worked on `claude/inspiring-ritchie-8hfw00` (assigned: `claude/tender-archimedes-ax6bw9`, not used). PR #345 stays draft.
- **Built and verified (diff review, local cross-module check, CI on the PR):** #341 Rezept teilen (T3, `RecipeFileProvider`), #338 SCR-26 Daten & Sicherung (escalated T2 → T3 because it adds `backupReminderEnabled` to the backup's settings), #339 backup reminder (T2, `BackupReminderWorker`, the tap to SCR-26), #342 Rezept übernehmen (T3, `ReceiveRecipeActivity`, SCR-32, the tap fetch) with two follow-ups from the PO's answers (package sheet, hidden rows, one transaction, the size message, an owned shop-link tea). The timer diff stayed empty after every ticket; `checkManifestPermissions` unchanged.
- **CI fixes:** `RecipeShareDeviceTest` expected a plain space where the share text has a no-break space; `NetworkModule` threw `NameNotFoundException` from a start-up launch in Robolectric and leaked into the next test (`UncaughtExceptionsBeforeTest`); `ReceiveRecipeDeviceTest` waited on a compose rule that never saw the `MainActivity` the hand-over opened.
- **New decisions:** **D-131** *Alle Daten löschen* (SET-33) was never built; the PO put it into v0.9.0 as **#346** (like a fresh install, greyed out during a timer, a hint to back up first, onboarding again; approved, built in build session 3) and chose the device-backup note's wording. **D-132** a „What's new" (English, 3–8 bullets) on every final and rc release page from `docs/whats-new/vX.Y.Z.md`; `release.yml` reads it (housekeeping PR #348, merged into `main` `6fc62c2a`); the v0.9.0 draft is in.
- **PO answers during the build (as options, D-130 build notes):** #341 no ⋮ on a new recipe, a snackbar when sharing fails; #338 the snackbar's undo acts directly, no zero counts, two new texts, the as-built defaults; #339 first check 30 days after the update, back to Einstellungen, the channel text, an import's switch is followed; #342 the package sheet on *Rezept übernehmen*, a tea with the file's key gets the recipe, the shop link only without a match, the copy.
- **Environment:** set up again (mirror init script, `gw.sh`, Robolectric jars). The container restarted twice; the scratchpad and the worktrees survived, #342's report was recovered from its transcript.

### v0.9.0: build session 1, steps 1–3 (2026-11-18)

- **Branch:** worked on `claude/inspiring-ritchie-8hfw00` (assigned: `claude/wizardly-fermi-djdpxn`, not used). Draft milestone PR [#345](https://github.com/katznmeik/UmamiT/pull/345).
- **Gate:** the PO approved spec #333, tickets #334–#344 and #330, the plan and D-130 items 17–27 as written (asked as options); the tickets and the spec were relabelled `ready-for-agent`.
- **Built and verified (orchestrator's diff review, local cross-module check, CI on the PR):** #334 backup writer (T3), #340 recipe file (T3), #335 backup reader (T4), #337 journal CSV (T3), #336 replacing import, safety copy, undo and expiry (T4; its crash-safety design note read and recorded before the code), #330 the tabs' ⋮ menu gives way to an alert (T3). The timer diff (`feature/brew`, `core/timer`, `core/timer-engine`) stayed empty after every ticket; umamit.db stays v9.
- **PO answers during the build (as options, D-130 build notes):** a CSV text cell starting with `=`, `+`, `-` or `@` gets a leading apostrophe; the import and the undo are refused while a catalog refresh runs; a step that fails after the commit still reports „Sicherung eingespielt".
- **Specs:** 02 BAK-04 (formula cells), 05 §8 (validation order, the replace's refusals, commit token and start gate), 10 (D-130 build notes for #334–#337, #340, #336).
- **Environment:** Maven Central answered HTTP 429 again; the mirror init script (`mirror.gradle`, scratchpad) and the Robolectric jars (sha512 checked) were set up as PROJECT_STATE says. The container restarted once mid-session; the scratchpad survived, #330's report was lost and its commit reviewed by the orchestrator instead.

### v0.9.0: specify session (2026-11-18)

- **Branch:** worked on `claude/inspiring-ritchie-8hfw00` (assigned: `claude/lucid-hopper-qc2hw0`, not used). No code, no PR.
- **Grilling:** four rounds of selectable options, Q1–Q16, recorded as **D-130**. The PO took every recommended option except Q4: the shop page of a received recipe's tea is fetched **only on a tap** (*Tee über Shop-Link laden*), which amends D-128 Q9 and the wording of GEN-01 (d) and CLAUDE.md rule 7. Claude's defaults are D-130 items 17–27.
- **Specs:** 02 (GEN-01 (d), PRF-08, BAK-01, BAK-02, BAK-04, BAK-06, SET-20), 03 (SCR-08, SCR-12, SCR-26, the new SCR-32, §5), 05 (§6, §8, the new §8a), 06 (§2.2, §3, §5.4, §5.7), 09 (§2, §4 „v0.9.0"), 10 (D-130, log), 11, the README, CLAUDE.md rule 7.
- **Issues:** spec #333; tickets #334–#344; #330 triaged into v0.9.0 as a sub-issue (T3, no timer review). All `needs-triage` until the PO approves.
- **Plan:** `docs/plans/v0.9.0.md`.

### v0.8.0: released (2026-11-18)

- **PO approval (2026-11-18, in session 6):** the session found no reply on PR #328 after the rc.2 comment, stopped and asked. The PO answered „all approved, nothing fails": the whole checklist 09 §4 „v0.8.0" on rc.2. Recorded on PR #328 (comment 6972174736). The design review was approved on rc.1 (session 4, D-129).
- **Version:** 09 §4 ticked and `umamit.version=0.8.0` (versionCode 80099) in `c658d496`. CI run 44259893817 went green on attempt 2: the first „Checks" failed on Maven Central's HTTP 429 for a Robolectric jar.
- **Merge:** PR #328 merged into `main` with a merge commit (`60e07bbe`). CI run 44265347019 was green on it on the first attempt.
- **Accuracy on `60e07bbe` (09 §4a, API 36, 50 steps, full SHA):** standard (44265373167), alongside (44265378630) and two-brews (44265383876) all PASS on the first attempt.
- **Release:** run 44275090504 with `RELEASE_TOKEN` (D-118) passed on the first attempt.
  - [`v0.8.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.8.0) is built from `main` `60e07bbe`, not a pre-release, with the tag on the merge commit.
  - versionCode 80099, APK SHA-256 `b67a6901…2016`, the release digest of the downloaded APK.
  - `scripts/verify-release-apk.sh` on that APK: the signature is valid, the certificate `a500e2c6…2f67` matches `signing/release-cert-sha256.txt`, `com.umamit.app` 0.8.0 / 80099, not debuggable.
- **Issues:** #326 and #327 closed. #330 stays open for v0.9.0. #104 („Timer folgt dem hellen Design", `needs-triage`) is still open although the light timer shipped; closing it is the PO's call.
- **Branch:** the working branch `claude/inspiring-ritchie-8hfw00` was restarted from `main`. The session was assigned `claude/pensive-hypatia-xl1eq9` and did not use it.
- **Session 6 (Sonnet `medium` orchestrator, no subagents):** mostly waiting on CI, accuracy and the release run.

### v0.8.0: session 5, #331 and rc.2 published (2026-11-18)

- **Branch:** worked on `claude/inspiring-ritchie-8hfw00` (assigned: `claude/youthful-ride-nxyb6e`). The environment (mirror init script, `gw.sh`, Robolectric jars) was written again.
- **#331 (T3, Opus `medium` implementer):**
  - The failing test came first (`9017da60`, `PickerHandOverTest`; seen failing by the orchestrator), then the fix (`78ca92a8`, `16f41215`) and the docs (`e6aed920`: D-129 „Claude's details", 03 SCR-03).
  - The mechanism: a frozen frame of the sheet (`SheetHandOver` in `core:ui`). It is recorded synchronously in the tap into a `Picture` and drawn by the shell below the routes. The real sheet hands over its scrim and fades out over it in about four frames.
  - No change to the timer wiring, so there was no T4 read. A container restart mid-session lost only a wait.
- **rc:** `95da6ad5` (`0.8.0-rc.2`, versionCode 80002).
  - Timer tests 263 / 513 / 776; full local gate PASS.
  - CI run 44198004897 green on attempt 3. API 36 failed first in `ProcessDeathTest`'s two-brews case (the known flake), then in `TransitionJankTest.savedSheetInAndOut` on a slow emulator (the reference fade at ≈ 120 ms frames).
  - Accuracy standard, alongside and two-brews PASS (44201903920, 44201908972, 44201912905).
  - Release run 44211626516 needed one rerun for `JournalPerformanceTest`'s wall-clock budget. APK SHA-256 `b0396be0…7f79`.
  - The checklist line is with the PO in comment 6966407229 on PR #328, with a request to approve the whole checklist.

### v0.8.0: session 4, the PO's check of rc.1 (2026-11-17)

- **The PO's check:** the design review is approved as built, with Claude's four details. The checklist passed except one item.
- **The failing item, from the PO's screen recordings:**
  - Tapping a tea in *Tee wählen* removes the sheet within one frame, so Start shows bare for ≈ 150–200 ms before *Neue Session* fades in.
  - Frames at 30 fps showed that the longer view of Start in the same recording was the PO's own back tap and recent-row tap.
- **The rest of the PO's answers:** the late tea photos on launch are kept as they are; the session hands over.
- **Recorded:** D-129, 03 SCR-03 „Hand-over to the setup", a 09 §4 line for rc.2, ticket #331 (T3).
- **What did not happen:** no merge, no version change; PR #328 stays a draft. Worked on `claude/inspiring-ritchie-8hfw00` (assigned: `claude/sharp-einstein-te907a`).

### v0.8.0: build session 3, rc.1 published (2026-11-17)

- **The PO's answers (in the session, as options):** the sage alarm as rendered; the active-brew bar's alarm static sage in the light app (D-128 item 38); the five open build notes kept as built (item 39); after the reviews, an ink ring on the light timer's overtime dot (item 40, #329) and the tabs' ⋮ alert gate as a v0.9.0 ticket (item 41, #330). #324 closed.
- **#325 (T3, Opus `medium`):** the final look (`LightTimerLook` removed, `LightTimerDesignCheckTest` kept for the review renders), the cover in `UmamitRoot` and the system-bar icons in the app's theme, one-frame recolour on a theme change, the bar's static sage, the dark-timer pin removed and the light goldens re-recorded; no dark golden changed. 02 SET-05 and 03 §3.1 aligned (sheets `surfaceContainerLow`, as rendered and approved).
- **#326 reviews:** T3 milestone review (no blocker), T4 timer review (no rule-8 risk), and, because `/review-animations` is user-invoke only, an ad-hoc T2 motion review at the PO's pick. One T3 fix round: taste resets name their axes, an unreadable tea falls back to the snapshot category's set, the tea editor's Sorte round trip, the radar's axis check, the refresh status without an empty exit frame and cleared on leaving the settings (SET-23), a golden renamed. Then #329 (T3).
- **rc:** `9c0e4225` (`0.8.0-rc.1`, versionCode 80001). Full local gate PASS; timer tests 263 / 513 / 776 (21 skipped: 20 record-only design-check renders, 1 earlier); CI green (API 36 needed one rerun each time: Maven Central once, `TransitionJankTest` „Start is not shown" once); accuracy standard, alongside and two-brews PASS (runs 44153741187, 44153745560, 44153749225). Design review in `docs/design/review/v0.8.0/`.

### v0.8.0: build session 2 (2026-11-17)

- **Branch:** worked directly on `claude/inspiring-ritchie-8hfw00` (PR #328); the session's assigned branch `claude/hopeful-feynman-nizldo` was not used.
- **Environment:** the mirror init script and `gw.sh` written again in the scratchpad, and Robolectric's SDK 35/36 jars pre-fetched and checked against the sha512. The wrapper now fetches Gradle 9.8.2.
- **Merged on #328, each verified by the orchestrator** (the diff, the cross-module check, the empty timer diff where the plan asks for it, and `feature:brew`'s tests and goldens after a shared-module change):
  - #319 (T2, `6ee8d0f0`): progress and result in *Katalog*. The formatter composables are in `core:ui`, which now depends on `core:data`; the `data_catalog_*` words stay in `core:data`, which the notifications share.
  - #320 (T3, `157dd673`): umamit.db v9 and `TasteSet`; `9.json` is Room-generated and `check-frozen-schemas.sh` passes.
  - #313 (T3, `752111b1`): `firstOrNull` guard. The test deletes through `FakeTeaArchive` (`teaId` null, as on Room); it was red before the guard and green after.
  - #321 (T2, `3d1ef4ba`): the sets and `TasteInfo` on the session page, the tea detail and the editor.
  - #322 (T3, `b465d7a5`): the timer's taste sheet per set with `TasteInfo`, the alert gate with the info unfolded, two brews with two sets, and the leftover „Sitzung" / *Brühen anpassen* KDoc. Timer tests 263 / 513 / 765 (25 skipped).
- **#325 step 1** (T3, `3cfa5870`): the light timer's sage alarm. `TimerTheme` provides `LightTimerAlertColors` in light only; no existing golden changed and `dark-alarm.png` is byte-identical. Timer tests 263 / 513 / 770 (29 skipped). The implementer stopped on the stated swing: it measures 1.734:1, not 1.72 (and matcha measures 2.975, not 2.95). The colours stay; the figures are corrected in 03 §3.1 and D-128 item 36 (`a5cb7006`). Renders posted on #324 for the PO's confirmation, with the active-brew bar's alarm in the light app (unchanged matcha).
- **CI:** green on `752111b1` (run 44109846265) and fully green on `3cfa5870` (run 44127033591, both emulator jobs).

### v0.8.0: build session 1 (2026-11-17)

- **The PO approved** spec #315, tickets #316–#327 and #313, and `docs/plans/v0.8.0.md` at the session's start; D-128 *Accepted*. Tickets relabelled (`ready-for-agent`, #324 `ready-for-human`); draft milestone PR #328 from `claude/inspiring-ritchie-8hfw00` (the assigned branch, fast-forwarded to `claude/magical-heisenberg-qev7fe`).
- **Environment:** Maven Central answered 429 again, so the mirror init script was written anew (scratchpad, passed by `gw.sh`) and Robolectric's SDK 35/36 jars were pre-fetched and checked against the sha512. The first baseline failed 103 Robolectric tests for want of them.
- **Merged on #328:** #317 (T3, `77a5691b`), #318 (T1, `bdd53797`), #316 (T2, `503a3092`) with its follow-up for a `feature:brew` test and 4 goldens on the shared stock-sheet text (T3, `79d3f557`), and #323 (T3, `162aee5f`). Each was verified by the orchestrator: the diff, the timer tests (263 / 513 / 734, 25 skipped), the cross-module check, the brew goldens with the dark timer unchanged, and lint.
- **The light timer's renders** went to #324 for the PO: 8 states light and dark, the band (ink on washi / plain) and the fill (today / deeper), with the contrast values below 3:1.
- **The PO's design check #324 (end of the session, as options):** band A13 in ink, fill as today, everything else as rendered, and a sage alarm (`#AEBF8D`) for the light timer only, still pulsing washi ↔ sage. Recorded as D-128 items 34–37, in 03 §3.1 and in #325's body.
- **CI:** one API 36 `TransitionJankTest` failure on `bdd53797` („Start is not shown"), taken as the known jank flake; CI fully green on `79d3f557` (run 44085961534).

### v0.8.0: specified, waiting for the PO's approval (2026-11-17)

- **The PO's nine points after `v0.7.2`:** „Session starten", „Session" for „Sitzung", an info section on the taste sliders, sliders for teas that are not green, shared recipes, the Statistik ⋮ menu opening at the left, refresh progress in the settings, a light timer, export/import.
- **Grilling (Opus `medium` session, Q1–Q31 as options in nine rounds), D-128:** two releases (v0.8.0: points 1–4, 6–8 and #313; v0.9.0: points 5 and 9); 1.0 only on the PO's go. During the round the PO widened point 2: „brühen" becomes „aufgießen" / „Aufguss", and „Session" where a whole session is meant; the setup is titled „Neue Session" / „Session anpassen".
- **Written:** D-128; the specs' German copy swept (02, 03, 04, 05, 11, 12); the v0.8.0 sections in 02, 03, 05, 08 (§4a), 09, 11 and the README; spec issue #315 with tickets #316–#327; #313 triaged (not reachable today, fixed anyway) and attached to #315; `docs/plans/v0.8.0.md`. v0.9.0 is recorded as planned (D-128 Q4–Q11, BAK-01/02/04/06, PRF-08).
- **Housekeeping:** the docs PR #314 (the v0.7.2 handover, `2fd0a01a`) merged into `main` (`76e0a356`). The session's assigned branch was `claude/magical-heisenberg-qev7fe`, so the v0.8.0 docs are there, not on `claude/wonderful-davinci-fu8tqi`.
- **No code was written.** Usage: not measured (the session had no usage readout); no subagents.

### v0.7.2: released (2026-11-17)

- **PO approval (2026-11-17, in the session and on #312):** the checklist 09 §4 „v0.7.2" was ticked on the rc.1 comment of #312 (edited 06:43 UTC); the orchestrator found no reply with words, asked, and the PO answered „Yes, approved" (checklist and design review; the unchanged goldens stand in, plan §„Release"). No rc.2.
- **Version:** `umamit.version=0.7.2` (versionCode 70299) in `a536b004`; CI green on the PR head (run 44036384159). Merged as PR #312 into `main` with a merge commit (`c5f19e8e`); CI green on it (run 44039016121).
- **Accuracy on `c5f19e8e` (09 §4a, API 36, 50 steps, full SHA), all PASS on the first attempt:** standard (run 44039026445), alongside (run 44039030588), two-brews (run 44039033997).
- **Release:** run 44048604027 with `RELEASE_TOKEN` (D-118), [`v0.7.2`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.2) from `main` `c5f19e8e` (not a pre-release, tag on the merge commit; versionCode 70299, APK SHA-256 `efc2cca6…20fa` = the release digest of the downloaded APK). `scripts/verify-release-apk.sh` on that APK: signature valid, certificate `a500e2c6…2f67` = `signing/release-cert-sha256.txt`, `com.umamit.app` 0.7.2 / 70299, not debuggable.
- #296–#310 closed. Working branch restarted from `main`.
- **Session 14 usage (Sonnet `medium` orchestrator, no subagents):** about 60 tool calls, mostly waiting on CI, accuracy and the release run; not measured from the transcript (the session had no usage readout).

### v0.7.2: #309 done, rc.1 published (2026-11-17)

- **Session 13 (Opus `medium` orchestrator, no subagents):** `umamit.version=0.7.2-rc.1` (`a8f78d4f`, versionCode 70201); the proof on that commit, recorded on #312: goldens and `core/database/schemas` vs `main` empty (1085 tracked goldens, so the pathspec matched), DataStore keys = the #297 baseline (36 keys, `schemaVersion` 3), `check-frozen-schemas.sh` (179 files), `SchemaVersionsTest` (3 tests), `checkManifestPermissions`, `verifyRoborazziDebug`, the full local gate PASS, the 04 §12 timer tests 263 / 512 / 707 (1 skipped) counted from the result XMLs.
  - **CI** green on `a8f78d4f` (run 44020292963, both emulator jobs). **Accuracy on `a8f78d4f` (09 §4a, API 36, 50 steps), all PASS on the first attempt:** standard (run 44020476878), alongside (run 44020480260), two-brews (run 44020482994).
  - **`v0.7.2-rc.1`** published by `release.yml` run 44028380084 (pre-release, tag on `a8f78d4f`). The orchestrator downloaded the APK and ran `scripts/verify-release-apk.sh` on it: APK SHA-256 `dd1cbdd8…aab1c` as in the notes, certificate `a500e2c6…2f67` = `signing/release-cert-sha256.txt`, 0.7.2-rc.1 / 70201, not debuggable. Checklist 09 §4 „v0.7.2" on #312, push notification sent.
  - **Environment:** the mirror init script written again as PROJECT_STATE describes; it worked first time. Maven Central answered 429 on the SDK 36 Robolectric jar's `.sha512` once and served it on retry.
  - **Usage (from the transcript, before the final handover turn):** orchestrator 56 requests, 8.0 M cache read, 0.13 M cache write, 25 k output.

### v0.7.2: milestone and timer review done (2026-11-16)

- **Session 12 (Opus `medium` orchestrator):** the T3 milestone review (Opus `medium`, `git diff main`) and the T4 timer review (Opus `high`, `d9e79cbe..c0116fc7` plus `d2eee004`) found **no Block**. The orchestrator reread the two untested paths (D-125 `SettingsNav.alertKey`, TMR-A-07 `slotSession` on a failed read) and both hold.
  - **Fixes, one commit each:** R-1 – R-4 dead imports (T1, Sonnet `low`), R-5/R-6 formatting (T1), T-4 stale `AlertPlayer` KDoc in `core:timer`/`core:timer-engine` (T3, Opus `medium`, comments only). The doc pass `f167499d` covers the stale `ControllerBrewTimer`/`finishActive` prose (10, evidence).
  - **Verified:** the diffs read (imports and comments only, besides R-5/R-6), `spotlessCheck detekt :app:assembleDebug :app:assembleDebugAndroidTest` and the touched modules' test compiles, the 04 §12 timer tests 263 / 512 / 707 (1 skipped) on `f167499d` and `1da1e5fb`, no golden/schema diff, no assert line removed. CI green on `f167499d` (43946236160) and on `1da1e5fb` (43953846325, both emulator jobs).
  - **Notes left open (no change):** R-7, R-8, T-1, T-2, T-3 (PROJECT_STATE „Reviews").
  - **Usage (from the transcripts, before the final handover turn):** orchestrator 59 requests, 8.4 M cache read, 0.11 M cache write, 25 k output; T3 reviewer 71 requests, 15.0 M / 0.30 M / 31 k; T4 reviewer 64 requests, 10.0 M / 0.22 M / 31 k; two fix implementers 37 requests, 2.8 M / 0.41 M / 19 k.

### v0.7.2: #308 done (2026-11-16)

- **Execution part 4, session 11 (Opus `medium` orchestrator, `/implement-spec #296`):**
  - **What:** #308's alert-path group as seven T3 implementers (Opus `medium`), at most two at a time on disjoint files: TMR-A-14 `d7acfb17`, TMR-A-01 `568446cf`, TMR-A-11 `dfb1fa31`, TMR-A-12 `778e282a`, TMR-A-13 `d3fee39f`, D-126 step 1 (DAT-A-14) `bb08bebc`, step 2 (TMR-A-06) `c0116fc7`.
  - **How it was verified:**
    - the orchestrator read each diff and ran the 04 §12 timer tests on four landed heads, `568446cf` (14 + 01), `778e282a` (11 + 12), `bb08bebc` (13 + DAT-A-14) and `c0116fc7`, while each implementer ran its own scoped tests (263 / 512 / 707, 1 skipped, unchanged from the baseline; `core:data` 656);
    - DAT-A-14 got a mechanical line-multiset check (the non-blank, non-import lines are equal);
    - `ActiveBrewSlotStore` was read first and found to be a pure forward;
    - pushes went in three groups (A: 14, 01, 11; B: 12, 13; C: D-126), each waiting for a green CI run (43926091785, 43929989903, 43933873414; emulator jobs included).
  - **Proof:** over the group's diff, no golden, schema, `strings.xml`, DataStore or 06 change. Only `AlertArbitrationTest`, `TimerSlotsTest` and `TimerTestActivity` changed among the tests (TMR-A-14, PO-approved), and no `@Test`, `@Ignore` or `assume` line went.
  - **Deviations, no behaviour change:**
    - TMR-A-14's non-null `settle` asserts compare `SlotEvent(QUICK, …)`;
    - TMR-A-11 is longer (+44/−28, ktlint);
    - TMR-A-12 left `PersistedTimer.guarded` (it returns a value);
    - TMR-A-13's `handOver` uses one shared scope of the same kind.
  - **Usage:** orchestrator 115 requests, 21.7 M cache read, 0.19 M cache write, 48 k output; seven implementers 77 requests, 5.7 M cache read, 0.93 M cache write, 39 k output (from the transcripts, before the final handover turn).
- **Execution part 3, session 10 (#308 groups 1 and 2, 20 steps; CI green on the code head `ec0d7001`, run 43912284383, both emulator jobs included):**
  - **PO answers (2026-11-16, as options, recorded in #308):** one `UnlockedDevice.kt` in `core/timer/src/sharedAndroidTest/`, a source folder that `app` and `core:timer` both add (no dependency); UI-A-07's fix action `rememberReliabilityFixAction` in `core:ui`, generic over the check type (it takes the notification check as a value, not a predicate: the permission dialog's „denied for good" path needs it without a check in hand); DAT-A-03 through a public test helper `FakeCollection.deleteTeaRows` (sessions keep their tea id, as the old fake did).
  - **Found, out of scope:** #313 (`needs-triage`): `BrewSetupViewModel` throws when a session's tea id is null after a real delete.
  - **Build-file pattern:** `android { sourceSets }` throws a ClassCastException in these modules; use `extensions.configure<LibraryExtension|ApplicationExtension> { sourceSets.getByName(…).kotlin.directories.add(…) }` (D-127's `src/sharedTest` too) and add the folder to `detekt { source.from(…) }`.
  - **Helpers changed shape:** `rememberExpandedSheetState` has `saveable` (the steps sheet); `core:ui` `Stepper` has `valueMinWidth`; `StartQuestions` (`core:timer` `controller/`) holds the refused-start question for setup, quick timer and Start; `ReliabilityChecks` (`core:timer` `reliability/`).
  - **For the T4 timer review (also in #308):** the D-125 `SettingsNav.alertKey` path no test covers; TMR-A-07's `slotSession` (no test fails `getSession`: the seal's silent fallback and the stock's logged retry hold by construction); TMR-A-04's „any alert closes the question" (`openedIn` was null at every site).
  - **CI:** `TransitionJankTest.savedSheetInAndOut` (API 36) failed once on `e0057946` and passed on rerun (comment on #312).
  - **Stale prose:** `docs/specs/10-open-questions-and-decisions.md` (≈ l. 6898, 7154) and `docs/plans/v0.7.2-evidence.md:356` still say `ControllerBrewTimer` implements both seams; fix in the milestone review's doc pass.
  - **Usage (session 10):** orchestrator ≈ 105 requests, 20.5 M cache read, 0.21 M cache write, 46 k output; four implementers 317 requests, 44.0 M cache read, 2.42 M cache write, 167 k output (from the transcripts).

### v0.7.2: #307 done (2026-11-16)

- **Execution part 2 (Opus `medium` orchestrator, `/implement-spec #296`):** #307's 12 steps and one follow-up fix on #312, by nine T3 implementers (Opus `medium`, at most two at a time; D-123 beside D-125 step 4, since D-123 and D-124 share `CatalogScreen.kt`). D-123 `teaTypePlaceholderColor`, `openInBrowser`, `PastDatePickerDialog` in `core:ui`; D-124 `TeaLinkHolder` (`TooManyFunctions` suppression kept: 12 functions); D-125 `NavOnTop.kt` (eight sites, `UmamitApp.kt:588` untouched), `settingsRoutes<S>` with `SettingsNav` (two deviations from the design: an `openVesselResumedOnly` flag, because `resumedOnly` is composable and needs the destination's lifecycle; `SettingsNav` sits in `SettingsScreens.kt` for Detekt's `MatchingDeclarationName`), tab then root graph; a follow-up fix makes `SettingsNav.alertKey` a `() -> String?` read in each screen's content, as the root's destinations read `alert` before step 6 (found in the orchestrator's review; no test covers a settings dialog above the timer following the current alert, so the milestone and timer reviews should look at it); D-121 `CatalogRowMappers.kt`, `CatalogRefreshFailureText.kt`, `CatalogRefreshRun.kt` (`MAX_LISTED` moved with the run; moved blocks diffed against the old file); D-122 `FakeShop.kt` and `catalogRefresher(…)`, 13 of 14 classes moved (`CatalogRefreshRunnerTest` keeps its latch dispatcher), `:core:data` 656 tests before and after, no assertion line changed. Proof: fence and fenced `app`/data files untouched, no golden, schema, string, DataStore key or 06 change. CI runs on the PR are cancelled by each new push, so only the head is gated. Usage: orchestrator 110 requests, 23.0 M cache read, 0.22 M cache write, 52 k output; nine implementers 208 requests, 20.7 M cache read, 1.24 M cache write, 120 k output (summed from the transcripts, before the final handover turn).

### v0.7.2: #305 and #306 done (2026-11-16)

- **Execution part 1 (Opus `medium` orchestrator, `/implement-spec #296`):** #305 (5 deletions; DAT-A-03 moved to #308, DAT-A-05's DAO query too) and #306 (all 26 simplifications, in three batches: low data/catalog T3, UI T2, medium/high data/catalog T3) on #312, head `2cab854f`. Fenced remainders of DAT-A-09, -11, -15 routed to #308. Proof after each batch: fence empty, golden digest `cbe41c94…` unchanged, schemas frozen, DataStore unchanged, `strings.xml` lost only the 19 unused refresh copies (checked byte-identical), no test weakened. Usage: 112 orchestrator requests, 22.0 M cache read, 65 k output; implementers about 0.89 M tokens.

### v0.7.2: restructuring design done (2026-11-16)

- **Design (#304, Fable 5.2 `medium`, in the session):** `docs/plans/v0.7.2-restructuring.md` for D-121 – D-127: target files and types, steps in a build-green order (all T3), the module rules and 06 before and after (no section changes), the risk per decision. The PO approved variant A of each in eight questions as options, recorded in the decisions. Found against the triage: `Run` in `CatalogRefresher.kt` is an inner class (about 15 changed lines, not 5); the two settings registrations differ in gating, `launchSingleTop` and the settings route type. Steps written into #307 (12) and #308 (3). Usage: 28 requests, 3.9 M cache read, 45 k output. No code change.

### v0.7.2: triage done (2026-11-16)

- **Triage (#303, Opus `medium`, in the session):** the PO picked all 60 audit findings in 15 multi-select questions over the four groups (overlaps as one choice each; DAT-A-04 follow-up: `VesselDao.delete` goes too, a raw `DELETE` in `SessionEditorTest`). „pick?" filled in the four audit files; the seven restructurings are D-121 – D-127 (#304); #305 (6 IDs), #306 (26), #307 (5), #308 (21, the fence) list them. Usage: 25 requests, 3.39 M cache read, 30 k output. No code change.

### v0.7.2: UI audit done (2026-11-16)

- **UI audit (#302, Opus `medium`, in the session, full scope):** `docs/plans/v0.7.2-audit-ui.md`, 15 findings (UI-A-01 … UI-A-15: the settings routes registered twice, two intent-request holders and their consumers, the undo snackbar three times, the past-only date picker three times (widens CAT-A-08), the leaf/water/temperature input rules of two editors, the session-zone fallback six times, the reliability-check wiring twice, the pill stepper twice, small copies; UI-A-15 is the group's share of TMR-A-03). Scope 31 665 main lines plus the six `feature:brew` screen files for #308; about 14 900 + 2 040 read in full (scope table). No dead code found through R8. Correction to the data audit's `WallClock.today()` note (one of the four places uses the session's zone). No code change.

### v0.7.2: catalog audit done (2026-11-16)

- **Catalog audit (#298, Opus `medium`, in the session):** `docs/plans/v0.7.2-audit-catalog.md`, 15 findings (CAT-A-01 … CAT-A-15: duplicated refresh strings and mappings, the image job in two near-twins and a variant, the allow-list URL check four times, page-to-row mapping twice, `CatalogRefresher.kt` split, cover copy twice, the refresh tests' fake shop eight times, three cross-feature UI helpers, the green-type tables twice in the parser, five small ones). Scope 13 668 main lines (the three modules plus the catalog parts of `core:data`/`core:database`), read in full in part (scope table). No dead code found through R8. No code change.
- **#299 (PO):** data, timer-path and UI audits all kept as planned (D-119 build note „Remaining audits").

### v0.7.2: step 1 done (2026-11-15)

- **Approval:** the PO approved plan, spec #296 and tickets #297–#310 with Claude's two proposals (audits in the session itself; unchanged goldens instead of a design review). Tickets relabelled: #299, #303 `ready-for-human`, the other twelve `ready-for-agent`.
- **Step 1 (#297, Sonnet `medium`):** `docs/plans/v0.7.2-evidence.md` on `main` `f0ffaf1a`: R8 usage (noisy; candidates only), Lint unused resources/ids **0**, Dependency Analysis (61 unused, mostly convention-plugin entries; plugin outside its AGP range, needed build-file lines instead of an init script), 30 largest files, duplicated test fixtures, baseline (1 085 goldens with per-module digests, schemas 1–8 and 1, 36 settings keys, `schemaVersion` 3). Nothing of the tooling committed. Draft PR #312.

### v0.7.2: specified (2026-11-15)

- **Grilling (one round, Q1–Q4):** D-120: Fable 5.2 at `medium` (Claude had recommended `high`), findings files per group, the Dependency Analysis plugin from a local init script, the tier label T4-F.
- **Written:** `docs/plans/v0.7.2.md`; spec #296 with tickets #297–#310 (`needs-triage`); 09 §2 row and §4 „v0.7.2"; 12 §2 note and §3.2 „v0.7.2". Waiting for the PO's approval; no step 1, audit or code change yet.

### v0.7.1: released (2026-11-15)

- **PO approval (2026-11-15, in the session prompt; D-117 build note „Approval (PO, 2026-11-15, rc.2 check)"):** asked for the result on `v0.7.1-rc.2`, the PO answered „all approved": every rc.2 item of checklist 09 §4 „v0.7.1" passed. No rc.3. The design review's details 1–8 (with rc.1) were not corrected and stand as built.
- **Version:** `umamit.version=0.7.1` (versionCode 70199) in `4b9cfaad`; CI green on the PR head `f2b91cf0` (run 43749314657, both emulator jobs). No local gate on the bump (CI is the full gate).
- PR #290 merged into `main` with a merge commit (`6192bff5`); CI green on it (run 43751545411, attempt 2; attempt 1 failed in `ProcessDeathTest`'s two-brews test on its single diagnostics-log read, the known flake, D-112 Q19).
- **Accuracy on `6192bff5` (09 §4a, API 36, 50 steps), all PASS:** standard (run 43751551379), alongside (run 43751554724), two-brews (run 43751557905).
- **Release:** run 43758738993 with `RELEASE_TOKEN` (D-118), [`v0.7.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.1) from `main` `6192bff5` (versionCode 70199, APK SHA-256 `0e3b2970…3a85`), certificate `a500e2c6…2f67` = `signing/release-cert-sha256.txt` (checked by Claude with `apksigner` on the published APK).
- #289 and #291–#294 closed. Working branch restarted from `main`.
- **v0.7.2 grilling (one round, Q1–Q4):** D-119: the full four-step cleanup review, its own release `v0.7.2`, Fable 5.2 for the timer and data audits and the restructuring design (Opus `medium` for the other audits and timer/data edits, Sonnet for mechanical edits), the catalog group first. Next: the v0.7.2 specify session.

### v0.7.1: the PO's rc.1 check, the lock screen, rc.2 (2026-11-15)

- **PO's rc.1 check:** all approved except the lock-screen transitions (*Abgegossen* → the lock screen, the timer over the keyguard closed: janky). The `/review-animations` tickets #291/#292 approved.
- **Audit and decision:** lock-screen audit A-17–A-20 (`docs/plans/v0.7.1-motion-audit.md` §8); one grilling round: D-117 build note „Lock screen" (keyguard only when leaving the timer while locked; A-19/A-20 kept; report-only measurement). Tickets #293 (T3), #294 (T2).
- **Build:** #291, #292, #293, #294; T3 review, T4 timer-path review (B1 and N1–N5 fixed), T4 re-review (one race fixed). Gates: GATE PASS, timer tests, CI green on `e57861e0`, accuracy standard/alongside/two-brews PASS on `3c1bf97b`.
- **Release:** publishing with `GITHUB_TOKEN` got HTTP 403 from 14:21 UTC on (eight attempts, settings fine, cause unknown); D-118: the PO's fine-grained `RELEASE_TOKEN` (Contents + Workflows) for the publish step. `v0.7.1-rc.2` (70102) published from `e57861e0` by run 43744506758 at 18:27 UTC; APK SHA-256 `84e4a437…527d`, certificate verified. After the rc: the #294 measurement fix (`d52156d4`, tests and CI only).

### v0.7.0: released (2026-11-14)

- **PO approval (2026-11-14, in the session; D-115 „Approval"):** asked for the result on `v0.7.0-rc.3`, the PO answered „All changes approved": every rc.3 item of checklist 09 §4 „v0.7.0" passed, and Claude's rc.3 defaults 2–5 stand as built. No rc.4.
- **Version:** `umamit.version=0.7.0` (versionCode 70099) in `b3897df9`; CI green on it (run 43595619900, incl. both emulator jobs). No local gate on the bump (CI is the full gate; the rc.3 code had GATE PASS).
- PR #266 merged into `main` with a merge commit (`f3582466`); CI green on it (run 43597854068).
- **Accuracy on `f3582466` (09 §4a, API 36, 50 steps), all PASS:** standard (run 43597857819), two-brews (run 43597862600), alongside (run 43597860120, attempt 2; attempt 1 stopped at the test's setup guard, 14 s to screen-off on a slow emulator, before any step was measured).
- **Release:** run 43608440667, [`v0.7.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.0) from `main` `f3582466` (versionCode 70099, APK SHA-256 `dc0a09a5…9557`), certificate `a500e2c6…2f67` = `signing/release-cert-sha256.txt`.
- #255–#263 and #268–#277 closed by the merge, #264 after the release. Working branch restarted from `main`.

### v0.7.0: the PO's review of rc.1 and rc.2, rc.3 (2026-11-13/14)

- **PO review of rc.1:** ten points on the checklist and the design review, not an approval: what a „Tasse" is, a per-tea donut, a more compact and foldable Überblick, Bewertungen rethought, Verbrauch with the value drunk, a custom month range, separate tea and brewing ratings, the Knapp chip losing the grams, transitions that are not smooth (worst after unlocking into the app).
- **Decisions:** D-115 Amendment 1 (R1–R16) for rc.2; D-116 plans v0.7.1 (Tee + Zubereitung ratings per session, old ratings copied to both; a transition audit).
- **Build:** #268 cups by volume + *Tassengröße* (SET-34), #269 „14 g · knapp", #270 stats data (custom range, Tees donut, GETRUNKEN, Tee-Rangliste, Neu entdeckt, removals), #271 shell (Monate sheet, folding, half-height charts, label fade), #272 tab contents, #273 review, gate and rc.2. Renders in `docs/design/review/v0.7.0/`.
- **PO review of rc.2 (2026-11-14):** everything approved except „Tassen": the PO's cups differ in size, so a cup count is not informative. Grilled V1–V7, then V8–V9 on the rc.3 renders: D-115 Amendment 2: volume (ml/l) instead of cups everywhere, „€ pro Liter", the chart TEE, *Tassengröße* removed, „12,4 Liter Tee" with the unit in the body font.
- **Build rc.3:** #274 volume rule, tea detail € pro Liter, *Tassengröße* removed; #275 Statistik in volume; #277 „Liter" in full; #276 gate (GATE PASS), renders, `v0.7.0-rc.3` (70003) from `73907f6d`. No T3 review for the rc.3 diff (a unit change within reviewed code; the next milestone review is v0.7.1's).

### v0.6.1: released (2026-11-13)

- **PO approval (2026-11-13, in the session; D-114 „Approval"):** asked for the result on `v0.6.1-rc.1`, the PO answered „Approve everything": every item of checklist 09 §4 „v0.6.1" passed, and the seven design-review points of `docs/design/review/v0.6.1/README.md` stand as built. No rc.2.
- **Version:** `umamit.version=0.6.1` (versionCode 60199) in `87dc394e`; the full local gate passed on it; CI green (run 43391376208; its API 35 emulator job first failed to download `junit:junit:4.13.2` and more from Maven Central and passed on the rerun).
- **Accuracy on `87dc394e` (09 §4a, API 36, 50 steps), all PASS:** standard (run 43400138510), alongside (run 43400142129), two-brews (run 43400146292). A first dispatch with the short SHA as `ref` failed at checkout (runs 43391392397, 43391396276, 43391400707); the workflow needs the full SHA. The merge commit has the same tree as `87dc394e` (`main` had only the docs-only #253, already in the branch).
- PR #252 merged into `main` with a merge commit (`22b84788`); CI green on it (run 43409588294).
- **Release:** run 43412555270, [`v0.6.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.6.1) from `main` `22b84788` (versionCode 60199, APK SHA-256 `221d2e8a…b984`), certificate `a500e2c6…2f67` = `signing/release-cert-sha256.txt`. A first dispatch (run 43409593705) stopped at the release's CI check because CI on the merge commit had not finished yet.
- #240–#251 closed by the merge. Working branch restarted from `main`.
- **Environment that day:** the local init script (Google's Maven Central mirror) and Robolectric's two runtime jars from the mirror, checked against the sha512, made the local gate run.

### v0.6.1: build, rc.1 and the state before the final (2026-11-12)

Moved from `PROJECT_STATE.md` as it stood before the final release.

- **Scope (D-114):** taste and a note per infusion, entered on the timer screen for the infusion ended last and on the session page (chips *Alle · 1. · 2. …*; umamit.db v7 with a tested migration; the tea's radar averages every infusion); GESCHMACK folds; quick-timer chips 1:00 · 2:00 · 3:00 · 5:00 · 8:00 · 10:00 · 15:00; a full-screen photo viewer; ⋮ *Tee ansehen* / *Im Katalog ansehen*; the Katalog opens with every kind, grouped by kind; „N Tees"; *Darstellung* › *Design*.
- **Timer code:** #241 and #244 change `feature:brew`: T3+ implementers, a T4 timer review, the 04 §12 unit tests, and all three accuracy variants on the rc and the final (09 §4a). `core:timer` / `core:timer-engine` stay untouched; if a ticket needs them, stop and ask the PO.
- **Order and tiers:** the plan's task graph (PO-approved): #242 + #241, #243 + #247, #244 + #245, #246 + #249, #248; then #250 (reviews, accuracy, rc.1, checklist 09 §4 „v0.6.1", design review in `docs/design/review/v0.6.1/`) and #251 (merge, final).
- **Milestone PR:** #252 (draft).
- **Then Statistik (v0.7.0, M4 part 2; not yet specified):** its own specify session. Starting point, not yet confirmed by the PO: INS-01–INS-07 (02 §12), SCR-18, the A9 empty state. With D-114 the taste data is per infusion.
- **Known from v0.6.0, not blocking:** #231's „smooth with 10 000 sessions" is shown by JVM timing only. A tab that comes back to the Journal can show the earlier list for a frame until the next build.
- **Config housekeeping (2026-11-12, #253 on `main`):** a prompt audit of the Claude Code config pointed `CLAUDE.md` at the
  per-release plans and plainly restated `emil-design-eng`'s review format (a local edit; its hash no longer
  matches `skills-lock.json`, see `CLAUDE.md`). No app change.
- **Environment:** Maven Central answered HTTP 429 on 2026-11-11 and 2026-11-12; a local Gradle
  init script (`~/.gradle/init.d`, not in the repository) pointed
  `repo.maven.apache.org` at Google's Maven Central mirror
  (`maven-central.storage-download.googleapis.com`), which the „UmamiT"
  environment reaches. A new container needs it again if 429 persists:
  set the URL of every `repo.maven.apache.org` repository in
  `settingsEvaluated` (plugin and dependency repositories) and
  `allprojects`. One Gradle build at a time (`flock` as in
  `docs/plans/v0.4.0-resume.md` §3).
  Robolectric's runtime jars (SDK 35 `15-robolectric-13954326-i7`, SDK 36
  `16-robolectric-13921718-i7`) are fetched from Maven Central at test time: pre-fetch both from the mirror
  as `docs/plans/v0.4.0-resume.md` §3 shows for SDK 35.

### v0.6.0: released (2026-11-12)

- **PO approval (2026-11-12, in the session; D-113, PR #235 comment):** the on-device checklist on `v0.6.0-rc.1`: „Yes, all twelve passed"; the design review with the slim pin slider (#237), judged from its renders: „Approved". No rc.2 (D-113, answer 7).
- **Version:** `umamit.version=0.6.0` (versionCode 60099) in `d10e7ef0`; the full local gate passed on it; CI green (run 43179353777), and on the head before it, `8198d442` (run 43177366250). `ProcessDeathTest` passed in every run of the day.
- **Accuracy on `d10e7ef0` (09 §4a, API 36, 50 steps), all PASS:** standard (pre-alert p95 49 / max 231 ms, alert 31 / 232 ms; run 43183870952); alongside (brew pre-alert 13 / 156, alert 32 / 41 ms; quick timer pre-alert 10 / 283, alert 10 / 106 ms; run 43183875784); two-brews (brew 1 pre-alert 43 / 141, alert 16 / 200 ms; brew 2 pre-alert 13 / 122 ms; brew 2's held alert held 7954 / 8045 ms, release → sound p95 12 / max 15 ms, not gated; run 43183880162). The runs were on the branch tip before the merge; the merge commit has the same tree (`main` had no other commits).
- **Pre-merge audit (read-only, four auditors):** no blocker. No file under `core/timer`, `core/timer-engine`, `feature/brew`, the manifests or the app's timer wiring changed against `main`; the schemas gained only `6.json`, identical to the one in `v0.6.0-rc.1`; no secrets and no Tee-Kontor content in the diff.
- PR #235 merged into `main` with a merge commit (`232c4b82`); CI green on it (run 43210759821).
- **Release:** run 43213268264, [`v0.6.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.6.0) from `main` `232c4b82` (versionCode 60099, APK SHA-256 `505fe673…28d3`), certificate `a500e2c6…2f67` = `signing/release-cert-sha256.txt`.
- #221–#233, #236 and #237 closed by the merge, #234 closed after the release. Working branch restarted from `main`.
- **Environment that day:** Maven Central answered HTTP 429 to the container again; the local init script of the section below (Google's Maven Central mirror) and Robolectric's two runtime jars fetched from the mirror, checked against Maven Central's sha512, made the local gate run.

### v0.6.0: specify, build, rc.1 and the feedback round (2026-11-11/12)

Moved from `PROJECT_STATE.md` as it stood before the final release. Spec #221,
plan `docs/plans/v0.6.0.md`, milestone PR **#235** from
`claude/wonderful-davinci-fu8tqi`.

- **Specify phase: done** (2026-11-11). The PO was grilled in five rounds
  (Q1–Q18, all as options); the answers are D-112. The PO's rule for the
  release: every tasting input is optional and skippable with as little
  friction as possible.
- **Scope:** rating, notes, taste sliders with radar, descriptors and session
  photos in place on the session page; five stars and both actions on the
  „Gespeichert" sheet; *Zeiten ins Rezept übernehmen* after deliberate
  changes only (SET-18 retired); the session editor; journal search with
  three filters and the A6 empty state; ratings, GESCHMACK and LETZTE
  SITZUNGEN on the tea screens; umamit.db v6. No timer code changes.
- **Specs written** (commit `b4d2aae7` and the plan commit): 02, 03, 05, 06,
  08, 09, 10 (D-112), 12, the specs README.
- **Tickets:** the PO approved the breakdown of thirteen (2026-11-11). One
  refinement after the approval: #232 is also blocked by #231 (it uses the
  Journal's tea-filter argument).
- **Review and rc phase (#233): done** (2026-11-11, one session).
  - **[`v0.6.0-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.6.0-rc.1)**
    is published (pre-release, versionCode 60001, from `74237151` on the PR
    branch; release run 43106966020; APK SHA-256 `7f9530bb…30a7`;
    certificate `a500e2c6…2f67` = `signing/release-cert-sha256.txt`). CI
    was green on that commit (run 43103655084: checks, build, emulator
    tests on API 35 and 36). No accuracy run for the rc: no timer code
    changed.
  - **CI on the way there.** Run 43094657346 (head `d902df8c`) failed in
    „Emulator tests (API 35)": the brew-and-quick-timer test of
    `ProcessDeathTest`
    (`aBrewStepAndAQuickTimerDueTogetherSurviveTwoKillsAndAlertTheBrewFirst`)
    saw the held quick-timer alert 2327 ms after *Abgegossen* (tolerance
    2 s). This is not the flake of D-112 Q19 (other method, API 35, a
    timing bound). Timer code is unchanged against `main`, GitHub's runners
    were degraded at that time (artifact uploads failed, and the next run,
    43096106697, hung in the queue for an hour), and the test passed on
    both API levels in run 43103655084. Nothing was changed; the PO knows.
    If it shows again: rerun once; if it repeats, stop and ask the PO
    (timer).
  - **Milestone review (T3):** no blocker. **Motion review (T2):**
    `docs/plans/v0.6.0-motion-review.md`. What was fixed and what was left
    is D-112's „Review and fix round".
  - **Fix round, built at T2:** #236 (accepting the recipe offer keeps the
    recipe's step notes by position, PRF-07 AC5); round A (the session page
    reads one session, the session delete is uncancellable, unknown
    descriptor keys are refused, a dead read removed); round B (the stars
    keep the tapped value, the journal list is built off the main thread,
    the radar eases in, the dividers' fade, the average's rounding).
  - **One failure after the fix round:** the first full gate failed once in
    `SessionPageFlowTest`: the app's tests clicked a journal row before the
    off-thread list existed. A sweep found the same race in
    `TeaDetailSessionsRouteTest` and `ShellNavigationTest`; fixed at T3,
    test code only (`app/src/test/.../JournalWaits.kt`), and reviewed. The
    full gate then passed on `efc061b6`.
  - **No timer code changed:** the diff against `main` is still empty for
    `core/timer`, `core/timer-engine`, `feature/brew` and the app's timer
    routes; `core/database/schemas` gained only `6.json`.
  - **Renders:** `docs/design/review/v0.6.0/` (147, light, dark and font
    2.0, placeholder images only) with a README that lists the points to
    judge.
- **Feedback round after rc.1 (D-113, #237): built and approved**
  (2026-11-12, same session).
  - **The PO's approval (2026-11-12, in the session):** the slim slider's
    renders: „Approved"; the on-device checklist of rc.1: „Yes, all twelve
    passed". The checklist and the design review of v0.6.0 are approved
    (D-113); nothing waits on the PO.
  - **The PO's answer on rc.1:** „All looks good except the handle of the
    taste slider is way to big and bulky. This needs to look more classy and
    elegant. Everything else looks fine and works as expected." So the
    on-device checklist and the design review are approved except for the
    slider's handle.
  - **D-113 (seven answers, as options):** the slim pin (option B of four
    drawn designs) for the taste sliders and for the settings'
    *Lautstärke*; TalkBack on an unset slider, the single-axis unset and
    the radar's position stay as built; 03 notes the fold's 100 ms fade
    with reduce motion; **no rc.2**: the PO approves the slider from its
    renders, then the merge and the final follow.
  - **#237 (T2), merged:** `SlimSlider` in `core:designsystem`, used by
    `TasteSlider` and `VolumeRow`; 44 goldens changed or new (8 taste, 32
    settings, 4 component). A read-only review measured the goldens against
    03 §3.8 and found nothing to fix.
  - **A second test flake of the off-thread journal list, fixed at T3:**
    `JournalSearchTest` failed once with a wrong-thread error while #237
    was built. In Compose UI tests the list's result was written on the
    worker thread (the app was never affected). The context of the list
    build is now injected (`JournalSources.listContext`); a test pins it.
  - **Verified:** the full local gate passed on `4a15f547`; CI is green on
    `284e83a2` (run 43174172386). No timer code changed.
  - **Renders for the PO:** `docs/design/review/v0.6.0/` („To judge now" in
    its README): the taste section, *Lautstärke*, the component sheet.
- **Known, not blocking:** #231's „smooth with 10 000 sessions" is shown by
  JVM timing only. A tab that comes back to the Journal can show the
  earlier list for a frame (a session just deleted, yesterday's day
  headings right after midnight) until the next build. The drag feel of
  the slim pin is first seen in the final (D-113, answer 7).
- **Environment:** Maven Central answered HTTP 429 all day; a local Gradle
  init script (`~/.gradle/init.d`, not in the repository) pointed
  `repo.maven.apache.org` at Google's Maven Central mirror
  (`maven-central.storage-download.googleapis.com`), which the „UmamiT"
  environment reaches. A new container needs it again if 429 persists:
  set the URL of every `repo.maven.apache.org` repository in
  `settingsEvaluated` (plugin and dependency repositories) and
  `allprojects`. One Gradle build at a time (`flock` as in
  `docs/plans/v0.4.0-resume.md` §3).

### v0.5.1: released (2026-11-10)

- **PO approval (2026-11-10, in the session):** „rc.2 works, the transition is clean now, approved" (on-device checklist including the Start hand-off of D-111, and the design review). The last checklist point was ticked in PR #219 and in #220.
- **Version:** `umamit.version=0.5.1` (versionCode 50199) in `5d461669`; CI green on it (run 42887719781).
- **Accuracy on `5d461669` (09 §4a, API 36, 50 steps), all PASS:** standard (pre-alert p95 58 / max 222 ms, alert 43 / 171 ms; run 42887712480); alongside (brew pre-alert 44 / 587, alert 35 / 104 ms; quick timer pre-alert 14 / 230, alert 15 / 235 ms; run 42887719098); two-brews (brew 1 pre-alert 42 / 183, alert 23 / 360 ms; brew 2 pre-alert 15 / 154 ms; brew 2's held alert held 7944 / 8051 ms, release → sound p95 12 / max 18 ms, not gated; run 42887725778). The runs were on the branch tip before the merge; the merge commit has the same tree (`main` had no other commits).
- PR #219 merged into `main` with a merge commit (`73a97fc5`); CI green on it (run 42899588780).
- **Release:** run 42901966896, [`v0.5.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.5.1) from `main` `73a97fc5` (versionCode 50199, APK SHA-256 `d7d41691…155d`), certificate `a500e2c6…2f67` = `signing/release-cert-sha256.txt`.
- #212–#217 and #220 closed by the merge, #218 closed after the release. Working branch restarted from `main`.

### v0.5.1: feedback round, rc.1 and rc.2 (2026-11-10)

The PO's four points of feedback on v0.5.0 (2026-11-10; D-110): Start's two
sections into one; *Knapp ab* shows its value; the filter sheet's *Fertig*;
a sans in place of the serif font. Spec issue #212, plan
`docs/plans/v0.5.1.md`, draft milestone PR **#219** from
`claude/wonderful-davinci-fu8tqi`.

- **Build phase: done** (2026-11-10, started by the PO with
  `/implement-spec #212`). #213–#216 are merged into the PR branch (head
  `ba89af29` before this handover). Each implementer started from the draft
  `75e4198b` and reviewed its part; the full local gate passed on the merged
  branch (spotlessCheck, detekt, checkManifestPermissions, testDebugUnitTest,
  lint, verifyRoborazziDebug, assembleDebugAndroidTest).
  - #213 (T2): the draft stood; a test for the row's tap and its TalkBack
    label was added.
  - #214 (T2): the global value is drawn by a `VisualTransformation`, the
    field's text stays empty (the draft made it the text, which could give
    „1525").
  - #215 (T1): with the motion review's S1 (*Fertig* slides the sheet out)
    and N1 (a hairline under the pinned title while scrolled).
  - #216 (T2): the draft's fonts, licence and roles stood; all 708 goldens
    are re-recorded.
- **Motion review: done** (`docs/plans/v0.5.1-motion-review.md`; S1 and N1
  are built).
- **Review and fix round: done** (2026-11-10; #217, first part).
  - CI on `df9959c3` was green on all jobs; nothing to fix.
  - Milestone review (T3): no must-fix; two should-fix (D-096's stale Start
    note; TalkBack could not tell the shown global *Knapp ab* value from an
    own one) and ten nits. No T4 review: no timer code changed.
  - Fix round (T2, own worktree; merge `c24e9e90`): all twelve findings and
    the four known items (rows keyed by tea; the font feature list in 03
    §3.2; `ShowsGlobalTest` for the quieter colour; D-110's build notes).
    New behaviour: TalkBack reads the state „Vorgabe" while *Knapp ab* shows
    the global value (03 SCR-09, string in `feature:collection`). The filter
    sheet dismisses only once hidden, with a test for the slide-out path.
    No golden changed.
  - The full local gate passed on `c24e9e90` (spotlessCheck, detekt,
    checkManifestPermissions, testDebugUnitTest, lint, verifyRoborazziDebug,
    assembleDebugAndroidTest). A fresh container needs the Robolectric SDK 35
    jar fetched first (`v0.4.0-resume.md` §3).
- **rc phase: done** (2026-11-10; #217, second part).
  - Commit `06cf4ea8`: `umamit.version=0.5.1-rc.1` (versionCode 50101) and
    the design review renders `docs/design/review/v0.5.1/` (92 renders,
    600 px, placeholder images only; README with the points to judge).
  - CI green on `06cf4ea8` (checks, build, emulator API 35 and 36; run
    42832175360).
  - Release run 42834765374: pre-release
    [`v0.5.1-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.5.1-rc.1)
    (APK SHA-256 `645a6184…0e6d`), certificate `a500e2c6…2f67` =
    `signing/release-cert-sha256.txt`.
  - PR #219's body and #217 are updated; the PR stays in draft.
- **Feedback on rc.1 and fix round: done** (2026-11-10; D-111, #220).
  - The PO: the one list on Start looks good, but a tap on a *Zuletzt
    gebrüht* row shows a different screen for a split second before the
    timer. Diagnosis (confirmed by the PO): Start rearranges with the new
    brew (card, bar) under the timer route's fade. The PO chose „Start
    holds still" and gave the go for the fix round and rc.2.
  - Specs in `0a092ce4`: D-111, 03 SCR-02, 09 §4 „v0.5.1" (last point).
  - Built at T3 (Opus `medium`) in `debc7351`, reviewed through two lenses
    (rule 8 at T3, spec at T2; no must-fix, two should-fix on test
    strength), review fixes in `bee40f17`. New: `StartHandOff`
    (`feature:home`), `BrewBarHold`/`heldBar` (`app`), tests in
    `HomeViewModelTest` and `BrewAgainHandOffTest`. No timer code, no
    route transition and no golden changed.
  - Claude's details, not put to the PO (D-111 build notes): the answer to
    „Welchen Timer beenden?" holds Start the same way; a bar already shown
    stays live while held.
  - The full local gate passed on `cd19b70a`.
- **rc.2: done** (2026-11-10).
  - Commit `cd19b70a`: `umamit.version=0.5.1-rc.2` (versionCode 50102).
    CI green (run 42874910092).
  - Release run 42878477934: pre-release
    [`v0.5.1-rc.2`](https://github.com/katznmeik/UmamiT/releases/tag/v0.5.1-rc.2)
    (APK SHA-256 `aee6a142…9402`), certificate `a500e2c6…2f67` =
    `signing/release-cert-sha256.txt`.
- **PO approval: given** (2026-11-10, in the session, after Claude named
  the hand-off check and the design review as what the approval covers):
  „rc.2 works, the transition is clean now, approved". The last checklist
  point (D-111) passes on the phone. Still to tick by the final session:
  that point in PR #219's checklist and in #220.
- **Next phase (fresh session, Sonnet
  `medium`): #218.** Accuracy run first (09 §4a); `umamit.version=0.5.1`;
  CI green; PR out of draft and merged with a merge commit; final `v0.5.1`
  dispatched from `main`, certificate verified; tickets closed,
  `docs/history.md` and this file updated; the working branch restarted
  from `main`.
- The branch `claude/modest-ptolemy-86vgji` is contained in the PR branch
  and no longer needed.

### v0.5.0: released (2026-11-10)

- PR #210 merged into `main` with a merge commit (`db2ba2b3`); CI green on the branch tip `9ab3c689` and on `main`'s merge commit (run 42773576913).
- **Accuracy on `db2ba2b3` (09 §4a, API 36, 50 steps), all PASS:** standard (pre-alert p95 65 / max 154 ms, alert 25 / 225 ms; run 42773655399); alongside (brew pre-alert 19 / 350, alert 39 / 40 ms; quick timer pre-alert 13 / 313, alert 25 / 248 ms; run 42773594216); two-brews (brew 1 pre-alert 36 / 167, alert 14 / 31 ms; brew 2 pre-alert 12 / 53 ms; brew 2's held alert held 7979 / 8039 ms, release → sound p95 10 / max 14 ms, not gated; run 42773597724). The first standard dispatch failed to reach GitHub and was sent again.
- **Release:** run 42781616158, [`v0.5.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.5.0) from `main` `db2ba2b3` (versionCode 50099, APK SHA-256 `1a91bdb6…89bd`), certificate `a500e2c6…2f67` = `signing/release-cert-sha256.txt`, marked latest.
- #194, #196–#208 closed (by the merge). Working branch restarted from `main`.

### v0.5.0: approved, final (2026-11-10)

- **PO (in the session, 2026-11-10):** „All tests passed, no feedback so far, please release": every rc.1 checklist item on #207 passed; that is the approval of the checklist and the design review (rule 9).
- **#208:** `umamit.version=0.5.0` (versionCode 50099) on the branch → CI green → merge PR #210 into `main` with a merge commit → all three accuracy variants on the merge commit (09 §4a) → `v0.5.0` from `main` → close #194 and #196–#208, restart the branch from `main`.

### v0.5.0: rc.1 out (2026-11-09)

- **Build:** the PO's `/implement-spec #194`; tickets #196–#206 built on PR #210 by implementer subagents at the tiers in `docs/plans/v0.5.0.md`, each verified by the orchestrator (full local gate, diff, CI). One test fix: `ProcessDeathTest` now expects the dormant `BREW_2 IDLE` line.
- **Reviews:** T3 milestone review, no must-fix; T4 timer-path review „safe for an rc", its held-alarm finding answered by the PO as D-109 (a held alarm rings once the other alarm stops sounding); motion review by project rules (the PO's choice; `/review-animations` cannot run in a subagent), three should-fix. Two fix rounds (non-timer, timer), GATE PASS on `572aaf10`.
- **Version:** `0.5.0-rc.1` (versionCode 50001) in `e53cc2b0`, with the design-review renders `docs/design/review/v0.5.0/` (86 renders, 600 px, placeholder photos). CI green on it (checks, build, emulator API 35 and 36).
- **Accuracy on `e53cc2b0` (09 §4a, API 36, 50 steps):** standard PASS (pre-alert p95 44 / max 331 ms, alert 27 / 95 ms; run 42695580791); alongside PASS (brew pre-alert 17 / 76, alert 43 / 310 ms; quick timer pre-alert 14 / 736, alert 27 / 370 ms; run 42695589924); two-brews PASS (brew 1 pre-alert 44 / 774, alert 18 / 162 ms; brew 2 pre-alert 13 / 372 ms; brew 2's held alert held 7977 / 8052 ms, release → sound p95 9 / max 9 ms, not gated; run 42695597023).
- **Release:** run 42706294674, pre-release [`v0.5.0-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.5.0-rc.1) (versionCode 50001, APK SHA-256 `e3ff8717…2438`), certificate `a500e2c6…2f67` = `signing/release-cert-sha256.txt`.
- **Checklist:** 09 §4 v0.5.0 block, posted on #207 (comment 6752753944) and linked from PR #210.
- **Next:** the PO's results on #207. Findings → fix round → `-rc.2` (all three accuracy variants if timer code changed). On explicit approval: #208.

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
| 2026-11-26 | **v0.11.0 rc session:** T3 review and `/review-animations` findings fixed after the PO's answers (D-137), renders, What's new, full local gate; `v0.11.0-rc.1` (110001) from `1e9153a1`. Next: the PO's checklist, then the final session (#371). |
| 2026-11-26 | **v0.11.0 build session:** #366–#369 built (mock-up kit, eight Anleitungen, the tour rework, ⋮ *Hilfe* on seven screens), each verified and CI green on `17e9c75d`; D-137 build notes; three questions for the PO. Next: the rc session (#370). |
| 2026-11-26 | **v0.11.0 specify session:** D-137 Q1–Q16, spec #365, tickets #366–#371, plan, draft PR #372; approved by the PO. |
| 2026-11-25 | **v0.10.0 final session (#363):** the PO approved rc.1 and ran `/review-animations` (approved; two tour findings deferred); the tour feedback recorded for the next release; PR #364 merged (`1933bc15`), all three accuracy variants PASS, `v0.10.0` (100099) released and verified. Next: a specify session, tour rework first. |
| 2026-11-25 | **v0.10.0 rc session (#362):** GEN-13 AC1 fix (texts without an activity in the app language), jank calibration (*timer → landscape Journal* gates), T3 and T4 reviews with two fix rounds, the app language renumbered GEN-13, the accuracy harness follows the test runner; gate, CI and all three accuracy variants PASS on `5978b729`; `v0.10.0-rc.1` (100001) published. Next: the PO's check, then #363. |
| 2026-11-24 | **v0.10.0 build session 2:** steps 3–5 on PR #364: #358 (app language, formatting), #360 (CSV), #359 runs 1 and 2 (English strings), #361 (tour); a test-only emulator fix for #358 (Compose's animation scale). CI green on `b40aef15` and `abf629ce`; fixes for #358 (Compose's animation scale in the jank test; the per-app language without an activity, T4). |
| 2026-11-24 | **v0.10.0 build session 1:** draft PR #364; steps 1–2 of the plan: #354 (stock edits, cost recompute), #355 (Vollbild research), #357 (portrait timer, rotation in place; D-136 build note), #356 (Vollbild sheet SCR-34). Verified locally; CI green on step 1 (`2d3d9f76`); on step 2 the four new #357 emulator cases failed first (all test causes), green on `4379b120`. |
| 2026-11-24 | **v0.10.0 specify session:** the PO chose a feedback round (five points); grilled Q1–Q9 (D-136); specs 02, 03, 05, 07, 09, 10, 11 and CLAUDE.md rule 4 updated; spec #353, tickets #354–#363, plan `docs/plans/v0.10.0.md`; approved by the PO the same day. |
| 2026-11-18 | **v0.9.0 specify session:** grilled Q1–Q16 (D-130); specs 02, 03, 05, 06, 09, 11 and CLAUDE.md rule 7 updated; spec #333, tickets #334–#344 and #330, `docs/plans/v0.9.0.md`; waiting for the PO's approval. |
| 2026-11-18 | **v0.8.0 session 6:** the PO approved the whole checklist on rc.2; `umamit.version=0.8.0` (80099); PR #328 merged (`60e07bbe`), CI and all three accuracy variants green on it; [`v0.8.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.8.0) released and verified. Next: the v0.9.0 specify session. |
| 2026-11-17 | **v0.8.0 build session 3:** the PO confirmed the sage alarm (bar static sage too); #325 and #329 built; the T3, T4 and ad-hoc motion reviews with one fix round; the full gate, CI and all three accuracy variants green on `9c0e4225`; `v0.8.0-rc.1` (80001) published with the checklist and the design review. Next: session 4 (the PO's check, merge, final `v0.8.0`). |
| 2026-11-17 | **v0.8.0 build session 2:** #319, #320, #313, #321 and #322 merged and verified on PR #328; #325 step 1 (the sage alarm of the light timer) built and its renders posted on #324; the swing figures corrected to the measured 1.73 / 2.97. Next: the PO's confirmation, then build session 3 (the rest of #325, #326). |
| 2026-11-17 | **v0.8.0 build session 1:** the PO approved spec #315, the tickets and the plan; PR #328 (draft) opened; #317, #318, #316 (+ a brew test follow-up) and #323 merged and verified; the light timer's renders posted on #324 and answered by the PO (band ink, fill as today, sage alarm for the light timer; D-128 34–37). Next: build session 2 (#320, #319, #321, #313, #322, #325). |
| 2026-11-17 | **v0.8.0 specify session:** docs PR #314 merged; the PO's nine points grilled (Q1–Q31) into D-128: v0.8.0 (Session and Aufguss wording, three taste sets with *Geschmacksprofil* and the slider info, the timer follows *Design*, refresh progress in the settings, the ⋮ fix, #313) and v0.9.0 planned (export/import, shared recipes); 1.0 postponed. Specs, spec #315, tickets #316–#327, plan `docs/plans/v0.8.0.md` written. Next: the PO's approval, then build session 1. |
| 2026-11-17 | **v0.7.2 session 14 (#310: the PO's approval, merge, final):** the PO approved checklist and design review; PR #312 merged (`c5f19e8e`), CI and all three accuracy variants PASS on it; `v0.7.2` released (70299, certificate verified); #296–#310 closed. Next: the PO decides the next release. |
| 2026-11-17 | **v0.7.2 session 13 (#309: the proof, accuracy, rc.1):** the proof on `a8f78d4f` all clean (recorded on #312), full local gate PASS, CI green, all three accuracy variants PASS; `v0.7.2-rc.1` released (70201, certificate verified), checklist to the PO on #312. Next: #310 after the PO's check. |
| 2026-11-16 | **v0.7.2 session 11 (execution part 4, #308's alert-path group):** TMR-A-14, -01, -11, -12, -13 and D-126 (DAT-A-14, TMR-A-06) by seven T3 implementers, at most two at a time on disjoint files. Each step was verified (diff, proof; 04 §12 counts 263/512/707 unchanged on four landed heads) and pushed in three groups, each green on CI before the next. #308 is done. Usage: orchestrator 115 requests, 21.7 M cache read, 0.19 M cache write, 48 k output; seven implementers 77 requests, 5.7 M cache read, 0.93 M cache write, 39 k output (from the transcripts, before the final handover turn). Next: the T3 milestone review and the T4 timer review, then #309. |
| 2026-11-16 | **v0.7.2 session 10 (execution part 3, #308 groups 1 and 2):** 20 timer-path steps by four T3 implementers in two lanes each, verified per step and on the combined head (diff, proof, 04 §12 test counts 263/512/707 unchanged), CI green (API 36 jank flake once, rerun green). PO answers as options, in #308: one `UnlockedDevice.kt` in a shared androidTest folder; UI-A-07's fix action generic in `core:ui`; DAT-A-03 via a public `FakeCollection.deleteTeaRows`. Possible crash filed as #313. Usage: orchestrator ≈ 105 requests, 20.5 M cache read, 0.21 M cache write, 46 k output; four implementers 317 requests, 44.0 M cache read, 2.42 M cache write, 167 k output (from the transcripts). Next: #308's alert-path group in one session. |
| 2026-11-16 | **v0.7.2 session 9 (execution part 2):** #307's 12 steps (D-121 – D-125) by eight T3 implementers plus one follow-up fix (`SettingsNav.alertKey` read lazily), verified per step (diff, fence, proof), CI green. Next: #308 on its own (Opus `medium` orchestrator). |
| 2026-11-16 | **v0.7.2 session 7:** restructuring design #304 (Fable 5.2 `medium`): `docs/plans/v0.7.2-restructuring.md`, the PO approved variant A of all seven designs (recorded in D-121 – D-127), 06 unchanged, steps in #307 and #308, pushed to draft PR #312. Next: the execution, #305 first (Opus `medium` orchestrator). |
| 2026-11-16 | **v0.7.2 session 6:** triage #303 with the PO (Opus `medium`): all 60 findings picked, D-121 – D-127 for the seven restructurings, #304–#308 bodies updated, pushed to draft PR #312. Next: restructuring design #304 (Fable 5.2 `medium`). |
| 2026-11-16 | **v0.7.2 session 5:** UI audit #302 (Opus `medium`, full scope), `docs/plans/v0.7.2-audit-ui.md` (15 findings UI-A-01 … 15: settings routes twice, request holders, undo snackbar and date picker three times each, the editors' input rules, the session-zone fallback six times; brew parts marked for #308; no code change), pushed to draft PR #312. Next: triage #303 with the PO (Opus `medium`). |
| 2026-11-16 | **v0.7.2 session 4:** timer-path audit #301 (Fable 5.2 `medium`, full scope), `docs/plans/v0.7.2-audit-timer.md` (15 findings TMR-A-01 … 15: the dead `AlertPlayer` interface and factory, the pass-through `TimerSlotStore`, the start question in three view models, the sheet state eight times, the accuracy report in main sources; the app's timer wiring files named for the #308 fence; no code change), pushed to draft PR #312. Next: UI audit #302 (Opus `medium`). |
| 2026-11-16 | **v0.7.2 session 3:** data audit #300 (Fable 5.2 `medium`, full scope), `docs/plans/v0.7.2-audit-data.md` (15 findings DAT-A-01 … 15, four of them deletions found through R8, no code change), pushed to draft PR #312. Next: timer-path audit #301 (Fable 5.2 `medium`). |
| 2026-11-16 | **v0.7.2 session 2:** catalog audit #298, `docs/plans/v0.7.2-audit-catalog.md` (15 findings, no code change), pushed to draft PR #312; #299: the PO keeps all three remaining audits. Next: data audit #300 (Fable 5.2 `medium`). |
| 2026-11-15 | **v0.7.2 step 1:** the PO approved the plan and tickets; relabelled #297–#310; evidence file `docs/plans/v0.7.2-evidence.md` (R8, Lint 0 unused, Dependency Analysis, largest files, fixtures, proof baseline); draft PR #312. Next: catalog audit #298 (Opus `medium`). |
| 2026-11-15 | **v0.7.2 specified:** one grilling round (D-120: Fable 5.2 `medium`, findings files per group, Dependency Analysis plugin locally, T4-F); plan `docs/plans/v0.7.2.md`, spec #296, tickets #297–#310, 09 §2/§4 „v0.7.2", 12 §2/§3.2; docs PR merged. Waiting for the PO's approval. |
| 2026-11-15 | **v0.7.1 done:** the PO approved rc.2 („all approved"); 0.7.1 bump, #290 merged (`6192bff5`), CI on `main` green (rerun of the known `ProcessDeathTest` flake), all three accuracy variants PASS, `v0.7.1` released with `RELEASE_TOKEN` (70199, certificate verified); #289, #291–#294 closed. v0.7.2 grilling: D-119. Next: the v0.7.2 specify session. |
| 2026-11-14 | **v0.7.0 done:** the PO reviewed rc.2 (all but „Tassen") → D-115 Amendment 2 (V1–V9, volume instead of cups), #274/#275/#277, `v0.7.0-rc.3` (70003); the PO approved rc.3 („All changes approved"); 0.7.0 bump, #266 merged (`f3582466`), all three accuracy variants PASS, `v0.7.0` released (70099, certificate verified); #264 closed. Next: the v0.7.1 specify session (D-116). |
| 2026-11-13/14 | **v0.7.0 rc.2:** the PO reviewed rc.1 with ten points (no approval); grilled in four rounds (R1–R16) into D-115 Amendment 1 and D-116 (v0.7.1: split rating, transition audit; cleanup → v0.7.2); specs 02/03/05/09/11 updated; tickets #268–#273 built at T2 (one detekt fix at T3), one T3 review (12 findings, all fixed); full local gate PASS and CI green; `v0.7.0-rc.2` (70002) from `ccc57917`. A container restart lost one implementer run (resumed from its worktree); PR CI had not run while #266 conflicted with `main` (merged `main` in). |
| 2026-11-13 | **v0.7.0 specified:** the PO answered the Statistik grilling in five rounds of options (Q1–Q20, D-115); 02 §12 INS-01–INS-08, SCR-18, 05 §6, 09, 11, 12 written; spec #255, tickets #256–#264, plan `docs/plans/v0.7.0.md`, waiting for the PO's approval. |
| 2026-11-13 | **v0.6.1 done:** PO approved rc.1 („Approve everything": checklist and the seven design-review points; D-114 „Approval"; no rc.2); full local gate, CI and all three accuracy variants PASS on `87dc394e`; #252 merged (`22b84788`), `v0.6.1` released (versionCode 60199, certificate verified); #240–#251 closed. Next: Statistik (v0.7.0), its specify session. |
| 2026-11-12 | **v0.6.1 built, rc.1 out:** #241–#249 built per the plan's task graph, #250's T3 milestone review, T4 timer review, `/review-animations` and fix rounds; full local gate, CI and all three accuracy variants PASS on `62657833`; `v0.6.1-rc.1` (60101) for the PO's checklist and design review. |
| 2026-11-12 | **v0.6.1 specified (feedback round on v0.6.0):** the PO's nine points grilled in five rounds (Q1–Q19) into **D-114**: taste and a note per infusion, also on the timer screen (umamit.db v7), GESCHMACK folds, quick-timer chips 1:00–15:00, photo viewer, catalog links in ⋮, the Katalog opens with every kind, „N Tees", *Design*. Timer code (`feature:brew`): T3+, T4 timer review, accuracy runs per rc. Statistik becomes v0.7.0. Spec #240, tickets #241–#251 (approved as proposed), plan `docs/plans/v0.6.1.md`. Next: the build. |
| 2026-11-12 | **v0.6.0 done:** PO approved rc.1 (all twelve checklist points) and the slim slider's renders (D-113, #237; no rc.2); full local gate, CI and all three accuracy variants PASS on `d10e7ef0`; #235 merged (`232c4b82`), `v0.6.0` released (versionCode 60099, certificate verified); #221–#234, #236, #237 closed. Next: v0.6.1 (Statistik), its specify session. |
| 2026-11-10 | **v0.5.1 done:** PO approved rc.2 (checklist with the Start hand-off of D-111, design review); all three accuracy variants PASS on `5d461669`; #219 merged (`73a97fc5`), `v0.5.1` released (versionCode 50199, certificate verified); #212–#218, #220 closed. Next: M4 → v0.6.0. |
| 2026-11-10 | **v0.5.0 done:** PO approved rc.1 (checklist and design review); #210 merged (`db2ba2b3`), all three accuracy variants PASS on `main`, `v0.5.0` released (versionCode 50099, certificate verified); #194, #196–#208 closed. Next: M4 → v0.6.0. |
| 2026-11-09 | **v0.5.0 built, rc.1 out:** `/implement-spec #194`, tickets #196–#206 on PR #210; T3/T4/motion reviews and two fix rounds; D-109 (held alarm rings once the other stops sounding). `0.5.0-rc.1` (`e53cc2b0`): CI green, all three accuracy variants PASS, pre-release published, checklist on #207. Next: the PO's checklist and design review. |
| 2026-11-09 | **PO feedback after v0.4.1** (four points) grilled in two rounds into **D-108**: v0.5.0 before M4 (M4 → v0.6.0): any two active timers, every tea section of the shop with one category chip at a time, per-tea stock warning with its own threshold and a global reminder interval, a flavour filter (taste notes and scales). 01 NG6/NG7 retired, 09 §2 row added. Spec #194 published with `/to-spec` (six existing test seams: timer controller harness, catalog parser, catalog refresher, `CatalogBrowser`, low-stock repository and schedule, umamit.db v5 migration; PO confirmed). Tickets #196–#208 with `/to-tickets` (13, approved by the PO as proposed; #205, #206 at T3). Next: the build on the PO's go. |
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
