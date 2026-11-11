# UmamiT promo video — storyboard (draft 1)

A draft for the PO to react to. Nothing here is decided: captions, order and
length are proposals, and the open points are listed in §5.

## 1. Format

| Item | Proposal |
|---|---|
| Length | 44 s (11 bars of 4 s) |
| Picture | 1080 × 1920 (9:16), 60 fps; a 16:9 cut from the same scenes later |
| Language | German captions, informal „du"; no voiceover |
| Look | The app's own design: washi paper, ink artwork, Alegreya Sans, matcha green |
| Music | Own synthesised piece, 60 BPM, yo scale on D (§3) |
| Sound | The app's own alert tone (Klangschale) where the timer ends |

## 2. Scenes

Every scene starts on a bar of the music. „Real" means a Roborazzi render of
the app's screen; „rebuilt" means the screen is rebuilt in HTML from the app's
colours, fonts and artwork, so that it can move.

| # | Time | Scene | What you see | Caption (draft) | Source |
|---|---|---|---|---|---|
| 1 | 0:00–0:04 | Opening | Empty washi paper. The ink sprig of the logo appears as if brushed; the name sets below it. | „UmamiT" · „Grüner Tee, auf den Punkt gebrüht." | Logo A1, rebuilt |
| 2 | 0:04–0:08 | Start | The phone rises in with the Start screen. A tap on *Aufguss starten*; the tea picker opens. | „Tee wählen und los." | Real: `Home_seeded`, `TeaPicker` |
| 3a | 0:08–0:13 | Timer runs | The timer screen in the dark Steeping look. The ring runs, the liquor rises, the digits count down in real time, one per beat. | „Jeder Aufguss auf die Sekunde." | Rebuilt (the test scene) |
| 3b | 0:13–0:16 | Pre-alert | Time-lapse to ten seconds before the end. The tick on the ring glows once. | „Ein leiser Ton, kurz bevor es so weit ist." | Rebuilt |
| 3c | 0:16–0:20 | Alert | The screen pulses washi ↔ matcha, „Jetzt abgießen!". The Klangschale sounds. A tap on *Abgegossen*; the second dot fills. | „Und einer, wenn es so weit ist." | Rebuilt; `TimerScreen_Alerting` as reference |
| 4 | 0:20–0:24 | Your recipe | The brew setup: temperature, amount of tea, vessel, the steps of all infusions. | „Temperatur, Menge, Ziehzeit: für jeden Tee gemerkt." | Real: `BrewSetup_kyusu`, `StepsSheet` |
| 5 | 0:24–0:28 | Two at once | Back on Start, two brews run in the bar at the bottom; the quick timer's chips flash by. | „Zwei Tees gleichzeitig? Geht." | Real: `ActiveBrewBar_twoBrews`, `QuickTimer_chips` |
| 6 | 0:28–0:32 | Catalog | The catalog list scrolls; the filter sheet slides up, a flavour is chosen, the list narrows. | „Finde deinen Tee. Nach Geschmack." | Real: `Catalog_list`, `Catalog_filterSheetTaste` |
| 7 | 0:32–0:36 | Your teas | *Meine Tees* with the stock; one tea shows *Knapp ab*. | „Dein Vorrat im Blick." | Real: `Collection_myTeas`, `TeaDetail_stockWarningOn` |
| 8 | 0:36–0:40 | Journal | The journal fills by itself, session by session. | „Dein Tee-Tagebuch schreibt sich von selbst." | Real: `Journal_withSessions`, `SessionPage_available` |
| 9 | 0:40–0:44 | Close | The phone sinks away; the sprig and the name on washi. The bowl rings out. | „UmamiT" · „Mehr Umami in jeder Tasse." | Logo A1, rebuilt |

Scene 8 gains the statistics once M4 has built them (today the app shows a
placeholder there).

## 3. Motion and music

- **One move per scene.** The phone arrives once (scene 2) and stays; between
  scenes 2 and 8 only its screen changes, with a short cross-fade and a
  slight push. Captions rise in line by line.
- **The app's motion is shown as it is.** Ring, liquor, pre-alert glow and
  alert pulse follow `TimerRing.kt` and 03 §3.6. The one liberty in the test
  scene is the ring running up to its level while the phone arrives.
- **Light and dark alternate** as in the app: the timer is dark (Steeping),
  everything else is light.
- **Music:** a plucked string (koto-like), a soft pad, a singing bowl at the
  start of each phrase and a quiet bass, in a small hall. The first beat is at
  0.5 s; the countdown's digits change on the beat. `music.js` writes the
  sketch (24 s so far; it grows to the full length with the video).

## 4. Rules that apply

- No Tee-Kontor texts, photos or product names (CLAUDE.md rule 3): teas carry
  made-up names („Gyokuro Schattenhain") and placeholder images.
- Artwork, fonts and sounds are the app's own (`docs/design/assets/source/CREDITS.md`).
  The body font in the video is Roboto, as in the Roborazzi renders; on the
  PO's phone the system font differs.
- The video does not change the app.

## 5. Open points for the PO

1. **Now or after M4?** With v0.5.1 the video has no statistics. Waiting for
   M4 gives the full story; making it now and replacing scene 8 later is also
   possible.
2. **Length:** 44 s as above, or a shorter cut of about 20 s (scenes 1, 3, 6, 9)
   in addition?
3. **Captions:** the wording above is a draft. Which claim closes the video?
4. **Tee-Kontor:** the catalog scene shows made-up teas. Should the shop be
   named at all?
5. **Where it is shown** (GitHub README, a release page, elsewhere) decides
   the formats: 9:16 only, or 16:9 as well.
6. **The music sketch:** is this the right direction (calm, Japanese
   colouring), or should it be warmer, brighter, or more reduced?
