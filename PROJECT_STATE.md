# PROJECT_STATE — UmamiT

> Handover between sessions: **only the current state**. Update it at the end
> of every session or phase. Finished releases, past plans and the session
> log are in [`docs/history.md`](docs/history.md); read that only when you
> need it (D-101).

**Last updated:** 2026-11-13. **Statistik (v0.7.0, M4 part 2) is specified:** the PO answered the grilling (Q1–Q20, D-115); spec issue #255 with tickets #256–#264 and the plan `docs/plans/v0.7.0.md` **wait for the PO's approval**. `v0.6.1` is released (versionCode 60199). **Next:** the v0.7.0 build session, once the PO approves the tickets. **After v0.7.0:** a code cleanup review before M5 (recorded on the PO's request, see below).

## Next: Statistik build (v0.7.0, M4 part 2; specified, waiting for approval)

- **Spec:** #255, D-115 (Q1–Q20), 02 §12 INS-01–INS-08, 03 SCR-18, 05 §6 `statsRange`/`statsTab`, 09 §4 „v0.7.0". **Tickets:** #256–#264 (tiers T2–T3, blockers in each). **Plan:** `docs/plans/v0.7.0.md` (task graph, order, verification).
- **Gate:** the build starts only after the PO approves the tickets and the plan (its per-ticket verification is proposed as in v0.6.1), with `/implement-spec #255`.
- **No timer code, no migration:** no T4 timer review; only the final release needs the accuracy run (09 §4a). `git diff main -- feature/brew core/timer core/timer-engine core/database/schemas` must stay empty.
- **Known from v0.6.0, not blocking:** #231's „smooth with 10 000 sessions" is shown by JVM timing only. A tab that comes back to the Journal can show the earlier list for a frame until the next build.
- **Open from D-114 (Claude's note, accepted):** the activity re-creation after the *Design* hand-over can still meet an alert starting in the same ≈ 0.5 s.
- **Environment:** Maven Central answered HTTP 429 on 2026-11-11 and 2026-11-12; a local Gradle
  init script (`~/.gradle/init.d/mirror.gradle`, not in the repository) points every
  `repo.maven.apache.org` repository (`settingsEvaluated` plugin and dependency repositories, `allprojects`,
  `buildscript`) at Google's Maven Central mirror (`maven-central.storage-download.googleapis.com`), which the
  „UmamiT" environment reaches. Robolectric's runtime jars (SDK 35 `15-robolectric-13954326-i7`, SDK 36
  `16-robolectric-13921718-i7`) are pre-fetched from the mirror and checked against the sha512 as
  `docs/plans/v0.4.0-resume.md` §3 shows. One Gradle build at a time (gw.sh/gate.sh, same §3). A new container
  needs all of this again (the specify session of 2026-11-13 set it up in its own container).
- **Release dispatch:** `release.yml` refuses a commit without a successful CI run, so dispatch it only after CI on the merge commit in `main` has finished. `accuracy.yml` needs the full 40-character SHA as `ref`.

## After v0.7.0: code cleanup review (planned, not yet specified)

Claude's proposal of 2026-11-13, recorded on the PO's request. It is **not a go and not a decision**: scope, release number and the model tier are open and go through a grilling round once v0.7.0 is released (rule 1).

- **Goal:** remove what is no longer needed, simplify, and restructure only where a finding shows concrete pain. **No behaviour change.**
- **When:** its own release between v0.7.0 and M5 (proposed `v0.7.1`). Not earlier, because the v0.7.0 tickets are written against today's structure and Statistik is the last feature that adds queries and UI patterns. Not later, because M5's Baseline Profile, performance budgets and backup verification should measure the final structure.
- **Step 1, mechanical evidence (no model reading):** R8's usage report from the release build, Lint for unused resources, an unused-dependency check, a list of the largest files.
- **Step 2, read-only audit:** one fresh session per group, each writing a findings file (place, category delete / simplify / restructure, risk, size); about the top 15 findings per group, no style points. Groups by main lines (2026-11-13): catalog (`core:catalog-parser`, `core:network`, `feature:catalog`) ≈ 8 500; data (`core:data`, `database`, `datastore`, `domain`, `model`) ≈ 15 100; timer path (`core:timer`, `core:timer-engine`, `feature:brew`) ≈ 17 800; UI (`core:designsystem`, `core:ui`, the other features, `app`) ≈ 27 500. Tests (≈ 94 000 lines) are not read line by line: only where main code goes or step 1 shows duplicated fixtures.
- **Step 3, triage with the PO:** the PO picks the findings. Each restructuring becomes a decision and an update of `06-architecture.md`; then tickets.
- **Step 4, execution by risk** (`/implement-spec`, one milestone PR): deletions, then local simplifications, then restructuring, the timer path last and on its own. Proof of no behaviour change: unchanged Roborazzi goldens, unchanged Room schemas and DataStore keys, the timer tests (rule 8), the accuracy run (09 §4a).
- **Models (proposal, needs the PO's decision because D-101 has no Fable tier):** Fable 5.2 for the timer and data audits and the restructuring design; Opus `medium` for the other audits and for timer and data edits; Sonnet for deletions and other mechanical edits.
- **Usage:** audit the catalog group first and read what it consumed before committing to the rest; one group per session and per 5-hour window; the audits in one week, the execution in a later week with no other milestone work beside it.

## Phase

| Item | Status |
|---|---|
| Current milestone | **Statistik (v0.7.0, M4 part 2)**: specified (D-115, #255), tickets #256–#264 and the plan wait for the PO's approval. v0.6.1 is released (2026-11-13) |
| Development go from PO | ✅ **In effect** since 2026-10-22 09:05 Europe/Berlin (D-038) |
| Working branch | `claude/gracious-tesla-1edym3` (from `main` `bc410bbc` after #265) |
| Repository | `katznmeik/UmamiT` (renamed from `UmamiTea` on 2026-10-21; old URLs redirect) |
| Open PR | the docs PR that records the cleanup review (this file only), merged when CI is green; the specify PR #265 is merged |
| Latest APK | [`v0.6.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.6.1) (full release, versionCode 60199, from `main` `22b84788`, certificate verified); rc [`v0.6.1-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.6.1-rc.1) (pre-release, 60101, from `62657833`); [`v0.6.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.6.0) (60099); [`v0.5.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.5.1) (50199); [`v0.5.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.5.0) (50099) |
| Decision numbers | Up to **D-115** used; the next free is **D-116** |

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

1. **Approve the v0.7.0 tickets and plan:** #256–#264 and `docs/plans/v0.7.0.md` (incl. its per-ticket verification); then start the build session.
2. **Optional, at any time:** the PO's own alarm or Vorwarnung recordings (`docs/design/assets/source/sound-alert-<name>.wav`, `sound-prealert-<name>.wav`; WAV, sound from the first sample). Each becomes one more entry in `PreAlertSound` / `AlertSound`.
3. **Optional:** install the Claude GitHub App on the repository (https://github.com/apps/claude/installations/select_target). Without it, PR CI results don't wake a session; the sessions check CI themselves.
4. **Environment:** cloud sessions must run in the personal environment **„UmamiT"** (created 2026-11-08: `dl.google.com`, Gradle and Maven hosts, `raw.githubusercontent.com`, `www.tee-kontor-kiel.de`). The plain „Default" environment blocks `dl.google.com`, so the Android plugin doesn't resolve.

## Open for later

- **Ideas the PO kept for later (D-112, 09 §5):** custom descriptors, manual journal entries, brew coach tips (notes per infusion came with v0.6.1, D-114).
- **Parked from v0.3.0:** the two *Pause* buttons and the shortened tea name in the brew row at font 2.0.
- **#58:** its remaining optional review findings stay open for later milestones.
- **Images:** all delivered. A6 (Journal) is built in since v0.6.0 (#231); A9 (Statistik) comes with v0.7.0 (#259).

## Known caveats

- Mockup images still show the old name „UmamiTea".
- Live Update / ProgressStyle and the full-screen alert were verified on the Galaxy with `v0.0.2-probe`; the full timer keeps those patterns (D-043, D-044, `docs/plans/M1-android16-api-notes.md`). Now Bar presence is best effort.
- Tee-Kontor uses Cloudflare; refreshes may get blocked (handled by stopping without changes).
- After a reboot, alerts fire only once the phone has been unlocked (E2); Samsung's automatic overnight restart should be disabled (ONB-02).
- `ProcessDeathTest` (two brews, process death; emulator, API 36) can fail on its single read of the diagnostics log; rerun CI (D-112 Q19). Its brew-and-quick-timer test failed once on API 35 on a timing bound (2327 ms against 2 s, run 43094657346, runners degraded).
- Robolectric fetches its runtime jar at test time; a network hiccup has failed CI before. Rerun once and comment on the PR.
