# UmamiT promo video — storyboard (draft 2, built)

The film as `scenes/film.html` draws it. The PO has seen draft 1 and the test
scene and answered its open points (§5); the film itself is not approved yet.

## 1. Format

| Item | Value |
|---|---|
| Length | 44 s (11 bars of 4 s) |
| Picture | 1080 × 1920 (9:16) for phones, 60 fps |
| Language | German captions, informal „du"; no voiceover |
| Look | The app's own design: washi paper, ink artwork, Alegreya Sans, matcha green |
| Music | Own synthesised piece, 60 BPM, yo scale on D (§3) |
| Sound | The app's own pre-alert tick and Klangschale where the timer plays them |

## 2. Scenes

Every scene starts on a bar of the music. „Real" is a Roborazzi golden of the
app's screen; „rebuilt" is the timer screen rebuilt in HTML from the app's
colours, fonts and artwork, so that it can move. The story follows one brew:
Start → choose the tea → its recipe → the timer → pour.

| # | Time | Scene | What you see | Caption | Source |
|---|---|---|---|---|---|
| 1 | 0:00–0:04 | Opening | Washi paper. The logo's sprig appears as if brushed; the name sets below it. | „UmamiT" · „Grüner Tee, auf den Punkt gebrüht." | Logo A1 |
| 2 | 0:04–0:08 | Start | The phone rises in with the Start screen. A tap on *Aufguss starten*; the tea picker slides up; a tap on a tea. | START · „Tee wählen und los." | Real: `Home_seeded`, `TeaPicker` |
| 3 | 0:08–0:12 | Recipe | The brew setup of that tea: vessel, water, leaves, kettle temperature, the infusions. A tap on *Brühen starten*. | DEIN REZEPT · „Temperatur, Menge, Zeit: alles in deinem Rezept." | Real: `BrewSetup_kyusu` |
| 4 | 0:12–0:16 | Timer runs | The timer in the dark Steeping look. The ring runs, the liquor rises, the digits count in real time, one per beat. | DER TIMER · „Jeder Aufguss auf die Sekunde." | Rebuilt |
| 5 | 0:16–0:20 | Pre-alert | Time-lapse to ten seconds before the end; the tick on the ring glows once and the app's tick sounds. A second time-lapse to the end. | DIE VORWARNUNG · „Ein leiser Ton kurz vor Schluss." | Rebuilt |
| 6 | 0:20–0:24 | Alert | The screen pulses washi ↔ matcha, „Jetzt abgießen!", the overtime counts. The app's Klangschale sounds. A tap on *Abgegossen*. | DER ALARM · „Die Klangschale, wenn es so weit ist." | Rebuilt; `TimerScreen_Alerting` as reference |
| 7 | 0:24–0:28 | Two timers | A second tea steeps; the first brew waits in its row above. Both count down. | ZWEI TIMER · „Zwei Tees gleichzeitig? Geht." | Rebuilt; `TimerScreen_Running_brewRow` as reference |
| 8 | 0:28–0:32 | Catalog | The catalog; a tap on *Filter*, the sheet slides up, *Fertig*, the list is filtered. | DER KATALOG · „Das ganze Sortiment von Tee-Kontor Kiel." | Real: `Catalog_list`, `Catalog_filterSheetTaste`, `Catalog_listFiltered` |
| 9 | 0:32–0:36 | Your teas | *Meine Tees* with stock and *Knapp*; a tap opens a tea with its stock and the warning. | MEINE TEES · „Dein Vorrat immer im Blick." | Real: `Collection_myTeas`, `TeaDetail_stockWarningOn` |
| 10 | 0:36–0:40 | Journal | The journal; a tap opens a session with its infusions, planned and actual. | DAS JOURNAL · „Dein Tee-Tagebuch schreibt sich von selbst." | Real: `Journal_withSessions`, `SessionPage_available` |
| 11 | 0:40–0:44 | Close | The phone sinks away; the sprig and the name on washi. A large bowl rings out. | „UmamiT" · „Mehr Umami in jeder Tasse." | Logo A1 |

The statistics join scene 10 once M4 has built them (today the app shows a
placeholder there).

## 3. Motion and music

- **One phone, one move.** The phone arrives in scene 2 and stays until the
  close; only its screen changes, as a route fade or a bottom sheet. A soft
  dot shows each tap. Captions rise in line by line.
- **The app's motion is shown as it is.** Ring, liquor, pre-alert glow and
  alert pulse follow `TimerRing.kt` and `Motion.kt` (03 §3.6). Liberties: the
  ring runs up to its level as the timer arrives; two time-lapses shorten the
  minute; scene 6 cuts from *Abgegossen* straight to the second brew.
- **Light and dark alternate** as in the app: the timer is dark (Steeping),
  the alert pulses, everything else is light.
- **Music** (`music.js`): singing bowls carry it. Large struck bowls mark the
  chapters, sung bowls hold a drone, small high bowls answer; a koto-like
  string plays the melody and runs up the scale in the two time-lapses; wind
  moves through leaves all film long, in gusts. The first beat is at 0.5 s and
  the timer's digits change on the beat. The app's tick (B5) and Klangschale
  (A5) are notes of the scale; the music steps back under them.

## 4. Rules that apply

- No Tee-Kontor texts or photos (CLAUDE.md rule 3). The shop is named, as the
  PO decided; the screens show the goldens' fixture teas and placeholder
  images.
- Artwork, fonts and sounds are the app's own (`docs/design/assets/source/CREDITS.md`).
  The body font in the film is Roboto, as in the Roborazzi renders; on the
  PO's phone the system font differs.
- The film does not change the app.

## 5. Decisions (PO, 2026-11-11)

| # | Point | Answer |
|---|---|---|
| 1 | Now or after M4? | Now. |
| 2 | Length | 44 s. |
| 3 | Captions and closing claim | Claude chooses (the captions in §2). |
| 4 | Name Tee-Kontor? | Yes. |
| 5 | Where it is shown | On phones: 9:16 only. |
| 6 | Music | Direction is right, but it sounded very synthetic: more singing bowls, some leaves in the wind. |

## 6. Open

- The PO's verdict on the film: pacing, captions, and whether the music now
  sounds natural enough. Claude cannot listen to it; levels and pitches are
  measured, the sound itself is not judged.
- Naming Tee-Kontor Kiel in a promotional film: whether the shop should be
  asked first is the PO's call.
