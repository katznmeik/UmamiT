# 10 · Open Questions & Decision Log

> Status: **Living document** · Last updated: 2026-10-25

## Open questions

| ID | Question | Proposed default | Needed by |
|---|---|---|---|
| — | None open. | — | — |

### Notes on Q-04 — illustration and imagery options

| Option | Quality | Effort / cost | Notes |
|---|---|---|---|
| **A. Commission an illustrator** (sumi-e / ink-wash artist) | Highest, unique, consistent | Highest (typically several hundred €; weeks) | Best if the look should be truly one-of-a-kind |
| **B. Licensed stock art** (e.g. Adobe Stock, Shutterstock, Creative Market ink-wash packs) | High, professional | Low–medium (per image or pack) | Check the licence covers use in an app; style consistency across images can vary |
| **C. AI-generated, curated and post-processed** (ChatGPT / similar, same session as the mockups) | High if curated: generate many, pick the best, colour-match to the palette, clean up, export light and dark variants | Low | Matches the mockup look exactly; fine for a personal app; check the tool's terms |
| **D. Public-domain museum art** (CC0 open-access collections, e.g. Met Museum, Rijksmuseum, Smithsonian: historical Japanese ink landscapes) | Very high, authentic | Low (curation and cropping) | Real historical artworks; need toning to the washi palette; fewer tea-specific motifs |
| **E. Generated in code** (Compose canvas "ink" mountains) | Medium | Medium (development time) | Tiny size and infinitely scalable, but less rich than painted art |

Recommendation: **C** for illustrations (fast, on-brand, matches the
mockup), optionally mixed with **D** for one or two hero landscapes;
logo redrawn as a clean vector from the mockup. Tea photos come from
Tee-Kontor (catalog) and the PO's own photos.

### Notes on Q-11 — public vs private repository

A public repository is fine **as long as no Tee-Kontor content is committed**
(D-016): parser tests use synthetic HTML fixtures that mimic the page
structure without the shop's texts or images. Other aspects:
- Signing keys are GitHub secrets and never visible, public or not.
- Public GitHub Releases mean anyone could download the APK. It contains no
  personal data and no shop content, so this is harmless.
- The specs and the brand mockup would be visible to anyone.
- Private repositories have a limited free GitHub Actions quota; public ones
  have unlimited minutes for standard runners.

### Resolved questions

| ID | Resolution (2026-10-21) |
|---|---|
| Q-04 Illustrations | Option C (AI-generated, curated), optionally with D (CC0 museum art) — D-029; asset list in `docs/design/assets.md`. |
| Q-11 Repository visibility | Public — D-030. |
| Q-01 Application ID | `com.umamit.app` (originally `com.umamitea.app`; changed with the rename, D-033). |
| Q-05 Serif font | Cormorant Garamond (accepted). |
| Q-18 Galaxy Watch | Not used; no watch support needed (NOT-04 → Won't). |
| Q-15 Default alert sound (2026-10-22) | Klangschale variant C and the bowl-like tick variant C, chosen by the PO after the probe — D-045. The M1 checklist still listens to all tones via *Alarm testen*. |
| Q-02 Monetisation | Not applicable: personal use only. |
| Q-03 Languages | German only (D-017). |
| Q-06 Content review | Tee-Kontor is the content source; built-in styles are fallbacks only. |
| Q-07 Navigation | Start · Journal · Sammlung · Statistik (D-018). |
| Q-08 Tablet layouts | Phone-first; wider layouts best effort. |
| Q-09 Play account | Not applicable: no store (D-013). |
| Q-10 Min Android | Android 15 / API 35 (D-023). |
| Q-12 Sample teas | Dropped; catalog replaces them. |
| Q-13 Brand mockup | Added at `docs/design/brand-mockup.png`. |
| Q-14 Support/privacy policy hosting | Not applicable. |

## Decision log

Status: *Accepted* (by the PO), *Proposed* (default unless the PO objects),
*Superseded*.

<a id="d-001"></a>
### D-001 — No accounts; personal data local only · *Accepted*, amended by D-014

Originally "offline only, no accounts" (2026-10-21). Still true for all
personal data; the network exceptions to the shop host are listed in D-014
and D-036.

<a id="d-002"></a>
### D-002 — Concept: tea brewing companion · *Accepted*

Collection + brewing guidance + steep timer + journal, umami-focused.

<a id="d-003"></a>
### D-003 — Kotlin, Jetpack Compose, Material 3 · *Proposed*

Min SDK changed by D-023.

<a id="d-004"></a>
### D-004 — Timer background strategy · *Superseded by D-019 / [04 §6](04-brew-timer.md#6-background-execution)*

v0.1 avoided exact-alarm permissions because of Play policy. As a sideloaded
personal app (D-013), v0.2 uses `USE_EXACT_ALARM` + foreground service +
battery-optimisation exemption together.

<a id="d-005"></a>
### D-005 — Room + DataStore, UUIDs, metric storage · *Proposed*

<a id="d-006"></a>
### D-006 — No analytics or tracking SDKs · *Proposed* (network part superseded by D-014)

<a id="d-007"></a>
### D-007 — Navigation Compose, type-safe routes · *Proposed*

<a id="d-008"></a>
### D-008 — Guides rendered from Markdown · *Proposed* (now Tee-Info guides converted to Markdown)

<a id="d-009"></a>
### D-009 — Brand theme by default, dynamic colour opt-in · *Proposed*

<a id="d-010"></a>
### D-010 — Stats inside Journal · *Superseded by D-018*

<a id="d-011"></a>
### D-011 — Photo handling · *Proposed*

Photo picker/camera intent, resized, EXIF stripped, app-private storage;
excluded from device backup; own photos included in manual export.

<a id="d-012"></a>
### D-012 — Timer screen always dark · *Proposed*

<a id="d-013"></a>
### D-013 — Personal app, sideloaded, no store · *Accepted* (PO, 2026-10-21)

- No Play Store or other store; one user, one device (Samsung Galaxy,
  Android 16 / One UI 8).
- Consequences: no store policies, listings or beta. The app may use
  `USE_EXACT_ALARM`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` and a
  `specialUse` foreground service without review.

<a id="d-014"></a>
### D-014 — In-app catalog refresh over the internet · *Accepted* (PO, 2026-10-21)

- The app declares `INTERNET` and fetches tee-kontor-kiel.de when the user
  taps *Katalog aktualisieren* (the complete list of network uses is in
  D-036).
- Alternatives rejected: bundling the catalog only in each APK (needs a
  developer for every refresh), one-time snapshot.
- Consequences: parser maintenance when the shop changes; host allow-list;
  atomic refresh with sanity checks.

<a id="d-015"></a>
### D-015 — Tee-Kontor catalog scope and independence · *Accepted* (PO, 2026-10-21)

- All green teas incl. flavoured teas, matcha and powders; one entry per tea;
  product photos and *Tee-Info* guides imported.
- Catalog lives in *Sammlung › Katalog*; adding a tea creates an independent
  copy; refresh updates only the catalog; manual *Aus Katalog zurücksetzen*.
- Adding asks for the package size; stock and price are prefilled.

<a id="d-016"></a>
### D-016 — Handling of scraped content · *Accepted* (PO, 2026-10-21)

Scraped texts and photos are stored only on the phone for personal use and are
**never committed** to the repository. Parser tests use **synthetic HTML
fixtures** that reproduce the page structure (element classes, table layout)
with invented text. A manual "live parser check" task runs the parser against
the real site from a cloud session when needed. No bundled catalog snapshot
in the APK (CAT-14 is `Won't`, D-036): the APK is public.

<a id="d-017"></a>
### D-017 — German only, metric · *Accepted* (PO, 2026-10-21)

German UI and content; °C, g, ml; German formats.

<a id="d-018"></a>
### D-018 — Navigation: Start · Journal · Sammlung · Statistik · *Accepted* (PO, 2026-10-21)

As in the brand mockup. Learn tab and own guides/tools dropped; Tee-Kontor
guides live in the catalog.

<a id="d-019"></a>
### D-019 — Timer behaviour · *Accepted* (PO, 2026-10-21)

- Supports all styles (kyusu, mug, gongfu, cold brew).
- READY always waits for the user's tap between infusions.
- Running controls: Pause only (others in the overflow menu); no ±10 s.
- Pre-alert tick before the end: global setting (Aus, 5–30 s in 5 s steps;
  default 10 s since D-035), overridable per recipe.
- Alert follows the ringer mode (sound / vibration / silent), repeats ~15 s.
- Live Update on lock screen, status bar and Now Bar (Now Bar best effort,
  D-036).
- One timer at a time.
- Samsung reliability: exact alarms, FGS, battery exemption (see D-013).

<a id="d-020"></a>
### D-020 — Automatic journaling · *Accepted* (PO, 2026-10-21)

Sessions are logged automatically without a summary screen; a short sheet
offers *Notizen hinzufügen* and, if times changed, *Zeiten ins Rezept
übernehmen* (ask, never automatic).

<a id="d-021"></a>
### D-021 — Recipe derivation from Tee-Kontor data · *Accepted* (PO, 2026-10-21)

Ranges → midpoint; temperature snapped to 5 °C, time to 5 s; brewing table
wins over prose, prose over style defaults; non-table recipes flagged
„Rezept prüfen". Details: [08 §2.5](08-content.md#25-recipe-derivation).

<a id="d-022"></a>
### D-022 — Backup via Android device backup · *Accepted* (PO, 2026-10-21)

Database and settings in device backup; manual export file as extra safety;
photos only in the manual export. In-app restore is post-1.0 (D-036).

<a id="d-023"></a>
### D-023 — Min SDK 35, target SDK 36 · *Accepted* (PO, 2026-10-21)

PO's phone runs Android 16; one version of slack.

<a id="d-024"></a>
### D-024 — Delivery: Claude builds, PR per milestone, GitHub Releases · *Accepted* (PO, 2026-10-21)

- Claude develops in cloud sessions; one PR per milestone into `main`.
- Signed APK published on GitHub Releases per milestone; PO installs on the
  phone.
- Timer first (M1).
- **No development starts before the PO's explicit go.**

<a id="d-025"></a>
### D-025 — Temperatures in 5 °C steps; no cooling helper · *Accepted* (PO, 2026-10-21)

The PO's kettle is adjustable in 5 °C steps; the timer shows a kettle hint per
step.

<a id="d-029"></a>
### D-029 — Imagery: AI-generated and curated, optional CC0 museum art · *Accepted* (PO, 2026-10-21)

Illustrations are generated (ChatGPT) in one consistent style, curated and
post-processed (palette match, light/dark variants, WebP). Hero landscapes may
use public-domain (CC0) museum ink paintings instead. The logo is redrawn as a
vector in M1. Asset list and prompts: [`docs/design/assets.md`](../design/assets.md).

<a id="d-030"></a>
### D-030 — Public repository · *Accepted* (PO, 2026-10-21)

The repository is public. Consequences: no Tee-Kontor content is ever
committed (D-016, synthetic test fixtures); secrets only in GitHub Actions
secrets; the APK on GitHub Releases is publicly downloadable (contains no
personal data or shop content).

<a id="d-031"></a>
### D-031 — Grill round 2 decisions · *Accepted* (PO, 2026-10-21)

- Catalog refresh only on user request, on any connection (CAT-07 AC7).
- Discontinued catalog teas are hidden; kept only if owned (CAT-07 AC4).
- Empty teas stay in the collection, marked „Leer", sorted last (INV-05).
- Finishing early is a normal session (TMR-12).
- One poured infusion = one cup for cost and statistics (INV-08, INS-05).
- Manual tea entry stays comfortable (PO sometimes buys elsewhere).
- *Nochmal brühen*: 3 most recently brewed teas; tap → timer READY with the
  last parameters (BRW-11).
- Rinse steps never added automatically; matcha keeps the whisk timer.
- Screen dims while steeping (TMR-17, SET-25); streaks shown neutrally; theme
  follows the system.
- M1 ships the PO's two teas (Bio Sencha Tanabata, Bio Sencha Hisui) as seed
  data with 100 g stock each ([08 §2.5a](08-content.md#25a-m1-seed-teas-pos-current-teas)).

<a id="d-032"></a>
### D-032 — Grill round 3: seed teaware, waiting sessions, review process · *Accepted* (PO, 2026-10-21)

- Seed vessels: Kyusu 300 ml (brews 250 ml, home) and Becher mit Sieb 400 ml
  (work); seed recipes scaled to them (WARE-06, 08 §2.5a).
- *Nochmal brühen* keeps the last session's parameters even across vessels;
  switching via ⋮ *Brühen anpassen*.
- Westlich recipes with one infusion get a second (0,25 × the first duration,
  rounded to 5 s, +5 °C; set by the PO from experience).
- Leaf in grams with 0,1 g; Google device backup is on.
- Waiting sessions live until midnight; Live Update demoted after 30 min
  (TMR-14).
- Milestone done = on-device checklist + design review of rendered
  screenshots in the PR (09 §1).

<a id="d-033"></a>
### D-033 — App renamed to „UmamiT" · *Accepted* (PO, 2026-10-21)

- The app is called **UmamiT** everywhere (app name, wordmark, docs).
- Application ID `com.umamit.app`; backup extension `.umamit`; database
  `umamit.db`; user agent `UmamiT/<version>`.
- The GitHub repository was renamed to `katznmeik/UmamiT` by the PO on
  2026-10-21 (GitHub redirects the old `UmamiTea` URLs).
- The existing mockup images still show „UmamiTea"; the wordmark in the app
  and in new mockups is „UmamiT".

<a id="d-034"></a>
### D-034 — Grill round 4 · *Accepted* (PO, 2026-10-21)

- Silent ringer mode stays visual-only (no vibration override); the PO keeps
  the phone on vibrate or ring when brewing.
- Claude generates the release keystore and hands file and passwords to the
  PO, who stores them offline and adds the GitHub secrets.
- ~~Alert sounds come from free CC0 recordings, credited in About.~~
  *Amended by D-036:* the tones are synthesised first; the PO may replace
  any of them later with CC0 recordings (credited in About).
- New APKs are announced by Claude in the session/PR; no in-app update check.
- Seed teas are linked to the catalog and filled with description and photo on
  the first refresh (CAT-15).
- Starting a second tea keeps the confirmation dialog (TMR-21).
- Overtime ≥ 15 s is shown prominently (TMR-06).

<a id="d-035"></a>
### D-035 — Welcome text, no home greeting, 10 s pre-alert, signing process · *Accepted* (PO, 2026-10-21)

- Welcome page text: **„Aus Liebe zum Tee"**.
- The Start screen shows no greeting text (header illustration only).
- Pre-alert default **10 s** before the end of a step (SET-10). Steps shorter
  than N + 5 s get no pre-alert (15 s with the default 10 s).
- Signing: Claude generates the key, the backup file and the fingerprint
  check; the PO only saves the backup and adds two GitHub secrets
  ([06 §6.1](06-architecture.md#61-signing-process-d-035)).

<a id="d-083"></a>
### D-083 — Finding teas in the catalog: search, filters, „Neu" (#69) · *Accepted* (Claude, 2026-10-25)

The catalog list (SCR-19) gets CAT-10's search, filters and sort. Decided
while building it:

- **„Neu"** is a tea whose `first_seen_at` is the start of the latest
  successful refresh, and only when an earlier successful refresh exists: the
  first refresh finds every tea, so 274 „Neu" badges would say nothing. A
  refresh keeps a known tea's `first_seen_at` at the swap (D-077); a refresh
  that fails or is cancelled ends nobody's „Neu". The badge stays until the
  next successful refresh.
- **Filters** live in a pure `CatalogBrowser` (`core:domain`) over the
  current range; the search runs on `catalog_fts`. Chips and sheet as in
  03 SCR-19 (D-076). „Rezept prüfen" is a row badge, not a filter (CAT-10 has
  none). *Umami* filters by the shop's umami scale (08 §2.6: 0 none … 4–5
  very high); a tea without a scale passes only „Egal". Several origins of
  one tea („Japan, Korea") count each. Stock unknown counts as available.
- **Search:** every word of the search text is a prefix, all required, any
  other character only separates words (so no FTS syntax reaches the
  query). `catalog_fts` uses the `unicode61` tokenizer, so „älpler" finds
  „Älpler" and „goko" finds „Gokō" (05 §4.1; `catalog.db` is not frozen
  before `v0.2.0-rc.1`). The filters apply to the hits too; the hits they
  hide are counted with *Alle zeigen*, so a flavoured tea is „searchable but
  hidden" (D-073) without vanishing silently.
- **„im Besitz"** is a catalog key some collection tea links to
  (`tea.catalog_key`, archived teas included), read from `umamit.db` by a
  new query; no schema change.
- **Not stored:** search, filters and sort live as long as the screen; the
  list always opens in the default view (D-073), so 05 §6's `catalogSort`
  setting is dropped.

<a id="d-081"></a>
### D-081 — Catalog package sizes and prices, split numbers (#67) · *Accepted* (Claude, 2026-10-25)

The catalog shows each tea once with all its sizes (CAT-02, CAT-11, 08 §2.4
items 6–7). Decided while building it:

- **No size page is fetched** (D-076's budget): the sizes are the union of
  the page's configurator options („250g 3% gespart¹") and the sitemap's
  `<base>-<grams>` URLs. The fetched page's own size (its unit content, else
  the checked option) has the page's price and stock; a larger size is
  estimated by 08 §2.4's formula (savings from its option, else 0 %), half up
  to the cent; a smaller size has no price („Preis beim Hinzufügen"); the
  other sizes' stock is unknown. Each size keeps its sitemap URL as
  `variant_url` (the product page for the page's own size; none for a size
  only the configurator names, whose article number is then `<base>-<grams>`).
  `label` is „250 g · 3 % gespart" as in 05 §4.2. Parser version 3.
- **Split numbers** are merged after all pages are read, before the staging
  tables are filled: same normalised name (lower case, sizes such as „30g" and
  punctuation removed; the URL slug when a page has no name), only when the
  pages' own sizes differ. Two pages with the same name and the same size are
  two teas (e.g. two harvests), and a size can be a package only once
  (primary key `catalog_key`, `grams`). The lowest number is the key and its
  page gives the details; a size's own page wins over another page's
  estimate; the tea is in stock when any of its pages is; the price per 100 g
  is the key page's. Merges are written to the diagnostics log (numbers only).
- **SCR-21 *Packungen*:** after *Zubereitung*, one row per size, smallest
  first: „12,40 €", „≈ 30,07 € · 3 % gespart", „Preis beim Hinzufügen", with
  „nicht vorrätig" for a size known to be out of stock and a note under the
  rows explaining „≈" when an estimate is shown. Numbers and units are joined
  by non-breaking spaces so a row never breaks inside „3 % gespart" at large
  font sizes. The list keeps the price per 100 g (CAT-10, #64).

<a id="d-080"></a>
### D-080 — The other timer notification is cancelled again after `startForeground` (TMR-41, D-044) · *Accepted* (Claude, 2026-10-25)

The CI emulator test `ProcessDeathTest` (API 35) once found notification 1
still posted, with its foreground flag, next to the alert of a step whose
process had been killed. The service cancels the other notification before
`startForeground`. After a process death, though, the system can still hold
notification 1 as the restarted service's foreground notification, and it
ignores a cancel of the current foreground notification. The service now
cancels the other notification once more right after `startForeground`, when
it is no longer the foreground one. Only one of notifications 1 and 2 is
shown at a time again (D-044). Robolectric cannot reproduce the system's
refusal, so the emulator test stays the check.

<a id="d-079"></a>
### D-079 — Catalog refresh in the background: service, notifications, entry points (#65) · *Accepted* (Claude, 2026-10-25)

The refresh keeps running with the phone locked (CAT-07 AC1, 06 §5.3) and
gets its three entry points. Decided while building it:

- **Hosting:** the refresh job stays in the app's own scope
  (`AppCatalogRefreshRunner`, D-077). `CatalogRefreshService`, the `dataSync`
  foreground service, keeps the process running while it lasts and follows
  the runner's state for its notification; it has no refresh logic, so the
  screens keep observing the same `CatalogRefreshRunner`, and *Abbrechen* in
  the sheet and in the notification cancel the same job. The system creates
  the service; the app hands it the refresh through `CatalogRefreshHost`
  (the timer's `TimerHost` pattern, D-057).
- **The timer first (rule 8, CAT-07 AC8):**
  - own service, channel `catalog` (LOW, so Android 16 sorts it into the
    silent section and never groups it with the alert, D-044), own
    notification IDs (10 progress, 11 result), own wake lock, no alarms;
  - the progress notification changes at most every 2 s: Android drops an
    app's notification updates beyond five a second, and the timer's Live
    Update must never be one of them;
  - every start calls `startForeground` before anything else, and only the
    latest start stops the service (`stopSelfResult`): a service started with
    `startForegroundService` that stops without it crashes the process;
  - a refused foreground start (Android 15+'s six-hour daily `dataSync`
    budget) is logged, and the refresh then runs while the app lives, as in
    #64; at the end of the budget (`onTimeout`) the refresh is cancelled and
    the service stops at once;
  - an error the refresher does not turn into an outcome itself (e.g. a stack
    overflow on a page) ends the run as „shop changed" instead of the process.
- **Locked phone:** a foreground service alone does not keep the CPU awake.
  The service holds a partial wake lock of its own (`UmamiT:CatalogRefresh`),
  renewed with each progress update and expiring 10 min after the last one, so
  a stuck refresh never drains the battery; it is released at the end
  (`WAKE_LOCK` was declared for the timer already; 06 §5.7 updated).
- **Notifications:** the progress „Katalog wird geladen · Seiten: 120 von 1560
  · Produkte: 40" with *Abbrechen*; at the end a quiet result („Katalog
  geladen · 274 Grüntees · 12 neu", or „Katalog nicht geladen" with the
  reason), none after *Abbrechen* or when the result was seen in the app
  already; seeing the result in the app or starting the next refresh removes
  it. A tap opens the app where it was, like its launcher icon. A deep link
  into SCR-19 would be a navigation of its own that must give way to alerts
  like the timer's; it is left out for now (03 §2.4 updated).
- **Entry points:**
  - Sammlung › Katalog's ⋮ menu: *Katalog aktualisieren* starts the refresh
    and opens the sheet (during an alert it starts without the sheet);
  - Settings, section *Katalog* (SET-23): „Letzter Abruf" with the date of
    the last successful refresh from the refresh log („21. Okt. 2026", or
    „Noch nie") and *Jetzt aktualisieren*, which waits while a refresh runs;
    progress and result show in the notification;
  - the setup's third page (ONB-03): *Katalog jetzt laden* shows the progress
    inline and *Weiter* finishes the setup while the load goes on; *Später*
    loads nothing. The page belongs to every run of the setup (also
    *Einrichtung erneut zeigen*); a phone set up before M2 never sees it
    (D-073).

<a id="d-078"></a>
### D-078 — Catalog tea detail: parser rules, photos, shop link (#66) · *Accepted* (Claude, 2026-10-25)

The second M2 slice (spec #62, #66) fills every CAT-03 field, downloads the
photos and shows a catalog tea's detail (SCR-21). Decided while building it:

- **Parser rules** (08 §2.3, parser version 2): every detail field is read
  from a copy of the page without the other products it embeds (D-076).
  Several values of one property are joined with „, ". The region is the
  first of a fixed list of growing regions (Japan, China, Taiwan, Korea)
  named as a whole word in the title, else in the description. *Flavoured*:
  an *Aroma* other than „naturbelassen", an ingredient other than tea, or the
  flavoured section; roasted rice (genmaicha) is no flavouring, and footnotes
  („*aus kontrolliert ökologischem Anbau") are no ingredients. The brewing
  prose is the description's paragraphs about brewing (Zubereitung, Aufguss,
  amounts in g or ml, °C), verbatim; `brew_table_raw` holds one object per
  grid row, `{"kind":"japan","cells":[{"label":"Menge","text":"~4g/100ml"},…]}`,
  the labels from the grid's header.
- **Description** (08 §2.8): staff tips, scripts, styles, forms, images and
  embeds are removed (a video player becomes a plain link); headings become
  bold paragraphs; only paragraphs, line breaks, bold, italics, lists and
  absolute http(s) links remain, without attributes but `href`.
  `description_text` is its plain text, one paragraph per block.
- **Photos** (CAT-08): the sanity check runs before the photos, so a broken
  parser costs no photo request. Each green tea's photo goes through the
  refresh's shop session; a photo that is missing, disallowed or doesn't
  decode leaves that tea with the photo it had (or none), a stop ends the run
  like any stop. `photo_file` stores the file name (`<key>.webp`). After
  every run, successful or not, the photo files of teas the catalog doesn't
  hold are deleted. With SET-22 off, a refresh fetches no photo and keeps the
  photos already stored. Until #70 every photo is downloaded again on each
  refresh (no change detection yet).
- ***Im Shop öffnen*** (D-072): the size of the latest INITIAL or PURCHASE
  event is matched by grams, else by its package label; without a match the
  product page opens. The page goes to the browser as a `VIEW` intent
  (`CATEGORY_BROWSABLE`); without a browser a short message says so.
- **SCR-21 layout:** the photo full width (4:3, the type-coloured leaf
  without one) with the back button over it; name, secondary name, tags
  (*Sorte*, Bio, aromatisiert, Coldbrew geeignet, nicht vorrätig), price per
  100 g and *Im Shop öffnen*; then the sections *Steckbrief*, *Geschmack*
  (scales as five leaves, aroma, taste), *Beschreibung* and *Zubereitung*
  (the shop's grid per method and its text, verbatim), each only when the
  shop fills it. The derived recipe, the packages and *In Sammlung
  aufnehmen* follow with #68, #67 and #71.

<a id="d-077"></a>
### D-077 — Catalog refresh tracer bullet: staging, skip list, parser rules, sheet (#64) · *Accepted* (Claude, 2026-10-25)

The first M2 slice (spec #62, #64) runs *Katalog laden* from the empty
Katalog tab to a filled list. Decided while building it:

- **Staging tables** (05 §4.5): `catalog_tea_staging` and
  `catalog_package_staging` are real tables of `catalog.db` (part of its
  frozen schema from `v0.2.0-rc.1`) with exactly the columns of `catalog_tea`
  and `catalog_package` in the same order (the staging entities embed the
  live ones; a test holds them equal), so the swap is `INSERT … SELECT *` in
  one transaction. Each refresh empties them first and after any end without
  a swap. `catalog_package` is keyed by (`catalog_key`, `grams`).
- **The skip list changes only with a successful swap**, in the same
  transaction. A refresh that stops, is cancelled or fails the sanity check
  keeps no classification, so a broken parser or a renamed shop section can
  never hide teas for 90 days; the price is that an interrupted first
  refresh classifies again next time. A product page without a breadcrumb is
  neither kept nor skipped and counts as defective for 08 §2.9.
- **Sanity check** (08 §2.9): defective pages count among the products and
  as lacking name, price and article number; the brewing threshold is judged
  only once the parser reads the brewing grid and prose (#68).
- **Parser rules** (08 §2.3): the secondary name is the Japanese or Chinese
  text in the title (kana, kanji, CJK brackets), the name the rest without the
  size suffix; *Sorte* is the title of the breadcrumb's green type level
  (`…/japan/<type>/`, `/tee/japantee/<type>/`, `matcha-teepulver`), else
  empty; the price per 100 g comes from the unit price, else from price and
  content; stock is unknown (null) when the buy box shows neither a buy
  button with delivery information nor a stock notification. Only the
  product's own buy box is read. The live sample run checks these rules.
- **Discovery** (08 §2.4): a product URL is `/<slug>/<article number>`; per
  base number the parent's page is fetched, else its `M` page, else the
  smallest size. A sitemap that is missing, disallowed, broken or lists no
  product is a structural failure.
- **Outcomes:** success; cancelled; blocked (challenge, Cloudflare block,
  429, `Retry-After`); offline; shop unavailable (401/403/5xx without
  Cloudflare); shop changed (structural, also for an unexpected error, so a
  refresh bug never crashes the process the timer runs in); storage failed.
  Each failure carries technical details for *Details kopieren* (counts and
  article numbers, no shop content) and is logged (`catalog_refresh_log`,
  diagnostics log).
- **Hosting until #65:** an app-scoped job (`AppCatalogRefreshRunner`) runs
  the Android-independent `CatalogRefresher` while the app's process lives;
  #65 moves it into the `dataSync` foreground service behind the same
  `CatalogRefreshRunner`. Closing the sheet never stops the refresh; the empty
  state then offers *Fortschritt anzeigen*. An alert removes the sheet in the
  same frame (rule 8).
- **Sammlung › Katalog** is a slot of `CollectionScreen` that the app fills
  with `feature:catalog` (no feature-to-feature dependency, 06 §3); the
  catalog's lazy list scrolls itself below the segmented control. Until #69
  the list shows every tea of the current range by name, with *Sorte* and
  price per 100 g; until the PO's A8 arrives, the empty state shows the
  placeholder landscape.

<a id="d-076"></a>
### D-076 — M2 implementation clarifications (exploration of the shop and the code) · *Accepted* (PO for the request budget, Claude for the rest, 2026-10-25)

An exploration before the first M2 slices (spec #62) found contradictions
between the specs, the tickets and the code. Resolved:

- **Request budget (PO):** the live sitemap lists ~1,560 distinct base
  article numbers across all shop categories, not ~700. The first refresh
  classifies every unclassified parent once, so it takes ≈ 15–18 min
  (background service, phone may be locked); later refreshes stay ≈ 3–4 min.
  No prefix shortcut, so no green tea is missed. NFR-PERF-11 first refresh
  ≤ 20 min; 06 §5.3 and 08 §2.4 updated.
- **Retries:** CAT-07 AC2's „≤ 3 retries" applies only to timeouts and
  connection errors of a refresh request. Each retry is a new call through
  `ShopClient` (rate-limited, allow-listed), after 2/4/8 s. Stop signals
  (challenge, block, 429, `Retry-After`) and HTTP errors are never retried.
  This keeps D-050 (one network attempt per call). Seed photos (run once)
  and the exact price fetch (falls back to the estimate) don't retry.
- **Structural failure:** the refresh aborts by the three thresholds of
  08 §2.9, not by a single „< 50 % parsed" rule (#64's wording).
- **Manifest:** `FOREGROUND_SERVICE_DATA_SYNC` is already in the allowed
  list of the manifest check (06 §5.7); M2 adds the permission and the
  service to the manifest only.
- **Filters:** CAT-10 is the source of truth. SCR-19 shows the frequent
  filters as chips and the rest in a filter sheet; #69 aligns 03.
- **catalog.db columns:** a plain-text `description_text` for search (FTS),
  and the thumbnail as `<key>_thumb.webp` next to the photo (05 §4.1).
- **Discontinued teas kept when owned:** the refresh reads the owned
  `catalog_key`s from `umamit.db` before the swap (two databases, no shared
  transaction).
- **Built-in styles:** 08 §3's style table is code for the first time
  (pure module). Styles without a cold-brew default use sencha's (08 §2.5
  step 6).
- **Photos copied into the collection** (*In Sammlung aufnehmen*, CAT-15)
  are re-encoded to the collection's JPEG format, so restore detection
  keeps them.
- **Page parsing:** selectors are scoped to the product's own sections
  (product pages embed other products); the nested brewing grid is
  de-duplicated; staff tip blocks are stripped from the description; the
  sitemap `.xml.gz` is decompressed by the app (the shop sends no
  `Content-Encoding`). Challenge markers are checked only on refusals, as
  `ShopClient` already does, because normal pages contain the marker text.

<a id="d-075"></a>
### D-075 — Seed retry at app start (#58 D-O1, M2-11) · *Accepted* (Claude, 2026-10-25)

A seed that failed on the first start (D-066: logged, the download left due)
was never retried after *Weiter*, so Start and the tea picker stayed empty.

- **Every app start retries the seed** while `seedDataCreated` is false:
  `UmamitApplication` calls `FirstStart.retrySeedAtAppStart`, once per
  process, in the first start's IO scope. It runs only once the setup is done
  (`onboardingCompleted`); before that the setup page's first start seeds. The
  seeder's own rules stay: one transaction, nothing on a database that already
  holds personal data (a restore), the marker set afterwards. A failure is
  logged and retried at the next start; nothing is shown.
- **Only the seed, never the photo download.** The retry sends no request,
  so the one-time seed photo download (ONB-06, D-050) stays a first-start
  setup thing: it is never repeated, and it is not started outside the setup
  either (GEN-01, NFR-PRIV-02). The covers of seed teas created by a retry
  come with the first catalog refresh (CAT-15).
- Instrumented tests mark the seed done before the setup (`SetupDoneRule`),
  so the retry never seeds in the emulator.

<a id="d-074"></a>
### D-074 — Shared shop session and image targets (M2 prefactor, #63) · *Accepted* (Claude, 2026-10-25)

**Shop session (`core:network`, rule 7):** the robots.txt handling and the
stop mapping move out of the seed photo download into `ShopSession`, which
the catalog refresh (CAT-07) and the exact price fetch (CAT-12) reuse. One
session = one job; every request still goes through the one `ShopClient`.
- `ShopSession.open` reads robots.txt once with D-050's status rules: 2xx
  parsed; 404/410 → everything allowed; a stop answer → the session starts
  stopped; any other answer → everything disallowed.
- `fetch` returns `Fetched`, `Disallowed` (robots; nothing sent),
  `Unavailable(code)` (3xx, 404, other 4xx: only this URL is left out) or
  `Stopped(cause)`. The causes: network, challenge, Cloudflare block page,
  429, `Retry-After`, 401/403/5xx.
- Retries per D-076 are the session's `NetworkRetry`: `REFRESH` retries a
  timeout or connection error after 2, 4 and 8 s (robots.txt included), each
  a new call; `NONE` (seed photos, exact price) never retries. An oversized
  answer or a refused second attempt is not retried; when the retries fail,
  the session stops with `NETWORK`.
- A stop is **sticky**: every later `fetch` returns the same stop without a
  request. Fetches of one session are serialised, so a fetch queued behind
  the stopping one is never sent. A cancellation is no stop.
- A foreign host still fails with `HostNotAllowedException` (a programming
  error). The seed photo download behaves exactly as before (D-050); its
  `FailureCause` maps one-to-one from the session's `StopCause` plus
  `STORAGE`.
- Not in this step: `Crawl-delay` (the shop sets none, 08 §2.2) and a stop
  shared across concurrent sessions.

**Image targets (`PhotoTranscoder`, CAT-08):** re-encoding takes a target
(long-side cap, optional short-side cap, format, quality) and never
upscales or changes the aspect ratio. The source is decoded once, sampled for
the largest target, and re-encoded without metadata.
- Collection photos and covers: ≤ 2048 px JPEG 85 (05 §3.9, unchanged).
- Catalog photo: ≤ 1200 px WebP (lossy, quality 80, D-073).
- Catalog list thumbnail: WebP quality 80, short side 256 px (a round list
  thumbnail up to 64 dp at 4× density), never larger than the photo. The
  thumbnail size was not fixed before; it adds roughly 10 KB per tea to
  CAT-08's 25–35 MB.
- Sources stay JPEG only, as the shop's images are (08 §2.2); truncated,
  oversized (> 8192 px per side or 40 MP) or undecodable images give no photo.

<a id="d-073"></a>
### D-073 — M2 cut, behaviour and process (grilling with the PO) · *Accepted* (PO, 2026-10-25)

Grilled with the PO before the M2 spec. The catalog itself stays as
specified (CAT-01 … CAT-15, 08 §2); these answers settle what the specs left
open or what M1 changed.

**Scope of M2**
- *In Sammlung aufnehmen* and *Nachkauf erfassen* (CAT-12, SCR-23) ship with
  M2, not M3. A new tea is immediately in the tea picker and brewable with
  its derived recipe and stock deduction. M3 adds the *Meine Tees* screens,
  the tea detail (with INV-09) and the recipe editor.
- The tea picker's *Aus dem Katalog* leads through the catalog to the add
  sheet and back to the brew setup with that tea selected. A catalog tea is
  never brewed without adding it (stock and journal stay correct).
- A tea flagged „Rezept prüfen" is adjusted per brew in the Schritte-Sheet;
  the „Gespeichert" sheet then offers *Rezept passt*, which clears the flag
  (CAT-05, SCR-06). The full editor stays in M3.
- The Tee-Info guides (CAT-09) are the last slice of M2; if they hold up the
  first rc, they follow in a later rc.
- From #58, M2 also takes: the seed retry at app start (D-O1), the °C spacing
  and abbreviations (U-O7), the navigation label at font 2.0 (U-O1), the tea
  picker's surface colour (U-O8) and the unused imports (U-O9).

**Behaviour**
- On a phone whose first-start setup is done, the catalog is first loaded
  from the empty state of Sammlung › Katalog (*Katalog laden*) or Settings;
  ONB-03 shows only on fresh installs. No extra card on Start.
- A refresh may run during a brew session; the timer always has priority
  (CAT-07 AC8, rule 8).
- Teas first seen in the latest refresh get a „Neu" badge and a *Neu* filter
  (CAT-10).
- The catalog list opens grouped by *Sorte*, available teas only, with the
  „Pur" filter on: flavoured teas are loaded and searchable but hidden until
  the filter is changed (CAT-10).
- Photos: max 1200 px WebP plus a list thumbnail, about 25–35 MB (CAT-08;
  was 1600 px).

**Process**
- Claude tests the parser against the live shop from the cloud session:
  samples of about 20 teas during development and one full run before each
  rc, with the app's own limits (≤ 2 requests/s, one connection, robots.txt,
  stop on a challenge or 429). Nothing from the shop is committed (D-016).
- The accuracy run (09 §4a, D-067) is required from M2 on only for an rc whose
  changes touch timer code, and always before the final release.
- An early design check of the catalog list, tea detail and add sheet
  follows the first UI slice (like #39); the final design review comes with
  the release candidate.
- Test plan (agreed with the PO for the M2 spec, kept lean): parser and
  recipe rules on synthetic pages; the whole refresh against a simulated shop
  (rate limit, stop signals, variant merge, atomic swap); adding and
  repurchase on a test database; screenshots of the catalog list, tea detail,
  add sheet and refresh sheet (light and dark, font 2.0 for list and detail);
  Claude's live-shop runs; the PO's checklist. No emulator test of the
  refresh: the PO checks a refresh during a brew on the phone (09 §4 M2).
- The PO generates the illustrations A8 (empty catalog) and A11 (Ratgeber
  header) now, for the first M2 rc.

<a id="d-072"></a>
### D-072 — One-tap reorder from the tea detail (CAT-11, INV-09) · *Accepted* (PO, 2026-10-25)

The PO wants ordering a particular tea to be as easy as possible. The specs
had *Im Shop öffnen* on the catalog tea (SCR-21) but hid it in the overflow
menu of the collection tea (SCR-09), where reordering starts. Decided:

- SCR-09 shows „Beim Tee-Kontor nachbestellen" in VORRAT & KOSTEN for every
  catalog-linked tea (new INV-09); it leaves the overflow menu. It is a filled
  button when the stock is *Knapp* or *Leer* (INV-05), otherwise a text link.
- The link opens the shop page of the **package size bought last**: every size
  has its own URL (08 §2, `catalog_package.variant_url`). The size comes from
  the grams of the tea's latest INITIAL or PURCHASE event; without a match, or
  when the size is gone, the product page (`product_url`) opens. SCR-21's
  *Im Shop öffnen* follows the same rule for teas already in the collection.
- No schema change: v1 is frozen (D-054), and the existing columns
  (`inventory_event.delta_grams`, `catalog_package.grams`/`variant_url`,
  `tea.product_url`) are enough.
- Opening the browser is the user's own navigation, not one of the app's
  three network uses (GEN-01, D-036): the app sends no request.
- Teas without a `catalog_key` and discontinued catalog teas show no link.
  It ships with M3 (SCR-09); SCR-21's rule ships with M2.

<a id="d-071"></a>
### D-071 — A Live Update overtaken by the foreground start's late post is posted again (TMR-41, #54) · *Accepted* (Claude, 2026-10-23)

CI on API 35 showed notification 1 still with READY's text more than 10 s
after *Start* (`TimerNotificationsDeviceTest`, intermittent). `BrewTimerService`
shows a notification in two ways: the first command calls `startForeground`,
a later one with the same ID calls `notify`. In AOSP the notification of
`startForeground` is not posted synchronously: `ServiceRecord.postNotification`
queues it on the ActivityManager's handler, which later hands it to the
NotificationManager, while the app's `notify` is a direct binder call. An
update within milliseconds of the start can therefore reach the
NotificationManager first and be overwritten by the start's older content:
for up to 15 s while RUNNING (the refresh, 04 §8.3, repairs it) and for good
in READY, PAUSED or FINISHED. On the phone the likely trigger is a
notification action that cold-starts the process (the recovery's
`startForeground`, then the action's update). Decided:

- The service remembers when its last `startForeground` succeeded
  (`SystemClock.uptimeMillis`). A same-ID update posted with `notify` within
  `REPOST_WINDOW_MS` (1 s) of it is posted once more after `REPOST_DELAY_MS`
  (0.5 s) on the main looper, with `FLAG_ONLY_ALERT_ONCE` added, so the copy
  never rings or vibrates again.
- At most one re-post is pending; any newer command replaces it. It is
  dropped on `stopForeground` and in `onDestroy`, and it posts only while
  that ID is still the foreground notification.
- Same-ID updates do not switch to `startForeground`: every later call is
  checked again against the background-start rules and could be refused.
- The port and the controller do not change. The service logs which path
  each notification took (`startForeground`, `notify`, re-post); the device
  test's failure dump adds `dumpsys activity services`, and CI's logcat
  filter adds `NotificationService`, `Shedding`, `enqueue rate` and
  `ActiveServices`.

Consequences: a phase change is no longer left stale behind the start's
late post, as long as that post lands within 0.5 s of the update; a later
one is still repaired by the 15 s refresh while RUNNING. The device test
keeps its 10 s timeout: 04 §8.3 tolerates 15 s only for progress within a
state. Unit test: `LateForegroundPostTest` (Robolectric). 04 §6.3 and §8.3
updated.

<a id="d-070"></a>
### D-070 — Release gate and workflow hardening (M1 milestone review, #54) · *Accepted* (Claude, 2026-10-23)

The M1 milestone review (data S1–S3 and O2, timer S3) found that a release
could be built from a commit whose CI or schema checks were red, that the job
holding the signing key gave a tag-pinned third-party action a write token,
and that nothing forced a schema version bump to ship its migration. Decided:

- **Release only a green commit.** `release.yml` requires a successful
  `ci.yml` run on the exact commit it builds (GitHub API, `actions: read`;
  runs are matched by the workflow path, not by job names, so renaming a CI
  job does not break it). It also repeats CI's schema freshness check and
  `scripts/check-frozen-schemas.sh` after its unit tests. The rc flow is
  therefore: push the bump, wait for CI, dispatch.
- **Signing and publishing are separate jobs.** The `build` job holds the
  keystore with `contents: read` (plus `actions: read`). It runs only
  GitHub's own actions (`checkout`, `setup-java`, `upload-artifact`) and no
  longer `gradle/actions/setup-gradle`, which injects init scripts into every
  Gradle build of its job, the signing build included; a read-only cache
  saved little for a handful of releases. It verifies the APK
  (`verify-release-apk.sh`, fingerprint against
  `signing/release-cert-sha256.txt`) and uploads it as an artifact. The
  `publish` job has `contents: write` and `actions: read` and contains no
  `uses:` at all: it downloads the artifact with `gh run download`, checks its
  SHA-256 against the build job's output, runs the verify script again with
  the script and fingerprint file of the built commit, and creates tag and
  release with `gh release create`. The workflow-level permissions are `{}`.
- **Actions pinned to commit SHAs** in `release.yml`, `ci.yml` and
  `accuracy.yml`, with the version in a comment (replaces D-042's major-tag
  pinning). The SHAs were resolved from the action repositories' tags on
  2026-10-23 and equal what the major tags pointed to, so nothing changed in
  behaviour: `actions/checkout` v7.0.1, `actions/setup-java` v6.1.0,
  `gradle/actions/setup-gradle` v6.4.0, `actions/upload-artifact` v7.0.2,
  `actions/cache` v6.1.0, `reactivecircus/android-emulator-runner` v2.38.0.
  Updates are made by hand: resolve the new tag to its commit, replace SHA
  and comment in all three files.
- **The freeze compares what Room compares.** `check-frozen-schemas.sh`
  passes a frozen schema file that is byte-identical or that keeps its
  `database.version` and `database.identityHash`; a formatting-only
  difference (a Room upgrade writing the JSON differently) passes with a
  warning. A byte comparison would make every such Room upgrade impossible,
  because the freshness check requires the regenerated file to be committed.
  A changed version or identity hash, a deleted file or an unreadable one
  fails (replaces D-054's "byte-identical").
- **Every committed schema version opens with the production builder.**
  `core:database` keeps its migrations in one list, `UMAMI_MIGRATIONS`, which
  `umamiDatabaseBuilder` registers. `SchemaVersionsTest` (Robolectric)
  creates a database of every committed `N.json` with `MigrationTestHelper`,
  opens it with `umamiDatabaseBuilder` and expects `UmamiDatabase.VERSION`,
  runs `runMigrationsAndValidate` with the same list, and checks that the
  committed files are exactly versions 1 to `VERSION`. At version 1 it only
  reopens v1; a bump to 2 without a `Migration(1, 2)` in the list fails it
  (tried locally).
- **Dispatch-only workflows on `main`.** D-039's exception covers both
  `release.yml` and `accuracy.yml`. The file only has to exist on `main`;
  the version at the dispatch's workflow ref runs, so both are dispatched
  with the PR branch as the ref, and the M1 branch's versions are copied to
  `main` unchanged by a housekeeping PR.
- **M1 checklist (09 §4) gains the missing rule-8 cases:** a long step in
  Doze (30-min quick timer, not charging), the quick timer (1 min and a
  12-min long step), *Beenden & neu starten* during a running step, and a
  best-effort refused background start whose result is read in *Diagnose*
  (the emulator cannot refuse a start, 04 §12; otherwise the Robolectric
  tests cover it). The reboot item now names the real notice text.

<a id="d-069"></a>
### D-069 — M1 review fixes for the app and the UI (#54) · *Accepted* (Claude, 2026-10-23)

The M1 milestone review (#54) found these in the app; decided while fixing:

- **A bar that arrives alerting snaps in (rule 8, 03 §3.8).** A new activity
  (rotation, a light/dark switch, reopening from the launcher) built the active
  brew bar's source with `null`, so the matcha bar grew and faded in over about
  0.4 s while the step already alerted. `TimerActiveBrewSource` now starts
  with a synchronous value from the controller's current state, and
  `ActiveBrewBar` enters with `EnterTransition.None` when the brew it arrives
  with is alerting. Both are tested against the old behaviour.
- **System bars with one owner.** `enableEdgeToEdge` picks the bar icons from
  the phone's theme, which made the clock, the battery and the gesture handle
  invisible on the always-dark timer in the light theme. The root
  (`UmamitRoot`) is the only place that sets the appearance, derived from the
  destination on top of the back stack (as the lock-screen flags): the timer
  gets light icons on the Steeping surface and dark ones on the washi/matcha
  alert, the quick timer (SCR-07) light ones, everything else the app theme's
  own. A first version let each screen set and restore it; SCR-07 and the
  timer overlap during the crossfade and SCR-07 restored last, leaving the
  running quick timer and then the shell with the wrong icons (re-review of
  #54). Renders have no system bars, so this is a Robolectric test of
  quick timer → timer → alert → back, plus a 09 §4 checklist item.
- **Honest placeholders (GEN-09).** *Meine Tees* no longer says the shelf is
  empty (the seed teas exist) and *Statistik* no longer waits for a first
  brew: both say the area follows with a later update and where to go now
  (Start, the journal). The segments of *Sammlung* stay at the top. Renders in
  light, dark and font 2.0 are in `docs/design/review/M1/`.
- **The tea photo reaches the timer (amends D-058).** `TimerViewModel` reads
  the session's tea and its cover photo file. A brew always reserves the
  photo's space and draws the landscape placeholder until the file is read,
  when the tea has none, or when it cannot be loaded; so nothing moves when the
  photo arrives, and the renders show what the phone shows. The quick timer has
  no photo.
- **No stale question after a double tap on *Nochmal brühen* (TMR-21).**
  `HomeViewModel` ignores taps while a start is in flight (as D-065 does for
  the quick timer) and treats a refusal within 1 s by the session it has just
  started as the same gesture: no „Erst die …-Sitzung beenden?" is left on
  Start. A deliberate tap later still asks.

<a id="d-068"></a>
### D-068 — M1 review fixes in the timer area: deadline guard, accuracy up to the sound, recovery-aware timer route (#54) · *Accepted* (Claude, 2026-10-23)

The M1 milestone review (#54) found these in the timer area; all are fixed
on the M1 branch. Amends D-059, D-063, D-064 and D-067.

- **Deadline guard (amends D-063).** A long step has its deadline alarm as
  the only wake-up. Android skips every broadcast to a process while Auto
  Backup runs the app's full backup (`BroadcastQueueModernImpl`,
  "isInFullBackup"), counts it as delivered, and then kills the process; the
  backup runs when the phone is idle and charging, the conditions of an
  overnight cold brew. Recovery the next morning would acknowledge the step
  silently and record it as poured on time. So the alert watchdog alarm
  (request code 107, exact, `ELAPSED_REALTIME_WAKEUP`) is also set **while a
  step runs, 90 s after its deadline**. In a live process the deadline turn
  replaces it by the alert's 30 s cadence (the first one 30 s after the alert
  started), and `StartAlert` never restarts the playing alert, so an alert
  never plays twice. A process the guard starts recovers the step with the
  deadline passed less than 10 min ago and alerts, at most 90 s late (the
  exact-alarm delivery allows the FGS start). Pausing or ending the step
  cancels it. 90 s lies in the review's 60–120 s window: late enough that a
  backup that swallowed the deadline has ended and killed the process,
  early enough for the alert. Declaring `PROPERTY_USE_RESTRICTED_BACKUP_MODE`
  instead was rejected: backing up `umamit.db` and its WAL while the app
  writes risks an inconsistent copy. Cost: one alarm set and cancelled per
  step. Tests: the guard is set while RUNNING and goes with the step; a long
  step whose deadline alarm never came alerts when only the guard starts a
  new process; the deadline and the guard in a live process give one alert,
  in either order.
- **Accuracy up to the sound (corrects D-067).** The accuracy gate and
  *Diagnose* measured up to the controller's handling of the event, before
  the persist, the alarms, the service command and the player's main-thread
  queue, so a late sound could pass. The alert port now hands the player the
  scheduled time of the tick (the event's due time) and of the alert (the
  step's deadline); the player's „alert" log entry, written after the sound
  started, carries scheduled vs actual time and the delta like an event
  entry. `AccuracyReport` pairs each step's accepted event with that entry
  (`CueTimes`, same kind and scheduled time) and **gates on the sound
  start**; the handled delay stays as a second column (CSV, summary), so a
  slow persist or queue shows where the time went. *Diagnose* shows both
  lines; the „alert" line is the one the PO checks (09 §4).
- **Accuracy run reset.** `AccuracyRunTest` resets like `ProcessDeathTest`:
  alarms and the alert watchdog cancelled, process killed, database deleted,
  killed again, so a run that fails mid-alert leaves nothing to replay.
- **Alert settings after a failed read (amends D-059).** One failed read of
  the settings file ended the player's settings cache, which then kept the
  defaults until the process restarted (SET-09, SET-11, SET-14 ignored).
  The cache now keeps the last settings read (or the defaults before the
  first), logs the failure and reads the file again after 1 s, doubling up to
  a minute, so a later alert follows the stored tone, length and vibration.
  The alert still never waits for the file longer than 300 ms (rule 8).
- **An unreadable settings file never crashes a screen.**
  `DataStoreSettingsRepository.settings` turns an `IOException` of the file
  (DataStore corruption, a disk error) into the defaults before the first
  value, logs it and reads again with the same backoff; any other error
  still fails. So the timer, setup and settings screens read the defaults
  instead of crashing; `MainActivity`'s own read for the onboarding decision
  stays. *Amended by the re-review (R2-1):* the fallback is the defaults
  with the first-start setup counted as done (`UNREADABLE_SETTINGS`), since
  the total flow no longer reaches `MainActivity`'s catch: otherwise a
  corrupt file showed the setup on every launch and kept Start and the timer
  out of reach (D-066: an unreadable file opens on Start). A fresh install
  has no file and still gets the setup. The full-screen op read also falls
  back to the permission check on any `RuntimeException`, since the timer
  screen's view model reads it unguarded.
- **The timer route waits for recovery (GEN-07, 03 §2.4).** The controller
  publishes `recovered` (`BrewTimer.recovered`, true by default for fakes)
  after the first recovery of the process has published its state, or when
  the stored timer could not be read (no screen waits for a broken
  database). The timer route closes on `Idle` only once recovered, checking
  the current state, so the route restored from recents or opened from the
  quiet READY notification after process death lands on the recovered timer
  instead of closing itself.
- **Cold and ice brew in the notification (BRW-10).** In READY the Live
  Update's text and chip name the water at most 10 °C as SCR-05 does:
  „Bereit · 4:00:00 · Kaltes Wasser" and the chip „Kaltes Wasser", or
  „Eiswürfel" for an ice brew (#49's `iceBrew`). The chip may be shortened
  by the system; it stays the same word as the screen. `KettleWater` (the
  ≤ 10 °C rule) moved from `core:designsystem` to `core:model`, so the setup,
  the timer screen and the notifications share it.
- **The full-screen check reads the app op.** On the CI emulator (API 35
  and 36), `canUseFullScreenIntent()` still answered true after
  `appops set … USE_FULL_SCREEN_INTENT deny`, so a revoked full-screen alert
  would never reach the reliability card or *Beheben* (TMR-31). The reader
  now also reads the op's mode (`unsafeCheckOpNoThrow("android:use_full_screen_intent")`):
  denied or ignored counts as missing, „default" leaves the answer to the
  permission check. `PermissionStatesDeviceTest` proves it on both levels.
- **Wording (rule 2).** 04 §4 and `TimerServicePort.stopForeground` now say
  that `StopForeground` removes notifications 1, 2 and 4; D-064's notes on
  two-turn *Beenden & neu starten* and on resending a stale refused start are
  marked as amended by D-063.

<a id="d-067"></a>
### D-067 — Accuracy workflow before each release candidate (TMR-30, #52) · *Accepted* (Claude, 2026-10-23)

#52 built the accuracy run of [04 §12](04-brew-timer.md#12-testing)
as the manually dispatched workflow the PO chose in spec #40 (seam 6). How to
run and read it: [09 §4a](09-release-plan.md#4a-accuracy-run-before-each-release-candidate).
Decided while building it:

- **Own workflow, own test.** `.github/workflows/accuracy.yml`
  (`workflow_dispatch`: `ref`, `api-level` 36 / 35 / both, `steps`,
  `inject-delay-ms`) runs `AccuracyRunTest` in `core:timer`'s instrumented
  tests. The regular emulator job skips that test (it runs only with the
  instrumentation argument `umamitAccuracy=true`), so CI stays fast. The
  emulator setup is copied from `ci.yml` with a keep-in-sync comment rather
  than moved into a composite action, so the green CI job stays untouched; both
  use the same cache keys. Like `release.yml` it must be on `main` before
  the first dispatch (D-039).
- **Run with `am instrument`, not Gradle** (`scripts/accuracy-run.sh`): a
  Gradle connected run uninstalls the test APK at the end, and the report is
  in the test app's private files, which the script copies out with `run-as`.
- **The run:** 50 steps of 60 s with a 10 s pre-alert in five sessions of
  ten, through the test APK's real controller, alarms, service and
  notifications (#42, #47). Between steps the test taps *Abgegossen* in the
  alert and *Start* in the Live Update, and ends each session with
  *Sitzung beenden*, as the PO does on the lock screen. The screen is
  switched off (`KEYCODE_SLEEP`, checked) from each *Start* until the alert
  turns it on.
- **What is measured** (*corrected by D-068*): the time from the scheduled
  time to the moment the alert player **started the sound** of the tick or the
  alert, on the monotonic clock: the player's „alert" entry of the diagnostics
  log (D-060), paired with the accepted `PreAlertReached` or `DeadlineReached`
  entry, which says whether the exact alarm or the in-process wake-up
  delivered it (the CSV says which) and whose own delay, the moment the
  controller handled the event, is shown next to it. ~~The moment the
  controller handled the event and started the tick or the alert~~ came
  before the persist and the player's queue, so it could not show a late
  sound. It does not include the audio output itself (the CI emulator has no
  audio).
  `FireTime.parse` is the inverse of `TimerDiagnosticFormat`, and a round-trip
  unit test keeps the two together. Parser and report (`AccuracyReport`) live
  in `core:timer` next to the format; nothing in the app calls them, so R8
  removes them from the release.
- **Verdict:** the absolute delays, nearest-rank p50 / p95 and the max per
  event. The run passes only when every planned step completed and both
  events have p95 ≤ 250 ms and max ≤ 1 s. A step that times out still writes
  the report of the steps before it.
- **Gate check:** `inject-delay-ms` makes the test app set the pre-alert and
  deadline alarms, and arm the wake-up, that many ms late (`InjectedDelay`,
  test APK only). A run with, say, 1500 ms must fail, which proves the gate
  bites without changing and reverting app code.
- **Result:** `accuracy.md` goes into the job summary together with the ref
  and the commit. The artifact `accuracy-api-<level>` (30 days) holds
  `accuracy.md`, `accuracy.csv` (one row per fire time: step, event, source,
  scheduled and actual wall and monotonic time, delta), the diagnostics log
  lines, the instrumentation output and logcat.
- **Scope:** the emulator proves the timer's own path (controller, alarms,
  wake-up, service) with the screen off. It does not doze within a 60 s step,
  and it is not a Samsung phone. The device numbers still come from
  *Diagnose* in the 09 §4 checklist.

<a id="d-066"></a>
### D-066 — First-start setup, timer settings, the reliability card and the banner (M1-15, #50) · *Accepted* (Claude, 2026-10-23)

#50 built the first-start setup (SCR-01), the timer settings (SCR-25), *Über*
(SCR-27) and the reliability banner (TMR-31)
([03 §4](03-ux-ui.md#scr-01-einrichtung-first-start)). Decided while building
them:

- **One reliability seam in `core:timer`.** `TimerReliability` (`status()`,
  `settingsIntents(check)`) replaces the timer screen's own interface and the probe-backed
  `PermissionTimerReliability`. It maps the permission states #43 already reads
  for *Diagnose* (`PermissionStatesReader`, the probe's proven checks of #14) to
  five `ReliabilityCheck`s: notifications, Live Updates, the full-screen alert,
  the battery and exact alarms, each ✓, ! or not on this platform (Live Updates
  before API 36, never a problem). *Beheben* opens the check's own page, the
  probe's proven intents, with the app's details page last; the banner opens
  the first missing check in the card's order. The screen starts the pages
  from its Activity, as the probe did, so they open in the app's task and
  back returns to the app. The probe keeps its own copy
  until #54 removes it. The setup, the settings card, Start and the timer read
  the checks again on every resume.
- **The notifications row** asks for `POST_NOTIFICATIONS` while it is not
  granted, and opens the app's notification settings once Android shows no
  dialog any more or the permission is granted but notifications are off
  (`rememberNotificationPermissionAction` in `core:ui`, the probe's flow).
- **The setup is a root route.** While `onboardingCompleted` is false it is
  the start destination, so back on the welcome leaves the app and it shows
  again on the next start (ONB-04); *Weiter* writes the marker, then Start
  replaces the setup. Nothing is drawn until the marker is read, and no
  navigation (alert, notification request) runs before the graph exists. *Über
  › Einrichtung erneut zeigen* opens it above the shell like the other routes,
  so it gives way to a new alert (D-062, D-064); *Weiter* there returns to
  *Über*. An unreadable settings file opens on Start. Instrumented tests mark
  the setup done first through the app's settings store (`SettingsEntryPoint`),
  so the emulator never runs the first start or sends the seed photo requests.
- **First start in the background.** `FirstStart` (app scope, IO) runs
  `SeedDataSeeder.seedIfNeeded`, then `SeedPhotoDownloader.downloadIfNeeded`,
  once per process, as soon as the setup opens; it goes on after *Weiter*.
  A failed seed is logged and leaves the download due (D-054). The page shows
  „Teefotos werden geladen …" with *Überspringen* only while photos are
  fetched, **below** *Weiter*, so *Weiter* never moves under a finger when the
  line goes. No error is shown (ONB-06).
- **Setup pages.** Welcome: placeholder ink wash (A1 later), leaf, „UmamiT",
  „Aus Liebe zum Tee", お茶で、いい時間を。, *Los geht's*. Reliability: the four
  rows of ONB-02 with their action while missing (*Erlauben*, *Freigeben*,
  *Einschalten*), the Samsung hint with *Gerätewartung öffnen* (Device care,
  else the settings; checked on the Galaxy, 09 §4), *Weiter* always enabled.
  The pages change with the tab fade-through (`tabSwitch`); back on the second
  page returns to the welcome.
- **Settings (SCR-25), M1 rows only.** The *Timer-Zuverlässigkeit* card first
  (all five checks, *Beheben* on a missing one, a summary line), then *Timer &
  Alarm*: Alarmton (Klangschale, Glocke, Holzblock, Glockenspiel, Sanft),
  Vorwarnung (Aus, 5 … 30 s), Vibration, Alarmdauer (Kurz einmal, Normal bis
  15 s, Lang bis zur Bestätigung, höchstens 2 Min), Bildschirm beim Brühen
  anlassen, Beim Ziehen abdunkeln, *Alarm testen*; then *Über*. Every row
  writes the settings at once. A failed write (disk full) never throws into
  the app, whose process may carry a running timer: it is logged and the
  screen says „Konnte nicht gespeichert werden"; on the setup *Weiter* then
  tries again. The tone picker stores a pick at once; *Anhören* plays that
  tone once (`TonePreview`: the alert's sound output and ringer rule, no tick,
  no vibration, no alert length) and stops when the picker closes, when
  another tone is picked, or when an alert removes the picker. *Alarm testen*
  (#44's `AlertTester`) plays tick and alert with the settings; *Stopp* next to
  it ends it, and so does leaving the screen. The other sections of SET-30
  come with their milestones.
- **Alerts first (rule 8).** No settings dialog is composed while an alert
  plays, and an alert removes an open one in its first frame without
  animation. Leaving the settings stops a running test (`stopTestAlert`); a
  real alert plays on (D-059). The banner on the timer stays hidden while it
  alerts.
- **Über (SCR-27).** Leaf, „UmamiT", the version, the Tee-Kontor source note,
  *Einrichtung erneut zeigen*, *Diagnose* (moved here from the settings
  placeholder of #43), and a short licence text (Cormorant Garamond OFL 1.1,
  Material Symbols Apache 2.0, the synthesised tones, the libraries).
  AboutLibraries (06 §4) comes with 1.0 (M5).
- **Accessibility.** The switch rows are `toggleable` (TalkBack says „an" or
  „aus"); a status row reads its label, explanation and state as one node.
  From font scale 1.5 a row's action moves below its text, so no word breaks.
- **Motion.** The only new motion is the page fade-through. Status rows, the
  banner and the photo line change without animation: they change on resume,
  not while the user looks. Dialogs are Material's.

<a id="d-065"></a>
### D-065 — Quick timer SCR-07 (M1-14, #49) · *Accepted* (Claude, 2026-10-23)

#49 built the quick timer (SCR-07, TMR-22, 04 §10) and its two entries, Start's
*Schnelltimer* and the tea picker's *Ohne Tee (Schnelltimer)*. Decided while
building it:

- **Two surfaces, one timer.** SCR-07 is its own route only before *Start*:
  the ring with the chosen duration, the chips and *Start*. Once the quick
  timer runs, the timer (SCR-05) replaces the route and shows it, because
  the notification, the active brew bar and the full-screen intent open the
  timer (D-058). Back from the running quick timer returns to Start. The
  ring sits at the same place on both, so the route's `fade` only crossfades
  the chips into the pill.
- ***Start* runs at once.** The start seam gains `startQuickTimer` and
  `finishActiveAndStartQuickTimer` (`BrewStarter`, D-062): the controller's
  `startQuickTimer` reduces `startQuickTimer` and `start` in one lock turn,
  so no other input lands between them and a cancelled caller never leaves
  a waiting quick timer behind. A second tap on *Start* while one is in
  flight is ignored. *Beenden & neu starten* into the quick timer is one
  turn too (`finishActiveAndStartQuickTimer`, through the controller's
  `startQuickTimer(finishActive = true)`, the quick-timer variant of D-063's
  operation): the active timer ends, the finish's sheet becomes
  „Sitzung gespeichert" (D-054), and the quick timer starts and runs. Opening SCR-07
  starts nothing, so it never blocks another session (TMR-21).
- **On the timer.** The quick timer has its title and no step label, dots or
  kettle hint; *Fertig* (`poured`) on the alert; the overflow holds
  *Abbrechen* (`cancel`, READY/RUNNING/PAUSED only, as 04 §10 allows) and
  *Timer-Einstellungen* (for the global pre-alert it uses). TalkBack says
  „Schnelltimer läuft".
- **Chips and picker.** SCR-07 opens with 1:00; the chosen duration survives
  process death (GEN-07), not app restarts. The selected chip has no check
  mark, so no chip widens under the finger; unselected chips have a border
  of ≥ 3:1 (WCAG 1.4.11). A tap on the ring opens „Dauer
  wählen": hours, minutes and seconds with − and +, each move kept within
  5 s – 24 h and rounded to PRF-04's increments (5 s below 10 min, whole
  minutes from 10 min on; the seconds do not move there). A move that would
  leave the bounds is disabled. *Übernehmen* applies, *Abbrechen* keeps the
  duration.
- **Second timer (TMR-21).** *Start* while a session is active asks
  „Erst die … -Sitzung beenden?" like the setup; while it alerts, the
  alerting timer opens instead.
- **Alerts first (rule 8).** SCR-07 is a route above the shell, so a new
  alert replaces it with the timer at once (D-062). During an alert,
  *Schnelltimer* and *Ohne Tee (Schnelltimer)* open the alerting timer
  instead of SCR-07, like a tea tap (D-062). The picker and the
  question remember the alert they were opened in and are removed when
  another one starts, without animation. The only motion of its own is
  Material's chip colour and the pill's press scale.
- **Kettle hint of the timer (BRW-10).** `TimerUiState.Active.iceBrew`
  carries an ice brew from the start, and from the session row after
  recovery, so a cold step's READY reads „Kaltes Wasser" or „Eiswürfel"
  like the setup, not „Wasserkocher auf 5 °C" (the follow-up of D-062).
  It goes through every recovery of D-063 (app start, reboot, update): the
  restored state takes it from the session row like the tea's name.

<a id="d-064"></a>
### D-064 — Notifications, the promoted Live Update and the full-screen alert on the real timer (M1-11, #47) · *Accepted* (Claude, 2026-10-22)

#47 replaced the interim notification content and `TimerAlertActivity` of #42
with the real ones ([04 §8](04-brew-timer.md#8-notifications); TMR-06,
TMR-14, TMR-18, TMR-41) and bound the notifier, the controller's last port.
Decided while building them:

- **One place shows notifications 1, 2 and 4.** `AndroidTimerNotifier` (the
  notifier port) hands `UpdateNotification` to `BrewTimerServicePort`, which
  also handles `StartForeground` and `StopForeground`. One lock orders the
  controller's commands and the 15 s refresh, and a command is sent while it is
  held, so the service receives them in that order.
  - While the service runs or starts, every change goes to the service, which
    shows it as its foreground notification. The ID changes only there, and the
    notifier never posts notification 2 next to the service's notification 1
    (D-044, D-057). The same state is not sent twice in one transition
    (`StartForeground` and `UpdateNotification` of the deadline), so the alert
    takes over the screen once.
  - Without the service the notification is posted directly, after the other
    two timer notifications are cancelled.
- **The quiet notification has its own ID 4.** A long running step („Fertig um
  14:30", TMR-18, a Live Update) and a demoted session (TMR-14, a regular
  ongoing notification without the promotion request, with *Start* or *Weiter*
  and *Sitzung beenden*) run without the service. When the service stops, the
  system removes its foreground notification asynchronously, which could remove
  a notification 1 posted right after; ID 4 avoids that race. ID 4 is never
  posted while alerting and is cancelled before every start of the service and
  every alert, so the alert stays the app's only notification of importance
  DEFAULT or higher (D-044). `StopForeground` removes 1, 2 and 4.
- **Content per phase** (04 §8.2): the title is „Gyokuro · Aufguss 2 von 3".
  The step word follows the step kind, as on SCR-05 (D-058). The quick timer is
  „Schnelltimer"; the alert text is „Gyokuro · Aufguss 2". The status-bar chip
  is the chronometer while running, otherwise the short critical text
  („60 °C", „Pause", „Fertig"). A long step keeps its step title (the state does
  not know the brew method, so not „Kaltaufguss").
- **Actions:**
  - *Start*, *Pause* or *Weiter* by phase;
  - *Abgegossen* (*Weggegossen* for a rinse, *Fertig* for the quick timer);
  - *Sitzung beenden* in FINISHED and on a demoted session;
  - *Abbrechen* for a waiting or running quick timer.

  *Sitzung beenden* is left out where it would discard the session: only the
  app may ask first (TMR-12 AC1). `TimerActionReceiver` (non-exported) sends
  each action to the controller with `EventSource.NOTIFICATION` through
  `TimerHost`. It then re-sends a refused service start within the tap's
  exemption, as the alarm receiver does.
- **ProgressStyle** (04 §8.3): one segment per step (at most 30) in tenths of a
  second. Each segment is at least a twentieth of the plan, and a rinse is
  drawn in a muted colour. The point marks the current step's pre-alert, and
  the progress is the session's elapsed share on that scale. Refreshed every
  15 s while running. The refresh runs only while the process lives and never
  after a newer command.
- **Dismissal** (04 §8.3): the delete intent records the dismissed state (session,
  step, phase, pre-alert, demoted). That state is not posted again by an update
  or the refresh; the next state change posts it. The alert ignores it. The
  record dies with the process.
- **Full-screen target: the app's timer route; `TimerAlertActivity` is removed.**
  - The content intent of every timer notification and the alert's full-screen
    intent open `MainActivity` with `com.umamit.action.OPEN_TIMER`, naming the
    app's launcher activity explicitly, so no intent filter is needed. A thin
    launcher activity was rejected: it would add a hop and a second activity
    over the lock screen.
  - `MainActivity` sets `showWhenLocked` (and `turnScreenOn` while alerting)
    at once, in `onCreate` or `onNewIntent`, while a session is active. Behind
    the keyguard with the screen off no frame is drawn, so the flags cannot
    wait for the navigation.
  - `UmamitRoot` pops whatever lies above the timer, or opens it. Until the
    timer is on top it draws the timer's dark background over the shell and
    reports the timer as visible, so the shell never shows over the lock screen
    (D-058).
  - A request the navigation has not handled yet is saved with the activity's
    state. A full-screen intent reaches a sleeping activity at once, and a
    configuration change when it becomes visible (dark mode, rotation, a fold)
    recreates it without delivering the intent again; the new activity
    restores the request, sets the flags and opens the timer.
- **„Sitzung gespeichert"** (ID 3, channel `sessions`, LOW, auto-cancel) names
  the session's tea as the notifier last showed it („Gyokuro · im Journal").
  It opens the app; the journal detail SCR-16 comes with M4.
- **The app runs the real timer.** With every port bound, `TimerAppModule`
  binds `ControllerBrewTimer` instead of `NoBrewTimer` (removed), and
  `UmamitApplication` is the `TimerHost` with the injected controller (`Lazy`,
  built on first use; D-056, D-057). `ControllerBrewTimer` is also the
  `BrewStarter` (D-062), so Start, *Nochmal brühen* and the setup start real
  brews. *Beenden & neu starten* still runs as two turns (`finishSession`,
  then `startSession`); the one-turn form of 04 §7.2 (D-054) is #46's.
  *Amended by D-063:* it is one turn since #46.
- **One place puts the timer on top** (`UmamitRoot.rememberTimerFirst`, with
  D-062): a new alert makes a route above the shell or the timer give way, and
  a notification's request brings the timer up. Both pop back to the timer when
  it is on the stack, else replace what lies above the shell. A tea tap on
  Start during an alert opens the timer instead of a setup, like a refused
  start (D-062 S3).
- **Tests.**
  - Robolectric:
    - content, actions, promotion, segments, intents and the delete intent
      per phase;
    - the notifier and the service port: through the service while it runs,
      directly without it, ID 4, the refresh and the dismissal;
    - D-044 through the real controller, service port, notifier and service:
      after `deadlineReached`, `addStep` while alerting, `recover` of an
      alerting state and a long step's deadline;
    - action round trips into the real controller.
  - Emulator (API 35/36):
    - the promoted Live Update (`FLAG_PROMOTED_ONGOING` on API 36, a normal
      ongoing notification on API 35);
    - *Pause* and *Weiter* from the notification;
    - the alert alone, not `FLAG_SILENT`, with the timer screen over the lock
      screen and *Abgegossen* from the notification (`core:timer`);
    - a cold brew's quiet notification (ID 4) giving way to the alert alone,
      without an autogroup summary (`core:timer`);
    - the app's own timer route over the lock screen through the app's
      controller, found by the ring's description and *Abgegossen* (`app`).
  - Not done (review of #47, open): a notification action can resend a stale
    refused start (needs refused exact alarms and no battery exemption;
    *amended by D-063:* closed, a refused start is resent from the current
    state); *Sitzung
    beenden* from the notification shows the „Gespeichert" sheet when the app
    is next opened (03 SCR-06 to decide); „Sitzung gespeichert" after recovery
    straight into the midnight timeout names no tea (the port would need the
    tea name).

<a id="d-063"></a>
### D-063 — Recovery after process death, reboot, app update and device restore (M1-09 part 3, #46) · *Accepted* (Claude, 2026-10-22)

#46 completed recovery ([04 §7.2–§7.5](04-brew-timer.md#72-recovery-on-process-start);
E2, E11; TMR-14, TMR-16, TMR-21) with the scope the #42 review handed
over (R2-1, R2-2, R2-3). Decided while building it:

- **Triggers.** `TimerInputs.recover(trigger)` is one controller turn. The
  first turn of a process recovers the persisted timer (D-057); in a process
  that already did, nothing changes. The triggers:
  - `APP_START`: the app's process start. The app calls it from
    `Application.onCreate`, off the main thread, once it hosts the controller
    (hand-off below).
  - `BOOT_COMPLETED` and `PACKAGE_REPLACED` come from `TimerRecoveryReceiver`.
    One receiver takes both broadcasts. It is not exported: the system sends
    both, and system broadcasts reach a receiver that is not exported (04 §13
    had an exported `BootReceiver`). `RECEIVE_BOOT_COMPLETED` is already in
    06 §5.7.
  - An app without a `TimerHost` runs no timer, so the receiver only logs to
    logcat. Unlike the alarm receiver, it never fails a debuggable build,
    because every boot reaches it.
  - A process that an alarm started may recover in the app start's turn. The
    alarm's log entry then takes its scheduled time from the restored state
    (06 §5.6).
- **Reboot notice (04 §7.3).** A session that survives a reboot (the boot
  count changed) gets „Dein Timer läuft weiter", with the tea below it,
  through the notifier port
  (`notifyTimerResumed`; its content comes with #47). While alerting there is
  no notice: the alert is the notice (D-044).
- **Device restore (E11).** Restore detection clears `active_brew` (D-052).
  Recovery then ends every `IN_PROGRESS` session other than the active one:
  - it is saved (`completeSession`, `auto_finished` = 1, as the app's own
    finish, 05 §3.6) when it has a completed infusion, i.e. a non-rinse step
    with recorded values that was not skipped;
  - otherwise it is discarded;
  - the same covers the session of a stored timer cleared as unreadable;
  - each one is logged (`OrphanSessionEnded`). A failure is logged and tried
    again at the next process start. `SessionRepository.inProgressSessionIds()`
    is new.
- ***Beenden & neu starten* (TMR-21, D-054).**
  `BrewSessionController.finishActiveAndStart(request)` is one turn:
  - it ends the active timer: *Sitzung beenden* for a brew session; for the
    quick timer *Abgegossen* while alerting, else *Abbrechen*;
  - then it starts the request;
  - the finish's `ShowSavedSheet` runs as `NotifySessionSaved`;
  - a start from elsewhere waits for the whole turn.

  `ControllerBrewStarter.finishActiveAndStart` (#48) calls it, in place of
  #48's two turns (D-062).
- **The recovery retries back off and stop (R2-1).** Each turn still tries
  the read three times (D-057). A turn whose reads all fail comes again after
  5 s during the first 30 s of failures, every 30 s up to 5 min, then every
  2 min; the alarm retry and the in-process wake-up use the same delay.
  - After 11 min of failed reads the process stops retrying, once
    (`RecoveryRetriesStopped`), so a database that stays broken does not
    wake the phone forever.
  - Only the retries stop (#46 review S1). The stored timer may well be
    valid: a cold brew can be hours from its deadline when the reads begin to
    fail. So its row, its alarms and its session stay. Its own alarms, a tap
    or the next process start read again, each with its three tries.
  - A snapshot that `restore` refuses is truly unreadable and is still
    cleared at once (D-057). Its alarms are cancelled, and any notification
    the old process posted without the service (a long step, a demoted
    session, a refused start) goes too, because it outlives that process.
  - Not done (#46 review O2): the 11 min count per process. A process that
    keeps dying between its retries starts again at 5 s; only the retry
    wake-ups continue, nothing is lost.
  - Every turn now re-arms the wake-up at its end, so a turn that only
    recovers retries too.
- **The refused start goes again from the current state (R2-2, amends
  D-057).** The controller sends it inside its turn:
  - at the end of a turn that may start the service from the background (an
    exact alarm, a notification action, a tap) and that sent the service no
    command itself; never after the in-process wake-up or the alert player's
    report;
  - with the notification of the state of that moment, which does not alert
    again (`FLAG_ONLY_ALERT_ONCE`). The alert keeps its full-screen intent
    while it plays;
  - a state that needs no service drops the refused start
    (`BrewTimerEngine.needsService`, new);
  - a start that is refused again is logged (`ServiceStartRefused`, *again*);
  - so the next interaction also restarts a service that recovery could not
    start (04 §7.2).

  The alarm receiver no longer resends on its own, and `ForegroundServiceState`
  only keeps whether a start was refused.
- **The alert watchdog for a kill during ALERTING (R2-3).**
  - *The gap:* any kill during ALERTING leaves only the overtime alarm
    (deadline + 10 min), however the deadline was handled. Its recovery then
    acknowledges the step silently.
  - *Decided:* while the alert plays, an exact `ELAPSED_REALTIME_WAKEUP` alarm
    (`TimerAlarmKind.ALERT_WATCHDOG`, request code 107) comes every 30 s. It
    ends with the alert: its cycle, *Abgegossen* or any other end.
  - In a new process, its turn recovers the state and replays the alert that
    had not run its cycle (at least once, D-047). In a live process it finds
    nothing due and is set again.
  - It is not an engine due time. That would need the alert's start in
    `active_brew`, a schema change right before the v1 freeze (D-054), and a
    due time must be consumed by an event that changes the state. The
    watchdog's only job is to start a process; recovery decides the rest.
  - `ScheduleAlarms` reconciles only the six due kinds
    (`TimerAlarmKind.DUE`); the watchdog has its own port call (`watchAlert`).
    A new process cancels a watchdog the old one left set when nothing plays.
  - Cost: at most four extra wake-ups per alert (Lang, 2 min), while the
    screen is on for the alert anyway.
- **Diagnostics (D-060).** New entries:
  - `RecoveryRequested`: the trigger, and whether the process had recovered
    already;
  - `Recovered`: the stored and the resulting phase, a reboot, the row of the
    table that applied (restored, alert replayed, overtime acknowledged,
    demoted, session ended), and whether the state runs with the service;
  - `OrphanSessionEnded`, `OrphanSessionsFailed` and `RecoveryRetriesStopped`;
  - *again* on `ServiceStartRefused`;
  - the watchdog alarm logs its scheduled time.
- **Tests.**
  - At the controller seam: the 04 §7.2 table (`RecoveryTableTest`), the
    retries and the backoff (`ProcessRestartTest`), *Beenden & neu starten*
    (`FinishAndRestartTest`), the refused start (`RefusedServiceStartTest`) and
    the watchdog (`AlertWatchdogTest`).
  - On Robolectric: the receiver, the alarm adapter and the service port.
  - In the CI emulator job, `ProcessDeathTest` also kills a step, reopens the
    app, checks the restored state and the alert within 2 s of the deadline.
    The runner's instrumentation lets Android allow every foreground start of
    the app's uid, so the emulator cannot show a refused start; those paths
    rest on Robolectric and the PO's device (#46 review O3).
- **Hand-off, done at the merge with #47 (D-064).** #47 bound the notifier,
  the last port, and made `UmamitApplication` the `TimerHost`. The merge
  adds:
  - `UmamitApplication.onCreate` calls `recover(APP_START)` off the main
    thread; an `:app` test fails without the call (the compiler cannot see
    it);
  - `AndroidTimerNotifier.notifyTimerResumed` posts the reboot notice on the
    `sessions` channel (LOW), so it never sits next to the alert (D-044);
  - the notification action no longer resends a refused start itself: the
    controller's turn does it from the current state (closes D-064's open
    point on a stale resend);
  - the device tests' resets cancel the watchdog alarm too.

<a id="d-062"></a>
### D-062 — Start, tea picker and brew setup (M1-13, #48) · *Accepted* (Claude, 2026-10-22)

#48 built Start (SCR-02), the tea picker (SCR-03) and the setup (SCR-04)
([03 §4](03-ux-ui.md#scr-02-start-home)). Decided while building them:

- **One start seam in `core:timer`.** Features depend only on `core:*`
  (06 §3), so Start (`feature:home`) and the setup (`feature:brew`) start a
  brew through `BrewStarter` (`state`, `startSession`,
  `finishActiveAndStart`); `ControllerBrewStarter` is the controller behind
  it, and `ControllerBrewTimer` implements both seams. The app binds
  `BrewStarter` to whatever `BrewTimer` is bound to, so it follows the
  controller when #47 binds it; until then `NoBrewTimer` ignores starts and
  the screen stays. *Beenden & neu starten* runs as two controller turns
  (finish, or cancel a quick timer, then start); #46 replaces it with the
  one-turn operation of D-054 behind the same seam (*done in D-063*:
  `BrewSessionController.finishActiveAndStart`). `alertKey()` moved to
  `core:timer` so every screen can give way to the alert.
- ***Nochmal brühen* (BRW-11).** The three most recently brewed distinct
  teas of the last 50 finished sessions; archived or deleted teas are left
  out. One tap starts the tea's last session again: its snapshot (recipe,
  vessel, leaf, water, method, base temperature), its step rows as planned,
  and its effective pre-alert frozen like a recipe value. A session finished
  early keeps only its started steps (TMR-12), so those are brewed again.
  The recent sessions come from `SessionRepository.observeRecentSessions`
  on the existing `observeFinished` query (no schema change).
- ***Zuletzt gebrüht*.** The last three sessions as `TeaListItem` rows:
  „21. Oktober · 1 Min · 65 °C" (the first brewed step's planned duration:
  whole hours „4 Std", whole minutes „1 Min", else „0:45"; its temperature).
  The rating comes with M4. *Alle anzeigen* opens the Journal tab.
- **Start layout.** Title „UmamiT", the placeholder landscape as the header
  (A1 later), *Aufguss starten* (or *Aufguss fortsetzen* with the tea and a
  countdown that ticks at each whole second while Start is visible), the
  *Schnelltimer* entry, then the three lists. With an empty collection the
  empty state replaces the header and the card; *Katalog öffnen* switches to
  *Sammlung › Katalog*. *Schnelltimer* and *Ohne Tee (Schnelltimer)* are wired
  to SCR-07 by #49. Photos are app-private files loaded with Coil
  (`core:ui`); without one, the leaf placeholder.
- **Tea picker.** Material 3's modal sheet, fully expanded, like the
  Schritte-Sheet (D-051), not the `sheet` token; under reduce motion it
  appears without the slide. A pick opens the setup in the tap's frame and
  closes the sheet; the setup's fade is the only transition (no sheet exit
  first, so no 600 ms of motion in a row), and no navigation can follow an
  alert that started after the tap. The search is GEN-10's: word prefixes of name and type, case- and
  diacritics-insensitive („kor" finds „Kōridashi"), debounced 150 ms, and
  forgotten when the sheet closes. *Aus dem Katalog* is hidden until M2.
- **Setup scaling (BRW-03).** The lock keeps the recipe's ratio; every
  rescale starts from that ratio, so steps never compound rounding errors
  and undoing a change by hand gives the recipe's values back. Re-locking
  takes the current values as the ratio. The steppers move the leaf by
  0,1 g and the water to the next multiple of 5 ml (the rounding units of
  05 §7). A vessel sets the water to its usable volume, else its capacity.
  Times and temperatures never scale. A tea without a recipe starts with
  5,0 g / 250 ml, one minute at 70 °C, and has no *Im Rezept speichern*.
- ***Im Rezept speichern* (BRW-02).** Shown only while vessel, water, leaf,
  steps or pre-alert differ from the selected recipe; it writes all of them
  (a step at the base temperature keeps none of its own) and the session then
  inherits the recipe's pre-alert. After the save its slot keeps its height
  and shows „Im Rezept gespeichert" (a polite live region), so *Brühen
  starten* never moves under a second tap (D-058). The Schritte-Sheet's own button writes
  only steps and pre-alert (SCR-31). The setup's values survive process
  death in the `SavedStateHandle` (GEN-07).
- **Kettle hint (BRW-10).** At ≤ 10 °C the setup reads „Kaltes Wasser", or
  „Eiswürfel" for an ice brew (`KettleWater`); the timer's READY does the
  same since #49 (D-065).
- ***Brühen anpassen*.** The timer's menu item opens the setup of the waiting
  session in place of the timer, filled with that session's values.
  *Brühen starten* there replaces the session without asking (no step has
  started, so finishing it discards it); the timer then replaces the setup.
- **Alerts first (rule 8).**
  - **Routes give way.** Every full-screen route above the shell or the timer
    (the setup, *Timer-Einstellungen*, *Diagnose* and any later one)
    remembers the alert it was opened in. When another alert starts, it gives
    way to the **timer** in that frame, without a transition: popped back to
    the timer when the timer is under it, else replaced by it (back then
    returns to the shell). The timer, not the shell's alerting bar, because
    it is the alert's full surface with *Abgegossen* (SCR-05), as the
    full-screen intent opens it (D-058). Unsaved setup values are dropped.
  - **Overlays.** The picker and the Schritte-Sheet are removed in the frame
    another alert starts (`AlertGate`, D-051), without animation.
  - **The second-session question** is never composed while an alert plays
    (gated in the UI, and closed by the view model). A start refused while
    the active step alerts opens the timer instead of asking.
  - **Motion.** Nothing on these screens is animated into or over an alert.
    The only own motion is *Im Rezept speichern* growing from its top edge
    (`standard`/`fade`, leaving with `exit`/`exitFade`), its crossfade to the
    confirmation, and the press scale of the cards and pills.
- **TalkBack.** The ratio lock is one switch node („Verhältnis halten,
  2,0 g / 100 ml", on or off). Each stepper row's label and value are one
  node that announces its new value.

<a id="d-061"></a>
### D-061 — Minimal journal and the „Gespeichert" sheet (M1-16, #51) · *Accepted* (Claude, 2026-10-22)

#51 built the read-only journal (SCR-15 minimal, JRN-01, JRN-08) and the
saved sheet (SCR-06, TMR-12). Decided while building them:

- **Read side.** `JournalRepository.observeSessions()` (core:data) emits every
  session, in progress or finished, newest start first, with its steps and
  its tea's cover photo file. One Room query with a join on `tea`; no schema
  change. It never writes: stock deduction and the opened date stay with
  `SessionRepository` (#36). The list follows the database, so a finished
  brew appears at once.
- **Day groups (E13).** A session belongs to the local date of its start in
  its own time zone, also when it ran past midnight, and shows its time of
  day in that zone. „Heute"/„Gestern" are relative to the phone's date now
  (re-read on resume and at midnight); older days read „Montag, 12. Oktober",
  with the year only outside the current year.
- **Meta line.** „55 °C · 1:00 · 3 Aufgüsse" is the base temperature (or the
  first infusion's, without one), the *planned* time of the first infusion
  and the count of poured infusions: ended steps that are neither a rinse nor
  skipped. When fewer were poured than the plan's non-rinse steps, the count
  reads „2 von 3 Aufgüssen" (a skipped step counts as not poured). Rating and
  the no-notes dot of JRN-08 come with M4, since M1 has neither.
- **Pinned session.** Every `IN_PROGRESS` session sits under „Läuft gerade"
  above the days, with its count so far („1 von 3 Aufgüssen"); a tap opens
  the timer. Finished rows are not tappable until the session detail (M4).
- **Saved sheet port.** `SavedSheetRequests` (core:timer, singleton) is the
  controller's `SavedSheetPort`: it only stores the session id and the
  monotonic time of the request, so the call in the controller's lock never
  blocks. The shell shows the sheet over the bottom of the tab content (above
  the brew bar and the navigation), without a scrim, on the `surface` token
  of sheets (03 §3.1) with a hairline; *Sitzung beenden* closes the timer
  route, so it appears over Start or wherever the user returns. Its text:
  „Gespeichert im Journal" and „3 Aufgüsse · 2:20" (poured infusions and
  their actual steeping time).
- **Leaving.** After 10 s (TalkBack's recommended timeout if longer), on a
  swipe down past 48 dp or a fling, or with TalkBack's *Schließen* action. The
  10 s count from the request, also while no shell shows the sheet (the app in
  the background, the next brew's timer covering the shell): a sheet comes
  back only for the rest of its time and never once it has run out (review of
  #51). A finger on the sheet holds it from the press on, also when it rests
  without moving; the full 10 s start again on release. A newer session
  replaces the sheet.
- **Rule 8.** While an alert plays, the sheet is not composed at all, so no
  exit animation runs over it, and a sheet due then is dropped, never shown
  afterwards.
- **Motion.** In and out with the `sheet` spring (critically damped,
  interruptible; a short drag springs back with its velocity, a fling carries
  its velocity out, and an upward drag meets friction instead of a wall),
  fading in with `fade` and out with `exitFade`; under reduce motion the slide
  is instant and the fades are short.
- **TalkBack.** The meta line is spoken with the duration in words („55 °C, 1
  Minute, 3 Aufgüsse"), so „1:00" is never read as a time of day.
- **Plumbing.** The app injects `JournalSources` (journal, sessions, saved
  sheet requests, wall clock) and passes it to the shell, like the active
  brew source, so the shell tests run on the fakes of core:testing
  (`FakeJournalRepository`). The tab keeps its last read, so a returning tab
  keeps its list height and scroll position. `ScreenScaffold` gained a
  `verticalArrangement`; `TeaThumbnail` is public for rows that are not a
  `TeaListItem`.

<a id="d-060"></a>
### D-060 — Diagnostics log and the *Diagnose* screen (M1-20, #43) · *Accepted* (Claude, 2026-10-22)

#43 built the rolling diagnostics log of [06 §5.6](06-architecture.md#56-error-handling--logging)
and *Diagnose* (SCR-27; TMR-30). Decided while building it:

- **Storage** (`DiagnosticsLog`, `core:data`): plain text in
  `Context.noBackupFilesDir/diagnostics/`, which no backup or device transfer
  includes (05 §8; `BackupRulesTest` checks that no rule includes it). Two
  files of 100 KB each, so the log never exceeds 200 KB: when the newer file
  is full it replaces the older one, so the oldest entries go first, in
  blocks. One line per entry: the local time to the millisecond with the zone
  offset, a tab, the message; line breaks in a message become spaces.
  - `core:data`, not `core:timer`: the catalog refresh writes to the same log
    from M2, and `core:data` cannot depend on `core:timer`.
- **Never in the timer's way** (rule 8): `append` only queues the line and
  returns; one coroutine on the IO dispatcher writes the queue in order. An
  I/O or security error loses that entry only. A read is queued behind the
  writes before it. The timer's port (`LogTimerDiagnostics`) formats and
  queues; it catches anything a broken entry throws. The alert player logs
  after it has started the alert, the service through a guarded reporter.
- **Entries in technical English, not localised:** they are pasted into
  issues and read by the accuracy workflow (04 §12). Only plain values are
  written, never a model's `toString()`, so no note text can reach the log;
  tea names are not logged either (allowed, but not needed). The screen
  around the entries is German.
- **Scheduled vs actual** (TMR-30): a time-based event and its alarm log
  `PRE_ALERT scheduled 10:00:50.000 (mono 5050000) actual 10:00:50.080 (mono
  5050080) delta +80 ms`. The kind's own clock gives the scheduled time; the
  other clock's is derived from the delay. Every other timer entry ends with
  the monotonic time and the boot count.
- **What is logged now:** every event the controller handles (source,
  phases, outcome), each alarm received, persist and effect failures, stale
  alert ends, a stored timer that is unreadable or cannot be read (recovery),
  inexact alarms, the service's foreground starts, its end and refused
  starts, the ringer mode, call and Do Not Disturb state with the outputs and
  tone at each pre-alert, alert and *Alarm testen*, and the permission
  states (notifications, Live Updates, full-screen alert, exact alarms,
  battery) at every process start, read off the main thread.
  `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED` are logged by the controller when
  their receiver asks it to recover, with each recovery decision (D-063).
- ***Diagnose***: newest first, the time in monospace above the message (also
  monospace), *Kopieren* in the top bar. It copies a header line (app version
  and versionCode, Android version and API level, device maker and model)
  and the entries as shown, newest first. Android 13+ confirms the copy
  itself, so the app shows no message. An empty log shows an empty state and
  disables *Kopieren*. The settings placeholder has a *Diagnose* button until
  M1-15 moves it under *Über*.

<a id="d-059"></a>
### D-059 — Production alert player, four more tones, *Alarm testen* (M1-10, #44) · *Accepted* (Claude, 2026-10-22)

#44 turned the ringer-mode player of #16 into the controller's alert port
([04 §9](04-brew-timer.md#9-alerts)). Decided while building it:

- **Two classes, one player.** `AlertPlayback` (singleton) owns the one
  `RingerModeAlertPlayer` with the device outputs. `ControllerAlertPort` is
  the controller's `TimerAlertPort` on top of it, and `AlertPlayback` itself
  is the `AlertTester` (*Alarm testen*) for the settings screen. The tester
  does not depend on the controller, so #50 can inject it before every
  controller port is bound (D-056). Hilt binds both in `TimerModule`.
- **Calls never block.** Every port call only queues a command; the
  commands run in order on the main thread, which the player and its
  Android outputs need. So a stop can never overtake its start.
- **Settings per alert.** Tone (SET-09), vibration (SET-11) and alert length
  (SET-14) are read at the start of every alert, tick and test from a cache
  of the settings file. Right after the process starts, a command waits at
  most 300 ms for the first value. An unreadable or late file plays the
  defaults (Klangschale, Normal, vibration on): a late alert is worse than a
  default tone (rule 8). The stored `alertSound` key is the file name; an
  unknown key plays the Klangschale. The timer's own `AlertLength` was
  replaced by the `core:model` enum of the setting.
- **End report.** When an alert has run its full length (Kurz one cycle,
  Normal up to 15 s, Lang up to 2 min; also when it was visual only), the
  port calls `alertCompleted` with that `StepAlert` once, from its own
  coroutine and never inside the controller's turn. The controller is
  reached through a `Provider`, which breaks the dependency cycle. An alert
  stopped (*Abgegossen*) or replaced first is not reported. If the report
  never arrives (process death), recovery replays the alert (at least once,
  D-047). A command that throws is logged, and the next alert still plays.
- **Tick at most once.** The engine plays the pre-alert once per step; the
  player also ignores a pre-alert while an alert plays, so a tick can never
  cut an alert. A controller test delivers the pre-alert alarm repeatedly
  and hears one tick.
- ***Alarm testen*** plays the tick, then the alert 1.5 s later (0.8 s tick,
  0.7 s pause), with the current settings including the alert length, through
  the same player and matrix. It is ignored while a real alert or tick plays;
  a real alert or tick replaces a running test. `stopTestAlert` stops only a
  test, e.g. when the settings screen closes.
- **Four tones** from `scripts/sounds/generate.sh`, each with its own
  character (04 §9.2): Glocke (small bell struck twice, 660 Hz bell
  partials, 3.0 s), Holzblock (two wood blocks, „tok-tok … tok-tok", noise
  burst plus short modes, band-passed, 1.2 s), Glockenspiel (rising arpeggio
  C6–E6–G6–C7, 2.2 s), Sanft (two soft tones rising a fourth, 3.0 s). Like
  the Klangschale: mono, 48 kHz, Ogg Vorbis q6, -16 LUFS, true peak ≤ -1.5
  dBTP, 9–16 KB. The wood block's noise comes from ffmpeg's seeded
  `random()`, so the file is reproducible. The generator gained an optional
  filter chain per sound, which leaves the Klangschale's and the tick's
  commands unchanged: both stay byte-identical (D-045), and `--check`
  compares all six files. A tone shorter than 3 s still repeats every 3 s
  (the vibration pattern plus 1 s), so Normal is 5 × 3 s.
- **The PO listens** to the four tones before the M1 checklist (samples on
  #44) and may pick another default or replace a tone (Q-15 procedure). The
  German tone names for the picker come with the settings screen (#50).

<a id="d-058"></a>
### D-058 — Timer screen SCR-05 (M1-12, #45) · *Accepted* (Claude, 2026-10-22)

#45 built the timer screen in `feature:brew` on the controller's state
([03 SCR-05](03-ux-ui.md#scr-05-timer)). Decided while building it:

- **One seam to the controller.** `BrewTimer` (`state`, `send`) is all the
  screen, its view model and the active brew bar use; `ControllerBrewTimer`
  is the controller behind it. Until #42 binds the remaining ports the
  controller cannot be injected (D-056), so the app binds `NoBrewTimer`
  (always idle, events ignored): nothing runs, the bar stays hidden and the
  timer route closes at once. Switching is one `@Binds` line in
  `TimerAppModule`. `TimerUiState.Active` gains `preAlert` (the session's
  own option) for the Schritte-Sheet.
- **Step label.** „Aufguss 2 von 4" numbers the position in the whole plan;
  the word follows the step kind („Spülaufguss 1 von 4", „Aufschlagen 1 von
  1"). Dots: done and current filled, pending outlined, a rinse a small square.
- **READY** keeps the digits in the ring with the caption „Bereit"; *Start* is
  the cream pill below and the ring is tappable too. The kettle hint carries
  „Aufguss 2 · 0:20" as its second line. **ALERTING** shows 0:00 in the ring
  with „Jetzt abgießen!" and the overtime below it, without the pre-alert
  marker. **PAUSED** dims only the ring, the liquor and the marker; the digits
  and the caption keep full contrast (≥ 4.5:1, tested). The dim never reaches
  the alert: PAUSED → ALERTING (the step resumed from the Live Update while the
  app was stopped) draws the ring at full strength in its first frame (tested).
- **The pill never moves under the finger.** The kettle hint and the overtime
  share one slot, sized by invisible copies of its tallest contents (a two-line
  kettle hint, the large overtime), so it holds under non-linear font scaling;
  the photo's space stays on the alert surface (the photo is not drawn there),
  and the reliability banner sits below the controls (hidden while the alert
  plays). Tested at *Start*, at the alert and at 15 s overtime, at font scale
  1.0 and 2.0.
- **First frame of a new state.** The screen reads the clock in the first
  composition of every new state, so *Start* or *Weiter* after a long wait
  never shows the stale time for a frame. The pre-alert glow is keyed by the
  step, so *Weiter* after the pre-alert does not replay it (D-046).
- **Overlays survive a configuration change** (the menu, the question and the
  sheet; GEN-07).
- **Finishing without a completed step** asks with the same „Sitzung
  verwerfen? Nichts wird gespeichert." (*Verwerfen* / *Weiter brühen*), because
  that is what it does; *Verwerfen* sends `finishSession`, which discards
  (TMR-12 AC1).
- **Alerts first for every overlay and route (motion reviews of #45).** Each
  alert has an identity (`alertKey`: session, step and deadline). A menu, a
  question or *Timer-Einstellungen* remembers the alert it was opened in, and
  only that alert may play under it: whatever was opened before an alert, or
  during an earlier one, is removed when a new alert starts. The menu is then
  not composed at all, so Material's exit animation cannot show over the alert.
  The settings route is popped back to the timer for every new alert, checked
  on each new alert and on resume, so an alert that started while the app was
  in the background is caught too. No route transition runs into, out of or
  onto an alerting timer (`EnterTransition.None`/`ExitTransition.None`);
  Navigation settles that swap within about five frames. The timer's body has
  one movable call site, so it keeps its state when the surface changes.
- **No *Im Rezept speichern* on SCR-05.** The screen calls only the
  controller, so the sheet opens without a recipe (`hasRecipe = false`); the
  recipe save stays on the setup (SCR-04). Steps without their own temperature
  show the plan's first temperature.
- **Dimming (TMR-17, SET-25).** The window brightness drops to 0.2 after 10 s
  untouched while RUNNING, but never brighter than the phone's own brightness
  setting (`min(0.2, system level)`; on the on-device checklist). The
  pre-alert brightens it until the step ends; the alert and the pre-alert are
  bright in their first frame. A new phase or step, a press, and closing the
  menu, a question or the sheet count as a touch, and it never dims while one
  of them is open. The rule is the pure `ScreenDimming`, tested with a moved
  clock; the screen re-checks only when the result can change.
- **Photo and liquor.** The tea photo sits at the top and fades into the
  background (mockup v2). The screen takes it as a parameter; since #54 the
  route passes the session tea's cover photo, or the placeholder, for every
  brew ([D-069](#d-069)).
  The liquor fill is the green liquor until the tea's style is in the state.
- **TalkBack.** One live region announces the state („Aufguss 2 von 3
  läuft", „Pausiert" …), the pre-alert („Gleich fertig"), „Noch 10 Sekunden"
  (only for steps longer than 10 s) and the alert (assertive). A 10 s pre-alert
  and the 10 s mark are one announcement („Gleich fertig, noch 10 Sekunden").
- **Lock screen.** Only the timer shows over the lock screen: `MainActivity`
  sets `showWhenLocked` while the timer is the current route with an active
  session, and `turnScreenOn` while it alerts there (`lockScreenFlags`). The
  route is read from the back stack, so the flags change when navigation
  starts, not after its fade. #47's full-screen intent opens the timer first,
  so an alert under the keyguard never shows the shell.
- **Navigation.** The timer is a top-level route above the shell; a tap on the
  active brew bar opens it, back keeps the session. *Timer-Einstellungen*
  opens the settings on the timer's stack; *Brühen anpassen* closes the timer
  until the setup exists (M1-13).
- **Active brew bar.** Fed by the controller's state; it ticks at each second
  of the countdown or the overtime while collected (not in the background),
  and a long step's progress moves only every 10 s (D-055 S9, tested).

<a id="d-057"></a>
### D-057 — Exact alarms, BrewTimerService and recovery in the first turn (M1-09 part 2, #42) · *Accepted* (Claude, 2026-10-22)

#42 added the Android adapters of the alarm and service ports, the alarm
receiver and the foreground service ([04 §4](04-brew-timer.md#4-components),
§6, §7.2; E3, E4; TMR-15, TMR-16, TMR-18). Decided while building them:

- **Alarm adapter** (`AndroidTimerAlarms`). Every `ScheduleAlarms` reconciles
  all six `TimerAlarmKind`s:
  - a kind with a time is set with its request code (101–106) and intent, so
    it replaces the earlier one; a kind without a time is cancelled;
  - pre-alert, deadline and overtime timeout:
    `setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP)`; idle demote and the
    quick-timer cancel: the same on `RTC_WAKEUP`; midnight: `set(RTC)`;
  - if exact alarms are refused, the alarm is set with `setAndAllowWhileIdle`
    and logged (`AlarmNotExact`);
  - the broadcast's action names the kind, so `dumpsys alarm` shows it.
- **Recovery in the first turn moves from #46 to #42** (amends D-056). E3
  needs it: in a process that an alarm started after a kill, the controller
  is idle.
  - The first turn of a process, whatever its input, reads `active_brew`,
    restores it and sends `recover` inside the event lock, once, before its
    own input (`EventSource.RECOVERY`). So a start in a new process is still
    refused while the persisted session is active (TMR-21).
  - A snapshot that `restore` refuses is cleared, its alarms are cancelled,
    and it is logged (`SnapshotUnreadable`).
  - A failed read is logged (`RecoveryFailed`) and tried twice more in the
    same turn (after 250 ms and 1 s; an alarm's turn stays awake). If every
    try fails, the alarm of the turn is set again 5 s out
    (`TimerAlarmPort.retry`, which leaves the other kinds alone) and the
    in-process wake-up comes back in 5 s, so the step's only alarm can never
    be used up by a passing database error.
  - The recovered session's tea name comes from its session row.
  - An alarm that started the process is logged with its scheduled time in
    the recovered state, so the log still shows scheduled vs actual (06 §5.6).
  - #46 keeps the rest: recovery at app start, `BOOT_COMPLETED` and
    `MY_PACKAGE_REPLACED`, the device-restore `IN_PROGRESS` row,
    *Beenden & neu starten*, and the tests of the whole 04 §7.2 table.
- **Receiver** (`TimerAlarmReceiver`, non-exported). It hands each alarm to
  `onAlarm(kind)` in one turn, kept alive with `goAsync`. The `StartForeground`
  of recovery or of the transition starts the service, within the exemption
  of the exact alarm.
  - An extra "ensure the service" after every alarm was rejected. The deadline
    alarm usually arrives just after the in-process wake-up handled the
    deadline; showing the alert again then would drop its full-screen intent.
  - Instead, the start Android refused last (kept until a newer command or a
    stop replaces it) is sent once more after the alarm's turn, with the same
    notification, full-screen intent included, and `FLAG_ONLY_ALERT_ONCE`. This
    covers the in-process wake-up winning the deadline race without the
    battery-optimisation exemption. *Amended by D-063:* the controller sends it
    inside its turn, with the notification of the state of that moment, also
    after a notification action or a tap.
- **The receiver and the full-screen alert find the controller through the
  app** (`TimerHost` with `TimerInputs`: `onAlarm` and `send`), not through
  Hilt, for two reasons:
  - the controller cannot be injected until every port has its adapter
    (D-056);
  - the app of the instrumented test cannot be a Hilt root: `@HiltAndroidApp`
    must sit in an application module.
  The ticket that binds the last port makes `UmamitApplication` the host with
  the injected controller. Until then the receiver drops an alarm; there is
  none, since nothing can start the controller. A debuggable build that gets
  an alarm without a host fails at once; a release build logs an error.
- **Service port** (`BrewTimerServicePort`) **and `BrewTimerService`**
  (`specialUse`, no timer logic):
  - Each command carries the notification of the state, its ID (2 while
    alerting, else 1) and the wake-lock time. The service shows it as its
    foreground notification and holds the partial wake lock: running, the
    remaining time + the longest alert (2 min) + 30 s; alerting, 2 min + 30 s;
    waiting states release it.
  - `startForeground(state, fullScreen)` now takes the full-screen flag, like
    `UpdateNotification`. So the alert that the service posts first at the
    deadline has the full-screen intent, and a later re-show has none.
  - Not yet in the foreground: `startForegroundService`. A
    `ForegroundServiceStartNotAllowedException` is logged
    (`ServiceStartRefused`) and the notification is posted without the
    service; an alert cancels notification 1 first.
  - Already in the foreground: the command goes with `startService`, which a
    running foreground service allows from the background, so the in-process
    wake-up can switch it to the alert. The same ID only updates the
    notification; a new ID takes `startForeground`. If that is refused, the
    service leaves the foreground (which removes notification 1), posts the
    alert alone (D-044) and logs it.
  - A stop never reaches the service before a pending start has called
    `startForeground`, which would crash the app; the service stops itself
    after the last pending command instead, with `stopSelfResult(startId)`,
    so Android ignores the stop when a newer start is already queued. The
    port and the service share this in a process-wide state, like the service
    itself.
  - The service creates the notification channels in `onCreate`, and the
    port before posting without the service: a foreground notification on a
    missing channel crashes the app (fresh install, cleared data).
  - A command takes a 10 s partial wake lock that the service releases once
    it holds its own, so the CPU stays awake between the alarm's broadcast
    and the service.
- **Notification content** (`TimerStateNotifications`) is minimal until #47.
  - Live Update: the tea or „Schnelltimer", the phase, the countdown chip
    while running, one ProgressStyle segment for the step with the pre-alert
    point.
  - Alert: „Jetzt abgießen!" on `steep_finished`, `CATEGORY_ALARM`.
  - Channels and IDs as verified (D-043, D-044).
- **Full-screen target until SCR-05 (#47; replaced by the timer route, D-064):** `TimerAlertActivity`. It shows
  over the lock screen and turns the screen on, set from code only while it is
  shown (04 §13). *Abgegossen* sends `poured` to the controller and closes it;
  a later alert updates its tea (`onNewIntent`).
- **For #47:** while the service runs, notification 1 or 2 is its foreground
  notification.
  - When the ID changes, the notifier changes it through the service, not
    with `notify`: the system ignores `cancel` of a foreground notification.
  - It never posts notification 2 itself while notification 1 is the
    foreground notification (D-044).
- **Probe:** it keeps its own scheduler, receiver and service until it is
  removed (#54), so the probe screen keeps working. The real classes reuse its
  verified patterns, channels and notification IDs.
- **Instrumented test** (`core:timer` androidTest, CI emulator job, API 35/36):
  - The main process of the test APK runs the real controller, alarm adapter,
    receiver, service, alert player (#44) and Room database, wired by hand in
    the test app. The ports of later tickets are logcat doubles; each ticket
    swaps in its adapter.
  - The test runner runs in its own process (`android:targetProcesses`).
  - A 25 s step without a pre-alert starts from an activity in the main
    process, like a tap. The test kills that process with SIGKILL from the same
    uid, as the low-memory killer does. `am kill` would not kill it: it spares
    processes with a foreground service and instrumented ones.
  - The deadline alarm starts a new process. The test asserts: the full-screen
    alert activity is resumed over the lock screen; the alert notification is
    the foreground notification, not `FLAG_SILENT`, and names the recovered
    tea; notification 1 is gone.
  - It starts from a clean state (process, alarms, database), fails fast when
    the start is refused, and checks that at least 5 s of the step are left
    at the kill.
- **Open for #46:** a process killed during ALERTING has only the overtime
  alarm left, however its deadline was handled (the alarm or the in-process
  wake-up; 04 §7.2). *Decided in D-063:* an alert watchdog alarm while the
  alert plays, not an engine due time.

<a id="d-056"></a>
### D-056 — BrewSessionController core (M1-09 part 1, #41) · *Accepted* (Claude, 2026-10-22)

#41 built the app-scoped controller in `core:timer` on the unchanged engine
([04 §4](04-brew-timer.md#4-components), §7.1). Decided while building it:

- **Inputs are suspend functions, outputs are ports.** Inputs:
  `startSession(BrewStart)`, `startQuickTimer(seconds)`, `send(event, source)`,
  `onAlarm(kind)` and `alertCompleted(alert)`. `send` takes only user events;
  the starts, `recover`, `AlertCompleted` and the time-based events are
  refused with `IllegalArgumentException`. Outputs are seven ports: alarms
  (`reconcile(DueTimes)`), service, notifier (also notification 3), alert
  player, saved sheet, diagnostics, and the in-process wake-up. A port never
  calls back into the controller synchronously. An adapter that reports back
  calls a suspend input instead (the alert player calls `alertCompleted`), so
  there are no callbacks and no dependency cycles.
- **Alarm kinds.** `TimerAlarmKind` holds the 04 §6.2 table: request code,
  clock, exactness, wake-up and event. The alarm port gets the full due set
  and reconciles it per kind (#42). `onAlarm(kind)` sends every event due at
  that moment (`dueEvents`), not only the kind's own. It logs the alarm with
  its scheduled time.
- **The in-process wake-up is a port.** The controller re-arms it after every
  handled event, accepted or not, for the earliest due time of a state of the
  current boot. Elapsed fields count on the elapsed clock and wall fields on
  the wall clock. In production, `CoroutineTimerWakeUp` detaches a fired
  wake-up before its callback runs. So the controller can re-arm from inside
  that turn without cancelling it. A port was chosen because Room's
  compatibility-mode `withTransaction` cannot run on a virtual-time
  dispatcher: the controller tests drive the wake-up with the fake clock
  instead.
- **A started turn runs to its end.** Once a turn holds the event lock, it is
  non-cancellable. Before this, a caller cancelled mid-turn (a screen closing
  right after *Abgegossen*) rolled back the transaction and skipped
  `StopAlert`, so the alert kept playing (rule 8). A caller cancelled while
  it waits for the lock still drops its event; the state is then unchanged.
- **Failures never block the alert (04 §4, D-054).**
  - A failed transaction (data effects + `Persist`) is logged (`PersistFailed`,
    no retry) and `Persist` alone is retried once; a failed retry is logged
    too.
  - The retried store write still records the step, because the store's
    write is authoritative (D-052).
  - Each side effect runs on its own: a port that throws is logged
    (`EffectFailed`) and the next effect still runs.
  - A diagnostics port that throws only logs to logcat.
  - An ignored event does not retry a failed persist; the next accepted
    transition persists again (04 §7.1).
- **One alert per deadline.** `StepAlert(kind, stepIndex, deadlineWallMs)`
  names an alert. `StartAlert` is skipped while the same alert plays;
  `StopAlert` and the player's end report clear it. An end report for any
  other alert is ignored and logged (`StaleAlertCompletion`). Otherwise it
  could mark a later step's alert as played, and recovery would not replay
  it. `UpdateNotification` is full-screen exactly when `StartAlert` is in the
  same transition.
- **UI state.** `TimerUiState` is `Idle` or `Active`. `Active` holds the kind,
  the tea name, the phase, the step, the plan with each step's status, the
  deadline on both clocks (a long step's end follows the wall clock), the
  paused remaining time, the pre-alert lead, due time and played flag, the
  long step, the kettle temperature, `demoted` and `finishWouldDiscard`. The
  screens compute the remaining time and the overtime with
  `display(nowElapsedMs)`, which rounds as in 04 §5; a test checks it against
  the engine. The state is published after the persist and before the side
  effects.
- **Pre-alert at the start.** The recipe's value, or else the global setting
  (an unreadable settings file counts as the defaults), is frozen as the
  inherited value. The Schritte-Sheet's choice from the setup
  (`BrewStart.preAlert`) becomes the session override.
- **Hilt.** The controller is a `@Singleton` that takes `TimerClocks` and
  `TimerPorts`. `TimerModule` binds `AndroidMonotonicClock`
  (`elapsedRealtime` and `BOOT_COUNT`, read once per process) and
  `CoroutineTimerWakeUp`. The other ports stay unbound until their adapters
  land (#42, #43, #44, #47, M1-16). Until then nothing can inject the
  controller, so no build ships a silent stand-in.
- **Left to #46:** `recover` at process start, the restored `IN_PROGRESS`
  row and *Beenden & neu starten*. A turn can already send several events.
  The engine was not changed.

<a id="d-055"></a>
### D-055 — Spec #27 slice review — UI · *Accepted* (Claude, 2026-10-22)

The T4 review of the UI slice of spec #27 (#29, #33, #34, #38) found no
blocking issue and eleven should-fix items. Decided and done:

- **Alert-safe children (S1, rule 8).** `AlertSurface` provides
  `LocalOnAlertSurface`; under it `TimerRingDefaults.colors()` returns the
  alert colours (ink, no liquor) and `PillButtonDefaults.colors()` an ink pill
  with washi text. A host that forgets them no longer makes the digits (1.08:1)
  or the cream pill (1.17:1) vanish in the washi phase. Contrast at both ends of
  the pulse: text 13.5:1 (washi) and 4.55:1 (matcha); the pill 13.5:1 / 4.55:1
  against the surface and 13.5:1 for its label. A test computes them from the
  colours the components resolve, in both themes, and a render test checks ink
  at the washi phase.
- **Overtime on the alert surface (S2a).** „+2:30 überzogen" is ink; the warm
  accent `#D9A35F` is only a non-text mark (a small dot), because as text it is
  1.97:1 on washi and 1.51:1 on matcha (03 §3.1, SCR-05, TMR-06).
- **No Schritte-Sheet in ALERTING (S2b).** *Schritte bearbeiten* and *Weiterer
  Aufguss* are not offered in ALERTING (D-051 keeps the sheet hidden there, and
  rule 8); they return after *Abgegossen* (03 SCR-05, SCR-31; 02 TMR-10, TMR-11).
- **Active brew bar in ALERTING (S3).** `ActiveBrew.alerting` and
  `overtimeSeconds`: the bar snaps to static matcha `#6B9845` with ink content
  (4.55:1), „Jetzt abgießen!" in place of the step and „+0:07" counting up; the
  mini ring is full, in ink, without liquor. It does not pulse: the full alert
  is SCR-05, which a tap opens. It never navigates by itself (03 §3.8, GEN-06).
- **Unsaved edits in the Schritte-Sheet (S4, GEN-08).** Swipe, fling, scrim,
  handle and back with unsaved edits keep the sheet and ask „Änderungen
  verwerfen?" (*Weiter bearbeiten* / *Verwerfen*, the new
  `DiscardChangesDialog`); without edits, or with edits undone by hand, the
  sheet closes at once. *Fertig* counts its edits as applied. *Verwerfen*
  resets the edits after the sheet has left. Back with edits is handled before
  Material's predictive close, so a veto never leaves the sheet half-shrunk.
  The alert path is unchanged: `alerting = true` removes the sheet and the
  question in the same frame, without asking. Amends D-051 „Closing applies
  nothing".
- **Scroll-to-top one-shot (S5, GEN-07, D-049).** A screen's handled count is
  no longer saveable: a screen composed again (rotation, return from a pushed
  screen, tab switch) adopts the current count, so a reset counter never
  swallows re-selects and a pop never replays a scroll. The shell remembers
  the back stack entry a re-select scrolled, so a screen pushed again later
  scrolls first instead of popping.
- **Bar text contrast (S6).** The bar's step line uses `onSurface`
  (10.8:1 light, 10.1:1 dark) instead of `onSurfaceVariant` (4.21:1 light on
  `surfaceContainerHigh`). A unit test checks every text/background token pair
  the components use in both schemes.
- **Reduce motion on the device (S7).** Reduce motion is animator scale 0, and
  Compose then ends every scaled animation on its first frame: on the phone
  every transition is instant, the 100 ms fades included. Only the alert and
  glow rules depend on the flag there; the in-app `reduceMotion` path stays as
  it is (tests, previews). Corrects D-046 „reduce motion keeps short fades" and
  03 §3.6. The bar leaves with `exitFade` like the routes.
- **Drawing without per-frame allocations (S8).** Ring and mini ring build
  strokes, the glow's gradient and the marker's place once per size
  (`drawWithCache`); the liquor is an arc closed by its chord (no path, no
  clip), still linear in height.
- **Mini ring long-step mode (S9).** `ActiveBrew.longStep`: the mini ring glides
  like `TimerRing`, and `ActiveBrewSource.progress` must hold still between the
  coarse updates of a long step (M1-09 tests the bound source).
- **Rail insets (S10).** `ScreenScaffold` also takes the bottom safe-drawing
  inset; the bar layout has consumed it already, and in the rail layout the
  shell consumes it above the content while the brew bar applies it.
- **Tests (S11).** The reduce-motion long-step test uses a level below 1; the
  shell tests observe real scrolls in a short window, rotation, return without
  replay, a re-pushed screen and per-tab scroll positions; the shell has
  screenshots at font scale 2.0 and with the alerting bar.

Optional items: only `exitFade` in the 03 §3.6 table and the 03 date were taken;
the others stay open for the milestone's design pass.

<a id="d-054"></a>
### D-054 — Spec #27 slice review — engine and data · *Accepted* (Claude, 2026-10-22)

The T4 slice review of the timer engine (#28, #31, #32) and of the data layer
(#30, #35, #36, #37) for spec #27, with the orchestrator's decisions on it
([04 §3, §4, §5, §6.2, §7.1, §11, §12](04-brew-timer.md#4-components);
[05 §3.6, §8, §9](05-data-model.md#9-migrations); 02 TMR-14, TMR-23;
08 §2.5a). The UI part of the review is recorded separately.

**Engine**

- **An edit never consumes a due pre-alert (B1).** `addStep` and
  `editSteps` recomputed the running step's pre-alert from now, which erased
  one that had come due but whose `preAlertReached` was still queued (ms, up
  to 1 s, longer after a process restart), so its tick was lost. Now an
  unplayed, already due pre-alert stays due while the step still has one,
  and `preAlertReached` decides by the 1-s rule. Only *Aus* (or N that leaves
  the step without a pre-alert, E7) removes it. The seeded test checks "due
  before an edit ⇒ due after".
- **Stale readings (S1).** A reading older than the state produced states
  that `restore` refuses (a negative paused total, more remaining than
  planned), so the next process start would have cleared `active_brew` and
  lost the alert. The engine clamps: `pause` keeps 1 ms – planned, and a
  negative open pause counts as 0 in `resume` and in the recorded paused
  time. Clamping was chosen over rejecting: a rejected *Pause* or *Weiter*
  would drop a user action, a clamped one keeps it at most a few ms off. The
  controller rules are now in 04 §4: read the clocks inside the event lock,
  one reading per `reduce`; a failed transaction is logged, `Persist` is
  retried alone once, and the side effects (`StartAlert`, `StopAlert`,
  `PlayPreAlert`) run anyway. The seeded test feeds readings older than the
  last accepted one.
- **`restore` refuses what the engine never produces (O2):** a demoted
  Running or Alerting state, a pre-alert on a long step, a played pre-alert
  that is still due. Tests that built such states by hand now drive the
  reducer (O7).
- **Notification per state (S2, D-044).** `StartForeground` and
  `UpdateNotification` use the new state's notification: the alert (ID 2) is
  the foreground notification while alerting and ID 1 is never posted then;
  ID 1 in every other active state (04 §4, `TimerEffect` KDoc).
- **`CompleteSession(sessionId, autoFinished)` (S3, data S5).** True only for
  the midnight timeout; `SessionRepository.completeSession` takes it without
  a default, so `brew_session.auto_finished` is set (05 §3.6).
- **Alarm kinds (S4).** 04 §6.2 has one row per `DueTimes` field: pre-alert,
  deadline and overtime timeout exact `ELAPSED_REALTIME_WAKEUP`; idle demote
  and the quick-timer cancel exact `RTC_WAKEUP`; midnight inexact `RTC`
  without wake-up; fixed request codes 101–106 (101 and 102 as in
  `ExactAlarmScheduler`). `BrewTimerEngine.dueEvents(state,
  now)` maps the due set to events in apply order, for the wake-up and the
  receivers alike.
- **Beenden & neu starten (S5, TMR-21).** No new engine event: the
  controller sends the ending event (`finishSession` for a brew session,
  `cancel` or `poured` for the quick timer) and then the start in one
  event-lock turn, and executes the finish's `ShowSavedSheet(id)` as
  `NotifySessionSaved(id)` („Sitzung gespeichert", TMR-14). The TMR-21
  dialog is the confirmation, so a session without a completed non-rinse
  step is discarded without a second question; it is not auto-finished.
- **Seeded service oracle (S6).** The invariant test now checks the D-053
  core rule against an oracle written from the spec (every active state
  needs the service except a demoted session and a running long step):
  entering such a state ensures it before the notification, leaving it for
  another active state stops it, recovery ensures it exactly when needed. It
  also checks the pre-alert tick (once per step, ≥ 1 s before the deadline,
  never on a long step) and that no due event is ignored after an ignored
  event too.
- **Smaller points.** A tick that plays ensures the service first (O3), so
  after a refused restart the exact pre-alert alarm can start it.
  `preAlertLeadSeconds(state)` gives the ring and the progress point the
  current step's N without re-implementing the skip rules (O6). The overtime
  auto-ack takes only states with a deadline (`TimerState.WithDeadline`), so
  `reduce` has no `error(...)` branch left (O1). Spec drift fixed: E2 (no
  alert after 10 min of overtime at recovery), E6 (skipped inside the window),
  TMR-14 idle time, TMR-23 session override, the §3 diagram and §3.1 table,
  §6.2 reconciliation.
- **Not done:** O4 (validating `PlannedStep` and `editSteps` input ranges),
  O5 (an engine signal for the reboot notice) and the optional
  `notificationSlot(state)`; the controller and the UI validate their input
  in M1-09/M1-11.

**Data**

- **Seed photo download waits for the seed teas (S1).** Before a seed tea
  exists it sends nothing, leaves its once-only marker unset and returns the
  current progress, so a call that races the seeder does not use up the run.
  A settings or database read failure no longer escapes as an exception
  (O3 of the data review).
- **Schema v1 freezes with the first rc (S2).** From `v0.1.0-rc.1` on, every
  schema change bumps the version and ships a tested migration (05 §9); this
  replaces D-052's "changed in place until the first release". CI runs
  `scripts/check-frozen-schemas.sh`: every schema JSON a `v*` tag contains
  must stay byte-identical (the probe tags contain none). *D-070 compares the
  version and identity hash instead and also runs it in the release
  workflow.*
- **No new tables before the freeze (S3, declined).** `tag`, `tea_tag`,
  `custom_descriptor`, `session_descriptor`, `tea_fts` and `session_fts`
  arrive with M3 and M4 through tested migrations, as 05 §9 plans. Adding
  unused tables now would freeze their design before the features that need
  them are specified in detail, and M3 needs the migration machinery anyway.
- **Data effects are total (S4).** Inside the controller's transition
  transaction, a `RecordStep` without its row changes nothing (logged; the
  `Persist` re-syncs the rows), an unreadable settings file counts as the
  defaults for *Bestand abziehen*, and a session zone this device does not
  know counts as the device's zone. A throw there would roll back `Persist`
  and leave a zombie `active_brew`.
- **Restore sweeps photo rows (S6).** In the restore branch of the open
  callback, `photo` rows whose file is missing are deleted and dangling
  covers cleared, in the same transaction as the `active_brew` clear, so
  CAT-15 can refill the seed covers in M2. Rows whose file exists stay (a
  false-positive restore loses nothing).
- **Exact session cost (O1).** Leaf × Σ price ÷ Σ grams in `BigDecimal`,
  rounded half up once: 25 g Hisui is 448 ct, not 447.
- **Tests (S7):** re-saving a tea, vessel, recipe or photo keeps everything
  that hangs off it (fails with REPLACE); half-up costs through
  `completeSession`; download before seeding; the real DataStore read inside
  the transaction.
- **One HTTP client (#37 O6).** Detekt `ForbiddenImport` forbids
  `okhttp3.OkHttpClient`, `okhttp3.Request`, `java.net.URL`,
  `URLConnection`, `HttpURLConnection` and `HttpsURLConnection` outside
  `core/network`, so GEN-01 AC1 is structural.

<a id="d-053"></a>
### D-053 — Timer time rules and the foreground-service contract (M1-08 part 2, #31) · *Accepted* (Claude, 2026-10-22)

#31 added the time-based rules to the pure engine, with S5 of the #28 review
and the notes of the #32 review (O6, O7, the consume rule of D-048)
([04 §3.2](04-brew-timer.md#32-events-and-transitions), §4, §5, §6.1, §7.2,
§8.2, §10; [05 §3.12](05-data-model.md#312-activebrew)):

- **Due set.** `DueTimes` has six fields: pre-alert, deadline and overtime
  timeout on the elapsed clock; idle demote, midnight and the quick-timer
  cancel on the wall clock. Each time-based event (`preAlertReached`,
  `deadlineReached`, `overtimeTimeout`, `idleDemote`, `midnightTimeout`,
  `quickTimerTimeout`) is accepted only when due, and a due one is always
  accepted. The seeded invariant test checks this after every accepted
  event: each due event is accepted by the new state.
- **Pre-alert.** N = session override ?: inherited (recipe ?: global). A
  step has none when N = 0, it is shorter than N + 5 s, it is a rinse
  shorter than 15 s, or it is a long step. `start` schedules it at
  deadline − N. `resume` and `editSteps` recompute an unplayed one from the
  deadline and the current N, and skip it when that time is not after now
  (exactly N left counts as past). A played one never comes back (E6).
  `preAlertReached` plays the tick while **≥ 1 s** is left before the
  deadline (the tick is 0.8 s, D-045), and later only marks it played. Both
  clear the due time and update the notification.
- **Overtime timeout.** It is due at deadline + 10 min in Alerting and uses
  the shared transition: actual = planned, overtime 0, ended at the
  deadline's wall time, auto-ack; a quick timer ends. A live
  `deadlineReached` that late applies the timeout instead of alerting, and
  `poured`, `endStepNow` or `finishSession` that late record the same
  auto-ack (O6), so live and recovered sessions agree.
- **Idle time.** A waiting state is idle since its last interaction or since
  it started waiting (`readySince`, `pausedSince`), whichever is later. The
  overtime timeout is not an interaction. A cold brew started at 18:00 that
  times out at 01:10 would otherwise be demoted, and finished at midnight,
  the moment it starts waiting. The wall clock stays the measure (D-048).
- **Idle demote.** For brew sessions only; the quick timer's 30-min cancel
  comes at the same time. It is due 30 min after the idle start while the
  session is not demoted. It sets `demoted` and emits `StopForeground` and
  `UpdateNotification` (the regular ongoing notification). Any user event
  clears `demoted`.
- **Foreground-service contract (S5).** Every active state runs with the
  service except a demoted session and a long running step.
  - `start` and `resume` emit `StartForeground` for a short step and
    `StopForeground` for a long one.
  - `deadlineReached` ensures the service before `StartAlert`.
  - `idleDemote` and every way to Idle emit `StopForeground`.
    `StopForeground` removes notifications 1 and 2 also when they were
    posted without the service, so Idle never emits `UpdateNotification`.
  - Any other accepted transition from a state without the service to one
    with it adds `StartForeground`: a user event on a demoted session, a
    pause or early end of a long step.
  - `recover` restores the service only where the state needs it (O7).
- **Midnight timeout.** For brew sessions in Ready, Paused or Finished,
  demoted or not. It is due at the first start of a local day in the
  session's zone at or after idle start + 6 h. The 6 h are real hours, and
  `LocalDate.atStartOfDay` handles a day whose midnight a DST change skips.
  It reuses the finish rules without confirmation. If a non-rinse step was
  completed (a paused step counts), the session is completed with the new
  side effect `NotifySessionSaved` (notification 3, „Sitzung gespeichert")
  and no saved sheet. Otherwise it is discarded silently. A paused step ends
  where its pause began; the open pause is not recorded (#28 review,
  decision 9).
- **Recovery.** A waiting session applies the timeouts that came while the
  process was dead, in this order: quick-timer cancel, midnight, idle
  demote. A demoted session and a long running step are restored without
  `StartForeground`.
- **Long steps.** A step is long when it is planned for ≥ 10 min, or when
  the session is a cold or ice brew (`StartSession.longStepsOnly`, from
  `BrewTimerEngine.longStepsOnly(method)`). The flag is not an `active_brew`
  column: `TimerSnapshot.longStepsOnly` is filled from
  `brew_session.method`. It is false for the quick timer, and `restore`
  refuses a quick timer that has it. `isLongStep(state)` and
  `longStepEndWallMs(state, now)` (now + the remaining time on the
  monotonic clock, so it follows a wall-clock change, E1) feed
  „Fertig um 14:30".

<a id="d-052"></a>
### D-052 — Session lifecycle, active brew store, device backup and restore detection (M1-06, #36) · *Accepted* (Claude, 2026-10-22)

[05 §3.6, §3.7, §3.12, §8, §9](05-data-model.md#37-sessionstep):

- **API follows the engine's effects.** `SessionRepository` has one operation
  per data effect (`startSession`, `replacePendingSteps`, `recordStep`,
  `completeSession`, `discardSession`) and takes the engine's `PlannedStep`,
  `StepRecord` and `TimerSnapshot`, so `core:data` depends on
  `core:timer-engine` (pure). `ActiveBrewStore.persist(snapshot?)` is
  `Persist` (null clears). `DatabaseTransactions` lets the controller run a
  transition's data effects and `Persist` in one Room transaction; the
  repositories' own transactions join it.
- **The store's write is authoritative.** Persisting a snapshot also brings
  the session's step rows to its plan (rows keep their id, note and
  `created_at`; missing rows are inserted, extra positions deleted). A
  persisted state restores equal even if a data effect was missed.
  Temperatures are snapped to 5 °C at every write (GEN-12), so the round trip
  is lossless for every state whose temperatures are multiples of 5, which
  the recipes and the Schritte-Sheet guarantee.
- **Schema v1 changed in place** (unreleased; frozen from `v0.1.0-rc.1`,
  D-054): `active_brew` gains
  `pre_alert_override_seconds`, `pre_alert_inherited_seconds` and `zone_id`
  and loses `active_elapsed_ms` (D-048); `(session_id, position)` in
  `session_step` becomes unique.
- **Completion details.**
  - `ended_at` is the end of the last ended step (the completion time if no
    step has one), so a late *Sitzung beenden* or a recovery does not stretch
    the session.
  - Cost = leaf × cost per gram, rounded half up, null without a tea, leaf
    amount or priced stock.
  - The BREW event is dated `ended_at`, needs a tea and a leaf amount > 0, and
    is skipped with *Bestand abziehen* off.
  - INV-06 is implemented in full: the opened date is set when it is empty,
    and also when the latest INITIAL/PURCHASE is on a later local day than
    `opened_date` and the stock before it was ≤ 0.001 g. The session's local
    date (of `started_at`, E13) is used.
  - A session that is no longer `IN_PROGRESS` is not completed again, so a
    repeated `CompleteSession` adds no second deduction.
- **Device backup: includes only.** The rules include exactly `umamit.db`,
  `-wal`, `-shm` and `datastore/` instead of "the whole database domain
  except `catalog.db*`" (05 §8 v0.2). With includes, everything else is out
  by construction, including databases that libraries put there (LeakCanary's
  `leaks.db` in debug builds). `fullBackupContent` carries the same set, so
  no backup path falls back to "everything".
- **Restore detection in Room's callbacks.** `onCreate` writes the marker;
  `onOpen` without the marker clears `active_brew`, then writes the marker.
  Room runs `onOpen` before serving any query, so nothing reads the restored
  row. A marker that cannot be written (disk full) is logged and retried on
  the next start, which then clears `active_brew` once more; failing the
  database open would stop the whole app. A restored `IN_PROGRESS` session
  row is left for M1-09 to finish or discard.
- **CI schema check.** After the quality gate, CI fails on
  `git diff --exit-code core/database/schemas` and on an uncommitted schema
  file, since `ExportedSchemaTest` compares with the freshly generated copy.
- **Fakes.** `FakeSessions` (session repository, store, transactions) shares
  rows like the database; it has no rollback and implements only the
  "empty opened date" part of INV-06.

<a id="d-051"></a>
### D-051 — Schritte-Sheet details (M1-19) · *Accepted* (Claude, 2026-10-22)

#38 settled these points of SCR-31 that the specs left open
([03 SCR-31](03-ux-ui.md#scr-31-schritte-sheet-session-steps)):

- **Material 3's `ModalBottomSheet`, with its own motion.** The sheet runs
  in its own window, so TalkBack and keyboard focus stay inside it, and
  Material brings predictive back, drag and fling to dismiss with the
  gesture's velocity, nested scrolling of the step list and clean reopening.
  It always opens fully expanded (`skipPartiallyExpanded`). Its show and hide
  motion is Material's (calm, no overshoot), not the `sheet` token of
  03 §3.6: Material 3 1.4 keeps the sheet's motion scheme internal. A first
  version with an own sheet drawn in the host window applied the token, but
  the motion and accessibility review of #38 blocked it (no focus trap, a
  fling lost its velocity, a reopen during the exit stayed displaced, no
  predictive back). Accessibility and reliability come before matching the
  token; the PO judges the motion from the GIF in #39. Under reduce motion
  the sheet opens already expanded and closes without the slide.
- **Alerts first (rule 8).** The sheet never covers the alert. When the timer
  enters ALERTING, the host sets `alerting = true` and `visible = false`: the
  sheet's window is removed at once, without the close animation, and the
  unapplied edits are dropped. While `alerting` is true the sheet stays
  hidden.
- **Closing applies nothing.** Only *Fertig* sends `editSteps`; a swipe,
  back, the scrim or the handle close the sheet without changing the session.
  With unsaved edits they first ask „Änderungen verwerfen?" (GEN-08,
  amended by [D-055](#d-055)).
  *Im Rezept speichern* saves the recipe and keeps the sheet open, so the
  session still changes only with *Fertig*.
- **Stepper rules.** A tap moves to the next multiple of 5 s below 10 min and
  of 1 min from 10 min on (lengthen: 9:55 → 10:00 → 11:00; shorten: 11:00 →
  10:00 → 9:55), within 5 s – 24 h. Temperatures move to the next multiple of
  5 °C within 5–100 °C (05 §3.2). A step without its own temperature shows the
  recipe's base temperature and stays without one until it is changed; a step
  without any (quick timer) has no temperature stepper.
- **Delete** is disabled when it would leave no non-rinse step in the whole
  plan, done steps included (D-048). Done (✓), running („läuft") and skipped
  („übersprungen") steps are read-only.
- **Recipe save.** The whole plan as shown (done steps included) replaces the
  recipe's steps (step notes arrive in M4). *Wie Einstellung* keeps the
  recipe's own pre-alert, *Aus* stores 0, a lead time stores its seconds.
  The confirmation snackbar „Im Rezept gespeichert" shows on the tap, above
  the buttons, once (a reopened sheet does not replay it); the host does the
  write.
- **Step kinds in German:** Aufguss (INFUSION), Spülaufguss (RINSE),
  Aufschlagen (WHISK).

<a id="d-050"></a>
### D-050 — Shop client and seed photo download (M1-07, #37 and its T4 review) · *Accepted* (Claude, 2026-10-22)

**Shop client (`core:network`, rule 7):**
- One `ShopClient` singleton for all three network uses. Allow-list = scheme,
  host and port of `https://www.tee-kontor-kiel.de`, checked before any
  connection and again on the network; redirects are never followed; the
  base URL can be changed only by a `@VisibleForTesting` factory.
- **Direct connection** (`Proxy.NO_PROXY`): a system, Wi-Fi or PAC proxy is
  not used, so the shop is the real TCP destination of every request.
- **One connection, one request per call:** one dispatcher thread,
  `maxRequests = maxRequestsPerHost = 1`, a pool of one connection, no fast
  fallback (no parallel connects), no connection retries. OkHttp repeats a
  503 with `Retry-After: 0` by itself, so the client hides `Retry-After` from
  OkHttp (it is kept under an internal header for the stop rule) and refuses
  any second network attempt of a call before it is written.
- **≤ 2 requests/s on the wire:** the limiter runs as a network interceptor,
  so requests are written ≥ 500 ms apart however long the connection setup
  takes.
- **Stop signals** (08 §2.8): `cf-mitigated: challenge`; a 401/403/503 with
  challenge markers (incl. `challenges.cloudflare.com`, `cf-turnstile`) or
  served by Cloudflare (`CF-RAY`, `Server: cloudflare`, a block page); HTTP
  429; any `Retry-After`. Bodies over 5 MB are refused.
- **robots.txt:** RFC 9309 with a backtracking-free wildcard matcher (runs
  of `*` collapsed), only the first 512 KiB read, a leading BOM ignored, a
  `User-agent` product token matched without its version. The parser lives
  in `core:network` from M1 on (06 §3 had it in M2). `Crawl-delay` is not
  yet honoured (M2).

**Seed photo download (`core:data`, ONB-06):**
- Runs once: the marker `seedPhotosAttempted` (05 §6) is set **before the
  first request**, so a skip, a failure or a crash never repeats it. It is in
  DataStore and so in the device backup: a restored phone doesn't download
  again (CAT-15 fills in photos).
- robots.txt first. 404/410 → everything allowed; 401/403 or 5xx → stop;
  any other failure (3xx, other 4xx) → everything disallowed.
- Progress: `Idle`, `Running(current, total)` (photo being fetched, robots.txt
  counts as photo 1), `Done(stored)`, `Skipped` (*Überspringen* or the
  caller's cancellation), `Failed(cause)`.
- `Failed` = the run stopped: a stop signal, a 401/403/5xx, a network error
  (offline), or a storage error (disk full, database). Nothing is shown and
  nothing escapes as an exception. Photos stored before stay.
- Per photo, without stopping: a robots-disallowed path, a 404 or other 4xx,
  or an answer that is no complete JPEG is left out (placeholder), and so is
  a seed tea that is gone or already has a cover.
- Images are decoded and **re-encoded** (05 §3.9, 06 §5.2): sources larger
  than 8192 px per side or 40 MP and truncated images (no EOI) are refused;
  the stored JPEG is at most 2048 px, quality 85, without metadata.

<a id="d-049"></a>
### D-049 — Navigation shell details (M1-05, #34) · *Accepted* (Claude, 2026-10-22)

- One navigation controller per tab; Settings and the probe sit on the stack
  of the tab they were opened from.
- Re-selecting a tab (03 §2.2): the first tap scrolls the screen on top to the
  top; a second tap pops a deeper stack to the tab root. On a root screen every
  tap scrolls. The scroll is a one-shot event, never replayed on return
  (how, after the slice review: [D-055](#d-055)).
- Tab switches use a fade-through (`tabSwitch`, 03 §3.6); back from another
  tab returns to Start with a predictive-back preview.
- The active brew bar applies the system bar insets itself in the rail layout.

<a id="d-048"></a>
### D-048 — Step edits, quick timer, recovery and the `active_brew` snapshot · *Accepted* (Claude, 2026-10-22)

#32 settled these engine points, from the T4 review of #28 (S6, S7) and the
spec gaps it left ([04 §3.2](04-brew-timer.md#32-events-and-transitions),
§5, §7, §10; [05 §3.12](05-data-model.md#312-activebrew)):

- **`addStep` default.** The base is the last non-rinse step of the plan (the
  last step if all are rinses), so a trailing rinse never sets the next
  infusion. The new step keeps the base's kind (a whisk session adds a whisk;
  a rinse base gives an infusion) and temperature. The duration is
  1.5 × the base, rounded half up to 5 s when the unrounded value is below
  10 min and to whole minutes from 10 min on, then clamped to 5 s – 24 h. It
  is the public pure `defaultAddedStep(plan)`, which the Schritte-Sheet also
  uses.
- **`editSteps`.** The request carries the whole plan, so the sheet sends
  what it shows. The started or ended prefix must come back unchanged, else
  `Rejected(StartedStepChanged)`. At least one non-rinse step must remain in
  the whole plan, done steps included, else `Rejected(NoBrewStepLeft)`.
  Rejections keep the state and have no effects. A Ready whose step was
  removed re-points to the first pending step, or goes to Finished; Finished
  goes to Ready when steps are added. `addStep` and `editSteps` emit
  `ReplacePendingSteps(sessionId, fromIndex, steps)`, which replaces the
  unstarted rows from `fromIndex` on (05 §3.7). Both are user interactions.
- **Pre-alert values.** `PreAlertSettings(sessionOverrideSeconds,
  inheritedSeconds)`: `startSession` freezes recipe ?: global, and the
  effective value is session override ?: inherited. `editSteps` carries a
  `PreAlertChoice` (`Inherit` = *Wie Einstellung* clears the override, `Off`
  = 0, `Seconds(n)`). The quick timer inherits the global value.
- **Quick timer.** `startQuickTimer(duration, globalPreAlert)` starts it with
  `SessionKind.Quick`. `cancel` works in Ready, Running and Paused but not
  while alerting, where *Fertig* ends it. `quickTimerTimeout` is due 30 min
  after the last interaction in Ready or Paused, on the wall clock like the
  other waiting timeouts (`DueTimes.quickCancelAtWallMs`). `editSteps`,
  `addStep`, `endStepNow`, `skipStep`, `finishSession` and `discard` are
  ignored, and `finishWouldDiscard` is false. A start of either kind while
  one is active is `Rejected(ActiveTimerExists(kind))`, also before
  `recover`. A duration outside 5 s – 24 h or a pre-alert other than 0 or
  5–30 s is `Rejected(InvalidQuickTimer)`; `reduce` never throws.
- **Recovery in the engine.** The clock reading carries the boot count, and
  the session records the boot of its elapsed values. `recover` is an event
  sent on every process start:
  - after a reboot, every elapsed value is first moved to the new elapsed
    clock at the same distance from now as its wall value, clamped to
    physical bounds: a passed deadline and a pause start ≤ 0, a running
    deadline ≤ the planned time (every old-boot instant lies before
    elapsed 0). A state impossible on the same boot count (an alert deadline
    or a pause start after now, more than the planned time left) is treated
    as a reboot. This catches a wall clock set back across the reboot. A
    wall clock set forward cannot be detected: the step counts as longer
    off and past 10 min is auto-acknowledged silently, an accepted limit;
  - waiting states and a running step with its deadline ahead are restored;
  - a deadline passed less than 10 min ago alerts with overtime;
  - from 10 min on (`≥`, the same instant at which the #31 timeout event
    becomes due) the step is poured on time with `overtimeAutoAck`, ended at
    the deadline's wall time, silently; a quick timer just ends;
  - every row emits `StartForeground` and `UpdateNotification`, and the
    alert is replayed while `alertPlayed` is false;
  - an unplayed pre-alert stays due even if its time has passed, so a
    pre-alert alarm that restarts the process can still tick. #31 implements
    the rule: a due `preAlertReached` is never ignored. It plays the tick and
    sets `preAlertPlayed` while there is still time before the deadline;
    otherwise, and always at or after the deadline, it only sets
    `preAlertPlayed` (accepted, no `PlayPreAlert`). In general a time-based
    event that is due is applied or consumed, never `Ignored`, so the
    controller's wake-up cannot spin;
  - a quick timer whose 30-min cancel passed while the process was dead
    ends silently;
  - the midnight row comes with #31's midnight rule.
  Any other event whose reading comes from another boot is
  `Rejected(BootChanged)` until `recover` ran. The controller sends
  `recover` exactly once per process, first, inside the lock of the restore,
  and re-arms alarms only from a recovered state. `StartAlert` is
  idempotent ("ensure the alert of the alerting step plays"), so a deadline
  event before `recover`, or a second `recover`, gives one alert. The
  overtime-timeout transition is shared, so #31 only adds the event and its
  due time. For #31: a live `poured`/`endStepNow`/`finishSession` at
  ≥ deadline + 10 min should record the timeout's auto-ack like `recover`,
  and recovery must not restart the service for a `demoted` session or a
  long step.
- **Snapshot.** `ActiveBrewSnapshot` (`core:model`) mirrors 05 §3.12 and
  adds `pre_alert_override_seconds`, `pre_alert_inherited_seconds` and
  `zone_id`. It carries `alert_played`, `demoted` and `boot_count`.
  `SessionStepRow` mirrors the timer columns of `session_step`.
  `active_elapsed_ms` is dropped, because it is derived. A step's status is
  derived, not stored. In FINISHED, `step_index` is the number of steps. The
  quick timer has no step rows. `restore` refuses an inconsistent snapshot
  with `IllegalArgumentException` and nothing else: a missing or
  out-of-range value (planned ≤ 0, a pause with nothing or more than planned
  left, a negative paused total, a FINISHED index other than the step
  count), a field of another phase, a recorded field on an unrecorded row,
  or a plan that is not ended steps, the current step, then pending steps
  (a READY needs a pending step). The controller then clears `active_brew`.
  The Room entity follows in #36.
- **Idle time on the wall clock.** The quick-timer cancel, #31's idle demote
  and the midnight rule measure from `last_interaction_wall_ms` on the wall
  clock. It survives a reboot without a rebase, the midnight rule needs the
  wall clock anyway, and no extra `active_brew` column is needed. A manual
  wall-clock jump forward can therefore cancel or demote early; that is
  accepted, because the waiting timeouts are housekeeping, not timing.
- **`demoted`** is a field of the session, false until #31's idle rules set
  it.

<a id="d-047"></a>
### D-047 — Timer engine design (T4 review of #28) · *Accepted* (Claude, 2026-10-22)

The T4 design review of the pure `BrewTimerEngine` (#28) settled these points
before #31, #32 and M1-09 build on the engine
([04 §3.2](04-brew-timer.md#32-events-and-transitions), §4, §5, §5.1):

- **Due-time set.** A public, pure `dueTimes(state)` holds every alarm time of
  a state. Today that is the pre-alert, until it has played, and the deadline.
  #31 only adds fields: overtime timeout, idle demote, midnight and the
  quick-timer cancel.
  - Every accepted transition emits `ScheduleAlarms(dueTimes(newState))`. The
    controller reconciles it per alarm type, and `CancelAlarms` is gone.
  - Time-based events carry no payload and are accepted only when they are
    due. The controller re-arms its in-process wake-up after every `reduce`,
    and recovery uses the same function.
- **Delivery.**
  - The alert is at-least-once. `Alerting.alertPlayed` is set by the
    `AlertCompleted` event from the AlertPlayer, and recovery replays
    `StartAlert` while it is false.
  - The pre-alert is at-most-once: `preAlertPlayed` is set in the transition
    that plays it (#31).
- **Outcome.** `TimerTransition` reports `Accepted`, `Ignored` or
  `Rejected(reason)`. `RejectionReason.ActiveTimerExists` refuses a second
  start (GEN-03, TMR-21); #32 adds the `editSteps` refusals. A non-accepted
  event always keeps the state and has no effects.
- **Session rows.** `CreateSession` is a data effect of an accepted start, so a
  refused start leaves no orphaned `brew_session` or `session_step` rows.
- **Effect order.** Data effects, then one `Persist` (one Room transaction),
  then one `ScheduleAlarms`, then the side effects. `StartForeground` means
  "ensure it runs" and is also emitted before `StartAlert`. `StopForeground`
  removes the timer notifications.
- **Session identity.** `SessionKind` is `Brew(sessionId)` or `Quick`, so a
  brew session without an id cannot exist.
- **Recorded data.**
  - Durations are rounded half up; the display and the journal may differ by
    1 s.
  - A skipped step records zeros and null start/end times.
  - A Running step ended at or after the deadline, before `deadlineReached`
    was processed, counts as poured, with overtime.
- **Finish.**
  - A finish that falls back to discard emits only `DiscardSession`.
  - A started step counts as completed.
  - `finishWouldDiscard(state)` lets the UI confirm in advance (TMR-12 AC1).
- **Small API changes.**
  - `StepStatus.RUNNING` is renamed to `STARTED`.
  - The state, step and `StartSession` constructors have no defaults.
  - The display rounding uses plain arithmetic instead of `Math.ceilDiv`.

These review points move to other tickets: service and notification rules for
long steps, idle demote and recovery (S5 → #31); recovery and the boot count in
the engine (S6 → #32); pre-alert and zone columns in `active_brew` and
*Wie Einstellung* in `editSteps` (S7 → #32/#36).

<a id="d-046"></a>
### D-046 — Design system details from M1-04 · *Accepted* (Claude, 2026-10-22)

Settled while building the theme and the simple components (#29); the PO
reviews them with the screenshots and motion recordings.

- **Colour schemes:** generated once from `#4A5643` with material-color-utilities
  (tonal spot, spec 2021) and committed as constants (03 §3.1). Where 03 §3.1
  overrides a role, its twin follows: `onBackground` = `onSurface`,
  `surfaceTint` = `primary`. All other unlisted roles keep the generated value.
- **Fonts:** static instances of the Cormorant Garamond variable font; `lnum`
  and `tnum` survived subsetting, so the 03 §3.2 fallback is not used. Every
  serif style sets `lnum` (the font's default figures are old-style).
- **German text:** all text styles carry the German locale and automatic
  hyphenation, so long compounds break at syllables at font scale 2.0; ß in
  overlines is uppercased to ẞ.
- **Icons:** the Material Symbols (Apache 2.0) are the only third-party assets
  besides the font; their licence is committed next to the OFL.
- **Motion tokens:** 03 §3.6 „Motion tokens", with the reasons. Press feedback
  (scale 0.97, faster spring release) is added to pills and cards, not list
  rows; reduce motion keeps short fades (on the device they are instant:
  corrected by [D-055](#d-055)).
- **Alert motion (motion review):** entering ALERTING is always instant
  (`alertEnter`); the pulse starts at full matcha; the pulse and the pre-alert
  glow ignore developer animator scales (`UnscaledMotion`), while scale 0 still
  means reduce motion.
- **Timer components (#33):** the alert surface pulses between washi
  `#F3F0E9` and a lighter matcha `#6B9845` with ink `#1F271B` content (≥ 4.5:1
  on both ends; the type colour Matcha `#5F8C3E` would give only 3.9:1). The ring
  shows no liquor while alerting. The liquor fill is drawn at 32 % opacity, its
  level linear in height. Ring captions use `onSurface`, not `onSurfaceVariant`,
  because they sit on the liquor. The pre-alert glow is keyed on the tick's
  identity (`preAlertGlowTick`), not on a level: it plays once per tick, is never
  cut short and never replays when the ring comes back. Progress is a lambda read
  in the draw phase; a long step's update to 1 is drawn without a glide. The
  alert pulse restarts at full matcha when the app returns to the foreground.
  The active brew bar grows from its bottom edge, so the content above does not
  jump. Motion review findings for #33: M1 and S1–S5 applied, plus O3 and O5. The motion GIFs come from Roborazzi's
  experimental `recordRoboVideo` (record mode only), shrunk with ffmpeg.
- **PO check (#39, 2026-10-22):** the pre-alert glow, the alert pulse
  (matcha ↔ washi, 1.5 s, ink content) and the light-green
  `PrimaryActionCard` in the dark theme are approved unchanged.

<a id="d-045"></a>
### D-045 — Default alert sound and pre-alert tick (Q-15) · *Accepted* (PO, 2026-10-22)

After the probe (`v0.0.2-probe`), the PO found the draft Klangschale (440 Hz)
not bright enough and wanted the pre-alert tick to sound more like a singing
bowl. From a listening round of three variants each, the PO chose **C + C**:

- **Klangschale** „hell und klar": 880 Hz fundamental with the bowl partials,
  3.5 s, -16 LUFS. It stays the default alert (04 §9.2).
- **Tick** „warm lang": a softly struck small bowl at 990 Hz, about **0.8 s**
  (no longer a 90 ms wooden tick), -24 LUFS, 8 LU below the alert.

Both come from `scripts/sounds/generate.sh` and are byte-identical to the files
the PO heard. The other tones (Glocke, Holzblock, Glockenspiel, Sanft) follow
with SET-31 in M1.

<a id="d-044"></a>
### D-044 — Alert replaces the Live Update while ALERTING; promotion always requested · *Accepted* (Claude, 2026-10-22)

Root-caused at T4 from the CI emulator (API 36) and the AOSP Android 16
sources (details and source references: `docs/plans/M1-android16-api-notes.md`):

- **Full-screen alert:** Android 16 force-groups an app's ungrouped
  notifications once two share an importance section and marks them
  `FLAG_SILENT`; System UI then suppresses the full-screen intent and the
  heads-up. The Live Update (DEFAULT) and the alert (HIGH) were grouped, so
  the alert never turned the screen on (also on a real phone). **While
  ALERTING, the alert becomes the foreground notification and the Live Update
  is removed** (04 §8.1, §8.2). Android 15 groups too but has no silencing.
- **Promotion:** the probe requested promotion only while
  `canPostPromotedNotifications()` was true. The request is now **always**
  set on Android 16, so the system promotes as soon as the user allows it
  (04 §8.3). The promotability rules differ between Android 16 builds (flag
  `android.app.ui_rich_ongoing`): before QPR1 a per-app preference that is
  off by default and needs a colorized notification; from QPR1 the
  `POST_PROMOTED_NOTIFICATIONS` permission plus appop (allowed by default)
  and a non-colorized notification. The probe posts whichever variant the
  platform accepts. `POST_PROMOTED_NOTIFICATIONS` must stay declared
  (amends D-043).
- **Fix loop (D-040):** `v0.0.1-probe` was published before this fix and is
  superseded by `v0.0.2-probe`; the PO tests only the latter.

<a id="d-043"></a>
### D-043 — Android 16 Live Update API names corrected · *Accepted* (Claude, 2026-10-22)

Verified against the compileSdk 36 `android.jar` and AndroidX core 1.18 during
M1-03a (#14, #15); 04 §8.3, §13, 02 ONB-02, 03 and 06 §5.7 corrected:

- the settings page for Live Updates is
  `Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS` (with
  `EXTRA_APP_PACKAGE`); `ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS` does not
  exist;
- promotion is requested with AndroidX
  `NotificationCompat.Builder.setRequestPromotedOngoing(true)`; the platform
  builder has no such method;
- `android.permission.POST_PROMOTED_NOTIFICATIONS` is not a public SDK 36
  permission. It stays declared (*amended by D-044:* required from Android 16
  QPR1, see there); what counts is
  `NotificationManager.canPostPromotedNotifications()`, which the user
  controls.

<a id="d-042"></a>
### D-042 — Build tooling choices from M1-01/M1-02 · *Accepted* (Claude, 2026-10-22)

Technical decisions made while building the skeleton and the CI gate
(M1-01, M1-02); no PO-visible behaviour.

- **Detekt 2.0 alpha:** Detekt `2.0.0-alpha.6` (plugin `dev.detekt`)
  instead of 1.23.x. Detekt 1.23 embeds Kotlin 2.0 and cannot load the
  Compose rules (0.5+ need the Detekt 2 API; 0.4.x crash on Detekt 1.23's
  Kotlin runtime). Detekt 2 is built with Kotlin 2.4 like the app. Move to
  the stable 2.0 release when it ships.
- **compileSdk 36 limits library versions:** many current AndroidX releases
  require compileSdk 37. compileSdk stays pinned at 36 (D-023), so the
  catalog uses the newest versions that build against 36 (comment in
  `gradle/libs.versions.toml`).
- **Gradle configuration cache off:** it replays dependency-resolution
  failures (HTTP 429 from Maven Central in cloud sessions) instead of
  retrying. Gradle's network retries are raised instead.
- **Lint exceptions:** `MissingApplicationIcon` until M1-21 and
  `DataExtractionRules` until M1-06 (TODOs in `config/lint/lint.xml`).
  Network- and date-dependent checks are off. No baseline.
- **GitHub Actions:** both workflows use the same major versions
  (`checkout@v7`, `setup-java@v6`, `setup-gradle@v6` with the open-source
  `basic` cache provider). *Since D-070 pinned to the commit SHAs of these
  versions.*

<a id="d-041"></a>
### D-041 — Motion guardrails · *Accepted* (PO, 2026-10-21)

Grilled with the PO; details in 03 §3.6:

- **Style:** calm, ritual-like motion.
- **Signature moments:** functional motion plus exactly two of them, the
  liquor fill and the pre-alert glow. The liquor fill replaces the
  „optional" fill of v0.2.
- **Long steps:** coarse updates.
- **Alert:** deliberately strong but non-flashing surface pulse.
- **Reduce motion:** the system setting only; under it the alert is static
  and high-contrast.
- **Review:** the PO judges motion on the device (release candidates) and
  in short motion recordings. These are GIFs made with Roborazzi's
  experimental, record-only `recordRoboVideo`, committed next to the
  screenshots in `docs/design/review/<milestone>/`.
- **Details:** curves, durations, the fill direction and the pulse rhythm
  are worked out in the waves 2–3 spec with `/animate` and `/apple-design`.
- **Skills:** the design skills are part of Claude's workflow (M1 plan
  §3.2); the PO does not need to invoke them.

<a id="d-040"></a>
### D-040 — The probe gates the real timer; fix loop until it passes · *Accepted* (PO, 2026-10-21)

Grilled with the PO before the M1 start:

- **Gate:** all four items of the probe checklist (09 §4) must pass on the
  PO's Galaxy before M1-09 and M1-11 are finished:
  1. the APK installs;
  2. the alert follows the ringer mode, with the full-screen alert over the
     lock screen;
  3. the status-bar chip and the lock-screen Live Update are shown;
  4. all status rows show ✓.
- **Now Bar:** stays best effort (04 §8.3). Its presence is recorded but
  never blocks.
- **Fix loop:** a failing item is fixed and re-tested with a new probe:
  `v0.0.2-probe` (versionCode 201), `v0.0.3-probe` (301) and so on, per the
  06 §6 rule. If the same item fails twice and the diagnosis points outside
  the app (system setting, One UI behaviour, platform bug), Claude brings it
  to the PO with the analysis and they decide together.
- **Fallbacks stay:** the runtime fallbacks of 04 §8 (normal ongoing
  notification, heads-up instead of full-screen) stay in the real timer
  (M1-11) even if the probe passes, because permissions can be revoked and
  One UI can change later.
- **Removed (#54):** the probe screen, its classes and its tests were removed
  in M1-18 (#54); the real timer's emulator tests (#42, #46, #47) and
  `PermissionStatesDeviceTest` cover what the probe tests checked.

<a id="d-039"></a>
### D-039 — `release.yml` on `main` before the first dispatch · *Accepted* (PO, 2026-10-21)

GitHub only dispatches a `workflow_dispatch` workflow that exists on the
default branch. So `release.yml` (M1-03) reaches `main` through a small
housekeeping PR before the probe release `v0.0.1-probe` is dispatched from
the M1 PR branch; release candidates are dispatched the same way. This is an
exception to „one PR per milestone" limited to the release workflow; later
changes to `release.yml` within M1 travel with the milestone PR.
*Extended by D-070:* the exception covers the dispatch-only workflows
`release.yml` and `accuracy.yml`. Only the file's existence on `main` is
required; a dispatch runs the version at its workflow ref, so both are
dispatched with the PR branch as the ref. Agreed with
the PO as part of the spec issue
[katznmeik/UmamiT#6](https://github.com/katznmeik/UmamiT/issues/6), which
also fixes the M1-00 … M1-03a test seams (M1 plan §3.1).

<a id="d-038"></a>
### D-038 — Go for development · *Accepted* (PO, 2026-10-21)

The PO gave the go for development, effective **2026-10-22 at 09:05
Europe/Berlin** (after the PO's weekly usage limit resets), unless the PO
says otherwise before then. Development starts with M1 per
[`docs/plans/M1-timer.md`](../plans/M1-timer.md).

<a id="d-037"></a>
### D-037 — Claude merges all pull requests · *Accepted* (PO, 2026-10-21)

The PO never merges. Claude merges documentation and housekeeping PRs after
checking the diff (and green CI, if any). Milestone PRs are merged by Claude
once the done criteria are met, including the PO's explicit approval of the
on-device checklist and the design review. Merge commits; the working branch
is restarted from `main` afterwards. Recorded as rule 9 in `CLAUDE.md`.

<a id="d-036"></a>
### D-036 — Spec review fixes and PO decisions · *Accepted* (PO, 2026-10-21)

Result of an Opus `xhigh` review of spec v0.2 plus the PO's answers.

**PO decisions**
- **Three network uses**, all only to the allow-listed host
  `www.tee-kontor-kiel.de`: (a) the user-triggered catalog refresh (CAT-07);
  (b) a **one-time download of the two seed teas' product photos** at first
  start (during the first-start setup, visible progress, skippable,
  placeholder fallback; ONB-06); (c) the on-demand exact variant price fetch
  when the user adds a package (CAT-12). Updates GEN-01, NFR-PRIV-02,
  06 §4/§5.7, 08 §2.5a.
- **Sounds:** synthesised first (ffmpeg in the cloud container): Klangschale
  (default), Glocke, Holzblock, Glockenspiel, Sanft and the pre-alert tick.
  The PO may replace any tone later with uploaded CC0 recordings (licence in
  `docs/design/assets/source/CREDITS.md`). Amends D-034.
- **Screen wake:** at the end of every step (short and long) the alert is a
  **full-screen intent** on `steep_finished`: the screen turns on and SCR-05
  in ALERTING is shown over the lock screen, also in silent mode (then
  visual only). Needs `USE_FULL_SCREEN_INTENT`; checked in ONB-02, SET-24
  and the reliability banner (TMR-06, 04 §8–9).
- **Backup simplified:** Android device backup (BAK-05) plus a simple manual
  export ZIP (BAK-01). In-app restore with validation (BAK-02) is `Won't`
  for 1.x (post-1.0 backlog).
- Tee-Info guides import (CAT-09) and wider layouts stay (cuts declined).

**Review fixes** (IDs from the review)
- R-02: session-only **Schritte-Sheet** (SCR-31) for SCR-04 *Bearb.*,
  TMR-10 and TMR-11; executable M1 checklist.
- R-03: the cloud container has no emulator; instrumented tests run in a
  **CI emulator job** on GitHub Actions (API 35 and 36).
- R-04: `release.yml` runs via `workflow_dispatch` and creates tag and
  release; release candidates `vX.Y.Z-rc.N` (pre-releases from the PR
  branch) and the final `vX.Y.Z` from `main`; versionCode scheme
  ([06 §6](06-architecture.md#6-build--distribution)).
- R-05: keystore hand-off as session file attachments; fallback commands
  for the PO ([06 §6.1](06-architecture.md#61-signing-process-d-035)).
- R-07: Live Update corrections; Now Bar is best effort; a **probe release**
  `v0.0.1-probe` is tested on the Galaxy before the full timer is built
  ([04 §8.3](04-brew-timer.md#83-live-update)).
- R-09: recovery tries to restart the FGS immediately; app-update receiver;
  midnight passed while killed → `midnightTimeout`
  ([04 §7](04-brew-timer.md#7-persistence--recovery)).
- R-10: minimal diagnostics log already in M1.
- R-11: Material 3 schemes generated from seed `#4A5643` with overrides;
  extended tokens; timer digit font features ([03 §3](03-ux-ui.md#3-visual-design-system)).
- R-17: CAT-14 (bundled snapshot) → `Won't`.
- R-18: CI also builds `assembleRelease` (R8).
- R-19: `planned_step_count`, session-step lifecycle, midnight rule
  (first local midnight ≥ 6 h after the last interaction), device restore
  clears `active_brew`, backup rules include all of `umamit.db*`.
- R-20: catalog hardening (Cloudflare detection, stop on HTTP 429, own
  `dataSync` foreground service instead of WorkManager).
- R-21: app icon and status-bar icon in M1; Samsung auto-restart hint.
- R-23: all module stubs before parallel work; PR template sections.
- R-12…R-16: consistency fixes (mockup notes, seed descriptions via CAT-15,
  worked examples, glossary, single source of M1 scope).

<a id="d-026"></a>
### D-026 — Subagent distribution: model and effort per task · *Accepted* (PO, 2026-10-21)

Development tasks are delegated to subagents by tier: T1 simple → Sonnet,
low effort; T2 standard → Opus, medium; T3 complex → Opus, high; T4 critical →
Opus, extra high (`xhigh`, the ceiling; `max` not used). Details, task
catalogue and escalation rules: [12 · Subagent Distribution Plan](12-agent-plan.md).

<a id="d-027"></a>
### D-027 — Application ID and display font · *Accepted* (PO, 2026-10-21)

Application ID `com.umamit.app`; serif display font Cormorant Garamond (OFL).

<a id="d-028"></a>
### D-028 — No smartwatch support · *Accepted* (PO, 2026-10-21)

No Galaxy Watch integration or testing; notification bridging may happen by
default but is not a requirement.

## Change log

| Date | Change |
|---|---|
| 2026-10-25 | D-083: finding teas (#69) — „Neu" only after a second successful refresh, CAT-10's filters as chips plus a filter sheet, prefix search over `catalog_fts` with the `unicode61` tokenizer and a count of hits the filters hide, „im Besitz" from `tea.catalog_key`, filters not stored (`catalogSort` dropped); 03 SCR-19 and 05 §4.1, §6 updated. |
| 2026-10-25 | D-081: catalog package sizes and prices (#67) — sizes from the configurator and the sitemap without fetching size pages, the page's own size at its price, larger sizes estimated, smaller ones priced when added, each with its own `variant_url`; split numbers merged by normalised name only when their own sizes differ; SCR-21 *Packungen*; parser v3; 03 SCR-21, 05 §4.2, 06 §2, §3 and 08 §2.4 updated. |
| 2026-10-25 | D-080: the timer service cancels the other notification again after `startForeground`, so a killed step's notification 1 never stays next to the alert. |
| 2026-10-25 | D-079: catalog refresh in the background (#65) — the job in the app's scope kept alive by the `dataSync` service, which follows it; own channel, IDs 10/11 and wake lock; progress at most every 2 s; `startForeground` first and `stopSelfResult`; refusal and time-limit handling; errors end the run, not the process; quiet result notification opening the app; entry points in the catalog menu, Settings (SET-23) and the setup's third page (ONB-03); 03, 04, 06 and 09 updated. |
| 2026-10-25 | D-078: catalog tea detail (#66) — full 08 §2.3 field map (parser v2: joined properties, region list, flavoured rule, brewing prose and grid JSON), sanitised description, photos after the sanity check with orphan cleanup and SET-22, *Im Shop öffnen* by grams then label, SCR-21 layout; 02 CAT-08, 03 SCR-21, 05 §4.1, 06 §5.3 and 08 §2.3, §2.8 updated. |
| 2026-10-25 | D-077: catalog refresh tracer bullet (#64) — staging tables with the live columns, skip list written only with a successful swap, sanity-check counting, parser rules for secondary name, *Sorte*, unit price and stock, product page choice, typed outcomes with details, app-scoped job until #65, catalog as a slot of Sammlung; 03, 05 and 08 updated. |
| 2026-10-25 | D-076: M2 implementation clarifications — first refresh ≈ 15–18 min (~1,560 products, PO), retries only for timeouts/connection errors, 08 §2.9 abort thresholds, FTS text and thumbnail columns, owned keys read before the swap, style table and cold-brew fallback, JPEG copies, page parsing notes; 02, 05, 06, 07, 08 updated. |
| 2026-10-25 | D-075: a failed first seed is retried at every app start once the setup is done, without the seed photo download; plus the #58 fixes of M2-11 (one-line nav labels at font 2.0, the picker sheet on `surface`, no-break space before units and „Std."/„Min.", separators from `strings.xml`). 05 §6, §7 and 08 §2.5a updated. |
| 2026-10-25 | D-074: shared shop session (`ShopSession`: robots.txt once per job with the RFC 9309 status rules, typed fetch results, sticky stop, serialised fetches, D-076 retries for the refresh only) and image targets for re-encoding (catalog photo ≤ 1200 px WebP 80, list thumbnail 256 px short side, covers unchanged) from #63; 02 CAT-08, 06 §4 and §5.2, 08 §2.8 updated. |
| 2026-10-25 | D-073: M2 grilled with the PO: CAT-12 add and repurchase in M2 with the picker flow, *Rezept passt*, guides last, small #58 items; catalog first loaded from the Katalog tab; refresh allowed during a brew (CAT-07 AC8); „Neu" badge; list grouped by Sorte, available only, „Pur" on; photos 1200 px plus thumbnails; live-shop tests by Claude; accuracy run only for rcs that touch timer code (09 §4a); early design check; A8/A11 now. |
| 2026-10-25 | D-072: one-tap reorder: „Beim Tee-Kontor nachbestellen" visible on SCR-09 (new INV-09; filled when the stock is low), opening the shop page of the size bought last; CAT-11 / SCR-21 follow the same rule; checks added to 09 §4 M2/M3. Requested by the PO. |
| 2026-10-23 | D-071: `BrewTimerService` posts a same-ID update made within 1 s of `startForeground` once more after 0.5 s (only-alert-once, only while still the foreground ID, newest wins), because AOSP posts the start's notification asynchronously and it can overwrite the update; service path logging, `dumpsys activity services` in the device test dump, wider CI logcat filter. 04 §6.3 and §8.3 updated. |
| 2026-10-23 | D-070: release gate and workflow hardening (#54 milestone review): `release.yml` builds only a commit with a successful `ci.yml` run and repeats the schema checks; signing job (read-only token, only GitHub's own actions, no setup-gradle) and publish job (`contents: write`, no actions, re-verifies the APK) split; all actions in `release.yml`, `ci.yml`, `accuracy.yml` pinned to commit SHAs; the schema freeze compares version and identity hash; `SchemaVersionsTest` opens every committed schema version with the production builder and `UMAMI_MIGRATIONS`; D-039 covers `accuracy.yml`; 09 §4 gains Doze, quick timer, *Beenden & neu starten* and refused-start items. 05 §9, 06 §6, 09 §1 and §4, `docs/release.md` updated. |
| 2026-10-23 | D-069: M1 review fixes (#54): the active brew bar snaps in when it arrives alerting (synchronous first value, no enter motion); system-bar icons per timer surface with the root as their one owner; honest *Sammlung*/*Statistik* placeholders with renders; the tea photo (or placeholder) on every brew timer, D-058 amended; double-tap guard on *Nochmal brühen*. 03 SCR-05, SCR-08, SCR-18, §1.1, §3.8 and 09 §4 updated. |
| 2026-10-23 | D-068: M1 review fixes in the timer area (#54): deadline guard (alarm 107 at deadline + 90 s while a step runs), accuracy gate and *Diagnose* measured up to the start of the sound (D-067 corrected), accuracy run reset with the watchdog, alert settings read again after a failed read, settings flow total on an unreadable file, timer route waits for recovery, cold and ice READY text and chip, full-screen check reads the app op, wording of `StopForeground` and D-064. 04 §4, §6.1, §6.2, §7.2, §8.2, §8.3, §11, §12, 06 §5.6, 09 §4, §4a updated. |
| 2026-10-23 | D-067: accuracy workflow (#52): `accuracy.yml` (`workflow_dispatch`, on `main` per D-039) runs `AccuracyRunTest` (skipped in the regular emulator job) with `am instrument`; 50 × 60 s steps with the screen off in sessions of ten, driven through the notification actions; delays from the diagnostics log's accepted pre-alert and deadline events (parser round-trip tested against the format); nearest-rank p95 ≤ 250 ms and max ≤ 1 s for both, every step required; injected-delay gate check in the test APK; summary in the job summary, CSV and log lines as an artifact. 04 §12, 09 §1 and §4a updated. |
| 2026-10-23 | D-066: first-start setup, timer settings, reliability card and banner (#50): `TimerReliability` in `core:timer` over the probe-proven permission states, *Beheben* to the check's own page, the setup as the root start destination until completed and above the shell when shown again, `FirstStart` seeding then downloading the seed photos in the background, the photo line below *Weiter*, the M1 settings rows with the German tone names and *Anhören* through `AlertTester`, no settings dialog over an alert, *Über* with *Diagnose*. 03 SCR-01, SCR-25, SCR-27 updated. |
| 2026-10-23 | D-065: quick timer SCR-07 (#49): chips 0:30–5:00 and „Dauer wählen" (5 s – 24 h, PRF-04 steps), *Start* as one controller turn, *Beenden & neu starten* into a quick timer as one turn, *Fertig* and *Abbrechen* (no session, step or stock record), every entry opens the alerting timer during an alert; `iceBrew` in the timer state for the cold and ice READY hint, carried through recovery. 03 SCR-07, SCR-05 updated. |
| 2026-10-22 | D-063: recovery after process death, reboot, app update and device restore (#46): recovery triggers through one non-exported receiver, reboot notice, device restore finishes or discards `IN_PROGRESS` rows, one-turn *Beenden & neu starten*, recovery retries back off and only stop (never clear a valid session), refused starts resent from the current state, alert watchdog alarm 107 while ALERTING. 04 §7.2, §11, §12 updated. |
| 2026-10-22 | D-064: notifications, the promoted Live Update and the full-screen alert (#47): `AndroidTimerNotifier` through `BrewTimerServicePort`, the one ordered place for notifications 1, 2 and the quiet ID 4 (long step, demoted session); content and actions per phase, ProgressStyle segments with the pre-alert point, 15 s refresh, dismissal kept until the next state; actions to the controller with source NOTIFICATION; the full-screen and content intents open the app's timer route over the lock screen (`TimerAlertActivity` removed); „Sitzung gespeichert"; the app runs the real controller (`TimerHost`). 04 §8.2, §8.3, §13 updated. |
| 2026-10-22 | D-062: Start, tea picker and setup (#48): `BrewStarter` seam following the `BrewTimer` binding, *Nochmal brühen* from the last session, *Zuletzt gebrüht* row format, picker as Material sheet with GEN-10 search and an immediate pick, scaling from the locked ratio (0,1 g / 5 ml steppers), *Im Rezept speichern* only when changed with an in-place confirmation, cold/ice kettle hint, *Brühen anpassen* through the setup; every route above the shell or timer gives way to the timer on a new alert, overlays and the TMR-21 question never over an alert. 03 SCR-02, SCR-03, SCR-04 updated. |
| 2026-10-22 | D-061: minimal journal and the „Gespeichert" sheet (#51): `JournalRepository` read side with cover photos, day groups by the start's local date in the session's zone (E13), meta line with planned first infusion and poured count („2 von 3 Aufgüssen"), pinned in-progress session opening the timer, `SavedSheetRequests` as the saved sheet port, sheet without scrim over the tab content, 10 s / swipe / hold / TalkBack action, never composed during an alert, `sheet` spring motion. 03 SCR-06, SCR-15 updated. |
| 2026-10-22 | D-060: diagnostics log and *Diagnose* (#43): two-file rolling log ≤ 200 KB in the no-backup storage, written in order on the IO dispatcher and never blocking or throwing into the timer; technical English entries with scheduled vs actual times on both clocks and the delta; service starts and stops, ringer mode at each alert and test, permission states at each process start; *Diagnose* newest first with *Kopieren* (header + entries). 06 §5.6, 03 SCR-27 updated. |
| 2026-10-22 | D-059: production alert player (#44): `AlertPlayback` with a main-thread command queue and settings per alert (300 ms wait, defaults on failure), `ControllerAlertPort` reporting `AlertCompleted` once after the full alert length, `AlertTester` for *Alarm testen* (tick, alert 1.5 s later), a tick never cuts an alert; four synthesised tones Glocke, Holzblock, Glockenspiel, Sanft at -16 LUFS with Klangschale and tick byte-identical. 04 §9.1, §9.2 updated. |
| 2026-10-22 | D-058: timer screen SCR-05 (#45), amended by its motion review (no route transitions or menu exit around the alert, settings popped on alert, paused ring contrast, stable pill, fresh first frame, glow per step, saveable overlays, timer-only lock screen, dim never brighter than the phone): `BrewTimer` seam with `NoBrewTimer` until #42, `TimerUiState.Active.preAlert`, step label and dots, READY/ALERTING layout, finish-would-discard question, every overlay removed on ALERTING, no recipe save on SCR-05, dimming rule, photo slot, TalkBack live region, lock-screen flags, top-level timer route, bar fed by the controller. 04 §4 updated. |
| 2026-10-22 | D-057: exact alarms, BrewTimerService and recovery in the first turn (#42): alarm adapter per kind with request codes 101–106 and the inexact fallback logged; recovery once per process in the first controller turn (moved from #46, amends D-056); receiver and full-screen alert via the app's `TimerHost`; failed recovery reads retried and the alarm set again; service port with the full-screen flag, channels created, `startService` while in the foreground, deferred stop with `stopSelfResult`, handover wake lock, wake lock up to the longest alert, refusals logged and resent by the next alarm; minimal notification content and `TimerAlertActivity` until #47; process-death test with its runner in its own process. 04 §4, §6.2, §6.3, §7.2, §12, §13 updated. |
| 2026-10-22 | D-056: BrewSessionController core (#41): suspend inputs and seven ports, alarm-kind table, in-process wake-up port, non-cancellable lock turns, persist retry and guarded side effects, one alert per deadline with stale end reports ignored, `TimerUiState`, pre-alert frozen at the start, Hilt bindings without stand-ins. 04 §4 updated. |
| 2026-10-22 | D-055: spec #27 slice review, UI: alert-safe children on the alert surface (ink ring and pill), overtime in ink with a warm-accent mark, no Schritte-Sheet entries in ALERTING, alerting active brew bar, GEN-08 guard in the Schritte-Sheet, scroll-to-top one-shot keyed by entry, bar step text contrast, reduce motion instant on the device, allocation-free ring drawing, mini ring long-step mode, rail bottom inset; 02 GEN-06, TMR-06, TMR-10, TMR-11 and 03 §2.3, §3.1, §3.6, §3.8, SCR-05, SCR-31 updated. |
| 2026-10-22 | D-054: spec #27 slice review, engine and data: edits keep a due pre-alert; stale readings clamped and controller rules (clocks inside the lock, failed persist never blocks an alert); `restore` refuses unreachable states; notification per state while alerting; `CompleteSession.autoFinished`; alarm kind per due field and `dueEvents`; *Beenden & neu starten* sequence; seeded service oracle; seed photo download waits for the seed teas; schema v1 frozen from rc.1 with a CI guard; S3 tables declined; total data effects; restore photo sweep; exact cost; one HTTP client (Detekt). 02 TMR-14, TMR-23; 04 §3, §4, §5, §6.2, §7.1, §11, §12; 05 §3.6, §8, §9; 06 §3; 08 §2.5a updated. |
| 2026-10-22 | D-053: timer time rules from #31: pre-alert source order, skip rules, E6/E7 and the 1-s late rule; overtime timeout event and live auto-ack; idle time from the later of last interaction and waiting start; idle demote with `demoted`; the foreground-service contract (S5); midnight timeout in the session zone with `NotifySessionSaved`; recovery order; long steps incl. cold/ice brew; six-field due set. 04 §3.2, §4, §5, §6.1, §7.2, §8.2, §10, §12 and 05 §3.12 updated. |
| 2026-10-22 | D-052: session lifecycle per engine effect, authoritative active brew store, `active_brew` columns of D-048 in schema v1, completion details (end time, cost, BREW event, full INV-06), include-only device backup rules with `fullBackupContent`, restore detection in Room's callbacks, CI schema-staleness check (#36); 05 §3, §6, §8, §9 updated with the #30 decisions. |
| 2026-10-22 | D-051: Schritte-Sheet details (#38): Material 3 modal sheet with its own motion instead of the `sheet` token, closed at once on ALERTING, closing applies nothing, stepper and temperature rules, recipe save mapping; 03 SCR-31 updated. |
| 2026-10-22 | D-050: shop client (allow-list, direct single connection, one request per call, rate limit on the wire, stop signals incl. Cloudflare block pages, hardened robots.txt) and the run-once seed photo download (marker before the first request, stop rules, re-encoded covers) from #37 and its T4 review; 05 §6 and 08 §2.5a updated. |
| 2026-10-22 | D-049: navigation shell details (re-select order, tab fade-through, insets of the active brew bar); 03 §3.6 `tabSwitch` token. |
| 2026-10-22 | D-048: `addStep` default, `editSteps` with `PreAlertChoice` and its rejections, quick timer, boot-aware `recover` in the engine with physical bounds for the reboot rebase, the consume rule for due events, idempotent `StartAlert`, idle time on the wall clock, and the lossless, validated `active_brew` snapshot (#32 and its T3 review); 04 §3.2, §4, §5, §7.1, §7.2, §10 and 05 §3.12 updated. |
| 2026-10-22 | D-047: timer engine design from the T4 review of #28 (due-time set, alert at-least-once with `AlertCompleted`, transition outcome, `CreateSession`, `SessionKind`, ending after the deadline counts as poured, `finishWouldDiscard`); 04 §3.2, §4, §5, §5.1 updated. |
| 2026-10-22 | D-046: design system details from M1-04 (#29): generated schemes with twin roles, font subset keeps `lnum`/`tnum`, German hyphenation, Material Symbols licence, motion tokens in 03 §3.6. |
| 2026-10-22 | Q-15 → D-045: Klangschale variant C (880 Hz) and a bowl-like 0.8 s pre-alert tick; probe `v0.0.2-probe` passed, the Live Update also appeared in the Now Bar. |
| 2026-10-22 | D-044: alert replaces the Live Update while ALERTING (Android 16 auto-group silencing); promotion always requested; D-043 amended; probe → v0.0.2-probe. |
| 2026-10-22 | D-043: Android 16 Live Update API names corrected (promotion settings action, NotificationCompat request, POST_PROMOTED_NOTIFICATIONS not public). |
| 2026-10-22 | D-042: build tooling choices (Detekt 2 alpha, compileSdk-36 library limits, configuration cache off, lint exceptions, action versions). |
| 2026-10-21 | D-041: motion guardrails (calm style, liquor fill + pre-alert glow, strong non-flashing alert, reduce motion, GIF recordings for review); 03 §3.6 rewritten. |
| 2026-10-21 | D-040: the probe gates M1-09/M1-11; fix loop with `0.0.N-probe`; Now Bar best effort; runtime fallbacks stay. |
| 2026-10-21 | D-039: `release.yml` on `main` before the first dispatch; spec issue #6 and test seams for M1-00 … M1-03a. |
| 2026-10-21 | D-036: spec review (Opus xhigh) fixes and PO decisions — three network uses incl. one-time seed photo download, synthesised sounds, full-screen alert, simplified backup (no in-app restore in 1.x), Schritte-Sheet, CI emulator job, release candidates, probe release, catalog hardening; D-016 accepted. |
| 2026-10-21 | v0.1: initial specification set; D-001, D-002 accepted; brand mockup incorporated. |
| 2026-10-21 | Brand mockup committed; Q-13 resolved. |
| 2026-10-21 | D-038: go for development, effective 2026-10-22 09:05 Europe/Berlin. |
| 2026-10-21 | D-037: Claude merges all PRs; PO only approves milestone PRs. |
| 2026-10-21 | Repository renamed to `katznmeik/UmamiT` (D-033 updated); PR #1 merged. |
| 2026-10-21 | D-035: welcome text „Aus Liebe zum Tee", no home greeting, pre-alert default 10 s, signing process with 2 secrets. |
| 2026-10-21 | Grill round 4 (D-034): silent stays visual-only, Claude-generated signing key, CC0 sounds, releases announced by Claude, seed teas linked on first refresh, prominent overtime. |
| 2026-10-21 | App renamed to UmamiT (D-033); application ID `com.umamit.app`. |
| 2026-10-21 | Second mug steep set to 0,25 × first duration (+5 °C) per PO experience (D-032). |
| 2026-10-21 | Grill round 3 (D-032): seed teaware, second mug steep, waiting sessions until midnight, screenshot design review per milestone. |
| 2026-10-21 | Grill round 2 (D-031): manual-only refresh on any network; discontinued teas hidden unless owned; empty teas stay marked „Leer"; early finish = normal session; one infusion = one cup; *Nochmal brühen* (3 last teas) opens the timer directly; no automatic rinse; screen dims while steeping; M1 seed teas Tanabata & Hisui with live data; small-size prices not estimated. |
| 2026-10-21 | Mockups v2 added (`docs/design/mockup-v2-*.png`) and reviewed in 03 §1.1; journal row format adopted from them. |
| 2026-10-21 | Q-04 → D-029 (imagery), Q-11 → D-030 (public repo); asset list `docs/design/assets.md`. |
| 2026-10-21 | V-01: Tee-Kontor structure verified live (Shopware 6.7, Cloudflare, sitemap discovery, Kyusu/Westlich brewing grid, variants per URL, shop taste scales); 08 §2 rewritten; rate limit 2 req/s; estimated package prices. |
| 2026-10-21 | Agent plan (12, D-026); Q-01, Q-05, Q-18 resolved (D-027, D-028); Q-04 options and Q-11 notes added; D-016 switched to synthetic fixtures; tee-kontor-kiel.de reachable from the cloud environment. |
| 2026-10-21 | v0.2: PO interview — personal sideloaded app, German, Samsung/Android 16, Tee-Kontor catalog with in-app refresh, timer details (pre-alert, ringer-mode alert, Live Update), auto-journal, Stats tab, delivery plan. D-013 … D-025 added; D-004, D-010 superseded; D-001, D-006 amended. |
