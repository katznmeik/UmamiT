# M2 design review (early check #77, final review with the rc)

Rendered from the committed Roborazzi goldens (`src/test/screenshots/` of each module),
scaled to 600 px wide and palette-optimised, as for M1. Synthetic sample teas and
placeholder images only (no Tee-Kontor content, D-016). Record-only: not verified in CI.
Regenerate the goldens with `./gradlew recordRoborazziDebug`.

Naming: `<Screen>_<state>_<light|dark>.png`; the suffix `_font2x` is the same render at
font scale 2.0 (the worst-case set).

| Folder | Content |
|---|---|
| `catalog/` | SCR-19 catalog list: default view (grouped by *Sorte*, available, „Pur" on; badges „Neu", Bio, „im Besitz", „Rezept prüfen"), filtered list with no hits, filter sheet, empty state with *Katalog laden* (A8 placeholder until the PO's image). SCR-22 refresh sheet: running, success (first load), summary of a later refresh, shop changed. SCR-21 tea detail: top (*In Sammlung aufnehmen*, *Im Shop öffnen*), end (description and the shop's brewing table verbatim), derived recipes with „Rezept prüfen", packages with estimates. SCR-23 add sheet and repurchase sheet (font 1.0 only). |
| `onboarding/` | ONB-03 setup page *Katalog jetzt laden* / *Später*, and its loading state. |
| `settings/` | The *Katalog* section: last refresh with *Jetzt aktualisieren*, and *Fotos laden* (SET-22, SET-23). |

Known points to judge:
- The floating back button on the detail stays over the content when it scrolls (see
  `Catalog_detailRecipes_*`), semi-transparent over section headings.
- Rows without a photo show the tea-type leaf placeholder (03 §3.5); the sample photos are
  gradients.
- Not built yet: the Ratgeber (SCR-20, #76) and the picker flow *Aus dem Katalog* (#72).
