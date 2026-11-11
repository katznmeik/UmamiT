# v0.6.0 design review (with `v0.6.0-rc.1`, #233)

These are Roborazzi goldens of the screens v0.6.0 added or changed (`src/test/screenshots/` of `feature:journal` and `feature:collection`), scaled to 600 px wide and palette-optimised, as for v0.5.1.
- They use synthetic sample teas and placeholder photos only (flat tints, the app's own illustrations), no Tee-Kontor content (D-016).
- Re-record the goldens with `./gradlew recordRoborazziDebug`.

Naming:
- `<Screen>_<state>_<light|dark>.png`.
- `_font2x` is the same render at font scale 2.0.
- `_short_font2x` is a short screen at font scale 2.0 (light only).

## The screens of D-112

| Part | Folder | Renders |
|---|---|---|
| **„Gespeichert" sheet** (#227, #228; SCR-06, TMR-12) | `journal/` | `Journal_savedSheet`: the summary line, five empty stars, *Notizen hinzufügen*; `Journal_savedSheetRecipeOffer`: with *Zeiten ins Rezept übernehmen*; `Journal_savedSheetAllLines`: every line at once (offer, „Packung als geöffnet markiert", *Rezept passt*); `Journal_savedSheetOpened`, `Journal_savedSheetRecipeCheck`: the earlier lines under the new ones. |
| **Session page: top** (#224, #229; SCR-16, JRN-02, JRN-05, JRN-07) | `journal/` | `SessionHero_teaCover`: the tea's cover as the hero, ← and ⋮ over it, *+ Foto*; `SessionHero_sessionPhotos`: own photos with dots and the photo's ⋮; `SessionHero_leaf`: no photo at all; `SessionHero_limitReached`: *+ Foto* greyed at six; `SessionPage_available`: an untouched session (empty stars, empty notes); `SessionPage_ratedWithLongNote`: 3,5 stars and a long note; `SessionPage_top_short_font2x`. |
| **Session page: GESCHMACK** (#225; JRN-03) | `journal/` | `SessionPage_tasteFolded`: the one row „Geschmack hinzufügen"; `SessionPage_tasteTwoAxes`: open, two sliders set, no radar yet, the others „–"; `SessionPage_tasteAllSix`: the radar above the six sliders and *Zurücksetzen*. |
| **Session page: BESCHREIBUNGEN** (#226; JRN-04, SCR-30) | `journal/` | `SessionDescriptors_sectionEmpty`, `_sectionWithChips`: the section on the page; `SessionDescriptors_sheetOpen`: the sheet with its groups and *Fertig*; `_sheetSearched`: the search „nor"; `_sheetAtTwenty`: the limit, the others greyed with „Höchstens 20 Beschreibungen". |
| **Session page: recipe offer and tea states** (#228; PRF-07) | `journal/` | `SessionPage_recipeOffer`: the line under the step table; `SessionPage_archivedTea`, `SessionPage_deletedTea`, `SessionPage_archivedEnd_short_font2x`: the inputs stay for a tea that is archived or gone. |
| **Session editor** (#230; SCR-17, JRN-11) | `journal/` | `SessionEditor_untouched`: date, time, vessel, leaf, water, the steps with *Zeit* and the °C stepper, skipped and never brewed steps greyed; `SessionEditor_stock_line`: „Dein Vorrat wird um 2 g angepasst." under a changed leaf; `SessionEditor_error`: the inline messages. |
| **Journal** (#231; SCR-15, JRN-08, JRN-09) | `journal/` | `Journal_withSessions`: „★ 4,5" in the rows, the search field and the filter icon; `Journal_searchActive`; `Journal_filterSheet`: *Tee*, *Bewertung*, *Mit Notizen*; `Journal_filterChips`: active filters as chips under the search; `Journal_noMatch`: „Keine Sitzung passt zu deiner Suche." with *Filter zurücksetzen*; `Journal_empty`: the A6 illustration. |
| **Tea screens** (#232; SCR-08, SCR-09, TEA-08, TEA-11, TEA-13) | `collection/` | `TeaDetail_rating`: „★ 4,5 (12)" under the names; `TeaDetail_taste`: GESCHMACK with the average radar and the top descriptors; `TeaDetail_recent`: LETZTE SITZUNGEN with *Alle anzeigen*; `Collection_myTeas`: the average in the rows; `Collection_sortMenu`: the order *Bewertung*; `Collection_myTeas_short_font2x`. |

Not rendered (behaviour, checked by tests and on the phone, 09 §4 „v0.6.0"): the stars' tap cycle (4,0 → 3,5 → none) and the sheet's 10 s restarting with each tap; the notes saving themselves; the radar easing in above the sliders when the third axis is set; the photo picker and the hero's swipe; accepting the recipe offer; the editor's effect on stock and date; the search matching notes and descriptors; *Alle anzeigen* opening the Journal filtered to the tea; TalkBack's readings.

## Points to judge

Collected while building (D-112's build notes) and in the reviews. None is decided; an answer goes through the process (grilling → spec → ticket).

**„Gespeichert" sheet** (`Journal_savedSheet*`)
- The empty stars are faint in the dark theme.
- *Notizen hinzufügen* is a plain text button.
- The check mark aligns to the top of the taller column.

**Filter sheet** (`Journal_filterSheet`)
- „Mit Notizen" is both the heading and the chip.

**GESCHMACK** (`SessionPage_taste*`)
- The label column's width at font 2.0 (`_font2x`).
- An unset slider's thumb sits at 0, like a set 0; only the „–" tells them apart (`SessionPage_tasteTwoAxes`).
- The radar appears above the sliders once three are set and moves them down (eased since the fix round). It could also sit below the sliders, or have its space reserved from the start.
- TalkBack (not visible here): an unset slider cannot be set to 0, and no axis can be unset on its own.

**Session editor** (`SessionEditor_*`)
- *Zeit* is a text field (m:ss), not a duration picker.
- The leaf field has the setup's limits (0,1–100 g), so 0 cannot be entered.
- The out-of-range message „Zwischen 0:01 und 24:00:00." mixes two formats.

**Tea detail** (`TeaDetail_*`)
- The descriptor chips sit close under the radar's bottom label.
- The edit button covers the third LETZTE SITZUNGEN row's rating.
- GESCHMACK is absent with one or two axes and no descriptor.

***Meine Tees*** (`Collection_myTeas`)
- A rated favourite reads „★ 4,5 ★".
- Archived rows show the rating too.

**Session page** (`SessionHero_sessionPhotos`)
- Two ⋮ on the hero: the page's at the top, the photo's at the bottom (the layout itself is confirmed, D-112 Q20).

**Recipe offer** (behaviour; D-112's build notes for #228)
- An archived tea gets no offer.
- Rounding follows the recorded time (5 s steps below 10 min, 1 min from 10 min).
- A base temperature changed in the setup counts as a change.
- A rinse ended early counts.
