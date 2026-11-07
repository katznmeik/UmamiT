# PROJECT_STATE — UmamiT

> Handover between sessions: **only the current state**. Update it at the end
> of every session or phase. Finished releases, past plans and the session
> log are in [`docs/history.md`](docs/history.md); read that only when you
> need it (D-101).

**Last updated:** 2026-11-07. **The PO passed every item of the rc.1 checklist (#160) and reported five points** (2026-11-07). Four are built as **D-102** for `v0.4.0-rc.2` (versionCode 40002): the journal row shows the first infusion's own temperature (JRN-08); ⋮ *Löschen* on the session page (JRN-10, pulled forward from M4); *Tee hinzufügen* moved from the FAB into Sammlung's ⋮ menu (SCR-08); the keyboard no longer hides fields or leaves an empty band (`adjustResize`, the shell consumes the covered height, the editors pad once). The fifth, own photos for a tea added *Ohne Katalog*, is v0.4.1 already (D-100). rc.2 also carries the bamboo timer band (#164) and the tea-branch Start header (#165). No timer code changed, so no accuracy run for rc.2 (D-073). **Next: the PO tests rc.2** (the four rc.2 items in 09 §4 plus the band and the header), then approves #161.

> **Resuming v0.4.0? Read [`docs/plans/v0.4.0-resume.md`](docs/plans/v0.4.0-resume.md) next.** Its §0 has the start prompts (the rc session's is §0.1): this handover is only on the milestone branch, not on `main`. §3 has the environment setup, §5 steps 3–8 the rc phase.

## Phase

| Item | Status |
|---|---|
| Current milestone | **v0.4.0, `v0.4.0-rc.2` with the PO** (spec #145, PR #162 draft, D-100, D-102). All build tickets #147–#159, the review fixes F1–F15 (`docs/plans/v0.4.0-review.md`) and the rc.1 findings (D-102) are on the branch. |
| Next phase | **PO testing rc.2** (#160: the rc.2 items of 09 §4, the timer band and the Start header). Then #161 (merge, final `v0.4.0` from `main`, both accuracy variants again per 09 §4a). |
| Development go from PO | ✅ **In effect** since 2026-10-22 09:05 Europe/Berlin (D-038); `/implement-spec #145` given on 2026-10-31 |
| Working branch | `claude/umamit-m1-start-k0jg3n` = the v0.4.0 milestone branch (PR #162) |
| Repository | `katznmeik/UmamiT` (renamed from `UmamiTea` on 2026-10-21; old URLs redirect) |
| Open PR | #162 (v0.4.0 milestone, draft) |
| Latest APK | [`v0.4.0-rc.2`](https://github.com/katznmeik/UmamiT/releases/tag/v0.4.0-rc.2) (pre-release, versionCode 40002); before it `v0.4.0-rc.1` (40001, `5240a57`); stable [`v0.3.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.3.0) (versionCode 30099, from `main` 688a23a) |
| Decision numbers | Up to **D-102** used; the next free is **D-103** |

## Working agreements in effect

- **Tiers (D-101):** T1 Sonnet `low`, T2 Sonnet `medium`, T3 Opus `medium`, T4 Opus `high`. `xhigh` only with the PO's OK; never `max`. Timer, backup and migration code is never below T3.
- **Sessions (D-101):** one phase per session. Each phase ends with a handover in this file (and a resume note if the phase stops part-way) that names the model the next phase needs.
- **Briefs and reviews (D-101):** lean implementer briefs (12 §5.2); one milestone review at T3, plus T4 for a changed timer path; `/review-animations` once per milestone.
- **Parallelism:** at most **2** implementer subagents at a time, and **one Gradle build at a time** (the machine-wide lock in `v0.4.0-resume.md` §3).
- **Reporting:** push notifications only when the PO must act (rc ready, a blocking question). Questions go to the PO as selectable options.
- **Merging:** Claude merges all PRs (D-037); the PO only approves milestone PRs.

## Waiting on the PO

1. **Testing `v0.4.0-rc.2`** (#160): the four rc.2 items of 09 §4 (journal temperature, *Löschen* on the session page, the ⋮ menu instead of the FAB, the keyboard on every text field), plus the bamboo timer band and the tea-branch Start header. Then the explicit approval → #161.
2. **Optional, at any time:** the PO's own alarm or Vorwarnung recordings (`docs/design/assets/source/sound-alert-<name>.wav`, `sound-prealert-<name>.wav`; WAV, sound from the first sample). Each becomes one more entry in `PreAlertSound` / `AlertSound`.
3. **Optional:** install the Claude GitHub App on the repository (https://github.com/apps/claude/installations/select_target). Without it, PR #162's CI results don't wake a session; the sessions check CI themselves.

## v0.4.0: rc.2 out (2026-11-07)

- **rc.1 results (PO, 2026-11-07):** every checklist item passed; five points, recorded as **D-102**: (1) the journal row showed the base temperature, not the first infusion's (bug); (2) journal entries must be deletable; (3) the FAB *Tee hinzufügen* covers the rows → into Sammlung's ⋮; (4) the keyboard: an empty band above it on *Meine Tees*, and the editor's fields vanished behind it (every field); (5) own photos for *Ohne Katalog* teas → v0.4.1 (already planned, D-100).
- **Fixes (this session, on branch `claude/quirky-ptolemy-ld5oet` → PR into the milestone branch):** `JournalList` takes the first infusion's `tempC ?: baseTempC`; `JournalRepository.deleteSession` → `SessionDao.deleteFinished` (never `IN_PROGRESS`), `SessionPageViewModel.delete` + `SessionPageEvent.Deleted`, ⋮ and „Sitzung löschen?" on `SessionPageScreen`, the app pops the page; `CollectionScreen` gets `onAddFromCatalog` / `onAddWithoutCatalog` menu items on *Meine Tees*, `MyTeasContent` lost the FAB and `snackbarShowing`; `MainActivity` `adjustResize`, `Modifier.consumeCoveredBelow()` in `UmamitApp`, no `imePadding()` in the tea, recipe and vessel editors and *Sorte wählen*. Specs 02, 03, 09, 10 and the plan updated. Collection goldens re-recorded (no FAB) and the review renders refreshed.
- **Not covered by tests:** the keyboard itself (Robolectric reports no IME); the PO's rc.2 check is the test.

## v0.4.0: rc.1 out (2026-11-05)

- **rc phase (2026-11-05):** `5240a57` version bump; accuracy on `5240a57`: standard PASS (pre-alert p95 40 ms / max 53 ms, alert 15 / 19 ms), alongside PASS (brew 29 / 33 and 37 / 58 ms, quick timer 10 / 275 and 12 / 56 ms); CI green on `5240a57` and `cc20e1b` (renders, docs only). The first two release dispatches were refused („No successful CI run"): `release.yml` accepted only `push` CI runs, which a PR branch no longer has since step 0 (D-101). Fixed in `fe1d925` (also this repository's `pull_request` runs, never a fork's; `docs/release.md`). rc.1 published from `5240a57`, certificate checked; checklist on #160 and linked from PR #162.

- **Fix round (2026-11-05, Opus `medium` orchestrator):** step 0 (`ci.yml` runs `push` only on `main`; a push to the PR runs 4 checks, not 8). Then:
  - A, F1 (T3): one `AlertGate` in `core:ui` for every menu, dialog, sheet and picker; the four copies deleted.
  - C, the brew path (T3): F3, F4, F10, F11 and F12 as decided, plus the `ProcessDeathTest` gap log. Session completion untouched.
  - E, the motion review (T2, read-only), whose findings went to B.
  - B, the screens (T2): F2, F5–F9, F13, F15, the nits, and E's drag-reorder and list motion.
  - D, F14 (T2): `GermanNumber` in `core:domain`.

  The full local gate passed on A+C and on B+D. The orchestrator did not take E's reserved *Im Rezept speichern* slot: it left an empty band on every unchanged setup, against SCR-04 (`380d992`). All of this is recorded in D-100.
- **Goldens:** B re-recorded 32 in `feature:catalog` (F9's back arrow). The brew setup goldens are unchanged after the slot revert.

**Next** (`v0.4.0-resume.md` §5 steps 7–8):
1. Wait for the PO's results on #160. rc findings are fixed on the branch (timer code never below T3) → `0.4.0-rc.2`, with both accuracy variants again if timer code changed (09 §4a).
2. After the PO's explicit approval of the checklist and the design review: #161 (merge PR #162 with a merge commit, both accuracy variants on `main`'s merge commit, `v0.4.0` from `main`, restart the branch from `main`).
3. The workflow-dispatch runs (accuracy, release) don't wake a session; check them yourself. A release needs a green CI run on the exact commit it builds, so don't push while that CI runs (a newer push cancels it).

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
