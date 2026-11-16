# PROJECT_STATE — UmamiT

> Handover between sessions: **only the current state**. Update it at the end
> of every session or phase. Finished releases, past plans and the session
> log are in [`docs/history.md`](docs/history.md); read that only when you
> need it (D-101).

**Last updated:** 2026-11-16 (v0.7.2 session 8, execution part 1). **`v0.7.1` is released** (versionCode 70199, from `main` `6192bff5`; details in `docs/history.md`). The v0.7.2 plan, audits, triage and restructuring design (D-121 – D-127) are done and approved. **Execution: #305 (deletions) and #306 (local simplifications) are done** on the draft milestone PR [#312](https://github.com/katznmeik/UmamiT/pull/312), head `2cab854f`; the fence and the proof hold. **Next:** #307 (the restructuring, its 12 steps in order), then #308 (the timer path, last and on its own).

## Next: v0.7.2 execution, part 2 (#307 → #308)

- **Model:** Opus `medium` orchestrator (12 §5.1), `/implement-spec #296`. Branch `claude/wonderful-davinci-fu8tqi`, draft PR #312. #307 at T3 (its 12 steps in its order; `docs/plans/v0.7.2-restructuring.md`, D-121 – D-125); #308 T3, T4 on a failure, last and on its own (D-126, D-127). Then the T3 milestone review and the T4 timer review, #309, #310.
- **Each ticket:** the fence (`git diff main -- core/timer core/timer-engine feature/brew` empty until #308, plus the fenced files of `v0.7.2-audit-timer.md` §„The app's timer wiring"), the plan's „Proof" and „Verification"; at most 2 implementers, one Gradle build at a time. A session stops at a ticket boundary with CI green and a handover here.
- **Session 8 facts to know:**
  - **Fenced parts routed to #308** (applying the triage's rule, recorded in the tickets): DAT-A-03 whole (its test `BrewSetupFromSessionTest` is in `feature/brew`); the fenced remainders of DAT-A-05 (`ActiveBrewDao.observeActiveTeaId`, now unused), DAT-A-09 (`RoomSessionRepositories.kt`), DAT-A-11 (`SessionMappers.kt` onto `stepTasting(…)`), DAT-A-15 (the `active_brew JOIN brew_session` fragment with `SessionDaos.kt`). All in #308's checklist.
  - **Answered in #306:** CAT-A-02 leaves `TeaLinkLoader.photo` as it is (observable differences); CAT-A-15 keeps the two breadcrumb-segment lists apart (they differ); DAT-A-13 leaves `TimerAppWiringTest`'s and `AddFromCatalogTest`'s own clocks.
  - **#306 touched `CatalogRefresher.kt`** (CAT-A-02, -03, -04: new `ImageFetches.kt`, `ShopUrls.kt`, `CatalogEntry.rows`, `parseProduct`), so take #307's line numbers again. `UmamitRoot.kt` changed too (UI-A-09 `shell`, UI-A-02 `RequestTaps.kt`), which #307's settings-routes step (D-125) works in.
  - **Worktrees:** an implementer's worktree is cut from the stale local `main`, not the branch; brief each one to `git reset --hard <branch head>` first, and update local `main` (`git fetch origin main && git branch -f main FETCH_HEAD`) before the fence check.
  - **Environment:** the mirror init script can't go into `~/.gradle/init.d` here (the permission check refused it); it lives in the session scratchpad and `gw.sh` passes it with `--init-script` (PO, 2026-11-16).
  - **Usage:** orchestrator 112 requests, 22.0 M cache read, 0.23 M cache write, 65 k output (summed from the transcript); five implementers about 0.89 M tokens in all.

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
| Current milestone | **v0.7.2** (cleanup review, D-119, D-120): approved by the PO (2026-11-15); step 1, the four audits, the triage (#303, D-121 – D-127) and the restructuring design (#304) done; execution: **#305 and #306 done** (2026-11-16, CI green), #307 next, then #308. **v0.7.1** (two ratings, smooth transitions) is released (2026-11-15) |
| Development go from PO | ✅ **In effect** since 2026-10-22 09:05 Europe/Berlin (D-038) |
| Working branch | `claude/wonderful-davinci-fu8tqi` (restarted from `main` `f0ffaf1a`; the v0.7.2 docs, #305 and #306 on top, head `2cab854f`) |
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
