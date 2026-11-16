# PROJECT_STATE — UmamiT

> Handover between sessions: **only the current state**. Update it at the end
> of every session or phase. Finished releases, past plans and the session
> log are in [`docs/history.md`](docs/history.md); read that only when you
> need it (D-101).

**Last updated:** 2026-11-16 (v0.7.2 session 10, execution part 3). **`v0.7.1` is released** (versionCode 70199, from `main` `6192bff5`; details in `docs/history.md`). The v0.7.2 plan, audits, triage and restructuring design (D-121 – D-127) are done and approved. **Execution: #305, #306, #307 and #308's first two groups are done** on the draft milestone PR [#312](https://github.com/katznmeik/UmamiT/pull/312); the proof holds. **Next:** #308's third group (alert path, arbitration, recovery, with D-126), in one session.

## Next: v0.7.2 execution, part 4 (#308's alert-path group)

- **Model:** Opus `medium` orchestrator (12 §5.1), `/implement-spec #296`. Branch `claude/wonderful-davinci-fu8tqi`, draft PR #312. The third group of #308 („Alert path, arbitration, recovery"), in its checklist order, **in one session** (PO, 2026-11-16): TMR-A-14, TMR-A-01, TMR-A-11, TMR-A-12, TMR-A-13, then D-126 last (DAT-A-14 = step 1, TMR-A-06 = step 2, blocked by step 1). Every step T3, T4 on a failure; the 04 §12 timer tests after each step. These steps share `core/timer` files (`TransitionEffects.kt`, `PersistedTimer.kt`, `BrewSessionController.kt`), so run them one at a time; two implementers only where files are truly disjoint. Then the T3 milestone review and the T4 timer review (#308's whole diff), #309, #310.
- **Done in session 10 (#308 groups 1 and 2, 20 steps; CI green on the code head `ec0d7001`, run 43912284383, both emulator jobs included):**
  - **PO answers (2026-11-16, as options, recorded in #308):** one `UnlockedDevice.kt` in `core/timer/src/sharedAndroidTest/`, a source folder that `app` and `core:timer` both add (no dependency); UI-A-07's fix action `rememberReliabilityFixAction` in `core:ui`, generic over the check type (it takes the notification check as a value, not a predicate: the permission dialog's „denied for good" path needs it without a check in hand); DAT-A-03 through a public test helper `FakeCollection.deleteTeaRows` (sessions keep their tea id, as the old fake did).
  - **Found, out of scope:** #313 (`needs-triage`): `BrewSetupViewModel` throws when a session's tea id is null after a real delete.
  - **Build-file pattern:** `android { sourceSets }` throws a ClassCastException in these modules; use `extensions.configure<LibraryExtension|ApplicationExtension> { sourceSets.getByName(…).kotlin.directories.add(…) }` (D-127's `src/sharedTest` too) and add the folder to `detekt { source.from(…) }`.
  - **Helpers changed shape:** `rememberExpandedSheetState` has `saveable` (the steps sheet); `core:ui` `Stepper` has `valueMinWidth`; `StartQuestions` (`core:timer` `controller/`) holds the refused-start question for setup, quick timer and Start; `ReliabilityChecks` (`core:timer` `reliability/`).
  - **For the T4 timer review (also in #308):** the D-125 `SettingsNav.alertKey` path no test covers; TMR-A-07's `slotSession` (no test fails `getSession`: the seal's silent fallback and the stock's logged retry hold by construction); TMR-A-04's „any alert closes the question" (`openedIn` was null at every site).
  - **CI:** `TransitionJankTest.savedSheetInAndOut` (API 36) failed once on `e0057946` and passed on rerun (comment on #312).
  - **Stale prose:** `docs/specs/10-open-questions-and-decisions.md` (≈ l. 6898, 7154) and `docs/plans/v0.7.2-evidence.md:356` still say `ControllerBrewTimer` implements both seams; fix in the milestone review's doc pass.
  - **Usage (session 10):** orchestrator ≈ 105 requests, 20.5 M cache read, 0.21 M cache write, 46 k output; four implementers 317 requests, 44.0 M cache read, 2.42 M cache write, 167 k output (from the transcripts).
- **#307's deviations from the design** (both no behaviour change; `SettingsNav.openVesselResumedOnly`, `SettingsNav` in `SettingsScreens.kt`, the kept `TooManyFunctions`, `MAX_LISTED` in `CatalogRefreshRun`, D-122's `FakeShop.kt`) and session 9's usage: `docs/history.md` „Execution part 2". The milestone review reads them as decided, not as defects.
- **Session facts still true:**
  - **CI:** a new push cancels the PR's running CI (concurrency group), so only the head is gated. Push a group of verified steps, then let one run finish before the next push; a docs-only push skips the build jobs, so push docs after a green code run. `gh` is for search only here (except `gh api …/actions/jobs/<id>/logs`, which worked for a job log): check CI with the GitHub MCP tools (`pull_request_read` `get_check_runs`, `actions_get`, `actions_run_trigger` `rerun_failed_jobs`).
  - **Worktrees:** brief each implementer to `git reset --hard <branch head>` first (a local, unpushed head works too). Remove worktrees with `git worktree remove --force --force`, then `rm -rf`, `git worktree prune`, and delete the `worktree-agent-*` branch.
  - **Environment:** the mirror init script lives in the session scratchpad and `gw.sh` passes it with `--init-script`. It has to be written again in a new container: a `maven { name = 'gcsMirror' }` made first (`remove` + `addFirst`) in `pluginManagement`, `dependencyResolutionManagement` and every `buildscript` (`beforeSettings` and `settingsEvaluated`), URL rewrites only on project repositories. Written so on 2026-11-16 (sessions 9 and 10), it worked first time. The three timer test tasks take ≈ 8½ min cold.

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
- **Release dispatch:** `release.yml` refuses a commit without a successful CI run, so dispatch it only after CI on the merge commit in `main` has finished. `accuracy.yml` needs the full 40-character SHA as `ref`.
- **accuracy.yml, alongside:** its setup guard can stop the run before any step on a slow emulator („too slow, 14 s of step 1 passed before the screen went off"); rerun the failed job (2026-11-14).

## Phase

| Item | Status |
|---|---|
| Current milestone | **v0.7.2** (cleanup review, D-119, D-120): approved by the PO (2026-11-15); step 1, the four audits, the triage (#303, D-121 – D-127) and the restructuring design (#304) done; execution: **#305, #306, #307 and #308's groups 1 and 2 done** (2026-11-16), #308's alert-path group next. **v0.7.1** (two ratings, smooth transitions) is released (2026-11-15) |
| Development go from PO | ✅ **In effect** since 2026-10-22 09:05 Europe/Berlin (D-038) |
| Working branch | `claude/wonderful-davinci-fu8tqi` (restarted from `main` `f0ffaf1a`; the v0.7.2 docs, #305 – #307 and #308's groups 1 and 2 on top, code head `ec0d7001`) |
| Repository | `katznmeik/UmamiT` (renamed from `UmamiTea` on 2026-10-21; old URLs redirect) |
| Open PR | **[#312](https://github.com/katznmeik/UmamiT/pull/312)** (draft): the v0.7.2 milestone PR |
| Latest APK | [`v0.7.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.1) (full release, versionCode 70199, from `main` `6192bff5`, certificate verified); rc [`v0.7.1-rc.2`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.1-rc.2) (pre-release, 70102, from `e57861e0`); rc [`v0.7.1-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.1-rc.1) (pre-release, 70101, from `3fe102c0`); [`v0.7.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.0) (full release, versionCode 70099, from `main` `f3582466`, certificate verified); rc [`v0.7.0-rc.3`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.0-rc.3) (pre-release, 70003, from `73907f6d`); rc [`v0.7.0-rc.2`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.0-rc.2) (pre-release, 70002, from `ccc57917`); rc [`v0.7.0-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.0-rc.1) (pre-release, 70001, from `e37d4631`); [`v0.6.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.6.1) (full release, versionCode 60199, from `main` `22b84788`, certificate verified); rc [`v0.6.1-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.6.1-rc.1) (pre-release, 60101, from `62657833`); [`v0.6.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.6.0) (60099); [`v0.5.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.5.1) (50199); [`v0.5.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.5.0) (50099) |
| Decision numbers | Up to **D-127** used; the next free is **D-128** |

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

1. **Nothing blocks the execution.** The PO starts its session (a later week, D-119 Q4). Installing `v0.7.1` over rc.2 stays optional.
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
- Robolectric fetches its runtime jar at test time; a network hiccup has failed CI before. Rerun once and comment on the PR.
