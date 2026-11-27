# PROJECT_STATE — UmamiT

> Handover between sessions: **only the current state**. Update it at the end
> of every session or phase. Finished releases, past plans and the session
> log are in [`docs/history.md`](docs/history.md); read that only when you
> need it (D-101).

**Last updated:** 2026-11-26 (v0.11.0 rc session: T3 review, `/review-animations`, fixes, renders, What's new; [`v0.11.0-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.11.0-rc.1) published from `1e9153a1`). **Next:** the PO's rc.1 checklist and design review, then the final session (#371).

## Next: the PO's check of rc.1, then the final session for v0.11.0 (#371)

- **rc.1 published (2026-11-26, rc session #370):** [`v0.11.0-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.11.0-rc.1), pre-release, versionCode 110001, from `1e9153a1` (release run 45837997878), APK SHA-256 `15143b6e…40c6`, certificate `a500e2c6…2f67` verified against `signing/release-cert-sha256.txt` by download. Full local gate passed on `1e9153a1` (and on `df439e23` before the animation fixes); CI green on all five jobs (run 45833307581).
- **The rc session's work (D-137 blocks „The PO's answers to the build questions", „The T3 milestone review's findings", „`/review-animations`"):**
  - The PO's answers to the four build questions: full titles in „Mehr dazu", the „Tipp" label kept, both *Hilfe* placements kept; the mock-up size is judged on the Galaxy (a new 09 §4 item).
  - T3 milestone review: no code defect, copy matches 03 (212 cells). Fixed: the step badge sits outside the ring; the drawn ⋮ menu at the right below the segment row (✓ on the selected segment); M-SAVED's five stars; M-HISTORY „+100 g · 12,40 €"; the SCR-36 title wraps at font 2.0; back from „Mehr dazu" keeps the scroll position (`bf4a0992`, `2391a6ab`).
  - `/review-animations` (typed by the PO): passed the dots, page scroll, button, mock-ups and menus; fixed the hard cut to „Mehr dazu" (now the root's fade) and *Überspringen* flipping at the half-way point (now fades with the swipe) (`1e9153a1`).
  - Renders and README: `docs/design/review/v0.11.0/` (94 renders); What's new: `docs/whats-new/v0.11.0.md`.
  - `git diff 1933bc15..1e9153a1` is empty for `feature/brew`, `core/timer`, `core/timer-engine`, `core/database`, `core/backup`: no T4 review, no accuracy run for the rc.
- **CI note (rc session):** `TransitionJankTest.savedSheetInAndOut` (API 36) failed twice on `df439e23` and passed on the third run. The sheet measured as on the last green run (16/17 janky frames against 17/19); only the allowance from the run's reference measurement dropped (15.4/16.3 against 18/20). `SavedSheet.kt` is unchanged in v0.11.0. The gate's sensitivity to the reference on API 36 is a candidate for a later harness ticket.
- **The final session (#371), after the PO's approval of the 09 §4 „v0.11.0" checklist and the design review:** if the PO finds something, an rc.2 goes through the process first. Otherwise: set `umamit.version=0.11.0` in a commit on the PR branch, CI green, merge #372 into `main` (merge commit), the three accuracy variants on the merge commit (09 §4a, `accuracy.yml` with the full SHA), then dispatch `release.yml` for `0.11.0` (110099) from `main`, verify APK and certificate, restart the branch from `main`.
- **Open (not blocking):**
  - **The launcher ANR on the API 35 emulator:** on `main` `1933bc15` every `TransitionJankTest` case failed twice behind „Application Not Responding: com.google.android.apps.nexuslauncher" (the same tree passed on the PR head); the third run was green. The `ForeignAnrDialog` handler logged nothing for it (0 lines in job 139316775975), so it does not catch the launcher's ANR on API 35: a harness gap for a later ticket.
  - `ReceiveRecipeDeviceTest` no longer proves grant enforcement; the phone check covered it on v0.9.0 rc.2.
- **Release dispatch hiccup (2026-11-25):** `workflow_dispatch` of `release.yml` answered HTTP 500/502 four times with no run created; the fifth try queued it. Check the runs list before retrying, so nothing is dispatched twice.
- **Still true:** umamit.db v9 (no migration in v0.10.0 or v0.11.0); the network has three uses (D-135); `checkManifestPermissions` passes unchanged.
- **Branch:** `claude/inspiring-ritchie-8hfw00`, restarted from `main` `1933bc15` after the v0.10.0 merge; draft PR #372 (v0.11.0). The v0.10.0 sessions were assigned other branches (the final session `claude/intelligent-knuth-3fje0d`) and did not use them. A new session with another assigned branch works here directly.
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
  - **CI status by API:** `gh api repos/katznmeik/UmamiT/actions/runs/<id>` and `…/runs/<id>/jobs` (GET) work for run and job status; a background `until` loop on them replaces polling by hand (2026-11-26).
  - **Copy check:** the build session compared 03's copy with `strings.xml` by a scratchpad script (lost with the container): every table cell of SCR-33/SCR-35/SCR-36 by text, and each topic's Intro/steps/Tipp against `howto_<n>_intro`/`_step<k>`/`_tip` by key, quotes „…“ and no-break spaces normalised. Write it again if copy changes (2026-11-26).
  - **Robolectric SDK 35 jar:** not fetched in the build session's container; the full local gate (`:core:timer` tests) needs it (`docs/plans/v0.4.0-resume.md` §3).
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
  - API 36: *timer → landscape Journal* gates since 2026-11-24 with one janky frame of margin, and a failed reference measurement now fails `timerFromLandscapeJournal` too; rerun once before investigating.
  - API 36 emulator: `ProcessDeathTest`'s two-brews case, and `TransitionJankTest.savedSheetInAndOut` on a slow emulator.
  - `release.yml`'s „Unit tests" step: `JournalPerformanceTest`'s wall-clock budget. It fails before the tag is made, so rerunning the failed jobs is safe.
- **Jank coverage of #331:** `TransitionJankTest` taps a tea row (`openSetup()`) but measures only the picker's in and out and setup → timer, not the tap → setup hand-over itself. The PO's eye is the check.
- **accuracy.yml, alongside:** its setup guard can stop the run before any step on a slow emulator („too slow, 14 s of step 1 passed before the screen went off"); rerun the failed job (2026-11-14).

## Phase

| Item | Status |
|---|---|
| Current milestone | **v0.11.0** (the tour reworked, D-137): `v0.11.0-rc.1` published 2026-11-26 (110001, from `1e9153a1`), waiting for the PO's checklist and design review, then the final session (#371); before it **v0.10.0** (D-136) **released 2026-11-25** (100099, from `main` `1933bc15`); M5 / 1.0 postponed until the PO's go |
| Development go from PO | ✅ **In effect** since 2026-10-22 09:05 Europe/Berlin (D-038) |
| Working branch | `claude/inspiring-ritchie-8hfw00`, restarted from `main` `1933bc15` after the v0.10.0 merge |
| Repository | `katznmeik/UmamiT` (renamed from `UmamiTea` on 2026-10-21; old URLs redirect) |
| Open PR | #372 (draft, v0.11.0) |
| Latest APK | rc [`v0.11.0-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.11.0-rc.1) (pre-release, versionCode 110001, from `1e9153a1`, APK SHA-256 `15143b6e…40c6`, certificate verified); before it [`v0.10.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.10.0) (full release, versionCode 100099, from `main` `1933bc15`, APK SHA-256 `ed5d151b…142c`, certificate verified); earlier: rc [`v0.10.0-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.10.0-rc.1) (pre-release, versionCode 100001, from `5978b729`, APK SHA-256 `7fea0c3b…f7b9`, certificate verified); before it [`v0.9.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.9.0) (full release, versionCode 90099, from `main` `f663318b`, APK SHA-256 `6dd9f74f…4f4b`, certificate verified); earlier: rc [`v0.9.0-rc.2`](https://github.com/katznmeik/UmamiT/releases/tag/v0.9.0-rc.2) (pre-release, versionCode 90002, from `f243d7fd`, APK SHA-256 `83935b9a…c31b`, certificate verified); rc [`v0.9.0-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.9.0-rc.1) (pre-release, versionCode 90001, from `8cfa6a91`, APK SHA-256 `dd961af8…e852`, certificate verified); [`v0.8.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.8.0) (full release, versionCode 80099, from `main` `60e07bbe`, APK SHA-256 `b67a6901…2016`, certificate verified); earlier: rc [`v0.8.0-rc.2`](https://github.com/katznmeik/UmamiT/releases/tag/v0.8.0-rc.2) (pre-release, 80002, from `95da6ad5`, APK SHA-256 `b0396be0…7f79`, certificate verified); rc [`v0.8.0-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.8.0-rc.1) (pre-release, 80001, from `9c0e4225`, certificate verified); [`v0.7.2`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.2) (full release, versionCode 70299, from `main` `c5f19e8e`, APK SHA-256 `efc2cca6…20fa`, certificate verified); earlier: rc [`v0.7.2-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.2-rc.1) (pre-release, 70201, from `a8f78d4f`, certificate verified); [`v0.7.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.1) (full release, versionCode 70199, from `main` `6192bff5`, certificate verified); rc [`v0.7.1-rc.2`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.1-rc.2) (pre-release, 70102, from `e57861e0`); rc [`v0.7.1-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.1-rc.1) (pre-release, 70101, from `3fe102c0`); [`v0.7.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.0) (full release, versionCode 70099, from `main` `f3582466`, certificate verified); rc [`v0.7.0-rc.3`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.0-rc.3) (pre-release, 70003, from `73907f6d`); rc [`v0.7.0-rc.2`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.0-rc.2) (pre-release, 70002, from `ccc57917`); rc [`v0.7.0-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.0-rc.1) (pre-release, 70001, from `e37d4631`); [`v0.6.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.6.1) (full release, versionCode 60199, from `main` `22b84788`, certificate verified); rc [`v0.6.1-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.6.1-rc.1) (pre-release, 60101, from `62657833`); [`v0.6.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.6.0) (60099); [`v0.5.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.5.1) (50199); [`v0.5.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.5.0) (50099) |
| Decision numbers | Up to **D-137** used; the next free is **D-138** |

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

1. **v0.11.0-rc.1:** install it over v0.10.0 and work through the 09 §4 „v0.11.0" checklist (including the new items: mock-up size, the badge where it touches a menu or a second ring (topics 1, 2, 4, 6), the tour fades) and the design review (`docs/design/review/v0.11.0/README.md`); approve both in a session or on PR #372, or name what to change for rc.2.
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
