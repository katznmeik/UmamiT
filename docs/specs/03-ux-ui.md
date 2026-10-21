# 03 · UX & UI Specification

> Status: **Draft v0.1** · Last updated: 2026-10-21

This document defines the information architecture, navigation, visual design
system and every screen of UmamiTea. Functional rules live in
[02 · Functional Requirements](02-functional-requirements.md); this document
references them by ID.

## 1. Design reference — brand mockup

The product owner supplied a brand mockup (October 2026) that defines the
**look and feel**. The screens in it are *illustrative*: they do not define the
feature set. Store a copy at `docs/design/brand-mockup.png` (to be added by the
product owner) so designers and developers share the same reference.

What the mockup establishes and this spec adopts:

| Element | In the mockup | Adopted as |
|---|---|---|
| Mood | Calm, quiet, premium, Japanese tea-house; lots of whitespace | Product principle "Calm over clever" |
| Background | Warm off-white "washi paper" with subtle texture | `background` / `surface` tokens (§3.1) |
| Primary colour | Deep matcha / olive green (*Start Steeping* card, FAB) | `primary` token |
| Dark surface | Deep forest-green near-black (timer screen) | Timer screen always uses the dark "Steeping" scheme |
| Display type | Elegant serif for titles and tea names ("UmamiTea", "Journal", "Sencha Asamushi") | Serif display font (§3.2) |
| Labels | Small, letter-spaced uppercase labels ("RECENTLY LOGGED", "TASTING NOTES") | `labelOverline` style |
| Body type | Small, quiet sans-serif in warm grey | Sans body font |
| Imagery | Sumi-e / ink-wash mountain landscapes, tea leaves, moody tea-cup photography | Bundled illustrations & style photos (§3.5) |
| Logo | Two-leaf tea sprig, dark green | App icon & splash |
| Japanese | Secondary names (煎茶 浅蒸し), tagline お茶で、いい時間を。 | GEN-11, onboarding |
| Timer | Large thin ring, centred light digits, "Steeping" caption, pill-shaped cream *Pause* button | SCR-05 |
| Lists | Round photo thumbnails, serif title, "Jun 8, 2025 · 3 min · 80 °C", "★ 4.5" right-aligned | Journal & Home lists |
| Tea detail | Hero photo, serif name, Japanese subtitle, rating with count, category chip, 3-column stat row (Temperature · Steep time · Infusions) with line icons | SCR-09 |
| Stats | "Your Tea Journey" with tabs, big numerals, rating bars, donut of tea types | SCR-18 |
| Bottom navigation | Four items with thin line icons and small labels: Home, Journal, Collection, Stats | §2.2 (see note on Stats vs Learn) |
| Edit affordance | Small round dark-green FAB with pencil | Detail screens |

## 2. Information architecture & navigation

### 2.1 Site map

```
App
├── Onboarding (SCR-01)  — first launch only
├── Home (SCR-02)
│   ├── Start steeping → Tea picker (SCR-03) → Brew setup (SCR-04) → Timer (SCR-05) → Summary (SCR-06)
│   ├── Brew again (recent teas) → Brew setup
│   ├── Quick timer (SCR-07)
│   └── Settings (SCR-25) — top-bar action
├── Collection (SCR-08)
│   ├── Tea detail (SCR-09)
│   │   ├── Tea editor (SCR-10)
│   │   ├── Profile editor (SCR-12)
│   │   ├── Stock history (SCR-29)
│   │   └── Photo viewer (SCR-28)
│   └── Add tea → Style picker (SCR-11) → Tea editor (SCR-10)
├── Journal (SCR-15)
│   ├── Session detail (SCR-16) → Session editor (SCR-17)
│   ├── Log a session → Session editor (SCR-17)
│   └── Your Tea Journey (SCR-18)
├── Learn (SCR-19)
│   ├── Guide (SCR-20)
│   ├── Tea style detail (SCR-21)
│   └── Tools: Ratio calculator (SCR-22), Cooling helper (SCR-23), Unit converter (SCR-24)
└── Settings (SCR-25)
    ├── My teaware (SCR-13) → Teaware editor (SCR-14)
    ├── Backup & restore (SCR-26)
    └── About & licences (SCR-27)
```

### 2.2 Primary navigation

Four top-level destinations, shown as a **bottom navigation bar** on compact
widths and a **navigation rail** on medium/expanded widths (Material 3
`NavigationSuiteScaffold`):

| # | Label | Icon (line style) | Destination |
|---|---|---|---|
| 1 | **Home** | house | SCR-02 |
| 2 | **Journal** | notebook | SCR-15 |
| 3 | **Collection** | tea canister | SCR-08 |
| 4 | **Learn** | open book / leaf | SCR-19 |

> **Note:** the mockup shows *Stats* as the 4th item. Because Learn (guides,
> styles, tools) is central to the umami/brewing mission, this spec places
> statistics ("Your Tea Journey") inside Journal (header button and tab) and
> keeps Learn as a top-level item. This is open question
> [Q-07](10-open-questions-and-decisions.md#open-questions).

Rules:
- Re-selecting the current tab scrolls to top, then pops to the tab root.
- Each tab keeps its own back stack; switching tabs preserves state.
- Settings is reached from the top-app-bar action on Home (and from each tab's
  overflow menu).
- The **active brew bar** (GEN-06) sits directly above the navigation bar.
- The timer (SCR-05) is full-screen: no navigation bar, no active brew bar.

### 2.3 Back behaviour

- System back / predictive back pops the current tab's stack; on a tab root it
  goes to Home; on Home it exits the app.
- Back from the timer returns to the previous screen; the session keeps running.
- Back from the session summary saves (data is already persisted, JRN-01).
- Editors with unsaved changes trigger GEN-08.

### 2.4 Deep links (internal only)

| Source | Target |
|---|---|
| Timer notification / alert notification | SCR-05 |
| Auto-finished session notification | SCR-16 for that session |
| Reminder notification | SCR-08 filtered (stock) or SCR-26 (backup) |
| App shortcut "Quick timer" | SCR-07 |
| Dynamic shortcut "Brew <tea>" | SCR-04 for that tea |

Deep links use an internal scheme and are not exported to other apps except
via `PendingIntent`s and shortcuts.

### 2.5 Adaptive layouts

| Width class | Layout |
|---|---|
| Compact (< 600 dp) | Bottom bar; single pane |
| Medium (600–839 dp) | Navigation rail; Collection and Journal use list-detail with detail pane when landscape |
| Expanded (≥ 840 dp) | Navigation rail; list-detail panes for Collection and Journal; Learn shows list + article side by side |

- The app supports portrait and landscape on all devices, and does not lock
  orientation (Android 16 ignores orientation locks on large screens anyway).
- Timer landscape layout: ring on the left, controls on the right.
- Foldable tabletop posture: TMR-25.

## 3. Visual design system

### 3.1 Colour tokens

Values are first-pass approximations of the mockup and are to be finalized by
a designer (open question [Q-04](10-open-questions-and-decisions.md#open-questions)).
All pairs must meet the contrast targets in [07 §4](07-non-functional.md#4-accessibility).

**Light scheme ("Washi")**

| Token | Hex | Usage |
|---|---|---|
| `primary` | `#4A5643` | Primary buttons, *Start steeping* card, FAB, selected nav |
| `onPrimary` | `#F6F3EC` | Text/icons on primary |
| `primaryContainer` | `#DDE2D2` | Selected chips, subtle highlights |
| `onPrimaryContainer` | `#1F271B` | |
| `secondary` | `#8A7B5E` | Hojicha-brown accents, secondary icons |
| `secondaryContainer` | `#ECE5D6` | Cream pills (e.g. *Pause* on dark), tags |
| `tertiary` | `#6F8A5A` | Fresh-leaf green accents (umami meter, charts) |
| `background` | `#F3F0E9` | App background (washi) |
| `surface` | `#F7F5F0` | Cards, sheets |
| `surfaceContainerLow` | `#EFEBE3` | Input fields, search bar |
| `surfaceContainerHigh` | `#E7E2D8` | Chips |
| `onSurface` | `#2B2D27` | Primary text (ink) |
| `onSurfaceVariant` | `#6B6A62` | Secondary text, captions (≈ 4.8:1 on `background`; the lighter grey in the mockup, ≈ `#77766D`, only reaches ≈ 4.0:1 and fails AA for body text) |
| `outline` | `#D9D4C8` | Dividers, field outlines |
| `error` | `#9E3B2F` | Errors (used sparingly) |

**Dark scheme ("Steeping")** — also used for the timer screen in both themes.

| Token | Hex | Usage |
|---|---|---|
| `background` | `#1C231D` | Deep forest green-black |
| `surface` | `#232B24` | Cards |
| `surfaceContainerHigh` | `#2E372F` | Chips, inputs |
| `primary` | `#B9C8A6` | Ring progress, selected elements |
| `onPrimary` | `#1F271B` | |
| `secondaryContainer` | `#E5DFCF` | Cream *Pause* pill |
| `onSecondaryContainer` | `#2B2D27` | |
| `onSurface` | `#ECE8DE` | Primary text |
| `onSurfaceVariant` | `#A9A99D` | Secondary text |
| `outline` | `#4A544A` | Ring track, dividers |

**Category accent colours** (chips, chart series, fallback thumbnails; always
paired with a label/icon, never colour alone):
Green `#7C9A5B`, Matcha/powder `#5F8C3E`, White `#C9C2A8`, Yellow `#C7B45A`,
Oolong `#A88A4E`, Black `#8C4F35`, Dark `#5B4636`, Herbal `#9A7A9A`,
Other `#8C8C80`.

**Liquor colours** for the timer's "tea fill" animation by category, e.g.
green `#C9D58F`, gyokuro `#D7DF9F`, black `#B5672F`.

Dynamic colour (Material You) is **off by default** to preserve the brand
(SET-06).

### 3.2 Typography

| Role | Font | Size / line height | Notes |
|---|---|---|---|
| Display (splash, timer digits) | Serif display | 57 / 64 sp; timer 64–96 sp | Timer digits use tabular (monospaced) figures; light weight on dark |
| Headline (screen titles "Journal") | Serif display | 28 / 36 sp | Regular weight |
| Title (tea names) | Serif display | 20–22 / 28 sp | |
| Body | Sans | 14–16 / 20–24 sp | Warm grey for secondary text |
| Label | Sans | 12–14 / 16–20 sp | Buttons, chips |
| Overline (`labelOverline`) | Sans, uppercase | 11 / 16 sp, letter-spacing 0.12 em | "RECENTLY LOGGED", "TASTING NOTES" |

- **Serif display font:** a bundled OFL-licensed serif matching the mockup,
  candidates *Cormorant Garamond*, *EB Garamond* or *Noto Serif Display*
  (decision Q-05). Only the needed weights (Regular, Medium) are bundled,
  subsetted to Latin + Latin Extended.
- **Sans font:** system default (Roboto / device font) — no bundling.
- **Japanese text** uses the system CJK fallback font (no bundled CJK font, to
  keep APK size small).
- All sizes in `sp`; layouts must reflow at 200 % font scale.

### 3.3 Shape, spacing, elevation

- Spacing scale: 4, 8, 12, 16, 24, 32, 48 dp. Screen side margin 20 dp
  (compact), 24 dp (medium+).
- Corner radii: cards 16 dp, primary card (*Start steeping*) 12 dp, chips 8 dp,
  pills/buttons fully rounded, thumbnails circular (journal/home) or 12 dp
  (collection grid).
- Elevation: flat design; cards use `surface` on `background` with a 1 dp
  `outline` hairline or tonal difference instead of shadows.
- Dividers: hairline `outline`, inset to text start, as in mockup lists.

### 3.4 Iconography

- Material Symbols **Outlined / Light (weight 300)** to match the thin line icons.
- Custom vector icons (same stroke weight) for: tea leaf logo, kyusu, houhin,
  shiboridashi, gaiwan, chawan, chasen (whisk), cold-brew bottle, thermometer,
  leaf amount, infusion count, umami.
- Icons imported as individual vector drawables (no full icon font).

### 3.5 Imagery & illustration

- **Ink-wash landscapes** (mountains, mist, tea fields): Home header, onboarding,
  empty states, Learn hero images. Format: optimized WebP or vector; light and
  dark variants (dark variant inverted/tinted to fit the Steeping scheme).
- **Tea photography**: one moody photo per style (cup of liquor, top-down leaf
  shot) used as fallback hero/thumbnail when a tea has no user photo. Bundled
  WebP ≈ 40–80 KB each, ≤ 2.5 MB total.
- All imagery must be original or properly licensed for commercial
  redistribution (open question Q-04/Q-06). AI-generated imagery must have its
  licence/terms checked before use.
- Decorative images have no content description; meaningful images do.

### 3.6 Motion

- Standard Material 3 motion; shared-axis transitions between tabs' screens,
  container transform from list items to detail.
- Timer ring progresses smoothly (frame-based), not in 1 s jumps.
- Optional "tea fill" animation: the ring's interior slowly fills with the
  liquor colour during a step.
- *Alerting* state: a slow (2 s) pulse of the ring; no flashing (≤ 3 flashes/s
  rule, WCAG 2.3.1).
- All non-essential motion is disabled when the system "Remove animations"
  setting is on.

### 3.7 Haptics

- Light tick on step start/pause; a distinct pattern on alert (in addition to
  vibration settings); confirm haptic on *Poured*.

### 3.8 Voice & tone

- Calm, warm, concise, encouraging. Second person, sentence case.
- Japanese terms are used where natural and explained once (e.g. "Yuzamashi —
  cooling water by pouring it between vessels").
- No exclamation marks except "Pour now!" in the alert. No guilt ("You broke
  your streak").
- Examples: Home greeting "Good tea brings a moment of calm." Empty journal:
  "Your first cup is waiting."

### 3.9 Core components

| Component | Description |
|---|---|
| `TeaListItem` | Circular 48 dp thumbnail, serif title, meta line ("Jun 8 · 3 min · 80 °C"), trailing rating "★ 4.5", hairline divider |
| `PrimaryActionCard` | Dark-green card with leaf icon, title + subtitle, chevron (*Start steeping*) |
| `StatTriplet` | Three equal columns with line icon, value, caption (Temperature / Steep time / Infusions) |
| `SectionHeader` | Overline label + optional "See all" text button |
| `TimerRing` | Thin (3 dp) ring, round cap, track in `outline`, progress in `primary`, leaf glyph at top, digits + caption inside |
| `PillButton` | Fully rounded, cream on dark or green on light |
| `RatingStars` | Half-star input, 48 dp touch targets, accessible adjustable semantics |
| `TasteSlider` | 0–5 discrete slider with label and value |
| `TasteRadar` | Hexagonal radar (6 axes), with table alternative for accessibility |
| `DescriptorChip` | Filter chip with group colour dot |
| `UmamiMeter` | 5 leaf glyphs filled per umami level |
| `StockChip`, `FreshnessChip` | Quiet chips: "42 g left", "Low · 8 g", "Opened 21 d ago · Enjoy soon" |
| `ActiveBrewBar` | 56 dp bar: mini ring, tea name, step, remaining time |
| `EmptyState` | Ink-wash illustration, serif title, body, primary button |

## 4. Screen specifications

Each screen lists: purpose, entry points, layout & content, actions, states,
and related requirements. Wireframes are indicative only.

### SCR-01 Onboarding

- **Purpose:** welcome, units, notifications, starting content (ONB-01…06).
- **Pages:**
  1. *Welcome* — ink-wash landscape (full-bleed), leaf logo, "UmamiTea", "BETTER
     TEA. DEEPER MOMENTS.", お茶で、いい時間を。, *Get started*.
  2. *Your units* — three segmented buttons (°C/°F, g/oz, ml/fl oz) pre-selected
     from locale; *Continue*; *Skip*.
  3. *Alerts* (Android 13+ only) — illustration of a phone ringing beside a
     kyusu, explanation, *Allow notifications* → system dialog; *Not now*.
  4. *Start* — two selectable cards: *Start with sample teas* (default) / *Start
     empty*; *Begin*.
- **States:** page indicator dots; back moves to the previous page.

### SCR-02 Home

```
┌──────────────────────────────┐
│ UmamiTea                 ⚙   │  serif title, settings action
│  [ink-wash mountains ……… ]   │  header illustration (~180 dp)
│  Good tea                    │  serif greeting over illustration
│  brings a moment of calm.    │
├──────────────────────────────┤
│ ┌──────────────────────────┐ │
│ │ 🍃 Start steeping      › │ │  PrimaryActionCard → SCR-03
│ │    Choose a tea and brew │ │
│ └──────────────────────────┘ │
│ ⏱ Quick timer                │  text button
│ BREW AGAIN           See all │  horizontal cards of recent teas
│ (○ Gyokuro) (○ Sencha) (○ …) │  tap → SCR-04
│ RECENTLY LOGGED      See all │
│ (○) Sencha Asamushi    ★ 4.5 │  last 3 sessions → SCR-16
│     Jun 8 · 3 min · 80 °C    │
│ FAVOURITES                   │
│ (○) Kabusecha  (○) Matcha    │
└──────────────────────────────┘
│ ▣ Home  ▢ Journal ▢ Collection ▢ Learn │
```

- **Greeting** rotates among a few calm lines by time of day ("Good morning",
  "A quiet afternoon cup?") — no user name (no accounts).
- **Nudges** (Should): at most one card, e.g. "Your shincha is best enjoyed
  soon" or "Back up your journal" — dismissible.
- **Empty state** (no teas): illustration, "Your first cup is waiting", *Add a
  tea*, *Explore tea styles*, *Quick timer*.
- **Active session:** the *Start steeping* card is replaced by a *Resume
  steeping* card showing the live countdown.
- Requirements: BRW-01, GEN-06, TEA-06, JRN-08, LRN-07.

### SCR-03 Tea picker (bottom sheet)

- Modal bottom sheet, full height when expanded. Search field, then sections:
  *Recent*, *Favourites*, *All teas* (A–Z, non-archived). Each row shows stock
  chip if tracked.
- Footer: *Add a new tea* (→ SCR-11) and *Brew without a tea* (→ quick timer).
- Selecting a tea → SCR-04.

### SCR-04 Brew setup

```
┌──────────────────────────────┐
│ ←  Brew                       │
│ Gyokuro Yame Premium          │ serif
│ 玉露 · ★ 4.6 · 38 g left       │
│ [Houhin✓] [Ice brew] [Cold]  ✎│ profile chips + edit
│ Vessel   Houhin 120 ml      ▾ │
│ Water    60 ml        [−][+]  │
│ Leaf     6.0 g  ≈2 tsp [−][+] │  🔒 ratio 10 g/100 ml · 1:10
│ Water temp  50 °C             │
│   From boiling: ~5 transfers ›│ cooling hint (BRW-05)
│ STEPS                     Edit│
│ 1  Infusion  2:00   50 °C     │
│ 2  Infusion  0:20   55 °C     │
│ 3  Infusion  1:00   65 °C     │
│ ▸ Before you start (3)        │ (Could)
│ [ Save to profile ]           │ visible only when changed
│ [      Start brewing       ]  │ primary
└──────────────────────────────┘
```

- Numeric fields support direct entry (tap value) and steppers (long-press
  repeats).
- If another session is active → TMR-21 dialog on *Start brewing*.
- Requirements: BRW-02…09, PRF-03/04.

### SCR-05 Brew timer

Always uses the dark "Steeping" scheme, edge-to-edge, status bar icons light.

```
┌──────────────────────────────┐
│ ←                         ⋮  │
│   [moody tea photo/illus.]   │ top ~35 %, fades into background
│      Sencha Asamushi         │ serif
│      80 °C · Infusion 2 of 4 │
│     ●  ●  ◉  ○  ○            │ step dots (rinse = small hollow square)
│        ╭────────╮            │
│       │   🍃    │            │
│       │  02:48  │            │ tabular serif/sans digits
│       │ Steeping│            │
│        ╰────────╯            │
│   [−10 s]  [ ⏸ Pause ]  [+10 s]│
│          Pour now            │ text button (TMR-05)
└──────────────────────────────┘
```

| State | Ring | Caption | Primary control | Secondary |
|---|---|---|---|---|
| READY | empty track | "Pour water at 55 °C, then start" | **Start infusion 2** (whole ring also tappable) | Skip step (overflow) |
| RUNNING | progress | "Steeping" | **Pause** | −10 s, +10 s, Pour now |
| PAUSED | frozen, dimmed | "Paused" | **Resume** | Pour now |
| ALERTING | full, pulsing | "Pour now!" + overtime "+0:07" | **Poured** (or *Discarded* for rinse) | — |
| FINISHED | full | "All infusions done" | **Finish & log** | Add another infusion |

- Overflow menu: Edit steps, Skip step, Add infusion, Finish session, Discard
  session, Timer settings.
- Warning banner (TMR-31) under the top bar when notifications are blocked.
- TalkBack: the time is announced on state changes, at 10 s remaining, and at
  the end — not every second (see [07 §4](07-non-functional.md#4-accessibility)).

### SCR-06 Session summary & tasting

- Header: tea name, date/time, "3 infusions · 4 min 30 s total".
- Steps table: #, planned, actual, temperature (actual > planned highlighted
  neutrally, e.g. "+0:12").
- *How was it?* — RatingStars (JRN-02).
- *Taste* — six TasteSliders (collapsed into an expandable card if empty),
  live TasteRadar preview.
- *Notes* — descriptor chips (opens SCR-30), notes field, photos row, water.
- Contextual cards: *Save today's times to this profile* (PRF-07); brew coach
  tip (JRN-14).
- Bottom bar: *Skip* (text) and *Done* (primary).

### SCR-07 Quick timer

- Dark scheme like SCR-05. Preset chips row, large time display; tapping the
  time opens a duration picker (h/m/s wheels). *Start* → same states as SCR-05
  except FINISHED (returns to idle after *Done*).

### SCR-08 Collection

```
┌──────────────────────────────┐
│ Collection              ⋮    │
│ [🔍 Search your teas…      ] │
│ [♡ Favourites][Category ▾][Stock ▾][Tags ▾] ⇅ │
│ (○) Gyokuro Yame Premium   ♡ │
│     玉露 · Hoshinomura · ★4.6 │
│     [38 g left] [Opened 12 d] │
│ ───────────────────────────── │
│ (○) Kabusecha Saemidori       │
│ …                             │
│                         (＋)  │ FAB: Add tea
└──────────────────────────────┘
```

- Requirements: TEA-08…12, ONB-05 sample card.
- Empty state: "Your collection is empty" + *Add a tea* + *Browse tea styles*.
- No results: "No teas match" + *Clear filters*.

### SCR-09 Tea detail

Matches the mockup's tea detail.

- Collapsing hero photo (user cover → style photo → illustration); back and
  overflow over the image with scrim for contrast.
- Serif name, secondary name, rating "★ 4.5 (12)", category chip, umami meter.
- `StatTriplet` for the default profile: Temperature · Steep time (first
  infusion) · Infusions.
- Primary *Brew* button (sticky at bottom on scroll) and FAB-pencil for edit.
- Sections (overline headers): BREWING PROFILES, STOCK & FRESHNESS, TASTING
  NOTES (average radar + top descriptors), RECENT SESSIONS, NOTES, DETAILS
  (origin, cultivar, harvest, shading, vendor, purchase), TAGS.
- Overflow: Favourite, Duplicate (Could), Archive, Delete.

### SCR-10 Tea editor

- Full-screen dialog style: close (✕), title "Add tea"/"Edit tea", *Save* text
  action (as in the mockup).
- Photo drop zone at top ("Add a photo", dashed outline).
- Sections as TEA-02, with collapsed optional sections.
- Inline validation; *Save* disabled until name is valid.

### SCR-11 Style picker

- Search, category tabs (All, Green, White, Yellow, Oolong, Black, Dark,
  Herbal), list rows with style photo, name, secondary name, umami meter.
- *Other / not sure* sticky at top.

### SCR-12 Profile editor

- Fields per PRF-03; steps list with drag handles; *Generate infusions* sheet
  (PRF-05); *Reset to style defaults* in overflow; *Set as default* switch.

### SCR-13 / SCR-14 Teaware list & editor

- List with type icon, name, capacity; editor with name, type grid (icons),
  capacity, material, notes, photos.

### SCR-15 Journal

Matches the mockup's journal.

- Serif title "Journal", actions: *Your Tea Journey* (chart icon), filter icon.
- Search bar "Search your journal…".
- Day-grouped list of `TeaListItem`s; IN_PROGRESS session pinned at top (live).
- FAB *Log a session* (JRN-12).
- Empty state: "Your first cup is waiting" + *Start steeping*.

### SCR-16 Session detail

- Hero (session photo or tea photo), tea name (link to tea), date/time, method,
  vessel, leaf/water/ratio, steps table, rating, radar, descriptors, notes,
  water, photos.
- Actions: *Brew this again*, edit (pencil FAB), overflow *Delete*, *Share*
  (Could).

### SCR-17 Session editor

- Same fields as SCR-06 plus date/time, tea selection (manual only), method,
  leaf, water, temperature and steps (manual entries may give only an infusion
  count).

### SCR-18 Your Tea Journey (Insights)

Matches the mockup's stats screen.

- Serif title "Your Tea Journey"; tabs **Overview** · **Ratings** · **Tasting**.
- Overview: Total teas (big numeral, "+3 this month"), sessions this week/month,
  streak, activity bar chart, tea types donut + legend with percentages.
- Ratings: average rating, distribution bars, top-rated teas.
- Tasting: "What you love" radar, top descriptors, umami preference by style.
- Range selector (INS-07) in the top bar.
- Every chart has a "View as table" accessible alternative.

### SCR-19 Learn

- Sections: GUIDES (cards with ink-wash hero, title, reading time), TEA STYLES
  (search + category chips → list), TOOLS (Ratio calculator, Cooling helper,
  Unit converter).

### SCR-20 Guide (article)

- Hero illustration, serif title, reading time, rich text body (max line length
  ~65 characters on large screens), related styles/guides at the end.

### SCR-21 Tea style detail

- Style photo, name, secondary name, category, origin, umami meter,
  description, default profiles (readable, e.g. "Houhin · 6 g / 60 ml · 50 °C ·
  2:00, 0:20, 1:00, 1:30"), freshness advice, related guides, *Add to my
  collection*.

### SCR-22 Ratio calculator, SCR-23 Cooling helper, SCR-24 Unit converter

- Single-screen tools per LRN-04…06. Cooling helper shows a vertical sequence
  of vessels with the temperature after each transfer, e.g. 100 → 90 → 80 → 70
  → 60 → 50 °C.

### SCR-25 Settings

- Grouped list per SET-30; each setting shows its current value as supporting
  text. Version number at the bottom.

### SCR-26 Backup & restore

- Last backup date; *Back up now*; *Back up & share* (Could); *Restore from
  file*; *Restore safety backup* (if present); *Export CSV*; explanation of OS
  auto-backup; backup reminder setting.
- Restore flow: pick file → validating (progress) → preview card → confirm
  dialog ("This replaces all your teas, journal and settings") → progress →
  success → app restarts its UI at Home.

### SCR-27 About & licences

- Logo, version, tagline, privacy statement ("UmamiTea has no internet access.
  Your data never leaves your device unless you export it."), privacy policy
  (bundled text), open-source licences, *Send feedback* (opens e-mail app),
  *Rate on Google Play*, *Show introduction*, content credits (photos,
  illustrations, fonts).

### SCR-28 Photo viewer

- Full-screen pager, pinch-zoom, delete, set as cover, share (system share).

### SCR-29 Stock history (sheet)

- Remaining amount, *Add purchase*, *Set remaining*, list of events (date, type,
  ±g, linked session).

### SCR-30 Descriptor picker (sheet)

- Search, grouped chips, *Add custom descriptor*, selected count; *Done*.

## 5. Dialogs & messages

| Situation | Type | Copy (draft) |
|---|---|---|
| Unsaved changes | Dialog | "Discard changes?" · Keep editing / Discard |
| Delete tea with sessions | Dialog | "Delete *Gyokuro*? Your 12 journal entries stay, but profiles and photos are removed." · Archive instead / Delete |
| Another session active | Dialog | "Finish your *Sencha* session first?" · Finish & start new / Cancel |
| Discard session | Dialog | "Discard this session? Nothing will be saved." · Discard / Keep brewing |
| Restore | Dialog | "Replace everything with this backup from 3 Oct 2026? Your current data is saved as a safety backup first." · Restore / Cancel |
| Deleted item | Snackbar | "Tea deleted" · Undo |
| Auto-finished session | Notification | "Your Gyokuro session was saved to your journal." |
| Notifications blocked | Banner | "Alerts won't show while UmamiTea is closed." · Fix |

## 6. Accessibility in the UI

Summary (full requirements in [07 §4](07-non-functional.md#4-accessibility)):

- Touch targets ≥ 48 × 48 dp; timer primary button ≥ 72 dp tall.
- Text contrast ≥ 4.5:1 (body) and ≥ 3:1 (large text, UI components) in both
  schemes — verify warm-grey secondary text on washi background.
- Every icon-only button has a content description; decorative images none.
- Charts have table alternatives; radar values are readable via TalkBack.
- Custom controls (rating, sliders, ring) expose correct semantics and actions.
- Layouts tested at 200 % font size and with display size "Largest".
