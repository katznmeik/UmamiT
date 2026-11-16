# PROJECT_STATE — UmamiT

> Handover between sessions: **only the current state**. Update it at the end
> of every session or phase. Finished releases, past plans and the session
> log are in [`docs/history.md`](docs/history.md); read that only when you
> need it (D-101).

**Last updated:** 2026-11-16 (v0.7.2 session 3, the data audit). **`v0.7.1` is released** (versionCode 70199, from `main` `6192bff5`; details in `docs/history.md`). The PO approved the v0.7.2 plan and tickets (`docs/plans/v0.7.2.md`, spec #296, #297–#310; D-119, D-120). **Step 1 is done** (`docs/plans/v0.7.2-evidence.md`). **The catalog audit (#298) and the data audit (#300) are done:** `docs/plans/v0.7.2-audit-catalog.md` (CAT-A-01 … CAT-A-15) and `docs/plans/v0.7.2-audit-data.md` (DAT-A-01 … DAT-A-15), no code change, both on the draft milestone PR [#312](https://github.com/katznmeik/UmamiT/pull/312). **#299 answered (2026-11-16):** the PO keeps all three remaining audits as planned (D-119 build note „Remaining audits"). **Next:** session 4, the timer-path audit (#301).

## Next: v0.7.2 session 4, the timer-path audit (#301)

- **Model:** Fable 5.2 `medium` (T4-F), the audit in the session itself (no subagents). Branch `claude/wonderful-davinci-fu8tqi`, draft PR #312.
- **Order (#299, PO 2026-11-16):** session 4 timer path (#301, Fable 5.2 `medium`), session 5 UI (#302, Opus `medium`), then the triage (#303); one group per session and per 5-hour window.
- **Input:** `docs/plans/v0.7.2-evidence.md` (candidates only), ticket #301, and the two audit files for their format, scope tables and overlaps. Output: `docs/plans/v0.7.2-audit-timer.md` (one table, about the top 15, plan „Step 2"), IDs `TMR-A-01` …; the session's usage noted at the end. No code change; stops with the file pushed.
- **Session 3 facts to know:**
  - The data audit read about 9 800 of the group's 16 296 main lines in full (its scope table says which files; KDoc was skipped). Not read in full by any audit: seven `core:data` `catalog/` files (≈ 620 lines), the guide reader and the data tables of `core:domain`, most of `core:model`, the entities and migrations.
  - R8 found four members with no caller in the app (DAT-A-03, 04, 05, 12); the evidence lists stay candidates only.
  - **Overlaps with the timer path:** DAT-A-14 (splitting `RoomSessionRepositories.kt`, which holds `RoomActiveBrewStore` and the session lifecycle) is the timer's persistence and belongs with #308; the `PlannedStep` builders (`core/data/src/test/.../TimerTestSupport.kt:103–107` and `core:timer-engine`'s copy, evidence §5) and `ActiveBrewDao.clear` (only `ControllerHarness:156`) were left to #301.
  - **Other overlaps:** DAT-A-02 widens CAT-A-06 (one ticket); the spelled-out `WallClock.today()` in four view models is for the UI audit (#302).
  - **Usage:** this session has no `explain-usage` command; the usage was summed from the transcript's per-request records (the audit file's last section says how).
- **Group sizes (main lines):** catalog 13 668 as audited; data 16 296 as audited; timer path ≈ 17 800; UI ≈ 27 500; tests ≈ 94 000.

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
| Current milestone | **v0.7.2** (cleanup review, D-119, D-120): approved by the PO (2026-11-15); step 1 done (evidence file), catalog and data audits done (2026-11-16; #299: all three remaining audits kept); the timer-path audit is next. **v0.7.1** (two ratings, smooth transitions) is released (2026-11-15) |
| Development go from PO | ✅ **In effect** since 2026-10-22 09:05 Europe/Berlin (D-038) |
| Working branch | `claude/wonderful-davinci-fu8tqi` (restarted from `main` `f0ffaf1a`; evidence and the catalog and data audit commits on top) |
| Repository | `katznmeik/UmamiT` (renamed from `UmamiTea` on 2026-10-21; old URLs redirect) |
| Open PR | **[#312](https://github.com/katznmeik/UmamiT/pull/312)** (draft): the v0.7.2 milestone PR |
| Latest APK | [`v0.7.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.1) (full release, versionCode 70199, from `main` `6192bff5`, certificate verified); rc [`v0.7.1-rc.2`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.1-rc.2) (pre-release, 70102, from `e57861e0`); rc [`v0.7.1-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.1-rc.1) (pre-release, 70101, from `3fe102c0`); [`v0.7.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.0) (full release, versionCode 70099, from `main` `f3582466`, certificate verified); rc [`v0.7.0-rc.3`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.0-rc.3) (pre-release, 70003, from `73907f6d`); rc [`v0.7.0-rc.2`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.0-rc.2) (pre-release, 70002, from `ccc57917`); rc [`v0.7.0-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.0-rc.1) (pre-release, 70001, from `e37d4631`); [`v0.6.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.6.1) (full release, versionCode 60199, from `main` `22b84788`, certificate verified); rc [`v0.6.1-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.6.1-rc.1) (pre-release, 60101, from `62657833`); [`v0.6.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.6.0) (60099); [`v0.5.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.5.1) (50199); [`v0.5.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.5.0) (50099) |
| Decision numbers | Up to **D-120** used; the next free is **D-121** |

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

1. **Nothing until the triage (#303)** after the two remaining audits. Installing `v0.7.1` over rc.2 stays optional.
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
