# 09 · Delivery Plan & Roadmap

> Status: **Draft v0.2** · Last updated: 2026-10-21
> v0.2: personal app, built by Claude, delivered as one PR per milestone and
> installed from GitHub Releases. No store, no beta programme.

## 1. Ground rules

1. **No development before the PO's explicit go.** Milestone M0 (this
   specification) is the only work until then.
2. **One pull request per milestone** into `main`; CI must be green; the PO
   reviews and merges.
3. **Every milestone ends with an installable APK** on a GitHub Release
   (`v0.x.0`), plus an on-device acceptance checklist in the PR.
4. Specs are updated in the same PR when behaviour changes.
5. Work is delegated to subagents with model and effort per
   [12 · Subagent Distribution Plan](12-agent-plan.md).

## 2. Milestones

| # | Milestone | Content | Requirements | Release |
|---|---|---|---|---|
| **M0** | **Specification** | These documents; PO review; open questions answered | — | — (PR only) |
| — | **Go gate** | PO gives explicit go for development | — | — |
| **M1** | **Timer first** | Project skeleton, CI, signing & release workflow, design system (washi/steeping themes, serif font, core components), navigation shell (Start · Journal · Sammlung · Statistik, placeholders), Einrichtung reliability page, complete timer (multi-step recipes, Pause, pre-alert, alerts per ringer mode, Live Update, exact alarms, FGS, recovery, reboot), quick timer, active brew bar, timer settings, minimal manual tea + recipe creation (name, style template, recipe) for testing, minimal session log | ONB-01/02/04, BRW-01…03, BRW-10, TMR-*, NOT-01…03, SET-09…11, SET-14, SET-17, SET-24, SET-31, GEN-* | v0.1.0 |
| **M2** | **Tee-Kontor catalog** | Re-check selectors (V-01 done), parser + synthetic fixtures, variant merge, style mapping, recipe derivation, refresh worker with progress, photos, catalog list/detail, guides, „Rezept prüfen" | CAT-*, ONB-03, SET-22/23 | v0.2.0 |
| **M3** | **Collection** | Meine Tees list/detail/editor, add from catalog with package size, recipes & steps editor, teaware with scaling, stock, cost, freshness, reset from catalog | TEA-*, PRF-*, WARE-*, INV-*, BRW-06/08 | v0.3.0 |
| **M4** | **Journal & statistics** | Auto-log refinements, „Gespeichert" sheet with notes and recipe update, session detail/edit, rating, taste radar, descriptors, Statistik (Überblick, Bewertungen, Verbrauch) | JRN-*, PRF-07, INS-*, TMR-12 | v0.4.0 |
| **M5** | **Data & polish → 1.0** | Device backup rules, manual export/restore, reminders, all settings, accessibility pass, Baseline Profile, performance budgets, diagnostics log, final illustrations | BAK-*, SET-*, NFR-* | **v1.0.0** |

There are no calendar estimates: pace depends on session availability and the
PO's review and testing turnaround.

## 3. Prerequisites before / during development

| ID | Item | Needed for |
|---|---|---|
| P-01 | PO's explicit go | M1 start |
| P-02 | ✅ `www.tee-kontor-kiel.de` allowed in the cloud environment (verified 2026-10-21); add any image CDN host found during V-01 | M2 |
| P-03 | ✅ Application ID `com.umamitea.app` (D-027) | M1 |
| P-04 | Release keystore created; stored in GitHub secrets; PO keeps an offline copy | M1 release |
| P-05 | ✅ Serif font Cormorant Garamond (D-027); imagery approach decided (D-029) | — |
| P-06 | PO generates and uploads images A1–A5 ([`docs/design/assets.md`](../design/assets.md)) | M1 (placeholders are used until they arrive) |
| P-07 | PO generates A6–A12 | M2–M5 |
| V-01 | ✅ [08 §2](08-content.md#2-tee-kontor-catalog-import) verified against the live site and updated (2026-10-21); re-check selectors at M2 start | M2 start |

## 4. Acceptance checklists (on the PO's Galaxy)

### M1 — Timer

- [ ] APK installs from the GitHub Release; updates install over it.
- [ ] Einrichtung: notifications, battery „Uneingeschränkt", Live notifications all show ✓ after setup.
- [ ] 3-step kyusu session: READY waits for *Start*; kettle hint shows the step temperature.
- [ ] Pre-alert tick at 5 s (global) and at 15 s (recipe override); none for a 5 s step.
- [ ] Listen to all alert sounds via *Alarm testen*; approve the default „Klangschale" or pick another (Q-15).
- [ ] Alert with ringer normal (sound + vibration), vibrate (vibration only), silent (visual only); stops on *Abgegossen* or after ~15 s.
- [ ] Screen off during a 2-min step: pre-alert and alert on time.
- [ ] App swiped from recents during a step: alert still fires.
- [ ] Live Update visible as status chip, on lock screen and in the Now Bar; *Pause*/*Weiter*/*Abgegossen* work from it.
- [ ] Reboot during a 25-min cold-brew step and unlock the phone: alert still fires (on time if unlocked before the deadline).
- [ ] Session appears in the minimal journal list with actual times.

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

- [ ] Manual export → delete all data → restore → identical state.
- [ ] Device backup includes database (verified via backup test command or new-device restore when available).
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
| Bundled catalog snapshot | CAT-14 |

## 6. Risks

| # | Risk | Mitigation |
|---|---|---|
| R1 | Samsung battery management delays alerts | Exact alarms + FGS + battery exemption + Never-sleeping apps; on-device acceptance tests |
| R2b | Cloudflare bot protection blocks the refresh | Low rate, clear user agent, stop on challenge with a clear message, retry later; no bypassing |
| R2 | Tee-Kontor site changes break the parser | Isolated parser with fixtures and `parserVersion`; atomic refresh with sanity checks; clear error; quick fix release |
| R3 | Brewing info too inconsistent for good recipes | Table-first rules, verbatim display, „Rezept prüfen" flags, easy editing |
| R4 | Live Update APIs or Now Bar behave differently on One UI 8 | Fallback to normal ongoing notification; verify early in M1 |
| R5 | Losing the release keystore blocks updates | Keystore in GitHub secrets + PO offline copy |
| R6 | Network allowlist not granted | M1, M4 and M5 do not depend on it; M3 works with manually added teas; M2 waits |
| R7 | Copyright of scraped content | Personal use only, not redistributed, never committed; synthetic test fixtures (D-016) |
