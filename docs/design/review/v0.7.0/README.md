# v0.7.0 design review (`v0.7.0-rc.3`, #276)

These renders are Roborazzi goldens scaled to 600 px wide and palette-optimised. The stats and chart goldens come from `feature:stats/src/test/screenshots/`, the others from `feature:collection` and `feature:settings`.
- They use invented sample teas and the app's own placeholders only, with no Tee-Kontor content (D-016).
- Re-record the goldens with `./gradlew recordRoborazziDebug`.
- The tab renders use a tall screen so a whole tab fits in one picture. On the phone the tab scrolls under the pinned range selector and tabs.

Naming:
- `<Screen>_<state>_<light|dark>.png`.
- `_font2x` is the same render at font scale 2.0, and `_font1.15x` at the selector's cap of 1.15 on a 360 dp screen.

rc.3 carries your answer on rc.2 ([D-115](../../../specs/10-open-questions-and-decisions.md#d-115) Amendment 2, V1–V7): **volume instead of cups**. Everything else in rc.2 and the rc.2 defaults 2–8 are approved; the rc.2 section below stays for reference.

## What changed since rc.2

| Part | Renders | What to look at |
|---|---|---|
| **Überblick** (V3, V4) | `stats/Overview_days90`, `Overview_gesamt`, `Overview_empty` | The row reads „42 Sitzungen · 12,4 Liter Tee · 24 Tees"; below 1 l „750 ml Tee". |
| **Verbrauch** (V2–V5) | `stats/Consumption_data`, `Consumption_no_purchases`, `Consumption_none_running_out`, `Consumption_empty` | The chart **TEE** (was TASSEN) in ml/l, the axis „0 ml · 1,0 l · 2,0 l", and GETRUNKEN „Ø 2,70 € pro Liter". |
| **Tea detail** (V2, V7) | `other/TeaDetail_cost` | „ca. … € pro Liter (Rezept)" and „Ø … € pro Liter aus 5 Sitzungen". |
| **Einstellungen** (V1) | `other/Settings_teaware` | *Brühen & Vorrat* without *Tassengröße*. |

The `StatsShell_*` renders were re-recorded too; they change only where a folded line or the tab behind the sheet shows a volume.

### Claude's defaults in rc.3 (not put to you; change any of them)

1. **„l" in the display font:** at the big numeral the lowercase „l" of „12,4 l" is drawn in Alegreya Sans and looks close to a bar (see `Overview_days90_light_font2x`). **Your answers (V8, V9):** the unit moves to the smaller body font and reads „Liter" in full („12,4 Liter Tee"; below 1 l „750 ml").
2. **TalkBack** reads the visible text („12,4 l Tee"); no extra „Liter" description.
3. **„aus N Sitzungen mit Preis"** stays under GETRUNKEN's total, as in rc.2, and counts the sessions in the € pro Liter average (V5). A priced session without water still counts in the total but not in N.
4. **The TEE chart** has no big numeral of its own: the range's volume is on *Überblick* (as TASSEN in rc.2). Its fold line reads „TEE · 12,4 l".

## rc.2 (for reference)

rc.2 carries your answers on rc.1 ([D-115](../../../specs/10-open-questions-and-decisions.md#d-115) Amendment 1, R1–R16). The rc.1 review (defaults 1–8, questions A–E) is in this file's git history.

### What changed since rc.1

| Part | Renders | What to look at |
|---|---|---|
| **Range „Monate"** (R7) | `stats/StatsShell_months_sheet`, `StatsShell_months_years`, `StatsSelector_plain` / `_year` / `_years` | The fifth segment, the sheet „Monate wählen" with *Von* / *Bis*, and the segment reading „Dez.–Aug." or „Dez. 25–Aug. 26" (on two lines). The selectors are drawn at 360 dp and font 1.15. |
| **Folding** (R8) | `stats/StatsShell_folded` | A folded section is one line with its number („SERIE · 7 TAGE", „AM HÄUFIGSTEN · HISUI GYOKURO"), with a › instead of the ⌄. |
| **Überblick** (R6, R10, R12, R13) | `stats/Overview_days90`, `Overview_gesamt`, `Overview_empty` | Sitzungen · Tassen · Tees in one row, the new **TEES** donut after TEE-SORTEN, half-height bars, „längste 21 Tage" in the Serie line, and no „+n" under Gesamt. |
| **Bewertungen** (R4, R5, R11) | `stats/Ratings_data`, `Ratings_unrated`, `Ratings_unrated_plain`, `Ratings_empty` | Tee-Rangliste („4,6 ★ · 5×", 10 rows then „Alle anzeigen (12)"), Neu entdeckt, Bewertung im Verlauf and Bestes Rezept. Ø, distribution, „Am besten bewertet", Geschmack and Beschreibungen are gone. |
| **Verbrauch** (R9, R10) | `stats/Consumption_data`, `Consumption_no_purchases`, `Consumption_none_running_out`, `Consumption_empty` | GETRUNKEN below AUSGABEN („23,40 €", „aus 20 Sitzungen mit Preis", „Ø 0,27 € pro Tasse"), and the half-height bars. |
| **Charts** | `charts/` | The bars and habit rows at half height. The donut now also draws the Tees donut. |
| **Meine Tees** (R14) | `other/Collection_myTeas` | „8 g · knapp" in one chip in the Knapp colours. |

Not rendered; check these on the phone (09 §4 „v0.7.0", rc.2 items):
- *Tassengröße* in *Einstellungen › Brühen & Vorrat* and its dialog.
- The fold and unfold motion and the fading „ · 42".
- The value label's fade (R15).
- Remembering folded sections and the months.

### Claude's defaults in rc.2 (not put to you; change any of them)

1. **Cups:** water is stored once per session, so every poured infusion of a session counts the session's water ÷ the cup size. A recipe's estimate is at least 1 cup, as a session is.
2. **„Monate" first tap:** it applies the last three months at once, so the segment is live while the sheet is open. The pickers are ‹ › month steppers.
3. **Selector at font 1.15:** the segments have no check icon and uneven widths so that five fit on 360 dp. The tab labels never wrap.
4. **Fold summaries:** Serie shows the current streak, with no number at 0. Am häufigsten shows the top tea. Gewohnheiten shows the busiest day part. Bewertung im Verlauf shows the latest period's Ø.
5. **Bewertungen without any rating:** „In diesem Zeitraum nichts bewertet" replaces the Rangliste, and Bewertung im Verlauf hides. „18 von 25 Sitzungen bewertet" went away with the Ø headline.
6. **„Alle anzeigen (N)"** opens the list in place and has no „weniger".
7. **Tee-Rangliste** sorts by the Ø as shown (one decimal), so equal-looking teas are ordered by more ratings, then by name.
8. **Bar charts** for *Monate* scroll sideways like *Gesamt* when they have many bars. The rating line keeps its full height.
