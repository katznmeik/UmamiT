# PROJECT_STATE — UmamiT

> Handover between sessions: **only the current state**. Update it at the end
> of every session or phase. Finished releases, past plans and the session
> log are in [`docs/history.md`](docs/history.md); read that only when you
> need it (D-101).

**Last updated:** 2026-11-24 (v0.10.0 build session 2: steps 3–5 of the plan done on PR #364: #358, #360, #359 in two runs, #361). **Next:** the rc session (#362).

## Next: the rc session of v0.10.0 (#362)

- **Done** (details in `docs/history.md`; build notes in D-136):
  - build session 1: #354 stock edits and recompute, #355 Vollbild research, #357 portrait timer, #356 SCR-34 sheet;
  - build session 2: #358 app language and locale-aware formatting, #360 the CSV in the app language, #359 English strings (run 1 non-timer modules, run 2 `feature:brew`, `core:timer`, `core:backup`), #361 the tour, plus two fixes for #358 found by CI: `ComposeAnimationScale` (test only) and the per-app language without an activity (`AppLanguageContext` in `core:timer`, T4).

  All on PR [#364](https://github.com/katznmeik/UmamiT/pull/364) (draft). The tickets stay open until the merge.
- **CI:** green on all five jobs on `b40aef15` (steps 3–4) and on **`abf629ce`** (step 5 with the German pin fix), emulator jobs on API 35 and 36 included. On `16a7eab7` both emulator jobs had failed: German device-test assertions met English, because a process drops the per-app language when its last activity goes; `AppLanguageContext` (timer notifications) and a per-test re-pin in `GermanLocaleRunner` fixed it (T4, escalated from #358's T3).
- **The rc session (#362)** (Opus `medium` orchestrator; T3/T4 reviewers per the plan):
  - **Calibrate and gate the jank lines.** The eight jank lines of #357 are in `JankGate.REPORT_ONLY`; calibrate them on three CI runs (D-117) and gate them. Since `ComposeAnimationScale` (build session 2), `TransitionJankTest` measures at Compose scale 1 for certain; use only runs from `b40aef15` on.
  - **T3 milestone review** of the whole PR; **T4 timer review**: #357's `app` changes and its `close()` question (D-136 build notes), #358's `TimerNotificationTexts` (`Locale.ROOT`), the `GermanLocaleRunner` of `:core:timer`'s device tests, `StepsSheetHostTest`/`TeaPickerScreenshotTest` test changes, and #359 run 2's resources in `feature:brew` and `core:timer`, and **`AppLanguageContext` / `TimerStateNotifications`** (named question: how many `LocaleManager` lookups one alert post makes, on which thread, also under *System*; could it be one snapshot per post?). The three accuracy variants are the check.
  - **Fix before rc.1 (GEN-13 AC1 defect, not a PO question):** texts built without an activity still follow the phone's language: the low-stock reminder (words and numbers), catalog notifications from a worker, the notification channels' names. `AppLanguage.current()` reads `Locale.getDefault()`, which is the phone's language in a process without an activity; the fix needs a language source that reads `LocaleManager` (as `AppLanguageContext` does).
  - `/review-animations` (T2) on the new and changed screens: SCR-29 sheets, SCR-34, SCR-33 tour, the *Sprache* dialog, rotation in place.
  - **All three accuracy variants** (timer-code release, 09 §4a), the full local gate on the rc commit, renders (light and dark, de, and en for the new English screens) in `docs/design/review/v0.10.0/`, What's new `docs/whats-new/v0.10.0.md`, then `v0.10.0-rc.1` (versionCode 100001).
  - **Watch the #357 emulator cases.** Green on `4379b120` and `b40aef15`. If they flake, tune their waits and keep the rotation via `user_rotation` and the creation counter (`DisplayRotation.kt`). With rotation locked, WindowManager writes a forced portrait back into `user_rotation`, so a test re-applies „held sideways" after leaving the timer.
- **For the PO (asked in the session's last message, not decided):**
  - **From build session 1:** #355's options A–D; the app-wide rotation in place (#357); the implementers' choices for #354 and #356; whether the Galaxy is a foldable (Android ignores the portrait request at sw ≥ 600 dp).
  - **From build session 2** (D-136 build notes):
    - *Überspringen* on the tour's last page: SCR-33 hides it, ONB-07 AC1 and D-136 item 16 say every page; as built it shows on all six.
    - The English wording: the CSV headers and the ISO date, the tea categories translated (Sorte names, descriptors and slider names stay German), „Sorte" = „Variety", the run 2 terms („Poured off", „Steeping …", „Leaf"), the typed words REPLACE / DELETE.
    - *Sprache*: a pick applies at once and closes the dialog.
    - The edge with a phone list like [Français, English] under *System* (English words, German numbers).
- **Timer code:** v0.10.0 changes the timer path in `app` (#357), `core:timer` (`TimerNotificationTexts`: `Locale.ROOT` for digit-only formats, same output; English `values-en`; the device-test runner) and `feature:brew` (English `values-en`; two test files pinned to German). Build session 2 also changed `core:timer` main code for the German pin fix: `AppLanguageContext` (new) wraps the context of `TimerStateNotifications`, so the notification texts and actions read the app language from `LocaleManager` on every resource lookup, also without an activity (D-136 build note „A process without an activity"). The 04 §12 tests passed after #358, after #359 run 2 and on the merged head with the fix. The T4 timer review and all three accuracy variants run before every rc.
- **Open (not blocking):**
  - The system UI ANR on the API 36 emulator (it covered Start in `TransitionJankTest` after `ReceiveRecipeDeviceTest` began using a test-APK provider). Foreign ANR dialogs are now answered „Wait" and logged (`ForeignAnrDialog`); the cause is unknown because the full logcat artifact cannot be downloaded from a container (blob host blocked). If it returns, read the `ForeignAnrDialog` lines in the job log.
  - `ReceiveRecipeDeviceTest` no longer proves grant enforcement (the test provider is exported without a permission); the phone check covered it on rc.2.
- **Still true:** umamit.db v9 (no migration in v0.10.0); the network has three uses (D-135); `checkManifestPermissions` passes unchanged.
- **Branch:** `claude/inspiring-ritchie-8hfw00`, restarted from `main` `f663318b` after the merge, with the v0.10.0 spec and build commits on top (PR #364). The specify session and build sessions 1 and 2 were assigned `claude/elegant-galileo-46r9pb`, `claude/kind-brown-eyjcmo` and `claude/focused-cannon-n1p2q6` and did not use them. A new session with another assigned branch works here directly.
- **Session facts still true:**
  - **CI:** a new push cancels the PR's running CI (concurrency group), so only the head is gated. Push a group of verified steps, then let one run finish before the next push; a docs-only push skips the build jobs, so push docs after a green code run. `gh` is for search only here (except `gh api …/actions/jobs/<id>/logs`, which worked for a job log): check CI with the GitHub MCP tools (`pull_request_read` `get_check_runs`, `actions_get`, `actions_run_trigger` `rerun_failed_jobs`).
  - **Worktrees:** never remove an agent worktree while a Gradle build runs: `spotlessCheck` scans `.claude/worktrees/` and fails on a vanished path (2026-11-17). Brief each implementer to `git reset --hard <branch head>` first (a local, unpushed head works too). Remove worktrees with `git worktree remove --force --force`, then `rm -rf`, `git worktree prune`, and delete the `worktree-agent-*` branch.
  - **Combined check:** two tickets that each pass alone can fail together. Detekt limits sit at `MainActivity` (`TooManyFunctions` 11, `onCreate` `LongMethod` 60) and `UmamitRoot` (complexity). Always run the check on the merged head before pushing (2026-11-24).
  - **Animation scale in device tests:** Compose keeps one animation scale per process and hears `animator_duration_scale` only while a window listens; a test that sets the scale with no activity open must make Compose follow (`ComposeAnimationScale`, 2026-11-24).
  - **Test locale:** JVM and Roborazzi tests run in German (`config/test-locale`); `RuntimeEnvironment.setQualifiers` bypasses the pin and must append `+de-rDE`; device tests use `GermanLocaleRunner` (2026-11-24).
  - **Docs push on a red head:** a docs-only push skips the build and emulator jobs and counts as a successful run, hiding a red code head. Push docs with the fix, or after a green run (2026-11-24).
  - **Release APK download:** `gh release download` fails here (it uses GraphQL, which is blocked); `gh api -H "Accept: application/octet-stream" repos/katznmeik/UmamiT/releases/assets/<id>` works (2026-11-20).
  - **Maven resolution hiccup:** a „Checks" job can fail at configuration with „Could not find org.jetbrains.kotlin:…" for an artifact that exists (Maven Central, 2026-11-20, on `main`); rerun the failed job once.
  - **Artifacts:** CI artifacts (full logcat) cannot be downloaded here (the blob host is blocked by the proxy); job logs work with `gh api …/actions/jobs/<id>/logs`.
  - **Environment:** the mirror init script lives in the session scratchpad and `gw.sh` passes it with `--init-script`. It has to be written again in a new container: a `maven { name = 'gcsMirror' }` made first (`remove` + `addFirst`) in `pluginManagement`, `dependencyResolutionManagement` and every `buildscript` (`beforeSettings` and `settingsEvaluated`), URL rewrites only on project repositories. Written so on 2026-11-16 (sessions 9 and 10), it worked first time; in `beforeSettings` reach the settings' buildscript as `mirrorFirst(s.buildscript.repositories)` (a `s.buildscript { … }` closure fails, 2026-11-17). The three timer test tasks take ≈ 8½ min cold.

- **Environment:** Maven Central answered HTTP 429 on 2026-11-11 and 2026-11-12; a local Gradle
  init script (`~/.gradle/init.d/mirror.gradle`, not in the repository) points every
  `repo.maven.apache.org` repository (`settingsEvaluated` plugin and dependency repositories, `allprojects`,
  `buildscript`) at Google's Maven Central mirror (`maven-central.storage-download.googleapis.com`), which the
  „UmamiT" environment reaches. Robolectric's runtime jars (SDK 35 `15-robolectric-13954326-i7`, SDK 36
  `16-robolectric-13921718-i7`) are pre-fetched from the mirror and checked against the sha512 as
  `docs/plans/v0.4.0-resume.md` §3 shows. One Gradle build at a time (gw.sh/gate.sh, same §3). A new container
  needs all of this again. **Since 2026-11-13 (build session):** the Gradle plugin portal redirects to Maven Central
  too, so the init script must also add the mirror as the *first* repository of `pluginManagement`,
  `dependencyResolutionManagement` and every `buildscript` (`repos.maven { name = 'gcsMirror'; url = MIRROR }`,
  then `remove` + `addFirst`; in `beforeSettings` and `settingsEvaluated`), and only rewrite URLs (never add) on project
  repositories (`FAIL_ON_PROJECT_REPOS`).
- **Release token (D-118):** `RELEASE_TOKEN` (the PO's fine-grained token, UmamiT only, Contents + Workflows read and write) publishes; it expires (the PO chose the date) and is renewed by the PO; an expired token fails „Create tag and GitHub Release" with 401/403. `main` has used it since the merge of #290; it published `v0.7.1`.
- **Release dispatch:** `release.yml` refuses a commit without a successful CI run, so dispatch it only after CI on the merge commit in `main` has finished. `accuracy.yml` needs the full 40-character SHA as `ref`. **D-132:** a final release needs `docs/whats-new/v<version>.md` at its ref or `release.yml` fails before tagging; an rc without one publishes the table alone.
- **Known flakes (rerun the failed job once; seen on rc.2, 2026-11-18):**
  - API 36 emulator: `ProcessDeathTest`'s two-brews case, and `TransitionJankTest.savedSheetInAndOut` on a slow emulator.
  - `release.yml`'s „Unit tests" step: `JournalPerformanceTest`'s wall-clock budget. It fails before the tag is made, so rerunning the failed jobs is safe.
- **Jank coverage of #331:** `TransitionJankTest` taps a tea row (`openSetup()`) but measures only the picker's in and out and setup → timer, not the tap → setup hand-over itself. The PO's eye is the check.
- **accuracy.yml, alongside:** its setup guard can stop the run before any step on a slow emulator („too slow, 14 s of step 1 passed before the screen went off"); rerun the failed job (2026-11-14).

## Phase

| Item | Status |
|---|---|
| Current milestone | **v0.10.0** specified (D-136, spec #353, tickets #354–#363), approved by the PO 2026-11-24; build sessions 1 and 2 done (#354–#361), the rc session (#362) next; before it **v0.9.0** (export/import, CSV, reminder, shared recipes, *Alle Daten löschen*, empty first start; D-128, D-130, D-131, D-133–D-135) released 2026-11-20 (90099, from `main` `f663318b`); the next release is the PO's choice; M5 / 1.0 postponed until the PO's go |
| Development go from PO | ✅ **In effect** since 2026-10-22 09:05 Europe/Berlin (D-038) |
| Working branch | `claude/inspiring-ritchie-8hfw00`, restarted from `main` `f663318b` after the v0.9.0 merge; v0.10.0 spec and build commits on top (PR #364) |
| Repository | `katznmeik/UmamiT` (renamed from `UmamiTea` on 2026-10-21; old URLs redirect) |
| Open PR | [#364](https://github.com/katznmeik/UmamiT/pull/364) (v0.10.0, draft); tickets #354–#363 open until the merge |
| Latest APK | [`v0.9.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.9.0) (full release, versionCode 90099, from `main` `f663318b`, APK SHA-256 `6dd9f74f…4f4b`, certificate verified); earlier: rc [`v0.9.0-rc.2`](https://github.com/katznmeik/UmamiT/releases/tag/v0.9.0-rc.2) (pre-release, versionCode 90002, from `f243d7fd`, APK SHA-256 `83935b9a…c31b`, certificate verified); rc [`v0.9.0-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.9.0-rc.1) (pre-release, versionCode 90001, from `8cfa6a91`, APK SHA-256 `dd961af8…e852`, certificate verified); [`v0.8.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.8.0) (full release, versionCode 80099, from `main` `60e07bbe`, APK SHA-256 `b67a6901…2016`, certificate verified); earlier: rc [`v0.8.0-rc.2`](https://github.com/katznmeik/UmamiT/releases/tag/v0.8.0-rc.2) (pre-release, 80002, from `95da6ad5`, APK SHA-256 `b0396be0…7f79`, certificate verified); rc [`v0.8.0-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.8.0-rc.1) (pre-release, 80001, from `9c0e4225`, certificate verified); [`v0.7.2`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.2) (full release, versionCode 70299, from `main` `c5f19e8e`, APK SHA-256 `efc2cca6…20fa`, certificate verified); earlier: rc [`v0.7.2-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.2-rc.1) (pre-release, 70201, from `a8f78d4f`, certificate verified); [`v0.7.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.1) (full release, versionCode 70199, from `main` `6192bff5`, certificate verified); rc [`v0.7.1-rc.2`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.1-rc.2) (pre-release, 70102, from `e57861e0`); rc [`v0.7.1-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.1-rc.1) (pre-release, 70101, from `3fe102c0`); [`v0.7.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.0) (full release, versionCode 70099, from `main` `f3582466`, certificate verified); rc [`v0.7.0-rc.3`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.0-rc.3) (pre-release, 70003, from `73907f6d`); rc [`v0.7.0-rc.2`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.0-rc.2) (pre-release, 70002, from `ccc57917`); rc [`v0.7.0-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.0-rc.1) (pre-release, 70001, from `e37d4631`); [`v0.6.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.6.1) (full release, versionCode 60199, from `main` `22b84788`, certificate verified); rc [`v0.6.1-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.6.1-rc.1) (pre-release, 60101, from `62657833`); [`v0.6.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.6.0) (60099); [`v0.5.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.5.1) (50199); [`v0.5.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.5.0) (50099) |
| Decision numbers | Up to **D-136** used; the next free is **D-137** |

## Working agreements in effect

- **Tiers (D-101):** T1 Sonnet `low`, T2 Sonnet `medium`, T3 Opus `medium`, T4 Opus `high`. `xhigh` only with the PO's OK; never `max`. Timer, backup and migration code is never below T3.
- **Sessions (D-101):** one phase per session. Each phase ends with a handover in this file (and a resume note if the phase stops part-way) that names the model the next phase needs.
- **Next-session prompt (PO, 2026-11-10):** whenever a new session is to be started, the current session's last message ends with the ready-to-paste prompt for it (CLAUDE.md „Start of every session", 12 §5.1).
- **Briefs and reviews (D-101):** lean implementer briefs (12 §5.2); one milestone review at T3, plus T4 for a changed timer path; `/review-animations` once per milestone.
- **Parallelism:** at most **2** implementer subagents at a time, and **one Gradle build at a time** (the machine-wide lock in `v0.4.0-resume.md` §3).
- **Per-ticket verification (PO, 2026-11-12, agreed for v0.6.1; the v0.7.0 plan proposes its own to the PO):** the orchestrator's diff review + a local cross-module check, then push; CI on the milestone PR is the full gate, checked before the next merge builds on it; the full local gate once more on the rc commit (`docs/plans/v0.6.1.md` „Verification").
- **Reporting:** push notifications only when the PO must act (rc ready, a blocking question). Questions go to the PO as selectable options.
- **Merging:** Claude merges all PRs (D-037); the PO only approves milestone PRs.
- **CI (D-105):** a change of `docs/**`, `*.md` or `.claude/**` only skips the checks, build and emulator jobs and still counts as a successful run for the release check; anything else, or any doubt, runs the full gate.

## Waiting on the PO

1. **v0.10.0 build session questions:** #355's options A–D, the app-wide rotation in place (#357), the implementers' choices for #354 and #356, whether the Galaxy is a foldable, and build session 2's points (tour skip on the last page, English wording, *Sprache*; see „Next"). Answers in a session or on PR #364; the rc can be built without them, and rc.1 lets the PO judge them on the phone.
2. **Optional, at any time:** the PO's own alarm or Vorwarnung recordings (`docs/design/assets/source/sound-alert-<name>.wav`, `sound-prealert-<name>.wav`; WAV, sound from the first sample). Each becomes one more entry in `PreAlertSound` / `AlertSound`.
3. **Optional:** install the Claude GitHub App on the repository (https://github.com/apps/claude/installations/select_target). Without it, PR CI results don't wake a session; the sessions check CI themselves.
4. **Environment:** cloud sessions must run in the personal environment **„UmamiT"** (created 2026-11-08: `dl.google.com`, Gradle and Maven hosts, `raw.githubusercontent.com`, `www.tee-kontor-kiel.de`). The plain „Default" environment blocks `dl.google.com`, so the Android plugin doesn't resolve.

## Open for later

- **Ideas the PO kept for later (D-112, 09 §5):** custom descriptors, manual journal entries, brew coach tips (notes per infusion came with v0.6.1, D-114).
- **Parked from v0.3.0:** the two *Pause* buttons and the shortened tea name in the brew row at font 2.0.
- **#58:** its remaining optional review findings stay open for later milestones.
- **Images:** all delivered. A6 (Journal) is built in since v0.6.0 (#231); A9 (Statistik) is built in since v0.7.0 (#259).

## Known caveats

- Mockup images still show the old name „UmamiTea".
- Live Update / ProgressStyle and the full-screen alert were verified on the Galaxy with `v0.0.2-probe`; the full timer keeps those patterns (D-043, D-044, `docs/plans/M1-android16-api-notes.md`). Now Bar presence is best effort.
- Tee-Kontor uses Cloudflare; refreshes may get blocked (handled by stopping without changes).
- After a reboot, alerts fire only once the phone has been unlocked (E2); Samsung's automatic overnight restart should be disabled (ONB-02).
- `ProcessDeathTest` (two brews, process death; emulator, API 36) can fail on its single read of the diagnostics log; rerun CI (D-112 Q19). Its brew-and-quick-timer test failed once on API 35 on a timing bound (2327 ms against 2 s, run 43094657346, runners degraded).
- `TransitionJankTest` on the API 36 emulator failed once with „Start is not shown" in all its cases (CI run 44153741694, `9c0e4225`, 2026-11-17; nothing in `app` or `feature:home` had changed) and passed on the rerun: rerun once before investigating.
- Robolectric fetches its runtime jar at test time; a network hiccup has failed CI before. Rerun once and comment on the PR.
