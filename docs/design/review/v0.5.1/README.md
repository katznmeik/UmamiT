# v0.5.1 design review (with `v0.5.1-rc.1`, #217)

These are Roborazzi goldens of the screens v0.5.1 changed (`src/test/screenshots/` of each module), scaled to 600 px wide and palette-optimised, as for v0.5.0.
- They use synthetic sample teas and placeholder photos only (flat tints, the app's own illustrations), no Tee-Kontor content (D-016).
- Re-record the goldens with `./gradlew recordRoborazziDebug`.

Naming:
- `<Screen>_<state>_<light|dark>.png`.
- `_font2x` is the same render at font scale 2.0.
- The timer screens (`brew/TimerScreen_*`, `brew/QuickTimer_chips`) look the same in both themes, so they have one render without a theme suffix.

## The four points of D-110

| Point | Folder | Renders |
|---|---|---|
| **Start: one list** (#213, SCR-02, BRW-11) | `home/` | `Home_seeded`: one section *Zuletzt gebrüht* with six teas, each with its last brew, no tiles; `Home_longNames`: long tea names in the rows; `Home_empty`: before the first brew; `Home_activeSession`: the list below a running brew; `Home_lowStock`: the list with the stock card. |
| | `designsystem/` | `TeaListItem`: the row itself. |
| **Tea detail *Knapp ab*** (#214, SCR-09) | `collection/` | `TeaDetail_stockWarningOn`: no value of its own, the global „15" shown in the quieter colour; `TeaDetail_stockWarningOff`: the tea's own „20" in the normal colour. |
| **Filter sheet *Fertig*** (#215, SCR-19) | `catalog/` | `Catalog_filterSheet`: *Fertig* beside the title, *Zurücksetzen* at the end of the list; at font 2.0 the title and *Fertig* still share one line; `Catalog_filterSheetTaste`: scrolled to *Weitere*, the title row pinned with the hairline below it; `Catalog_filterSheetHerbal`: the shorter Kräutertee sheet (font 1.0 only). |
| **Font: Alegreya Sans** (#216, 03 §3.2) | `designsystem/` | `TypeScale`: the display roles (Light, Regular, Medium) with umlauts, ß, macrons and digits, above the Roboto body text; `PrimaryActionCard`, `TimerRing_Running`, `ActiveBrewBar_twoBrews`. |
| | `brew/` | `TimerScreen_Ready`, `_Running`, `_Alerting_washiPhase`, `_Finished`: the tea name and the timer digits; `QuickTimer_chips`; `BrewSetup_kyusu`. |
| | `collection/`, `catalog/`, `journal/` | `TeaDetail_ready`, `Collection_myTeas`, `Catalog_list`, `Journal_withSessions`, `SessionPage_available`: titles and tea names in lists and on detail pages. |

Not rendered (behaviour, checked by tests, CI's emulator job and on the phone, 09 §4 „v0.5.1"): a tap on a Start row opening the timer in READY; *Knapp ab* typed over the shown global value and saved on *Fertig*; *Fertig* sliding the sheet out; TalkBack's „Nochmal brühen" and „Vorgabe"; the running timer's digits not shifting sideways (tabular figures).

All 708 goldens were re-recorded for the font (#216); only a selection is repeated here. Every other screen changed in the same way: display text in Alegreya Sans, body text unchanged.

Points to judge:
- **The font on the phone:** the renders show shapes and spacing, not how the Light weight reads on the Galaxy's display, in the dark theme or at the phone's largest font size. That is the checklist's second point.
- **Start's rows** show the placeholder leaf where a tea has no photo; with real photos the list is livelier than here.
- **Filter sheet at font 2.0:** the title and *Fertig* fit on one line with little room to spare.
