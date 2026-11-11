# PROJECT_STATE — UmamiT

> Handover between sessions: **only the current state**. Update it at the end
> of every session or phase. Finished releases, past plans and the session
> log are in [`docs/history.md`](docs/history.md); read that only when you
> need it (D-101).

**Last updated:** 2026-11-11. **v0.6.0 (the journal, M4 part 1; D-112) is built**: all eleven build tickets (#222–#232) are merged into `claude/wonderful-davinci-fu8tqi` (draft PR #235) and the full local gate passed on `08796ec4`. **Next phase (fresh session): #233**, the milestone review, the motion review, the fix round and `v0.6.0-rc.1`. Four questions wait for the PO (below). [`v0.5.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.5.1) is the latest release (from `main` `73a97fc5`); details in `docs/history.md`.

## v0.6.0 (built; review and rc next)

- **Build phase: done** (2026-11-11, one session). Each ticket was built in
  its own worktree at its tier, reviewed read-only against its criteria,
  the specs and D-112's rule, fixed, then verified by the orchestrator
  (diff, goldens, full local gate on the merge) before the push. Build
  decisions are D-112's „Build notes", one block per ticket.
  - **Merged:** #222 (T1), #223 (T3), #224 (T2, finished at T3), #231 (T2),
    #225 (T2), #227 (T2), #226 (T2), #230 (T3), #232 (T2), #228 (T3),
    #229 (T3). The last gate passed on `08796ec4`.
  - **No timer code changed:** the diff against `main` is empty for
    `core/timer`, `core/timer-engine`, `feature/brew` and
    `app/.../TimerRoutes.kt`; `completeSession` and `SavedSheetRequests`
    are unchanged; `core/database/schemas` gained only `6.json`. So no T4
    timer review is due and the rc needs no accuracy run (09 §4a).
  - **Not fully shown:** #231's criterion „smooth with 10 000 sessions" is
    met by JVM timing only (its box is unticked); the list is built on the
    composition thread per repository emission.
  - **CI:** run 43005004378 failed once in „Emulator tests (API 36)":
    `ProcessDeathTest` (two brews, process death) reads the diagnostics log
    once while the timer process writes the recovery line asynchronously.
    The recovery itself worked; the same test flaked once before
    (`docs/history.md`). The runs after it were green. A fix is test-only
    but under `core/timer/src/androidTest`, so it waits for the PO
    (question 1 below).
- **Next phase, #233 (fresh session, Opus `medium`):**
  1. Check CI on the head of PR #235.
  2. One milestone review at T3 over `main...HEAD` (12 §5.3), with the
     items below; `/review-animations` once by a T2 subagent, notes in
     `docs/plans/v0.6.0-motion-review.md` (plan „Review, rc, release").
  3. The PO's answers to the four questions go through the process (D-112
     addendum or a new decision, spec, ticket) before any code.
  4. One fix round by tier, the full gate, then `umamit.version=0.6.0-rc.1`,
     the renders in `docs/design/review/v0.6.0/` (placeholder images only),
     `release.yml` from the PR branch, the checklist of 09 §4 „v0.6.0".
  5. Stop when the rc is published and the PO is notified.
- **Items for the milestone review (collected while building):**
  - Journal: the list is built on the composition thread (above);
    `observeTeaTasting` combines three queries.
  - Session page: TalkBack cannot move an unset taste slider to 0 and there
    is no per-axis unset; with reduce motion the GESCHMACK fold keeps its
    short fade; the hero's view model reads the whole journal to find one
    tea cover; `RoomSessionPhotos.add` repeats `RoomTeaPhotos`' flow.
  - Tea screens: `RoomCollectionListRepository` maps the rating aggregate a
    second time beside `JournalRepository.observeTeaRatings`.
  - German number helpers are local in two places instead of `GermanFormat`.
- **Items for the PO's design review (with the rc's renders):**
  - „Gespeichert" sheet: the empty stars are faint in dark; *Notizen
    hinzufügen* is a plain text button; the check mark aligns to the top.
  - Filter sheet: „Mit Notizen" is heading and chip.
  - GESCHMACK sliders: the label column's width at font 2.0.
  - Editor: *Zeit* is a text field (m:ss), not a duration picker; the leaf
    field has the setup's limits (0,1–100 g), so 0 cannot be entered; the
    out-of-range message „Zwischen 0:01 und 24:00:00." mixes two formats.
  - Tea detail: the descriptor chips sit close under the radar's bottom
    label; the edit button covers the third LETZTE SITZUNGEN row's rating;
    GESCHMACK is absent with one or two axes and no descriptor.
  - *Meine Tees*: a rated favourite reads „★ 4,5 ★"; archived rows show the
    rating too.
  - Session page: two ⋮ on the hero (the page's at the top, the photo's at
    the bottom).
  - Recipe offer (Claude's details in D-112's build notes for #228): an
    archived tea gets no offer; rounding follows the recorded time; a base
    temperature changed in the setup counts as a change; a rinse ended
    early counts.
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
| Current milestone | **v0.6.0** (the journal, M4 part 1; D-112, spec #221, plan `docs/plans/v0.6.0.md`): built (#222–#232), review and rc next (#233). Then v0.6.1 (Statistik, M4 part 2), not yet specified |
| Development go from PO | ✅ **In effect** since 2026-10-22 09:05 Europe/Berlin (D-038) |
| Working branch | `claude/wonderful-davinci-fu8tqi` (from `main` `73a97fc5`; v0.6.0's build, last gate on `08796ec4`) |
| Repository | `katznmeik/UmamiT` (renamed from `UmamiTea` on 2026-10-21; old URLs redirect) |
| Open PR | **#235** (draft): v0.6.0 milestone PR, all build tickets merged |
| Latest APK | [`v0.5.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.5.1) (full release, versionCode 50199, from `main` `73a97fc5`, certificate verified); rc `v0.5.1-rc.2` (50102), rc `v0.5.1-rc.1` (50101); [`v0.5.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.5.0) (50099) |
| Decision numbers | Up to **D-112** used; the next free is **D-113** |

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

1. **v0.6.0, four questions** (asked as options at the end of the build session; an answer goes through the process before any code):
   1. **`ProcessDeathTest`'s log read (see „CI" above):** (a) leave it as a known flake and rerun; (b) fix the test in v0.6.0, a test-only change under `core/timer`; (c) fix it after v0.6.0. Until answered nothing under `core/timer` changes.
   2. **Session page, ← and ⋮ (SCR-16, #229):** built over the hero on translucent surfaces as on the tea detail, so a loaded page has no title „Sitzung". (a) Keep it; (b) a solid top bar with ← · „Sitzung" · ⋮ above the hero; (c) as built, with the title appearing in a bar once the hero has scrolled away.
   3. **Recipe offer after *Sitzung beenden* in the middle of an infusion (PRF-07, #228):** the stored steps cannot tell it from *Aufguss jetzt beenden*, so today it offers the shorter time. (a) Keep it; (b) leave out the step that *Sitzung beenden* ended (recognised by its end time, not exact in every case).
   4. **Recipe offer and step notes (PRF-07, #228):** accepting replaces the recipe's steps as *Im Rezept speichern* does, which drops the recipe's step notes. (a) Keep it; (b) keep each step's note at its position when the kind matches; (c) keep the notes only when the number of steps is unchanged.

   Corrections to D-112's details and build notes marked „Claude" are welcome at any time.
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
- Robolectric fetches its runtime jar at test time; a network hiccup has failed CI before. Rerun once and comment on the PR.
