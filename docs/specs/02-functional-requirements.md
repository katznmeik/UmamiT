# 02 · Functional Requirements

> Status: **Draft v0.2** · Last updated: 2026-10-30 (v0.3.0: quick timer
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

**GEN-03 — Single active timer** `Must`
At most one brew session or quick timer is active at any time.

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
D-055).

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
catalog (D-073). With SET-22 *Katalog: Fotos laden* off, a refresh fetches
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
  by the filters.
- **Foldable groups (v0.3.0, D-098).** In the grouped default and
  Kräutertee views a tap on a *Sorte* header folds or unfolds its teas; the
  header shows a chevron and the count („Bancha · 12").
  - AC1: Folded groups are remembered across app starts, separately for the
    green and the Kräutertee view (setting `catalogFolded`).
  - AC2: A non-empty search shows every hit unfolded; clearing it restores
    the folding.
  - AC3: The catalog's ⋮ offers *Alle einklappen* and *Alle ausklappen*.
  - AC4: *Gemerkt* and the flat sort orders (name, price) have no folding.

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

**CAT-14 — Bundled snapshot** `Won't`
No catalog snapshot is bundled in the APK: the APK is publicly downloadable
from GitHub Releases, which would redistribute shop content (D-016, D-036).

**CAT-16 — Sold-out teas stay in the catalog** `Must` (v0.2.1, D-094, #97)
The shop leaves sold-out products out of its sitemap (08 §2.2). During a
refresh, every catalog tea whose article number is missing from the sitemap
gets its own product page fetched once, within the refresh's limits and stop
rules (CAT-07 AC2). This is part of network use (a) of GEN-01, not a new use.
- AC1: Answer 200, the canonical URL is that product page, and the breadcrumb
  is still green or herbal tea (CAT-01) → the tea is kept with the page's
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
- AC4: A green or herbal tea (CAT-01) is stored in the catalog as a regular
  entry (photo per SET-22) and its detail (SCR-21) opens with *In Sammlung
  aufnehmen* / *Nachkauf erfassen* as usual; the exact price is fetched on
  adding (GEN-01 (c)). A sold-out tea shows „nicht vorrätig". It is not
  marked „Neu".
- AC5: Not green or herbal → „Dieser Tee gehört nicht zum Katalog (nur
  Grün- und Kräutertees)." and nothing is stored.
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

**TEA-01 — Add a tea** `Must`
From the catalog (CAT-12, primary path) or manually (*Tee ohne Katalog
anlegen*): style picker (SCR-11) → editor (SCR-10) with defaults from the
style. Only *Name* is mandatory (1–80 chars).

**TEA-02 — Tea fields** `Must`
All fields of `Tea` ([05 §3.1](05-data-model.md#31-tea)) in sections:
*Allgemein* (photo, name, secondary name, style/type, umami level, flavoured),
*Herkunft* (country, region, cultivar, harvest year & flush, shading days,
processing), *Kauf* (vendor — default „Tee-Kontor Kiel" for catalog teas —
plus purchase date, package size and price, which are stored as the tea's
INITIAL stock event, [05 §3.10](05-data-model.md#310-inventoryevent)), *Vorrat & Frische* (stock, opened
date, best before, storage note), *Notizen & Schlagwörter*.

**TEA-03 — Edit** `Must`
All fields editable. Changing the style does not overwrite recipes.

**TEA-04 — Archive / delete** `Must`
*Archivieren* hides a tea from lists and pickers (e.g. when used up); journal
entries remain. *Löschen* keeps journal sessions (name snapshot) and removes
recipes, photos and stock events; the dialog offers *Stattdessen archivieren*.
- **From the tea picker (v0.2.1, D-093):** a long press on a tea in SCR-03
  offers *Aus Sammlung entfernen*. It archives the tea and shows the
  snackbar „*Tanabata* aus der Sammlung entfernt" · *Rückgängig*. An
  archived tea disappears from Start (*Nochmal brühen*, BRW-11) and from the
  tea picker; the journal keeps its entries with name and photo. Not
  possible for the tea of the active brew session: the item is greyed out
  with „Erst die Sitzung beenden". Adding it again from the catalog (CAT-12)
  un-archives the same tea and records the package as a PURCHASE event
  (D-085); its earlier stock events stay (correct with INV-03). An archived
  tea's catalog row has no „im Besitz" badge and its detail offers *In
  Sammlung aufnehmen*.

**TEA-05 — Photos** `Should`
Up to 10 photos per tea (catalog photo pre-attached as cover for catalog
teas); own photos via photo picker or camera; *Als Titelbild* sets the cover.
Processing rules: [06 §5.2](06-architecture.md#52-photos).

**TEA-06 — Favourites** `Must`

**TEA-07 — Tags** `Could`
Free-form tags (1–30 chars, ≤ 20 per tea).

**TEA-08 — List** `Must`
SCR-08 („Meine Tees") rows: cover photo, name, secondary name, type, average
rating, stock chip („38 g übrig"), freshness chip, favourite, „Rezept prüfen"
badge.

**TEA-09 — Search** `Must` over name, secondary name, vendor, region,
cultivar, notes, tags.

**TEA-10 — Filters** `Should`: favourites, type, stock (vorrätig / knapp /
leer), flavoured, archived.

**TEA-11 — Sorting** `Must`: name, zuletzt gebrüht (recently brewed), zuletzt
hinzugefügt, Bewertung, Vorrat (low → high), geöffnet seit (oldest first).

**TEA-13 — Tea detail** `Must`
SCR-09: hero photo, names, rating „★ 4,5 (12)", type chip, umami meter,
`StatTriplet` of the default recipe (Temperatur · Ziehzeit · Aufgüsse), primary
*Brühen*, recipes, stock & cost, freshness, tasting summary, recent sessions,
notes, details, and for catalog teas the visible reorder link
„Beim Tee-Kontor nachbestellen" (INV-09).

**TEA-14 — Duplicate** `Could`

**TEA-17 — Recipe check** `Must`
Teas flagged „Rezept prüfen" show the reason and *Rezept bestätigen*
(confirm) / *Rezept bearbeiten*; editing or confirming clears the flag.

---

## 5. Built-in tea styles (STY)

**STY-01 — Built-in styles** `Must`
The app ships the style list in [08 §3](08-content.md#3-built-in-tea-styles)
(Japanese and other green teas plus generic other categories). Styles are
**not** browsable as a catalog; they provide: mapping targets for catalog teas
(CAT-06), umami level, freshness window, and fallback recipes; and templates
for manually added teas.

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

**PRF-04 — Steps editor** `Must`
Add, delete, reorder steps; per step: kind, duration 5 s – 24 h (5 s steps
below 10 min, 1 min steps above), optional temperature (5 °C steps), note.
At least one non-rinse step. From v0.3.0 (D-098, #141) a step's duration is
set fast where it is edited (the Schritte-Sheet, SCR-31, also from the setup,
SCR-04): a tap on the time opens „Dauer wählen" (SCR-07's picker, the same
bounds and increments), and a held − / + repeats and speeds up, one increment
per repeat.

**PRF-05 — Generate series** `Should`
First duration + increment × count, optional +5 °C per step.

**PRF-06 — Reset** `Should`
*Aus Katalog zurücksetzen* (catalog teas) or *Auf Standard zurücksetzen*
(style default).

**PRF-07 — Learn from sessions** `Must`
If a session's actual step times differ from the plan by more than
max(5 s, 10 %) on any step, or steps were edited/added during the session, the
post-session prompt (TMR-12) offers *Zeiten ins Rezept übernehmen*. Accepting
writes the actual durations (rounded to 5 s, or 1 min for steps ≥ 10 min) and
added steps into the recipe used.

---

## 7. Teaware (WARE) — SCR-13, SCR-14

**WARE-01 — Manage vessels** `Must`
Name, type, volume (10–3000 ml), material, notes, photo, favourite.

**WARE-02 — Vessel picker** `Must`
In brew setup and recipe editor: favourites first.

**WARE-03 — Scaling** `Must`
Choosing a vessel sets the water volume to the vessel's brewing volume and
scales leaf by the recipe's ratio (BRW-03).

**WARE-04 — Usable volume** `Should`
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

**WARE-05 — Default vessel per method** `Could`
E.g. KYUSU → „Kyusu 180 ml".

---

## 8. Brew setup (BRW) — SCR-03, SCR-04

**BRW-01 — Start a brew** `Must`
Entry points: Home *Aufguss starten* (tea picker → setup), tea detail
*Brühen* (→ setup), journal *Nochmal brühen* (→ setup prefilled), and Home
*Nochmal brühen* (→ **directly to the timer**, BRW-11).

**BRW-11 — One-tap brew again** `Must`
Home *Nochmal brühen* shows the **3 most recently brewed distinct teas**
(non-archived). Tapping one starts a session immediately and opens the timer
in READY with the parameters of that tea's last session (recipe, vessel, leaf,
water, temperatures, step durations as planned). The kettle hint shows the
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

**BRW-03 — Ratio scaling** `Must`
With ratio lock (default on), changing water or vessel rescales leaf (0,1 g);
changing leaf rescales water (5 ml). Times and temperatures never scale.

**BRW-06 — Brew from a past session** `Should`
*Nochmal brühen* on a journal entry pre-fills setup with that session's
parameters.

**BRW-08 — Stock warning** `Should`
„Nur noch 3 g übrig" if stock is below the leaf amount.
- **v0.2.2 (D-097):** the setup's stock line marks a *Knapp* tea (INV-05):
  „14 g übrig · knapp"; the warning above is still M3.

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
not recorded as skipped (their rows are deleted at finish, 05 §3.7) and do
not count as a time difference for PRF-07.

*Sitzung beenden* (FINISHED state or overflow) completes the session; it is
saved to the journal automatically **without a summary screen**. A bottom
sheet (SCR-06) appears for 10 s and stays reachable from the journal:
„Gespeichert im Journal" with *Notizen hinzufügen* (opens SCR-17) and — only if
PRF-07 applies — *Zeiten ins Rezept übernehmen*. Ignoring it is fine.
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

**TMR-22 — Quick timer** `Should`
SCR-07: countdown without a tea: chips 0:30, 1:00, 1:30, 2:00, 3:00, 5:00,
8:00 (from v0.2.2, D-097) and custom; same alert, pre-alert (global setting,
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
    while the brew alarm is on waits; a ringing quick alarm gives way at
    once to a brew alarm. The waiting one rings right after *Abgegossen*
    with its overtime („+12:00"), however long it waited, and ends silently
    only 10 min after it started ringing. Two sounds never play at once
    (04 §9.3).

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

**JRN-01 — Automatic logging** `Must`
Every finished session is stored with tea, date/time, recipe used, vessel,
leaf, water, and per step the planned and actual time and temperature.

**JRN-02 — Rating** `Should`
Optional 0,5–5,0 stars (half steps), added later via *Notizen hinzufügen*.

**JRN-03 — Taste profile** `Should`
Optional 0–5 sliders: Umami, Süße, Bitterkeit, Adstringenz, Körper, Aroma;
radar chart on session and tea detail (average).

**JRN-04 — Descriptors** `Should`
Multi-select chips from the German vocabulary ([08 §4](08-content.md#4-tasting-descriptor-vocabulary)),
plus custom ones; max 20 per session.

**JRN-05 — Notes** `Must` (≤ 5000 chars; per-step notes ≤ 300).

**JRN-07 — Session photos** `Should` (≤ 6, via *Notizen hinzufügen*).

**JRN-08 — Journal list** `Must`
Newest first, grouped by day („Heute", „Gestern", „Montag, 12. Oktober").
Row (as in mockup v2): thumbnail, tea name, time of day („09:12"), meta line
„55 °C · 1:00 · 3 Aufgüsse" (base temperature · first infusion · count),
rating if present, a subtle dot for sessions without notes. In-progress session pinned on top.

**JRN-09 — Search & filters** `Should` (tea, type, rating, date range, with/without notes).

**JRN-10 — Session detail** `Must` with *Bearbeiten*, *Löschen*, *Nochmal brühen*.

**JRN-11 — Edit session** `Must` (all fields incl. times; leaf edits update the stock deduction).

**JRN-12 — Manual entry** `Could` (session without the timer).

**JRN-14 — Brew coach tips** `Could` ([08 §6](08-content.md#6-brew-coach-rules)).

---

## 11. Stock, cost & freshness (INV)

**INV-01 — Stock from purchase** `Must`
Adding a catalog tea with a package option (CAT-12) or entering a package size
manually creates an initial stock event with the package weight and price.

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

**INV-04 — Session edits** `Must` update the linked deduction.

**INV-05 — Stock status** `Must`
Leer (≤ 0 g), Knapp (≤ threshold, default **15 g** from v0.2.2, D-097; was
10 g), Vorrätig. Empty teas stay
in *Meine Tees* with a „Leer" chip and are sorted to the bottom in every sort
order; they are never archived automatically.

**INV-06 — Opened date & freshness** `Should`
*Geöffnet am* is set automatically (snackbar *Rückgängig*) on the first
session after a new package: i.e. when `opened_date` is empty, or when the
latest INITIAL/PURCHASE event is newer than `opened_date` and the stock had
reached 0 g before that purchase; freshness from the style's window: „Frisch",
„Bald genießen", „Über dem Höhepunkt".

**INV-07 — Low stock & freshness reminders** `Could`
Weekly digest notification (off by default).
- **v0.3.0 (D-098):** low stock has its own daily reminder (INV-11); this
  digest keeps only freshness.

**INV-08 — Cost** `Must`
Cost per gram from purchase events (weighted by grams of each purchase); cost
per session = leaf × cost per gram. **One poured infusion = one cup**: cost
per cup = session cost ÷ completed infusions (rinse and whisk-only matcha:
the whole session is one cup). Shown on tea detail („0,24 € pro Tasse") and in
statistics.

**INV-09 — Reorder link** `Must`
SCR-09 shows „Beim Tee-Kontor nachbestellen" in VORRAT & KOSTEN for every tea
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

**INV-11 — Daily low-stock reminder** `Should` (v0.3.0, D-098)
Once a day at the time of SET-29 (default 18:00) one quiet notification
lists every tea that is *Knapp* or *Leer* (INV-05): „3 Tees werden knapp",
with lines „Tanabata · 14 g", „Hisui · leer". A tap opens Start.
- AC1: Teas hidden with *Ausblenden* (INV-10) are included until their next
  INITIAL or PURCHASE event; archived teas and teas without stock events
  never.
- AC2: No notification on a day without such a tea; never a sound,
  vibration or full-screen intent (channel „Vorrat", low importance).
- AC3: Switched off with SET-28 (default on); no per-tea switch (removing
  the tea stops it).
- AC4: Inexact by design (within about 15 min); it never wakes the phone
  with an exact alarm and never disturbs a running timer or its alert
  (rule 8).

---

## 12. Statistics (INS) — SCR-18

Top-level tab „Statistik" with tabs **Überblick · Bewertungen · Verbrauch**
(as in the mockup's "Your Tea Journey").

**INS-01 — Activity** `Must`
Sessions this week / month, total teas in collection („+3 diesen Monat"),
sessions per week (last 12 weeks), current and longest streak (shown neutrally),
most-brewed teas (top 5).

**INS-02 — Ratings** `Must`
Average rating and distribution in five buckets 1–5 ★ (half stars rounded
down, 0,5 counts as 1 ★; rated sessions only, with count of rated vs.
total), top-rated teas (≥ 3 ratings).

**INS-03 — Tea types** `Must`
Share of sessions by type (Gyokuro, Sencha, Matcha, …) as donut with legend
and percentages.

**INS-04 — Taste** `Should`
Average taste radar of sessions rated ≥ 4 ★; most frequent descriptors.

**INS-05 — Consumption & cost** `Must`
Grams used per month (bar chart, 12 months), cups per month (one cup per
infusion, INV-08), money spent on tea per month (purchases), average cost per
cup, teas running out (Knapp/Leer).

**INS-07 — Time range** `Must`: 30 Tage, 90 Tage, 12 Monate, Gesamt (default 90 Tage).

Every chart has an accessible table alternative.

---

## 13. Settings (SET) — SCR-25

| ID | Setting (German label) | Values / default | Priority |
|---|---|---|---|
| SET-05 | Design (*Hell / Dunkel / System*) | System | Must |
| SET-06 | Hintergrundfarben verwenden (dynamic colour) | aus | Could |
| SET-09 | Alarmton | Bundled tones (Klangschale, Glocke, Holzblock, Glockenspiel, Sanft; synthesised, D-036) — Klangschale | Must |
| SET-10 | Vorwarnung (pre-alert; the base B of TMR-23's automatic rule) | Aus, 5, 10, 15, 20, 25, 30 s — **10 s** | Must |
| SET-26 | Vorwarnton (pre-alert sound, with *Anhören*; v0.2.1, D-093) | Four new synthesised sounds, mastered much louder with a fall-off of ≈ 3 s, at the notification volume until v0.2.2, on the alarm volume from v0.3.0 (D-098) (Zimbel, Glöckchen, Klangstab, Tiefe Schale; 04 §9.2) and today's tick „Ting (leise)" — default chosen by the PO via *Alarm testen* on the first v0.2.1 rc (until then Zimbel) | Must |
| SET-27 | Lautstärke (Vorwarnung and alarm, on top of the phone's alarm volume; v0.3.0, D-098) | 10–100 % in 10 % steps — **100 %**; *Alarm testen* plays at it | Must |
| SET-11 | Vibration | an | Must |
| SET-14 | Alarmdauer (alert length) | Kurz (1×) / Normal (bis 15 s) / Lang (bis Bestätigung, max. 2 Min) — Normal | Should |
| SET-15 | Verbrauch automatisch abziehen (auto-deduct) | an | Must |
| SET-16 | Schwelle „knapp" („Knapp ab", in *Brühen & Vorrat*; row new in v0.2.2, D-097) | 0–500 g in 5 g steps — **15 g** (was 10 g; a stored 10 g becomes 15 g once) | Should |
| SET-28 | Tägliche Vorrats-Erinnerung (INV-11, in *Brühen & Vorrat*; v0.3.0, D-098) | an | Should |
| SET-29 | Uhrzeit (the reminder's time; INV-11, time picker, shown while SET-28 is on; v0.3.0, D-098) | **18:00** | Should |
| SET-17 | Bildschirm beim Brühen anlassen | an | Must |
| SET-25 | Beim Ziehen abdunkeln (TMR-17) | an | Should |
| SET-18 | Geänderte Zeiten anbieten (PRF-07) | an | Should |
| SET-19 | Wöchentliche Vorrats-Erinnerung (INV-07; freshness only from v0.3.0, D-098) | aus | Could |
| SET-20 | Backup-Erinnerung (manual export) | Aus / Monatlich — Monatlich | Should |
| SET-22 | Katalog: Fotos laden | an | Should |
| SET-23 | Katalog: letzter Abruf, *Jetzt aktualisieren* | — | Must |
| SET-24 | Timer-Zuverlässigkeit (status of notification, Live Update, full-screen alert, battery, exact alarm, and from v0.3.0 the alarm volume, with *Beheben* links) | — | Must |

**SET-30 — Sections** `Must`: *Timer & Alarm*, *Brühen & Vorrat*, *Katalog*,
*Teegeschirr* (→ SCR-13), *Darstellung*, *Daten* (→ SCR-26, Alle Daten
löschen), *Über* (→ SCR-27).

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
| TMR-40 | Multiple concurrent timers | One at a time until v0.2.2; partly revived in v0.3.0: one quick timer alongside one brew session, never more (TMR-21, TMR-22, D-093, D-098) |
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
