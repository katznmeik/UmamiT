# 09 · Delivery Plan & Roadmap

> Status: **Draft v0.2** · Last updated: 2026-10-21
> v0.2: personal app, built by Claude, delivered as one PR per milestone and
> installed from GitHub Releases. No store, no beta programme.

## 1. Ground rules

1. **No development before the PO's explicit go.** Milestone M0 (this
   specification) is the only work until then.
2. **One pull request per milestone** into `main`; CI must be green; the PO
   reviews and approves; **Claude merges** (D-037).
3. **Every milestone ends with an installable APK** on a GitHub Release
   (`v0.x.0`), plus an on-device acceptance checklist in the PR. Release
   path ([06 §6](06-architecture.md#6-build--distribution)): Claude triggers
   `release.yml` (`workflow_dispatch`), which creates tag and release.
   During the milestone the PO tests **release candidates** `v0.x.0-rc.N`
   (pre-releases built from the PR branch); after the merge the final
   `v0.x.0` is built from `main`. The version lives only in
   `gradle.properties`.
4. Specs are updated in the same PR when behaviour changes.
5. **Definition of done per milestone:** the on-device checklist passes on
   the PO's Galaxy **and** a design review round passes. For the review,
   every PR includes automatically rendered screenshots (Roborazzi) of all
   new or changed screens in light and dark theme, committed under
   `docs/design/review/<milestone>/`. Screenshots use placeholder images,
   never Tee-Kontor photos (public repo, D-016). The PO compares them with
   the mockups and comments in the PR; fixes follow before Claude merges.
6. Work is delegated to subagents with model and effort per
   [12 · Subagent Distribution Plan](12-agent-plan.md).

## 2. Milestones

| # | Milestone | Content | Requirements | Release |
|---|---|---|---|---|
| **M0** | **Specification** | These documents; PO review; open questions answered | — | — (PR only) |
| — | **Go gate** | PO gives explicit go for development | — | — |
| **M1** | **Timer first** | Probe release, project skeleton, CI (incl. emulator job), signing & release workflow, design system, navigation shell, Einrichtung, complete timer with Live Update and full-screen alert, Schritte-Sheet, quick timer, seed teas ([08 §2.5a](08-content.md#25a-m1-seed-teas-pos-current-teas)), minimal journal, diagnostics log | Single source: [M1 plan §2](../plans/M1-timer.md#2-scope) | v0.0.1-probe, v0.1.0 |
| **M2** | **Tee-Kontor catalog** | Re-check selectors (V-01 done), parser + synthetic fixtures, variant merge, style mapping, recipe derivation, refresh worker with progress, photos, catalog list/detail, guides, „Rezept prüfen" | CAT-*, ONB-03, SET-22/23 | v0.2.0 |
| **M3** | **Collection** | Meine Tees list/detail/editor, add from catalog with package size, recipes & steps editor, teaware with scaling, stock, cost, freshness, reset from catalog | TEA-*, PRF-*, WARE-*, INV-*, BRW-06/08 | v0.3.0 |
| **M4** | **Journal & statistics** | Auto-log refinements, notes editor and recipe update added to the „Gespeichert" sheet (TMR-12's sheet itself ships in M1), session detail/edit, rating, taste radar, descriptors, Statistik (Überblick, Bewertungen, Verbrauch) | JRN-*, PRF-07, INS-* | v0.4.0 |
| **M5** | **Data & polish → 1.0** | Device backup verification (rules exist since M1), simple manual export (no in-app restore, D-036), reminders, all settings, accessibility pass, Baseline Profile, performance budgets, diagnostics log extended to the catalog, final illustrations | BAK-01, BAK-05, BAK-06, SET-*, NFR-* | **v1.0.0** |

There are no calendar estimates: pace depends on session availability and the
PO's review and testing turnaround.

## 3. Prerequisites before / during development

| ID | Item | Needed for |
|---|---|---|
| P-01 | PO's explicit go | M1 start |
| P-02 | ✅ `www.tee-kontor-kiel.de` allowed in the cloud environment (verified 2026-10-21); add any image CDN host found during V-01 | M2 |
| P-03 | ✅ Application ID `com.umamit.app` (D-027) | M1 |
| P-04 | Release keystore created; stored in GitHub secrets; PO keeps an offline copy | M1 release |
| P-05 | ✅ Serif font Cormorant Garamond (D-027); imagery approach decided (D-029) | — |
| P-06 | PO generates and uploads images A1–A5 ([`docs/design/assets.md`](../design/assets.md)) | M1 (placeholders are used until they arrive) |
| P-07 | PO generates A6–A12 | M2–M5 |
| V-01 | ✅ [08 §2](08-content.md#2-tee-kontor-catalog-import) verified against the live site and updated (2026-10-21); re-check selectors at M2 start | M2 start |

## 4. Acceptance checklists (on the PO's Galaxy)

### M1 — Probe release (`v0.0.1-probe`, early in M1)

All four items must pass before M1-09/M1-11 are finished; a failing item is
fixed and re-tested with `v0.0.N-probe` (D-040). Now Bar presence is recorded
only.


- [ ] APK installs from the GitHub pre-release.
- [ ] Probe screen: a 1-min test step with the phone locked and the screen off → screen turns on with the full-screen alert at the end; synthesised sound plays (ringer normal), vibration only (vibrate), visual only (silent).
- [ ] Status-bar chip and lock-screen Live Update with countdown visible during the step; note whether it also appears in the Now Bar (best effort, report the result).
- [ ] Probe's built-in status list (notifications, promoted notifications, full-screen intent, exact alarm, battery) all ✓ after following its links.

### M1 — Timer (release candidates `v0.1.0-rc.N`, then `v0.1.0`)

- [ ] APK installs from the GitHub Release; the next rc installs over it.
- [ ] Einrichtung: notifications, battery „Uneingeschränkt", Live notifications and Vollbild-Alarm all show ✓ after setup; Samsung auto-restart hint shown.
- [ ] Seed teas Tanabata and Hisui present with photos (downloaded during the first-start setup online), recipes and 100 g stock; seed vessels Kyusu (250 ml) and Becher mit Sieb (400 ml).
- [ ] Waiting 30 min in READY demotes the Live Update to a quiet notification; *Start* from it works. Next morning: the session left waiting the evening before is in the journal („Sitzung gespeichert").
- [ ] *Nochmal brühen* tea → timer READY in one tap; *Start* = second tap.
- [ ] 3-step kyusu session: READY waits for *Start*; kettle hint shows the step temperature.
- [ ] Screen dims ~10 s into a steep and brightens at the pre-alert.
- [ ] Pre-alert tick 10 s before the end (global default) on the 1:00 Hisui infusion; none on the 0:10 infusions (shorter than 15 s).
- [ ] Pre-alert override: in SCR-04 open the Schritte-Sheet, set *Vorwarnung* to 20 s → tick 20 s before the end of the first infusion.
- [ ] Schritte-Sheet: change a step's time and temperature, add and delete a step; the timer uses the changes; *Weiterer Aufguss* in FINISHED opens an editable step.
- [ ] Listen to all alert sounds via *Alarm testen*; approve the default „Klangschale" or pick another (Q-15).
- [ ] Alert with ringer normal (sound + vibration), vibrate (vibration only), silent (visual only); stops on *Abgegossen* or after ~15 s.
- [ ] Phone locked, screen off during a 2-min step: pre-alert and alert on time; the screen turns on and the timer shows over the lock screen.
- [ ] App swiped from recents during a step: alert still fires.
- [ ] Live Update visible as status chip and on the lock screen; *Pause*/*Weiter*/*Abgegossen* work from it.
- [ ] (Best effort, not blocking) Live Update appears in the Now Bar.
- [ ] Reboot test: set a step to 15 min via the Schritte-Sheet, start it, reboot, unlock the phone before the deadline → the alert fires on time.
- [ ] Accuracy: after a few sessions, *Einstellungen › Über › Diagnose* shows pre-alert and alert fire times within 1 s of the scheduled time; *Kopieren* works.
- [ ] Session appears in the minimal journal list with actual times („2 von 3 Aufgüssen" when finished early).

### M2 — Catalog

- [ ] *Katalog laden* completes (first run ≈ 10 min); ~260–280 teas; no duplicates per size; no sets or accessories.
- [ ] 10 spot-checked teas: name, type, origin, prices, photo correct; brewing table/text shown verbatim; derived recipe follows §2.5; flags plausible.
- [ ] Second refresh reports „0 neu · 0 geändert" when nothing changed.
- [ ] Airplane mode during refresh: clear error, old catalog intact.
- [ ] Guides readable offline.

### M3 — Collection

- [ ] Add catalog tea with 100 g package → stock 100 g, price set, recipes copied, photo copied.
- [ ] Brew → stock reduced by leaf; cost per cup shown.
- [ ] Choosing a vessel rescales leaf; recipe pre-alert override works.
- [ ] Catalog refresh does not change the collection tea; *Aus Katalog zurücksetzen* does.

### M4 — Journal & statistics

- [ ] Finishing shows the „Gespeichert" sheet; *Zeiten ins Rezept übernehmen* appears only when times differ.
- [ ] Notes, rating, taste, descriptors can be added later.
- [ ] Statistik tabs show plausible numbers for a week of real use.

### M5 — 1.0

- [ ] Device backup restore check: `adb shell bmgr backupnow com.umamit.app` then uninstall/reinstall restores teas, journal and settings (or a new-device transfer when available); no session is resumed after the restore.
- [ ] *Sicherung erstellen* writes a `.umamit` file that opens as a ZIP and contains `data.json` with the teas and sessions and the own photos.
- [ ] 200 % font, dark theme, TalkBack spot checks pass.

## 5. Post-1.0 backlog

| Item | Ref |
|---|---|
| Home-screen widget, app shortcuts, Quick Settings tile | NOT-06…08 |
| Brew coach tips | JRN-14 |
| Weekly stock digest | INV-07 |
| CSV export | BAK-04 |
| Galaxy Watch tile / companion | — |
| Tags, manual journal entries | TEA-07, JRN-12 |
| Non-green Tee-Kontor categories in the catalog | NG6 |
| In-app restore of a `.umamit` file | BAK-02 (D-036) |
| Direct-boot alert before the first unlock | 04 §7.3 |

## 6. Risks

| # | Risk | Mitigation |
|---|---|---|
| R1 | Samsung battery management delays alerts | Exact alarms + FGS + battery exemption + Never-sleeping apps; on-device acceptance tests |
| R2b | Cloudflare bot protection blocks the refresh | Low rate, clear user agent, stop on challenge with a clear message, retry later; no bypassing |
| R2 | Tee-Kontor site changes break the parser | Isolated parser with fixtures and `parserVersion`; atomic refresh with sanity checks; clear error; quick fix release |
| R3 | Brewing info too inconsistent for good recipes | Table-first rules, verbatim display, „Rezept prüfen" flags, easy editing |
| R4 | Live Update APIs or Now Bar behave differently on One UI 8 | Probe release `v0.0.1-probe` tested on the Galaxy before the full timer; fallback to normal ongoing notification; Now Bar is best effort |
| R5 | Losing the release keystore blocks updates | Keystore in GitHub secrets + PO offline copy |
| R6 | ~~Network allowlist not granted~~ | **Resolved:** `www.tee-kontor-kiel.de` is reachable from the cloud environment (P-02) |
| R7 | Copyright of scraped content | Personal use only, not redistributed, never committed; synthetic test fixtures (D-016) |
