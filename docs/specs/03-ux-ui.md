# 03 · UX & UI Specification

> Status: **Draft v0.2** · Last updated: 2026-10-21

Information architecture, navigation, visual design system and screens.
Functional rules: [02](02-functional-requirements.md). The UI language is
**German**; copy examples below are the intended German strings.

## 1. Design reference — brand mockup

The PO's brand mockup defines the **look and feel**. Its screens are
*illustrative*, not a feature list. It is stored at
[`docs/design/brand-mockup.png`](../design/brand-mockup.png).

![UmamiTea brand mockup](../design/brand-mockup.png)

| Element | In the mockup | Adopted as |
|---|---|---|
| Mood | Calm, quiet, premium, Japanese tea house; generous whitespace | Principle "Calm over clever" |
| Background | Warm off-white "washi paper" | `background`/`surface` tokens (§3.1) |
| Primary colour | Deep matcha/olive green (*Start Steeping* card, FAB) | `primary` |
| Dark surface | Deep forest green-black (timer) | Timer always uses the dark "Steeping" scheme |
| Display type | Elegant serif for titles and tea names | Serif display font (§3.2) |
| Labels | Small, letter-spaced uppercase labels („ZULETZT GEBRÜHT") | `labelOverline` |
| Imagery | Ink-wash mountains, tea leaves, moody tea photography | Illustrations + Tee-Kontor product photos |
| Logo | Two-leaf tea sprig | App icon & splash |
| Japanese | Secondary names (煎茶 浅蒸し), tagline お茶で、いい時間を。 | GEN-11, welcome |
| Timer | Thin ring, light centred digits, caption, cream pill *Pause* | SCR-05 |
| Lists | Round photo thumbnails, serif title, meta line, „★ 4,5" | Journal & Home |
| Tea detail | Hero photo, serif name, subtitle, rating with count, type chip, Temperature · Steep time · Infusions row | SCR-09 / SCR-21 |
| Stats | "Your Tea Journey" with tabs, big numerals, rating bars, tea-type donut | SCR-18 |
| Bottom nav | **Home · Journal · Collection · Stats** with thin line icons | §2.2 (German labels) |

## 2. Information architecture & navigation

### 2.1 Site map

```
App
├── Einrichtung (SCR-01) — first start only
├── Start / Home (SCR-02)
│   ├── Aufguss starten → Teeauswahl (SCR-03) → Brühen (SCR-04) → Timer (SCR-05) → Gespeichert-Sheet (SCR-06)
│   ├── Nochmal brühen (recent teas) → SCR-04
│   ├── Schnelltimer (SCR-07)
│   └── Einstellungen (SCR-25) — top-bar action
├── Journal (SCR-15)
│   └── Sitzung (SCR-16) → Sitzung bearbeiten / Notizen (SCR-17) → Beschreibungen (SCR-30)
├── Sammlung (Collection) — segmented: [ Meine Tees | Katalog ]
│   ├── Meine Tees (SCR-08)
│   │   ├── Tee (SCR-09) → Bearbeiten (SCR-10), Rezept (SCR-12), Vorrat (SCR-29), Fotos (SCR-28)
│   │   └── Tee ohne Katalog anlegen → Sorte wählen (SCR-11) → SCR-10
│   └── Katalog (SCR-19)
│       ├── Katalog-Tee (SCR-21) → In Sammlung aufnehmen (SCR-23)
│       ├── Ratgeber (Tee-Info guides) → Artikel (SCR-20)
│       └── Katalog aktualisieren (SCR-22)
├── Statistik (SCR-18)
└── Einstellungen (SCR-25)
    ├── Teegeschirr (SCR-13) → SCR-14
    ├── Daten & Sicherung (SCR-26)
    └── Über (SCR-27)
```

### 2.2 Primary navigation

Bottom navigation bar (navigation rail on wide screens):

| # | Label | Icon (line) | Destination |
|---|---|---|---|
| 1 | **Start** | house | SCR-02 |
| 2 | **Journal** | notebook | SCR-15 |
| 3 | **Sammlung** | tea canister | SCR-08 / SCR-19 |
| 4 | **Statistik** | bar chart | SCR-18 |

- **Sammlung** opens on the last used segment (*Meine Tees* by default).
- Re-selecting a tab scrolls to top, then pops to its root; each tab keeps its
  back stack.
- Settings via the gear on Start and the ⋮ menu of the other tabs.
- The active brew bar (GEN-06) sits above the navigation bar; the timer is
  full-screen without either.

### 2.3 Back behaviour

Predictive back; back from the timer keeps the session running; editors with
unsaved changes trigger GEN-08.

### 2.4 Internal deep links

| Source | Target |
|---|---|
| Timer notification / Live Update / alert | SCR-05 |
| „Sitzung gespeichert" notification | SCR-16 |
| Catalog refresh result notification | SCR-19 |
| Reminder notification | SCR-08 (filtered) or SCR-26 |
| Reliability banner *Beheben* | System settings page (notifications, battery, Live Updates) |

### 2.5 Layout

Phone-first (Galaxy, portrait and landscape). On wider windows (landscape,
split screen, DeX) the app uses a navigation rail and list-detail panes for
Sammlung and Journal; no orientation lock.

## 3. Visual design system

### 3.1 Colour tokens

First-pass values approximated from the mockup, to be finalized with the PO.

**Light ("Washi")**

| Token | Hex | Usage |
|---|---|---|
| `primary` | `#4A5643` | Primary buttons, *Aufguss starten* card, FAB, selected nav |
| `onPrimary` | `#F6F3EC` | |
| `primaryContainer` | `#DDE2D2` | Selected chips |
| `onPrimaryContainer` | `#1F271B` | |
| `secondary` | `#8A7B5E` | Hōjicha-brown accents |
| `secondaryContainer` | `#ECE5D6` | Cream pills, tags |
| `tertiary` | `#6F8A5A` | Umami meter, charts |
| `background` | `#F3F0E9` | Washi background |
| `surface` | `#F7F5F0` | Cards, sheets |
| `surfaceContainerLow` | `#EFEBE3` | Inputs, search bar |
| `surfaceContainerHigh` | `#E7E2D8` | Chips |
| `onSurface` | `#2B2D27` | Primary text (ink) |
| `onSurfaceVariant` | `#6B6A62` | Secondary text (≈ 4.8:1 on background; the mockup's lighter grey fails AA) |
| `outline` | `#D9D4C8` | Hairlines |
| `error` | `#9E3B2F` | Errors (rare) |

**Dark ("Steeping")** — dark theme and the timer screen in both themes.

| Token | Hex | Usage |
|---|---|---|
| `background` | `#1C231D` | Forest green-black |
| `surface` | `#232B24` | Cards |
| `surfaceContainerHigh` | `#2E372F` | Chips, inputs |
| `primary` | `#B9C8A6` | Ring progress |
| `onPrimary` | `#1F271B` | |
| `secondaryContainer` | `#E5DFCF` | Cream *Pause* pill |
| `onSecondaryContainer` | `#2B2D27` | |
| `onSurface` | `#ECE8DE` | Text |
| `onSurfaceVariant` | `#A9A99D` | Secondary text |
| `outline` | `#4A544A` | Ring track |

**Type colours** (chips, chart series; always with a label): Gyokuro
`#5F7F45`, Kabusecha `#6C8A4F`, Sencha `#7C9A5B`, Fukamushi `#8AA463`,
Shincha `#9DB86E`, Matcha `#5F8C3E`, Kukicha/Karigane `#A6B07A`, Hōjicha
`#8C6A48`, Genmaicha `#B59A62`, China/Korea green `#8FA77A`, aromatisiert
`#9A7A9A`, other `#8C8C80`. Liquor colours for the timer fill animation:
green `#C9D58F`, gyokuro `#D7DF9F`, hōjicha `#B07A45`.

Dynamic colour is off by default (SET-06).

### 3.2 Typography

| Role | Font | Size / line height |
|---|---|---|
| Timer digits | Serif display or sans with tabular figures, light | 72–96 sp |
| Headline („Journal") | Serif display | 28 / 36 sp |
| Title (tea names) | Serif display | 20–22 / 28 sp |
| Body | System sans | 14–16 / 20–24 sp |
| Label | System sans | 12–14 sp |
| Overline | System sans, uppercase, +0.12 em tracking | 11 / 16 sp |

- Serif: bundled OFL font closest to the mockup (proposed **Cormorant
  Garamond**, Q-05), Regular + Medium, Latin subset incl. German umlauts/ß.
- Japanese text via the system CJK fallback font.
- Uppercase overlines use German `ẞ`-safe uppercasing (`ZULETZT GEBRÜHT`).

### 3.3 Shape, spacing, elevation

Spacing 4/8/12/16/24/32/48 dp; side margin 20 dp; cards 16 dp radius;
primary card 12 dp; pills fully rounded; circular thumbnails in lists; flat
design with hairlines instead of shadows.

### 3.4 Iconography

Material Symbols Outlined, weight 300; custom line icons for kyusu, houhin,
shiboridashi, gaiwan, chawan, chasen, cold-brew bottle, kettle, thermometer,
leaf amount, infusions, umami.

### 3.5 Imagery

- Ink-wash landscape illustrations (Home header, welcome, empty states),
  light and dark variants. Origin/licence to be settled with the PO (Q-04).
- Tea photos: Tee-Kontor product photos (downloaded by the catalog refresh,
  personal use) and the PO's own photos; fallback = type-coloured leaf
  illustration.

### 3.6 Motion & haptics

Material 3 motion; ring progresses per frame; optional liquor-fill
animation; *Alerting*: slow ring pulse (no flashing); honours "Remove
animations". Haptics: tick on start/pause, pre-alert tick, alert pattern,
confirm on *Abgegossen*.

### 3.7 Voice & tone (German)

Ruhig, warm, knapp; informal **„du"**; sentence case; Japanese terms in
romanisation with macrons (Kōridashi) where standard. Examples:
- Home: „Guter Tee schenkt einen Moment der Ruhe."
- Empty journal: „Deine erste Tasse wartet."
- Alert: „Jetzt abgießen!"
- Pre-alert caption: „Gleich fertig …"

### 3.8 Core components

| Component | Description |
|---|---|
| `TeaListItem` | 48 dp round thumbnail, serif title, meta line („8. Juni · 3 Min · 80 °C"), trailing „★ 4,5", hairline divider |
| `PrimaryActionCard` | Dark-green card, leaf icon, title + subtitle, chevron |
| `StatTriplet` | Temperatur · Ziehzeit · Aufgüsse with line icons |
| `SectionHeader` | Overline + optional „Alle anzeigen" |
| `TimerRing` | 3 dp ring, round cap, leaf glyph, digits + caption; pre-alert segment marked as a short tick on the ring |
| `PillButton` | Cream on dark / green on light |
| `KettleHint` | Kettle icon + „Wasserkocher auf 60 °C" |
| `RatingStars`, `TasteSlider`, `TasteRadar`, `DescriptorChip`, `UmamiMeter` | As named |
| `StockChip`, `FreshnessChip`, `CheckRecipeBadge` | „38 g übrig", „Bald genießen", „Rezept prüfen" |
| `CatalogFlagChips` | „Bio", „aromatisiert", „im Besitz", „nicht mehr im Sortiment" |
| `ActiveBrewBar` | 56 dp mini ring, tea name, step, remaining time |
| `ReliabilityBanner` | Quiet warning bar with *Beheben* |
| `EmptyState` | Illustration, serif title, body, button |

## 4. Screens

### SCR-01 Einrichtung (first start)

1. *Willkommen* — full-bleed ink-wash, logo, „UmamiTea", „BESSERER TEE.
   TIEFERE MOMENTE.", お茶で、いい時間を。, *Los geht's*.
2. *Damit der Timer zuverlässig klingelt* — checklist rows with status ✓/!:
   Benachrichtigungen erlauben · Akku: Uneingeschränkt · Live-Benachrichtigungen.
   Each row has an action button; *Weiter* always available.
3. *Katalog laden* — „Alle Grüntees von Tee-Kontor Kiel offline verfügbar
   machen" · *Katalog jetzt laden* (progress inline) / *Später*.

### SCR-02 Start (Home)

```
┌──────────────────────────────┐
│ UmamiTea                 ⚙   │
│  [ink-wash mountains ……… ]   │
│  Guter Tee schenkt einen     │
│  Moment der Ruhe.            │
│ ┌──────────────────────────┐ │
│ │ 🍃 Aufguss starten     › │ │  → SCR-03
│ │    Tee wählen und los    │ │
│ └──────────────────────────┘ │
│ ⏱ Schnelltimer               │
│ NOCHMAL BRÜHEN   Alle anzeigen│  recent teas, tap → SCR-04
│ (○ Gyokuro) (○ Sencha) (○ …) │
│ ZULETZT GEBRÜHT  Alle anzeigen│  last 3 sessions
│ (○) Sencha Asamushi    ★ 4,5 │
│     8. Juni · 3 Min · 80 °C  │
│ FAVORITEN                    │
└──────────────────────────────┘
│ Start · Journal · Sammlung · Statistik │
```

- Reliability banner (TMR-31) at top when needed.
- Active session replaces the card with *Aufguss fortsetzen* + live countdown.
- At most one nudge card (knapper Vorrat, Backup, Katalog nie geladen).
- Empty state: „Deine Sammlung ist leer" · *Katalog öffnen* · *Schnelltimer*.

### SCR-03 Teeauswahl (bottom sheet)

Search; sections *Zuletzt*, *Favoriten*, *Alle* (non-archived owned teas);
footer *Aus dem Katalog* (→ SCR-19) and *Ohne Tee (Schnelltimer)*.

### SCR-04 Brühen (setup)

```
┌──────────────────────────────┐
│ ←  Brühen                     │
│ Bio Uji Gyokuro Gokō          │ serif
│ 玉露 · ★ 4,6 · 38 g übrig      │
│ [Kyusu ✓] [Kaltaufguss]     ✎ │ recipe chips
│ Gefäß    Houhin 120 ml      ▾ │
│ Wasser   100 ml       [−][+]  │
│ Blätter  4,0 g        [−][+]  │ 🔒 4,0 g / 100 ml
│ 🫖 Wasserkocher auf 55 °C      │ KettleHint
│ AUFGÜSSE                Bearb.│
│ 1  Aufguss  1:30   55 °C      │
│ 2  Aufguss  0:30   60 °C      │
│ 3  Aufguss  0:20   60 °C      │
│ Vorwarnung  5 s (Standard)    │
│ [ Im Rezept speichern ]       │ only when changed
│ [      Brühen starten      ]  │
└──────────────────────────────┘
```

„Rezept prüfen" badge with reason above the steps if flagged.

### SCR-05 Timer

Always dark ("Steeping"), edge-to-edge, keeps the screen on.

```
┌──────────────────────────────┐
│ ←                         ⋮  │
│   [tea photo, fades out]     │
│      Bio Uji Gyokuro Gokō    │
│      55 °C · Aufguss 1 von 3 │
│        ●  ○  ○               │ step dots (rinse = small square)
│        ╭────────╮            │
│       │   🍃    │            │ tick mark on ring = pre-alert point
│       │  01:12  │            │
│       │ Zieht … │            │
│        ╰────────╯            │
│      [  ❙❙  Pause  ]         │ cream pill (only control)
└──────────────────────────────┘
```

| State | Ring | Caption | Primary control |
|---|---|---|---|
| READY | empty | KettleHint „Wasserkocher auf 60 °C" + „Aufguss 2 · 0:20" | **Start** (ring tappable too) |
| RUNNING | progress | „Zieht …" → „Gleich fertig …" after pre-alert | **Pause** |
| PAUSED | frozen, dimmed | „Pausiert" | **Weiter** |
| ALERTING | full, slow pulse | „Jetzt abgießen!" + overtime „+0:07" | **Abgegossen** (*Weggegossen* for rinse) |
| FINISHED | full | „Alle Aufgüsse fertig" | **Sitzung beenden**, secondary *Weiterer Aufguss* |

Overflow ⋮ items by state:

| Item | READY | RUNNING | PAUSED | ALERTING | FINISHED |
|---|---|---|---|---|---|
| Aufguss jetzt beenden | – | ✓ | ✓ | – | – |
| Schritt überspringen | ✓ | – | – | – | – |
| Schritte bearbeiten | ✓ | ✓ | ✓ | ✓ | ✓ |
| Weiterer Aufguss | ✓ | ✓ | ✓ | ✓ | ✓ (also as button) |
| Sitzung beenden | ✓ | ✓ | ✓ | ✓ | ✓ (also as button) |
| Sitzung verwerfen | ✓ | ✓ | ✓ | ✓ | ✓ |
| Timer-Einstellungen | ✓ | ✓ | ✓ | ✓ | ✓ |
TalkBack announcements: state changes, pre-alert, 10 s remaining, alert.

### SCR-06 „Gespeichert" sheet

Small bottom sheet after *Sitzung beenden*, auto-dismisses after 10 s:
„Gespeichert im Journal · 3 Aufgüsse · 2:20" with *Notizen hinzufügen* and (if
PRF-07 applies) *Zeiten ins Rezept übernehmen*.

### SCR-07 Schnelltimer

Dark like SCR-05; chips 0:30 … 5:00; tap time for a duration picker; *Start*;
while running *Pause*/*Weiter*; on alert *Fertig*; overflow *Abbrechen*
([04 §10](04-brew-timer.md#10-quick-timer)).

### SCR-08 Sammlung › Meine Tees

Segmented control [ **Meine Tees** | Katalog ] at the top; search
„Deine Tees durchsuchen …"; filter chips; sort; rows per TEA-08; FAB *Tee
hinzufügen* (→ choice: *Aus dem Katalog* / *Ohne Katalog*).
Empty: „Noch keine Tees · Im Katalog stöbern".

### SCR-09 Tee (my tea detail)

As in the mockup's tea detail: collapsing hero photo, serif name, subtitle,
„★ 4,5 (12)", type chip, umami meter, `StatTriplet`, sticky *Brühen*, pencil
FAB. Sections: REZEPTE, VORRAT & KOSTEN („38 g übrig · 0,42 € pro Tasse"),
FRISCHE, GESCHMACK (radar, top descriptors), LETZTE SITZUNGEN, NOTIZEN,
DETAILS. Overflow: Favorit, Aus Katalog zurücksetzen, Im Shop öffnen,
Archivieren, Löschen.

### SCR-10 Tee bearbeiten

Full-screen dialog (✕ · „Tee bearbeiten" · *Speichern*), photo drop zone,
sections per TEA-02.

### SCR-11 Sorte wählen (manual add)

List of built-in styles by group with search; *Andere / unbekannt*.

### SCR-12 Rezept bearbeiten

Fields per PRF-03/04 incl. *Vorwarnung* selector; step list with drag
handles; *Aufgüsse erzeugen* sheet; *Als Standard*.

### SCR-13 / SCR-14 Teegeschirr

List (type icon, name, volume) and editor.

### SCR-15 Journal

Serif „Journal", search „Journal durchsuchen …", filter icon; day-grouped
`TeaListItem`s; in-progress session pinned. Empty: „Deine erste Tasse wartet."

### SCR-16 Sitzung (detail)

Hero, tea (link), date/time, method, vessel, leaf/water/ratio, step table
(geplant / tatsächlich / °C), rating, radar, descriptors, notes, photos.
Actions: *Notizen hinzufügen*/*Bearbeiten*, *Nochmal brühen*, *Löschen*.

### SCR-17 Sitzung bearbeiten / Notizen

Rating, taste sliders with live radar, descriptors (→ SCR-30), notes, photos,
and (edit mode) date/time, leaf, water, step times.

### SCR-18 Statistik

Serif „Deine Tee-Reise"; tabs **Überblick · Bewertungen · Verbrauch**; range
selector. Überblick: teas total („24 · +3 diesen Monat"), sessions this
week/month, streak, sessions per week, tea-type donut. Bewertungen: average,
distribution bars, top teas, taste radar. Verbrauch: grams per month, money
spent per month, cost per session, running-out list.

### SCR-19 Sammlung › Katalog

Segmented control [ Meine Tees | **Katalog** ]; header „Tee-Kontor Kiel ·
274 Grüntees · aktualisiert am 21. Okt."; *Ratgeber* row (→ guides list);
search; filter chips (Sorte, Herkunft, Bio, aromatisiert, im Besitz,
Rezept prüfen); sort; rows with photo, name, subtitle, type, origin,
„15,90 €/100 g", flag chips. Overflow: *Katalog aktualisieren*.
Empty (never loaded): „Der Katalog ist noch leer" · *Katalog laden*.

### SCR-20 Ratgeber-Artikel

Hero image (if any), serif title, source line „Tee-Kontor Kiel · Tee-Info",
rich text, links to catalog teas resolved locally, *Im Browser öffnen*.

### SCR-21 Katalog-Tee

Photo, name, subtitle, type, origin/cultivar/harvest/shading/steaming, Bio /
aromatisiert / Coldbrew geeignet, the shop's taste scales (Umami,
Bitterstoffe, … as leaf rows 0–5), aroma and taste notes, description, ZUBEREITUNG (shop table and text, verbatim),
ABGELEITETES REZEPT (steps + flag reason), PACKUNGEN (sizes and prices), primary
*In Sammlung aufnehmen* or *Nachkauf erfassen*, secondary *Im Shop öffnen*.

### SCR-22 Katalog aktualisieren (sheet)

Progress (Seiten, Produkte, Fotos), *Abbrechen*; result summary (CAT-07 AC3);
error state with *Details kopieren* for the developer.

### SCR-23 In Sammlung aufnehmen (sheet)

Package option radio list („50 g · 8,95 €", „100 g · 17,90 €", „250 g ·
≈ 43,41 € · 3 % gespart", custom); estimated prices are marked „≈" and
replaced by the exact price when fetched; Kaufdatum; *Aufnehmen*.

### SCR-25 Einstellungen

Sections per SET-30; *Timer-Zuverlässigkeit* card at the top with status rows
and *Beheben* buttons; *Alarm testen*.

### SCR-26 Daten & Sicherung

Device-backup explanation, *Sicherung erstellen*, *Wiederherstellen*,
safety backup, (Could) CSV export, *Alle Daten löschen*.

### SCR-27 Über

Logo, version, Tee-Kontor source note („Katalogdaten und Fotos © Tee-Kontor
Kiel, nur für den privaten Gebrauch"), licences, *Einrichtung erneut zeigen*,
*Diagnose* (local timer/catalog log, *Kopieren*; [06 §5.6](06-architecture.md#56-error-handling--logging)).

### SCR-28 Fotos, SCR-29 Vorrat, SCR-30 Beschreibungen

Photo pager (zoom, cover, delete); stock sheet (remaining, *Nachkauf
erfassen*, *Bestand korrigieren*, history); descriptor picker (search,
groups, custom).

## 5. Dialogs & messages (German)

| Situation | Copy |
|---|---|
| Unsaved changes | „Änderungen verwerfen?" · Weiter bearbeiten / Verwerfen |
| Delete tea | „*Gyokuro* löschen? Deine 12 Journal-Einträge bleiben erhalten." · Stattdessen archivieren / Löschen |
| Another session | „Erst die *Sencha*-Sitzung beenden?" · Beenden & neu starten / Abbrechen |
| Discard session | „Sitzung verwerfen? Nichts wird gespeichert." · Verwerfen / Weiter brühen |
| Reset from catalog | „Rezepte und Beschreibung aus dem Katalog übernehmen? Deine Änderungen gehen verloren." |
| Catalog error | „Die Shop-Seite hat sich geändert – ein App-Update ist nötig. Dein bisheriger Katalog bleibt erhalten." |
| Restore | „Alles durch diese Sicherung vom 3. Okt. 2026 ersetzen?" |

## 6. Accessibility

Touch targets ≥ 48 dp (timer pill ≥ 72 dp high); contrast ≥ 4.5:1 for body
text in all schemes; content descriptions in German; chart table
alternatives; correct semantics for rating, sliders, ring; 200 % font scale
without loss of essential content.
