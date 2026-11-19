# PROJECT_STATE — UmamiT

> Handover between sessions: **only the current state**. Update it at the end
> of every session or phase. Finished releases, past plans and the session
> log are in [`docs/history.md`](docs/history.md); read that only when you
> need it (D-101).

**Last updated:** 2026-11-19 (v0.9.0 build session 3: #346 done on PR #345). **Next:** the v0.9.0 rc session (#343).

## Next: the v0.9.0 rc session (#343)

- **Approved and building:** spec #333, tickets #334–#344, #330 and (2026-11-18, D-131) **#346**, all `ready-for-agent`. Draft milestone PR [#345](https://github.com/katznmeik/UmamiT/pull/345).
- **Done** (verified by the orchestrator, CI on the PR): build session 1: #334, #340, #335, #336, #337, #330. Build session 2: #341 Rezept teilen, #338 SCR-26, #339 backup reminder, #342 Rezept übernehmen (+ two follow-ups from the PO's answers). Build session 3: #346 *Alle Daten löschen* (`clearApplicationUserData()`; the PO's answers in D-131's build notes). Details in `docs/history.md` „v0.9.0: build session 2" and „build session 3".
- **rc session (#343):** the T3 milestone review with the **import path at T4** (D-130 Q15: #335, #336, and #342's `ReceiveRecipeActivity`); `/review-animations` (T2); fix the findings; renders in `docs/design/review/v0.9.0/` (SCR-26 states incl. #346's row, dialog and notice, the import dialogs, SCR-12 ⋮, SCR-32 incl. the package sheet); finish `docs/whats-new/v0.9.0.md` (D-132); the full local gate (`gate.sh`) on the rc commit; dispatch `v0.9.0-rc.1` (90001).
- **Carried to #343 (fix or review):**
  - #346 at font 2.0: the typed field's two-line label crowds the body's last line in the delete dialog, as in the ERSETZEN dialog (design review).
  - #346's wipe is proven on the JVM only as „empty storage → first start"; the real wipe and which grants survive on One UI 9 are the new 09 §4 „v0.9.0" checklist item.
  - #336's late-failure undo (D-130 build note, PO 2026-11-18: undo right away, counted from the import).
  - The start gate in `UmamitApplication.onCreate` (a file check before timer recovery, on the path an alarm-started process takes) and **the start-up launches have no error guard**: a throw while building a `Lazy` graph escapes `recoveryScope` (seen as `UncaughtExceptionsBeforeTest`; the `NetworkModule` cause is fixed, the gap is not; a scope handler would also change timer-recovery failures — a design point for the PO).
  - `ReceiveRecipeActivity` waits on its main thread up to 2 s for the copy (the URI grant ends with the `NoDisplay` activity); the alternative is passing the read grant on to `MainActivity` and reading there (D-130 build note #342).
  - `UseCaseDataBackup` (SCR-26's adapter: the switch and the export's reschedule) has no test.
  - #334's taste fixture repeats values and its read-back goes through its own mappers.
- **Key rules for the build:** no timer code (an empty `git diff aed2b877 -- feature/brew core/timer core/timer-engine` after every ticket, else stop and ask), so no T4 timer review and no accuracy runs; no migration (umamit.db v9); no new permission (`checkManifestPermissions` unchanged).
- **v0.8.0 is done:** PR #328 merged into `main` (`60e07bbe`), [`v0.8.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.8.0) released (80099).
- **1.0 (M5) is postponed** (D-128 Q3).
- **Branch:** `claude/inspiring-ritchie-8hfw00`, from `main` `60e07bbe`, PR #345. The specify session and build sessions 1–3 (assigned `claude/lucid-hopper-qc2hw0`, `claude/wizardly-fermi-djdpxn`, `claude/tender-archimedes-ax6bw9`, `claude/youthful-bardeen-0yai1v`) did not use their assigned branches. `main` has moved on by #348 (`release.yml`, D-132); no conflict with the branch. A new session with another assigned branch works here directly.
- **Session facts still true:**
  - **CI:** a new push cancels the PR's running CI (concurrency group), so only the head is gated. Push a group of verified steps, then let one run finish before the next push; a docs-only push skips the build jobs, so push docs after a green code run. `gh` is for search only here (except `gh api …/actions/jobs/<id>/logs`, which worked for a job log): check CI with the GitHub MCP tools (`pull_request_read` `get_check_runs`, `actions_get`, `actions_run_trigger` `rerun_failed_jobs`).
  - **Worktrees:** never remove an agent worktree while a Gradle build runs: `spotlessCheck` scans `.claude/worktrees/` and fails on a vanished path (2026-11-17). Brief each implementer to `git reset --hard <branch head>` first (a local, unpushed head works too). Remove worktrees with `git worktree remove --force --force`, then `rm -rf`, `git worktree prune`, and delete the `worktree-agent-*` branch.
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
| Current milestone | **v0.9.0** (export/import, CSV, reminder, shared recipes, *Alle Daten löschen*; D-128, D-130, D-131) specified 2026-11-18, spec #333, tickets and plan **approved by the PO 2026-11-18**; build sessions 1–3 done (steps 1–5b), the rc session next; **v0.8.0** released 2026-11-18; M5 / 1.0 postponed until the PO's go |
| Development go from PO | ✅ **In effect** since 2026-10-22 09:05 Europe/Berlin (D-038) |
| Working branch | `claude/inspiring-ritchie-8hfw00`, restarted from `main` `60e07bbe` after the v0.8.0 merge; v0.9.0 spec docs and steps 1–3 on top (PR #345) |
| Repository | `katznmeik/UmamiT` (renamed from `UmamiTea` on 2026-10-21; old URLs redirect) |
| Open PR | [#345](https://github.com/katznmeik/UmamiT/pull/345) (v0.9.0, draft) |
| Latest APK | [`v0.8.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.8.0) (full release, versionCode 80099, from `main` `60e07bbe`, APK SHA-256 `b67a6901…2016`, certificate verified); earlier: rc [`v0.8.0-rc.2`](https://github.com/katznmeik/UmamiT/releases/tag/v0.8.0-rc.2) (pre-release, 80002, from `95da6ad5`, APK SHA-256 `b0396be0…7f79`, certificate verified); rc [`v0.8.0-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.8.0-rc.1) (pre-release, 80001, from `9c0e4225`, certificate verified); [`v0.7.2`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.2) (full release, versionCode 70299, from `main` `c5f19e8e`, APK SHA-256 `efc2cca6…20fa`, certificate verified); earlier: rc [`v0.7.2-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.2-rc.1) (pre-release, 70201, from `a8f78d4f`, certificate verified); [`v0.7.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.1) (full release, versionCode 70199, from `main` `6192bff5`, certificate verified); rc [`v0.7.1-rc.2`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.1-rc.2) (pre-release, 70102, from `e57861e0`); rc [`v0.7.1-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.1-rc.1) (pre-release, 70101, from `3fe102c0`); [`v0.7.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.0) (full release, versionCode 70099, from `main` `f3582466`, certificate verified); rc [`v0.7.0-rc.3`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.0-rc.3) (pre-release, 70003, from `73907f6d`); rc [`v0.7.0-rc.2`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.0-rc.2) (pre-release, 70002, from `ccc57917`); rc [`v0.7.0-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.0-rc.1) (pre-release, 70001, from `e37d4631`); [`v0.6.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.6.1) (full release, versionCode 60199, from `main` `22b84788`, certificate verified); rc [`v0.6.1-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.6.1-rc.1) (pre-release, 60101, from `62657833`); [`v0.6.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.6.0) (60099); [`v0.5.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.5.1) (50199); [`v0.5.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.5.0) (50099) |
| Decision numbers | Up to **D-132** used; the next free is **D-133** |

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

1. **v0.9.0:** nothing until rc.1 (spec, tickets, plan, items 17–27 and #346 approved 2026-11-18; #346's open points answered 2026-11-19).
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
