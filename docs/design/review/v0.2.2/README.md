# v0.2.2 design review (with `v0.2.2-rc.1`, #118)

These are the Roborazzi goldens that v0.2.2 added or changed (`src/test/screenshots/` of each module), scaled to 600 px wide and palette-optimised, as for v0.2.1.
- They use synthetic sample teas and placeholder photos only, no Tee-Kontor content (D-016).
- Re-record the goldens with `./gradlew recordRoborazziDebug`.

Naming:
- `<Screen>_<state>_<light|dark>.png`.
- `_font2x` is the same render at font scale 2.0.
- The timer is always dark (Steeping), so its `light`/`dark` is the app theme around it.

| Folder | Content |
|---|---|
| `brew/` | See below. |
| `home/` | Start with the low-stock card „*Tanabata* wird knapp · 14 g übrig" (*Ausblenden*, *Nachbestellen*; INV-10, #115) and without it. |
| `settings/` | The new section *Brühen & Vorrat* with „Knapp ab 15 g" and its dialog (SET-16, #115); *Katalog* moved down. |
| `catalog/` | See below. |

**`brew/`**
- **SCR-05 timer, READY (#112):** „4,0 g Tee · 250 ml Wasser" under „Wasserkocher auf 60 °C", in the same style; also cold and ice brew. Every brew timer render changed slightly: the space below the ring now reserves the amount line and FINISHED's two controls, so the pill never moves at *Start*, at the alert or at the last *Abgegossen* (D-058, D-097).
- **`TimerScreen_short_*`:** 384 × 832 dp at font 2.0 with a two-line name; the band gives way so *Start* fits.
- **SCR-07 Schnelltimer (#116, #111):** the chips with 8:00 (wrapping 5 + 2, at font 2.0 4 + 3), the *Vorwarnung* row („Auto · 10 s", „Aus", „20 s", „· zu kurz") and its menu, the pre-alert mark on the ring before *Start*.
- **SCR-04 setup (#115):** „14 g übrig · knapp"; stock amounts without a trailing „,0".
- **SCR-03 tea picker (#114):** *Zuletzt* and *Favoriten* on top, then „Grüntees" and „Kräutertees".

**`catalog/`**
- **SCR-19 Merkliste (#117):** the bookmark on every row of a tea not in the collection, the *Gemerkt* chip after *Filter*, the *Gemerkt* view (green and herbal, discontinued ones tagged „Nicht mehr im Sortiment") and its empty state „Noch nichts gemerkt · Tippe bei einem Tee auf das Lesezeichen.".
- **SCR-21:** *Merken* / „Gemerkt" next to *Im Shop öffnen*.
- The other catalog renders changed only by the *Gemerkt* chip and the row bookmarks.

Not rendered: the notification tap that opens the refresh sheet (#113) is behaviour, checked by tests and on the phone.

Known points to judge:
- The bookmark is an outline when not marked and filled in the accent ink when marked; does it read clearly next to the row's chevron?
- The READY amount line has the kettle hint's weight; is that the right emphasis?
- On the short screen the band nearly disappears at font 2.0 so that every control fits.
