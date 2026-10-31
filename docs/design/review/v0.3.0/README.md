# v0.3.0 design review (with `v0.3.0-rc.1`, #138)

These are the Roborazzi goldens that v0.3.0 added or changed (`src/test/screenshots/` of each module), scaled to 600 px wide and palette-optimised, as for v0.2.2.
- They use synthetic sample teas and placeholder photos only, no Tee-Kontor content (D-016).
- Re-record the goldens with `./gradlew recordRoborazziDebug`.

Naming:
- `<Screen>_<state>_<light|dark>.png`.
- `_font2x` is the same render at font scale 2.0.
- The timer is always dark (Steeping), so its `light`/`dark` is the app theme around it.

| Folder | Content |
|---|---|
| `brew/` | See below. |
| `bar/` | The active brew bar with both timers (#136): brew and Schnelltimer side by side, an alerting half in matcha with its overtime, a held Schnelltimer reading „fertig". |
| `home/` | Start with the banner „Alarmlautstärke ist aus" (#128). |
| `settings/` | *Lautstärke* 10–100 % (#128), the alarm-volume row in the reliability card, the daily *Vorrats-Erinnerung* with its time and picker (#130). The other settings renders changed only because the page grew. |
| `catalog/` | See below. |

**`brew/`**
- **The other timer's row (#136):** `TimerScreen_Running_quickRow`, `TimerScreen_Paused_quickRow` (the brew with „Schnelltimer 2:14" and its own *Pause*), `TimerScreen_Quick_brewRow` (the Schnelltimer with the brew's row), the short screen 384 × 832 at font 2.0 (`TimerScreen_short_*_quickRow`).
- **A held Schnelltimer alert (#134):** `TimerScreen_Alerting_quickHeld`, the brew's alert with the Schnelltimer waiting.
- **Stock on READY (#132):** `TimerScreen_Ready_lowStock` („14 g übrig · knapp" under the amount line). Every brew render changed a little: the space for the stock line is reserved in every phase, so the ring and the pill never move.
- **SCR-07 polish (#133):** the Vorwarnung row keeps its place for every choice; the running Schnelltimer's controls stand where *Start* stood, so the ring and the pill stay put at *Start* (`TimerScreen_Quick_*`, `QuickTimer_*`). „Schnelltimer beenden und neu starten?" (`QuickTimer_question`, #134).
- **Long hot steps (#127):** `StepsSheet_hotStepsOfMinutes`, `BrewSetup_hotStepsOfMinutes` (a 15-min hot step with its Vorwarnung), `StepsSheet_coldBrew` (cold brew stays long).
- **Faster step duration (#141):** `StepsSheet_durationPicker`, `BrewSetup_durationPicker` („Dauer wählen" opened from a tap on the time).
- **Setup ⋮ (#129):** `BrewSetup_catalogMenu`, `BrewSetup_catalogMenuOpen` (*Im Katalog ansehen*).

**`catalog/`**
- **Foldable Sorte groups (#131):** `Catalog_listFoldedSome`, `Catalog_listFoldedAll`, `Catalog_listSearchFolded` (a search shows every hit unfolded). The headers read „BANCHA · 12" with a chevron; the other catalog renders changed only by the new headers.

Not rendered (behaviour, checked by tests and on the phone): the alarm volume and DND, the Live Update following the timer due next, the held alert ringing after *Abgegossen*, notification taps opening their own timer, the reminder notification and its tap.

Known points to judge:
- **Two *Pause* buttons on one screen** (the other timer's row and the pill): clear enough which timer each one pauses?
- **The brew row on the Schnelltimer at font 2.0** shortens the tea's name („Bio Uji Gyok…") and leaves a wide gap before „· 0:48".
- **The running Schnelltimer's controls sit about 100 dp lower** than in v0.2.2, where *Start* stood (#133).
- **At 384 × 832, font 2.0, a two-line tea name:** FINISHED's *Weiterer Aufguss* is reached by scrolling (#132), and the seal gives way to the other timer's row (#136).
- **The 24 h time picker for the reminder at font 2.0:** its dial numbers crowd (#130).
