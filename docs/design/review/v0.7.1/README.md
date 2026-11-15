# v0.7.1 design review (`v0.7.1-rc.1`, #288)

These renders are Roborazzi goldens scaled to 600 px wide and palette-optimised: `ratings/` from `feature:journal` and `feature:collection`, `stats/` from `feature:stats`.
- They use invented sample teas and the app's own placeholders only, with no Tee-Kontor content (D-016).
- Re-record the goldens with `./gradlew recordRoborazziDebug`.

Naming: `<Screen>_<state>_<light|dark>.png`; `_font2x` is the same render at font scale 2.0.

Decisions: [D-117](../../../specs/10-open-questions-and-decisions.md#d-117) and its build notes.

## Two ratings (JRN-02, D-117 Q1–Q12)

| Part | Renders | What to look at |
|---|---|---|
| **Gespeichert sheet** (SCR-06) | `ratings/Journal_savedSheet`, `Journal_savedSheetAllLines` | Two labelled star rows „Tee" above „Zubereitung". At font 2.0 both labels sit above their stars (they decide together, so the stars always line up). |
| **Session page** (SCR-16) | `ratings/SessionPage_ratedWithLongNote`, `SessionPage_available` | BEWERTUNG with both rows; a half star in the Tee row. |
| **Journal rows** (SCR-08) | `ratings/Journal_withSessions` | „Tee ★ 4,5 · Zubereitung ★ 3,5"; only the set one when one is missing; wraps after „·" at font 2.0. |
| **Tea detail** (SCR-09) | `ratings/TeaDetail_rating`, `TeaDetail_recent` | „★ 4,5 (12)" with „Zubereitung Ø 3,8 (10)" below in the small body font; LETZTE SITZUNGEN rows with the two ratings. |
| **Statistik › Bewertungen** (SCR-18, INS-02) | `stats/Ratings_data`, `Ratings_data_brewing`, `Ratings_only_brewing` | The *Tee · Zubereitung* switch above the Tee-Rangliste; BEWERTUNG IM VERLAUF with two lines (Tee solid, Zubereitung dashed) and a legend; BESTES REZEPT „nach Zubereitung". |
| **Chart** | `stats/Chart_line` | The two-line chart on its own, with gaps where a week has no rating of that kind. |

### Claude's details (not put to you; change any of them)

1. **Font 2.0, the two rows:** when „Zubereitung" and five stars don't fit side by side, *both* labels go above their stars, so the stars stay in one column.
2. **LETZTE SITZUNGEN:** the two ratings sit on a second line under the meta line (in the trailing slot they were squeezed to a few characters).
3. **Verlauf:** Zubereitung is the dashed line in the third accent colour; TalkBack reads „KW 44: Tee 4,2, Zubereitung 3,8".
4. **Fold summary of TEE-RANGLISTE:** the top tea's name of the chosen kind.
5. **After the update:** every old rating was copied to both kinds, so at first both Verlauf lines lie on top of each other until you rate differently.
6. **TalkBack of the switch:** „Rangliste nach Tee" / „Rangliste nach Zubereitung".

## Transitions (NFR-PERF-12, D-117 Q13–Q19)

Motion can't be rendered as stills; check it on the phone (09 §4 „v0.7.1"). What changed:
- **Unlock and resume:** the second counters (timer, Start's card, the bar) read the clock before the first frame after unlock; the bar no longer replays its old value.
- **Cold start:** the window and splash are washi (dark: the dark background) instead of the phone's colour; Start's first frame waits up to ≈ 150 ms for the stock card, then arrives in one piece (never while the timer opens or an alert plays).
- **Design (A-08, your answer):** a theme change recolours the app in place, without restarting it.
- **Status-bar icons (A-16, your answer):** on setup → timer and back they flip at the fade's midpoint; an alert flips them at once.
- **Journal:** coming back shows the current list without the earlier one flashing; a list that changed late cross-fades once.
- **Pushed screens:** content fades in once instead of popping in after an empty frame; the tea detail keeps its back arrow throughout.
- **Sheets closed by their own button** (stock, Aufnehmen, refresh, link) slide out instead of vanishing.
- **Timer exit:** no empty light frame before the shell; a timer restored after process death draws the dark surface at once.
- **Teeauswahl** composes its list one frame after the sheet appears; the timer band is decoded once, off the main thread.

### Claude's details (not put to you; change any of them)

7. Under *Bewegung reduzieren*, the icon flip waits half of the shortened fade.
8. The Journal's cached list lives as long as the app process (it survives a theme change and the activity's re-creation).

## Measured on the CI emulator

`TransitionJankTest` numbers per run: `docs/plans/v0.7.1-motion-audit.md` §7. The gated transitions pass on API 36; the tab switches, Teeauswahl in, Schritte-Sheet in, tea detail push, cold start, setup → timer and session page push are reported only and are yours to judge on the phone (D-117 build note „Gate scope").
