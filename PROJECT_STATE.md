# PROJECT_STATE — UmamiT

> Handover between sessions: **only the current state**. Update it at the end
> of every session or phase. Finished releases, past plans and the session
> log are in [`docs/history.md`](docs/history.md); read that only when you
> need it (D-101).

**Last updated:** 2026-11-25 (v0.10.0 rc session #362: GEN-13 fix and review fixes, jank calibration, `v0.10.0-rc.1` published). **Next:** the PO's check of rc.1, then the final session (#363).

## Next: the PO checks `v0.10.0-rc.1`, then the final session (#363)

- **rc.1 is out:** [`v0.10.0-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.10.0-rc.1) (pre-release, versionCode 100001, from `5978b729`, APK SHA-256 `7fea0c3b…f7b9`, certificate verified, the same as v0.9.0's).
  - Full local gate PASS; CI green on all five jobs.
  - All three accuracy variants PASS on `5978b729`: runs 45503182911 (standard), 45503187252 (alongside) and 45503191669 (two-brews), API 36, 50 steps.
  - The first release run failed on a Maven Central HTTP 429 and passed on the rerun.
- **The PO's part** (CLAUDE.md rules 6 and 9):
  - the on-device checklist 09 §4 „v0.10.0", including #355's probe steps for the Vollbild grant on the update to the rc;
  - the design review: `docs/design/review/v0.10.0/`, 138 renders, light and dark, de and en.

  Approval in a session or on PR [#364](https://github.com/katznmeik/UmamiT/pull/364). An rc.2 only if the checklist finds something.
- **Done in the rc session** (details in D-136 „Build notes (rc session)" and `docs/history.md`):
  - **GEN-13 AC1 fix:** texts built without an activity follow the app language: the low-stock, backup and catalog notifications and every channel name, the timer's included. One `AppLanguageContext` (`core:data`), one `LocaleManager` lookup per notification.
  - **Jank calibration (D-117):** *timer → landscape Journal* gates (PASS at 1.7× on its first gated run). The other seven turn lines stay report-only, with the reasons in D-136.
  - **Reviews:** T3 milestone review and T4 timer review (no blocker, no rule-8 risk), and their fixes:
    - the stock edit sheets on float noise;
    - the English decimal mark;
    - the confirm words from the hint's resources;
    - the FSI check off the alert's main-thread path;
    - `BuildConfig.VERSION_CODE`;
    - the English no-break spaces.

    The `close()` question is answered: test tooling, not the app.
  - **Renumbered:** the app language is **GEN-13**. It had been numbered GEN-10, which is „Instant local search"; earlier commits and the tickets say GEN-10.
  - **Accuracy harness:** `scripts/accuracy-run.sh` and `stress-instrumented.sh` now read the runner from the device. They still named `AndroidJUnitRunner` after #358 set `GermanLocaleRunner`, so all three runs on `b6f093fb` failed before step 1.
- **`/review-animations`:** it is user-invoke only, and the PO chose to run it. It was invoked in the rc session without a scope and has not run yet. Run it before the merge, with this scope: the SCR-29 sheets, SCR-34, the SCR-33 tour, *Sprache* and rotation in place (CI: one frame of ~1 s on *landscape Journal → timer*).
- **The final session (#363), after the PO's approval:**
  1. Merge PR #364 (merge commit).
  2. All three accuracy variants on `main`'s merge commit.
  3. `umamit.version=0.10.0` and the final `v0.10.0` (100099) from `main`; What's new `docs/whats-new/v0.10.0.md` exists (check it).
  4. Make CLAUDE.md rule 4's „(D-136, planned)" current.
  5. Close #353–#363 and restart the branch from `main`.
- **For the PO (asked in the session's last message, not decided):**
  - **From build session 1:** #355's options A–D; the app-wide rotation in place (#357); the implementers' choices for #354 and #356; whether the Galaxy is a foldable.
  - **From build session 2:** *Überspringen* on the tour's last page; the English wording; *Sprache* closing at once; [Français, English] under *System*.
  - **From the rc session:**
    - Editing an old purchase reprices every past session (INV-08, as specified).
    - English number input: „1,250" is refused and „1.250" reads as 1250 g.
    - The Vollbild sheet shows on any open in a version while the permission is off and unanswered; item 13 says the first open.
- **Timer code:** v0.10.0 changes the timer path in three modules:
  - `app`: #357 and the FSI resume gate;
  - `core:timer`: strings, `Locale.ROOT`, one language snapshot per notification through `core:data`'s `AppLanguageContext`, and `GermanLocaleRunner`;
  - `feature:brew`: English strings.

  The T4 review is done, and the 04 §12 tests and all three accuracy variants passed on the rc commit. The final needs all three again on `main`.
- **Open (not blocking):**
  - The launcher / system UI ANR on the emulators. On `00c3327e` every `TransitionJankTest` case failed on API 35 with a launcher „Application Not Responding" window holding focus; the next head was green. Rerun once if it returns.
  - `ReceiveRecipeDeviceTest` no longer proves grant enforcement; the phone check covered it on v0.9.0 rc.2.
- **Still true:** umamit.db v9 (no migration in v0.10.0); the network has three uses (D-135); `checkManifestPermissions` passes unchanged.
- **Branch:** `claude/inspiring-ritchie-8hfw00`, restarted from `main` `f663318b` after the merge, with the v0.10.0 spec and build commits on top (PR #364). The specify session and build sessions 1 and 2 were assigned `claude/elegant-galileo-46r9pb`, `claude/kind-brown-eyjcmo` and `claude/focused-cannon-n1p2q6` and did not use them. A new session with another assigned branch works here directly.
- **Session facts still true:**
  - **CI:** a new push cancels the PR's running CI (concurrency group), so only the head is gated. Push a group of verified steps, then let one run finish before the next push; a docs-only push skips the build jobs, so push docs after a green code run. `gh` is for search only here (except `gh api …/actions/jobs/<id>/logs`, which worked for a job log): check CI with the GitHub MCP tools (`pull_request_read` `get_check_runs`, `actions_get`, `actions_run_trigger` `rerun_failed_jobs`).
  - **Worktrees:** never remove an agent worktree while a Gradle build runs: `spotlessCheck` scans `.claude/worktrees/` and fails on a vanished path (2026-11-17). Brief each implementer to `git reset --hard <branch head>` first (a local, unpushed head works too). Remove worktrees with `git worktree remove --force --force`, then `rm -rf`, `git worktree prune`, and delete the `worktree-agent-*` branch.
  - **Combined check:** two tickets that each pass alone can fail together. Detekt limits sit at `MainActivity` (`TooManyFunctions` 11, `onCreate` `LongMethod` 60) and `UmamitRoot` (complexity). Always run the check on the merged head before pushing (2026-11-24).
  - **Animation scale in device tests:** Compose keeps one animation scale per process and hears `animator_duration_scale` only while a window listens; a test that sets the scale with no activity open must make Compose follow (`ComposeAnimationScale`, 2026-11-24).
  - **Test locale:** JVM and Roborazzi tests run in German (`config/test-locale`); `RuntimeEnvironment.setQualifiers` bypasses the pin and must append `+de-rDE`; device tests use `GermanLocaleRunner` (2026-11-24).
  - **Docs push on a red head:** a docs-only push skips the build and emulator jobs and counts as a successful run, hiding a red code head. Push docs with the fix, or after a green run (2026-11-24).
  - **User-invoke-only skills:** `/review-animations` and `/implement-spec` carry `disable-model-invocation`; a subagent or Claude cannot start them. Ask the PO to type the command, with the scope in the same message (2026-11-25).
  - **Accuracy runs:** `scripts/accuracy-run.sh` reads the instrumentation runner from the device; if a module's `testInstrumentationRunner` changes, nothing else needs to (2026-11-25).
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
| Current milestone | **v0.10.0** specified (D-136, spec #353, tickets #354–#363), approved by the PO 2026-11-24; build sessions 1 and 2 done (#354–#361), rc session (#362) done, `v0.10.0-rc.1` published 2026-11-25 for the PO's check; the final session (#363) next; before it **v0.9.0** (export/import, CSV, reminder, shared recipes, *Alle Daten löschen*, empty first start; D-128, D-130, D-131, D-133–D-135) released 2026-11-20 (90099, from `main` `f663318b`); the next release is the PO's choice; M5 / 1.0 postponed until the PO's go |
| Development go from PO | ✅ **In effect** since 2026-10-22 09:05 Europe/Berlin (D-038) |
| Working branch | `claude/inspiring-ritchie-8hfw00`, restarted from `main` `f663318b` after the v0.9.0 merge; v0.10.0 spec and build commits on top (PR #364) |
| Repository | `katznmeik/UmamiT` (renamed from `UmamiTea` on 2026-10-21; old URLs redirect) |
| Open PR | [#364](https://github.com/katznmeik/UmamiT/pull/364) (v0.10.0, draft); tickets #354–#363 open until the merge |
| Latest APK | rc [`v0.10.0-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.10.0-rc.1) (pre-release, versionCode 100001, from `5978b729`, APK SHA-256 `7fea0c3b…f7b9`, certificate verified); before it [`v0.9.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.9.0) (full release, versionCode 90099, from `main` `f663318b`, APK SHA-256 `6dd9f74f…4f4b`, certificate verified); earlier: rc [`v0.9.0-rc.2`](https://github.com/katznmeik/UmamiT/releases/tag/v0.9.0-rc.2) (pre-release, versionCode 90002, from `f243d7fd`, APK SHA-256 `83935b9a…c31b`, certificate verified); rc [`v0.9.0-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.9.0-rc.1) (pre-release, versionCode 90001, from `8cfa6a91`, APK SHA-256 `dd961af8…e852`, certificate verified); [`v0.8.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.8.0) (full release, versionCode 80099, from `main` `60e07bbe`, APK SHA-256 `b67a6901…2016`, certificate verified); earlier: rc [`v0.8.0-rc.2`](https://github.com/katznmeik/UmamiT/releases/tag/v0.8.0-rc.2) (pre-release, 80002, from `95da6ad5`, APK SHA-256 `b0396be0…7f79`, certificate verified); rc [`v0.8.0-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.8.0-rc.1) (pre-release, 80001, from `9c0e4225`, certificate verified); [`v0.7.2`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.2) (full release, versionCode 70299, from `main` `c5f19e8e`, APK SHA-256 `efc2cca6…20fa`, certificate verified); earlier: rc [`v0.7.2-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.2-rc.1) (pre-release, 70201, from `a8f78d4f`, certificate verified); [`v0.7.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.1) (full release, versionCode 70199, from `main` `6192bff5`, certificate verified); rc [`v0.7.1-rc.2`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.1-rc.2) (pre-release, 70102, from `e57861e0`); rc [`v0.7.1-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.1-rc.1) (pre-release, 70101, from `3fe102c0`); [`v0.7.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.0) (full release, versionCode 70099, from `main` `f3582466`, certificate verified); rc [`v0.7.0-rc.3`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.0-rc.3) (pre-release, 70003, from `73907f6d`); rc [`v0.7.0-rc.2`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.0-rc.2) (pre-release, 70002, from `ccc57917`); rc [`v0.7.0-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.0-rc.1) (pre-release, 70001, from `e37d4631`); [`v0.6.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.6.1) (full release, versionCode 60199, from `main` `22b84788`, certificate verified); rc [`v0.6.1-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.6.1-rc.1) (pre-release, 60101, from `62657833`); [`v0.6.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.6.0) (60099); [`v0.5.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.5.1) (50199); [`v0.5.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.5.0) (50099) |
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

1. **v0.10.0-rc.1:** the on-device checklist 09 §4 „v0.10.0" and the design review `docs/design/review/v0.10.0/`; approval for the merge and the final.
1. **v0.10.0 questions:** #355's options A–D, the app-wide rotation in place (#357), the implementers' choices for #354 and #356, whether the Galaxy is a foldable, and build session 2's points (tour skip on the last page, English wording, *Sprache*; see „Next"). Answers in a session or on PR #364; the rc can be built without them, and rc.1 lets the PO judge them on the phone.
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
