# UmamiT promo video

A short promotional video for the app, built as code: each scene is an HTML
page that draws any moment of itself, rendered frame by frame in headless
Chromium and encoded with ffmpeg. The music is synthesised by a script.

**Status:** storyboard draft 1 and a five-second test scene (the running
timer). The PO has not approved either yet. Nothing here is part of the app
or its build.

| File | What it is |
|---|---|
| `storyboard.md` | The scenes, captions, music and the open points for the PO |
| `scenes/timer.html` | Scene 3a: the running timer |
| `render.js` | Renders a scene to MP4, to a still, or to a comparison shot |
| `music.js` | Writes the music sketch `out/music.wav` |
| `out/` | Renders (not committed) |

## Render

Needs Node, ffmpeg and Playwright with Chromium (all preinstalled in cloud
sessions). Roboto comes from npm.

```bash
cd promo
npm install
node music.js
node render.js timer --music out/music.wav     # out/timer.mp4, 1080 × 1920, 60 fps (takes a few minutes)
node render.js timer --still 3.2               # one frame
```

## How a scene works

- A scene sets `window.DURATION` (seconds) and `window.seek(t)`, which draws
  the frame at second `t`. It has no clock of its own, so a render is
  repeatable.
- `render.js` serves the repository on localhost. Scenes load the app's own
  fonts and artwork by path (`/core/designsystem/src/main/res/…`,
  `/docs/design/assets/source/…`); nothing is copied.
- App screens that must move are rebuilt in the scene in dp, from the specs
  (03 §3.1, §3.2) and the composables. Check a rebuilt screen against its
  Roborazzi golden:

  ```bash
  node render.js timer --compare                 # out/timer-compare.png, 822 × 1782
  # golden: feature/brew/src/test/screenshots/TimerScreen_Running_appDark_font1_0.png
  ```

## Rules

- No Tee-Kontor texts, photos or product names; made-up tea names and
  placeholder images only (CLAUDE.md rule 3).
- Only the app's own artwork, fonts and sounds, or own work (see
  `docs/design/assets/source/CREDITS.md`).
- German captions, informal „du" (CLAUDE.md rule 4).
