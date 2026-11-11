# PROJECT_STATE — UmamiT

> Handover between sessions: **only the current state**. Update it at the end
> of every session or phase. Finished releases, past plans and the session
> log are in [`docs/history.md`](docs/history.md); read that only when you
> need it (D-101).

**Last updated:** 2026-11-11. **[`v0.6.0-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.6.0-rc.1) is out** (the journal, M4 part 1; D-112; versionCode 60001, from `74237151` on `claude/wonderful-davinci-fu8tqi`, draft PR #235). The milestone review (T3), the motion review, #236 and the fix round are merged; CI is green on the rc's commit. **Waiting on the PO:** the on-device checklist (09 §4 „v0.6.0"), the design review (`docs/design/review/v0.6.0/`) and four open questions (below). **Next phase (fresh session, after the PO's answer): #234**, the merge and the final release, or a feedback round. [`v0.5.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.5.1) is the latest full release (from `main` `73a97fc5`); details in `docs/history.md`.

## v0.6.0 (rc.1 out; waiting on the PO)

- **Review and rc phase (#233): done** (2026-11-11, one session).
  - **[`v0.6.0-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.6.0-rc.1)**
    is published (pre-release, versionCode 60001, from `74237151` on the PR
    branch; release run 43106966020; APK SHA-256 `7f9530bb…30a7`;
    certificate `a500e2c6…2f67` = `signing/release-cert-sha256.txt`). CI
    was green on that commit (run 43103655084: checks, build, emulator
    tests on API 35 and 36). No accuracy run for the rc: no timer code
    changed.
  - **CI on the way there.** Run 43094657346 (head `d902df8c`) failed in
    „Emulator tests (API 35)": the brew-and-quick-timer test of
    `ProcessDeathTest`
    (`aBrewStepAndAQuickTimerDueTogetherSurviveTwoKillsAndAlertTheBrewFirst`)
    saw the held quick-timer alert 2327 ms after *Abgegossen* (tolerance
    2 s). This is not the flake of D-112 Q19 (other method, API 35, a
    timing bound). Timer code is unchanged against `main`, GitHub's runners
    were degraded at that time (artifact uploads failed, and the next run,
    43096106697, hung in the queue for an hour), and the test passed on
    both API levels in run 43103655084. Nothing was changed; the PO knows.
    If it shows again: rerun once; if it repeats, stop and ask the PO
    (timer).
  - **Milestone review (T3):** no blocker. **Motion review (T2):**
    `docs/plans/v0.6.0-motion-review.md`. What was fixed and what was left
    is D-112's „Review and fix round".
  - **Fix round, built at T2:** #236 (accepting the recipe offer keeps the
    recipe's step notes by position, PRF-07 AC5); round A (the session page
    reads one session, the session delete is uncancellable, unknown
    descriptor keys are refused, a dead read removed); round B (the stars
    keep the tapped value, the journal list is built off the main thread,
    the radar eases in, the dividers' fade, the average's rounding).
  - **One failure after the fix round:** the first full gate failed once in
    `SessionPageFlowTest`: the app's tests clicked a journal row before the
    off-thread list existed. A sweep found the same race in
    `TeaDetailSessionsRouteTest` and `ShellNavigationTest`; fixed at T3,
    test code only (`app/src/test/.../JournalWaits.kt`), and reviewed. The
    full gate then passed on `efc061b6`.
  - **No timer code changed:** the diff against `main` is still empty for
    `core/timer`, `core/timer-engine`, `feature/brew` and the app's timer
    routes; `core/database/schemas` gained only `6.json`.
  - **Renders:** `docs/design/review/v0.6.0/` (147, light, dark and font
    2.0, placeholder images only) with a README that lists the points to
    judge.
- **Next phase, #234 (fresh session, after the PO's answer):**
  1. If the PO approves the checklist (09 §4 „v0.6.0") and the design
     review: set `umamit.version=0.6.0` (versionCode 60099), run the
     accuracy check with all three variants (09 §4a), mark PR #235 ready,
     merge it with a merge commit once CI is green, dispatch `release.yml`
     from `main`, verify the certificate, close #234, move this section to
     `docs/history.md`, restart the working branch from `main`.
  2. If the PO reports problems or answers the open questions with a
     change: that is input to the process (grilling as options → spec and
     decision D-113 → tickets → build by tier → `v0.6.0-rc.2`), never
     straight into code.
- **Open questions for the PO (not decided, not built; D-112's review
  notes):**
  1. TalkBack on an unset taste slider: an action „Auf 0 setzen" / the
     first swipe sets 0 / leave it. And: should one axis be unsettable on
     its own (yes / no)?
  2. How an unset slider looks: as now (thumb at 0, „–") / no thumb and a
     dimmed track / a hollow thumb.
  3. Where the radar sits: above the sliders with the eased shift (as
     built) / below the sliders / its space reserved from the start.
  4. Optional: the wording of 03's reduce-motion line for the GESCHMACK
     fold (its 100 ms fade stays; no behaviour change).
- **Items for the PO's design review** are the „Points to judge" in
  `docs/design/review/v0.6.0/README.md` (the sheet, the filter sheet,
  GESCHMACK, the editor, the tea detail, *Meine Tees*, the session page's
  two ⋮, the recipe offer's details).
- **Known, not blocking:** #231's „smooth with 10 000 sessions" is shown by
  JVM timing only. A tab that comes back to the Journal can show the
  earlier list for a frame (a session just deleted, yesterday's day
  headings right after midnight) until the next build.
- **Environment:** Maven Central answered HTTP 429 all day; a local Gradle
  init script (`~/.gradle/init.d`, not in the repository) pointed
  `repo.maven.apache.org` at Google's Maven Central mirror
  (`maven-central.storage-download.googleapis.com`), which the „UmamiT"
  environment reaches. A new container needs it again if 429 persists:
  set the URL of every `repo.maven.apache.org` repository in
  `settingsEvaluated` (plugin and dependency repositories) and
  `allprojects`. One Gradle build at a time (`flock` as in
  `docs/plans/v0.4.0-resume.md` §3).
- **Specify phase: done** (2026-11-11). The PO was grilled in five rounds
  (Q1–Q18, all as options); the answers are D-112. The PO's rule for the
  release: every tasting input is optional and skippable with as little
  friction as possible.
- **Scope:** rating, notes, taste sliders with radar, descriptors and session
  photos in place on the session page; five stars and both actions on the
  „Gespeichert" sheet; *Zeiten ins Rezept übernehmen* after deliberate
  changes only (SET-18 retired); the session editor; journal search with
  three filters and the A6 empty state; ratings, GESCHMACK and LETZTE
  SITZUNGEN on the tea screens; umamit.db v6. No timer code changes.
- **Specs written** (commit `b4d2aae7` and the plan commit): 02, 03, 05, 06,
  08, 09, 10 (D-112), 12, the specs README.
- **Tickets:** the PO approved the breakdown of thirteen (2026-11-11). One
  refinement after the approval: #232 is also blocked by #231 (it uses the
  Journal's tea-filter argument).
- **After v0.6.0:** v0.6.1 (Statistik) gets its own specify session.

## Phase

| Item | Status |
|---|---|
| Current milestone | **v0.6.0** (the journal, M4 part 1; D-112, spec #221, plan `docs/plans/v0.6.0.md`): built (#222–#232, #236), reviewed and fixed (#233), **`v0.6.0-rc.1` out**; the PO's test, then #234. Then v0.6.1 (Statistik, M4 part 2), not yet specified |
| Development go from PO | ✅ **In effect** since 2026-10-22 09:05 Europe/Berlin (D-038) |
| Working branch | `claude/wonderful-davinci-fu8tqi` (from `main` `73a97fc5`; v0.6.0's build and fix round, last full local gate on `efc061b6`) |
| Repository | `katznmeik/UmamiT` (renamed from `UmamiTea` on 2026-10-21; old URLs redirect) |
| Open PR | **#235** (draft): v0.6.0 milestone PR, all build tickets and the fix round merged, rc.1 published |
| Latest APK | rc [`v0.6.0-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.6.0-rc.1) (pre-release, versionCode 60001, from `74237151`, certificate verified); [`v0.5.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.5.1) (full release, versionCode 50199, from `main` `73a97fc5`, certificate verified); rc `v0.5.1-rc.2` (50102), rc `v0.5.1-rc.1` (50101); [`v0.5.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.5.0) (50099) |
| Decision numbers | Up to **D-112** used (its PO answers run to Q22); the next free is **D-113** |

## Working agreements in effect

- **Tiers (D-101):** T1 Sonnet `low`, T2 Sonnet `medium`, T3 Opus `medium`, T4 Opus `high`. `xhigh` only with the PO's OK; never `max`. Timer, backup and migration code is never below T3.
- **Sessions (D-101):** one phase per session. Each phase ends with a handover in this file (and a resume note if the phase stops part-way) that names the model the next phase needs.
- **Next-session prompt (PO, 2026-11-10):** whenever a new session is to be started, the current session's last message ends with the ready-to-paste prompt for it (CLAUDE.md „Start of every session", 12 §5.1).
- **Briefs and reviews (D-101):** lean implementer briefs (12 §5.2); one milestone review at T3, plus T4 for a changed timer path; `/review-animations` once per milestone.
- **Parallelism:** at most **2** implementer subagents at a time, and **one Gradle build at a time** (the machine-wide lock in `v0.4.0-resume.md` §3).
- **Reporting:** push notifications only when the PO must act (rc ready, a blocking question). Questions go to the PO as selectable options.
- **Merging:** Claude merges all PRs (D-037); the PO only approves milestone PRs.
- **CI (D-105):** a change of `docs/**`, `*.md` or `.claude/**` only skips the checks, build and emulator jobs and still counts as a successful run for the release check; anything else, or any doubt, runs the full gate.

## Waiting on the PO

1. **v0.6.0:** test `v0.6.0-rc.1` with the on-device checklist (09 §4 „v0.6.0", also in PR #235), judge the renders (`docs/design/review/v0.6.0/README.md`, „Points to judge") and answer the four open questions above. The approval (in a session or as a PR comment) starts #234; anything else is input to a feedback round.
2. **Optional, at any time:** the PO's own alarm or Vorwarnung recordings (`docs/design/assets/source/sound-alert-<name>.wav`, `sound-prealert-<name>.wav`; WAV, sound from the first sample). Each becomes one more entry in `PreAlertSound` / `AlertSound`.
3. **Optional:** install the Claude GitHub App on the repository (https://github.com/apps/claude/installations/select_target). Without it, PR CI results don't wake a session; the sessions check CI themselves.
4. **Environment:** cloud sessions must run in the personal environment **„UmamiT"** (created 2026-11-08: `dl.google.com`, Gradle and Maven hosts, `raw.githubusercontent.com`, `www.tee-kontor-kiel.de`). The plain „Default" environment blocks `dl.google.com`, so the Android plugin doesn't resolve.

## Open for later

- **v0.6.1** (Statistik, M4 part 2, D-112): its own grilling and spec after v0.6.0; INS-01–INS-07 are the starting point, not yet confirmed by the PO; the A9 empty state comes with it.
- **Ideas the PO kept for later (D-112, 09 §5):** custom descriptors, notes per infusion, manual journal entries, brew coach tips.
- **Parked from v0.3.0:** the two *Pause* buttons and the shortened tea name in the brew row at font 2.0.
- **#58:** its remaining optional review findings stay open for later milestones.
- **Images:** all delivered. A6 (Journal) is built in with v0.6.0 (#231), A9 (Statistik) with v0.6.1.

## Known caveats

- Mockup images still show the old name „UmamiTea".
- Live Update / ProgressStyle and the full-screen alert were verified on the Galaxy with `v0.0.2-probe`; the full timer keeps those patterns (D-043, D-044, `docs/plans/M1-android16-api-notes.md`). Now Bar presence is best effort.
- Tee-Kontor uses Cloudflare; refreshes may get blocked (handled by stopping without changes).
- After a reboot, alerts fire only once the phone has been unlocked (E2); Samsung's automatic overnight restart should be disabled (ONB-02).
- `ProcessDeathTest` (two brews, process death; emulator, API 36) can fail on its single read of the diagnostics log; rerun CI (D-112 Q19). Its brew-and-quick-timer test failed once on API 35 on a timing bound (2327 ms against 2 s, run 43094657346, runners degraded).
- Robolectric fetches its runtime jar at test time; a network hiccup has failed CI before. Rerun once and comment on the PR.
