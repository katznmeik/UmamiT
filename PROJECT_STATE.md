# PROJECT_STATE — UmamiT

> Handover between sessions: **only the current state**. Update it at the end
> of every session or phase. Finished releases, past plans and the session
> log are in [`docs/history.md`](docs/history.md); read that only when you
> need it (D-101).

**Last updated:** 2026-11-12. **v0.6.0 (the journal, M4 part 1; D-112, D-113) is released**: [`v0.6.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.6.0) from `main` `232c4b82` (versionCode 60099, certificate verified), after the PO's approval of the on-device checklist on `v0.6.0-rc.1` and of the design review with the slim pin slider (#237). The gate, CI and all three accuracy variants passed on the released tree; details in `docs/history.md`. **Next phase (fresh session): a feedback round on v0.6.0** (the PO, 2026-11-12: „another feedback round before anything else"): its specify session takes the PO's points through a grilling round, then spec, plan and tickets. Statistik (M4 part 2) follows after it. No development before that (CLAUDE.md rule 1).

## Next: feedback round on v0.6.0 (not yet specified)

- **The PO's decision (2026-11-12, after the final release):** a feedback round on v0.6.0 comes before
  anything else; Statistik waits for it. The PO brings the points to the specify session; none is recorded yet.
- **Specify session first:** grill the PO on each point with questions as selectable options, then decisions
  (from D-114), the specs the points touch, 09 §2 and a §4 checklist, `/to-spec`, a plan in `docs/plans/` and
  tickets. Nothing is built before the PO has answered and approved the ticket breakdown. The pattern is the
  feedback round on v0.5.0 (`docs/plans/v0.5.1.md`, D-110).
- **Open, for the PO to answer in that session:** which release number the round gets, and what that means
  for Statistik's number. 09 §2, D-112 and 02 §12 still call Statistik „v0.6.1"; nothing is renumbered until
  the PO has answered.
- **If a point needs timer code:** the tiers change (never below T3), the T4 timer review applies, and every
  rc needs the accuracy runs (09 §4a).
- **Then Statistik (M4 part 2; not yet specified):** its own specify session. Starting point, not yet
  confirmed by the PO: INS-01–INS-07 (02 §12), SCR-18 and the Statistik tab in 03, the A9 empty state
  (`docs/design/assets.md`; the image is delivered). Statistik shows „Deine Statistik folgt bald" until then.
  v0.6.0 gives it ratings, taste values, descriptors and notes per session, per-tea rating and taste
  aggregates (05; umamit.db v6) and the journal's filters.
- **Config housekeeping (2026-11-12):** a prompt audit of the Claude Code config pointed `CLAUDE.md` at the
  per-release plans and plainly restated `emil-design-eng`'s review format (a local edit; its hash no longer
  matches `skills-lock.json`, see `CLAUDE.md`). No app change.
- **Known from v0.6.0, not blocking:** #231's „smooth with 10 000 sessions" is shown by JVM timing only. A tab
  that comes back to the Journal can show the earlier list for a frame until the next build. The drag feel of
  the slim pin is first seen on the device with `v0.6.0` (D-113, answer 7): the PO's word on it is welcome.
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
| Current milestone | **Feedback round on v0.6.0**: not yet specified, release number open (the PO decides it in the specify session, which is next). Then **Statistik** (M4 part 2; D-112; called v0.6.1 in the specs so far), not yet specified. **v0.6.0** (the journal, M4 part 1; D-112, D-113, spec #221, plan `docs/plans/v0.6.0.md`) is released (2026-11-12) |
| Development go from PO | ✅ **In effect** since 2026-10-22 09:05 Europe/Berlin (D-038) |
| Working branch | `claude/wonderful-davinci-fu8tqi`, restarted from `main` after every merge (#235 `232c4b82`, the docs PR #238 `e1d18896`, and the docs PR that carries this row); it equals `main` until the feedback round's first commit |
| Repository | `katznmeik/UmamiT` (renamed from `UmamiTea` on 2026-10-21; old URLs redirect) |
| Open PR | none (#235, the v0.6.0 milestone PR, was merged on 2026-11-12) |
| Latest APK | [`v0.6.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.6.0) (full release, versionCode 60099, from `main` `232c4b82`, certificate verified); rc [`v0.6.0-rc.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.6.0-rc.1) (pre-release, 60001); [`v0.5.1`](https://github.com/katznmeik/UmamiT/releases/tag/v0.5.1) (50199); [`v0.5.0`](https://github.com/katznmeik/UmamiT/releases/tag/v0.5.0) (50099) |
| Decision numbers | Up to **D-113** used; the next free is **D-114** |

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

1. **Feedback round on v0.6.0:** the PO's points and the answers in the specify session's grilling round (the next session asks them as selectable options), including the round's release number. Nothing else waits on the PO.
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
