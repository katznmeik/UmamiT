# v0.8.0 design review (`v0.8.0-rc.1`, #326)

These renders are Roborazzi goldens, scaled to 600 px wide and palette-optimised without colour shift:
- `taste/` comes from `core:ui`, `feature:collection` and `feature:journal`;
- `settings/` from `feature:settings`;
- `stats/` from `feature:stats`;
- `timer/` from `feature:brew`;
- `wording/` from `feature:home` and `feature:brew`.

`light-timer/` holds the design-check renders of #323 and #325, which you checked on #324. All renders use invented sample teas and the app's own placeholders only, with no Tee-Kontor content (D-016). Re-record the goldens with `./gradlew recordRoborazziDebug`.

Naming:
- `<Screen>_<state>_<light|dark>.png`;
- on the timer, `_appLight` / `_appDark` is the app's *Design*, which the timer now follows (SET-05).

Decisions: [D-128](../../../specs/10-open-questions-and-decisions.md#d-128) Q1–Q31 and build notes 34–41.

## Light timer (SET-05, D-128 Q20–Q23, items 34–40)

| Part | Renders | What to look at |
|---|---|---|
| **States** | `timer/TimerScreen_{Ready,Running,Paused,PreAlert,Finished}_appLight` beside `_appDark` | Washi surface, A13 in ink on washi, ink digits. The dark timer is unchanged from v0.7.2 apart from the wording. |
| **Alarm** | `timer/TimerScreen_Alerting_matchaPhase_appLight` (full sage; the file name is from the dark test) and `_washiPhase_appLight` | Washi ↔ sage `#AEBF8D` with ink content, ink pill and circles (items 36, 38). |
| **Overtime** | `timer/TimerScreen_Alerting_overtime_washiPhase_appLight` | The warm dot now has a 1 dp ink ring, on the light timer only (item 40). |
| **Taste sheet** | `timer/TimerScreen_TasteSheet_unfolded_appLight`, `_herbal_appLight` | The sheet in the app's theme, with „Was bedeuten die Regler?" unfolded. |
| **Quick timer** | `timer/TimerScreen_Quick_running_appLight`, `QuickTimer_chips_appLight` | Light, with the chips' full outline (item 37). |
| **Bar alarm** | `light-timer/light-app-bar-alert.png` | The active-brew bar outside the timer is static sage in the light app (item 38). |

## Taste sets and the slider info (JRN-03, TEA-02, D-128)

| Part | Renders | What to look at |
|---|---|---|
| **Info** | `taste/TasteInfo_folded_*`, `_unfoldedGreen_*`, `_unfoldedHerbal_*`, `_unfoldedOxidised_*` | The how-to and „0 = … · 5 = …" per slider of each set (08 §4a). |
| **Session page** | `taste/SessionPage_tasteUnfoldedHerbal_*`, `_tasteUnfoldedOxidised_*`, `_tasteInfoUnfoldedHerbal_*`, `_tasteFoldedRadarHerbal_*` | Each set's six sliders and radar axes. |
| **Tea detail** | `taste/TeaDetail_tasteHerbal_*`, `_tasteOxidised_*` | GESCHMACK radar with the set's axes. |
| **Tea editor** | `taste/TeaEditor_tasteSetAutomatic_*`, `_tasteSetChosen_*` | *Geschmacksprofil*: „Automatisch" or a chosen set. |

## Other

- **Settings › Katalog (SET-23):** `settings/Settings_catalogRefreshing_*`, `Settings_catalogResult_*`. The bar and counts under *Jetzt aktualisieren*, then the result line.
- **Statistik ⋮ (SCR-18):** `stats/StatsOverflowMenu_*`. The menu opens below the three dots at the right edge.
- **Wording (GEN-04):** `wording/Home_seeded_*` („Zuletzt aufgegossen", *Aufguss starten*) and `BrewSetup_changed_*` („Neue Session", *Session starten*).

## Claude's details (not put to you; change any of them)

1. The sheets on the light timer use the light scheme's `surfaceContainerLow`, as in the renders you approved (03 §3.1 now says so).
2. With the timer in the app's theme, setup → timer no longer flips the status-bar icons; only an alert flips them, at once.
3. If *Hell* is stored but not yet handed to the system (right after an install or update), a cold alert start on a dark phone may draw its first frames dark until the settings are read. Closing this would need a blocking read before the alert, which rule 8 forbids.
4. The refresh result in Settings is cleared once you leave the settings; a rotation keeps it.

## Motion

The ad-hoc motion review (#326; `/review-animations` can only be started by you) found one stale frame, which is fixed: the refresh status no longer collapses empty. The fold of „Was bedeuten die Regler?" uses the fold tokens of 03 §3.6. Check the transitions on the phone (09 §4 „v0.8.0").
