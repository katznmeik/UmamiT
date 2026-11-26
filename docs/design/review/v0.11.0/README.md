# v0.11.0 design review (`v0.11.0-rc.1`, #370)

These renders are Roborazzi goldens, scaled to 600 px wide and palette-optimised, in two folders:
- `onboarding/` from `feature:onboarding` (SCR-33 tour, SCR-35 list, SCR-36 Anleitungen with their mock-ups);
- `menus/` from `feature:collection`, `feature:journal` and `feature:settings` (the ⋮ *Hilfe* items and the *Hilfe* › *Anleitungen* row).

They use the invented demo data of 03 SCR-36 (Morgentau, Nebelhang, Herbstfeuer, Kleine Kyusu) and the type leaf only, with no Tee-Kontor content (D-016). Re-record the goldens with `./gradlew recordRoborazziDebug`.

Naming: `<Screen>_<state>_<light|dark>.png`; `_font2_0` is font scale 2.0 (`_font1_0` is 1.0); `En` in the name means the English UI, without it the German UI.

Decision: [D-137](../../../specs/10-open-questions-and-decisions.md#d-137) (Q1–Q16, items 17–30, the build notes, the PO's answers and the review findings of the rc session).

## Einführung (SCR-33; ONB-07; D-137 Q3–Q12)

| Part | Renders (`onboarding/`) | What to look at |
|---|---|---|
| **German tour** | `Tour_page{1..6}_{light,dark}`, `Tour_page1_{light,dark}_font2_0` | Each page: the illustration band, a mock-up of the area's screen with the spot marked by a matcha ring, the title, four to six lines and the „Mehr dazu: …" links (full Anleitung titles). Dots at the bottom, *Weiter* with one width; page 6 shows *Fertig* and no *Überspringen*. Page 3: the ⋮ menu drawn at the right, below the segment row, „✓ Meine Tees" readable. At font 2.0 the text scrolls and the mock-up keeps its size. |
| **English tour** | `TourEn_page{1..6}_{light,dark}`, `TourEn_page1_{light,dark}_font2_0` | The same pages in English („More: …", Next, Skip, Done). |

## Anleitungen (SCR-35, SCR-36; ONB-08; D-137 Q5–Q10)

| Part | Renders (`onboarding/`) | What to look at |
|---|---|---|
| **List** | `HowTo_List_{light,dark}`, `HowToEn_List_{light,dark}` | The eight topics in order with their one-line descriptions. |
| **Topics** | `HowTo_{WithoutCatalog,ShopLink,StockAndCost,Recipes,Brewing,JournalAndTaste,DataAndBackup,ReliableAlarm}_{light,dark}` and `HowToEn_…` | Intro, numbered steps (matcha circle), the mock-ups under their step with the step number in a badge **outside** the ring (it covers no text), the „Tipp" / „Tip" line at the end. Check M-CATALOG-TEA's price „12,40 € / 100 g", M-HISTORY's „+100 g · 12,40 €", M-SAVED's five stars (4,0 and 4,5), M-CATALOG-MENU with *Katalog* selected. |
| **Font 2.0** | `HowTo_StockAndCost_{light,dark}_font2_0`, `HowTo_ShopLink_{light,dark}_font2_0` | The text wraps, the mock-ups keep their size; the long title „Tee per Shop-Link hinzufügen" wraps in a taller top bar. |
| **Mock-up legibility** | any topic | The labels inside the mock-ups are small (about 6 dp); judge them on the Galaxy (09 §4 „v0.11.0", D-137 rc-session answers). |

## ⋮ Hilfe and the Hilfe row (SET-36; D-137 Q7, Q8)

| Part | Renders (`menus/`) | What to look at |
|---|---|---|
| **Sammlung** | `Collection_myTeasMenu_{light,dark}_font1_0` | *Hilfe* just above *Einstellungen*. |
| **Tee** | `TeaDetail_catalogMenu_{light,dark}_{font1_0,font2_0}` | *Hilfe* last, below *Löschen*. |
| **Rezept bearbeiten** | `RecipeEditor_shareMenu_{light,dark}`, `RecipeEditor_new_{light,dark}_{font1_0,font2_0}` | *Hilfe* last; a new recipe gets a ⋮ with *Hilfe* only. |
| **Session** | `SessionPage_menuCatalogOpen_{light,dark}_{font1_0,font2_0}` | *Hilfe* last. |
| **Daten & Sicherung** | `DataBackup_neverSaved_{light,dark}_{font1_0,font2_0}`, `DataBackupEn_neverSaved_{light,dark}` | The new ⋮ in the top bar (*Hilfe* only). |
| **Einstellungen** | `Settings_teaware_{light,dark}_{font1_0,font2_0}` | *Hilfe* with *Einführung* and the new *Anleitungen* row. |
