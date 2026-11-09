# v0.5.0 design review (with `v0.5.0-rc.1`, #207)

These are the Roborazzi goldens that v0.5.0 added or changed visibly (`src/test/screenshots/` of each module), scaled to 600 px wide and palette-optimised, as for v0.4.1.
- They use synthetic sample teas and placeholder photos only (flat tints), no Tee-Kontor content (D-016).
- Re-record the goldens with `./gradlew recordRoborazziDebug`.

Naming:
- `<Screen>_<state>_<light|dark>.png`.
- `_font2x` is the same render at font scale 2.0.

| Folder | Content |
|---|---|
| `designsystem/` | `ActiveBrewBar_twoBrews`: the bar with two brews, one half per timer (#201). |
| `brew/` | `TimerScreen_Running_brewRow`: a brew's screen with the other brew in the slim row; `TimerScreen_Alerting_brewHeld`: one brew ringing while the other waits („wartet"); `BrewSetup_question`: „Welchen Timer beenden?" from the setup (#201, D-109). |
| `home/` | `Home_question`: „Welchen Timer beenden?" from Start; `Home_activeSession`: the running brew's card with *Aufguss starten* below while one timer runs (#198 follow-up). |
| `catalog/` | `Catalog_chipRow`: the nine category chips (#206); `Catalog_list`, `Catalog_listBlack`, `Catalog_listDark`: the green, black and Pu-Erh views grouped by *Sorte* (#203, #205); `Catalog_listBlackPending`, `Catalog_listHerbalPending`: the hint until the first refresh re-reads the shop; `Catalog_filterSheetTaste`: *Geschmack* chips and the scale rows (#204); `Catalog_empty`, `Catalog_linkNotInCatalog`: texts for the whole range. |
| `collection/` | `TeaDetail_stockWarningOn` / `_Off`: VORRAT with *Warnung* and *Knapp ab* (#197); `TeaDetail_vorrat`: the reorder link at its fixed height (motion review S1). |
| `onboarding/`, `settings/` | `Onboarding_catalog`, `Settings_catalog`: the catalog texts for the whole range („etwa eine halbe Stunde", „etwa 80–100 MB"); `Settings_reminder`, `Settings_reminderInterval`: the reminder interval (#198). |

Not rendered (behaviour, checked by tests, CI's emulator job, the accuracy runs and on the phone, 09 §4 v0.5.0): two alarms in sequence (D-109), the Live Update for the timer due next, recovery after a process death or reboot, the first refresh's re-read, the chip row scrolling to the selected chip, *Knapp ab* saved on *Fertig*.

Other goldens re-recorded in v0.5.0 only shifted (the chip row's width, VORRAT's height, longer texts); they are not repeated here.

Known points to judge:
- **Nine chips** need a horizontal scroll; the selected one is scrolled into view.
- **Filter sheet:** at 100 % font the six scale chips per row wrap to a second line, so the sheet is long with about seven scales (#204 report).
- **Placeholder photos:** flat tints; the real catalog photos come with the refresh.
