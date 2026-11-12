# PROJECT_STATE — UmamiT

> Handover between sessions: **only the current state**. Update it at the end
> of every session or phase. Finished releases, past plans and the session
> log are in [`docs/history.md`](docs/history.md); read that only when you
> need it (D-101).

**Last updated:** 2026-11-12 (evening). **The v0.6.1 build is in #250** (D-114, spec #240). Milestone PR **#252** (draft) from `claude/wonderful-davinci-fu8tqi`. **All nine build tickets #241–#249 landed** (CI green per head). **Reviews done:** T3 milestone (0 Blocker/Major), T4 timer (1 Major: keyboard insets at the alert), motion review by project rules (`docs/plans/v0.6.1-motion-review.md`). The PO answered four questions (M6, M8 keep; M21 hand-over on dialog close; T4 MIN-1 slot from the first READY), recorded in SCR-05, SCR-25, D-114. **Now:** two T3 fix rounds (timer path; data/journal/settings), then a T2 round (viewer/catalog motion M3–M5, T3 nits n4–n6), then the full local gate, the three accuracy variants on the rc commit, `v0.6.1-rc.1`, renders in `docs/design/review/v0.6.1/`, push to the PO. See „Resume"

## Resume (v0.6.1 #250, 2026-11-12)

1. Review files were in the session scratchpad (lost with the container); their findings are summarised in D-114's amendments and `docs/plans/v0.6.1-motion-review.md`. If the fix rounds did not land (check `git log` for `fix(brew)` / `fix:` commits after `e3d700b2`), re-run the reviews' fix scope: T4 MAJ-1 (timer layout ignores `ime` insets), MIN-1 (slot from the first READY), MIN-3 (narrow taste read), MIN-5 (refused write shows the stored value); T3 m1 (migration skips `skipped = 1`), m2 (catch failed writes on the session page), m3/MIN-2/M21 (Design hand-over on dialog close, re-sync when out of step, never under an alert), m5 (note editor never overwrites a newer note); motion M1–M5, M7.
2. Then: full local gate (`gate.sh`) on the rc commit; `accuracy.yml` with `variant` standard, alongside, two-brews on that commit (all PASS); `release.yml` (`ref` = branch, `version` = `0.6.1-rc.1`); renders (light/dark) in `docs/design/review/v0.6.1/`; push notification to the PO with the checklist (09 §4 „v0.6.1") and the design-review points (D-114 build notes; m6: GESCHMACK hidden when a finished session has no infusion).
3. Per-ticket verification in this release: see the working agreement below. Environment: `~/.gradle/init.d/mirror.gradle`, gw.sh/gate.sh (`docs/plans/v0.4.0-resume.md` §3), Robolectric jars pre-fetched.

## v0.6.1 (in build)

- **Scope (D-114):** taste and a note per infusion, entered on the timer screen for the infusion ended last and on the session page (chips *Alle · 1. · 2. …*; umamit.db v7 with a tested migration; the tea's radar averages every infusion); GESCHMACK folds; quick-timer chips 1:00 · 2:00 · 3:00 · 5:00 · 8:00 · 10:00 · 15:00; a full-screen photo viewer; ⋮ *Tee ansehen* / *Im Katalog ansehen*; the Katalog opens with every kind, grouped by kind; „N Tees"; *Darstellung* › *Design*.
- **Timer code:** #241 and #244 change `feature:brew`: T3+ implementers, a T4 timer review, the 04 §12 unit tests, and all three accuracy variants on the rc and the final (09 §4a). `core:timer` / `core:timer-engine` stay untouched; if a ticket needs them, stop and ask the PO.
- **Order and tiers:** the plan's task graph (PO-approved): #242 + #241, #243 + #247, #244 + #245, #246 + #249, #248; then #250 (reviews, accuracy, rc.1, checklist 09 §4 „v0.6.1", design review in `docs/design/review/v0.6.1/`) and #251 (merge, final).
- **Milestone PR:** #252 (draft).
- **Then Statistik (v0.7.0, M4 part 2; not yet specified):** its own specify session. Starting point, not yet confirmed by the PO: INS-01–INS-07 (02 §12), SCR-18, the A9 empty state. With D-114 the taste data is per infusion.
- **Known from v0.6.0, not blocking:** #231's „smooth with 10 000 sessions" is shown by JVM timing only. A tab that comes back to the Journal can show the earlier list for a frame until the next build.
- **Config housekeeping (2026-11-12, #253 on `main`):** a prompt audit of the Claude Code config pointed `CLAUDE.md` at the
  per-release plans and plainly restated `emil-design-eng`'s review format (a local edit; its hash no longer
  matches `skills-lock.json`, see `CLAUDE.md`). No app change.
- **Environment:** Maven Central answered HTTP 429 on 2026-11-11 and 2026-11-12; a local Gradle
  init script (`~/.gradle/init.d`, not in the repository) pointed
  `repo.maven.apache.org` at Google's Maven Central mirror
  (`maven-central.storage-download.googleapis.com`), which the „UmamiT"
  environment reaches. A new container needs it again if 429 persists:
  set the URL of every `repo.maven.apache.org` repository in
  `settingsEvaluated` (plugin and dependency repositories) and
  `allprojects`. One Gradle build at a time (`flock` as in
  `docs/plans/v0.4.0-resume.md` §3).
  Robolectric's runtime jars (SDK 35 `15-robolectric-13954326-i7`, SDK 36
  `16-robolectric-13921718-i7`) are fetched from Maven Central at test time: pre-fetch both from the mirror
  as `docs/plans/v0.4.0-resume.md` §3 shows for SDK 35.

## Phase

| Item | Status |
|---|---|
| Current milestone | **v0.6.1, feedback round on v0.6.0** (D-114; spec #240, tickets #241–#251, plan `docs/plans/v0.6.1.md`): specified, the build is next. Then **Statistik** (v0.7.0, M4 part 2), not yet specified. v0.6.0 is released (2026-11-12) |
| Development go from PO | ✅ **In effect** since 2026-10-22 09:05 Europe/Berlin (D-038) |
| Working branch | `claude/wonderful-davinci-fu8tqi` (from `main` `8a880d0d`; carries the v0.6.1 specs and plan) |
| Repository | `katznmeik/UmamiT` (renamed from `UmamiTea` on 2026-10-21; old URLs redirect) |
| Open PR | **#252** (draft, v0.6.1 milestone; #241 and #242 landed) |
| Latest APK | [`v0.6.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.6.0) (full release, versionCode 60099, from `main` `232c4b82`, certificate verified); rc [`v0.6.0-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.6.0-rc.1) (pre-release, 60001); [`v0.5.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.5.1) (50199); [`v0.5.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.5.0) (50099) |
| Decision numbers | Up to **D-114** used; the next free is **D-115** |

## Working agreements in effect

- **Tiers (D-101):** T1 Sonnet `low`, T2 Sonnet `medium`, T3 Opus `medium`, T4 Opus `high`. `xhigh` only with the PO's OK; never `max`. Timer, backup and migration code is never below T3.
- **Sessions (D-101):** one phase per session. Each phase ends with a handover in this file (and a resume note if the phase stops part-way) that names the model the next phase needs.
- **Next-session prompt (PO, 2026-11-10):** whenever a new session is to be started, the current session's last message ends with the ready-to-paste prompt for it (CLAUDE.md „Start of every session", 12 §5.1).
- **Briefs and reviews (D-101):** lean implementer briefs (12 §5.2); one milestone review at T3, plus T4 for a changed timer path; `/review-animations` once per milestone.
- **Parallelism:** at most **2** implementer subagents at a time, and **one Gradle build at a time** (the machine-wide lock in `v0.4.0-resume.md` §3).
- **Per-ticket verification in v0.6.1 (PO, 2026-11-12):** the orchestrator's diff review + a local cross-module check, then push; CI on #252 is the full gate, checked before the next merge builds on it; the full local gate once more on the rc commit (`docs/plans/v0.6.1.md` „Verification").
- **Reporting:** push notifications only when the PO must act (rc ready, a blocking question). Questions go to the PO as selectable options.
- **Merging:** Claude merges all PRs (D-037); the PO only approves milestone PRs.
- **CI (D-105):** a change of `docs/**`, `*.md` or `.claude/**` only skips the checks, build and emulator jobs and still counts as a successful run for the release check; anything else, or any doubt, runs the full gate.

## Waiting on the PO

1. **Resume the paused v0.6.1 build** (prompt at the end of the pausing session). Later: the on-device checklist and design review of `v0.6.1-rc.1`.
2. **Optional, at any time:** the PO's own alarm or Vorwarnung recordings (`docs/design/assets/source/sound-alert-<name>.wav`, `sound-prealert-<name>.wav`; WAV, sound from the first sample). Each becomes one more entry in `PreAlertSound` / `AlertSound`.
3. **Optional:** install the Claude GitHub App on the repository (https://github.com/apps/claude/installations/select_target). Without it, PR CI results don't wake a session; the sessions check CI themselves.
4. **Environment:** cloud sessions must run in the personal environment **„UmamiT"** (created 2026-11-08: `dl.google.com`, Gradle and Maven hosts, `raw.githubusercontent.com`, `www.tee-kontor-kiel.de`). The plain „Default" environment blocks `dl.google.com`, so the Android plugin doesn't resolve.

## Open for later

- **Ideas the PO kept for later (D-112, 09 §5):** custom descriptors, notes per infusion, manual journal entries, brew coach tips.
- **Parked from v0.3.0:** the two *Pause* buttons and the shortened tea name in the brew row at font 2.0.
- **#58:** its remaining optional review findings stay open for later milestones.
- **Images:** all delivered. A6 (Journal) is built in since v0.6.0 (#231); A9 (Statistik) comes with the Statistik release.

## Known caveats

- Mockup images still show the old name „UmamiTea".
- Live Update / ProgressStyle and the full-screen alert were verified on the Galaxy with `v0.0.2-probe`; the full timer keeps those patterns (D-043, D-044, `docs/plans/M1-android16-api-notes.md`). Now Bar presence is best effort.
- Tee-Kontor uses Cloudflare; refreshes may get blocked (handled by stopping without changes).
- After a reboot, alerts fire only once the phone has been unlocked (E2); Samsung's automatic overnight restart should be disabled (ONB-02).
- `ProcessDeathTest` (two brews, process death; emulator, API 36) can fail on its single read of the diagnostics log; rerun CI (D-112 Q19). Its brew-and-quick-timer test failed once on API 35 on a timing bound (2327 ms against 2 s, run 43094657346, runners degraded).
- Robolectric fetches its runtime jar at test time; a network hiccup has failed CI before. Rerun once and comment on the PR.
