# PROJECT_STATE — UmamiT

> Handover between sessions. Update at the end of every session and
> milestone. Newest information first.

**Last updated:** 2026-10-21

## Phase

| Item | Status |
|---|---|
| Current milestone | **M0 — Specification** |
| Development go from PO | ✅ **Given on 2026-10-21, effective 2026-10-22 09:05 Europe/Berlin** (after the PO's weekly usage limit resets), unless the PO says otherwise before then. **No app code before that time.** |
| Spec version | v0.2 (`docs/specs/`) |
| Working branch | `claude/sweet-brown-e16crp` |
| Repository | `katznmeik/UmamiT` (renamed from `UmamiTea` on 2026-10-21; old URLs redirect) |
| Merged PRs | [katznmeik/UmamiT#1](https://github.com/katznmeik/UmamiT/pull/1): M0 specification (merged 2026-10-21) |
| Open PR | none |
| Merging | Claude merges all PRs (D-037); the PO only approves milestone PRs |
| Latest APK | none |

## Done (M0)

- Full specification v0.2 (01–12), reviewed twice for consistency.
- Tee-Kontor site structure verified live (V-01); catalog spec rewritten.
- M1 seed data verified live: Bio Sencha Tanabata (TG0213) and Bio Sencha
  Hisui (TG0557), plus the PO's vessels (Kyusu 250 ml usable, Becher mit Sieb
  400 ml).
- Design: brand mockup, mockups v2, mockup prompt, asset list with prompts.
- Subagent distribution plan (12).
- App renamed to **UmamiT** (D-033), application ID `com.umamit.app`.
- `CLAUDE.md`, this file, PR template and the M1 plan (`docs/plans/M1-timer.md`).
- Environment checked: Android SDK, Maven, Gradle and font hosts are
  reachable; JDK 21 and Gradle are preinstalled; the Android SDK is not; no
  KVM (emulator tests run in CI).
- Spec review (Opus xhigh) and PO decisions applied as D-036: three network
  uses, synthesised sounds, full-screen alert, simplified backup, Schritte-Sheet,
  CI emulator job, release candidates, probe release, catalog hardening.

## Waiting on the PO

1. Generate images A1–A5 (`docs/design/assets.md`); placeholders are used
   until then.
2. During M1: save the two signing files Claude delivers in the session and
   add 2 GitHub secrets (06 §6.1).
3. During M1: install and test the probe APK `v0.0.1-probe` (09 §4) and
   report the results.
4. At the M1 PR: test the release candidate, listen to the alert sounds and
   approve the default (Q-15).

## Next steps (from 2026-10-22 09:05 Europe/Berlin)

Follow `docs/plans/M1-timer.md`, starting with M1-00 (session-start hook
that installs the Android SDK), M1-01 (Gradle skeleton with all module
stubs), M1-02/03 (CI incl. emulator job, signing, release workflow) and the
probe release M1-03a (spec issue [#6](https://github.com/katznmeik/UmamiT/issues/6); tickets #8–#17 in dependency order, start with #8 and #9).

## Open questions

None blocking. The remaining approvals are listed under "Waiting on the PO".

## Known caveats

- Mockup images still show the old name „UmamiTea".
- Live Update / ProgressStyle and full-screen intent behaviour must be
  verified with the probe release on the Galaxy (`docs/specs/04-brew-timer.md`
  §8.3). Now Bar presence is best effort.
- Tee-Kontor uses Cloudflare; refreshes may get blocked (handled by stopping
  without changes).
- After a reboot, alerts fire only once the phone has been unlocked (E2);
  Samsung's automatic overnight restart should be disabled (ONB-02).

## Session log

| Date | Summary |
|---|---|
| 2026-10-21 | Spec #6 broken into 10 tickets (#8–#17, sub-issues of #6); frontier: #8 (SDK hook) and #9 (signing key). The PO created the missing triage labels; #9 and #17 are labelled `ready-for-human`. |
| 2026-10-21 | Spec issue [#6](https://github.com/katznmeik/UmamiT/issues/6) for M1-00 … M1-03a (`ready-for-agent`); M1 plan updated (test seams §3.1, task details); D-039 recorded. |
| 2026-10-21 | Matt Pocock skills set up: GitHub Issues as tracker, default triage labels, domain docs mapped to specs 10/11 (`docs/agents/`). An early M1 start was stopped by the PO; the go stays 2026-10-22 09:05. |
| 2026-10-21 | PO gave the go for development, effective 2026-10-22 09:05 Europe/Berlin (D-038); a scheduled check-in starts M1 then. |
| 2026-10-21 | PR #2 merged by Claude; rule D-037 (Claude merges all PRs) added. |
| 2026-10-21 | PR #1 (M0 specs) merged; repository renamed to `katznmeik/UmamiT`; references updated. |
| 2026-10-21 | Opus xhigh spec review; fixes applied (D-036). |
| 2026-10-21 | Grill round 4 (D-034) recorded; PO asked for an M1 walkthrough before deciding on the go. |
| 2026-10-21 | Specs v0.1 → v0.2 after PO interviews (grill rounds 1–3), live Tee-Kontor verification, seed teas, agent plan, rename to UmamiT, project docs. |
