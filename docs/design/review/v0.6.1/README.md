# v0.6.1 design review (with `v0.6.1-rc.1`, #250)

These are Roborazzi goldens of the screens v0.6.1 added or changed (`src/test/screenshots/` of `feature:journal`, `feature:collection`, `feature:brew`, `feature:catalog`, `feature:settings` and `core:ui`), scaled to 600 px wide and palette-optimised, as for v0.6.0.
- They use synthetic sample teas and placeholder photos only, no Tee-Kontor content (D-016).
- Re-record the goldens with `./gradlew recordRoborazziDebug`.

Naming:
- `<Screen>_<state>_<light|dark>.png`.
- `_font2x` is the same render at font scale 2.0.
- `_short_…_font2x` is a short screen (384 × 832 dp) at font scale 2.0.
- On the timer, `light`/`dark` is the app's theme. The timer surface itself is always Steeping.

## The screens of D-114

| Part | Folder | Renders |
|---|---|---|
| **Session page: GESCHMACK per infusion** (#243; SCR-16, JRN-03, JRN-05) | `journal/` | `SessionPage_tasteNoValues`: the row „Geschmack hinzufügen". `SessionPage_tasteFoldedRadar`: folded on „1. Aufguss" with three or more axes (radar, note in one line, *Anpassen* ⌄). `SessionPage_tasteFoldedSummary`: folded below three axes, the line „Umami 4 · Süße 3". `SessionPage_tasteUnfolded`: radar, six sliders, „Notiz zum Aufguss …", *Zurücksetzen*. `SessionPage_tasteAll`: *Alle*, the averaged radar, no *Anpassen*. The skipped 2nd infusion has no chip. |
| **⋮ links** (#245; JRN-10, TEA-13) | `journal/`, `collection/` | `SessionPage_menuCatalogOpen`: *Tee ansehen*, *Im Katalog ansehen*, divider, *Bearbeiten*, *Löschen*. `SessionPage_menuDeletedOpen`: a deleted tea, only *Bearbeiten* and *Löschen*. `TeaDetail_catalogMenu`: *Im Katalog ansehen* first. |
| **Timer: taste button and sheet** (#244, #250; SCR-05) | `brew/` | `TimerScreen_Ready_tasteSpace`: the session's first READY, with the button's space already there and empty (the PO's answer on MIN-1). `TimerScreen_Ready_taste`, `_Running_taste`: „Geschmack · 1. Aufguss" below the step dots. `TimerScreen_Ready_tasteDone`: „… ✓". `TimerScreen_TasteSheet`: the sheet, *Fertig* pinned, six sliders, the note, no radar. `TimerScreen_short_*`: the band rule at font 2.0 on a short screen; the button goes below the controls and *Start* stays on screen. `TimerScreen_short_Alerting_taste`: the button hidden, its space kept. |
| **Quick-timer chips** (#241; TMR-22, SCR-07) | `brew/` | `QuickTimer_chips`: 1:00 · 2:00 · 3:00 · 5:00 · 8:00 · 10:00 · 15:00. `QuickTimer_short_chips`, `_chips10`: font 2.0 on a short screen, where the chips wrap to three rows with 15:00 alone. |
| **Photo viewer** (#246; TEA-05, SCR-28) | `components/` | `PhotoViewer_portrait`, `PhotoViewer_landscape`: the whole photo fitted on black, „2 / 5" and ← on the translucent circle (placeholder images). |
| **Katalog without a kind** (#247, #248; CAT-10, CAT-07, SCR-19) | `catalog/` | `Catalog_listAllKinds`: no chip selected, every tea grouped by kind („Grüntee · 9" with a chevron), header „N Tees". `Catalog_listAllKindsFolded`: kind groups folded. `Catalog_list`, `Catalog_listBlack`: a kind's view, with the header now „N Tees". `Catalog_chipRow`: „Filter" without a count for the kind. `Guide_list`: the intro „Tee-Wissen von Tee-Kontor Kiel – auch ohne Verbindung lesbar." |
| **Darstellung › Design** (#249; SET-05, SET-30, SCR-25) | `settings/` | `Settings_allReady`: *Darstellung* first, below the *Timer-Zuverlässigkeit* card, *Design* „System". `Settings_theme`: the dialog *System · Hell · Dunkel* with *Dunkel* picked. |

Not rendered (behaviour, checked by tests and on the phone, 09 §4 „v0.6.1"):
- the GESCHMACK fold and the chip cross-fade;
- the note saving itself;
- the timer sheet closing at the alarm, also with the keyboard up;
- the button's space from the first READY on the phone;
- the viewer's pinch and double-tap zoom and swipe, and the hero following the last photo shown;
- the theme switching at once, then the app re-created once after the *Design* dialog closes;
- the starting window in the chosen theme;
- the migration of v0.6.0 taste to „1. Aufguss";
- TalkBack's readings.

## To judge (Claude's defaults from the build, D-114 build notes)

1. The timer button's icon: `UmamiIcons.Tune` (no taste icon exists) and its place below the step dots. At font 2.0 on a short screen it goes below the controls.
2. The chip labels: „1. Aufguss", then „2." „3." …, and „Aufschlagen" for a whisk step. A note alone gives a chip its dot.
3. GESCHMACK unfolded keeps *Anpassen* where it was tapped, and the folded summary and note leave (your answer on M6: keep).
4. A finished session with no poured infusion (rare: a v0.6.0 session whose infusions were all skipped) shows no GESCHMACK at all. Its migrated values count only in the tea's radar (T3 review m6).
5. The viewer: double-tap zooms to 2.5× (pinch to 4×). „1 / 1" shows for a single photo. The ← circle is the light `OverPhoto` surface.
6. The quick-timer chips wrap to three rows at font 2.0 on a short screen (#241).
7. The kind group header „Grüntee · 9" folds like the *Sorte* headers. With name or price sort the kind groups stay, and the rows inside are flat.
