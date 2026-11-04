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
| `core/content/src/main/res/raw/tick.ogg` | Tick — pre-alert, small-bowl „ting" (D-045); „Ting (leise)" in SET-26 | Synthesised, own work (ffmpeg, `scripts/sounds/generate.sh`) | Own work |
| `core/content/src/main/res/raw/zimbel.ogg` | Zimbel — pre-alert, two small cymbals with a slow beating (SET-26, D-093) | Synthesised, own work (ffmpeg, `scripts/sounds/generate.sh`) | Own work |
| `core/content/src/main/res/raw/gloeckchen.ogg` | Glöckchen — pre-alert, small high bell (SET-26, D-093) | Synthesised, own work (ffmpeg, `scripts/sounds/generate.sh`) | Own work |
| `core/content/src/main/res/raw/klangstab.ogg` | Klangstab — pre-alert, one wind-chime bar (SET-26, D-093) | Synthesised, own work (ffmpeg, `scripts/sounds/generate.sh`) | Own work |
| `core/content/src/main/res/raw/tiefe_schale.ogg` | Tiefe Schale — pre-alert, lower singing bowl (SET-26, D-093) | Synthesised, own work (ffmpeg, `scripts/sounds/generate.sh`) | Own work |
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
| `core/designsystem/src/main/res/drawable-nodpi/illustration_home_header_{light,dark}.webp` | A3 Start header, light and dark (SCR-02): a flowering tea branch since 2026-11-04. Light from `docs/design/assets/source/A3-home-header-teabranch-light.png` (2172 × 724): the motif band cropped at 3.43:1 (rows 61–694) and scaled to 1536 × 448, the paper colour-matched to `#F3F0E9` (in CIELAB, fully on the paper and fading out towards the darkest ink). Dark derived from the same crop: lightness inverted in CIELAB, the paper to `#1C231D`, the strongest ink to `#A9A99D`, the green kept; ChatGPT's night version `A3-home-header-teabranch-dark.png` was a repaint and is not used. WebP quality 82. Until then the landscape from `A3-home-header-{light,dark}.png`, whose sources stay | AI-generated for the PO (D-029, option C); dark variant derived by Claude | Own work |
| `core/designsystem/src/main/res/drawable-nodpi/illustration_welcome_{light,dark}.webp` | A2 welcome landscape, light and dark (SCR-01), from `docs/design/assets/source/A2-welcome-{light,dark}.png`, paper colour-matched to the theme background | AI-generated for the PO (D-029, option C) | Own work |
| `core/designsystem/src/main/res/drawable-nodpi/illustration_reliability_{light,dark}.webp` | A5 reliability vignette, light and dark (SCR-01), from `docs/design/assets/source/A5-reliability-{light,dark}.png`: motif cropped, paper colour-matched, edges feathered | AI-generated for the PO (D-029, option C) | Own work |
| `core/designsystem/src/main/res/drawable-nodpi/illustration_catalog_empty_{light,dark}.webp` | A8 empty-catalog vignette, light and dark (SCR-19, empty *Ratgeber* list), from `docs/design/assets/source/A8-catalog-empty-{light,dark}.png`: motif cropped (800 × 480), paper colour-matched, edges feathered | AI-generated for the PO (D-029, option C) | Own work |
| `core/designsystem/src/main/res/drawable-nodpi/illustration_guides_header_{light,dark}.webp` | A11 *Ratgeber* header band, light and dark (SCR-20), from `docs/design/assets/source/A11-ratgeber-header-{light,dark}.png`: middle band cropped (1536 × 640, 2.4:1), paper colour-matched, top and bottom feathered | AI-generated for the PO (D-029, option C) | Own work |
| `core/designsystem/src/main/res/drawable-nodpi/illustration_timer_band.webp` | A13 timer ink-wash band, dark only (SCR-05, SCR-07, D-095): a bamboo spray since 2026-11-03, from `docs/design/assets/source/A13-timer-band-bamboo-dark.png` (the night version of `A13-timer-band-bamboo-light.png`): cropped to 1536 × 822 (411:220, rows 40–861), the paper colour-matched from ≈ `#1D2017` to the Steeping background `#1C231D` (in CIELAB, fully on the paper and fading out towards the palest ink, which keeps its colour), WebP quality 82. Until then (v0.2.1–v0.3.0) the night landscape from `A13-timer-band-dark.png`, whose source stays; A13 replaced A4 (retired in v0.2.1, its source stays) | AI-generated for the PO (D-029, option C) | Own work |
