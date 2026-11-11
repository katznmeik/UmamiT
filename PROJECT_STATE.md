# PROJECT_STATE — UmamiT

> Handover between sessions: **only the current state**. Update it at the end
> of every session or phase. Finished releases, past plans and the session
> log are in [`docs/history.md`](docs/history.md); read that only when you
> need it (D-101).

**Last updated:** 2026-11-11. **M4 is specified and split (D-112): v0.6.0 is the journal, v0.6.1 is Statistik.** v0.6.0 has its spec (#221), tickets (#222–#234), plan (`docs/plans/v0.6.0.md`) and the draft milestone PR #235; **nothing is built: the build starts when the PO starts `/implement-spec #221`.** [`v0.5.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.5.1) is the latest release (from `main` `73a97fc5`); details in `docs/history.md`.

## v0.6.0 (specified, waiting for the PO's start)

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
- **Next phase (fresh session, Opus `medium`): the build,** started by the
  PO with `/implement-spec #221`. Waves and verification:
  `docs/plans/v0.6.0.md`. It will take more than one session; a session
  that stops part-way writes `docs/plans/v0.6.0-resume.md`.
- **After v0.6.0:** v0.6.1 (Statistik) gets its own specify session.

## Phase

| Item | Status |
|---|---|
| Current milestone | **v0.6.0** (the journal, M4 part 1; D-112, spec #221, plan `docs/plans/v0.6.0.md`): specified, not built. Then v0.6.1 (Statistik, M4 part 2), not yet specified |
| Development go from PO | ✅ **In effect** since 2026-10-22 09:05 Europe/Berlin (D-038) |
| Working branch | `claude/wonderful-davinci-fu8tqi` (restarted from `main` `73a97fc5`; docs commits only so far) |
| Repository | `katznmeik/UmamiT` (renamed from `UmamiTea` on 2026-10-21; old URLs redirect) |
| Open PR | **#235** (draft): v0.6.0 milestone PR |
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

1. **v0.6.0:** the start of the build with `/implement-spec #221` in a fresh session (the prompt is at the end of the specify session's last message). Corrections to D-112's details marked „Claude" are welcome before or during the build.
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
