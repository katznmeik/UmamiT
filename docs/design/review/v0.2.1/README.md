# v0.2.1 design review (with `v0.2.1-rc.1`, #93)

These are the committed Roborazzi goldens (`src/test/screenshots/` of each module), scaled to 600 px wide and palette-optimised, as for M1 and M2.
- They use synthetic sample teas and placeholder photos only, no Tee-Kontor content (D-016).
- Re-record the goldens with `./gradlew recordRoborazziDebug`.

Naming:
- `<Screen>_<state>_<light|dark>.png`.
- `_font2x` is the same render at font scale 2.0.
- The timer is always dark (Steeping), so its `light`/`dark` is the app theme around it.

| Folder | Content |
|---|---|
| `brew/` | See below. |
| `settings/` | *Vorwarnton* picker (SET-26, #84): Zimbel, Glöckchen, Klangstab, Tiefe Schale, „Ting (leise)", with *Anhören*. The timer settings with the new row, and the *Katalog* section wording. |
| `catalog/` | See below. |
| `onboarding/` | ONB-03 *Katalog laden*, worded for Grün- und Kräutertees. |

**`brew/`**
- **SCR-05 timer (D-095, #85, #102):**
  - the ink-wash band A13 with the tea seal: a white-backed photo blends into the washi disc, and a tea without a photo shows its type leaf;
  - ← and ⋮ on their circles (D-093 point 2);
  - READY, RUNNING, PRE-ALERT, the alert in both pulse phases, FINISHED;
  - the quick timer, which shows the band without a seal;
  - `TimerScreen_short_*`: a 384 × 832 dp screen at font 2.0 with a two-line name, where the band gets shorter so *Abgegossen* stays visible (D-096).
- **SCR-31 Schritte-Sheet (#92):** the Vorwarnung for each infusion („Auto · 5 s", „10 s · zu kurz").
- **SCR-04 brew setup:**
  - the *Vorwarnung* row per step („10 · 5 · –", #92);
  - the vessel list (#86);
  - the stock sheet „Wie viel ist noch da?" (#87), including its error and the line shown during a running session.
- **SCR-03 tea picker (#88):** the long-press menu *Aus Sammlung entfernen*, the blocked state during a session, and the snackbar with *Rückgängig*.

**`catalog/`**
- **SCR-19, Kräutertee view (#89, #91):**
  - grouped by *Sorte*;
  - the hint before the first refresh after the update, with *Jetzt laden*;
  - the filter sheet without Aroma and Umami.
- **SCR-19, green list and empty state:** the green list with the *Kräutertee* chip, and the empty state with A8 (#100).
- **SCR-22:** the refresh summary („276 Tees · …").
- ***Link einfügen* and the shared link (#98):**
  - the paste field;
  - „Das ist kein Tee-Link von Tee-Kontor Kiel.";
  - the not-in-catalog message;
  - loading, offline, and refresh running.
- **SCR-20:** *Ratgeber* under the A11 header (#100), and its empty state.

Known points to judge:
- The seal sits half on the band's faded lower edge, so on a dark band it reads mostly as a disc below the mountains.
- The quick timer's band doesn't shrink on short screens. *Fertig* fits at 411 × 800 dp with font 2.0.
- On the device, the alert sounds, the four Vorwarnung sounds and the band are judged on the phone, with the rc's checklist on #93.
