# PROJECT_STATE — UmamiT

> Handover between sessions: **only the current state**. Update it at the end
> of every session or phase. Finished releases, past plans and the session
> log are in [`docs/history.md`](docs/history.md); read that only when you
> need it (D-101).

**Last updated:** 2026-11-09. `v0.4.1` is released; M3 is done. The PO's feedback after v0.4.1 (four points) is grilled into **[D-108](docs/specs/10-open-questions-and-decisions.md#d-108)**: a release **v0.5.0** comes before M4, which becomes **v0.6.0**. The spec is **[#194](https://github.com/katznmeik/UmamiT/issues/194)** (`ready-for-agent`, six test seams confirmed by the PO). Its 13 tickets **#196–#208** are published and approved by the PO. **Next: the build**, when the PO starts `/implement-spec #194`, in a fresh session (Opus `medium`); nothing is built before the PO starts `/implement-spec` on the new spec.

## Phase

| Item | Status |
|---|---|
| Current milestone | **v0.5.0** (feedback on v0.4.1, D-108, 09 §2): specified in [#194](https://github.com/katznmeik/UmamiT/issues/194), tickets #196–#208 (approved); next is the build on the PO's `/implement-spec #194` (each ticket carries its spec edits into the milestone PR). Then **M4 → v0.6.0** (Journal & statistics). M3 is done: v0.4.0 and [`v0.4.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.4.1) (from `main` `2b5735fc`) are released |
| Development go from PO | ✅ **In effect** since 2026-10-22 09:05 Europe/Berlin (D-038) |
| Working branch | `claude/focused-gauss-vg06j0` (from `main` `b14c9604`) |
| Repository | `katznmeik/UmamiT` (renamed from `UmamiTea` on 2026-10-21; old URLs redirect) |
| Open PR | none |
| Latest APK | [`v0.4.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.4.1) (full release, versionCode 40199, from `main` `2b5735fc`, certificate verified); rc `v0.4.1-rc.1` (40101) |
| Decision numbers | Up to **D-108** used; the next free is **D-109** |

## Working agreements in effect

- **Tiers (D-101):** T1 Sonnet `low`, T2 Sonnet `medium`, T3 Opus `medium`, T4 Opus `high`. `xhigh` only with the PO's OK; never `max`. Timer, backup and migration code is never below T3.
- **Sessions (D-101):** one phase per session. Each phase ends with a handover in this file (and a resume note if the phase stops part-way) that names the model the next phase needs.
- **Briefs and reviews (D-101):** lean implementer briefs (12 §5.2); one milestone review at T3, plus T4 for a changed timer path; `/review-animations` once per milestone.
- **Parallelism:** at most **2** implementer subagents at a time, and **one Gradle build at a time** (the machine-wide lock in `v0.4.0-resume.md` §3).
- **Reporting:** push notifications only when the PO must act (rc ready, a blocking question). Questions go to the PO as selectable options.
- **Merging:** Claude merges all PRs (D-037); the PO only approves milestone PRs.
- **CI (D-105):** a change of `docs/**`, `*.md` or `.claude/**` only skips the checks, build and emulator jobs and still counts as a successful run for the release check; anything else, or any doubt, runs the full gate.

## Waiting on the PO

1. **v0.5.0 (D-108):** nothing blocking. Spec #194 and tickets #196–#208 are published; the build waits for the PO to start `/implement-spec #194`. Still open from v0.4.0: check the catalog's *Grüntee* chip (09 §4, v0.4.0's final item); v0.5.0 extends that chip row.
2. **Optional, at any time:** the PO's own alarm or Vorwarnung recordings (`docs/design/assets/source/sound-alert-<name>.wav`, `sound-prealert-<name>.wav`; WAV, sound from the first sample). Each becomes one more entry in `PreAlertSound` / `AlertSound`.
3. **Optional:** install the Claude GitHub App on the repository (https://github.com/apps/claude/installations/select_target). Without it, PR CI results don't wake a session; the sessions check CI themselves.
4. **Environment:** cloud sessions must run in the personal environment **„UmamiT"** (created 2026-11-08: `dl.google.com`, Gradle and Maven hosts, `raw.githubusercontent.com`, `www.tee-kontor-kiel.de`). The plain „Default" environment blocks `dl.google.com`, so the Android plugin doesn't resolve.

## Open for later

- **v0.5.0 (D-108, spec #194), handover for the build session:**
  - Tickets (all sub-issues of #194, `ready-for-agent`; „←" = blocked by):
    - timer: #196 arbitration over any slots, dormant `BREW_2` (T4) · #200 two brews in the controller (T4, ←#196) · #201 bar, slim row, „Welchen Timer beenden?" (T3, ←#200) · #202 two-brew accuracy variant (T3, ←#200);
    - stock: #197 per-tea warning, umamit.db v5 (T4) · #198 reminder interval (T3);
    - catalog: #199 catalog kind prefactor (T2) · #203 black teas with the one-time re-read (T4, ←#199) · #205 white, oolong, Pu-Erh (T3, ←#203) · #206 Rotbusch, Früchtetee, Chai (T3, ←#203) · #204 flavour filter (T3, ←#199);
    - release: #207 rc, checklist, design review (←all) · #208 merge and `v0.5.0` (←#207).
  - Frontier at the go: #196, #197, #198, #199. Timer first (#196, T4), at most 2 implementers, one Gradle build at a time. #205 and #206 are T3 by the PO's choice in the ticket review (the spec had said T4).
  - Spec edits: 01 (done: NG6, NG7), 02 GEN-03, TMR-21, TMR-22, CAT-01, CAT-06, CAT-10, INV-05, INV-09–INV-11, SET-28/29; 03 SCR-02, SCR-05, SCR-07, SCR-09, SCR-19, SCR-25; 04 §3 (two brew slots, held alert for a brew), §8.3, §9.3, §12 (new tests); 05 (`TeaCategory` `ROOIBOS`, `FRUIT`; per-tea warning columns, a migration; skip-list reset); 07 NFR-PERF-11; 08 §2.2, §2.4, §2.6, §3 (new style rows), §2 taste notes; 09 checklist; 11 glossary.
  - Risky parts, T4: two brew slots in the controller and the held brew alert (rule 8, 04 §12, both accuracy variants per rc); the Room migration; catalog classification for the new sections and the re-read of the skip list.
  - Before writing the catalog part, re-read a few live product pages of the new sections (breadcrumb, brewing grid, taste scales, *Geschmack*) at ≤ 2 req/s, as V-01 did; the sitemap was read on 2026-11-09 (sections in D-108).
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
