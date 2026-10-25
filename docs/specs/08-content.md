# 08 · Content & Catalog Import

> Status: **Draft v0.2** · Last updated: 2026-10-21
> v0.2: Tee-Kontor Kiel is the primary content source (§2 verified against the live site); built-in styles become
> internal fallbacks; German vocabulary; own guides and sample teas dropped.

## 1. Principles

- **Primary source:** [Tee-Kontor Kiel](https://www.tee-kontor-kiel.de/)
  product pages and *Tee-Info* guides, fetched by the app (CAT-07).
- **Personal use:** content is stored on the PO's phone only and not
  redistributed (NFR-PRIV-10). About screen credits the source.
- **Shown verbatim, derived transparently:** the shop's brewing table and text
  are always shown as found; the derived timer recipe is shown next to them,
  with a „Rezept prüfen" reason when it is not a straight table conversion.
- **No health claims** in any app-authored text.
- **Language:** German.

## 2. Tee-Kontor catalog import

### 2.1 Status of this section

**Verified against the live site on 2026-10-21 (task V-01)** with 19 sample
requests. The shop can change at any time; selectors are isolated in the
parser (NFR-MAINT-04).

### 2.2 Source structure

| Aspect | Verified finding |
|---|---|
| Platform | **Shopware 6.7** storefront behind **Cloudflare** (bot challenges possible) |
| Host | All pages and images on `www.tee-kontor-kiel.de` (`/media/…` originals, `/thumbnail/…_{W}x{W}.jpg`). This is the only allow-listed host. |
| `robots.txt` | `Allow: /`, but `Disallow: */?` (so paginated listings `?p=N` are off-limits), `*/widgets/`, `*/navigation/`, `*/bundles/`, account/checkout. No crawl-delay. Sitemap: `/sitemap.xml`. |
| Green tea sections | `/tee/gruener-tee/` with `japan/`, `china/`, `taiwan/`, `andere-regionen/`, `aromatisierter-gruentee/`, `bio-gruentee/`, `klassischer-gruentee/`, `matcha-teepulver/`, `shincha-<year>/` (year in slug). Japan by type: `/tee/gruener-tee/japan/{sencha,shincha,kabusecha,gyokuro,matcha,kukicha,bancha,houjicha,kamairicha,tamaryokucha,genmaicha,mizudashi-kaltaufgussbeutel}/`. A parallel tree exists at `/tee/japantee/<sorte>/`. Matcha also at `/matcha/matcha-teepulver/` (exclude `matcha-sets/`, `matchaschalen-chawan/`, `matchazubehoer/`, `leckeres-mit-matcha/`). |
| Size | Section „Grüner Tee" shows **295 products**, one card per parent product, incl. sets and a few non-tea items; after de-duplication ≈ 260–280 teas. |
| Discovery | **Sitemap** (`/sitemap.xml` → one `.xml.gz`, ~4,000 URLs, updated daily): ~3,500 product URLs (every size variant has its own URL), ~300 `/tee-info/<slug>` articles, all categories. No category info in the sitemap → classification via the product page breadcrumb. |
| Article numbers | Parent `TG0557`; size variants `TG0557-10/-50/-100/-250/-500/-1000` (suffix = grams); `TG0557M` is the same page as the parent (canonical `TG0557`), not a sample; lower-case suffixes like `AT0220a` are separate articles; `…SET` = sets (excluded); some teas are split into separate base numbers per size (e.g. `AT0868`/`AT0869`). |

### 2.3 Product page selectors and field mapping

| Data | Selector / source | Catalog field ([05 §4.1](05-data-model.md#41-catalogtea)) |
|---|---|---|
| Title | `h1.product-detail-name` (strip size suffix „– 30g") | `name` |
| Article number | `span.product-detail-ordernumber[itemprop=sku]` (label „Produktnummer:") | `key` (base number: strip `-<grams>` and `M`; keep lower-case letters) |
| Canonical URL | `link[rel=canonical]` | `product_url` |
| Price | `.product-detail-price` („17,90 €") | default package price |
| Base price | `.product-detail-price-unit` („Inhalt: 0.1 kg (179,00 € / 1 kg)") | package grams; `base_price_cents_per_100g` = per-kg ÷ 10 |
| Availability | `.delivery-information` present and `.btn-buy` present → available; `.acris-stock-notification` → out of stock | `available` |
| Properties | `table.product-detail-properties-table tr.properties-row` → `th.properties-label` / `td.properties-value > span` (skip `.st-property-label`). Labels: *Anbaugebiete* (country), *Aroma*, *Geschmack*, *Hersteller …*, *Kultivare*, *Saison* (when to drink — **not** the harvest), *Matcha Qualitäten* | `origin_country`, `aroma`, `taste_notes`, `manufacturer`, `cultivar`, `season`, `matcha_grade` |
| Badges | `.st-usps .filter-active`: „Bio", „Coldbrew geeignet"; `.st-invers`: „Dämpfung: …", „Ernte: …", „Beschattung: …" | `is_organic`, `cold_brew_suitable`, `steaming`, `harvest`, `shading` |
| Taste scales | `.st-scales-usp .container`: label (Umami, Bitterstoffe, …) + count of filled leaves (`scale-full-leaf.svg`) out of 5 | `taste_scales_json`; Umami scale → `umami_level` (§2.6) |
| Description | `.product-detail-description-text` paragraphs | `description_html`; also the source of brewing prose and „Zutaten: …" |
| Ingredients | Paragraph starting „Zutaten:" | `ingredients` |
| Flavoured | *Aroma* ≠ „naturbelassen" (e.g. „natürlich aromatisiert") **or** ingredients beyond tea, **or** breadcrumb `aromatisierter-gruentee` | `is_flavored` |
| Brewing grid | `div.st-zubereitung`: header `.st-zubereitung-header-col` (Menge, Temperatur, 1./2./3. Aufguss); rows `.st-zubereitung-value` with class `st-zubereitung-japan` (Kyusu icon) or `st-zubereitung-westlich` (western icon); strip `.st-zubereitung-mobilelabel`. Intro text is always „Unsere Angaben sind Richtwerte …" (ignored) | `brew_table_raw` (JSON per row) |
| Brewing prose | Description paragraphs mentioning Zubereitung, Aufguss, g/ml, °C, Kaltaufguss | `brew_text_raw` |
| Variants | `form[data-variant-switch]` → `.product-detail-configurator-option` labels („10g", „50g", „250g 3% gespart¹"); each variant is a separate URL/SKU; other sizes' prices are **not** on the parent page | `catalog_package` (grams, savings %) |
| Photo | `img.gallery-slider-image[data-full-image]` (original); thumbnails via `data-srcset` | `photo_url` |
| Breadcrumb | `ol.breadcrumb` (e.g. `/tee/gruener-tee/japan/sencha/`) | `breadcrumb`, `tea_type`, section membership |
| Structured data | Microdata `schema.org/Product` (name, sku, gtin13, image, offers, aggregateRating) — **no JSON-LD**; used only as a fallback for name/price/image | — |

Region is not a property; it is taken from the title or prose where
recognisable (e.g. „Uji", „Kirishima", „Yame"), else left empty. Harvest year
comes from titles („2026 Shincha …").

**Conversion into collection-tea fields** (CAT-12):

| Catalog data | Collection field | Rule |
|---|---|---|
| Ernte badge / prose / title | `harvest_year` | First 4-digit year 2000–(current + 1), else empty |
| Ernte badge / prose | `harvest_season` | „first flush", „Frühlingsernte", „Erste Pflückung", „Ichibancha", „Shincha" → `FIRST_FLUSH`; „second flush", „Nibancha" → `SECOND_FLUSH`; „Sommerernte", „Sommer" → `SUMMER`; „Herbst" → `AUTUMN`; else `UNKNOWN`. Never taken from *Saison* (that is when to drink the tea). |
| Beschattung badge / prose | `shading_days` | Number + „Tage" → n; + „Wochen" → 7n; ranges → midpoint (e.g. prose „für 6 Tage beschattet"); badge without number → empty |
| Dämpfung badge | `processing_notes` | Copied as text (e.g. „Chumushi") |
| Ingredients | `ingredients` | Copied |

### 2.4 Discovery, variants and request budget

1. Fetch `robots.txt` and respect it; fetch `sitemap.xml` and its `.xml.gz`.
2. From product URLs, derive base numbers; group variant URLs per base
   number. Skip `…SET`.
3. **First refresh:** fetch the parent page of every base number not yet
   classified, read the breadcrumb, and keep products under
   `/tee/gruener-tee/`, `/tee/japantee/` (green types only) or
   `/matcha/matcha-teepulver/`. Store non-green base numbers in a skip list
   (`catalog_skip`) so later refreshes don't fetch them again.
4. Exclude products whose ingredients contain no tea at all (e.g. „Ingwer
   Shot" in the flavoured section).
5. **Later refreshes:** fetch known green parents plus base numbers new in the
   sitemap; products missing from the sitemap → discontinued.
6. **Package prices:** variant sizes come from the configurator labels and
   variant URLs. Sizes **larger** than the parent package are **estimated**
   without extra requests: `price(size) = parent price ÷ parent grams × size ×
   (1 − savings %)`, rounded to the cent, marked as estimate (verified: within
   ±0,05 € of the real prices). Sizes **smaller** than the parent (e.g. 10 g
   samples) carry a surcharge and are **not estimated** (Hisui 10 g = 2,80 € =
   280 €/kg vs. 179 €/kg) — shown as „Preis beim Hinzufügen". The exact price
   of any size is fetched from its variant URL when the user adds that package
   (CAT-12, network use (c) of GEN-01, online only; offline the estimate or
   an empty price is used and editable).
7. Teas split into separate base numbers per size but with the same name are
   merged by normalised name. The merged entry's `key` is deterministic: the
   **lowest** base number (string order, e.g. `AT0868` for
   `AT0868`/`AT0869`).
8. New harvests usually get new article numbers; the old one becomes
   discontinued.

Request budget at the default rate limit (2 requests/s, all requests
including images):

| Refresh | Requests (approx.) | Duration |
|---|---|---|
| First | sitemap + ~1,560 parent pages for classification (all shop categories, counted live on 2026-10-25, D-076) + ~280 photos + guides (G = number of keyword-selected guide slugs, §2.7, plus their images; ~10–40) | ~15–18 min |
| Later | sitemap + ~280 green pages + new items + changed photos + G guides and changed guide images | ~3–4 min |

### 2.5 Recipe derivation

`RecipeDeriver` turns the brewing grid and prose into timer recipes
(CAT-04).

**Step 1 — Rows to recipes.** Each grid row becomes one recipe:
`st-zubereitung-japan` → „Kyusu" (`KYUSU`), or for matcha „Usucha"
(`MATCHA_USUCHA`);
`st-zubereitung-westlich` → „Westlich" (`WESTERN`). The **default recipe** is
the Kyusu row for Japanese teas when present, otherwise the first row.

**Step 2 — Parse cells.** Patterns observed: amounts `~4g/100ml`,
`~4g/200ml`, `~10g/L`, `2-2,5g/50ml`; temperatures `50-55°C`, `~65°C`,
`~65-75°C`; times `~90 Sek`, `60 Sek`, `~1 Min`; `-` = not given. A leading
`~` is ignored.

**Step 3 — Fill gaps per field:** grid value → else prose value → else the
built-in style default.

**Step 4 — Normalise:** ranges → midpoint; temperature → `snap5` (half up:
52,5 → 55 °C; 67,5 → 70 °C); times → nearest 5 s; amounts: water = stated
volume; per-litre amounts (`~10g/L`) use **250 ml** (one mug) for western
recipes and the style's default volume for kyusu recipes, leaf scaled by the
ratio (10 g/L → 2,5 g / 250 ml); leaf rounded to 0,1 g. The vessel picker
rescales later (WARE-03).

**Step 5 — Steps:** one INFUSION per given infusion (1–3) at the recipe
temperature. **PO preference:** a Westlich recipe with only one infusion gets
a second infusion of **0,25 ×** the first duration (rounded to 5 s, minimum
5 s; e.g. 1:00 → 0:15) at +5 °C
(source noted, not flagged). If only the first infusion is given and the method is KYUSU or
GONGFU, follow-up infusions come from the style default. Matcha: one WHISK
step with the style's whisk duration (the shop states none; not flagged).
**Rinse steps are never added automatically**; they exist only when the user
adds one to a recipe.

**Step 6 — Cold brew.** If the „Coldbrew geeignet" badge is present or the
prose describes a Kaltaufguss, add a recipe „Kaltaufguss" (`COLD_BREW`):
values from the prose when given (e.g. „Ziehzeit … 20 - 30 Minuten …
Dosierung verdoppeln" → 25 min, double leaf), otherwise the style's
cold-brew default; styles without one use the sencha cold-brew default
(D-076). Temperature 5 °C („kalt"). **„Dosierung verdoppeln"**
means: double the default recipe's leaf amount at the default recipe's
water volume (e.g. Kyusu 4,0 g / 200 ml → 8,0 g / 200 ml).

**Step 7 — Plausibility:** hot 40–100 °C; first hot infusion 5 s–10 min;
ratio 0,5–15 g/100 ml; otherwise style default + `IMPLAUSIBLE_VALUE`.

**Flags.** Every recipe stores its `source` (`CATALOG_TABLE`, `CATALOG_TEXT`,
`STYLE_DEFAULT`). The tea-level „Rezept prüfen" flag (CAT-05) is set only for
the **default recipe**:
- any of its fields from prose → `FROM_TEXT`;
- any field (except the matcha whisk time) from the style default →
  `FROM_STYLE_DEFAULT`;
- grid and prose both present but differing (temperature > 5 °C, time >
  max(10 s, 20 %), amount > 20 %) → `TABLE_TEXT_CONFLICT` (grid wins);
- implausible values → `IMPLAUSIBLE_VALUE`.

Additional recipes (cold brew, western) show their source as a small note in
the recipe instead of flagging the tea.

**Worked examples (live data, 2026-10-21):**

| Tea | Grid / prose | Derived recipes | Tea flag |
|---|---|---|---|
| Bio Uji Gyokuro Gokō `AT0634` | Kyusu `~4g/100ml · 50-55°C · ~90 Sek · ~30 Sek · ~20 Sek` | **Kyusu** 4,0 g / 100 ml / 55 °C: 1:30, 0:30, 0:20 · Kaltaufguss from style if badge present | — |
| Bio Sencha Hisui `TG0557` | Westlich `~10g/L · ~65°C · ~1 Min`; Kyusu `~4g/200ml · 60-65°C · 60 Sek · 10 Sek · 10 Sek`; badge „Coldbrew geeignet" | **Kyusu** 4,0 g / 200 ml / 65 °C: 1:00, 0:10, 0:10 · Westlich 2,5 g / 250 ml / 65 °C: 1:00, 0:15 @70 · Kaltaufguss (style default) 10,0 g / 1000 ml / 5 °C: 4:00:00 | — |
| Bio Premium Matcha Horai `AT0220a` | Kyusu `~1g/100ml · ~70°C · -` | **Usucha** 1,0 g / 100 ml / 70 °C: whisk 0:20 | — |
| Bio Yuzu Sencha `TG0569` (flavoured) | Westlich `~10g/L · ~65-75°C · ~1 Min`; Kyusu `~4g/200ml · 65-75°C · 60 Sek · 10 Sek · 10 Sek`; prose: Kaltaufguss 20–30 min, double dose | **Kyusu** 4,0 g / 200 ml / 70 °C: 1:00, 0:10, 0:10 · Westlich 2,5 g / 250 ml / 70 °C: 1:00, 0:15 @75 · Kaltaufguss 8,0 g / 200 ml / 5 °C: 25:00 | — |

### 2.5a M1 seed teas (PO's current teas)

The M1 timer APK has no catalog yet. It ships these two teas, which the PO
owns, as **seed data** in the collection (verified on the live site on
2026-10-21). Only factual data is committed (names, article numbers, origin,
cultivar, processing, brewing parameters, prices); the shop's description
texts and photos are **not** committed (D-016). During the first-start setup
the app downloads the two product photos **once** from the allow-listed host
(ONB-06, network use (b) of GEN-01; visible progress, skippable); without
network the timer fallback image (A4) is used. Descriptions (and photos, if
still missing) arrive **automatically** with the first catalog refresh in M2
via CAT-15 — no manual *Aus Katalog zurücksetzen* needed.

| Field | Bio Sencha Tanabata | Bio Sencha Hisui |
|---|---|---|
| `catalog_key` | `TG0213` | `TG0557` |
| URL | `/bio-sencha-tanabata/TG0213` | `/bio-sencha-hisui/TG0557` |
| Style / type | `sencha` / „Sencha" | `sencha` / „Sencha" |
| Origin | JP, Kagoshima (Kyushu) | JP, Kirishima, Kagoshima (Kyushu) |
| Cultivar | Blend, mainly Yabukita | Okumidori (100 %) |
| Harvest | Summer harvest, early July (`SUMMER`) | First flush (`FIRST_FLUSH`) |
| Shading | — | 6 days |
| Processing | Asamushi (light steaming) | Asamushi (light steaming) |
| Organic / flavoured | Bio / no | Bio / no |
| Shop taste scales | Komplexität 1, Bitterstoffe 1, Süße 1, Fruchtig 2 (no Umami scale) | Umami 1, Komplexität 2, Bitterstoffe 1, Süße 3, Fruchtig 2, Nussig 1, Würzig 1, Röstnoten 1 |
| Umami level | `MEDIUM` (style default — no shop scale) | `LOW` (shop scale 1) |
| Taste notes (*Geschmack*) | fruchtig, mild, süß | fruchtig, mild, mittelkräftig, süß |
| Badges | Für Einsteiger | Coldbrew geeignet, Für Einsteiger, Preistipp |
| Brewing grid (verbatim) | Westlich `~10g/L · ~65-70°C · ~1 Min · - · -`; Kyusu `~4g/200ml · 60-70°C · 45 Sek · 10 Sek · 10 Sek` | Westlich `~10g/L · ~65°C · ~1 Min · - · -`; Kyusu `~4g/200ml · 60-65°C · 60 Sek · 10 Sek · 10 Sek` |
| **Recipe „Kyusu" (default)**, vessel „Kyusu" (250 ml usable) | 5,0 g / 250 ml / **65 °C**: 0:45, 0:10, 0:10 | 5,0 g / 250 ml / **65 °C**: 1:00, 0:10, 0:10 |
| Recipe „Westlich", vessel „Becher mit Sieb" (400 ml) | 4,0 g / 400 ml / 70 °C: 1:00, 0:15 @75 | 4,0 g / 400 ml / 65 °C: 1:00, 0:15 @70 |
| Recipe „Kaltaufguss" | — | 10,0 g / 1000 ml / 5 °C: 4:00:00 (style default, from badge) |
| Recipe check flag | none | none |
| Packages (exact prices) | 10 g 1,85 € · 100 g 8,50 € · 250 g 20,60 € · 500 g 40,35 € · 1000 g 76,50 € | 10 g 2,80 € · 50 g 8,95 € · 100 g 17,90 € · 250 g 43,40 € · 500 g 85,05 € · 1000 g 161,10 € |
| Base price | 85,00 €/kg | 179,00 €/kg |
| **PO's stock (seed)** | INITIAL 100 g, 8,50 €, unopened | INITIAL 100 g, 17,90 €, unopened |
| Cost per cup (Kyusu, 3 infusions) | 5,0 g × 0,085 €/g = 0,43 € → 0,14 €/Tasse | 5,0 g × 0,179 €/g = 0,90 € → 0,30 €/Tasse |
| Cost per cup (Becher, 2 infusions) | 4,0 g × 0,085 €/g = 0,34 € → 0,17 €/Tasse | 4,0 g × 0,179 €/g = 0,72 € → 0,36 €/Tasse |
| Photo URL (downloaded at runtime) | `/media/ab/15/63/1727307323/gtbiosenchatanabataaiya.jpg` | `/media/7c/c3/7c/1727308250/gtbiosenchahisui.jpg` |

The photo download (D-050) reads robots.txt first, runs at most once
(marker set before the first request, and not before a seed tea exists,
D-054), stops on every stop signal of §2.8 and on a 401/403/5xx, and stores
each photo re-encoded as a JPEG cover
(≤ 2048 px, no metadata, origin `CATALOG_COPY`). A photo the shop doesn't
have, that robots.txt disallows or that doesn't decode keeps the placeholder.

Recipes are scaled from the shop's ratios (4 g/200 ml, 10 g/L) to the PO's
seed vessels (WARE-06). Purchase date of the seed stock = first app start;
`opened_date` is set by the first session (INV-06).

### 2.6 Style mapping and umami level

Style from the breadcrumb's last segment, else name keywords:

| Breadcrumb / keyword | Style key |
|---|---|
| `gyokuro` | `gyokuro` |
| `kabusecha` | `kabusecha` |
| `sencha` + „Fukamushi" in name | `fukamushi_sencha` |
| `sencha` | `sencha` (flavoured → `flavored_green`) |
| `shincha`, `shincha-<year>` | `shincha` |
| `matcha`, `matcha-teepulver` | `matcha` (latte/flavoured blends → `matcha`, flavoured) |
| `kukicha`, `kukicha-karigane-shiraore` | `karigane` |
| `bancha` | `bancha` |
| `houjicha` (incl. powder → form POWDER) | `hojicha` |
| `genmaicha` | `genmaicha` |
| `kamairicha` | `kamairicha` |
| `tamaryokucha` | `tamaryokucha` |
| `mizudashi-kaltaufgussbeutel` | style from name tokens (e.g. Sencha) else `green_generic`; form TEA_BAG; default recipe = cold brew |
| `aromatisierter-gruentee` | `flavored_green` |
| China/Taiwan/andere-regionen + name „Long Jing"/„Lung Ching" / „Bi Luo Chun" / „Anji" / „Sejak" | `longjing` / `bi_luo_chun` / `anji_bai_cha` / `korean_green` |
| anything else | `green_generic` + flag `UNMAPPED_STYLE` |

**Umami level** from the shop's Umami scale when present: 0 → `NONE`, 1 →
`LOW`, 2 → `MEDIUM`, 3 → `HIGH`, 4–5 → `VERY_HIGH`; otherwise the style's
level. All shop scales (Umami, Bitterstoffe, …) are shown on the catalog
detail page.

### 2.7 Tee-Info guides

The blog (`/tee-info/`, canonical `/blog`) shows only category tiles; articles
are discovered from the sitemap (`/tee-info/<slug>`, ~300). Articles are
selected by slug keywords (japantee, gruentee, matcha, sencha, gyokuro,
kabuse, shincha, houjicha, genmaicha, kaltaufguss, kyusu, umami) and fetched
individually. Article body: `.pixel-blog-detail .cms-element-text` (h1
inside; gallery `.st-blog-gallery`); JSON-LD `BlogPosting` provides title,
date and image.

Confirmed relevant articles: `ein-guide-fuer-japantee`,
`matcha-verstehen-ein-guide-fuer-teefans`, `a-guide-to-sencha`,
`kabuse-beschattung-von-japanischem-gruentee`, `teerezepte-matcha-eistee`,
`teefibel-zubereitung-der-kaltaufguss-fahrplan-wie-geht-eigentlich-die-teezubereitung-als-kaltaufguss`.

Conversion: Markdown (headings, paragraphs, lists, tables, images downloaded
locally); links to product pages become in-app catalog links.

### 2.8 Sanitising and robustness

- Strip scripts, styles, iframes, forms, tracking pixels and embeds (YouTube →
  plain link); keep basic formatting only.
- **Cloudflare:** a response is a challenge if it has the header
  `cf-mitigated: challenge`, or (HTTP 403/503) its body contains challenge
  markers (`cdn-cgi/challenge-platform`, `_cf_chl_opt`, „Just a moment…").
  The refresh then stops immediately, keeps the old catalog, and shows „Der
  Shop hat den Abruf vorübergehend blockiert – bitte später erneut
  versuchen." No attempt is made to bypass challenges.
- **Rate signals:** HTTP 429 or any `Retry-After` header also stops the
  refresh (old catalog kept, same message); no automatic waiting and retry.
- **Refusals and outages:** a 401/403/5xx or a network error (offline) also
  stops the whole job; after any stop the job sends no further request. A
  3xx (never followed), 404 or other 4xx leaves only that URL out (D-050,
  D-074).
- **One connection:** all requests go through a single HTTP connection,
  sequentially, at ≤ 2 requests/s.
- **robots.txt:** rules are matched with longest-match precedence and `*` /
  `$` wildcards (RFC 9309); a disallowed URL is never fetched. It is read
  once per job (seed photos, refresh, exact price) by the shared shop session:
  404/410 → everything allowed; 401/403/5xx or a stop signal → the job stops
  before any other request; any other failure → everything disallowed
  (RFC 9309 §2.3.1, D-050, D-074).
- Identify with a clear user agent („UmamiT/<version> (private app)").

### 2.9 Sanity thresholds (CAT-07 AC5)

Abort without changes if: fewer than 50 % of the previous green product count
(or < 150 on first import) are parsed; or > 30 % of products lack name, price
or article number; or > 50 % lack a brewing grid **and** brewing prose
(selector broke). Out-of-stock products have a price but no availability and
count as valid.

## 3. Built-in tea styles

Internal fallbacks (STY-01). Temperatures already in 5 °C steps; `g/TL` is
used only to convert teaspoon amounts during import. Freshness = suggested
days after opening.

| Key | Name (de) | Umami | Fresh. | g/TL | Default recipes |
|---|---|---|---|---|---|
| `gyokuro` | Gyokuro 玉露 | VERY_HIGH | 60 | 3,0 | KYUSU 6 g / 60 ml / 50 °C: 2:00, 0:20 @55, 1:00 @65, 1:30 @75 · Kaltaufguss 10 g / 500 ml / 5 °C: 3 h · Eisaufguss (ICE_BREW) 6 g / 100 ml Eis / 5 °C: 2 h |
| `kabusecha` | Kabusecha かぶせ茶 | HIGH | 45 | 2,5 | KYUSU 5 g / 120 ml / 65 °C: 1:30, 0:20 @70, 1:00 @80 |
| `sencha` | Sencha 煎茶 | MEDIUM | 45 | 2,5 | KYUSU 5 g / 150 ml / 75 °C: 1:00, 0:15 @80, 0:45 @85 · Kaltaufguss 10 g / 1000 ml: 4 h |
| `fukamushi_sencha` | Fukamushi Sencha 深蒸し煎茶 | MEDIUM | 45 | 3,0 | KYUSU 5 g / 150 ml / 70 °C: 0:45, 0:05 @75, 0:30 @80 |
| `shincha` | Shincha 新茶 | HIGH | 30 | 2,5 | KYUSU 5 g / 150 ml / 70 °C: 1:00, 0:15 @75, 0:45 @80 |
| `matcha` | Matcha 抹茶 | HIGH | 30 | 2,0 | Usucha 2 g / 70 ml / 75 °C: Aufschlagen 0:20 · Koicha 4 g / 35 ml / 75 °C: 0:40 |
| `karigane` | Kukicha / Karigane 茎茶 | MEDIUM | 45 | 1,5 | KYUSU 5 g / 150 ml / 70 °C: 1:00, 0:20 @75, 1:00 @85 |
| `bancha` | Bancha 番茶 | LOW | 60 | 2,0 | KYUSU 5 g / 200 ml / 85 °C: 0:45, 0:20 @90, 1:00 @95 |
| `hojicha` | Hōjicha ほうじ茶 | LOW | 60 | 1,5 | KYUSU 4 g / 200 ml / 95 °C: 0:30, 0:30, 1:00 |
| `genmaicha` | Genmaicha 玄米茶 | LOW | 45 | 3,0 | KYUSU 5 g / 200 ml / 85 °C: 0:45, 0:15 @90, 1:00 @95 |
| `kamairicha` | Kamairicha 釜炒り茶 | LOW | 60 | 2,5 | KYUSU 5 g / 150 ml / 80 °C: 1:00, 0:20 @85, 0:45 @90 |
| `tamaryokucha` | Tamaryokucha 玉緑茶 | MEDIUM | 45 | 2,5 | KYUSU 5 g / 150 ml / 75 °C: 1:00, 0:15 @80, 0:45 @85 |
| `longjing` | Long Jing 龙井 | MEDIUM | 90 | 1,5 | GONGFU 5 g / 100 ml / 80 °C: 0:20, 0:25, 0:30, 0:40, 1:00 · WESTERN 3 g / 250 ml / 80 °C: 2:00, 2:30 |
| `anji_bai_cha` | Anji Bai Cha 安吉白茶 | HIGH | 90 | 1,0 | GONGFU 4 g / 100 ml / 80 °C: 0:20, 0:25, 0:30, 0:40, 1:00 |
| `bi_luo_chun` | Bi Luo Chun 碧螺春 | MEDIUM | 90 | 2,0 | GONGFU 4 g / 100 ml / 75 °C: 0:15, 0:20, 0:25, 0:35, 0:50 |
| `korean_green` | Koreanischer Grüntee (Ujeon/Sejak) | HIGH | 60 | 2,0 | KYUSU 4 g / 120 ml / 70 °C: 1:00, 0:30 @75, 1:00 @80 |
| `flavored_green` | Aromatisierter Grüntee | LOW | 120 | 2,5 | WESTERN 3 g / 250 ml / 80 °C: 2:30, 3:00 |
| `green_generic` | Grüntee (sonstiger) | MEDIUM | 90 | 2,5 | WESTERN 3 g / 250 ml / 80 °C: 2:00, 2:30 · GONGFU 5 g / 100 ml / 80 °C: 0:15, 0:20, 0:25, 0:35, 0:50 |
| `white_generic`, `oolong_generic`, `black_generic`, `dark_generic`, `herbal` | Weißer Tee, Oolong, Schwarzer Tee, Pu-Erh, Kräuter | LOW/NONE | — | — | Western defaults (85 / 95 / 95 / 100 / 100 °C) for manually added non-green teas |
| `other` | Andere | — | — | 2,5 | WESTERN 3 g / 250 ml / 85 °C: 3:00 |

These values are well-known starting points; imported Tee-Kontor recipes
take precedence for catalog teas.

## 4. Tasting descriptor vocabulary

| Gruppe | Beschreibungen |
|---|---|
| Meer (MARINE) | Algen, Nori, Kombu, Meeresbrise, Dashi / brühig |
| Gemüse (VEGETAL) | Spinat, Zuckerschote, Edamame, Spargel, gedämpftes Gemüse, frisch gemähtes Gras, Heu, Artischocke |
| Süß (SWEET) | Zuckermais, Esskastanie, Honig, Karamell, Rohrzucker, Süßkartoffel, Vanille |
| Geröstet & nussig (ROASTED_NUTTY) | Gerösteter Reis, geröstete Nüsse, Toast, Kakao, Kaffee, Rauch |
| Blumig (FLORAL) | Orchidee, Jasmin, Osmanthus, Rose, Geißblatt, Kirschblüte |
| Fruchtig (FRUITY) | Zitrus, Yuzu, Steinobst, Muskateller, Melone, Trockenfrüchte, Beeren |
| Erdig & holzig (EARTHY_WOODY) | Waldboden, Pilze, Holz, Leder, Kampfer, mineralisch |
| Würzig & kräutrig (SPICE_HERB) | Minze, Kiefer, Zimt, Kräuter |
| Mundgefühl (MOUTHFEEL) | cremig, seidig, vollmundig, frisch-herb, trocknend, belegend, klarer Abgang, anhaltende Süße |
| Fehlnoten (OFF_NOTE) | abgestanden, flach, zu bitter, metallisch, muffig, sauer |

The umami taste is captured by the Umami slider, not a descriptor.

## 5. (Removed) Sample teas

Dropped in v0.2; the catalog replaces them.

## 6. Brew coach rules

`Could` (JRN-14). Evaluated after the PO adds tasting notes; at most two
tips:

| # | Condition | Tip (de) |
|---|---|---|
| 1 | Bitterkeit ≥ 4 or Adstringenz ≥ 4 | „Recht bitter? Versuch es 5 °C kühler oder den ersten Aufguss ~20 % kürzer." |
| 2 | Umami-style ≥ HIGH, Umami ≤ 2, temperature ≥ recipe + 5 °C | „Umami-Tees mögen es kühler – probier die Rezepttemperatur." |
| 3 | Körper ≤ 1 or „flach" | „Etwas dünn? 10–20 % mehr Blätter oder etwas länger ziehen lassen." |
| 4 | „abgestanden" and past freshness window | „Schon länger offen? Grüntee verliert nach einigen Wochen an Frische." |
| 5 | Rating ≥ 4,5 and session differed from recipe | „Hat dir geschmeckt? Übernimm die heutigen Werte ins Rezept." |

## 7. Micro-copy (de)

- Welcome page (SCR-01): „Aus Liebe zum Tee". The Start screen has **no
  greeting text** — only the ink-wash header (PO decision, D-035).
- Empty states: Meine Tees „Noch keine Tees – stöbere im Katalog." ·
  Journal „Deine erste Tasse wartet." · Katalog „Der Katalog ist noch leer." ·
  Suche „Nichts gefunden – weniger Filter probieren."
