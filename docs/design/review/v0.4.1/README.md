# v0.4.1 design review (with `v0.4.1-rc.1`, #188)

These are the Roborazzi goldens that v0.4.1 added or changed (`src/test/screenshots/` of each module), scaled to 600 px wide and palette-optimised, as for v0.4.0.
- They use synthetic sample teas and placeholder photos only (flat tints), no Tee-Kontor content (D-016).
- Re-record the goldens with `./gradlew recordRoborazziDebug`.

Naming:
- `<Screen>_<state>_<light|dark>.png`.
- `_font2x` is the same render at font scale 2.0.
- `_short_` is the short screen, 384 × 832 at font 2.0.

| Folder | Content |
|---|---|
| `collection/` | See below. |
| `journal/` | The „Gespeichert" sheet after a brew with „Packung als geöffnet markiert" · *Rückgängig* (`Journal_savedSheetOpened`, #180). |
| `brew/` | The tea picker with the freshness chip (`TeaPicker_freshness`, #178). |
| `teaware/` | Vessel photos (#187): the *Teegeschirr* list with photos instead of the type icon (`Teaware_listPhotos`), the list and the vessel editor (`VesselEditor_photo`: *Foto ersetzen* / *Foto entfernen*; `_edit`, `_newErrors`, `_archived` changed by the shared `EditorScaffold`, #176). |

**`collection/`**
- **Meine Tees:** `Collection_myTeas` with the freshness labels (#178).
- **Tea detail photos (#183, #186):** `TeaDetail_photos` (pager dots, ⋮ and *+ Foto*), `_photoMenu` (*Als Titelbild*, *Löschen*), `_photoDelete` („Foto löschen?"), `_photoLimit` (*+ Foto* greyed, „Höchstens 10 Fotos").
- **FRISCHE (#178, D-107):** `TeaDetail_freshness` (label, „Geöffnet am …", „Mindestens haltbar bis …"); `TeaDetail_dates` (DETAILS without the opening date).
- **VORRAT (#181, #182, #185):** `TeaDetail_vorrat`, `_cost` („… € / 100 g", the recipe estimate and the Ø per cup), `_history` (purchases, corrections, brews; *Alle N Einträge anzeigen*), `_history_delete` („Kauf löschen?"), `_purchase` (the *Nachkauf erfassen* sheet), `_lowArchived`, `_ready`.
- **Reset (#184):** `TeaDetail_reset` („Aus Katalog zurücksetzen?" with the note on dropped catalog recipes).
- **Tea editor dates (#179):** `TeaEditor_freshness` (*Geöffnet am*, *Haltbar bis*), `_freshnessError` (an opening date in the future).

Not rendered (behaviour, checked by tests and on the phone, 09 §4 v0.4.1): the photo picker and the fade-in, *+ Foto* moving to the new page, the purchase sheet sliding out, a purchase restoring an archived tea, the vessel photo after a process death, price „0" refused, *Rückgängig* of the opening date, Settings closing on a tab switch.

Known points to judge:
- **Placeholder photos:** the renders use flat tints; the real look depends on the PO's own photos on the phone.
- **The pencil FAB** can cover a history row's delete icon at the bottom edge; 88 dp end space keeps it reachable.
- **Detail opened from the setup** (⋮ *Tee ansehen*): brew rows in the history aren't tappable there (no session route).
- **No message after a reset**: the dialog closes and the detail shows the new recipes.
