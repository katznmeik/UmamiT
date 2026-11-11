# UmamiT promo video

A short promotional video for the app, built as code: each scene is an HTML
page that draws any moment of itself, rendered frame by frame in headless
Chromium and encoded with ffmpeg. The music is synthesised by a script.

**Status:** the whole film (44 s, storyboard draft 2) is built and waits for
the PO's verdict. Nothing here is part of the app or its build.

| File | What it is |
|---|---|
| `storyboard.md` | The scenes, captions, music, the PO's decisions and what is open |
| `scenes/film.html` | The film: all scenes on one timeline |
| `render.js` | Renders a scene to MP4, to stills, or to a comparison shot |
| `music.js` | Writes the film's sound `out/music.wav` (music, wind, the app's two alert sounds) |
| `out/` | Renders (not committed) |

## Render

Needs Node, ffmpeg and Playwright with Chromium (all preinstalled in cloud
sessions). Roboto comes from npm.

```bash
cd promo
npm install
node music.js                                  # needs ffmpeg, to decode the app's two sounds
node render.js film --audio out/music.wav      # out/film.mp4, 1080 × 1920, 60 fps
node render.js film --from 12 --to 16          # a part of it
node render.js film --still 21.3               # one frame
node render.js film --sheet 5.9,12.4,21.3      # several frames side by side
```

The full render draws 2640 frames with four browsers side by side
(`--workers`) and takes several minutes.

## How a scene works

- A scene sets `window.DURATION` (seconds) and `window.seek(t)`, which draws
  the frame at second `t`. It has no clock of its own, so a render is
  repeatable.
- `render.js` serves the repository on localhost. Scenes load the app's own
  fonts and artwork by path (`/core/designsystem/src/main/res/…`,
  `/docs/design/assets/source/…`); nothing is copied.
- Screens that stand still are the Roborazzi goldens, loaded by path. When a
  golden changes, the film changes with the next render.
- The timer must move, so it is rebuilt in the scene in dp, from the specs
  (03 §3.1, §3.2, §3.6) and the composables. Check it against its golden:

  ```bash
  node render.js film --compare                  # out/film-compare.png, 822 × 1782
  # golden: feature/brew/src/test/screenshots/TimerScreen_Running_appDark_font1_0.png
  ```
- The film's times live in one table (`T` in `scenes/film.html`); `music.js`
  repeats the four it needs (the pre-alert, the alert, the two time-lapses).

## Rules

- No Tee-Kontor texts or photos (CLAUDE.md rule 3). The shop is named, as
  the PO decided; teas and images are the goldens' fixtures.
- Only the app's own artwork, fonts and sounds, or own work (see
  `docs/design/assets/source/CREDITS.md`).
- German captions, informal „du" (CLAUDE.md rule 4).
