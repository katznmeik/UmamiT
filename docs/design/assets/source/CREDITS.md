# Credits for bundled assets

Every image and sound in the app is either our own work or public domain /
CC0. The exceptions are the display font (SIL OFL 1.1, D-027) and the Material
Symbols icons (Apache 2.0), whose licence texts are committed in
`core/designsystem/licenses/`. Third-party files record their source URL and licence here; the About
screen shows this list (`docs/design/assets.md` §2, D-036). Never add
Tee-Kontor photos or texts (CLAUDE.md rule 3).

## Sounds

| File | Asset | Source | Licence |
|---|---|---|---|
| `core/content/src/main/res/raw/klangschale.ogg` | Klangschale — default alert tone (D-045) | Synthesised, own work (ffmpeg, `scripts/sounds/generate.sh`) | Own work |
| `core/content/src/main/res/raw/tick.ogg` | Tick — pre-alert, small-bowl „ting" (D-045) | Synthesised, own work (ffmpeg, `scripts/sounds/generate.sh`) | Own work |
| `core/content/src/main/res/raw/glocke.ogg` | Glocke — alert tone, small bell struck twice (D-059) | Synthesised, own work (ffmpeg, `scripts/sounds/generate.sh`) | Own work |
| `core/content/src/main/res/raw/holzblock.ogg` | Holzblock — alert tone, two wood blocks (D-059) | Synthesised, own work (ffmpeg, `scripts/sounds/generate.sh`) | Own work |
| `core/content/src/main/res/raw/glockenspiel.ogg` | Glockenspiel — alert tone, rising steel-bar arpeggio (D-059) | Synthesised, own work (ffmpeg, `scripts/sounds/generate.sh`) | Own work |
| `core/content/src/main/res/raw/sanft.ogg` | Sanft — alert tone, two soft rising tones (D-059) | Synthesised, own work (ffmpeg, `scripts/sounds/generate.sh`) | Own work |

## Fonts

| File | Asset | Source | Licence |
|---|---|---|---|
| `core/designsystem/src/main/res/font/cormorant_garamond_{light,regular,medium}.ttf` | Cormorant Garamond Light/Regular/Medium (D-027), static instances (wght 300/400/500) of the variable font, subset to Latin + German with `lnum`/`tnum` (03 §3.2) | [google/fonts `ofl/cormorantgaramond`](https://github.com/google/fonts/tree/main/ofl/cormorantgaramond) (`CormorantGaramond[wght].ttf`), © 2015 the Cormorant Project Authors | SIL Open Font License 1.1 (`core/designsystem/licenses/CormorantGaramond-OFL.txt`) |

## Icons

| File | Asset | Source | Licence |
|---|---|---|---|
| `core/designsystem/src/main/res/drawable/ic_*.xml` (except `ic_custom_*`) | Material Symbols Outlined, weight 300, as vector drawables (03 §3.4) | [google/material-design-icons `symbols/web`](https://github.com/google/material-design-icons/tree/master/symbols/web) | Apache License 2.0 (`core/designsystem/licenses/MaterialSymbols-Apache-2.0.txt`) |
| `core/designsystem/src/main/res/drawable/ic_custom_*.xml` | Placeholder line icons (leaf, kettle, thermometer, infusions) until the PO's icons exist | Own work | Own work |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | Launcher icon foreground: the A1 logo sprig (M1-21), traced to a vector from `docs/design/assets/source/A1-logo-sprig.png` | AI-generated for the PO (D-029, option C) | Own work |
| `app/src/main/res/drawable/ic_launcher_monochrome.xml` | Themed-icon (monochrome) layer: the same A1 sprig silhouette | AI-generated for the PO (D-029, option C) | Own work |
| `core/timer/src/main/res/drawable/ic_stat_umamit.xml` | Status-bar small icon: the A1 sprig as a solid 24 dp silhouette | AI-generated for the PO (D-029, option C) | Own work |

## Images

| File | Asset | Source | Licence |
|---|---|---|---|
| `core/designsystem/src/main/res/drawable/illustration_placeholder_landscape.xml` | Placeholder landscape for empty states until A1–A5 exist (`docs/design/assets.md`) | Own work | Own work |
| `core/designsystem/src/main/res/drawable-nodpi/illustration_home_header_{light,dark}.webp` | A3 Start header landscape, light and dark (SCR-02), from `docs/design/assets/source/A3-home-header-{light,dark}.png`: cropped to the mountain band, paper colour-matched to the theme background | AI-generated for the PO (D-029, option C) | Own work |
| `core/designsystem/src/main/res/drawable-nodpi/photo_timer_fallback.webp` | A4 timer fallback photo (SCR-05), from `docs/design/assets/source/A4-timer-fallback.png` | AI-generated for the PO (D-029, option C) | Own work |
