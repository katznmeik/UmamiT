# PROJECT_STATE — UmamiT

> Handover between sessions: **only the current state**. Update it at the end
> of every session or phase. Finished releases, past plans and the session
> log are in [`docs/history.md`](docs/history.md); read that only when you
> need it (D-101).

**Last updated:** 2026-11-02. v0.4.0 is built and reviewed; the fix round F1–F15 is next, then `v0.4.0-rc.1`. The PO paused before the fix round and shifted the subagent tiers down to save usage (D-101). A later session the same day checked the handover for completeness. The PO settled nits, motion review and CI, recorded in D-101 under „Amended".

> **Resuming v0.4.0? Read [`docs/plans/v0.4.0-resume.md`](docs/plans/v0.4.0-resume.md) next.** Its §0 has the start prompt: this handover is only on the milestone branch, not on `main`. It has the PO questions to ask first (F5, F11, F12), the environment setup, the fix-round cut with the new tiers, the steps to rc.1, the implementer brief and the build's contracts.

## Phase

| Item | Status |
|---|---|
| Current milestone | **v0.4.0, built; review fixes pending** (spec #145, PR #162 draft, D-100). All build tickets #147–#159 are merged on the branch. The review is in `docs/plans/v0.4.0-review.md`; the fix round F1–F15 comes next, then rc.1. |
| Next phase | **Fix round**, in a fresh session on **Opus, `medium`** (D-101), started with the prompt in `v0.4.0-resume.md` §0. It ends after §5 step 2. Then the rc phase (version, accuracy runs, renders, dispatch) runs in a fresh **Sonnet** session. |
| Development go from PO | ✅ **In effect** since 2026-10-22 09:05 Europe/Berlin (D-038); `/implement-spec #145` given on 2026-10-31 |
| Working branch | `claude/umamit-m1-start-k0jg3n` = the v0.4.0 milestone branch (PR #162) |
| Repository | `katznmeik/UmamiT` (renamed from `UmamiTea` on 2026-10-21; old URLs redirect) |
| Open PR | #162 (v0.4.0 milestone, draft) |
| Latest APK | [`v0.3.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.3.0) (versionCode 30099, from `main` 688a23a) |
| Decision numbers | Up to **D-101** used; the next free is **D-102** |

## Working agreements in effect

- **Tiers (D-101):** T1 Sonnet `low`, T2 Sonnet `medium`, T3 Opus `medium`, T4 Opus `high`. `xhigh` only with the PO's OK; never `max`. Timer, backup and migration code is never below T3.
- **Sessions (D-101):** one phase per session. Each phase ends with a handover in this file (and a resume note if the phase stops part-way) that names the model the next phase needs.
- **Briefs and reviews (D-101):** lean implementer briefs (12 §5.2); one milestone review at T3, plus T4 for a changed timer path; `/review-animations` once per milestone.
- **Parallelism:** at most **2** implementer subagents at a time, and **one Gradle build at a time** (the machine-wide lock in `v0.4.0-resume.md` §3).
- **Reporting:** push notifications only when the PO must act (rc ready, a blocking question). Questions go to the PO as selectable options.
- **Merging:** Claude merges all PRs (D-037); the PO only approves milestone PRs.

## Waiting on the PO

1. **v0.4.0 fix round:** three decisions to ask first, with the recommended option first (`v0.4.0-resume.md` §2): F11 (*Ohne Rezept* Vorwarnung base), F12 (deleting the running brew's recipe), F5 (pencils on the tea detail above the setup).
2. **Later:** testing `v0.4.0-rc.1` with the 09 §4 checklist and the design review (#160).
3. **Optional, at any time:** the PO's own alarm or Vorwarnung recordings (`docs/design/assets/source/sound-alert-<name>.wav`, `sound-prealert-<name>.wav`; WAV, sound from the first sample). Each becomes one more entry in `PreAlertSound` / `AlertSound`.

## v0.4.0: built, review done, fix round next

- **Built and pushed:** all 13 build tickets (#147–#159), each verified with the full local gate and green on CI. Plus `ab84993`: `ProcessDeathTest` allows 2 s between the two timers' starts (CI API 35 fix).
- **Accuracy:** both variants passed on `5b5af88`. Timer-adjacent code changed afterwards, and F3, F4 and F10 change more, so rc.1 needs a fresh run of both (D-073).
- **Code review** (Standards T3, Spec T3, timer path T4): no blockers in the timer path. The fix list **F1–F15** is in `docs/plans/v0.4.0-review.md`: F1–F10 must be fixed, F11–F15 are should-fix or a PO decision, plus nits.
- **Paused by the PO** on 2026-11-01, before the fix round: no fix implementer and no accuracy runs have started.

**Steps to `v0.4.0-rc.1`** (details in `v0.4.0-resume.md` §4–§5):
1. Ask the PO F5, F11 and F12.
2. Fix round: step 0 (`ci.yml` push only on `main`), then four implementers A–D, at most two at a time, plus the read-only motion reviewer E:
   - A: F1, T3 (Opus `medium`);
   - C: the brew path, T3 (Opus `medium`), or T4 (Opus `high`) if it touches session completion;
   - E: `/review-animations` applied by a read-only reviewer, T2 (Sonnet `medium`), alongside A and C;
   - B: the screen findings, the small nits and E's findings, T2 (Sonnet `medium`);
   - D: F14, T2 (Sonnet `medium`), alongside B.

   Verify each result with the full local gate before merging it.
3. D-100 build notes and the review outcome (doc 10), the PR body. **The fix-round session ends here** with a handover for the Sonnet rc session.
4. `umamit.version=0.4.0-rc.1` (versionCode 40001), then accuracy runs (both variants) on that commit.
5. Design renders to `docs/design/review/v0.4.0/`, then dispatch `release.yml`.
6. Post the checklist on #160 and notify the PO. After the PO's approval: #161 (merge, `v0.4.0` from `main`).

## Open for later

- **v0.4.1** (stock history, freshness, cost per cup, own photos, *Aus Katalog zurücksetzen*): its spec follows v0.4.0 (D-100). It also takes two nit refactors from the v0.4.0 review: shared formatters in `core:ui` and one editor scaffold (D-101, Amended). Then M4 → v0.5.0.
- **Parked from v0.3.0:** the two *Pause* buttons and the shortened tea name in the brew row at font 2.0.
- **#58:** its remaining optional review findings stay open for later milestones.
- **Images:** all delivered. A6 (Journal) and A9 (Statistik) empty states are built in with M4.

## Known caveats

- Mockup images still show the old name „UmamiTea".
- Live Update / ProgressStyle and the full-screen alert were verified on the Galaxy with `v0.0.2-probe`; the full timer keeps those patterns (D-043, D-044, `docs/plans/M1-android16-api-notes.md`). Now Bar presence is best effort.
- Tee-Kontor uses Cloudflare; refreshes may get blocked (handled by stopping without changes).
- After a reboot, alerts fire only once the phone has been unlocked (E2); Samsung's automatic overnight restart should be disabled (ONB-02).
- Robolectric fetches its runtime jar at test time; a network hiccup has failed CI before. Rerun once and comment on the PR.
