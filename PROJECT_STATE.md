# PROJECT_STATE — UmamiT

> Handover between sessions: **only the current state**. Update it at the end
> of every session or phase. Finished releases, past plans and the session
> log are in [`docs/history.md`](docs/history.md); read that only when you
> need it (D-101).

**Last updated:** 2026-11-16 (v0.7.2 session 12, the milestone review and timer review). **`v0.7.1` is released** (versionCode 70199, from `main` `6192bff5`; details in `docs/history.md`). The v0.7.2 plan, audits, triage and restructuring design (D-121 – D-127) are done and approved. **Execution (#305 – #308) and both reviews are done** on the draft milestone PR [#312](https://github.com/katznmeik/UmamiT/pull/312): the T3 milestone review and the T4 timer review found no Block, their fixes are in (code head `1da1e5fb`, CI green, run 43953846325). **Next:** #309 without its review (the proof, accuracy all three variants, rc.1).

## Next: v0.7.2 #309 (the proof, accuracy, rc.1)

- **Model:** Opus `medium` orchestrator (12 §5.1). Branch `claude/wonderful-davinci-fu8tqi`, draft PR #312. #309's first bullet, the T3 milestone review, is **done** (session 12, below); do not run it again. `/review-animations` is not run (plan „Release").
- **Steps (#309, plan §„Proof", §„Verification" 4, §„Release"):**
  1. the proof on the rc commit, each item recorded in #312 with its result: goldens and `core/database/schemas` against `main` (empty), the DataStore key list against the #297 baseline (`docs/plans/v0.7.2-evidence.md`), `verifyRoborazziDebug`, `scripts/check-frozen-schemas.sh`, `SchemaVersionsTest`, `checkManifestPermissions`, the full local gate (`gate.sh`, v0.4.0-resume.md §3), the 04 §12 timer tests (263 / 512 / 707, 1 skipped);
  2. `umamit.version=0.7.2-rc.1` in `gradle.properties` (versionCode 70201), commit, CI green;
  3. accuracy (09 §4a) on that commit, all three variants (standard, alongside, two-brews; #308 changed code), `accuracy.yml` with the full 40-character SHA;
  4. dispatch `release.yml` (`ref` = the branch's rc commit, `version` = `0.7.2-rc.1`), verify the certificate, the checklist 09 §4 „v0.7.2" to the PO (push notification).
- **Stops** when `v0.7.2-rc.1` is published and the PO has the checklist. Then #310 (the PO's check, merge, `v0.7.2` 70299 from `main`) in a later session (Sonnet, plan §„Sessions").
- **Reviews (session 12), no Block:**
  - **T3 (standards and spec, `git diff main`):** all 60 picked findings have a commit, nothing unpicked done, no behaviour change, proof items clean. Fixed: R-1 – R-4 dead imports (`8ac24588`, `06f3173e`, `5d073668`, `e4ab6cb6`), R-5 and R-6 formatting (`d5bd17e8`, `1da1e5fb`). Notes left: R-7 (`deleteVessel`'s test went with DAT-A-04; it was the only test of `brew_profile.vessel_id` set-null; optional to bring back with a raw `DELETE`), R-8 (`StatsDao` no longer re-emits on a descriptor-only change; same output).
  - **T4 (timer path, `d9e79cbe..c0116fc7` and `d2eee004`):** all seven items hold (D-125 `alertKey`, TMR-A-07 `slotSession`, TMR-A-04, TMR-A-14, TMR-A-12, TMR-A-13, D-126); the orchestrator reread the two untested ones (`alertKey`, `slotSession`). Fixed: T-4 stale `AlertPlayer` KDoc (`5d294a02`). Notes left: T-1 (the old session's seal and stock flows now run until the new read returns; one failed read blanks both), T-2 (pre-existing: after one failed read the seal stays null for the view model's life; a ticket only if the PO wants it), T-3 (no test pins `handOver`'s finish-on-failure or a live alert over open settings).
  - **Doc pass:** `f167499d` (10 ≈ l. 6593, 6710, 6898, 7154; evidence l. 356). The l. 6710 note was not in the brief's list: the same stale `ControllerBrewTimer` claim.
- **Done in session 12, with usage:** `docs/history.md` „v0.7.2: milestone and timer review done".
- **Done in sessions 10 and 11, with usage:** `docs/history.md` „v0.7.2: #308 done". #313 (`needs-triage`, found in session 10) stays out of v0.7.2.
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
| Current milestone | **v0.7.2** (cleanup review, D-119, D-120): approved by the PO (2026-11-15); step 1, the four audits, the triage (#303, D-121 – D-127) and the restructuring design (#304) done; execution: **#305 – #308 done** (2026-11-16); the milestone and timer reviews **done** (no Block); #309 without its review next. **v0.7.1** (two ratings, smooth transitions) is released (2026-11-15) |
| Development go from PO | ✅ **In effect** since 2026-10-22 09:05 Europe/Berlin (D-038) |
| Working branch | `claude/wonderful-davinci-fu8tqi` (restarted from `main` `f0ffaf1a`; the v0.7.2 docs, #305 – #308 and the review fixes on top, code head `1da1e5fb`) |
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
