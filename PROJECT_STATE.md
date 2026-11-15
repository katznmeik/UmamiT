# PROJECT_STATE — UmamiT

> Handover between sessions: **only the current state**. Update it at the end
> of every session or phase. Finished releases, past plans and the session
> log are in [`docs/history.md`](docs/history.md); read that only when you
> need it (D-101).

**Last updated:** 2026-11-15 (`/review-animations` and its grilling round). **`v0.7.1-rc.1` is released** (pre-release from `3fe102c0`, PR #290). The PO's `/review-animations` (comment on #290) led to a grilling round: the arrival fade holds ≈ 150 ms on pushed screens and the tab roots, and the Journal's late-list cross-fade uses `exitFade` (D-117 build note „Arrival fade", *Accepted (PO)*), **built in v0.7.1 for an rc.2**. Tickets **#291** (T3) and **#292** (T2) wait for the PO's approval (`needs-triage`). **Next:** the PO approves the tickets, then an rc.2 build session (below). **After v0.7.1:** the code cleanup review (v0.7.2, below).

## Now: v0.7.1-rc.1 with the PO (D-116, D-117)

- **Built 2026-11-14/15** (build session): #280–#287 and the review round on PR #290 (draft until the PO's approval). T3 milestone review and T4 timer-path review done (T4: 0 blockers, 4 notes in the PR). Full local gate and the timer tests (04 §12) pass on the rc code; CI green on `3fe102c0`; accuracy standard, alongside, two-brews **PASS** on `427087e0` (after it only `TransitionJankTest`'s gated set and docs changed).
- **The PO's answers this session (D-117 build notes):** A-08 theme in place at once; A-11 measure first (measured within the limit, no change); A-16 icons at the fade's midpoint; the relative jank gate; gate the stable transitions; the PO runs `/review-animations` (user-invocable only).
- **Jank gate as released:** gated on API 36 only what never exceeded 3.0× over all runs (resume, every pop, Gespeichert in/out, both sheets' exit, timer → shell); reported only: the four tab switches, Teeauswahl in, Schritte-Sheet in, tea detail push, cold start, setup → timer, session page push. Claude first listed cold start, setup → timer and session page push as stable and corrected that after they failed on the rc commit (build note „Gated set corrected"). Runs: audit §7.
- **Reverted:** A-10 (visited tabs kept composed): no gain in the 5-run data, but one 7-run run with it had all tabs at 1.8–2.4× (without: up to 5.7×). Candidate for M5 with a measurement on the Galaxy.
- **Open notes (not blocking):** T4 N1 (band still decoded on the main thread on the full-screen-intent cold path), N3 (another slot's alert during a closing timer route shows in the fading route ≤ 200 ms, pre-existing), N4 (`rememberStartsAfterStop` ignores the first start of a composition created while stopped). The clipped „Zeiten ins Rezept übernehmen" at font 2.0 on the Gespeichert sheet (pre-existing).
- **rc.2 (PO, 2026-11-15):** spec written (D-117 build note „Arrival fade", 03 §3.6, audit A-09/A-12 amended, plan „rc.2"); tickets #291 (arrival hold in `fadeInOnArrival`, T3: it reaches `BrewSetupScreen`, so the timer tests, and the T4 timer review if `feature/brew`, `core/timer` or `core/timer-engine` change) and #292 (Journal ghost with `exitFade`, T2). The PO's rc.1 checklist and design review are not yet reported; ask for them in the rc.2 session too. The chart's two morphs (`RatingLineChart.kt`, `/review-animations` row 4) are not in rc.2: Claude's proposal is the v0.7.2 cleanup, open to the PO.
- **Next session:** after the PO approves #291/#292: build them (`/implement-spec #279`, these two tickets only), the T3 review of the diff, timer tests, all three accuracy variants (the setup screen changed), `v0.7.1-rc.2`, then the PO's check. Otherwise, as before: if the PO's checklist, design review or `/review-animations` find something: an rc.2 (grilling round for anything that changes a 03 §3.6 value or behaviour, rule 1). If approved: #289 — set `umamit.version=0.7.1` on the branch, CI green, merge #290 (merge commit), all three accuracy variants on the merge commit, `release.yml` with `0.7.1` from `main`.

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
- **Release dispatch:** `release.yml` refuses a commit without a successful CI run, so dispatch it only after CI on the merge commit in `main` has finished. `accuracy.yml` needs the full 40-character SHA as `ref`.
- **accuracy.yml, alongside:** its setup guard can stop the run before any step on a slow emulator („too slow, 14 s of step 1 passed before the screen went off"); rerun the failed job (2026-11-14).

## After v0.7.1: code cleanup review (planned for v0.7.2, not yet specified; D-116)

Claude's proposal of 2026-11-13, recorded on the PO's request. It is **not a go and not a decision**: scope, release number and the model tier are open and go through a grilling round once v0.7.0 is released (rule 1).

- **Goal:** remove what is no longer needed, simplify, and restructure only where a finding shows concrete pain. **No behaviour change.**
- **When:** its own release between v0.7.0 and M5 (proposed `v0.7.2`, after v0.7.1 per D-116). Not earlier, because the v0.7.0 tickets are written against today's structure and Statistik is the last feature that adds queries and UI patterns. Not later, because M5's Baseline Profile, performance budgets and backup verification should measure the final structure.
- **Step 1, mechanical evidence (no model reading):** R8's usage report from the release build, Lint for unused resources, an unused-dependency check, a list of the largest files.
- **Step 2, read-only audit:** one fresh session per group, each writing a findings file (place, category delete / simplify / restructure, risk, size); about the top 15 findings per group, no style points. Groups by main lines (2026-11-13): catalog (`core:catalog-parser`, `core:network`, `feature:catalog`) ≈ 8 500; data (`core:data`, `database`, `datastore`, `domain`, `model`) ≈ 15 100; timer path (`core:timer`, `core:timer-engine`, `feature:brew`) ≈ 17 800; UI (`core:designsystem`, `core:ui`, the other features, `app`) ≈ 27 500. Tests (≈ 94 000 lines) are not read line by line: only where main code goes or step 1 shows duplicated fixtures.
- **Step 3, triage with the PO:** the PO picks the findings. Each restructuring becomes a decision and an update of `06-architecture.md`; then tickets.
- **Step 4, execution by risk** (`/implement-spec`, one milestone PR): deletions, then local simplifications, then restructuring, the timer path last and on its own. Proof of no behaviour change: unchanged Roborazzi goldens, unchanged Room schemas and DataStore keys, the timer tests (rule 8), the accuracy run (09 §4a).
- **Models (proposal, needs the PO's decision because D-101 has no Fable tier):** Fable 5.2 for the timer and data audits and the restructuring design; Opus `medium` for the other audits and for timer and data edits; Sonnet for deletions and other mechanical edits.
- **Usage:** audit the catalog group first and read what it consumed before committing to the rest; one group per session and per 5-hour window; the audits in one week, the execution in a later week with no other milestone work beside it.

## Phase

| Item | Status |
|---|---|
| Current milestone | **v0.7.1** (D-116, D-117): `v0.7.1-rc.1` with the PO. **v0.7.0** (Statistik, M4 part 2) is released (2026-11-14) |
| Development go from PO | ✅ **In effect** since 2026-10-22 09:05 Europe/Berlin (D-038) |
| Working branch | `claude/wonderful-davinci-fu8tqi` (restarted from `main` `d8b85319`) |
| Repository | `katznmeik/UmamiT` (renamed from `UmamiTea` on 2026-10-21; old URLs redirect) |
| Open PR | #290, the v0.7.1 milestone PR (draft until the PO approves rc.1) |
| Latest APK | rc [`v0.7.1-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.1-rc.1) (pre-release, 70101, from `3fe102c0`); [`v0.7.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.0) (full release, versionCode 70099, from `main` `f3582466`, certificate verified); rc [`v0.7.0-rc.3`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.0-rc.3) (pre-release, 70003, from `73907f6d`); rc [`v0.7.0-rc.2`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.0-rc.2) (pre-release, 70002, from `ccc57917`); rc [`v0.7.0-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.0-rc.1) (pre-release, 70001, from `e37d4631`); [`v0.6.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.6.1) (full release, versionCode 60199, from `main` `22b84788`, certificate verified); rc [`v0.6.1-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.6.1-rc.1) (pre-release, 60101, from `62657833`); [`v0.6.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.6.0) (60099); [`v0.5.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.5.1) (50199); [`v0.5.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.5.0) (50099) |
| Decision numbers | Up to **D-117** used; the next free is **D-118** |

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

1. **Test `v0.7.1-rc.1`:** install over `v0.7.0`; the checklist 09 §4 „v0.7.1" (in PR #290); the design review `docs/design/review/v0.7.1/README.md` (Claude's details 1–8 are open to correction); `/review-animations` on the v0.7.1 changes. Then start the next session (prompt at the end of the build session).
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
