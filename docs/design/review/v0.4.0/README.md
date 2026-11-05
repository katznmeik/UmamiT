# v0.4.0 design review (with `v0.4.0-rc.1`, #160)

These are the Roborazzi goldens that v0.4.0 added or changed (`src/test/screenshots/` of each module), scaled to 600 px wide and palette-optimised, as for v0.3.0.
- They use synthetic sample teas and placeholder photos only, no Tee-Kontor content (D-016).
- Re-record the goldens with `./gradlew recordRoborazziDebug`.

Naming:
- `<Screen>_<state>_<light|dark>.png`.
- `_font2x` is the same render at font scale 2.0.
- `_short_` is the short screen, 384 × 832 at font 2.0.

| Folder | Content |
|---|---|
| `collection/` | See below. |
| `teaware/` | *Einstellungen › Teegeschirr* (#157): the list with favourites and „ARCHIVIERT" (`Teaware_list`), its empty state (`Teaware_empty`), the vessel editor (`VesselEditor_edit`, `_newErrors`, `_archived`). |
| `journal/` | The session page (#159): `SessionPage_available` (step table *geplant* / *tatsächlich* / °C, *So nochmal brühen*), `_archivedTea` (button greyed out, „Tee ist archiviert"), `_deletedTea` (name without a link, no button), the short screen. |
| `brew/` | See below. |
| `settings/` | *Bestand abziehen* in *Brühen & Vorrat* (`Settings_brewing`, #150) and the *Teegeschirr* row (`Settings_teaware`). The other settings renders changed only because the page grew. |
| `catalog/` | Only F9's back arrow on the tea detail and the guide; nothing else changed. |

**`collection/`**
- **Meine Tees (#153):** `Collection_myTeas` (photo, names, Sorte, stock chip „38 g" / *Knapp* / *Leer*, star, „Rezept prüfen", sort chip first), `Collection_archived` (filter *Archiviert*), `Collection_noMatch` („Kein Tee passt zu deiner Suche."), `Collection_noTeas` (the A7 empty state); `Collection_noMatch` carries A10.
- **Tea detail (#154):** `TeaDetail_ready` (umami meter, the three figures, REZEPTE with the default, VORRAT, *Beim Tee-Kontor nachbestellen* as a text link), `TeaDetail_lowArchived` (archived: *Brühen* greyed out, the reorder link as a filled button at low stock).
- **Tea editor and *Sorte wählen* (#155):** `TeaEditor_new`, `_newPurchase` (the *Kauf* section), `_edit`; `SortePicker` (grouped, with search).
- **Recipe editor (#156):** `RecipeEditor_new`, `_edit` (method, vessel, base temperature, *Vorwarnung*, live ratio), `_steps` (drag handles, *Spülen*, notes).

**`brew/`**
- **Vessels in the setup (#157, #158):** `BrewSetup_vesselList` (favourites first, *Neues Gefäß …*), `BrewSetup_archivedVessel` („<Gefäß> (archiviert)"), `BrewSetup_kyusu`.
- **Stock warning (#158):** `BrewSetup_shortStock` („Nur noch 3 g übrig · knapp"), `_lowStock`, `_stock`, `_stockInvalid`, `_stockSession`.
- **Setup ⋮ (#158):** `BrewSetup_menuOpen` (*Tee ansehen*), `_catalogMenuOpen`; `BrewSetup_withoutRecipe` (*Ohne Rezept*, from *So nochmal brühen*); `BrewSetup_changed` (*Im Rezept speichern* only after a change; no empty band otherwise, SCR-04); `BrewSetup_recipeCheck`.
- **Tea picker (#150, F2):** `TeaPicker_menu` (long press: *Archivieren*), `_menuBlocked` („Erst die Sitzung beenden"), `_removed`.
- **Rinse (#150):** `StepsSheet_manySteps` and `TimerScreen_Alerting_rinse_*` name the rinse *Spülen*.
- The other brew renders changed only by small shifts from the shared parts in `core:ui` (#149, F1).

Not rendered (behaviour, checked by tests and on the phone): archive and its *Rückgängig* snackbar, delete with *Stattdessen archivieren*, refusals during a brew, the sort and segment kept over a restart, drag reorder and its motion, *Bestand abziehen* off, *So nochmal brühen* prefilling never-started steps.

Known points to judge:
- **Placeholder vessel icons (#157):** the nine vessel types use simple line icons from `UmamiIcons`; final icons are not yet in `docs/design/assets.md`.
- **A7 (no teas) and A10 (no search hit) empty states** in *Meine Tees*: the vignettes as built in; are the texts right?
- **Tea detail, low stock (`TeaDetail_lowArchived`):** the pencil FAB sits over the right end of the filled *Beim Tee-Kontor nachbestellen* button when the page is scrolled to VORRAT. Acceptable, or should the FAB move or hide there?
- **Chips in *Meine Tees*:** the sort chip comes first; *Kategorie* and *Sorte* show only when the collection has more than one value; a chip's label lists the ticked values. At font 2.0 the chip row scrolls.
- **Tea detail:** the shown recipe temperature is the first non-rinse step's; the session count and the last brew count finished sessions only.
- **Teaware limits:** name 1–40, volume 10–3000 ml, usable 10 ml up to the volume; a new vessel defaults to Kyusu.
- **The session page** writes „übersprungen" for skipped steps and „nicht gebrüht" for never-started ones.
