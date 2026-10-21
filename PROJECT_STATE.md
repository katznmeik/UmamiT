# PROJECT_STATE — UmamiT

> Handover between sessions. Update at the end of every session and
> milestone. Newest information first.

**Last updated:** 2026-10-21

## Phase

| Item | Status |
|---|---|
| Current milestone | **M0 — Specification** |
| Development go from PO | ❌ **Not given.** Do not write app code. |
| Spec version | v0.2 (`docs/specs/`) |
| Working branch | `claude/sweet-brown-e16crp` |
| Open PR | [katznmeik/UmamiTea#1](https://github.com/katznmeik/UmamiTea/pull/1): M0 specification (not merged) |
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
  reachable; JDK 21 and Gradle are preinstalled; the Android SDK is not.

## Waiting on the PO

1. **Explicit go** for development.
2. Review and merge [katznmeik/UmamiTea#1](https://github.com/katznmeik/UmamiTea/pull/1).
3. Optional: rename the GitHub repository to `UmamiT` (Settings › General).
4. Generate images A1–A5 (`docs/design/assets.md`); placeholders are used
   until then.
5. During M1: add the signing secrets to GitHub (instructions follow in
   M1-03), then listen to the alert sounds and approve the default (Q-15).

## Next steps (after the go)

Follow `docs/plans/M1-timer.md`, starting with M1-00 (session-start hook
that installs the Android SDK) and M1-01 (Gradle skeleton).

## Open questions

None blocking. The remaining approvals are listed under "Waiting on the PO".

## Known caveats

- Mockup images still show the old name „UmamiTea".
- Live Update / ProgressStyle API names must be verified against the
  Android 16 SDK at implementation (`docs/specs/04-brew-timer.md` §8.3).
- Tee-Kontor uses Cloudflare; refreshes may get blocked (handled by stopping
  without changes).
- After a reboot, alerts fire only once the phone has been unlocked (E2).

## Session log

| Date | Summary |
|---|---|
| 2026-10-21 | Specs v0.1 → v0.2 after PO interviews (grill rounds 1–3), live Tee-Kontor verification, seed teas, agent plan, rename to UmamiT, project docs. |
