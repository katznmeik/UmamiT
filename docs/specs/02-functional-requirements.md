# 02 · Functional Requirements

> Status: **Draft v0.2** · Last updated: 2026-11-13 (v0.7.0-rc.2, D-115 Amendment 1: INS-01–INS-08 reworked, INV-08 cups by volume, SET-34 *Tassengröße*, v0.7.1 note at JRN-02, D-116; v0.7.0, Statistik: INS-01–INS-08, D-115; 2026-11-12: v0.6.1, the PO's feedback on v0.6.0: TMR-22 chips, JRN-03 and JRN-05 per infusion, JRN-09, JRN-10 ⋮, TEA-05 viewer, TEA-13 ⋮, CAT-07 „N Tees", CAT-10 no kind, SET-05, SET-30, D-114; 2026-11-11: v0.6.0, the journal: JRN-02–JRN-11, PRF-07,
> TMR-12, TEA-05, TEA-08, TEA-11, TEA-13, SET-18 retired, D-112;
> 2026-10-31: M3 in two releases,
> v0.4.0 and v0.4.1: TEA, PRF, WARE, INV, BRW-06, BRW-08, JRN-10, SET-15,
> SET-30, D-100; v0.3.0: SET-32 *Bei
> „Nicht stören" klingeln*, D-099; 2026-10-30: v0.3.0: quick timer
> alongside a brew, long steps from 60 min, alarm volume and *Lautstärke*,
> daily low-stock reminder INV-11, foldable catalog groups, stock on READY,
> D-098; v0.2.2 from the PO's
> feedback on v0.2.1: BRW-11, BRW-08, INV-05, INV-10, SET-16, TMR-22, CAT-18,
> D-097; 2026-10-28: sold-out teas and
> adding a tea from a shop link, CAT-16, CAT-17, D-094; 2026-10-26: v0.2.1
> and v0.3 from the PO's feedback on `v0.2.0-rc.1`, D-093)
> v0.2 incorporates the PO interview of 2026-10-21. Requirements dropped in
> v0.2 are listed in §16. A few v0.1 IDs were given new meanings in v0.2 (no
> code existed yet); from v0.2 on, IDs are stable and never reused.

## 0. Conventions

### 0.1 Requirement IDs

| Prefix | Area | Section |
|---|---|---|
| GEN | Cross-cutting | §1 |
| ONB | First-start setup | §2 |
| CAT | Tee-Kontor catalog | §3 |
| TEA | Collection (my teas) | §4 |
| STY | Built-in tea styles (fallback) | §5 |
| PRF | Brew recipes (profiles) | §6 |
| WARE | Teaware | §7 |
| BRW | Brew setup | §8 |
| TMR | Steep timer & sessions | §9 |
| JRN | Journal | §10 |
| INV | Stock, cost & freshness | §11 |
| INS | Statistics | §12 |
| SET | Settings | §13 |
| BAK | Backup & export | §14 |
| NOT | Notifications & Live Update | §15 |

### 0.2 Priorities

| Priority | Meaning |
|---|---|
| **Must** | Required for the first complete version (v1.0). |
| **Should** | Planned for v1.0; may slip to v1.1 if needed. |
| **Could** | Backlog. |
| **Won't** | Out of scope (documented to prevent scope creep). |

The delivery order (timer first) is defined in [09](09-release-plan.md).
Screen IDs `SCR-xx` refer to [03](03-ux-ui.md); timer details to
[04](04-brew-timer.md); catalog import rules to [08](08-content.md).

**Language:** the app UI is German. UI strings in this document are given in
German with an English gloss where helpful.

---

## 1. General (GEN)

**GEN-01 — Local-first, four network uses** `Must`
All personal data is stored only on the device. The app declares
`INTERNET` for exactly four uses (D-036, D-094), all to the shop host:
(a) the user-triggered catalog refresh (CAT-07, incl. the re-check of CAT-16);
(b) the one-time download of the two seed teas' photos at first start
(ONB-06); (c) the on-demand exact price fetch when the user adds a package
(CAT-12); (d) the user-triggered fetch of one product page the user shares
or pastes (CAT-17, v0.2.1).
- AC1: Network requests are made only to `www.tee-kontor-kiel.de` (the shop
  serves its images from the same host), enforced by a host allow-list in
  the HTTP client.
- AC2: Every feature except these four works in airplane mode (the seed
  teas then use the placeholder image; prices stay estimates).
- AC3: No analytics, ads, crash-reporting or other third-party network SDKs.

**GEN-02 — No accounts** `Must`

**GEN-03 — At most two active timers** `Must`
At most one brew session or quick timer is active at any time.
- **v0.5.0 (D-108):** at most **two** timers are active at once, in all:
  two brew sessions, or one brew session and one quick timer (TMR-21,
  TMR-22). A tea has at most one active session. A third start asks which
  timer to end (TMR-21).

**GEN-04 — German & metric** `Must`
UI language German (`de-DE`) only; units °C, g, ml; German number, date and
time formats (e.g. „21. Okt. 2026", „4,5 g").

**GEN-05 — Undo for destructive actions** `Should`
Deletions show a snackbar „Gelöscht · Rückgängig" for 5 s. Multi-object or
irreversible actions (e.g. delete all) use a confirmation dialog.

**GEN-06 — Active brew bar** `Must`
While a session or quick timer is active, top-level screens show a compact bar
above the navigation bar with tea name, step („Aufguss 2/4") and live remaining
time; tapping it opens the timer (SCR-05). While the step alerts, the bar turns
static matcha with „Jetzt abgießen!" and the overtime counting up (03 §3.8,
D-055). With two active timers (v0.3.0 a brew and the quick timer, D-098;
v0.5.0 also two brew sessions, D-108) it holds both in the slot order, each
half with its live countdown and its own tap, which opens that timer; an
alerting half takes the alert style alone, a held one shows that it waits
(„wartet" for a brew, „fertig" for the quick timer). TalkBack reads each
half with its tea, step and time.

**GEN-07 — State survives everything** `Must`
No input or timer state is lost on rotation, theme change, multi-window, or
process death.

**GEN-08 — Unsaved changes guard** `Must`
„Änderungen verwerfen?" (*Weiter bearbeiten* / *Verwerfen*) when leaving an
editor with unsaved changes.

**GEN-09 — Empty, loading, error states** `Must`
Every list and detail screen defines all three.

**GEN-10 — Instant local search** `Must`
Case- and diacritics-insensitive prefix search, debounced 150 ms.

**GEN-11 — Secondary names** `Should`
Teas can show a Japanese/Chinese secondary name (e.g. 「煎茶 浅蒸し」).

**GEN-12 — Temperatures in 5 °C steps** `Must`
All temperature inputs and displays use 5 °C steps (matching the PO's
kettle). Imported or legacy values are snapped to the nearest 5 °C
(half up: 52,5 → 55).

---

## 2. First-start setup (ONB) — SCR-01

**ONB-01 — Welcome** `Must`
Welcome page with logo, „UmamiT", the welcome text „Aus Liebe zum Tee",
ink-wash illustration, *Los geht's*.

**ONB-02 — Reliability setup** `Must`
One page „Damit der Timer zuverlässig klingelt" walks through:
1. Notification permission (`POST_NOTIFICATIONS`).
2. Battery: request exemption from battery optimisation
   (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) and explain Samsung's
   „Akku › Uneingeschränkt" and „Nie schlafende Apps" settings with a deep link
   to the app's battery settings.
3. Live notifications: verify that the app may show Live Updates
   (`canPostPromotedNotifications()`); deep link to
   `Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS` if not.
4. Full-screen alert: verify `NotificationManager.canUseFullScreenIntent()`;
   deep link to `Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` if not
   (TMR-06).
5. Hint (no ✓): Samsung's *Automatische Optimierung* may restart the phone
   at night; disable *Automatisch neu starten* or note its time window (04
   §7.3).
Each item shows a ✓ when satisfied. *Weiter* is always allowed; missing items
are then shown as a banner on Home and the timer (TMR-31).

**ONB-03 — Load catalog** `Must`
Final page offers *Katalog jetzt laden* (runs CAT-07 with progress) or
*Später*.

**ONB-06 — Seed photos** `Must`
During the first-start setup the app downloads the product photos of the two
seed teas once (08 §2.5a; network use (b) of GEN-01) with visible progress.
The step can be skipped and fails silently offline; the placeholder image is
used until the first catalog refresh (CAT-15).

**ONB-04 — Run once** `Must`
Shown until completed; reachable again via Einstellungen › Über ›
*Einrichtung erneut zeigen*.

---

## 3. Tee-Kontor catalog (CAT) — SCR-19 … SCR-23

The catalog is a local, read-only copy of all green teas from
[tee-kontor-kiel.de](https://www.tee-kontor-kiel.de/), refreshed on demand.
Source structure, field mapping and parsing rules: [08 §2](08-content.md#2-tee-kontor-catalog-import).

**CAT-01 — Scope** `Must`
All products in the shop's green tea sections (verified structure in
[08 §2.2](08-content.md#22-source-structure)): Japan (all types incl.
Mizudashi cold-brew bags), China, Taiwan, *andere Regionen*, *Bio*,
*Klassischer Grüntee*, *Aromatisierter Grüntee* (flavoured teas and blends),
the current *Shincha* section and *Matcha-Teepulver* — about 260–280 teas.
Excluded: sets (`…SET` in any case, or a set by its name or breadcrumb),
sale duplicates of a regular tea (`…SALE`), matcha bowls and accessories,
sweets, and items containing no tea (D-091).
- **Kräutertees (v0.2.1, D-093):** the shop's herbal teas (its *Kräutertee*
  section; not fruit tea or rooibos) are loaded too, with their own style
  `herbal` (2 g / 250 ml, Becher, 100 °C, one infusion of 6:00, no cold
  brew; [08 §3](08-content.md#3-built-in-tea-styles)). They are hidden by
  default and shown by the *Kräutertee* filter (CAT-10). They add about
  1–2 min to the first load (NFR-PERF-11 stays ≤ 20 min); the first refresh
  after the update re-classifies everything once (≈ 16 min, accepted by the
  PO).
- **Black teas (v0.5.0, D-108, #203):** the shop's *Schwarzer Tee* section
  (`/tee/schwarzer-tee/`, and the Japan tree's `koucha-schwarztee/`) is
  loaded too, as kind *Schwarztee* with category `BLACK` and the black styles
  of [08 §3](08-content.md#3-built-in-tea-styles) (`darjeeling`, `assam`,
  `ceylon`, `flavored_black`, `black_generic`). A product filed under a
  format or maker section (*Aufgussbeutel*, a maker's page) is classified by
  a tea section in its breadcrumb and skipped without one. Sets, multipacks,
  sale duplicates and teaware stay out as above. The first refresh after the
  update re-reads the skip list's not-green products once (05 §9, 08 §2.4
  item 3); the first-load budget is NFR-PERF-11's.
- **White teas, oolongs and Pu-Erhs (v0.5.0, #205):** the shop's *Weißer
  Tee* (`/tee/weisser-tee/`, and the Japan tree's `weisstee/`), *Oolong*
  (`/tee/oolong/`, and the Japan tree's `oolong/`) and *Pu-Erh*
  (`/tee/pu-erh/`) sections are loaded too, as kinds *Weißer Tee*, *Oolong*
  and *Pu-Erh* with categories `WHITE`, `OOLONG` and `DARK`, by the same
  rules as the black teas. They are parser v9: a catalog last refreshed by
  parser v8 (a v0.5.0 release candidate) re-reads its not-green products once
  more; a v0.4.1 catalog re-reads once (08 §2.4 item 3).
- **Rotbusch, Früchtetee and Chai (v0.5.0, #206):** the shop's *Rotbusch*
  (`/tee/rotbusch/`) and *Früchtetee* (`/tee/fruechtetee/`) sections are
  loaded too, as kinds *Rotbusch* and *Früchtetee* with the new categories
  `ROOIBOS` and `FRUIT`; *Chai* is a kind of its own (style `chai`). The
  shop files each chai under its base tea's section (live check, 08 §2.2),
  so a tea of any section whose name says „Chai" is a chai, of its section's
  category (black, rooibos, green, Kräutertee); one filed in `/tee/chaitee/`
  itself takes the base its name or ingredients name, else `OTHER`. A
  product of a Japan tea family's page that names another tea (Wakoucha,
  Oolong, Weißtee) is a tea of that kind, *Sorte* „Japan". They are parser
  v10: a catalog last refreshed by parser v9 re-reads its not-green
  products once more (08 §2.4 item 3).

**CAT-02 — One entry per tea** `Must`
Size variants (10 g … 1000 g, each a separate article in the shop) and teas
split into separate article numbers per size are merged into one catalog
entry with a list of package options (weight, price, savings).

**CAT-03 — Extracted data** `Must`
Per tea: name, secondary name, article number, product URL, shop category and
tea type (*Sorte*: Sencha, Gyokuro, …), origin country & region, cultivar,
harvest/flush, shading, steaming, organic flag, „Coldbrew geeignet" badge,
flavoured flag, aroma, taste notes (*Geschmack*), manufacturer, matcha
grade, ingredients, the shop's taste scales (Umami, Bitterstoffe, … 0–5),
description (German text), raw brewing grid (Kyusu and/or Westlich rows),
brewing prose, package options with price, base price per 100 g,
availability, main photo. Unavailable fields stay empty.

**CAT-04 — Derived recipe** `Must`
For each tea, one or more timer-ready recipes are derived from the brewing
information using the rules in [08 §2.5](08-content.md#25-recipe-derivation):
table wins over text, then the built-in style default; ranges use the
midpoint; temperatures snap to 5 °C, times to 5 s; cold-brew instructions
produce an extra cold-brew recipe.

**CAT-05 — „Rezept prüfen" flag** `Must`
A tea is flagged „Rezept prüfen" (check recipe) with a reason when its
**default** recipe came (partly) from the text or a style default instead of
the shop's brewing grid, when grid and text contradict, or when values were
implausible. Additional recipes (e.g. a cold brew from the style default)
only show their source as a note. The flag is visible in the
catalog and carries over to collection teas created from it until the user
edits or confirms the recipe. To confirm it before the recipe editor (M3):
after a session with a flagged tea, the „Gespeichert" sheet (SCR-06) offers
*Rezept passt*, which clears the flag (D-073).

**CAT-06 — Style mapping** `Must`
Every catalog tea is mapped to a built-in style (STY) by its *Sorte* or name
(e.g. „Gyokuro" → `gyokuro`), providing umami level, freshness window and
fallback recipe. Unmapped teas use `green_generic` and are flagged.
A tea of another section takes its section's style, never flagged: a
Kräutertee `herbal`; a black tea `flavored_black` when flavoured, else
`darjeeling`, `assam` or `ceylon` by its *Sorte* or name, else
`black_generic` (v0.5.0, D-108; [08 §2.6](08-content.md#26-style-mapping-and-umami-level));
a white tea `flavored_white` when flavoured, else `white_generic`; an oolong
`flavored_oolong`, else `oolong_generic`; a Pu-Erh `sheng` or `shou` by its
*Sorte* or name, else `dark_generic` (#205); a Rotbusch `rooibos`, a
Früchtetee `fruit`, a chai `chai`, flavoured or not (#206). A Gongfu row of the shop's grid
becomes a Gongfu recipe, as for green teas.
The shop's grid and text win over the style's values; „Rezept prüfen" shows
only without shop values (CAT-05). The PO's second western infusion stays
green-only.

**CAT-07 — Refresh** `Must`
*Katalog aktualisieren* (Sammlung › Katalog, overflow, and Settings) fetches
the shop, parses all products, and replaces the catalog atomically.
- AC1: Progress shows pages and products processed; the refresh can be
  cancelled; the phone may be locked during refresh (the app's own
  foreground service of type `dataSync`, started from the user's tap, with a
  progress notification; not WorkManager, 06 §5.3).
- AC2: Requests are polite: ≤ 2 requests/second (all requests, incl.
  images), one HTTP connection, identifying user agent, `robots.txt`
  respected (no paginated listings; discovery via the sitemap), timeouts and
  ≤ 3 retries for timeouts and connection errors only, each a new
  rate-limited request (D-076); a Cloudflare challenge, HTTP 429 or a `Retry-After` header
  stops the refresh without changes (08 §2.8).
- AC3: Result summary: „12 neu · 5 geändert · 2 nicht mehr im Sortiment ·
  3 Rezepte prüfen", after the count of green teas, each part only when
  above 0: *neu* not in the previous catalog, *geändert* the shop changed
  what it shows of the tea (or it is back in the range), *nicht mehr im
  Sortiment* left the range, *Rezepte prüfen* flagged since this refresh.
  The first load leaves out „neu" (every tea is); a later refresh without
  change says „keine Änderungen" (D-086).
- AC4: Teas no longer found are **hidden** from the catalog. They are kept
  (marked „Nicht mehr im Sortiment") only if a collection tea links to them
  via `catalog_key`; otherwise they are deleted with their photos. A tea
  still in the sitemap whose page can't be read once is kept as it was
  (08 §2.4 item 5, D-086). Exception (v0.2.1, D-094): a tea missing from the
  sitemap counts as no longer found only after the re-check of CAT-16.
- AC5: If parsing fails structurally (e.g. < 50 % of the previous product
  count, or required fields missing on most products), the existing catalog
  is kept unchanged and the error „Die Shop-Seite hat sich geändert – ein
  App-Update ist nötig" is shown with technical details for the developer.
- AC6: Date of the last successful refresh is shown in the catalog header.
  From v0.6.1 ([D-114](10-open-questions-and-decisions.md#d-114)) the header reads „Tee-Kontor Kiel · N Tees ·
  aktualisiert am …" in every view (N the view's count), and the refresh's
  notification and sheet count the whole range as „N Tees", never as
  green teas.
- AC7: Refresh runs **only when the user taps it** (never automatically) and
  is allowed on any connection (Wi-Fi or mobile data) without a warning.
- AC8: A refresh may run while a brew session is active. The timer always
  has priority (rule 8): the refresh never delays, hides or silences an
  alert, and the timer's alarms and service are independent of it (D-073).

**CAT-08 — Photos** `Must`
The main product photo of each tea is downloaded, resized (max 1200 px long
edge, WebP) and stored locally, with a small thumbnail for the lists
(256 px short side, D-074);
unchanged photos are not downloaded again, nor photos that failed for good
while their URL stays the same (D-091). JPEG, PNG and WebP sources are read
(D-091). About 25–35 MB for the whole
catalog (D-073); from v0.5.0, with every tea section, about 80–100 MB
(NFR-PERF-11, D-108). With SET-22 *Katalog: Fotos laden* off, a refresh fetches
no photo and keeps those already stored (D-078).

**CAT-09 — Tee-Info guides** `Should`
Tee-Kontor's guide articles relevant to green tea (e.g. „Ein Guide für
Japantee", matcha guides and recipes) are imported as readable articles and
listed in Katalog › *Ratgeber*. Links between guides and to catalog teas are
resolved locally where possible; other links open the browser.

**CAT-10 — Browse & search** `Must`
SCR-19 lists catalog teas with photo, name, secondary name, type, origin,
price per 100 g and flags (Bio, aromatisiert, im Besitz, Rezept prüfen).
Filters: type (multi), origin country (multi), organic, flavoured / pure,
umami level, „im Besitz" (owned), available, *Neu*. Sort: name, price per
100 g, type. Search over name, secondary name, description, cultivar, region.
- **Default view** (D-073): grouped by type (*Sorte*), available teas only,
  and the „Pur" filter on, so flavoured teas are loaded and searchable but
  hidden until the filter is changed.
- **Neu:** teas first seen in the latest refresh carry a „Neu" badge until
  the next refresh; the *Neu* filter lists them.
- **Kräutertee (v0.2.1, D-093):** herbal teas (CAT-01) are hidden in the
  default view; the filter chip *Kräutertee* switches the list to the
  Kräutertees only (grouped by *Sorte*), tapping it again returns to the
  green teas. The *Pur* filter does not apply in the Kräutertee view; the
  other filters still do. Search hits among the hidden kind count as hidden
  by the filters. From v0.4.0 a chip *Grüntee* names the default view next
  to *Kräutertee* and returns to it
  ([D-103](10-open-questions-and-decisions.md#d-103)).
- **Catalog kind (v0.5.0, D-108):** the chips *Grüntee* and *Kräutertee* are
  the two entries of the list of *catalog kinds* (see the glossary). A
  catalog tea's kind comes from its style (style `herbal` = Kräutertee,
  every other style = green, the default kind); the filter holds one kind,
  the chip row is rendered from the list, and a new kind is a new entry in
  it. „Pur" as the default and the umami filter apply to the green kind
  only. Search hits hidden by the filters are counted per other kind, and
  *Alle zeigen* switches to the kind with the most of them. The prefactor
  (#199) changed nothing visible.
- **Schwarztee (v0.5.0, #203):** the kind *Schwarztee* (key `black`, the
  black styles) has its chip between *Grüntee* and *Kräutertee*; its view
  is grouped by *Sorte* (Darjeeling, Assam, Ceylon, Nepal, …) with folding,
  and the header reads „N Schwarztees". Until the first successful refresh
  after the update its view shows „Schwarztees kommen mit dem nächsten Abruf
  (≈ 30 min, einmalig)" with *Jetzt laden*, as the Kräutertee view did in
  v0.2.1 (a kind is pending while the last successful refresh ran a parser
  older than its section's, 05 §9).
- **Weißer Tee, Oolong, Pu-Erh (v0.5.0, #205):** the kinds *Weißer Tee*
  (key `white`), *Oolong* (key `oolong`) and *Pu-Erh* (key `dark`) have their
  chips after *Schwarztee*, in the order *Grüntee · Schwarztee · Weißer Tee ·
  Oolong · Pu-Erh · Kräutertee* (D-108 point 5); each view is grouped by
  *Sorte* (Bai Mu Dan, …; Taiwan, China, …; Sheng, Shou, Aged) with folding
  of its own, and the header reads „N Weiße Tees", „N Oolongs", „N Pu-Erhs".
  Until the first successful refresh with parser v9 each view shows its own
  hint („Weiße Tees / Oolongs / Pu-Erhs kommen mit dem nächsten Abruf
  (≈ 30 min, einmalig)") with *Jetzt laden*.
- **Rotbusch, Früchtetee, Chai (v0.5.0, #206):** the kinds *Rotbusch* (key
  `rooibos`), *Früchtetee* (key `fruit`) and *Chai* (key `chai`) close the
  chip row, *Grüntee · Schwarztee · Weißer Tee · Oolong · Pu-Erh ·
  Kräutertee · Rotbusch · Früchtetee · Chai* (D-108 point 5). Their views
  are grouped by *Sorte* (Rotbusch: Grüner Rotbusch, Honeybush; Chai: its
  base, Schwarztee, Rotbusch, Grüntee, Kräutertee) with folding, and the
  header reads „N Rotbuschtees", „N Früchtetees", „N Chais". Until the first
  successful refresh with parser v10 each view shows its own hint
  („… kommen mit dem nächsten Abruf (≈ 30 min, einmalig)"). The chip row
  scrolls so the selected kind's chip is in view whenever the kind changes,
  by a tap or by *Alle zeigen*.
- **Flavour filter (v0.5.0, D-108 point 8, #204):** the filter sheet adds
  the shop's taste notes (*Geschmack*, 08 §2.3) and taste scales, for the
  kind shown; they apply in every kind's view.
  - AC1: *Geschmack* offers, as multi-select chips, every note that occurs
    in the current range of the kind shown (whatever the other filters),
    most frequent first, ties by name. A tea matches when it names
    **every** chosen note; notes compare whole, normalised like search
    (GEN-10: case and diacritics ignored, so „Süß" and „süß" are one note,
    „mild" does not match „mildsüß").
  - AC2: Each shop scale that occurs in the range (Süße, Bitterstoffe,
    Fruchtig, Nussig, Würzig, Röstnoten, Komplexität, Floral …), most
    frequent first, offers *Egal · ab 1 · … · ab 5* leaves, like the umami
    filter; a tea without the scale never reaches a minimum. Umami stays
    its own filter and is not offered among the scales.
  - AC3: The chosen notes count once in „Filter (n)", each scale with a
    minimum counts once; *Zurücksetzen* and „Filter zurücksetzen" clear
    them. A kind change keeps only the notes and scales the new kind's range
    has. Search hits they hide count as hidden; *Gemerkt* applies only the
    search, as before.
- **Foldable groups (v0.3.0, D-098).** In the grouped default and
  Kräutertee views a tap on a *Sorte* header folds or unfolds its teas; the
  header shows a chevron and the count („Bancha · 12").
  - AC1: Folded groups are remembered across app starts, separately for the
    green and the Kräutertee view, in general per catalog kind (setting
    `catalogFolded`, keys `<kind key>/<Sorte>`; the stored `green/…` and
    `herbal/…` entries of v0.4.1 stay valid).
  - AC2: A non-empty search shows every hit unfolded; clearing it restores
    the folding.
  - AC3: The catalog's ⋮ offers *Alle einklappen* and *Alle ausklappen*.
  - AC4: *Gemerkt* and the flat sort orders (name, price) have no folding.

- **No kind selected (v0.6.1, [D-114](10-open-questions-and-decisions.md#d-114)).** The Katalog opens with no
  kind chip selected and shows every tea, grouped by kind in the chip order
  (each kind group foldable, fold keys `all/<kind key>`), the *Sortierung*
  applying inside each group (with *Sortierung* *Sorte*, the *Sorte*
  headings show inside each kind group, without folding of their own).
  A tap on a kind chip narrows to that kind (one at a time); a tap on the
  selected chip clears it. *Pur* and the umami filter apply in the Grüntee
  view only. *Zurücksetzen*, „Filter zurücksetzen" and *Alle zeigen* clear
  the kind. The kind does not count in „Filter (n)" and is not remembered
  across app starts. A pending kind's hint (05 §9) is its group's body.
  - AC1: Opening the Katalog shows every kind; deselecting the last chip
    never selects *Grüntee*.

**CAT-11 — Catalog tea detail** `Must`
SCR-21 shows photo, names, type, origin details, organic/flavoured,
description, brewing guide (raw table and text as on the shop), the derived
recipe(s) with flag reason, package options and prices, and actions
*In Sammlung aufnehmen* and *Im Shop öffnen*. *Im Shop öffnen* opens the
Tee-Kontor page in the browser: the page of the package size bought last when
the tea is in the collection (as INV-09), else the product page
(`product_url`). Opening the browser is not a network use of the app (GEN-01,
D-072).

**CAT-12 — Add to collection** `Must`
*In Sammlung aufnehmen* opens SCR-23: choose package option (weight & price;
prices of other sizes are estimates from the base price and savings until the
exact price is fetched from the shop when online — editable either way),
purchase date (default today). The exact-price fetch is network use (c) of
GEN-01. Creates a collection tea that is
an independent copy (TEA-01) with recipes, stock (INV-01) and price. If the
tea is already in the collection, the action becomes *Nachkauf erfassen*
(record repurchase), adding stock instead of a duplicate. Both ship with M2
(D-073): the new tea is immediately in the tea picker and brewable with its
derived recipe and stock deduction. Started from the tea picker's *Aus dem
Katalog* (SCR-03), adding returns to the brew setup (SCR-04) with that tea
selected; a catalog tea is never brewed without adding it.

**CAT-15 — Link seed teas on first refresh** `Must`
After a successful catalog refresh, the M1 seed teas Tanabata and Hisui,
whose `catalog_key` matches a catalog entry, are linked and receive the
catalog description and photo automatically. Recipes, stock, journal entries
and user edits are never changed. Each seed tea has two parts, each done once
(05 §6 `seedTeaLinksDone`, D-086, D-090): the **description** as plain text,
only when the tea has none (done once it has one, linked or its own), and the
**photo** as the tea's own JPEG cover, only when it has none (done once it has
one, or when the refresh loaded no photos, SET-22; D-076). A part the refresh
couldn't give (the tea's page unread, no description or photo, a seed created
after the first refresh, D-075) is tried again after every later successful
refresh; a done part never again.

**CAT-13 — Collection teas stay independent** `Must`
A refresh never changes collection teas, except the one-time linking of seed
teas in CAT-15. Tea detail offers *Aus Katalog
zurücksetzen* (reset description/recipes from the current catalog entry, with
confirmation and a choice of what to reset).
- **v0.4.1 (D-100):** the reset takes recipes and description together after
  one confirmation (no choice); stock, photos, notes, favourite and journal
  stay. Not offered when the tea is no longer in the catalog.
- **As built (v0.4.1, D-104, #184):** the detail's ⋮ offers it while
  `catalog.db` holds a tea for the collection tea's `catalog_key` (a
  discontinued one included). One transaction replaces `description` and the
  catalog recipes (every `source` but USER; an edit makes a recipe USER) with
  those *In Sammlung aufnehmen* derives from the current entry (the same
  derivation; the style's recipes when the catalog has none). Details in
  PRF-06.

**CAT-14 — Bundled snapshot** `Won't`
No catalog snapshot is bundled in the APK: the APK is publicly downloadable
from GitHub Releases, which would redistribute shop content (D-016, D-036).

**CAT-16 — Sold-out teas stay in the catalog** `Must` (v0.2.1, D-094, #97)
The shop leaves sold-out products out of its sitemap (08 §2.2). During a
refresh, every catalog tea whose article number is missing from the sitemap
gets its own product page fetched once, within the refresh's limits and stop
rules (CAT-07 AC2). This is part of network use (a) of GEN-01, not a new use.
- AC1: Answer 200, the canonical URL is that product page, and the breadcrumb
  is still in a tea section of the catalog (CAT-01) → the tea is kept with the page's
  data and stock („nicht vorrätig" when no size is available); it never
  counts as „nicht mehr im Sortiment".
- AC2: 404/410, a redirect away from the product (its `Location`, never
  followed), or a breadcrumb out of scope → the tea is no longer found, as in
  CAT-07 AC4.
- AC3: The page can't be read this once without a stop (e.g. `robots.txt`
  disallows it, or the answer is over the body limit; 08 §2.8) → the tea is
  kept as it was. A 5xx, or a network error after its retries, stops the
  whole refresh as 08 §2.8 says.
- AC4: A Cloudflare challenge, HTTP 429 or `Retry-After` stops the whole
  refresh without changes, as today (08 §2.8).
- AC5: Expected about 5–20 extra requests per refresh, inside the refresh's
  budget (NFR-PERF-11).

**CAT-17 — Add a tea from a shop link** `Must` (v0.2.1, D-094, #98)
A tea the sitemap doesn't list (e.g. sold out) can be fetched on its own from
its shop link. This is network use (d) of GEN-01.
- AC1: Entry points: the Android share target (`ACTION_SEND`, `text/plain`),
  so UmamiT appears in the browser's *Teilen* sheet, and *Link einfügen* in
  the catalog's overflow menu (SCR-19), which opens a paste field.
- AC2: Accepted is only `https://www.tee-kontor-kiel.de/<slug>/<Artikelnummer>`
  with an optional `-<Gramm>` size suffix. Anything else (another or
  look-alike host, `http`, userinfo, a query string, a category or
  `/tee-info/` URL) shows „Das ist kein Tee-Link von Tee-Kontor Kiel." and
  sends no request.
- AC3: The app fetches only that one product page through the shared polite
  shop session (`robots.txt` respected, ≤ 2 requests/second, stop on a
  challenge, HTTP 429 or `Retry-After`). Offline, it shows a message and
  sends nothing.
- AC4: A tea of the catalog's sections (CAT-01: green, black, white, oolong, Pu-Erh, Kräutertee, Rotbusch, Früchtetee, Chai) is stored in the catalog as a regular
  entry (photo per SET-22) and its detail (SCR-21) opens with *In Sammlung
  aufnehmen* / *Nachkauf erfassen* as usual; the exact price is fetched on
  adding (GEN-01 (c)). A sold-out tea shows „nicht vorrätig". It is not
  marked „Neu".
- AC5: Not in those sections → „Dieser Artikel gehört nicht zum Katalog (nur
  Tees, ohne Sets und Zubehör)." (#206) and nothing is stored.
- AC6: Already in the catalog → its detail opens, and the page refreshes the
  entry. When the page can't refresh it without a stop (offline, no tea at
  the link, a page the app can't read), the detail opens as it was and the
  message shows too (D-096).
- AC7: While a refresh runs → „Gerade läuft ein Abruf – versuch es danach."
  and no request.
- AC8: A tea fetched this way that is not in the sitemap is kept by the
  re-check of CAT-16 on later refreshes.

**CAT-18 — Merkliste** `Should` (v0.2.2, D-097)
A catalog tea can be marked for later without adding it to the collection:
*Merken* (bookmark icon) on its detail (SCR-21) and on its catalog row
(SCR-19).
- AC1: The chip *Gemerkt* in the catalog shows every marked tea, green and
  herbal, sold out or discontinued; with it on, the other chips are hidden
  and only the search applies.
- AC2: Adding the tea to the collection (SCR-23) removes its mark. A tea in
  the collection offers no *Merken*; a removed (archived) one does.
- AC3: A marked tea is kept by the refresh when it leaves the range, like
  an owned one (CAT-07 AC4), and shows „Nicht mehr im Sortiment".
- AC4: Marks are personal data in umamit.db (`catalog_bookmark`, v3) and in
  the backup; a catalog refresh never touches them.

---

## 4. Collection — my teas (TEA) — SCR-08 … SCR-11

M3 ships in two releases ([D-100](10-open-questions-and-decisions.md#d-100)):
**v0.4.0** (brewing: list, detail, editors, teaware, favourites) and
**v0.4.1** (stock history, freshness, cost, own photos, reset from catalog).
Each item below names its release.

**TEA-01 — Add a tea** `Must`
From the catalog (CAT-12, primary path) or manually: style picker (SCR-11) →
editor (SCR-10) with defaults from the style. Only *Name* is mandatory
(1–80 chars).
- **v0.4.0 (D-100):** *Meine Tees*' FAB *Tee hinzufügen* offers *Aus dem
  Katalog* (opens the catalog, where a tea is added as before, SCR-23) and
  *Ohne Katalog* (→ *Sorte wählen*, SCR-11). *Sorte wählen* lists every
  built-in style by its Sorte name, grouped by Kategorie and searchable:
  the green styles, Kräutertee, Weißer Tee, Oolong, Schwarzer Tee, Pu-Erh and
  *Andere / unbekannt* ([08 §3](08-content.md#3-built-in-tea-styles)). The
  new tea gets the style's default recipe (PRF-02); a package entered in
  *Kauf* becomes its INITIAL stock event (INV-01); without a package it has
  no stock. It appears in *Meine Tees*, the tea picker and Start like any
  other tea.

**TEA-02 — Tea fields** `Must`
All fields of `Tea` ([05 §3.1](05-data-model.md#31-tea)) in sections:
*Allgemein* (photo, name, secondary name, style/type, umami level, flavoured),
*Herkunft* (country, region, cultivar, harvest year & flush, shading days,
processing), *Kauf* (vendor — default „Tee-Kontor Kiel" for catalog teas —
plus purchase date, package size and price, which are stored as the tea's
INITIAL stock event, [05 §3.10](05-data-model.md#310-inventoryevent)), *Vorrat & Frische* (stock, opened
date, best before, storage note), *Notizen*.
- **v0.4.0 (D-100):** *Allgemein* (name, secondary name, Sorte, umami
  level, flavoured), *Herkunft*, *Kauf* (vendor; purchase date, package and
  price only when a tea is added) and *Notizen*. Invalid values (an empty
  name, a harvest year in the future) show at the field and *Speichern*
  refuses them; ✕ with unsaved changes asks GEN-08's question.
- **v0.4.1:** photos (TEA-05) and the opened and best-before dates
  (INV-06). Tags are not planned (TEA-07).
  - **Dates (#179):** a section *Frische* with *Geöffnet am* and *Haltbar
    bis*, each picked with Material's date picker and cleared with
    *Entfernen*. Clearing *Geöffnet am* makes the tea unopened again. An
    opening date in the future is refused at the field (the picker offers no
    later day, and a stored one shows „Das Öffnungsdatum liegt in der
    Zukunft."); the best-before date may lie in the future. Saving changes
    only the dates the user touched: an untouched opening date keeps the one
    a brew set meanwhile. The freshness label is derived, so it follows at
    once.

**TEA-03 — Edit** `Must` (v0.4.0)
All fields editable. Changing the style does not overwrite recipes. Editing a
tea during its brew session changes nothing in the running session (rule 8).

**TEA-04 — Archive / delete** `Must`
*Archivieren* hides a tea from lists and pickers (e.g. when used up); journal
entries remain. *Löschen* keeps journal sessions (name snapshot) and removes
recipes, photos and stock events; the dialog offers *Stattdessen archivieren*.
- **From the tea picker (v0.2.1, D-093):** a long press on a tea in SCR-03
  offers *Archivieren* (until v0.3.0 „Aus Sammlung entfernen"). It archives
  the tea and shows the snackbar „*Tanabata* archiviert" · *Rückgängig*. An
  archived tea disappears from Start (*Nochmal brühen*, BRW-11) and from the
  tea picker; the journal keeps its entries with name and photo. Not
  possible for the tea of the active brew session: the item is greyed out
  with „Erst die Sitzung beenden". Adding it again from the catalog (CAT-12)
  un-archives the same tea and records the package as a PURCHASE event
  (D-085); its earlier stock events stay (correct with INV-03). An archived
  tea's catalog row has no „im Besitz" badge and its detail offers *In
  Sammlung aufnehmen*.
- **v0.4.0 (D-100):** one word everywhere, *Archivieren*, and
  *Wiederherstellen* to bring a tea back.
  - The detail's ⋮ offers *Archivieren* (snackbar with *Rückgängig*) and
    *Löschen*.
  - *Löschen* asks first: „*Gyokuro* löschen? Deine 12 Journal-Einträge
    bleiben erhalten." · *Stattdessen archivieren* / *Löschen* (03 §5). The
    journal keeps the entries with the tea's name; the tea's photo files are
    removed from the phone.
  - The list's *Archiviert* filter shows archived teas; *Wiederherstellen*
    brings one back to the list and the pickers, without re-adding it from
    the catalog.
  - A tea of the active brew session can be neither archived nor deleted
    until the session ends („Erst die Sitzung beenden").

**TEA-05 — Photos** `Should` (v0.4.1)
Up to 10 photos per tea (catalog photo pre-attached as cover for catalog
teas); own photos via the Android photo picker (no camera, D-100); *Als
Titelbild* sets the cover; the EXIF rotation of #58.
Processing rules: [06 §5.2](06-architecture.md#52-photos).
- **Add (v0.4.1, #183, D-104):** *+ Foto* on the detail's hero picks one
  image at a time. It goes after the tea's other photos; a tea's first photo
  becomes its cover. An 11th is refused: at 10, *+ Foto* is greyed out with
  „Höchstens 10 Fotos". An image that can't be read shows „Das Foto konnte
  nicht gelesen werden." and adds nothing.
- **Cover everywhere:** the cover shows wherever a tea photo shows: *Meine
  Tees*, the detail, Start, the tea picker, the timer seal and the journal
  rows. The setup (SCR-04) and the session page (SCR-16) show no tea photo
  in v0.4.1. **v0.6.0 (D-112):** the session page's hero shows the cover
  while the session has no photos of its own (JRN-07); the setup stays
  without a photo.
- ***Als Titelbild* (v0.4.1, #186):** the shown photo's ⋮ on the hero makes
  it the cover; it shows at once everywhere the cover shows. Offered on every
  photo but the cover.
- ***Löschen* (v0.4.1, #186):** after „Foto löschen?" the photo's row goes and
  then its file. If it was the cover, the next photo by `position` becomes the
  cover (after the last one: the first); the last photo deleted → no cover,
  the tea shows its type leaf again and *+ Foto* is enabled.
- **Photo viewer (v0.6.1, [D-114](10-open-questions-and-decisions.md#d-114)):** a tap on the hero (tea detail,
  session page, vessel editor) opens the whole photo full screen on black,
  with pinch and double-tap zoom (up to 4×) and a swipe over the same
  photos; ← or back closes it on the photo last shown. A hero without
  photos opens nothing.
  - AC1: A portrait photo the 4:3 hero crops shows uncropped in the
    viewer, upright (#58 rotation).
- **A catalog tea's shop photo** is just its first photo (`CATALOG_COPY`): it
  can lose the cover or be deleted like any other.
- **Deleting a tea** removes its photo files, as before (TEA-04).

**TEA-06 — Favourites** `Must` (v0.4.0)
The detail's ⋮ *Favorit* marks or unmarks a tea; favourites show under
*Favoriten* in Start and the tea picker, and with a star in *Meine Tees*.

**TEA-07 — Tags** `Could` — **not planned** (D-100)
Free-form tags (1–30 chars, ≤ 20 per tea). No tag tables exist.

**TEA-08 — List** `Must`
SCR-08 („Meine Tees") rows: cover photo, name, secondary name, Sorte, average
rating, stock chip („38 g übrig"), freshness chip, favourite, „Rezept prüfen"
badge.
- **v0.4.0 (D-100):** every part but the rating (M4) and the freshness chip
  (v0.4.1); the stock chip reads „38 g", *Knapp* or *Leer*. Empty states:
  „Noch keine Tees · Im Katalog stöbern" (A7) and „Kein Tee passt zu deiner
  Suche." (A10). Smooth with 500 teas (NFR-PERF-03).
- **v0.4.1 (D-104):** the freshness chip (INV-06) after the stock chip.
- **v0.6.0 (D-112):** the average rating („★ 4,5") of the tea's rated
  sessions, to one decimal; nothing for a tea without a rating.

**TEA-09 — Search** `Must` (v0.4.0)
Over name, secondary name, vendor, region, cultivar and notes, ignoring case
and accents (GEN-10). It runs in memory over the list's rows: there is no
full-text index and no tags (D-100, 05 §3.1).

**TEA-10 — Filters** `Should` (v0.4.0)
Favoriten, *Kategorie* (`TeaCategory`: Grüntee, Oolong …), *Sorte* (the
tea's display Sorte: Gyokuro, Sencha …), Vorrat (*Knapp* / *Leer*),
Aromatisiert, *Archiviert* (only archived teas, each with
*Wiederherstellen*) (D-100). From v0.5.0 (#206) *Kategorie* also offers Rotbusch and Früchtetee when present; a catalog chai counts under its base tea's Kategorie.

**TEA-11 — Sorting** `Must`
*Zuletzt gebrüht* (default), *Name*, *Zuletzt hinzugefügt*, *Vorrat*
(low → high). The choice is remembered (`collectionSort`); empty teas are
last in every order (INV-05).
- **v0.4.0 (D-100):** these four. *Bewertung* needs M4's ratings and
  *Geöffnet seit* the editable opened date of v0.4.1; neither is offered in
  v0.4.0.
- **v0.6.0 (D-112):** *Bewertung*: the highest average first, teas without
  a rating after the rated ones (empty teas last, as in every order).

**TEA-13 — Tea detail** `Must`
SCR-09: hero photo, names, rating „★ 4,5 (12)", type chip, umami meter,
`StatTriplet`, primary *Brühen*, recipes, stock & cost, freshness, tasting
summary, recent sessions, notes, details, and for catalog teas the visible
reorder link „Beim Tee-Kontor nachbestellen" (INV-09).
- **v0.4.0 (D-100):** hero photo, names, Sorte chip, umami meter, a
  `StatTriplet` of the tea's sessions, last brew and stock; sticky *Brühen*
  (setup with the default recipe); REZEPTE (a tap opens the setup with that
  recipe, its pencil the recipe editor; *Rezept hinzufügen* up to 10);
  VORRAT („38 g übrig" with *knapp*/*leer*, *Bestand korrigieren*, INV-09's
  link); BESCHREIBUNG (seed and catalog descriptions, D-090); NOTIZEN and
  DETAILS only with content; pencil FAB → editor; ⋮ *Favorit*,
  *Archivieren*, *Löschen*. Opened from *Meine Tees* and from the setup's ⋮
  *Tee ansehen*. It updates at once after an edit, a brew or a correction.
- **v0.4.1:** costs and freshness. **M4:** rating, tasting summary, recent
  sessions.
- **v0.6.0 (D-112):** „★ 4,5 (12)" under the names (average and count of
  the rated sessions; hidden without a rating); **GESCHMACK** (the tea's
  average radar, JRN-03, and its five most frequent descriptors with their
  counts; only with data); **LETZTE SITZUNGEN** (the last three sessions,
  a tap opens the session page; with more, *Alle anzeigen* opens the
  Journal filtered to the tea).
- **v0.6.1 ([D-114](10-open-questions-and-decisions.md#d-114)):** ⋮ starts with *Im Katalog ansehen* for a tea whose
  `catalog_key` has a catalog tea (discontinued counts), opening its SCR-21
  above the detail. GESCHMACK's radar averages every infusion (JRN-03). A
  tap on the hero opens the photo viewer (TEA-05).

**TEA-14 — Duplicate** `Could` — **not planned** (D-100)

**TEA-17 — Recipe check** `Must`
Teas flagged „Rezept prüfen" show the reason and *Rezept bestätigen*
(confirm) / *Rezept bearbeiten*; editing or confirming clears the flag.
- **v0.4.0 (D-100):** saving a recipe, in the recipe editor (SCR-12) or with
  the setup's *Im Rezept speichern*, clears the flag (until v0.3.0 only
  *Rezept passt* did).

---

## 5. Built-in tea styles (STY)

**STY-01 — Built-in styles** `Must`
The app ships the style list in [08 §3](08-content.md#3-built-in-tea-styles)
(Japanese and other green teas plus generic other categories). Styles are
**not** browsable as a catalog; they provide: mapping targets for catalog teas
(CAT-06), umami level, freshness window, and fallback recipes; and templates
for manually added teas. From v0.4.0 (D-100) every row of 08 §3 ships,
the other categories included, for *Sorte wählen* (SCR-11).

**STY-02 — Versioned with the app** `Must`
Updates never change existing teas or recipes.

---

## 6. Brew recipes (PRF) — SCR-12

A **recipe** (*Rezept*; data entity `BrewProfile`) is: method, leaf amount,
water volume, base temperature, optional vessel, optional pre-alert override,
and an ordered list of steps (rinse, infusion, whisk), each with its own
pre-alert choice (D-093).

**PRF-01 — Recipes per tea** `Must`
1–10 recipes per tea, exactly one default.
- **v0.4.0 (D-100):** *Rezept hinzufügen* (up to 10), *Als Standard*, and
  delete, never the last recipe; deleting the default makes the next recipe
  the default. The recipe of the active brew session can't be deleted until
  the session ends (E15, D-101).

**PRF-02 — Defaults** `Must`
Catalog teas get the recipes derived by CAT-04; manual teas get the style's
recipes; teas without style get *Western 3 g / 250 ml / 85 °C / 3:00*.

**PRF-03 — Edit recipe** `Must`

| Field | Limits |
|---|---|
| Name | 1–40 chars (e.g. „Kyusu", „Kaltaufguss") |
| Method | `BrewMethod` |
| Vessel | optional; selecting one sets water to its volume |
| Leaf | 0,1–100 g, step 0,1 g |
| Water | 10–3000 ml, step 5 ml |
| Base temperature | 5–100 °C in 5 °C steps; „kalt" (cold) for ≤ 10 °C |
| Pre-alert | „Wie in Einstellungen" (default) or Aus / 5 … 30 s in 5 s steps: the recipe's base value B for TMR-23 |
| Steps | 1–30, each with its pre-alert: *Automatisch* (default), *Aus* or 5 … 30 s (TMR-23, D-093) |
| Notes | 0–500 chars |

- AC1: Ratio shown live („4,0 g / 100 ml").
- **v0.4.0 (D-100):** the recipe editor (SCR-12), opened from the tea
  detail's REZEPTE. Choosing a vessel sets the water to its (usable) volume
  and scales the leaf by the ratio, as in the setup (WARE-03, WARE-04); the
  vessel list shows favourites first and hides archived vessels (WARE-02); a
  recipe's archived vessel shows „(archiviert)". Saving clears „Rezept
  prüfen" (TEA-17). A running brew keeps the steps it started with
  (rule 8).

**PRF-04 — Steps editor** `Must`
Add, delete, reorder steps; per step: kind (*Aufguss* / *Spülen* /
*Aufschlagen*), duration 5 s – 24 h (5 s steps
below 10 min, 1 min steps above), optional temperature (5 °C steps), note.
At least one non-rinse step. **v0.4.0 (D-100):** in the recipe editor,
steps are reordered by drag; durations use the Schritte-Sheet's picker and
hold-to-repeat. From v0.3.0 (D-098, #141) a step's duration is
set fast where it is edited (the Schritte-Sheet, SCR-31, also from the setup,
SCR-04): a tap on the time opens „Dauer wählen" (SCR-07's picker, the same
bounds and increments), and a held − / + repeats and speeds up, one increment
per repeat.

**PRF-05 — Generate series** `Should` — **not planned** (D-100)
First duration + increment × count, optional +5 °C per step.

**PRF-06 — Reset** `Should`
*Aus Katalog zurücksetzen* (catalog teas) or *Auf Standard zurücksetzen*
(style default).
- **v0.4.1 (D-100):** *Aus Katalog zurücksetzen* resets the recipes and the
  description after a confirmation; stock, photos, notes, favourite and
  journal stay. Not offered when the tea is no longer in the catalog.
- **As built (v0.4.1, D-104, #184):**
  - **Recipes:** the catalog's recipes come first, its default first; the
    user's own recipes (`source` USER) follow in their order, unchanged.
  - **Default:** if the default was a catalog recipe, the catalog's new
    default becomes the default; otherwise the user's default stays.
  - **Limit:** if the total would exceed 10, the catalog recipes beyond the
    limit are left out, and the confirmation says how many.
  - **Description:** the catalog tea's; when the catalog tea has none, the
    tea keeps its current description.
  - „Rezept prüfen" (TEA-17) is cleared; stock, photos, notes, favourite,
    archive flag and sessions stay.
  - **All or nothing:** a recipe write the repository refuses rolls the whole
    reset back; it fails and nothing changes.
  - **Rule 8:** offered during a brew of the tea too. The running session
    keeps its own steps; its recipe's row is rewritten, never deleted, so the
    session keeps its link.
  - **Recipe links (accepted):** the old catalog recipes' ids are reused for
    the new catalog recipes at new positions, so a finished session's recipe
    link may point to a different catalog recipe after a reset.

**PRF-07 — Learn from sessions** `Must` (v0.6.0, [D-112](10-open-questions-and-decisions.md#d-112))
After a session with **deliberate changes**, *Zeiten ins Rezept übernehmen*
offers to write them into the recipe used: on the „Gespeichert" sheet
(SCR-06) and, while it still applies, under the step table of the session
page (SCR-16).
- **Deliberate changes:** an infusion ended early with *Aufguss jetzt
  beenden*, or steps edited or added in the Schritte-Sheet without *Im
  Rezept speichern*. Pouring late after the alarm (overtime) never counts.
  A step that *Sitzung beenden* ended while it ran counts as ended early
  too (PO, D-112 Q21); so does a rinse.
  Until D-112 any difference of more than max(5 s, 10 %) counted, late
  pouring included.
- **The proposal** is the session's step plan as it ended (kinds,
  temperatures, pre-alerts, edited and added steps; never started steps as
  planned), with each early-ended step's time replaced by its recorded time,
  rounded to 5 s (to 1 min for steps from 10 min; at least 5 s).
- **It applies** when the session has a recipe that still exists and whose
  tea is in the collection, the offer was not accepted for this session, the
  recipe has not been changed since the session ended, and the proposal
  differs from the recipe's steps.
- **Accepting** writes the proposal into the recipe as *Im Rezept speichern*
  does and marks the session (`times_applied`); the line then reads „Rezept
  aktualisiert.". Unlike *Im Rezept speichern* it keeps the recipe's step
  notes by position: a step takes the note of the recipe's step at its
  position when both have the same kind (PO, D-112 Q22). Ignoring it is
  fine; there is no undo and no setting (SET-18 is retired).
- AC1: a session brewed as planned and poured 20 s late shows no offer.
- AC2: a first infusion ended at 0:48 of 1:00 offers the recipe with 0:50
  for that step.
- AC3: after accepting, or after any later change of the recipe, the offer
  is gone from that session's page.
- AC4: the rule is a pure function with unit tests; session completion and
  the timer are not changed.
- AC5: accepting an offer for a recipe whose second step has a note keeps
  that note when the proposal's second step has the same kind.

---

## 7. Teaware (WARE) — SCR-13, SCR-14

**WARE-01 — Manage vessels** `Must`
Name, type, volume (10–3000 ml), usable volume, material, notes, photo,
favourite.
- **v0.4.0 (D-100):** *Einstellungen › Teegeschirr* (SET-30) lists the
  vessels with type icon, name and volume (SCR-13) and opens the editor
  (SCR-14) for every field but the photo (v0.4.1). The seed vessels are
  edited like any other. A vessel is **archived, never deleted**:
  *Archivieren* hides it from the pickers while recipes and journal entries
  keep it; archived vessels sit in their own part of the list with
  *Wiederherstellen*. The vessel of the active brew session can't be
  archived until the session ends.
- **v0.4.1 (D-104, #187):** one photo per vessel, at the top of the editor:
  *Foto hinzufügen* opens the Android photo picker (06 §5.2), the picked image
  is processed like a tea photo; *Foto ersetzen* swaps it (the old file is
  deleted); *Foto entfernen* removes it. Like every field it is stored only
  with *Speichern* and counts as a change for „Änderungen verwerfen?"; an
  image that can't be read then (e.g. its picker grant is gone after a process
  death) is skipped, the rest is saved and a short toast says „Das Foto konnte
  nicht gelesen werden."; the pick's read grant is made persistable when it
  arrives. The
  *Teegeschirr* list shows the photo in place of the type icon (TalkBack:
  „Foto von *[name]*"); the setup's vessel list stays unchanged.

**WARE-02 — Vessel picker** `Must`
In brew setup and recipe editor: favourites first; archived vessels are not
offered.
- **v0.4.0 (D-100):** the setup's *Gefäß* list ends with *Neues Gefäß …*,
  which opens the vessel editor and, when saved, selects the new vessel. A
  recipe whose vessel was archived keeps it: the setup shows „Kyusu
  (archiviert)" with water and leaf unchanged; another vessel can be chosen,
  the archived one isn't in the list.

**WARE-03 — Scaling** `Must`
Choosing a vessel sets the water volume to the vessel's brewing volume and
scales leaf by the recipe's ratio (BRW-03). From v0.4.0 the recipe editor
does the same (PRF-03).

**WARE-04 — Usable volume** `Should` (v0.4.0)
Optional usable brewing volume (e.g. 180 ml kyusu brews 150 ml), used for
scaling if set.

**WARE-06 — Seed teaware** `Must`
M1 ships the PO's vessels: „Kyusu" (KYUSU, 300 ml, usable 250 ml, at home)
and „Becher mit Sieb" (MUG_INFUSER, 400 ml, usable 400 ml, at work).
- **v0.2.1 (D-093):** seven more seed vessels with typical sizes, named by
  usable volume: „Kyusu 120 ml", „Kyusu 180 ml", „Kyusu 300 ml", „Kyusu
  360 ml" (KYUSU; capacity 150, 220, 350, 420 ml) and „Becher 250 ml",
  „Becher 300 ml", „Becher 350 ml" (MUG_INFUSER; capacity 300, 350,
  400 ml). The two M1 vessels stay; they are named „Kyusu 250 ml" and
  „Becher mit Sieb 400 ml", renamed on an existing install only while
  they still carry their M1 names (Q18). A fresh install seeds all
  nine; an existing install gets the seven added once at the first start of
  v0.2.1 (fixed IDs, inserted only when absent, 05 §6
  `seedVesselsV2Added`). Free-size vessels come with M3's editor (WARE-01).

**WARE-05 — Default vessel per method** `Could` — **not planned** (D-100)
E.g. KYUSU → „Kyusu 180 ml".

---

## 8. Brew setup (BRW) — SCR-03, SCR-04

**BRW-01 — Start a brew** `Must`
Entry points: Home *Aufguss starten* (tea picker → setup), tea detail
*Brühen* and its recipes (→ setup, v0.4.0), the session page's *So nochmal
brühen* (→ setup prefilled, BRW-06, v0.4.0), and Home
*Nochmal brühen* (→ **directly to the timer**, BRW-11).

**BRW-11 — One-tap brew again** `Must`
Home *Zuletzt gebrüht* shows the **6 most recently brewed distinct teas**
(non-archived), each with its last session's date, first-step duration and
temperature (v0.5.1, D-110; until v0.5.0 three tiles under *Nochmal brühen*
and, apart from them, the last three sessions). Tapping one (*Nochmal
brühen*) starts a session immediately and opens the timer
in READY with the parameters of that tea's last session (recipe, vessel, leaf,
water, temperatures, step durations as planned). A session finished early
starts again with its started steps only (D-062), though from v0.4.0 it keeps
its never-started ones for BRW-06 (Q-19). The kettle hint shows the
temperature. Timer ⋮ › *Brühen anpassen* opens the setup screen for this
session (only while no step has started). A known tea starts in **2 taps**
(tea → *Start*).
- **Leaf and water on READY (v0.2.2, D-097).** Every READY state of a brew
  shows the session's leaf and water next to the kettle hint („4,0 g Tee ·
  250 ml Wasser"), so a tea started from *Nochmal brühen* shows how much to
  weigh without opening the setup (SCR-05).
- **Stock on READY (v0.3.0, D-098).** Under that line every READY of a brew
  shows the tea's stock as in the setup (INV-05): „38 g übrig", „14 g übrig ·
  knapp" or „Leer". No line for the quick timer, a session whose tea is not
  in the collection, or when the stock can't be read; reading it never
  delays the timer (rule 8).

**BRW-02 — Setup screen** `Must`
Shows tea, recipe chips, vessel, water, leaf, ratio lock, base temperature,
step list, effective pre-alert, *Brühen starten*. Changes apply to this
session only unless *Im Rezept speichern* is tapped; this includes each
step's pre-alert (TMR-23, D-093).
- **Stock correction (v0.2.1, D-093):** a tap on „38 g übrig" opens a small
  sheet „Wie viel ist noch da?" where the new total is entered in g (0,1 g
  steps; 0 allowed = leer); *Speichern* stores the difference as a
  correction (INV-03) and the setup shows the new amount.
- **Im Katalog ansehen (v0.3.0, D-098):** for a tea with a catalog entry
  the top bar's ⋮ opens its SCR-21; back returns to the unchanged setup.
- **Tee ansehen (v0.4.0, D-100):** the ⋮ is shown for every tea of the
  collection and offers *Tee ansehen* (the tea detail, SCR-09) above *Im
  Katalog ansehen*; back returns to the unchanged setup.

**BRW-03 — Ratio scaling** `Must`
With ratio lock (default on), changing water or vessel rescales leaf (0,1 g);
changing leaf rescales water (5 ml). Times and temperatures never scale.

**BRW-06 — Brew from a past session** `Should` (v0.4.0, D-100)
*So nochmal brühen* on a session's page (SCR-16) opens the setup prefilled
with the session as brewed: tea, vessel (shown „(archiviert)" if archived),
leaf, water and every planned step at its **set time** (*geplant*, including
Schritte-Sheet changes and steps never started, which a session keeps
from v0.4.0 on; an older session prefills only its stored steps, Q-19,
D-100) with its kind,
temperature and Vorwarnung; never the measured time. The recipe is changed
only by *Im Rezept speichern*.
- **Recipe:** the session's recipe while it exists; otherwise *Ohne
  Rezept*, and *Im Rezept speichern* saves a new recipe. *Ohne Rezept* keeps
  the session's own Vorwarnung base B, and so does the new recipe (D-101).
  *Im Rezept speichern* into the session's recipe writes its method and base
  temperature with the steps, so every step keeps its temperature.
- **Archived tea:** the button is greyed out with „Tee ist archiviert", and
  *Wiederherstellen* is offered.
- **Deleted tea:** no tea link and no button; the page stays readable.

**BRW-08 — Stock warning** `Should`
„Nur noch 3 g übrig" if stock is below the leaf amount.
- **v0.2.2 (D-097):** the setup's stock line marks a *Knapp* tea (INV-05):
  „14 g übrig · knapp".
- **v0.4.0 (D-100):** the warning ships. When the stock is known, above 0 g
  and below this brew's leaf, the stock line reads „Nur noch 3 g übrig"
  (with „· knapp" when *Knapp*) and follows leaf changes. It never blocks
  *Brühen starten*. A *Leer* tea keeps „Leer".

**BRW-09 — Freshness hint** `Could`

**BRW-10 — Kettle hint** `Must`
The setup and every READY state show the step temperature prominently as a
kettle instruction: „Wasserkocher auf 60 °C". For temperatures ≤ 10 °C the
hint reads „Kaltes Wasser" (cold brew) or „Eiswürfel" (ice brew) instead.

---

## 9. Steep timer & sessions (TMR) — SCR-05, SCR-06, SCR-07

Full behaviour: [04 · Brew Timer](04-brew-timer.md).

**TMR-01 — Start session** `Must`
*Brühen starten* creates an IN_PROGRESS session and opens the timer in READY
for the first step.

**TMR-02 — Countdown** `Must`
Large tabular digits (mm:ss, h:mm:ss ≥ 1 h) in a thin ring, caption, step
label „Aufguss 2 von 4", step temperature. The top bar's ← and ⋮ stay
legible on any tea photo, also a bright one (SCR-05, v0.2.1, D-093).

**TMR-03 — Pause / resume** `Must`
A cream pill button *Pause* / *Weiter* below the ring (as in the mockup) is the
only primary control while running.

**TMR-05 — End step early** `Must`
Overflow ⋮ › *Aufguss jetzt beenden* ends the running step, records the actual
time and moves on (no alert).

**TMR-06 — Alert** `Must`
At zero: sound and vibration **following the phone's ringer mode** (normal →
sound + vibration; vibrate → vibration; silent → no sound/vibration, visual
only), repeated for up to ~15 s or until acknowledged. The alert
notification uses a **full-screen intent** (short and long steps, all ringer
modes): the screen turns on and SCR-05 in ALERTING is shown over the lock
screen; when the phone is in use it appears as a heads-up „Jetzt
abgießen!". Requires `USE_FULL_SCREEN_INTENT` (checked in ONB-02/TMR-31;
without it: heads-up only). Screen shows overtime („+0:07") and a
large *Abgegossen* button. From 15 s of overtime on, the overtime is shown
prominently (large „+2:30 überzogen" in the alert's ink, marked with a warm
accent; never warm-accent text on the alert surface, D-055) so a missed
alert is obvious when returning to the app.

**TMR-07 — Acknowledge** `Must`
*Abgegossen* (in app, notification, or Live Update) stops the alert, records
the actual time (including overtime) and moves to READY for the next step or
FINISHED.

**TMR-08 — Wait for the user between steps** `Must`
READY never starts automatically. It shows the next step („Aufguss 2 ·
0:20 · 60 °C"), the kettle hint (BRW-10) and a large *Start* button; the whole
ring is also tappable.

**TMR-09 — Skip step** `Should` (overflow).

**TMR-10 — Add infusion** `Must`
In FINISHED and via overflow (not in ALERTING, D-055): *Weiterer Aufguss* appends a step (last
temperature; last duration + 50 %, rounded per PRF-04: 5 s below 10 min,
1 min above), editable before start in the Schritte-Sheet (SCR-31).

**TMR-11 — Edit remaining steps** `Should` (overflow ⋮ *Schritte
bearbeiten* → Schritte-Sheet SCR-31, not in ALERTING (D-055); this session
only unless *Im Rezept speichern*, each step's pre-alert included; the
running step's pre-alert is editable too, TMR-23, D-093).

**TMR-12 — Finish & auto-log** `Must`
Finishing early (e.g. after infusion 2 of 3) is a normal session; the
journal shows „2 von 3 Aufgüssen". Planned steps that were never started are
not recorded as skipped and do not count as a time difference for PRF-07.
From v0.4.0 their rows are kept without an actual time (until v0.3.0 they
were deleted at finish; 05 §3.7, Q-19, D-100), for *So nochmal brühen*
(BRW-06).

*Sitzung beenden* (FINISHED state or overflow) completes the session; it is
saved to the journal automatically **without a summary screen**. A bottom
sheet (SCR-06) appears for 10 s and stays reachable from the journal:
„Gespeichert im Journal" with *Notizen hinzufügen* (opens SCR-17) and — only if
PRF-07 applies — *Zeiten ins Rezept übernehmen*. Ignoring it is fine.
- **v0.6.0 (D-112):** the sheet carries five stars (JRN-02: one tap rates,
  ignoring it skips) and both actions; *Notizen hinzufügen* opens the
  session page (SCR-16), where everything is entered in place. A session
  the app finished on its own (`auto_finished`) shows no sheet, as before.
- AC1: Sessions with no completed non-rinse step are discarded after
  confirmation.

**TMR-13 — Discard session** `Must` (overflow, with confirmation).

**TMR-14 — Long waits and auto-finish** `Must`
A session may wait in READY/PAUSED/FINISHED for hours (e.g. a second mug
later at work).
- The idle time counts from the last interaction or from the moment the
  session started waiting, whichever is later (a step ended by the overtime
  timeout starts a new idle period; D-053).
- After **30 min** idle the timer service stops and the Live
  Update becomes a quiet, regular ongoing notification („Hisui · Aufguss 2
  bereit" with *Start*).
- At **midnight** — the first local midnight in the session's time zone at
  least 6 h after it became idle — a waiting session is finished and logged
  (marked auto-finished) with the notification „Sitzung gespeichert"; a
  session without any completed non-rinse step is discarded silently
  instead.
- When another session is started with *Beenden & neu starten* (TMR-21), the
  active session — waiting or not — is finished as by *Sitzung beenden* and
  logged with the notification „Sitzung gespeichert" instead of the saved
  sheet; without a completed non-rinse step it is discarded (04 §3.2,
  D-054).
- **Per session (v0.5.0, D-108).** With two brew sessions each has its own
  idle time, 30-min quiet notification and midnight; one session's waiting,
  interaction or end never moves the other's. Each is logged as its own
  journal entry with its own stock deduction and cost.

**TMR-15 — Background & lock screen** `Must`
The timer continues when leaving the app or locking the phone.

**TMR-16 — Survive process death and reboot** `Must`
See [04 §7](04-brew-timer.md#7-persistence--recovery).

**TMR-17 — Keep screen on, dimmed while steeping** `Must`
While SCR-05 is visible the screen stays on (SET-17). While a step is
RUNNING and the screen is untouched for 10 s, the window brightness dims to
~20 % (SET-25, default on); it returns to normal at the pre-alert, at the
alert, on any touch, and in READY/PAUSED/FINISHED.

**TMR-18 — Long steps (cold brew, ice brew)** `Must`
Steps ≥ 10 min (always including cold and ice brew) use exact alarms
without keeping a service running; no pre-alert; the Live Update shows
„Fertig um 14:30".
- **v0.3.0 (D-098, #122):** a long step is every step of a cold or ice brew
  and any step of **60 min or more** (threshold 3600 s, was 600 s). Hot
  steps of 10–59 min are short steps: ring countdown, the service kept,
  the countdown Live Update and the Vorwarnung (TMR-23).
  - AC1: A 15-min hot step plays its automatic Vorwarnung („Auto · 10 s")
    and shows „Gleich fertig …"; a 60-min hot step and a cold brew show
    „Fertig um …" and have none.
  - AC2: The duration rounding of PRF-04 (5 s below 10 min, whole minutes
    from 10 min) does not change.

**TMR-19 — Whisk steps** `Must` („Zügig in M/W-Form aufschlagen").

**TMR-20 — Rinse steps** `Must` („Spülen – Wasser weggießen"; button
*Weggegossen*).

**TMR-21 — Second session** `Must`
Starting while another is active: „Erst die *Gyokuro*-Sitzung beenden?"
(*Beenden & neu starten* / *Abbrechen*).
- **v0.3.0 (D-093, D-098):** one active brew session and one active quick
  timer (TMR-22) may run at the same time; either starts while the other
  runs, without this dialog. Only a second brew session asks as above; it
  never touches the quick timer. Until v0.3.0 one timer at a time.
  - AC1: *Aufguss starten*, *Nochmal brühen* and the setup start a brew
    while a quick timer runs, without a question.
  - AC2: *Sitzung beenden*, *Sitzung verwerfen* and every step action leave
    the quick timer running; its *Abbrechen* leaves the brew running.
  - AC3: Both survive swiping the app away, process death, a reboot and an
    app update (04 §7).
- **v0.5.0 (D-108): two brew sessions.** A brew of another tea starts
  while one brew session runs, without a question, as the second brew
  session (slot `BREW_2`, 04 §3.3); both keep their plan, leaf, vessel and
  step progress, and every action acts on its own session only. Starting a
  tea that already has an active session (*Nochmal brühen*, the setup, a
  recipe) opens that session instead of a second one. While two timers are
  active, any start asks „Welchen Timer beenden?" with one button per active
  timer (the tea name or *Schnelltimer*) and *Abbrechen*; the timer chosen
  ends as by *Beenden & neu starten* (a session logged with „Sitzung
  gespeichert", or discarded silently without a completed non-rinse step;
  the quick timer as by *Abbrechen*), then the new one starts. A second
  quick timer still asks „Schnelltimer beenden und neu starten?" (TMR-22).
  - AC4: With a green tea waiting between infusions, a herbal tea's
    session starts without a question; the green tea continues later where
    it stopped.
  - AC5: A third start offers both active timers and *Abbrechen*; ending one
    logs or discards it as above, never the other.
  - AC6: Two brew sessions survive swiping the app away, process death, a
    reboot and an app update, each in its own slot (04 §7).
  - **Screens (v0.5.0-03, #201).** A start of another tea while one timer
    is active opens the new session's timer (the second brew's, not the
    first's). A start of a tea with an active session opens that session,
    asking nothing. „Welchen Timer beenden?" is asked by *Nochmal brühen*,
    the setup's *Brühen starten* and SCR-07's *Start* (reached from Start's
    *Schnelltimer*); one full-width button per active timer in the slot
    order, *Abbrechen* keeps both. During an alert no question shows: the
    alerting timer opens instead (rule 8). *Brühen anpassen* replaces the
    waiting session in its own slot.

**TMR-22 — Quick timer** `Should`
SCR-07: countdown without a tea: chips 0:30, 1:00, 1:30, 2:00, 3:00, 5:00,
8:00 (from v0.2.2, D-097) and custom; from v0.6.1 ([D-114](10-open-questions-and-decisions.md#d-114)) the chips are
1:00, 2:00, 3:00, 5:00, 8:00, 10:00, 15:00 (still opening at 1:00); same alert, pre-alert (global setting,
automatic rule of TMR-23) and Live Update; not logged.
- **Pre-alert (v0.2.2, D-097).** The screen before *Start* has the
  *Vorwarnung* selector of the brew setup (*Automatisch*, *Aus*, 5–30 s),
  remembered for the next quick timer, and its ring shows the pre-alert
  mark. The pre-alert sounds and shows like a brew step's.
  - AC1: A 2:00 quick timer with *Vorwarnung* 10 s and *Automatisch* shows
    the mark at 0:10 while it runs and plays the Vorwarnton at 0:10 left,
    screen on or off (fixes the silent pre-alert the PO found in v0.2.1).
- **Alongside a brew (v0.3.0, D-093, D-098).** A quick timer starts from
  Start's *Schnelltimer* while a brew runs, without a question (not from the
  timer's ⋮ or a notification). A second quick timer asks „Schnelltimer
  beenden und neu starten?" (*Beenden & neu starten* / *Abbrechen*); the
  brew is never touched.
  - AC1: Each timer screen shows the other active timer in a slim row
    („⏱ Schnelltimer 2:14", „🍃 Tanabata · Aufguss 2 · 0:48") with its own
    *Pause*/*Weiter*; a tap opens it, back returns (SCR-05, SCR-07).
  - AC2: The active brew bar shows both countdowns (SCR-02).
  - AC3: The lock screen's Live Update shows the timer that ends next; the
    other is a quiet notification (04 §8.3).
  - AC4: **Alarm order.** The brew alarm comes first. A quick alarm due
    while the brew alarm sounds waits; a ringing quick alarm gives way at
    once to a brew alarm. The waiting one rings as soon as the brew alarm
    has stopped sounding (its *Alarmdauer*, SET-14, ended, or *Abgegossen*;
    D-109), with its overtime („+0:12"), however long it waited, and ends
    silently only 10 min after it started ringing. Meanwhile the brew stays
    „Jetzt abgießen!" without sound. A quick alarm due after the brew
    alarm's sound has ended rings at once. Two sounds never play at once
    (04 §9.3).
- **Two brews (v0.5.0, D-108).** AC1–AC4 hold for two brew sessions too:
  the bar and each timer screen show both (SCR-02, SCR-05: the other brew
  in the slim row, „🍃 Hisui · Aufguss 2 · 0:48", with its own
  *Pause*/*Weiter*; a tap opens it, back returns); the Live Update is the one
  that ends next. A held brew reads „wartet" in the bar and the row and
  „Fertig, wartet auf den anderen Aufguss" in its quiet notification. **Alarm
  order:** the first alarm rings; a brew alarm due while the other brew
  alarm sounds waits and rings as soon as that alarm has stopped sounding
  (D-109; its *Alarmdauer* ended, or *Abgegossen*), with its overtime,
  ending silently only 10 min after it started ringing; the first stays
  „Jetzt abgießen!" without sound. Of two brews due at once (a late alarm,
  a long process death) the one whose deadline came first rings. A brew
  still goes before a quick timer due at the same time, and a ringing quick
  alarm still gives way to a brew alarm.

**TMR-23 — Pre-alert tick** `Must`
A soft tick sound (plus short haptic) **N seconds before the end** of each
step so the kyusu can be picked up.
- **Per step (v0.2.1, D-093).** Each step has its own pre-alert:
  *Automatisch* (default), *Aus* or 5–30 s in 5 s steps, set in the
  Schritte-Sheet (SCR-31) for this session; it goes into the recipe's step
  (PRF-03) only via *Im Rezept speichern*, like every other step change.
- **Automatic rule:** B = the recipe's pre-alert, else the global setting
  (SET-10: Aus or 5–30 s in 5 s steps; default **10 s**), frozen at the
  session's start; d = the step's planned seconds;
  **N = min(B, 5 · ⌊d / 15⌋)** — a third of the step rounded down to whole
  5 s, never more than B; N = 0 means no pre-alert. Examples with B = 10 s:
  0:10 → none, 0:20 → 5 s, 0:30 and longer → 10 s.
- Skipped if the step is shorter than N + 5 s (only an explicit value can
  hit this), for RINSE steps shorter than 15 s, and for long steps
  (≥ 10 min, TMR-18; from v0.3.0 cold or ice brew, or ≥ 60 min, D-098).
- The tick follows the ringer mode like the alert (vibrate mode → short
  vibration only). From v0.3.0 both play on the alarm volume with the
  *Lautstärke* of SET-27 (04 §9.1, D-098).
- **Two timers (v0.3.0, D-098).** A Vorwarnung never cuts an alarm. One due
  while the other timer's alarm or Vorwarnung sounds plays after it, only
  if at least 1 s of its step is left.
- The Live Update / timer caption switches to „Gleich fertig…" at the
  pre-alert.

**TMR-30 — Accuracy** `Must`
Alerts and pre-alerts within 1 s of target (typical ≤ 250 ms), screen on or
off, app foreground or background.

**TMR-31 — Reliability banner** `Must`
If notifications are blocked, Live Updates are not allowed
(`canPostPromotedNotifications()` false), full-screen alerts are not allowed
(`canUseFullScreenIntent()` false), the battery is restricted for the app, or
exact alarms are unavailable, the timer and Home show „Der Timer ist
eventuell unzuverlässig – *Beheben*" deep-linking to the relevant setting.
Now Bar presence is not checked (best effort, TMR-41).
- **Alarm volume (v0.3.0, D-098):** the phone's alarm volume at 0 is a check
  of its own: „Alarmlautstärke ist aus" with *Beheben* (the system's sound
  settings), in the banner and in SET-24.

**TMR-41 — Live Update** `Must`
On Android 16, the active timer is a promoted ongoing notification (Live
Update) with progress-style segments per step, visible as status bar chip and
on the lock screen. Appearing in Samsung's Now Bar is **best effort** (One UI
may require the user to enable live notifications per app). Details:
[04 §8.3](04-brew-timer.md#83-live-update).

---

## 10. Journal (JRN) — SCR-15 … SCR-17, SCR-30

**v0.6.0 ([D-112](10-open-questions-and-decisions.md#d-112)).** The journal
part of M4. One rule holds for JRN-02 to JRN-07: **every tasting input is
optional and skippable without friction.** Nothing asks for a rating or a
note, no field is required, nothing marks or reminds of a session without
them, and such a session is complete. All of them are entered in place on
the session page (SCR-16) and saved at once.

**JRN-01 — Automatic logging** `Must`
Every finished session is stored with tea, date/time, recipe used, vessel,
leaf, water, and per step the planned and actual time and temperature.

**JRN-02 — Rating** `Should` (v0.6.0)
Optional 0,5–5,0 stars in half steps, on the „Gespeichert" sheet (SCR-06)
and on the session page.
- A tap on a star sets that many whole stars; a second tap on the same star
  takes half a star off (4,0 → 3,5); a third removes the rating.
- AC1: one tap rates a session; no tap leaves it unrated, with nothing
  shown or asked later.
- AC2: TalkBack reads „Bewertung, 3,5 von 5 Sternen", adjusts in half steps
  and can remove the rating.
- **v0.7.1 (planned, [D-116](10-open-questions-and-decisions.md#d-116)):**
  a session gets two optional ratings, „Tee" and „Zubereitung"; a tea's
  rating everywhere is the Ø of its *Tee* ratings; existing ratings are
  copied to both. Details are grilled in the v0.7.1 specify session; until
  then this requirement stands as written.

**JRN-03 — Taste profile** `Should` (v0.6.0)
Optional 0–5 sliders: Umami, Süße, Bitterkeit, Adstringenz, Körper, Aroma;
radar chart on the session page and, as the average, on the tea detail.
- An untouched slider is unset, not 0; *Zurücksetzen* clears all six.
- The radar draws once three axes are set; unset axes sit at the centre.
- A tea's average is per axis, over its sessions that set that axis.
- **v0.6.1 ([D-114](10-open-questions-and-decisions.md#d-114)): taste per infusion.** The six values belong to
  each infusion (every ended step that is not *Spülen*), not to the session.
  They are entered on the timer screen for the infusion ended last, while
  the session runs (SCR-05), and on the session page for every infusion
  (SCR-16, a chip per infusion, *Alle* shows the average). *Zurücksetzen*
  clears the selected infusion's six. A v0.6.0 session's values become its
  first infusion's (umamit.db v7, 05 §9). The session's radar under *Alle*
  and the tea's average are per axis over every infusion that set that
  axis (the tea's: in all its finished sessions).
  - AC1: Six values set for the 1st infusion on the timer during the 2nd
    infusion are on the session page under „1. Aufguss" after the session
    ends, also when the app was killed in between.
  - AC2: An alert closes the timer's taste sheet in its first frame and is
    never delayed; values released before it are stored.
  - AC3: After the update a v0.6.0 session shows its taste under
    „1. Aufguss" and the tea's radar is unchanged.

**JRN-04 — Descriptors** `Should` (v0.6.0)
Multi-select chips from the German vocabulary ([08 §4](08-content.md#4-tasting-descriptor-vocabulary));
max 20 per session. Picked in the descriptor sheet (SCR-30); each tick is
saved at once. Custom descriptors are not built (an idea kept for later,
D-112, 09 §5).

**JRN-05 — Notes** `Must` (v0.6.0; ≤ 5000 chars)
A text field on the session page, saved 1 s after the last key and when the
field or the page is left. Notes per infusion (≤ 300) are not built (an idea
kept for later, D-112, 09 §5). **From v0.6.1 ([D-114](10-open-questions-and-decisions.md#d-114))** each infusion
has a short note (≤ 200 characters) beside its taste values, entered on the
timer's taste sheet and on the session page; saved like the session's
notes.

**JRN-07 — Session photos** `Should` (v0.6.0)
Up to 6 own photos per session through the Android photo picker (no camera),
in the session page's hero with *+ Foto*, processed like tea photos
([06 §5.2](06-architecture.md#52-photos), #58 rotation). There is no cover:
they show in the order added; the hero's ⋮ deletes the photo shown. A 7th is
refused („Höchstens 6 Fotos"). They go with their session when it is
deleted. Like a tea's own photos they are not in the device backup (D-104).

**JRN-08 — Journal list** `Must`
Newest first, grouped by day („Heute", „Gestern", „Montag, 12. Oktober").
Row (as in mockup v2): thumbnail, tea name, time of day („09:12"), meta line
„55 °C · 1:00 · 3 Aufgüsse" (the first infusion's temperature as brewed, its
own or else the session's base, and its planned time · count; the base alone
until v0.4.0-rc.1, [D-102](10-open-questions-and-decisions.md#d-102)),
rating if present. In-progress session pinned on top.
- **v0.6.0 (D-112):** the rating shows as „★ 4,5". There is **no dot** for
  sessions without notes (planned until D-112). The thumbnail stays the
  tea's cover, also for a session with own photos. The empty journal shows
  asset A6 with „Deine erste Tasse wartet.".

**JRN-09 — Search & filters** `Should` (v0.6.0)
- **Search** over the tea's name, the notes (from v0.6.1 also the
  infusion notes, [D-114](10-open-questions-and-decisions.md#d-114)) and the chosen descriptors'
  labels, ignoring case and accents (GEN-10). It runs in memory over the
  journal's rows; there is no full-text index (D-112, as TEA-09).
- **Filters:** *Tee* (one of the teas that have sessions), *Bewertung*
  (*ab 3 ★*, *ab 4 ★*, *ab 4,5 ★*) and *Mit Notizen* (sessions with note
  text; from v0.6.1 also an infusion note). A date range and a filter by Sorte are not built.
- Search and filters are not remembered across app starts. While either is
  active the in-progress session is not pinned.
- AC1: smooth with 10 000 sessions (05 §10).

**JRN-10 — Session detail** `Must` with *Bearbeiten*, *Löschen*, *So nochmal brühen*.
- **v0.4.0 (D-100):** a tap on a past journal entry opens its page (SCR-16),
  read-only: the tea (tap → its detail), date and time, method, vessel,
  leaf / water / ratio, the step table (*geplant* / *tatsächlich* / °C) and
  *So nochmal brühen* (BRW-06). *Bearbeiten*, rating and taste
  follow in M4.
- **v0.4.0-rc.2 (D-102):** ⋮ *Löschen* on the page asks „Sitzung löschen?"
  and deletes a finished session with its steps and its BREW stock event
  (the deducted leaf is back in the stock); a session in progress is never
  deleted. No undo.
- **v0.6.0 (D-112):** the page carries the tasting inputs in place
  (JRN-02–JRN-05, JRN-07), a hero with the session's photos or else the
  tea's cover (TEA-05), the recipe offer while it applies (PRF-07) and ⋮
  *Bearbeiten* (JRN-11). Deleting a session also deletes its descriptors
  and its photos.
- **v0.6.1 ([D-114](10-open-questions-and-decisions.md#d-114)):** the page's ⋮ reads *Tee ansehen* (the tea's
  detail, SCR-09; not for a deleted tea), *Im Katalog ansehen* (the tea's
  SCR-21; only when the tea's `catalog_key` has a catalog tea), then
  *Bearbeiten* and *Löschen*. The tea name's link stays. GESCHMACK is per
  infusion and folds (JRN-03, SCR-16). A tap on the hero opens the photo
  viewer (TEA-05).

**JRN-11 — Edit session** `Must` (v0.6.0)
⋮ *Bearbeiten* on the session page opens the editor (SCR-17) for the
recorded data of a finished session, with *Speichern*:
- **Editable:** date and time of the start; vessel; leaf; water; each
  brewed step's recorded time and temperature (5 °C steps, GEN-12).
- **Fixed:** the tea, the recipe link, the method and the number of steps
  (D-112); never started and skipped steps show but cannot be changed.
- **Date and time:** the start, the end and every step's times shift by the
  same amount; never into the future. The BREW stock event moves with the
  end.
- **Leaf:** the session's BREW stock event deducts the new amount (a session
  without such an event gets none), so the tea's stock follows at once; the
  cost is set as at finish (INV-08: leaf × the tea's cost per gram now; for
  a tea that is gone it scales with the leaf).
- **Step time:** the recorded (*tatsächlich*) time; the planned time stays.
- A session in progress cannot be edited. Leaving with changes asks
  „Änderungen verwerfen?".
- AC1: after a leaf edit, stock, cost and the session page agree, also
  after an app restart.
- AC2: an edit never touches a session in progress or the `active_brew`
  rows (rule 8).

**JRN-12 — Manual entry** `Could` (session without the timer; an idea kept
for later, D-112, 09 §5).

**JRN-14 — Brew coach tips** `Could` ([08 §6](08-content.md#6-brew-coach-rules);
an idea kept for later, D-112, 09 §5).

---

## 11. Stock, cost & freshness (INV)

**INV-01 — Stock from purchase** `Must`
Adding a catalog tea with a package option (CAT-12) or entering a package size
manually creates an initial stock event with the package weight and price.
From v0.4.0 (D-100) the manual path is *Ohne Katalog* (TEA-01); a tea added
without a package gets no stock event.

**INV-02 — Auto-deduct** `Must`
Finishing a session deducts the leaf amount used (setting SET-15, default on);
discarding or deleting the session restores it.

**INV-03 — Manual adjustments** `Must`
*Nachkauf erfassen* (+g, price, date), *Bestand korrigieren* (set remaining),
history list (SCR-29).
- The user enters the new total in g (0 allowed = leer). A correction
  records an `ADJUSTMENT` event with the difference between that total and
  the current stock (none when equal); the history shows
  it as „Bestand korrigiert".
- **v0.2.1 (D-093):** the correction is first reachable from the brew setup
  (tap „… g übrig", BRW-02); SCR-29's history follows with M3.
- **v0.4.0 (D-100):** *Bestand korrigieren* also on the tea detail's VORRAT
  (the setup's sheet).
- **v0.4.1 (D-100):** the history (SCR-29) and *Nachkauf erfassen* for teas
  not from the catalog (catalog teas have it in SCR-21 since M2).
- ***Nachkauf erfassen* (v0.4.1, D-104, #181):** on every tea's detail
  (SCR-09 VORRAT), catalog teas included; their SCR-21/23 flow with the
  shop's sizes stays. A sheet takes the grams (required, as `GermanNumber`),
  an optional price in € and *Kaufdatum*, today by default and never later.
  It records one `PURCHASE` event of the grams with the price (none when left
  empty) and the package label („50 g"): bought today at now, on an earlier
  day at noon in the phone's zone, as INV-01's package
  (`StockRepository.recordPurchase`). A day after today is refused and
  nothing is written. The stock and the cost per gram follow from the events
  at once. A purchase after the stock reached 0 g is a new package for the
  next session's opening date (INV-06). An archived tea comes out of the
  archive in the same transaction, like SCR-23's repurchase (D-093 Q6; PO,
  v0.4.1 fix round).
- **History (v0.4.1, D-104, #185):** SCR-29 lists the tea's stock events
  from `observeEvents`, newest first, each on its day in the phone's zone:
  purchases (INITIAL and PURCHASE, „Kauf", grams and the price when
  recorded), corrections („Bestand korrigiert", the signed difference) and
  brews („Gebrüht", the grams used). A brew row opens its session page
  (SCR-16).
- **Delete (v0.4.1, D-104, #185):** a purchase (INITIAL or PURCHASE) or a
  correction can be deleted after a confirmation
  (`StockRepository.deleteEvent`, which returns whether it deleted). A
  `BREW` event is refused by the repository: it leaves only with its journal
  entry (D-102). The stock, the cost per gram and so the cost follow from the
  remaining events at once; the tea's `opened_date` is left as it is (only
  the editor changes it).

**INV-04 — Session edits** `Must` update the linked deduction.

**INV-05 — Stock status** `Must`
Leer (≤ 0 g), Knapp (≤ threshold, default **15 g** from v0.2.2, D-097; was
10 g), Vorrätig. Empty teas stay
in *Meine Tees* with a „Leer" chip and are sorted to the bottom in every sort
order; they are never archived automatically.
- **v0.5.0 (D-108, #197): the tea's own threshold.** The threshold is the
  tea's own *Knapp ab* (`tea.low_stock_grams`, whole grams 1–500, set on
  SCR-09's VORRAT) when it has one, else SET-16. The *Knapp* / *Leer* labels
  everywhere (the list, the detail, the setup's marker BRW-08), Start's card
  (INV-10), the reminder (INV-11) and the reorder highlight (INV-09 AC1)
  follow it at once. The labels stay with the tea's stock warning off
  (INV-10): they state the stock.

**INV-06 — Opened date & freshness** `Should`
*Geöffnet am* is set automatically (snackbar *Rückgängig*) on the first
session after a new package: i.e. when `opened_date` is empty, or when the
latest INITIAL/PURCHASE event is newer than `opened_date` and the stock had
reached 0 g before that purchase; freshness from the style's window: „Frisch",
„Bald genießen", „Über dem Höhepunkt".
- **Until v0.4.0:** the opened date is set automatically, without the
  snackbar; no freshness is shown.
- **v0.4.1 (D-100):** the freshness labels in the list and the detail, the
  *Rückgängig* snackbar, and the opened and best-before dates editable in
  the tea editor. The freshness period is the style's; there is no per-tea
  period.
- **The labels (v0.4.1, D-104):** counted in days from `opened_date` over the
  style's window (08 §3, `BuiltInStyles`); the thirds are whole days,
  rounded down. *Frisch* while the day is below two thirds of the window,
  *Bald genießen* until the window's end, *Über dem Höhepunkt* from then
  (Gyokuro, 60 days: *Frisch* days 0–39, *Bald genießen* 40–59, *Über dem
  Höhepunkt* from day 60). A `best_before` before today makes a tea *Über dem
  Höhepunkt*, opened or not (on the date itself it still counts as good). No
  label for an empty tea (*Leer*, INV-05; a tea without stock events is not
  empty), for an unopened one whose best-before has not passed, or by the
  opening date for a style without a window (*Andere / unbekannt*, or no
  style). `freshness_days` keeps no UI and is not read (D-100). Shown as
  `FreshnessChip` on the *Meine Tees* row (TEA-08), in the detail's FRISCHE
  (SCR-09) and as a small chip in the tea picker (SCR-03); Start is
  unchanged. TalkBack reads „Frische: Bald genießen" with the row. The pure
  rule is `FreshnessRule` in `core:domain`; a screen reads today when its
  data changes.
- **Rückgängig (v0.4.1, D-104):** the „snackbar" is a line on the
  „Gespeichert" sheet (SCR-06): after the session whose completion set
  `opened_date`, it reads „Packung als geöffnet markiert" · *Rückgängig*.
  *Rückgängig* writes back the value before, none or a date, so the next
  session counts as the first after the package again. No line when the
  completion left the date as it was. The change is remembered in memory
  only, for the sheet's 10 s; the completion's transaction reads and writes
  what it did in v0.4.0. A date changed since (the tea editor) is left
  alone, and the undo is offered once.

**INV-07 — Low stock & freshness reminders** `Could`
Weekly digest notification (off by default).
- **v0.3.0 (D-098):** low stock has its own daily reminder (INV-11); this
  digest keeps only freshness.
- **v0.5.0 (D-108):** the low-stock reminder's interval (SET-28) may be
  weekly too; this digest still keeps only freshness.

**INV-08 — Cost** `Must`
Cost per gram from purchase events (weighted by grams of each purchase); cost
per session = leaf × cost per gram. **A cup is poured water ÷ the cup size**
(SET-34, default 150 ml; from v0.7.0-rc.2, D-115 A1; before: one poured
infusion = one cup): cost per cup = session cost ÷ the session's cups. A
session's cups = Σ over its poured non-rinse infusions of `water_ml` ÷ cup
size, unrounded; a whisk-only session, or one whose every step was skipped,
counts one infusion; a session without `water_ml` keeps 1 per infusion.
Shown on tea detail („0,24 € pro Tasse") and in statistics.
- **Since v0.1.0:** the session's cost (`cost_cents`) is stored at
  completion.
- **v0.4.1 (D-100):** price per gram and cost per cup on the tea detail.
  **v0.7.0:** the statistics (INS-05).
- **v0.4.1 (D-104, #182):** the detail's VORRAT shows three lines below the
  stock, each in cents rounded half up:
  - **Price:** „12,40 € / 100 g", from the priced INITIAL and PURCHASE
    events weighted by grams (`costPerGramCents`).
  - **Estimate:** „ca. 0,24 € pro Tasse (Rezept)": the default recipe's
    leaf × price per gram ÷ its cups. **v0.7.0-rc.2 (D-115 A1):** its cups
    are the recipe's water × its non-rinse steps ÷ the cup size (SET-34);
    before, its non-rinse steps. A whisk-only recipe is one cup. Without a
    default recipe, no estimate.
  - **Average:** „Ø 0,27 € pro Tasse aus 5 Sitzungen" („aus 1 Sitzung"),
    from the first finished session with a `cost_cents`: Σ `cost_cents` ÷
    Σ cups of those sessions. A session's cups are its poured non-rinse
    steps (ended and not skipped; never-started rows don't count), each
    counting its `water_ml` ÷ the cup size (v0.7.0-rc.2, D-115 A1; before:
    one per step); a whisk-only session is one cup, and so is a priced
    session whose every step was skipped (its leaf was used). Changing the
    cup size changes the average at once (nothing is stored per session). Sessions without a cost and the
    running one are left out. The stored `cost_cents` stays as it was at
    completion; a later purchase changes only the price and the estimate.
  - **Cups by volume (D-115 Amendment 1, #268):** a cup is poured water ÷
    the **cup size** (*Tassengröße* in *Einstellungen*, `cupSizeMl`, default
    150 ml, 50–500 in 10 ml steps). A session's cups = Σ over its poured
    non-rinse infusions of `water_ml` ÷ cup size; whisk-only or all-skipped
    counts one infusion; without `water_ml` each infusion is one cup; never
    below 1 per session. Sums stay unrounded and display rounds half up. The
    estimate uses recipe water × its non-rinse steps ÷ cup size. Changing the
    setting recomputes every figure (nothing is stored per session); this
    replaces „one infusion = one cup" above.
  - **No price, no cost:** without a priced INITIAL or PURCHASE event none
    of the three lines shows (never „0 €"). The lines update at once after
    a purchase, a correction or a history delete.

**INV-09 — Reorder link** `Must`
SCR-09 shows „Beim Tee-Kontor nachbestellen" in VORRAT & KOSTEN (VORRAT in
v0.4.0, D-100) for every tea
with a `catalog_key`, so reordering takes one tap (D-072). It opens the
browser on the shop page of the **package size bought last**: the grams of the
tea's latest INITIAL or PURCHASE event matched to a `catalog_package` of its
`catalog_key` (`variant_url`). Without a match, or when that size is no longer
offered, it opens the product page (`product_url`).
- **AC1:** with stock *Knapp* or *Leer* (INV-05), the link is shown as a
  filled button; otherwise as a text link.
- **AC2:** teas without a `catalog_key`, and discontinued catalog teas (CAT-07
  AC4), show no link.
- **AC3:** the app makes no request itself; the browser opens the page
  (GEN-01).
- **v0.5.0 (D-108, #197):** with the tea's stock warning off (INV-10), the
  link stays a text link, *Knapp* or *Leer* or not.

**INV-10 — Low-stock card on Start** `Should` (v0.2.2, D-097)
When a tea's stock is *Knapp* or *Leer* (INV-05), Start's nudge slot (SCR-02)
shows a quiet card: „*Tanabata* wird knapp · 14 g übrig" („*Tanabata* ist
leer") with *Nachbestellen* (INV-09's link; only when INV-09 offers it) and
*Ausblenden*.
- AC1: One card at a time; with several such teas the one with the least
  stock. Archived teas and teas without stock events never show.
- AC2: *Ausblenden* hides the card for that tea until its next INITIAL or
  PURCHASE event; then the next most urgent tea, if any, shows.
- AC3: The card is part of the Start list; it never covers or delays an
  alert.
- **AC4, the stock warning (v0.5.0, D-108, #197):** each tea has a stock
  warning, on by default (`tea.low_stock_warning`, SCR-09's *Warnung*). A tea
  with it off never shows the card; the next most urgent tea takes the slot.
  A purchase clears *Ausblenden* (AC2) but never switches the warning back
  on; only the switch does. Its *Knapp* / *Leer* labels (INV-05) and the
  setup's marker (BRW-08) stay.

**INV-11 — Low-stock reminder** `Should` (v0.3.0, D-098; interval from v0.5.0, D-108)
At the time of SET-29 (default 18:00), daily or at SET-28's interval, one quiet notification
lists every tea that is *Knapp* or *Leer* (INV-05): „3 Tees werden knapp",
with lines „Tanabata · 14 g", „Hisui · leer"; one tea is named, „Tanabata
wird knapp" or, empty, „Hisui ist leer" (as INV-10 says it). A tap opens
Start.
- AC1: Teas hidden with *Ausblenden* (INV-10) are included until their next
  INITIAL or PURCHASE event; archived teas and teas without stock events
  never.
- AC2: No notification on a day without such a tea; never a sound,
  vibration or full-screen intent (channel „Vorrat", low importance).
- AC3: Switched off with SET-28 (default on; from v0.5.0 *Aus*, default *Täglich*). From v0.5.0 (D-108, #197) a
  tea with its stock warning off (INV-10 AC4) has no line, also after a
  purchase; a tea's own threshold (INV-05) decides its line like the card's.
- AC4: Inexact by design (within about 15 min); it never wakes the phone
  with an exact alarm and never disturbs a running timer or its alert
  (rule 8).
- AC5 (v0.5.0, D-108 point 7, #198): SET-28 is *Täglich · Alle 2 Tage ·
  Alle 3 Tage · Wöchentlich · Aus* (N = 1, 2, 3, 7). The interval counts
  from the date of the last reminder that was **posted**: a reminder is due
  on the first date at least N days later, at the set time. A due day with
  no tea due posts nothing and leaves the date, so the next day tries again.
  A run late on a due day posts at once; a run more than 30 min early waits;
  a DST change keeps the set time on the clock. A stored „on" reads as
  *Täglich* and „off" as *Aus* (settings `schemaVersion` 3, 05 §6).

---

## 12. Statistics (INS) — SCR-18

**v0.7.0 ([D-115](10-open-questions-and-decisions.md#d-115); grilled
2026-11-13, Q1–Q20).** Top-level tab „Statistik" with the title „Deine
Tee-Reise", a range selector (INS-07) and three tabs **Überblick ·
Bewertungen · Verbrauch** (as in the mockup). No database migration: every
figure is computed from the existing tables when read.

**v0.7.0-rc.2 ([D-115](10-open-questions-and-decisions.md#d-115) Amendment
1, the PO's review of rc.1; R1–R16).** Changes are marked „rc.2 (D-115 A1)":
cups by volume, a custom month range, foldable sections, a second donut,
*Bewertungen* rebuilt, *Geschmack* and *Beschreibungen* removed, a new
GETRUNKEN section, a compact layout. The split rating (JRN-02) and the
transition audit are v0.7.1 (D-116), not here.

**Counting rules (all INS).**
- A session counts when it is **finished** (`COMPLETED` or `MANUAL`; a
  session still `IN_PROGRESS` never counts), also when its tea was archived
  or deleted: it is counted under its snapshot (`tea_name`, `tea_type`).
  Sessions the app finished on its own (`auto_finished`) count too.
- A session belongs to the **day of its `started_at`** in its own `zone_id`
  (the journal's day).
- **Cups** as INV-08. **rc.2 (D-115 A1):** by volume: a session's cups = Σ
  over its poured non-rinse steps (ended, not skipped) of `water_ml` ÷ the
  cup size (SET-34, default 150 ml); a whisk-only session, or one whose
  every step was skipped, counts one infusion; a session without `water_ml`
  keeps 1 per infusion. Totals and bars sum the unrounded cups and round
  half up for display (whole cups; a session never shows below 1). A 1 l
  Kanne infusion counts ≈ 7 Tassen, a 120 ml Kyusu infusion ≈ 1. Changing
  the cup size recomputes every figure. (Before rc.2: one cup per poured
  non-rinse step.)
- **Taste** is per infusion (D-114): averages run per axis over the
  infusions that set that axis (and the v0.6.0 sessions without step rows,
  05 §3.6).
- **Folding (rc.2, D-115 A1, R8):** every section of the three tabs can be
  folded; see INS-08.
- The figures update while the screen shows, when a session finishes or is
  edited or deleted, and when a purchase or a tea changes.

**INS-01 — Activity (Überblick)** `Must`
- Big numerals: **sessions** in the range, **cups** in the range, and
  **teas** in the collection („24 Tees · +3 im Zeitraum": non-archived teas
  now; the plus counts teas created in the range and is hidden at 0).
  **rc.2 (D-115 A1, R10, R12):** the three numerals sit in **one row**; under
  *Gesamt* there is no „+n im Zeitraum" (every tea would be new).
- **Sessions per period** as bars (the periods of INS-07).
- **Streak** („Serie"), always over all time, never the range, shown
  neutrally: „7 Tage in Folge · längste 21 Tage · seit Beginn". A day counts
  with at least one finished session; the current streak includes today
  when today has a session, else it ends yesterday (a day without a session
  yet does not break it before the day is over); 0 shows „Keine Serie
  gerade". No flame, no reminder, no message when it breaks. **rc.2 (R13):**
  at 0 it reads „Keine Serie gerade · längste 21 Tage" (the longest only
  when there is one).
- **Most brewed:** the top 5 teas by sessions in the range („12×"); a tap
  opens the tea's detail (SCR-09); a deleted tea's row has no tap.

**INS-06 — Brewing habits (Überblick)** `Should`
Share of sessions in the range by **day part** — Morgen (05:00–10:59) ·
Mittag (11:00–13:59) · Nachmittag (14:00–17:59) · Abend (18:00–04:59) —
and by **weekday** (Mo–So), as two small bar rows with percentages.

**INS-03 — Tea types (Überblick)** `Must`
Share of sessions in the range by the tea's **Sorte** (`tea_type` snapshot:
Gyokuro, Sencha, Matcha, Darjeeling …) as a donut with legend and
percentages: the five largest, the rest as „Andere"; a session without a
Sorte counts as „Andere". Percentages are rounded to whole numbers and sum
to 100 (largest remainder).
- **Second donut „Tees" (rc.2, D-115 A1, R6):** the share of sessions per
  **tea**, after TEE-SORTEN on *Überblick*; the five largest named, the rest
  „Andere", as *Tee-Sorten* (legend, percentages as above); a deleted tea
  under its snapshot name; slice colours distinct within the donut.

**INS-02 — Ratings (Bewertungen)** `Must`
**rc.2 (D-115 A1, R4, R11):** *Bewertungen* has four parts, in this order. The
Ø headline („Ø 4,2 ★ · 18 von 25 Sitzungen bewertet"), the 5 ★–1 ★
distribution and „Am besten bewertet" are gone (replaced by the first part).
- **Tee-Rangliste:** every tea with **at least one rated session** in the
  range, best Ø first, each as „4,6 ★ · 5×" (the Ø over the range's rated
  sessions, one decimal; n× = their count); ties: more ratings first, then
  name. The **top 10**, then „Alle anzeigen (N)" expands the list in place. A
  tap opens the tea (none for a deleted tea).
- **Neu entdeckt:** teas whose first finished session ever falls in the
  range, newest first, five rows then „Alle anzeigen (N)"; each shows its Ø
  in the range or „ohne Bewertung"; hidden under *Gesamt* (every tea would be
  new). A tap opens the tea.
- **Bewertung im Verlauf** (kept): a line of the average rating per period
  (the periods of INS-07); a period without a rating has no point (the line
  breaks there).
- **Bestes Rezept** (kept): for each tea with **≥ 2 recipes that each have
  ≥ 2 rated sessions** in the range, its recipes by average rating, best
  first („Gyokuro Hisui · Kalt 4,6 ★ · Klassisch 4,0 ★"); up to 5 teas,
  ordered by the gap between best and worst. A session counts for the recipe
  it was brewed with (`profile_id`, named by its `profile_name` snapshot);
  sessions without a recipe don't count here. The section is hidden when no
  tea qualifies.
- Without any rating in the range the tab says „In diesem Zeitraum nichts
  bewertet" above *Neu entdeckt*.
- **v0.7.1 (planned, D-116):** where *Tee* and *Zubereitung* ratings show on
  Statistik is grilled in the v0.7.1 specify session.

**INS-04 — Taste (Bewertungen)** — **removed in rc.2 (D-115 A1, R5):**
*Geschmack* (the radar) and *Beschreibungen* (the top descriptors) are no
longer on Statistik. Taste stays on each tea's detail (SCR-09, JRN-03,
JRN-04).

**INS-05 — Consumption & cost (Verbrauch)** `Must`
- **Grams:** the big numeral of leaf used in the range („38 g") and bars of
  grams per period (Σ `leaf_grams` of the finished sessions; a session
  without a leaf amount adds nothing).
- **Cups:** per period as a second bar chart below the grams (by volume,
  rc.2). *Verbrauch* keeps separate charts (R10).
- **Money spent:** what was paid for tea: Σ `price_cents` of the INITIAL
  and PURCHASE events with a price whose `occurred_at` is in the range
  („64,80 € im Zeitraum"), and bars per period. Events of deleted teas are
  gone with their tea (cascade) and don't count. Without a priced purchase
  in the range: „Keine Käufe mit Preis". AUSGABEN stays priced purchases
  (D-115 Q10); „Ø € pro Tasse" moved from here to GETRUNKEN (rc.2).
- **Money drunk (GETRUNKEN, rc.2, D-115 A1, R9):** a section below AUSGABEN:
  the € value of the leaf brewed per period (each session's `cost_cents`) as
  bars, the range's total, and **„Ø 0,27 € pro Tasse"**: Σ `cost_cents` ÷ Σ
  cups (by volume) over the sessions in the range that have a `cost_cents`;
  hidden without such a session (never „0 €"). Sessions without a
  `cost_cents` don't count: „aus 20 Sitzungen mit Preis"; without any:
  „Keine Sitzungen mit Preis".
- **Running out:** the non-archived teas whose stock is *Leer* or *Knapp*
  (INV-05) and whose stock warning is on (`low_stock_warning`), *Leer*
  first, then by remaining grams; „12 g · knapp" / „leer"; a tap opens the
  tea. Not limited by the range. Hidden when none.

**INS-07 — Time range** `Must`
- One selector above the tabs for every tab: **30 T · 90 T · 12 M ·
  Gesamt**, rolling up to today („30 T" = today and the 29 days before).
  The first start shows 90 T on *Überblick*; afterwards the **last range
  and tab are remembered** across app starts (DataStore `statsRange`,
  `statsTab`, 05 §6).
- **Custom range „Monate" (rc.2, D-115 A1, R7):** a fifth segment opens a
  sheet with two month pickers (*Von*, *Bis*); the segment then reads
  „Dez.–Aug." (with years when they differ: „Dez. 25–Aug. 26"). Months run
  from the first session's month to the current month; *Bis* is not before
  *Von*; a range ending with the current month counts up to today. It is
  remembered like the others (`statsRange` plus its from/to months, 05 §6).
  The empty state and the „+n im Zeitraum" rule follow the other ranges (the
  „+n" shows).
- **Periods of the bar and line charts:** 30 T and 90 T **per week** (ISO
  weeks, Monday first, „KW 44"; the weeks touching the range, ≈ 5 and 13
  bars; the first and last week only count the range's days); 12 M **per
  month** (12 bars, the current month last); Gesamt **per month** from the
  first finished session's month, horizontally scrollable, opening on the
  current month; **Monate** (rc.2) **per month, per week when the span is at
  most 3 months**.
- Streaks (INS-01) and running out (INS-05) ignore the range.

**INS-08 — Interaction, motion and accessibility** `Must`
- A tap on a bar, a line point or a donut slice shows its value in a small
  label („KW 44 · 6 Sitzungen"; „Sencha · 42 % · 21 Sitzungen"); a second
  tap or a tap elsewhere hides it. No drill-down into the journal. **rc.2
  (R15):** the label fades (0,12 s in, 0,1 s out; reduce motion: at once).
- **Foldable sections (rc.2, D-115 A1, R8):** on all three tabs sections are
  open at first; a tap on the heading folds the section to one line with its
  key number („SITZUNGEN · 42"); folded sections stay folded across app
  starts (`statsFolded`, 05 §6). The chevron turns; the body expands or
  collapses in ≈ 200 ms (reduce motion: at once); TalkBack announces
  „eingeklappt"/„ausgeklappt".
- **Compact (rc.2, R10):** every bar chart is about half as tall as in rc.1;
  section spacing is tighter.
- **Motion:** bars and the donut grow once when a tab first shows
  (≈ 300 ms, ease-out); a range change morphs the bars to the new values;
  with reduce motion only a fade (03 §3.6).
- **Accessibility:** every chart is one TalkBack node that reads its data
  as a list („Sitzungen pro Woche. KW 44: 6. KW 45: 4. …"); there is no
  visible table toggle. Numerals and labels follow the font scale up to 2.0.
- **Empty state:** with no finished session at all, the screen shows A9
  (three stones and a leaf) with „Noch keine Statistik · Brühe deinen ersten
  Tee, dann wächst hier deine Tee-Reise." and no tabs. With sessions but
  nothing in the range, each section says quietly „In diesem Zeitraum
  nichts".

Performance: NFR-PERF-06 (500 ms for 10 000 sessions).

---

## 13. Settings (SET) — SCR-25

| ID | Setting (German label) | Values / default | Priority |
|---|---|---|---|
| SET-05 | Design (*System / Hell / Dunkel*; from v0.6.1 in the section *Darstellung*, D-114) | System | Must |
| SET-06 | Hintergrundfarben verwenden (dynamic colour) | aus | Could |
| SET-09 | Alarmton | Bundled tones (Klangschale, Glocke, Holzblock, Glockenspiel, Sanft; synthesised, D-036) — Klangschale | Must |
| SET-10 | Vorwarnung (pre-alert; the base B of TMR-23's automatic rule) | Aus, 5, 10, 15, 20, 25, 30 s — **10 s** | Must |
| SET-26 | Vorwarnton (pre-alert sound, with *Anhören*; v0.2.1, D-093) | Four new synthesised sounds, mastered much louder with a fall-off of ≈ 3 s, at the notification volume until v0.2.2, on the alarm volume from v0.3.0 (D-098) (Zimbel, Glöckchen, Klangstab, Tiefe Schale; 04 §9.2) and today's tick „Ting (leise)" — default chosen by the PO via *Alarm testen* on the first v0.2.1 rc (until then Zimbel) | Must |
| SET-27 | Lautstärke (Vorwarnung and alarm, on top of the phone's alarm volume; v0.3.0, D-098) | 10–100 % in 10 % steps — **100 %**; *Alarm testen* plays at it | Must |
| SET-11 | Vibration | an | Must |
| SET-14 | Alarmdauer (alert length) | Kurz (1×) / Normal (bis 15 s) / Lang (bis Bestätigung, max. 2 Min) — Normal | Should |
| SET-32 | Bei „Nicht stören" klingeln (below *Alarmdauer*; v0.3.0, D-099) | **an**. On: while Do Not Disturb lets alarms through („Nur Alarme", or priority mode whose policy allows alarms), Vorwarnung and alarm ring even when the phone reports silent, as with the ringer on normal (sound, plus vibration per SET-11). Off: the reported ringer mode decides (as in `v0.3.0-rc.1`). *Alarm testen* and *Anhören* follow it (04 §9.1) | Must |
| SET-15 | Bestand abziehen (auto-deduct, INV-02; first row of *Brühen & Vorrat*, v0.4.0, D-100; until then without a row) | an | Must |
| SET-16 | Schwelle „knapp" („Knapp ab", in *Brühen & Vorrat*; row new in v0.2.2, D-097) | 0–500 g in 5 g steps — **15 g** (was 10 g; a stored 10 g becomes 15 g once); a tea's own *Knapp ab* (SCR-09, 1–500 g, v0.5.0, D-108) wins over it | Should |
| SET-34 | Tassengröße (*Einstellungen*, v0.7.0-rc.2, D-115 A1, R2–R3; in *Brühen & Vorrat*, Claude's placement, not asked): the size of one cup. Poured water is counted in cups by it: Statistik (Tassen, Ø € pro Tasse) and the tea detail's „pro Tasse" lines (INV-08). Changing it recomputes every figure; nothing is stored per session | 50–500 ml in 10 ml steps — **150 ml** (`cupSizeMl`, 05 §6) | Should |
| SET-28 | Vorrats-Erinnerung (INV-11, in *Brühen & Vorrat*; a switch „Tägliche Vorrats-Erinnerung" in v0.3.0, D-098; from v0.5.0 *Täglich · Alle 2 Tage · Alle 3 Tage · Wöchentlich · Aus*, D-108) | Täglich | Should |
| SET-29 | Uhrzeit (the reminder's time; INV-11, time picker, shown while SET-28 is not *Aus*; v0.3.0, D-098) | **18:00** | Should |
| SET-17 | Bildschirm beim Brühen anlassen | an | Must |
| SET-25 | Beim Ziehen abdunkeln (TMR-17) | an | Should |
| SET-18 | ~~Geänderte Zeiten anbieten (PRF-07)~~ — **retired** (v0.6.0, D-112): the offer has no setting | — | Won't |
| SET-19 | Wöchentliche Vorrats-Erinnerung (INV-07; freshness only from v0.3.0, D-098) | aus | Could |
| SET-20 | Backup-Erinnerung (manual export) | Aus / Monatlich — Monatlich | Should |
| SET-22 | Katalog: Fotos laden | an | Should |
| SET-23 | Katalog: letzter Abruf, *Jetzt aktualisieren* | — | Must |
| SET-24 | Timer-Zuverlässigkeit (status of notification, Live Update, full-screen alert, battery, exact alarm, and from v0.3.0 the alarm volume, with *Beheben* links) | — | Must |

**SET-30 — Sections** `Must`: *Timer & Alarm*, *Brühen & Vorrat*, *Katalog*,
*Teegeschirr* (→ SCR-13; v0.4.0, D-100), *Darstellung*, *Daten* (→ SCR-26,
Alle Daten löschen), *Über* (→ SCR-27). From v0.6.1 ([D-114](10-open-questions-and-decisions.md#d-114))
*Darstellung* is the first section, below the *Timer-Zuverlässigkeit* card,
and holds *Design* (SET-05).

**SET-31 — Test alert** `Must`: *Alarm testen* plays pre-alert tick + alert
(the chosen *Vorwarnton*, SET-26, from v0.2.1; from v0.3.0 on the alarm
volume at the *Lautstärke* of SET-27, D-098).

**SET-33 — Delete all data** `Must` (type „LÖSCHEN" to confirm).

---

## 14. Backup & export (BAK) — SCR-26

**BAK-05 — Android device backup** `Must`
Database and settings participate in Android Auto Backup / Samsung & Google
device backup and device-to-device transfer. Catalog photos and own photos are
excluded (size limit; catalog photos can be re-downloaded).

**BAK-01 — Manual export** `Should`
*Sicherung erstellen* writes a simple `.umamit` ZIP file (personal data and
settings as JSON plus own photos; catalog excluded) via the system file
picker. Format: [05 §8](05-data-model.md#8-backup-file-format).

**BAK-02 — Restore** `Won't` (1.x; post-1.0 backlog as `Could`, D-036)
In-app restore of a `.umamit` file with validation, preview, safety backup
and atomic replace. Until then, data comes back via Android device backup
(BAK-05); the export file is readable JSON.

**BAK-04 — CSV export** `Could`

**BAK-06 — Backup reminder** `Should`
Monthly low-importance reminder to create a manual export if there are ≥ 20
new sessions since the last export.

---

## 15. Notifications & Live Update (NOT)

**NOT-01 — Channels** `Must`: Brühtimer (live/ongoing, silent), Aufguss
fertig (high), Sitzungen (low), Erinnerungen (low), Katalog (low, refresh
progress/result); from v0.3.0 (D-098) Weiterer Timer (low, silent: the
active timer not shown as the Live Update) and Vorrat (low, silent: INV-11).

**NOT-02 — Actions** `Must`: READY → *Start*; RUNNING → *Pause*; PAUSED →
*Weiter*; ALERTING → *Abgegossen*; FINISHED → *Sitzung beenden*. Tapping opens
SCR-05.

**NOT-03 — Lock screen** `Must`: timer notifications public.

**NOT-04 — Smartwatch** `Won't` (D-028).

**NOT-05 — No promotional notifications** `Must`.

**NOT-06 — App shortcuts** `Could`: *Schnelltimer*, last 3 teas.

**NOT-07 — Quick Settings tile** `Could`.

**NOT-08 — Home-screen widget** `Could`.

---

## 16. Dropped in v0.2 (IDs retired)

| ID | Was | Reason |
|---|---|---|
| ONB-02 (v0.1), ONB-04/05 (v0.1) | Unit selection, sample teas | German/metric only; catalog replaces samples (IDs reused for new meaning in v0.2 — no code existed) |
| TMR-04 | −10 s / +10 s buttons | PO wants a calm running screen (Pause only) |
| TMR-24/25 | Spoken countdown, tabletop layout | Not needed |
| TMR-40 | Multiple concurrent timers | One at a time until v0.2.2; partly revived in v0.3.0: one quick timer alongside one brew session (TMR-21, TMR-22, D-093, D-098); from v0.5.0 any two timers, two brew sessions included, never more than two and never two quick timers (GEN-03, D-108) |
| SET-01…04, SET-07, SET-08 | Units, teaspoons, language, leaf precision | German/metric only |
| SET-12 | Start first step immediately | Always wait for the user |
| SET-13 | Alarm stream choice | Alert always follows ringer mode; from v0.3.0 always the alarm stream, with *Lautstärke* (SET-27, D-098) |
| SET-21 | Precise reminders permission | Exact alarms always available (`USE_EXACT_ALARM`, no store review) |
| BRW-04, BRW-05, BRW-07 | Teaspoons, cooling helper, checklist | Kettle with 5 °C steps |
| LRN-01…07 | Learn tab, own guides, calculators, tip of the day | Replaced by Tee-Kontor guides (CAT-09); Stats takes the tab |
| STY-02/03 (v0.1) | Browsable style reference | Styles are internal fallbacks only |
| JRN-01 (v0.1) | Session summary screen | Auto-logging (TMR-12) |
| JRN-06, JRN-15, JRN-16 | Water note, share, calendar | Not needed |
| TEA-12, TEA-15, TEA-16 | Grid view, bulk actions, label scanning | Not needed |
| BAK-03, BAK-07 | Merge restore, share backup | Not needed |
| NOT (store-related) | — | No store |
