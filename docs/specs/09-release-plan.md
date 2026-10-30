# 09 · Delivery Plan & Roadmap

> Status: **Draft v0.2** · Last updated: 2026-10-30 (v0.2.2 from the PO's feedback on v0.2.1, D-097; 2026-10-28: v0.2.1: sold-out teas and a tea from a shop link, D-094; 2026-10-26: v0.2.1 and v0.3.0 from the PO's feedback, M3 → v0.4.0, M4 → v0.5.0, v0.2.1 checklist, D-093; slowest starts in the accuracy summary, D-092; accuracy run before each rc, §4a, D-067; measured up to the sound, D-068; M1 checklist: Doze, quick timer, *Beenden & neu starten*, refused start; release gate, D-070)
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
   `gradle.properties`. `release.yml` builds only a commit on which CI
   passed (D-070). Before each release candidate the accuracy
   workflow must pass ([§4a](#4a-accuracy-run-before-each-release-candidate),
   D-067).
4. Specs are updated in the same PR when behaviour changes.
5. **Definition of done per milestone:** the on-device checklist passes on
   the PO's Galaxy **and** a design review round passes. For the review,
   every PR includes automatically rendered screenshots (Roborazzi) of all
   new or changed screens in light and dark theme, committed under
   `docs/design/review/<milestone>/`, plus short GIF recordings of the key
   motions (D-041). Screenshots use placeholder images,
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
| **M2** | **Tee-Kontor catalog** | Re-check selectors (V-01 done), parser + synthetic fixtures, variant merge, style mapping, recipe derivation, refresh worker with progress, photos, catalog list/detail, *In Sammlung aufnehmen* / *Nachkauf erfassen* and the picker flow, *Rezept passt*, guides (last slice), „Rezept prüfen", small #58 items (D-073) | CAT-*, ONB-03, SET-22/23 | v0.2.0 |
| **v0.2.1** | **Feedback on v0.2.0-rc.1** (D-093, #79; points 2–9) | Legible ← and ⋮ on bright tea photos; pre-alert per infusion with the automatic rule, into the recipe via *Im Rezept speichern* (umamit.db v2 with a tested migration); four new, much louder pre-alert sounds with a ≈ 3 s fall-off and *Vorwarnton*; each rc needs the accuracy run (§4a; timer code changes); seven more seed vessels, added once to existing installs; stock correction from „… g übrig"; *Aus Sammlung entfernen* from the tea picker (archive, undo); Kräutertees in the catalog (style `herbal`, hidden by default, chip *Kräutertee*); sold-out teas missing from the sitemap stay in the catalog, and a tea can be added from a shared or pasted shop link (D-094, #97, #98) | SCR-05, TMR-23, SCR-31, PRF-03 (pre-alert per step), SET-26, WARE-06, INV-03 (correction only), BRW-02, TEA-04 (archive from the picker only), CAT-01, CAT-10, CAT-16, CAT-17, GEN-01 (d), NFR-PERF-11 | v0.2.1 |
| **v0.2.2** | **Feedback on v0.2.1** (D-097; seven points) | Leaf and water on every READY; the catalog's notifications open the refresh sheet; tea groups in the picker; *Knapp* from 15 g with a Start card and a setup marker; the quick timer's silent pre-alert fixed, its own *Vorwarnung* selector and an 8:00 chip; *Merken* for catalog teas (umamit.db v3 with a tested migration); each rc needs the accuracy run (§4a; timer code changes) | BRW-11, BRW-08 (marker only), SCR-03, SCR-22, INV-05, INV-10, SET-16, TMR-22, CAT-18 | v0.2.2 |
| **v0.3** | **Quick timers alongside a brew** (D-093, #79; point 1; planned) | One brew session as today plus quick timers that can always start alongside it: engine, `active_brew`, alarms, service, Live Update and alert player for two timers, with the timer tests of 04 §12 extended (rule 8); its own plan before building | TMR-21, TMR-22 | v0.3.0 |
| **M3** | **Collection** | Meine Tees list/detail/editor, add from catalog with package size, recipes & steps editor, teaware with scaling, stock, cost, freshness, reset from catalog | TEA-*, PRF-*, WARE-*, INV-*, BRW-06/08 | v0.4.0 |
| **M4** | **Journal & statistics** | Auto-log refinements, notes editor and recipe update added to the „Gespeichert" sheet (TMR-12's sheet itself ships in M1), session detail/edit, rating, taste radar, descriptors, Statistik (Überblick, Bewertungen, Verbrauch) | JRN-*, PRF-07, INS-* | v0.5.0 |
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

### M1 — Probe release (`v0.0.N-probe`, early in M1)

_Passed with `v0.0.2-probe` on 2026-10-22 (#17). The probe screen was removed
in M1-18 (#54); this checklist is kept as a record._

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
- [ ] Seed teas Tanabata and Hisui present in the tea picker (*Sammlung* is a placeholder until M3) with photos (downloaded during the first-start setup online), recipes and 100 g stock; seed vessels Kyusu (250 ml) and Becher mit Sieb (400 ml).
- [ ] Waiting 30 min in READY demotes the Live Update to a quiet notification; *Start* from it works. Next morning: the session left waiting the evening before is in the journal („Sitzung gespeichert").
- [ ] *Nochmal brühen* tea → timer READY in one tap; *Start* = second tap.
- [ ] 3-step kyusu session: READY waits for *Start*; kettle hint shows the step temperature.
- [ ] Screen dims ~10 s into a steep and brightens at the pre-alert; at night with a low brightness setting, „dimming" never makes the screen brighter, also with adaptive brightness on (D-058).
- [ ] Pre-alert tick 10 s before the end (global default) on the 1:00 Hisui infusion; none on the 0:10 infusions (shorter than 15 s).
- [ ] Pre-alert override: in SCR-04 open the Schritte-Sheet, set *Vorwarnung* to 20 s → tick 20 s before the end of the first infusion.
- [ ] Schritte-Sheet: change a step's time and temperature, add and delete a step; the timer uses the changes; *Weiterer Aufguss* in FINISHED opens an editable step.
- [ ] Listen to all alert sounds via *Alarm testen*; approve the default „Klangschale" or pick another (Q-15).
- [ ] Alert with ringer normal (sound + vibration), vibrate (vibration only), silent (visual only); stops on *Abgegossen* or after ~15 s.
- [ ] Phone locked, screen off during a 2-min step: pre-alert and alert on time; the screen turns on and the timer shows over the lock screen.
- [ ] App swiped from recents during a step: alert still fires.
- [ ] Long step in Doze (TMR-18: the exact alarm alone, no service during the step): start a 30-min quick timer (or a cold brew), lock the phone and leave it lying still with the screen off and **not** charging until the end → the alert comes on time, full screen, with sound.
- [ ] Quick timer (TMR-22, SCR-07): a 1-min quick timer from Start, phone locked with the screen off → tick, alert on time, *Fertig* on the lock screen ends it and nothing appears in the journal. Then a 12-min quick timer with the app in the background (a long step) → „Fertig um …" in the Live Update and the alert on time.
- [ ] *Beenden & neu starten* during a running step (TMR-21): while a session's step runs, start another tea from Start (e.g. *Nochmal brühen*) and confirm *Beenden & neu starten* → the first session is in the journal, „Sitzung gespeichert" is posted instead of the sheet, and the new session's step alerts on time.
- [ ] Refused background start (04 §6.3, best effort, not blocking): set *Akku* to *Optimiert*, start a 12-min quick timer, switch to another app and lock the phone → the alert still comes on time and full screen. Afterwards *Diagnose* shows whether Android refused a foreground start („refused", then „refused again" or a later start); report what it shows, then set *Akku* back to *Uneingeschränkt*. If no start was refused, the paths rest on the Robolectric tests (`RefusedServiceStartTest`, `BrewTimerServiceTest`), because the emulator allows every start (04 §12).
- [ ] Live Update visible as status chip and on the lock screen; *Pause*/*Weiter*/*Abgegossen* work from it.
- [ ] (Best effort, not blocking) Live Update appears in the Now Bar.
- [ ] Reboot test: set a step to 15 min via the Schritte-Sheet, start it, reboot, unlock the phone before the deadline → the notice „Dein Timer läuft weiter" with the text „<Tee> · nach dem Neustart" appears, and the alert fires on time.
- [ ] Update during a session: install the next rc while a 3-min step runs → the Live Update comes back right after the install and the alert fires on time (04 §7.5).
- [ ] Accuracy: after a few sessions, *Einstellungen › Über › Diagnose* shows each step's „alert PRE_ALERT" and „alert ALERT" lines (the moment the sound started) within 1 s of the scheduled time (their `delta`); the „event … accepted" line above each is only the controller's handling (D-068); *Kopieren* works.
- [ ] Session appears in the minimal journal list with its planned first infusion and the poured count („2 von 3 Aufgüssen" when finished early); the „Gespeichert" sheet after *Sitzung beenden* shows the actual total (D-061).
- [ ] Light phone theme: status-bar icons and the gesture handle are visible (light) on the timer and the quick timer, also right after starting a quick timer; dark on the alert surface; dark again on the tabs after leaving the timer (D-069).
- [ ] Rotate the phone, or switch light/dark, while a step alerts and the tabs are on screen: the matcha brew bar is there at once, without growing or fading in (03 §3.8, D-069).

### M2 — Catalog

- [ ] `v0.2.0-rc.1` installs over `v0.1.0` (and each later rc over the one before) without losing data: the journal, Tanabata and Hisui with their photos, recipes and stock, and the settings are all still there; a brew still alerts on time (spec #62 story 46).
- [ ] *Katalog laden* completes (first run ≈ 15–16 min, D-076, D-091); about 250–280 teas (the shop's range changes; 255 in the live run of 2026-10-26); no duplicates per size; no sets or accessories.
- [ ] 10 spot-checked teas: name, type, origin, prices, photo correct; brewing table/text shown verbatim; derived recipe follows §2.5; flags plausible.
- [ ] A second refresh right after the first reports „… Grüntees · keine Änderungen" (or only the changes the shop made meanwhile) and is far shorter: no photo is downloaded again (CAT-07 AC3, CAT-08, D-086).
- [ ] Airplane mode during refresh: clear error, old catalog intact.
- [ ] Guides readable offline.
- [ ] *Im Shop öffnen* on a catalog tea opens its Tee-Kontor page in the browser (CAT-11).
- [ ] Refresh during a brew: start a 3-min step, then *Katalog aktualisieren* and lock the phone → the pre-alert and the alert come on time and the refresh completes (CAT-07 AC8, D-073).
- [ ] Locked phone: the notification „Katalog wird geladen" counts on while the phone is locked; *Abbrechen* in it stops the refresh with the old catalog intact; at the end the progress notification goes and a quiet result remains; *Einstellungen › Katalog* shows the new date (CAT-07 AC1, SET-23, D-079).
- [ ] *Aus dem Katalog* in the tea picker → add a tea (size, price, date) → the brew setup opens with that tea; after brewing a tea marked „Rezept prüfen", *Rezept passt* clears the mark (CAT-12, CAT-05).
- [ ] Seed teas after the first catalog load (CAT-15, as far as M2 shows it): Tanabata and Hisui keep their recipes, stock and journal entries; a seed tea that had no photo now shows the shop's photo in the tea picker. Their descriptions show only in M3's tea detail (D-090).
- [ ] *Nachkauf erfassen* on a seed tea: in the catalog, Tanabata (TG0213) offers *Nachkauf erfassen*, not *In Sammlung aufnehmen*; saving a 100 g package adds it to Tanabata's stock and no second Tanabata appears in the tea picker (CAT-12, D-085).

### v0.2.1 — Feedback on v0.2.0-rc.1 (D-093, D-094, D-095)

v0.2.1 changes timer code (per-step pre-alert, sounds), so every
`v0.2.1-rc.N` needs a passing accuracy run first (§4a, D-067, D-073).

- [ ] `v0.2.1-rc.1` installs over `v0.2.0` without losing data (umamit.db v1 → v2): the journal, the teas with their recipes, photos and stock, and the settings are all there; a session running during the update keeps its steps and pre-alert and still alerts on time.
- [ ] Timer of a tea with a bright, white-backed photo: ← and ⋮ are clearly visible at once and after the band fades; ⋮ opens the menu (SCR-05).
- [ ] Timer top (D-095): the ink-wash band with the tea's photo in the round seal, no white square around a white-backed photo; a tea without a photo shows its type leaf; the quick timer shows the band without a seal; nothing moves when the alert starts.
- [ ] Automatic pre-alert (*Vorwarnung* 10 s): Hisui's Kyusu recipe (1:00, 0:10, 0:10) shows „10 · – · –" in the setup; a step of 0:20 shows 5 s and one of 0:30 shows 10 s (N = min(B, 5 · ⌊d / 15⌋), TMR-23).
- [ ] Schritte-Sheet: set step 2's pre-alert to 10 s → „zu kurz" (0:10 < 10 + 5 s); set it to 5 s → it sounds 5 s before the end of step 2. Without *Im Rezept speichern* the next session of that recipe is back to „Auto"; with *Im Rezept speichern* the next session shows 5 s.
- [ ] *Alarm testen* with each of the four new pre-alert sounds and „Ting (leise)": each is much louder than the old tick and rings out for about 3 s; the PO names the default (SET-26).
- [ ] Pre-alert follows the notification volume: lowering it makes the pre-alert quieter; ringer on vibrate → a short vibration only; silent → nothing (04 §9.1).
- [ ] The vessel picker offers Kyusu 120, 180, 300, 360 ml and Becher 250, 300, 350 ml next to „Kyusu 250 ml" and „Becher mit Sieb 400 ml" (the M1 vessels, renamed while unchanged, D-093 Q18), each once; choosing one rescales the leaf (WARE-03, WARE-06).
- [ ] Brew setup: tap „… g übrig", enter the new total 23,5 g, *Speichern* → „23,5 g übrig"; after brewing 5 g it shows 18,5 g. Entering 0 shows the tea as leer (INV-03, INV-05).
- [ ] Tea picker: long press a tea → *Aus Sammlung entfernen* → it is gone from the picker and from Start's *Nochmal brühen*; *Rückgängig* brings it back. Remove it again: its journal entries still show its name and photo (TEA-04).
- [ ] During a brew, long press that session's tea in the picker: *Aus Sammlung entfernen* is greyed out with „Erst die Sitzung beenden" (E15).
- [ ] Add a removed tea again from the catalog: the same tea comes back (no second one), the new package added to its stock (TEA-04, D-085).
- [ ] *Katalog aktualisieren*, the first after the update: it re-classifies everything once (≈ 16 min, accepted by the PO) and completes; the catalog still opens on the green teas.
- [ ] The chip *Kräutertee* shows only Kräutertees, grouped by *Sorte*, with no fruit tea, rooibos or accessory among them; „Pur" does not apply there; tapping the chip again returns to the green teas; the header counts both (CAT-01, CAT-10).
- [ ] A Kräutertee without the shop's brewing values: 2 g / 250 ml, 100 °C, one infusion of 6:00, marked „Rezept prüfen"; one with shop values uses them, unmarked (08 §3).
- [ ] In the browser, open the shop page of a sold-out tea (e.g. TG0170) and share it to UmamiT: its detail opens with its size „nicht vorrätig" (D-096: only the page's own size knows its stock) and can be added with *In Sammlung aufnehmen* (CAT-17).
- [ ] Catalog ⋮ *Link einfügen*: a category link or a link to another site shows „Das ist kein Tee-Link von Tee-Kontor Kiel."; a link to a black tea shows „Dieser Tee gehört nicht zum Katalog (nur Grün- und Kräutertees)." (CAT-17).
- [ ] *Katalog aktualisieren* after that: the sold-out tea stays in the catalog („nicht vorrätig"; visible with the *Vorrätig* filter off, CAT-10) and is not counted as „nicht mehr im Sortiment" (CAT-16). An owned tea that v0.2.0 marked „Nicht mehr im Sortiment" only because it sold out is back in the range (D-096).
- [ ] Kräutertee view before the first refresh after the update: the hint „Kräutertees kommen mit dem nächsten Abruf (≈ 16 min, einmalig)" with *Jetzt laden*; its filter sheet has no Aroma or Umami (Q19, Q20). Mate and Lapacho appear among the Kräutertees, as the shop files them there (D-096).

### v0.2.2 — Feedback on v0.2.1 (D-097)

v0.2.2 changes timer code (the quick timer's start and pre-alert), so every
`v0.2.2-rc.N` needs a passing accuracy run first (§4a).

- [ ] `v0.2.2-rc.1` installs over `v0.2.1` without losing data (umamit.db v2 → v3): journal, teas, recipes, photos, stock and settings are all there; a brew session or a quick timer running during the update still alerts on time.
- [ ] Start › *Nochmal brühen* › a tea: READY shows „4,0 g Tee · 250 ml Wasser" (the tea's leaf and water) next to the kettle hint; so does READY before each later infusion and after *Brühen anpassen* (BRW-11).
- [ ] *Katalog aktualisieren*, then switch to another app: a tap on the progress notification opens *Sammlung › Katalog* with the sheet showing the progress; after it ends, a tap on the result notification opens it with the summary (SCR-22).
- [ ] *Aufguss starten*: below *Zuletzt* and *Favoriten* the teas are grouped under „Grüntees" and „Kräutertees", A–Z in each; a search is grouped too (SCR-03).
- [ ] *Einstellungen › Brühen & Vorrat*: „Knapp ab 15 g". Correct a tea's stock to 14 g: Start shows „… wird knapp · 14 g übrig" with *Nachbestellen* (opens the shop page of the size bought last) and *Ausblenden*; the brew setup reads „14 g übrig · knapp". *Ausblenden* hides the card; a recorded purchase brings it back when the stock is still low (INV-10).
- [ ] Schnelltimer 2:00 with *Vorwarnung* „Auto · 10 s": the ring shows the mark before *Start* and while it runs; the Vorwarnton plays at 0:10 left, once with the screen on and once with the screen off. Choose 20 s: it plays at 0:20; the next Schnelltimer opens with 20 s; *Aus* plays none (TMR-22).
- [ ] The Schnelltimer chips include 8:00; all chips are readable at the largest font, wrapping onto a second row.
- [ ] Catalog: mark two teas with 🔖 (one on its row, one on its detail); the chip *Gemerkt* shows both and only those; add one to the Sammlung → it leaves the Merkliste; the marks survive *Katalog aktualisieren* and an app restart (CAT-18).

### v0.3 — Quick timers alongside a brew (planned, D-093)

The checklist is written with v0.3.0's plan; at least: a quick timer started
during a running brew step, both alerting on time with the screen off, and
each acknowledged on its own.

### M3 — Collection

- [ ] Add catalog tea with 100 g package → stock 100 g, price set, recipes copied, photo copied.
- [ ] Brew → stock reduced by leaf; cost per cup shown.
- [ ] Choosing a vessel rescales leaf; recipe pre-alert override works.
- [ ] Catalog refresh does not change the collection tea; *Aus Katalog zurücksetzen* does.
- [ ] „Beim Tee-Kontor nachbestellen" on a collection tea opens the shop page of the size bought last (e.g. 100 g); it is a filled button once the stock is *Knapp* (INV-09, D-072).

### M4 — Journal & statistics

- [ ] Finishing shows the „Gespeichert" sheet; *Zeiten ins Rezept übernehmen* appears only when times differ.
- [ ] Notes, rating, taste, descriptors can be added later.
- [ ] Statistik tabs show plausible numbers for a week of real use.

### M5 — 1.0

- [ ] Device backup restore check: `adb shell bmgr backupnow com.umamit.app` then uninstall/reinstall restores teas, journal and settings (or a new-device transfer when available); no session is resumed after the restore.
- [ ] *Sicherung erstellen* writes a `.umamit` file that opens as a ZIP and contains `data.json` with the teas and sessions and the own photos.
- [ ] 200 % font, dark theme, TalkBack spot checks pass.

## 4a. Accuracy run before each release candidate

From M1 on, the timer's accuracy (TMR-30,
[04 §12](04-brew-timer.md#12-testing)) is measured on the CI emulator before
every `vX.Y.Z-rc.N` (PO decision in spec #40; D-067). Claude dispatches the
run and reads its result. The PO is involved only through the summary linked
in the rc's announcement. The rc is dispatched only after the run passes on
the commit it is built from. A later commit that touches the timer (`core:timer`,
`core:timer-engine`, the app's timer wiring) needs a new run.

From M2 on (D-073), the run is required only for an rc whose changes since the
last passing run touch timer code (`core:timer`, `core:timer-engine`, the
timer screens and the app's timer wiring), and always before the final
`vX.Y.Z`.

**What it does.** `.github/workflows/accuracy.yml` builds `core:timer`'s
instrumented test APK and boots the CI emulator (same setup and caches as the
CI emulator job). Then `scripts/accuracy-run.sh` runs `AccuracyRunTest`. The
test runs 50 steps of 60 s with a 10 s pre-alert through the real controller,
alarms, foreground service and notifications. The screen is off during every
step, and *Abgegossen* / *Start* / *Sitzung beenden* are tapped in the
notifications between steps. It takes about an hour per API level. The
regular CI emulator job skips this test.

**Dispatching.** Use the GitHub Actions API or tool (`workflow_dispatch` of
`accuracy.yml`), with the PR branch as the workflow's ref. The inputs are:

| Input | Default | Meaning |
|---|---|---|
| `ref` | the M1 branch | Branch, tag or SHA to test: the commit of the next rc |
| `api-level` | `36` | `36`, `35` or `both` (one job per level, in parallel) |
| `steps` | `50` | `50` for the rc gate; fewer only to try the workflow |
| `inject-delay-ms` | `0` | Gate check only: every pre-alert and alert this many ms late; such a run must **fail** |

With the `gh` CLI the same dispatch is
`gh workflow run accuracy.yml --ref <branch> -f ref=<branch-or-sha> -f api-level=36`.
GitHub dispatches only workflows whose file exists on `main`; the
version at `--ref` runs (D-039, D-070).

**Reading the result.** The run page's summary shows a table per API level:

| Event | Steps | p50 | p95 | max | Handled p95 / max | Fired by | On target |
|---|---:|---:|---:|---:|---:|---|---|
| Pre-alert | 50 / 50 | 9 ms | 25 ms | 48 ms | 12 ms / 31 ms | WAKE_UP 50 | yes |
| Alert | 50 / 50 | 30 ms | 70 ms | 120 ms | 14 ms / 40 ms | WAKE_UP 50 | yes |

*(Illustrative numbers.)*

- **Delay** is the moment the player started the sound of the pre-alert and
  the alert minus their scheduled time, as the diagnostics log records them
  (06 §5.6, D-060, D-068), on the monotonic clock; the gate uses it.
  *Handled* is the delay up to the controller's handling of the event, for
  comparison: the difference is the persist, the alarms and the player's
  queue. *Fired by* says whether the in-process wake-up or the exact alarm
  delivered the event.
- **Slowest pre-alert / alert** (below the table) names the step with the
  latest sound start of each event and splits its start: queued, settings,
  outputs, and whether the sound was loaded before (D-092). The job log
  shows the same split for every step.
- **Pass** („**Result: PASS**", job green): all 50 steps completed, and both
  events have p95 ≤ 250 ms and max ≤ 1 s. Anything else fails the job:
  a slow distribution, or a step that never alerted. In that case the test
  failure in the log names the step and shows the device state.
- **Details:** the artifact `accuracy-api-<level>` has `accuracy.csv` (one row
  per pre-alert and alert: step, event, source, scheduled wall and monotonic
  time, then the handled and the sound-start wall and monotonic time with
  their deltas), `diagnostics.txt` (the log lines it read), the
  instrumentation output and logcat.
- A failed run blocks the rc. Find the cause from the CSV and the log lines,
  fix it (rule 8), and run again. A flaky emulator start (no report at all)
  may simply be re-run once.

**Proving the gate.** Once per workflow change, Claude dispatches a run with
`inject-delay-ms: 1500` and a few `steps`. It must fail with the delays
around 1.5 s; the gate check changes nothing in the app.

On the phone, the same numbers come from *Einstellungen › Über › Diagnose*
(the M1 checklist above): the emulator neither dozes within a 60 s step nor
behaves like a Samsung phone.

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
