# 10 · Open Questions & Decision Log

> Status: **Living document** · Last updated: 2026-10-21

## Open questions

| ID | Question | Proposed default | Needed by |
|---|---|---|---|
| Q-15 | Default **alert sound** „Klangschale" (singing bowl): PO agrees in principle but wants to **hear it** before final approval → listen via *Alarm testen* in the M1 APK. | Klangschale | M1 acceptance |

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
| Q-01 Application ID | `com.umamitea.app` (accepted). |
| Q-05 Serif font | Cormorant Garamond (accepted). |
| Q-18 Galaxy Watch | Not used; no watch support needed (NOT-04 → Won't). |
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
personal data; the catalog refresh is the one network exception (D-014).

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
  taps *Katalog aktualisieren*.
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
### D-016 — Handling of scraped content · *Proposed*

Scraped texts and photos are stored only on the phone for personal use and are
**never committed** to the repository. Parser tests use **synthetic HTML
fixtures** that reproduce the page structure (element classes, table layout)
with invented text. A manual "live parser check" task runs the parser against
the real site from a cloud session when needed. No bundled snapshot unless
generated at build time and not committed (CAT-14).

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
  default 5 s), overridable per recipe.
- Alert follows the ringer mode (sound / vibration / silent), repeats ~15 s.
- Live Update on lock screen, status bar and Now Bar.
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
photos only in the manual export.

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
- Westlich recipes with one infusion get a second (1,5 ×, +5 °C).
- Leaf in grams with 0,1 g; Google device backup is on.
- Waiting sessions live until midnight; Live Update demoted after 30 min
  (TMR-14).
- Milestone done = on-device checklist + design review of rendered
  screenshots in the PR (09 §1).

<a id="d-026"></a>
### D-026 — Subagent distribution: model and effort per task · *Accepted* (PO, 2026-10-21)

Development tasks are delegated to subagents by tier: T1 simple → Sonnet,
low effort; T2 standard → Opus, medium; T3 complex → Opus, high; T4 critical →
Opus, extra high (`xhigh`, the ceiling; `max` not used). Details, task
catalogue and escalation rules: [12 · Subagent Distribution Plan](12-agent-plan.md).

<a id="d-027"></a>
### D-027 — Application ID and display font · *Accepted* (PO, 2026-10-21)

Application ID `com.umamitea.app`; serif display font Cormorant Garamond (OFL).

<a id="d-028"></a>
### D-028 — No smartwatch support · *Accepted* (PO, 2026-10-21)

No Galaxy Watch integration or testing; notification bridging may happen by
default but is not a requirement.

## Change log

| Date | Change |
|---|---|
| 2026-10-21 | v0.1: initial specification set; D-001, D-002 accepted; brand mockup incorporated. |
| 2026-10-21 | Brand mockup committed; Q-13 resolved. |
| 2026-10-21 | Grill round 3 (D-032): seed teaware, second mug steep, waiting sessions until midnight, screenshot design review per milestone. |
| 2026-10-21 | Grill round 2 (D-031): manual-only refresh on any network; discontinued teas hidden unless owned; empty teas stay marked „Leer"; early finish = normal session; one infusion = one cup; *Nochmal brühen* (3 last teas) opens the timer directly; no automatic rinse; screen dims while steeping; M1 seed teas Tanabata & Hisui with live data; small-size prices not estimated. |
| 2026-10-21 | Mockups v2 added (`docs/design/mockup-v2-*.png`) and reviewed in 03 §1.1; journal row format adopted from them. |
| 2026-10-21 | Q-04 → D-029 (imagery), Q-11 → D-030 (public repo); asset list `docs/design/assets.md`. |
| 2026-10-21 | V-01: Tee-Kontor structure verified live (Shopware 6.7, Cloudflare, sitemap discovery, Kyusu/Westlich brewing grid, variants per URL, shop taste scales); 08 §2 rewritten; rate limit 2 req/s; estimated package prices. |
| 2026-10-21 | Agent plan (12, D-026); Q-01, Q-05, Q-18 resolved (D-027, D-028); Q-04 options and Q-11 notes added; D-016 switched to synthetic fixtures; tee-kontor-kiel.de reachable from the cloud environment. |
| 2026-10-21 | v0.2: PO interview — personal sideloaded app, German, Samsung/Android 16, Tee-Kontor catalog with in-app refresh, timer details (pre-alert, ringer-mode alert, Live Update), auto-journal, Stats tab, delivery plan. D-013 … D-025 added; D-004, D-010 superseded; D-001, D-006 amended. |
