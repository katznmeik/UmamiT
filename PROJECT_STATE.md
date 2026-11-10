# PROJECT_STATE — UmamiT

> Handover between sessions: **only the current state**. Update it at the end
> of every session or phase. Finished releases, past plans and the session
> log are in [`docs/history.md`](docs/history.md); read that only when you
> need it (D-101).

**Last updated:** 2026-11-10. **Feedback round v0.5.1 is built, reviewed and fixed (D-110; tickets #213–#216 on draft PR #219); the rc `v0.5.1-rc.1` is next** (below). **[`v0.5.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.5.0) is released** (from `main` `db2ba2b3`; D-108, D-109; two active timers, the whole tea range, per-tea stock warning, flavour filter). Details in `docs/history.md`. **Next: M4 → v0.6.0** (Journal & statistics); nothing is built for it before it is specified and planned.

## Feedback round v0.5.1 (in progress): reviewed and fixed, the rc is next

The PO's four points of feedback on v0.5.0 (2026-11-10; D-110): Start's two
sections into one; *Knapp ab* shows its value; the filter sheet's *Fertig*;
a sans in place of the serif font. Spec issue #212, plan
`docs/plans/v0.5.1.md`, draft milestone PR **#219** from
`claude/wonderful-davinci-fu8tqi`.

- **Build phase: done** (2026-11-10, started by the PO with
  `/implement-spec #212`). #213–#216 are merged into the PR branch (head
  `ba89af29` before this handover). Each implementer started from the draft
  `75e4198b` and reviewed its part; the full local gate passed on the merged
  branch (spotlessCheck, detekt, checkManifestPermissions, testDebugUnitTest,
  lint, verifyRoborazziDebug, assembleDebugAndroidTest).
  - #213 (T2): the draft stood; a test for the row's tap and its TalkBack
    label was added.
  - #214 (T2): the global value is drawn by a `VisualTransformation`, the
    field's text stays empty (the draft made it the text, which could give
    „1525").
  - #215 (T1): with the motion review's S1 (*Fertig* slides the sheet out)
    and N1 (a hairline under the pinned title while scrolled).
  - #216 (T2): the draft's fonts, licence and roles stood; all 708 goldens
    are re-recorded.
- **Motion review: done** (`docs/plans/v0.5.1-motion-review.md`; S1 and N1
  are built).
- **Review and fix round: done** (2026-11-10; #217, first part).
  - CI on `df9959c3` was green on all jobs; nothing to fix.
  - Milestone review (T3): no must-fix; two should-fix (D-096's stale Start
    note; TalkBack could not tell the shown global *Knapp ab* value from an
    own one) and ten nits. No T4 review: no timer code changed.
  - Fix round (T2, own worktree; merge `c24e9e90`): all twelve findings and
    the four known items (rows keyed by tea; the font feature list in 03
    §3.2; `ShowsGlobalTest` for the quieter colour; D-110's build notes).
    New behaviour: TalkBack reads the state „Vorgabe" while *Knapp ab* shows
    the global value (03 SCR-09, string in `feature:collection`). The filter
    sheet dismisses only once hidden, with a test for the slide-out path.
    No golden changed.
  - The full local gate passed on `c24e9e90` (spotlessCheck, detekt,
    checkManifestPermissions, testDebugUnitTest, lint, verifyRoborazziDebug,
    assembleDebugAndroidTest). A fresh container needs the Robolectric SDK 35
    jar fetched first (`v0.4.0-resume.md` §3).
- **Next phase (fresh session, Sonnet `medium` is enough): the rc** (#217,
  second part):
  1. Check CI on PR #219 for the head of the PR branch.
  2. `umamit.version=0.5.1-rc.1` in `gradle.properties`.
  3. Design review renders (light and dark, placeholder images only) in
     `docs/design/review/v0.5.1/` with a README: Start, tea detail *Knapp
     ab*, the filter sheet, the font.
  4. CI green on the rc commit; dispatch `release.yml` (`ref` the PR branch,
     `version` `0.5.1-rc.1`); verify the pre-release and its certificate.
  5. Update the PR body and #217; notify the PO for the checklist (09 §4
     „v0.5.1") and the design review. Keep the PR in draft until the PO
     approves.
- **Then** #218 after the PO's approval (accuracy run first, 09 §4a; merge;
  final `v0.5.1` from `main`).
- The branch `claude/modest-ptolemy-86vgji` is contained in the PR branch
  and no longer needed.

## Phase

| Item | Status |
|---|---|
| Current milestone | **M4 → v0.6.0** (Journal & statistics, 09 §M4): not yet specified or planned. v0.5.0 is done: [`v0.5.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.5.0) from `main` `db2ba2b3` |
| Development go from PO | ✅ **In effect** since 2026-10-22 09:05 Europe/Berlin (D-038) |
| Working branch | `claude/wonderful-davinci-fu8tqi` (draft PR #219; contains `claude/modest-ptolemy-86vgji`) |
| Repository | `katznmeik/UmamiT` (renamed from `UmamiTea` on 2026-10-21; old URLs redirect) |
| Open PR | **#219** (draft): v0.5.1 milestone PR |
| Latest APK | [`v0.5.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.5.0) (full release, versionCode 50099, from `main` `db2ba2b3`, certificate verified); rc `v0.5.0-rc.1` (50001) |
| Decision numbers | Up to **D-110** used; the next free is **D-111** |

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
