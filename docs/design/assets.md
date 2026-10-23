# Image assets

> Decision [D-029](../specs/10-open-questions-and-decisions.md#d-029): illustrations are
> AI-generated and curated (option C), optionally combined with public-domain
> (CC0) museum ink paintings (option D). The logo is redrawn as a vector during
> M1. Tea photos come from the Tee-Kontor catalog (downloaded by the app at
> runtime, never committed) and from the PO's own photos.

## 1. What is needed, and when

### 1.1 Needed to start (M1, timer first)

| # | Asset | Where it appears | Format to deliver | Variants |
|---|---|---|---|---|
| A1 | **Logo sprig** (two tea leaves) | App icon, splash, welcome, About | PNG ≥ 1024 × 1024, centred on plain washi background; used as reference for the vector redraw | — |
| A2 | **Welcome landscape** | SCR-01 welcome (full screen, portrait) | PNG ≥ 1440 × 3120 (9:19.5) | light |
| A3 | **Home header landscape** | SCR-02 header (no text on it) | PNG ≥ 2400 × 1000 (≈ 2.4:1) | light **and** dark |
| A4 | **Timer fallback photo** ✓ delivered | SCR-05 top area when a tea has no photo (the quick timer has none, D-069) | PNG, landscape 3:2 (≥ 1536 × 1024): the photo band is full width × 280 dp, centre-cropped, its lower part fading into the background; dark and moody, subject in the upper middle | dark only (timer is always dark) |
| A5 | **Reliability illustration** | SCR-01 page „Damit der Timer zuverlässig klingelt" | PNG ≥ 1200 × 900, transparent or washi background | light |

### 1.2 Needed later (M2–M5)

| # | Asset | Where | Format | Milestone |
|---|---|---|---|---|
| A6 | **Empty state: Journal** („Deine erste Tasse wartet.") | SCR-15 | ≥ 1200 × 900 vignette | M4 |
| A7 | **Empty state: Meine Tees** | SCR-08 | ≥ 1200 × 900 vignette | M3 |
| A8 | **Empty state: Katalog** (never loaded) | SCR-19 | ≥ 1200 × 900 vignette | M2 |
| A9 | **Empty state: Statistik** (not enough data) | SCR-18 | ≥ 1200 × 900 vignette | M4 |
| A10 | **No search results** | lists | ≥ 1200 × 900 vignette | M3 |
| A11 | **Ratgeber header** (guides list) | SCR-19 › Ratgeber | ≥ 2400 × 1000 | M2 |
| A12 | **Dark variants** of A2, A6–A10 | dark theme | as above | M5 |

Not needed as images (drawn in code or as vector icons): type colour leaf
fallbacks for tea thumbnails, line icons (kyusu, gaiwan, kettle, …), charts,
the timer ring.

## 2. Delivery

- Generate in ChatGPT (one conversation, so the style stays consistent),
  pick the best of several attempts per asset.
- Upload the chosen originals to `docs/design/assets/source/` with the asset
  number in the file name (e.g. `A3-home-header-light.png`). The repository is
  public, which is fine for our own generated images and CC0 museum images.
  **Never** upload Tee-Kontor photos or texts.
- Claude processes them during development: crop per screen, colour-match to
  the palette, add dark variants if needed, convert to WebP and keep the APK
  size budget (≤ 4 MB of imagery).
- For museum images (option D), note the source URL and licence (must be
  CC0 / public domain) next to the file in `docs/design/assets/source/CREDITS.md`.
- **Sounds** (D-036): the alert tones and the pre-alert tick are synthesised
  by Claude (ffmpeg) in M1. To replace a tone, upload a CC0 recording to
  `docs/design/assets/source/` and note its source URL and licence in
  `CREDITS.md`.

## 3. Shared style block

Prefix every prompt with this block:

```text
Style: Japanese sumi-e ink-wash painting, minimalist and calm, lots of empty
space, soft grey and charcoal ink with very subtle deep matcha green
(#4A5643) accents, painted on warm off-white washi paper (#F3F0E9) with a
faint natural fibre texture. Premium, quiet, wabi-sabi. No text, no
signatures, no seals, no frames, no borders, no people.
```

For dark variants, add:

```text
Dark variant: same composition as night scene, deep forest green-black
background (#1C231D), ink lines in soft pale grey-green (#A9A99D), very low
contrast, no pure black, no pure white.
```

## 4. Prompts per asset

**A1 — Logo sprig**
```text
<style block> A single small tea sprig with exactly two elegant tea leaves
growing from one short stem, painted in deep matcha green (#4A5643) with a
few confident brush strokes, centred on plain washi paper, lots of empty space
around it, symmetrical balance, suitable as an app logo. Square 1:1.
```

**A2 — Welcome landscape (portrait)**
```text
<style block> Tall portrait composition 9:19.5. Misty layered mountains in
the lower third fading into fog, a few terraced tea fields on a gentle slope
in the foreground, two or three tea branches entering from the lower right
corner in deep green. The upper two thirds are almost empty paper with soft
mist, leaving room for a logo and title.
```

**A3 — Home header (wide)**
```text
<style block> Wide panoramic composition about 2.4:1. Soft layered mountain
ridges in mist, a distant tea plantation on rolling hills, delicate
gradients of grey. Very subtle warm morning light.
```
(then the dark variant with the dark block, "evening, moonlit mist")

**A4 — Timer fallback photo**
```text
Photorealistic, moody still life: a handmade matte grey-green ceramic tea
cup filled with pale jade green Japanese tea, seen from slightly above,
standing on dark weathered wood, a few fresh tea leaves beside it, soft
window light from the upper right, deep forest green-black shadows
(#1C231D), shallow depth of field, calm and premium. Landscape 3:2, the cup
in the upper-middle part of the frame (the top 40 % holds its rim), the lower
half falling off into dark, empty shadow. No text, no people, no steam, no
extra props.
```

**A5 — Reliability illustration**
```text
<style block> A small side-handled Japanese kyusu teapot and a smartphone
lying beside it on washi paper, a few soft ink sound waves rising from the
phone, one small tea leaf. Simple, friendly, lots of empty space. 4:3.
```

**A6–A10 — Empty-state vignettes** (4:3, small motif in the centre, mostly
empty paper)
```text
<style block> 4:3 vignette, small motif in the centre, mostly empty paper:
A6: an empty handleless tea cup with a single tea leaf beside it.
A7: an empty wooden tea shelf with one small tea canister (chazutsu).
A8: a closed traditional Japanese paper booklet with a tea leaf on top.
A9: three small stones stacked beside a tea leaf, like a quiet garden.
A10: a single tea leaf floating on a calm water surface with soft ripples.
```
(generate each one separately, using the line for that asset)

**A11 — Ratgeber header**
```text
<style block> Wide 2.4:1: an open book with blank pages on washi paper, a
kyusu teapot and a cup in soft ink behind it, misty mountains faintly in the
background.
```

## 5. Museum alternative (option D)

For hero landscapes (A2, A3, A11) a public-domain historical Japanese ink
landscape can replace or complement the AI image. Search the open-access
collections (CC0 / public domain), e.g. The Met Open Access, Art Institute of
Chicago (CC0 images), Rijksmuseum, Smithsonian Open Access. Use search terms
like "Japanese ink landscape", "sansui", "suibokuga", "misty mountains
hanging scroll", "Kano school landscape". Download the highest resolution and
record the URL and licence in `CREDITS.md`.
