# v0.7.0 design review (with `v0.7.0-rc.1`, #263)

These are Roborazzi goldens of the new Statistik screen (SCR-18, `feature:stats/src/test/screenshots/`), scaled to 600 px wide and palette-optimised, as for v0.6.1.
- They use invented sample teas and the app's own placeholders only, no Tee-Kontor content (D-016).
- Re-record the goldens with `./gradlew recordRoborazziDebug`.
- The tab renders are drawn on a tall screen so that a whole tab fits in one picture; on the phone the tab scrolls under the pinned range selector and tabs.

Naming:
- `<Screen>_<state>_<light|dark>.png`.
- `_font2x` is the same render at font scale 2.0.

## The screens of D-115

| Part | Folder | Renders |
|---|---|---|
| **Empty state** (INS-08, A9) | `stats/` | `Stats_empty`: no finished session at all: A9, „Noch keine Statistik", no range selector, no tabs. |
| **Shell** (INS-07) | `stats/` | `Stats_shell`: „Deine Tee-Reise" with ⋮, the range selector 30 T · 90 T · 12 M · Gesamt (90 T the first time) and the tabs; here with an empty range („In diesem Zeitraum nichts"). |
| **Überblick** (INS-01, INS-03, INS-06) | `stats/` | `Overview_days90`: 42 Sitzungen, 96 Tassen, „24 Tees · +3 im Zeitraum", SITZUNGEN per week, SERIE, AM HÄUFIGSTEN (the deleted tea without a chevron), TEE-SORTEN, GEWOHNHEITEN; `Overview_gesamt`: Gesamt, monthly bars scrolled to the current month; `Overview_empty`: sessions exist but none in the range. |
| **Bewertungen** (INS-02, INS-04) | `stats/` | `Ratings_data`: „Ø 4,2 ★", „18 von 25 Sitzungen bewertet", the 5 ★ → 1 ★ bars, the rating line with gaps, AM BESTEN BEWERTET, BESTES REZEPT, GESCHMACK „aus 34 Aufgüssen", BESCHREIBUNGEN „Nori · 12×"; `Ratings_unrated`: sessions without a rating; `Ratings_empty`: nothing in the range. |
| **Verbrauch** (INS-05) | `stats/` | `Consumption_data`: GRAMM „38 g", TASSEN, AUSGABEN „64,80 €", „Ø 0,27 € pro Tasse · aus 20 Sitzungen", GEHT ZUR NEIGE (Leer first); `Consumption_no_purchases`: „Keine Käufe mit Preis", the cost line hidden; `Consumption_none_running_out`: GEHT ZUR NEIGE hidden; `Consumption_empty`. |
| **Charts** (INS-08) | `charts/` | `Chart_bars13`, `Chart_bars30`: weekly bars (the current period full, earlier ones at 70 %), thinned labels without overlap; `Chart_barsGesamt`: 40 months, opened at the end; `Chart_line`: 1–5 ★ with gaps; `Chart_donut6`, `Chart_donut1`: six slices with „Andere", one slice; `Chart_distribution`; `Chart_habitsDayPart`, `Chart_habitsWeekday`: as rows from font scale 1.5. |

Not rendered (behaviour, checked by tests and on the phone, 09 §4 „v0.7.0"): the value label on a tap; the grow when a tab first shows and the morph when the range changes; reduce motion; the header settling; TalkBack's lists; remembering range and tab.

## Claude's defaults (D-115 „Build notes"; not put to you, change any of them)

1. **12 M** = the current month and the 11 before, from the 1st (12 bars), not exactly 365 days.
2. **Gesamt** counts everything up to today; its bars start with the first session's month.
3. **Range change with a different number of bars** (e.g. 90 T → 12 M): the old bars fade out (0,1 s) and the new ones grow (0,25 s); with the same number they morph.
4. **Header** settles fully open or closed after a scroll.
5. **Large font:** the range selector and tabs stop growing at 1,15 × (else „Bewertungen" and „Gesamt" wrap); from 1,5 × the habit bars become rows.
6. **⋮ (Einstellungen)** sits in the title row and scrolls away with it.
7. **Value label:** one at a time; a tap elsewhere, a scroll or a tab change hides it.
8. The bars and the rating line are drawn by the app itself, not with the Vico library (Vico couldn't draw the 70 % bars or the line broken at gaps). Nothing visible changes.

## Questions for you (the rc.1 shows the first option of each)

- **A. „Am besten bewertet" when ratings exist but no tea has 3 of them:** keep „In diesem Zeitraum nichts" · hide the section · a hint „Ab 3 Bewertungen pro Tee".
- **B. Überblick under Gesamt** reads „24 Tees · +24 im Zeitraum": keep · hide the „+n" under Gesamt.
- **C. The first heading on Bewertungen** is „BEWERTUNGEN", like the tab: keep · „DURCHSCHNITT" · no heading.
- **D. Serie at 0:** „Keine Serie gerade" only · „Keine Serie gerade · längste 21 Tage".
- **E. Value label:** appears at once · a short fade (0,12 s in, 0,1 s out).
