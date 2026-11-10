# PROJECT_STATE — UmamiT

> Handover between sessions: **only the current state**. Update it at the end
> of every session or phase. Finished releases, past plans and the session
> log are in [`docs/history.md`](docs/history.md); read that only when you
> need it (D-101).

**Last updated:** 2026-11-10. **Feedback round v0.5.1 is specified (D-110) and waits for the PO's go on its tickets** (below). **[`v0.5.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.5.0) is released** (from `main` `db2ba2b3`; D-108, D-109; two active timers, the whole tea range, per-tea stock warning, flavour filter). Details in `docs/history.md`. **Next: M4 → v0.6.0** (Journal & statistics); nothing is built for it before it is specified and planned.

## Feedback round v0.5.1 (in progress): specified, waiting for the PO's go on the tickets

The PO gave four points of feedback on v0.5.0 on 2026-11-10 (Start's two
sections into one; *Knapp ab* shows its value; the filter sheet's *Fertig*;
a sans in place of the serif font).

- **Grilling: done** (Q1–Q13, 2026-11-10). **To-spec: done**: D-110 holds
  the PO's answers, with Claude's unasked details marked as such; 02 BRW-11,
  03 (§1, §3.2, SCR-02, SCR-09, SCR-19), 09 (§2, §4 v0.5.1) and 11 follow
  it. It ships as **v0.5.1 before M4**.
- **Tickets: done.** Spec issue #212, tickets #213–#218; plan
  `docs/plans/v0.5.1.md` (tiers, order, the draft question).
- **Next:** the PO decides about the draft and starts
  `/implement-spec #212` (delegation by tier; CLAUDE.md rule 1), ideally
  in a fresh session (12 §5.1; Opus `medium` or Sonnet is enough for these
  tickets). Nothing is built before that.
- **Branch `claude/modest-ptolemy-86vgji` holds an unapproved code draft**
  (in the WIP commit `75e4198b`): Claude implemented all four points
  without the process. Its behaviour matches the PO's later answers, but it
  was not delegated, not reviewed, and its goldens are not re-recorded. The
  PO decides whether the implementers start from it or from `main`'s code.
  The version is back at `0.5.0`.
- No PR, no release. Font comparison render: shown to the PO in the session
  (not in the repository).

## Phase

| Item | Status |
|---|---|
| Current milestone | **M4 → v0.6.0** (Journal & statistics, 09 §M4): not yet specified or planned. v0.5.0 is done: [`v0.5.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.5.0) from `main` `db2ba2b3` |
| Development go from PO | ✅ **In effect** since 2026-10-22 09:05 Europe/Berlin (D-038) |
| Working branch | `claude/modest-ptolemy-86vgji` (from `main`; holds the unapproved v0.5.1 draft, see above) |
| Repository | `katznmeik/UmamiT` (renamed from `UmamiTea` on 2026-10-21; old URLs redirect) |
| Open PR | none |
| Latest APK | [`v0.5.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.5.0) (full release, versionCode 50099, from `main` `db2ba2b3`, certificate verified); rc `v0.5.0-rc.1` (50001) |
| Decision numbers | Up to **D-110** used; the next free is **D-111** |

## Working agreements in effect

- **Tiers (D-101):** T1 Sonnet `low`, T2 Sonnet `medium`, T3 Opus `medium`, T4 Opus `high`. `xhigh` only with the PO's OK; never `max`. Timer, backup and migration code is never below T3.
- **Sessions (D-101):** one phase per session. Each phase ends with a handover in this file (and a resume note if the phase stops part-way) that names the model the next phase needs.
- **Briefs and reviews (D-101):** lean implementer briefs (12 §5.2); one milestone review at T3, plus T4 for a changed timer path; `/review-animations` once per milestone.
- **Parallelism:** at most **2** implementer subagents at a time, and **one Gradle build at a time** (the machine-wide lock in `v0.4.0-resume.md` §3).
- **Reporting:** push notifications only when the PO must act (rc ready, a blocking question). Questions go to the PO as selectable options.
- **Merging:** Claude merges all PRs (D-037); the PO only approves milestone PRs.
- **CI (D-105):** a change of `docs/**`, `*.md` or `.claude/**` only skips the checks, build and emulator jobs and still counts as a successful run for the release check; anything else, or any doubt, runs the full gate.

## Waiting on the PO

1. **M4 → v0.6.0:** nothing blocking; the next step is specifying M4 (e.g. `/to-spec`) when the PO wants to start.
2. **Optional, at any time:** the PO's own alarm or Vorwarnung recordings (`docs/design/assets/source/sound-alert-<name>.wav`, `sound-prealert-<name>.wav`; WAV, sound from the first sample). Each becomes one more entry in `PreAlertSound` / `AlertSound`.
3. **Optional:** install the Claude GitHub App on the repository (https://github.com/apps/claude/installations/select_target). Without it, PR CI results don't wake a session; the sessions check CI themselves.
4. **Environment:** cloud sessions must run in the personal environment **„UmamiT"** (created 2026-11-08: `dl.google.com`, Gradle and Maven hosts, `raw.githubusercontent.com`, `www.tee-kontor-kiel.de`). The plain „Default" environment blocks `dl.google.com`, so the Android plugin doesn't resolve.

## Open for later

- **M4 → v0.6.0** (Journal & statistics, 09 §M4; was v0.5.0 until D-108). Known starting points: A6 (Journal) and A9 (Statistik) empty states; v0.4.1's setup and session page show no tea photo yet (02 TEA-05).
- **Parked from v0.3.0:** the two *Pause* buttons and the shortened tea name in the brew row at font 2.0.
- **#58:** its remaining optional review findings stay open for later milestones.
- **Images:** all delivered. A6 (Journal) and A9 (Statistik) empty states are built in with M4.

## Known caveats

- Mockup images still show the old name „UmamiTea".
- Live Update / ProgressStyle and the full-screen alert were verified on the Galaxy with `v0.0.2-probe`; the full timer keeps those patterns (D-043, D-044, `docs/plans/M1-android16-api-notes.md`). Now Bar presence is best effort.
- Tee-Kontor uses Cloudflare; refreshes may get blocked (handled by stopping without changes).
- After a reboot, alerts fire only once the phone has been unlocked (E2); Samsung's automatic overnight restart should be disabled (ONB-02).
- Robolectric fetches its runtime jar at test time; a network hiccup has failed CI before. Rerun once and comment on the PR.
