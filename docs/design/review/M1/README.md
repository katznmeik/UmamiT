# M1 design review (M1-17, #53)

Rendered from the committed Roborazzi goldens (`src/test/screenshots/` of each module),
scaled to 600 px wide and palette-optimised. Placeholder images only. Record-only: not
verified in CI. Regenerate goldens with `./gradlew recordRoborazziDebug`.

Naming: `<Screen>_<state>_<light|dark>.png` (timer and quick timer: `appLight`/`appDark`).
The suffix `_font2x` is the same render at font scale 2.0, which doubles as the
worst-case set. Each state exists in light and dark.

| Folder | Content |
|---|---|
| `home/` | SCR-01 Start: seeded, empty collection, active session, question state |
| `onboarding/` | SCR-02 welcome and reliability page |
| `brew/` | SCR-03 tea picker (incl. empty), SCR-04 setup (kyusu, cold, changed, question), SCR-05 timer in all states (ready, running, pre-alert, paused, alerting incl. rinse, overtime, wash phases, finished, cold and ice; with the fallback photo A4 in the photo slot, which a brew without a readable cover photo also shows on the phone, D-069), SCR-07 quick timer (chips, custom, picker; no photo), Schritte-Sheet (without recipe, with done steps, 30 steps) |
| `journal/` | SCR-06 journal: with sessions (long German name, photo and placeholder), empty, saved sheet |
| `collection/` | SCR-08 / SCR-19 *Sammlung* placeholders until M2/M3: *Meine Tees* (where the teas are for now) and *Katalog* (D-069) |
| `stats/` | SCR-18 *Statistik* placeholder until M4 (D-069) |
| `settings/` | SCR-15 settings (all ready, missing check), SCR-25 about, SCR-27 diagnostics (empty, with entries) |
| `shell/` | navigation bar and rail, alerting state |
| `components/` | active brew bar, reliability banner, timer ring states, alert surface, empty state, tea list item (overlong name), others |
| `motion/` | GIFs re-recorded with `scripts/motion/record-gifs.sh` |

Long-name worst cases (`TeaPicker_longName`, `BrewSetup_longName`, `Home_longNames`, 'Bio Gyokuro Asahi Premium aus Yame, Frühjahrsernte'): no overlap or clipping; names ellipsize by design (picker and Start list: 2 lines, `TeaListItem.kt:75`; the Nochmal-brühen tile at font 2.0 shows only "Asahi P…" after 3 lines, `HomeScreen.kt:286`); the setup title wraps fully.

Worst cases covered: font scale 2.0 everywhere, overlong German names (journal, tea list
item), no photo slot (quick timer; a brew without a photo shows the placeholder), 30 steps,
empty collection, empty journal.

Not visible in renders: the status-bar icons and the gesture handle (Roborazzi draws no
system bars). They are light on the timer and the quick timer and dark on the alert surface;
covered by the app's `SystemBarIconsTest` and the 09 §4 checklist (D-069).
