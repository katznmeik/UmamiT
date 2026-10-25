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
| `guides/` | *Ratgeber* (CAT-09, #76): the list under the A11 header (placeholder landscape until the PO's image) with hero thumbnails and dates, its empty state (font 1.0 only), an article (SCR-20) at the top (hero, serif title, source line with date, text with a tea link) and at its end (list, image, links, *Im Browser öffnen*). The catalog list renders show its *Ratgeber* row. |
| `onboarding/` | ONB-03 setup page *Katalog jetzt laden* / *Später*, and its loading state. |
| `settings/` | The *Katalog* section: last refresh with *Jetzt aktualisieren*, and *Fotos laden* (SET-22, SET-23). |

Known points to judge:
- The floating back button on the detail stays over the content when it scrolls (see
  `Catalog_detailRecipes_*`), semi-transparent over section headings.
- Rows without a photo show the tea-type leaf placeholder (03 §3.5); the sample photos are
  gradients.
- The article's back button floats over the text the same way when it scrolls (`Guide_articleEnd_*`).
- Not built yet: the picker flow *Aus dem Katalog* (#72).
