# PROJECT_STATE — UmamiT

> Handover between sessions: **only the current state**. Update it at the end
> of every session or phase. Finished releases, past plans and the session
> log are in [`docs/history.md`](docs/history.md); read that only when you
> need it (D-101).

**Last updated:** 2026-11-08. **`v0.4.1` is released** from `main` `2b5735fc` (PR #191 merged, both accuracy variants PASS on `main`); M3 is done. **Next: M4 → v0.5.0** (journal and statistics, 09 §M4): its spec, plan and tickets come first, in a fresh session; nothing is built before the PO starts `/implement-spec` on the new spec.

## Phase

| Item | Status |
|---|---|
| Current milestone | **M4 → v0.5.0** (Journal & statistics, 09 §M4): not specified yet; next is the spec, the plan and the tickets. M3 is done: v0.4.0 and [`v0.4.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.4.1) (from `main` `2b5735fc`) are released |
| Development go from PO | ✅ **In effect** since 2026-10-22 09:05 Europe/Berlin (D-038) |
| Working branch | `claude/affectionate-heisenberg-8o8yfs` (restarted from `main` `2b5735fc` after the v0.4.1 merge) |
| Repository | `katznmeik/UmamiT` (renamed from `UmamiTea` on 2026-10-21; old URLs redirect) |
| Open PR | none |
| Latest APK | [`v0.4.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.4.1) (full release, versionCode 40199, from `main` `2b5735fc`, certificate verified); rc `v0.4.1-rc.1` (40101) |
| Decision numbers | Up to **D-107** used; the next free is **D-108** |

## Working agreements in effect

- **Tiers (D-101):** T1 Sonnet `low`, T2 Sonnet `medium`, T3 Opus `medium`, T4 Opus `high`. `xhigh` only with the PO's OK; never `max`. Timer, backup and migration code is never below T3.
- **Sessions (D-101):** one phase per session. Each phase ends with a handover in this file (and a resume note if the phase stops part-way) that names the model the next phase needs.
- **Briefs and reviews (D-101):** lean implementer briefs (12 §5.2); one milestone review at T3, plus T4 for a changed timer path; `/review-animations` once per milestone.
- **Parallelism:** at most **2** implementer subagents at a time, and **one Gradle build at a time** (the machine-wide lock in `v0.4.0-resume.md` §3).
- **Reporting:** push notifications only when the PO must act (rc ready, a blocking question). Questions go to the PO as selectable options.
- **Merging:** Claude merges all PRs (D-037); the PO only approves milestone PRs.
- **CI (D-105):** a change of `docs/**`, `*.md` or `.claude/**` only skips the checks, build and emulator jobs and still counts as a successful run for the release check; anything else, or any doubt, runs the full gate.

## Waiting on the PO

1. **M4 → v0.5.0:** nothing blocking; the next session drafts the M4 spec and asks the PO its open questions. Still open from v0.4.0: check the catalog's *Grüntee* chip (09 §4, v0.4.0's final item).
2. **Optional, at any time:** the PO's own alarm or Vorwarnung recordings (`docs/design/assets/source/sound-alert-<name>.wav`, `sound-prealert-<name>.wav`; WAV, sound from the first sample). Each becomes one more entry in `PreAlertSound` / `AlertSound`.
3. **Optional:** install the Claude GitHub App on the repository (https://github.com/apps/claude/installations/select_target). Without it, PR CI results don't wake a session; the sessions check CI themselves.
4. **Environment:** cloud sessions must run in the personal environment **„UmamiT"** (created 2026-11-08: `dl.google.com`, Gradle and Maven hosts, `raw.githubusercontent.com`, `www.tee-kontor-kiel.de`). The plain „Default" environment blocks `dl.google.com`, so the Android plugin doesn't resolve.

## Open for later

- **M4 → v0.5.0** (Journal & statistics, 09 §M4). Known starting points: A6 (Journal) and A9 (Statistik) empty states; v0.4.1's setup and session page show no tea photo yet (02 TEA-05).
- **Parked from v0.3.0:** the two *Pause* buttons and the shortened tea name in the brew row at font 2.0.
- **#58:** its remaining optional review findings stay open for later milestones.
- **Images:** all delivered. A6 (Journal) and A9 (Statistik) empty states are built in with M4.

## Known caveats

- Mockup images still show the old name „UmamiTea".
- Live Update / ProgressStyle and the full-screen alert were verified on the Galaxy with `v0.0.2-probe`; the full timer keeps those patterns (D-043, D-044, `docs/plans/M1-android16-api-notes.md`). Now Bar presence is best effort.
- Tee-Kontor uses Cloudflare; refreshes may get blocked (handled by stopping without changes).
- After a reboot, alerts fire only once the phone has been unlocked (E2); Samsung's automatic overnight restart should be disabled (ONB-02).
- Robolectric fetches its runtime jar at test time; a network hiccup has failed CI before. Rerun once and comment on the PR.
