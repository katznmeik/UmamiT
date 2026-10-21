# Prompt for new brand mockups (ChatGPT image generation)

Use with ChatGPT (image generation). Optionally attach the existing
`brand-mockup.png` as a style reference. Image models render long text
unreliably, so the prompt keeps on-screen text short and spells it out
exactly. Generate the overview board first, then single screens if you need
more detail.

## 1. Overview board (one image)

```text
Create a high-end mobile app design presentation board, landscape 3:2, for an
Android app called "UmamiT", a calm, premium tea-brewing companion for
Japanese green teas. If I attached an image, match its style closely.

STYLE
- Mood: quiet Japanese tea house, wabi-sabi, generous whitespace, premium.
- Background of the board: warm off-white washi paper with subtle fibre
  texture, an ink-wash (sumi-e) mountain landscape in soft grey fading in at
  the lower left, and a few dark-green tea leaves.
- App colours: washi off-white #F3F0E9 backgrounds, deep matcha/olive green
  #4A5643 for primary buttons, cream #ECE5D6 pills, ink text #2B2D27, warm grey
  secondary text. The timer screen is dark forest green-black #1C231D.
- Typography: elegant high-contrast serif (like Cormorant Garamond) for titles
  and tea names; small clean sans-serif for body; tiny letter-spaced UPPERCASE
  section labels.
- Thin line icons, circular photo thumbnails, hairline dividers, flat cards
  (no heavy shadows).
- Photography: moody, softly lit close-ups of green tea in handmade ceramic
  cups and kyusu teapots, dark wood, fresh tea leaves.

DEVICE
- Show the screens on a modern Samsung Galaxy phone (Android: punch-hole
  camera, Android status bar with "9:41"). Do NOT show iPhones.

LAYOUT
On the left third, the logo: a two-leaf tea sprig in deep green above the
serif wordmark "UmamiT" and the tagline "BESSERER TEE. TIEFERE MOMENTE.".
Small vertical Japanese text "お茶で、いい時間を。" near the right edge.
On the right two-thirds, eight phone screens in two rows of four.

ALL UI TEXT IS GERMAN. The bottom navigation on light screens has exactly four
items with thin icons: "Start", "Journal", "Sammlung", "Statistik".

SCREENS (top row, left to right)
1. START (home): serif title "UmamiT" with a settings gear; ink-wash mountain
   header illustration (no text over it);
   a dark-green card with a leaf icon "Aufguss starten"; a section label
   "NOCHMAL BRÜHEN" with three round tea thumbnails; a section "ZULETZT
   GEBRÜHT" with two list rows ("Gyokuro · 3 Aufgüsse", "★ 4,5").
   Navigation "Start" selected.
2. TIMER, running (dark screen): moody tea-cup photo at the top fading into
   the dark background; serif tea name "Bio Uji Gyokuro"; small line
   "55 °C · Aufguss 1 von 3"; three small step dots; a large thin circular
   progress ring with a tiny leaf at the top and a small tick mark on the ring
   shortly before the end; inside it "01:12" in light digits and "Zieht …";
   below, ONE cream pill button "Pause". No other buttons.
3. TIMER, between infusions (dark screen): same layout, empty ring; inside it
   a big "Start"; under the ring a kettle icon with "Wasserkocher auf 60 °C"
   and "Aufguss 2 · 0:20".
4. LOCK SCREEN: Samsung Galaxy lock screen with a soft tea photo wallpaper,
   big clock, and at the bottom a pill-shaped live activity ("Now Bar") with a
   leaf icon, "Gyokuro · Aufguss 2", a segmented progress bar and "0:14",
   plus a small "Pause" button.

SCREENS (bottom row, left to right)
5. SAMMLUNG › KATALOG: segmented control at the top with "Meine Tees" and
   "Katalog" (Katalog selected); a small line "Tee-Kontor Kiel · 142 Grüntees";
   a rounded search field; filter chips "Sorte", "Herkunft", "Bio"; three list
   rows with round product photos: "Bio Sencha Hisui" with "15,90 €/100 g",
   "Bio Uji Gyokuro Gokō" with a small chip "im Besitz", and "Matcha Horai"
   with a small chip "Rezept prüfen". Navigation "Sammlung" selected.
6. TEA DETAIL: hero photo of a ceramic cup of pale green tea; serif title
   "Bio Sencha Hisui", subtitle "煎茶", "★ 4,5 (12)", a chip "Sencha"; a row of
   three stats with line icons: "65 °C Temperatur", "1:00 Ziehzeit",
   "3 Aufgüsse"; a section label "VORRAT & KOSTEN" with "62 g übrig" and
   "0,64 € pro Tasse"; a dark-green round edit button with a pencil.
7. JOURNAL: serif title "Journal"; search field; day header "HEUTE"; four
   rows with round tea photos and serif names (Gyokuro, Sencha, Matcha,
   Kabusecha), each with a small meta line and a star rating on some.
   Navigation "Journal" selected.
8. STATISTIK: serif title "Deine Tee-Reise"; tabs "Überblick",
   "Bewertungen", "Verbrauch" (Verbrauch selected); a big number "38 g" with
   "diesen Monat"; a minimal bar chart of grams per month in muted greens;
   a donut chart of tea types with a legend (Sencha, Gyokuro, Matcha,
   Hōjicha). Navigation "Statistik" selected.

QUALITY
Photorealistic presentation, crisp UI, consistent spacing and alignment,
legible text, no lorem ipsum, no English UI words, no brand logos other than
UmamiT.
```

## 2. Single-screen prompts (higher detail)

Use these when the board is too small to judge a screen. Prefix each one with:

```text
Single Android phone screen (Samsung Galaxy, portrait 9:19.5), app "UmamiT",
same style as before: washi off-white #F3F0E9, deep matcha green #4A5643,
cream pills, serif titles (Cormorant Garamond style), thin line icons, all UI
text in German, Android status bar "9:41". Present the phone on a washi
paper background with a soft ink-wash landscape.
```

…then add one of the screen descriptions 1–8 from above.

Additional single screens worth generating:

- **Einrichtung (first start):** title „Damit der Timer zuverlässig klingelt";
  three rows with icons and green check marks: „Benachrichtigungen",
  „Akku: Uneingeschränkt", „Live-Benachrichtigungen"; button „Weiter".
- **Timer alert (dark):** pulsing full ring, „Jetzt abgießen!" in serif,
  „+0:07" in small text, a large cream pill „Abgegossen".
- **„Gespeichert" sheet:** bottom sheet over the journal: „Gespeichert im
  Journal · 3 Aufgüsse · 2:20" with two text buttons „Notizen hinzufügen" and
  „Zeiten ins Rezept übernehmen".
- **Brühen (setup):** tea name, recipe chips „Kyusu" and „Kaltaufguss",
  rows „Gefäß Houhin 120 ml", „Wasser 100 ml", „Blätter 4,0 g", kettle hint
  „Wasserkocher auf 55 °C", a steps list „1 Aufguss 1:30 · 55 °C",
  „2 Aufguss 0:30 · 60 °C", „3 Aufguss 0:20 · 60 °C", „Vorwarnung 10 s",
  primary button „Brühen starten".

## 3. Tips

- If text comes out garbled, ask: "Keep the exact layout but fix the text
  rendering; use exactly these words: …".
- To get consistent screens, generate them in one conversation and say "same
  style as the previous image".
- Ask for "no device frame shadow clutter, flat front view" if the phones
  look too skewed to judge the UI.
