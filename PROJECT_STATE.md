# PROJECT_STATE — UmamiT

> Handover between sessions: **only the current state**. Update it at the end
> of every session or phase. Finished releases, past plans and the session
> log are in [`docs/history.md`](docs/history.md); read that only when you
> need it (D-101).

**Last updated:** 2026-11-15 (rc.2 build session). **`v0.7.1-rc.2` is built, signed and verified but not published**: `release.yml` run 43722087355 (from `e57861e0`, versionCode 70102, APK SHA-256 `96038680…3235`, certificate `a500e2c6…2f67` = `signing/release-cert-sha256.txt`) failed at „Create tag and GitHub Release" with HTTP 403 „Resource not accessible by integration" (three attempts), and so did a fresh dispatch after the PO changed the workflow permissions (run 43726608377, 15:11 UTC), and again on a further fresh run (43734941736, 16:41 UTC). The PO's settings are as they should be (Actions: read and write, all actions allowed; no rulesets, no protected tags, release immutability off), so the cause is not in the repository's settings. A run the PO started (43739447174, 17:24 UTC) failed the same way, so it is not how Claude starts the runs either; the PO also re-confirmed Claude's access under GitHub › Settings › Applications (needed after enabling 2FA). Cause still unknown (GitHub-side or account-side); `release.yml` must publish again before the final `v0.7.1`. A run started from `main` (43741891550) failed too. **Fix (D-118, the PO's choice):** a fine-grained token `RELEASE_TOKEN` (UmamiT only, Contents: read and write) used only by the step „Create tag and GitHub Release"; the PO creates it, then Claude dispatches the rc.2 release from the PR branch (its `release.yml` has the change; `main` gets it with the merge of #290). The signed, verified APK is the artifact `release-apk` of runs 43726608377 and 43734941736 (kept 7 days). The same workflow published rc.1 this morning; no workflow change since, and the publish job declares `contents: write` itself. The PO approved rc.1 except the lock-screen transitions; the lock-screen audit (A-17–A-20) and one grilling round gave D-117 build note „Lock screen" (*Accepted (PO)*). Built in rc.2: #291 arrival hold, #292 Journal `exitFade`, #293 leaving the timer while locked shows only the keyguard, #294 lock-screen transitions reported only. **Next:** the PO checks the repository's Actions settings (Settings › Actions › General › Workflow permissions: „Read and write"; or an organisation/token policy), then the next session re-runs the publish job (or dispatches `release.yml` again with `e57861e0` and `0.7.1-rc.2`), posts the rc.2 announcement on #290, and the PO checks rc.2 (09 §4 „v0.7.1", the rc.2 items). **After v0.7.1:** the code cleanup review (v0.7.2, below).

## Now: v0.7.1-rc.2 with the PO (D-116, D-117)

- **rc.1 (2026-11-15, the PO):** all approved except the lock-screen transitions (*Abgegossen* → the lock screen; the timer opened over the keyguard, then closed: janky). The PO's `/review-animations` tickets #291 and #292 approved for rc.2.
- **Lock screen:** read-only audit (T3, `/improve-animations`), `docs/plans/v0.7.1-motion-audit.md` §8 A-17–A-20: the app never checked the keyguard; every exit from the timer faded onto a freshly composed shell, then cleared `showWhenLocked` a frame later, so One UI's keyguard animation ran over the app's fade. The PO's answers (D-117 build note „Lock screen"): A-17/A-18 *keyguard only*; A-20 *keep* (snap, the timer stays over the keyguard); A-19 *keep* (the PO notes whether One UI asks for the PIN on a Live Update tap); lock-screen transitions *report-only*. Amends SCR-05 and NFR-PERF-12.
- **Built (rc.2):** #291 (T3) `fadeInOnArrival` holds `ARRIVAL_HOLD_MS` = 150 ms (shared with the Journal); #292 (T2) the Journal's ghost fades with `exitFade`; #293 (T3) `KeyguardLock` + `LeavingOverKeyguard.kt`: a close while locked clears the flags in the same turn, keeps the timer drawn and inert, pops at `ON_STOP` with no transition, a back swipe has no preview; an alert or a notification request cancels it at once (rule 8); #294 (T2) „timer → lock screen" in `TransitionJankTest` and the *Abgegossen* frames in `TimerAlertOverLockScreenTest`, reported only. `feature/brew`, `core/timer`, `core/timer-engine` unchanged since rc.1.
- **Reviews:** T3 review of the rc.2 diff (no blockers; test gaps fixed). T4 timer-path review: B1 (an `ON_STOP` between an alert's start and its cancel frame popped the alerting timer) and N1–N5 fixed; T4 re-review: one more race (a stale `false` report after a cancel in the same turn) fixed, with a test. The 200 ms of #292's ghost can't be observed in Robolectric's readback; `UmamiMotionTest` asserts `exitFade`'s 200 ms and the call site uses it.
- **Gates:** full local gate on `a598e740` (GATE PASS) and app-scoped gate on the rc commit `3c1bf97b`; timer tests (04 §12) pass. CI full green on `e57861e0` (run 43716045507; = `3c1bf97b` + docs). Accuracy standard, alongside, two-brews **PASS** on `3c1bf97b` (runs 43715966194, 43715969182, 43715971831).
- **Jank (API 36, run 43716045507):** gated set passes (resume 0.3×, pops ≤ 1.3×, Gespeichert 1.1–1.2×, timer → shell 1.8×). Reported only: tab Sammlung → Statistik 3.6×, Statistik → Start 3.1×, Teeauswahl in 3.4×, Schritte-Sheet in 3.6× (over 3.0, as in rc.1); the others ≤ 2.7×. The new lock-screen measurement was skipped in that run (it read the session before the controller had it) and the *Abgegossen* line was not collected; fixed after the rc (`d52156d4`, test code and the CI report step only, not in the APK); its CI run 43722312502 on `a53c27e2` shows them: timer → lock screen 2 frames, 0.5× (System UI's own 17–19 frames are all over 16,7 ms on the emulator), *Abgegossen* over the lock screen 5 frames, first frame 362 ms (one run, no reference); all reported only.
- **Open notes (not blocking):** a rotation while a locked close waits loses the wait (fails safe: the timer is reported again); a self-close while stopped resolves at the next unlock with `exitFade`; the inert cover blocks touches but not TalkBack actions during the short wait. Earlier: T4 N1/N3/N4 of rc.1, the clipped „Zeiten ins Rezept übernehmen" at font 2.0.
- **Next session:** after the PO's rc.2 check. Either an rc.3 the same way (grilling for anything that changes a 03 §3.6 value or behaviour or a lock-screen rule; tickets under #279; timer path T3+, tests, T4 review, all three accuracy variants), or, if all approved: #289 — `umamit.version=0.7.1`, CI green, merge #290 (merge commit), CI on `main`, all three accuracy variants on the merge commit, `release.yml` with `0.7.1` from `main`, verify the certificate, restart the branch from `main`; then the v0.7.2 cleanup review's grilling round (below).

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
| Current milestone | **v0.7.1** (D-116, D-117): `v0.7.1-rc.2` with the PO. **v0.7.0** (Statistik, M4 part 2) is released (2026-11-14) |
| Development go from PO | ✅ **In effect** since 2026-10-22 09:05 Europe/Berlin (D-038) |
| Working branch | `claude/wonderful-davinci-fu8tqi` (restarted from `main` `d8b85319`) |
| Repository | `katznmeik/UmamiT` (renamed from `UmamiTea` on 2026-10-21; old URLs redirect) |
| Open PR | #290, the v0.7.1 milestone PR (draft until the PO approves the rc) |
| Latest APK | `v0.7.1-rc.2` built but not published (403, see above); rc [`v0.7.1-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.1-rc.1) (pre-release, 70101, from `3fe102c0`); [`v0.7.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.0) (full release, versionCode 70099, from `main` `f3582466`, certificate verified); rc [`v0.7.0-rc.3`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.0-rc.3) (pre-release, 70003, from `73907f6d`); rc [`v0.7.0-rc.2`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.0-rc.2) (pre-release, 70002, from `ccc57917`); rc [`v0.7.0-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.7.0-rc.1) (pre-release, 70001, from `e37d4631`); [`v0.6.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.6.1) (full release, versionCode 60199, from `main` `22b84788`, certificate verified); rc [`v0.6.1-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.6.1-rc.1) (pre-release, 60101, from `62657833`); [`v0.6.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.6.0) (60099); [`v0.5.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.5.1) (50199); [`v0.5.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.5.0) (50099) |
| Decision numbers | Up to **D-118** used; the next free is **D-119** |

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

1. **Release blocked:** check Settings › Actions › General › Workflow permissions („Read and write permissions") or any new token policy; `release.yml`'s publish job got HTTP 403 creating the release. Then start the next session.
2. **Test `v0.7.1-rc.2`** (once published): install over rc.1; the rc.2 items of checklist 09 §4 „v0.7.1" (arrival fades, Journal, the lock-screen items; in PR #290). Then start the next session (prompt at the end of the rc.2 session).
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
