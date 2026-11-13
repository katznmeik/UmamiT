# PROJECT_STATE — UmamiT

> Handover between sessions: **only the current state**. Update it at the end
> of every session or phase. Finished releases, past plans and the session
> log are in [`docs/history.md`](docs/history.md); read that only when you
> need it (D-101).

**Last updated:** 2026-11-13. **Statistik (v0.7.0, M4 part 2): built; `v0.7.0-rc.1` is out and waits for the PO's checklist and design review.** Tickets #256–#262 are built on PR #266 (draft); #263's reviews, fix round, full local gate (GATE PASS) and CI are green on `e37d4631`. `v0.6.1` is released (versionCode 60199). **Next:** the PO's answer on 09 §4 „v0.7.0", the design review and its questions A–E; then (if needed) an rc.2 fix session, else #264: merge and `v0.7.0` with the accuracy run.

## Next: the PO's test of `v0.7.0-rc.1`, then #264 (v0.7.0, M4 part 2)

- **Spec:** #255, D-115 (Q1–Q20 and its „Build notes"), 02 §12 INS-01–INS-08, 03 SCR-18, 05 §6 `statsRange`/`statsTab`, 09 §4 „v0.7.0". **Plan:** `docs/plans/v0.7.0.md`. **PR:** #266 (draft, closes #255–#262; #263 once the PO answered).
- **rc:** `v0.7.0-rc.1` (versionCode 70001) from `e37d4631`. **Design review:** `docs/design/review/v0.7.0/README.md` with Claude's defaults 1–8 and the **questions A–E** for the PO (top rated below 3 ratings, „+n" under Gesamt, the first Bewertungen heading, Serie at 0, value-label fade). The rc shows the first option of each.
- **Reviews done (#263):** T3 milestone review (no blocker; should-fix items fixed in rounds A and B), the motion review by the `review-animations` rules (applied by reading the skill files; the skill itself is user-invocable only), all within-spec findings fixed; a font-2.0 clipping of the habit labels found in the renders and fixed (rows from 1,5 ×). Items needing the PO are the questions A–E.
- **After the PO's answer:** answers that change behaviour go through the process (D-115 „Approval" plus amendments, tickets, rc.2). If the PO approves rc.1 as is: record it in D-115 „Approval", mark the PR ready, merge (merge commit), wait for CI on `main`, run `accuracy.yml` (all variants, 09 §4a) on the merge commit, bump to `0.7.0` and dispatch `v0.7.0` from `main` (#264).
- **No timer code, no migration:** `git diff main -- feature/brew core/timer core/timer-engine core/database/schemas` is empty; no T4 timer review.
- **Known from v0.6.0, not blocking:** #231's „smooth with 10 000 sessions" is shown by JVM timing only. A tab that comes back to the Journal can show the earlier list for a frame until the next build.
- **Open from D-114 (Claude's note, accepted):** the activity re-creation after the *Design* hand-over can still meet an alert starting in the same ≈ 0.5 s.
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
| Current milestone | **Statistik (v0.7.0, M4 part 2)**: built on PR #266; `v0.7.0-rc.1` with the PO (checklist, design review, questions A–E). v0.6.1 is released (2026-11-13) |
| Development go from PO | ✅ **In effect** since 2026-10-22 09:05 Europe/Berlin (D-038) |
| Working branch | `claude/wonderful-davinci-fu8tqi` (from `main` `bc410bbc` after #265) |
| Repository | `katznmeik/UmamiT` (renamed from `UmamiTea` on 2026-10-21; old URLs redirect) |
| Open PR | #266 (v0.7.0 milestone PR, draft) |
| Latest APK | rc [`v0.7.0-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.0-rc.1) (pre-release, 70001, from `e37d4631`); [`v0.6.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.6.1) (full release, versionCode 60199, from `main` `22b84788`, certificate verified); rc [`v0.6.1-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.6.1-rc.1) (pre-release, 60101, from `62657833`); [`v0.6.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.6.0) (60099); [`v0.5.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.5.1) (50199); [`v0.5.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.5.0) (50099) |
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

1. **Test `v0.7.0-rc.1`:** the checklist 09 §4 „v0.7.0" and the design review `docs/design/review/v0.7.0/` with its questions A–E (answer in the session or on PR #266).
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
