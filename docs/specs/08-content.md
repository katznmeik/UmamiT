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
| Title | `h1.product-detail-name` (strip size suffix „– 30g"); microdata name as fallback | `name` |
| Secondary name | Japanese or Chinese text in the title (kana, kanji, 「」), if any; the name keeps the rest (D-077) | `secondary_name` |
| Article number | `span.product-detail-ordernumber[itemprop=sku]` (label „Produktnummer:") | `key` (base number: strip `-<grams>` and `M`; keep lower-case letters) |
| Canonical URL | `link[rel=canonical]` | `product_url` |
| Price | `.product-detail-price` („17,90 €") | default package price |
| Base price | `.product-detail-price-unit` („Inhalt: 0.1 kg (179,00 € / 1 kg)"); without the reference: price ÷ content (D-077) | package grams; `base_price_cents_per_100g` = per-kg ÷ 10 |
| Availability | `.delivery-information` present and `.btn-buy` present → available; `.acris-stock-notification` → out of stock; neither → unknown (null) | `available` |
| Properties | `table.product-detail-properties-table tr.properties-row` → `th.properties-label` / `td.properties-value > span` (skip `.st-property-label`; each value after the first starts with its own „, ", which is dropped before joining with „, ", D-082). Labels: *Anbaugebiete* (country), *Aroma*, *Geschmack*, *Hersteller …*, *Kultivare*, *Saison* (when to drink — **not** the harvest), *Matcha Qualitäten* | `origin_country`, `aroma`, `taste_notes`, `manufacturer`, `cultivar`, `season`, `matcha_grade` |
| Badges | `.st-usps .filter-active`: „Bio", „Coldbrew geeignet"; `.st-invers`: „Dämpfung: …", „Ernte: …", „Beschattung: …" | `is_organic`, `cold_brew_suitable`, `steaming`, `harvest`, `shading` |
| Taste scales | `.st-scales-usp .container`: label without its colon (Umami, Bitterstoffe, …) + count of filled leaves (`scale-full-leaf.svg`) out of 5 | `taste_scales_json`; Umami scale → `umami_level` (§2.6) |
| Description | `.product-detail-description-text` paragraphs, without staff tip blocks (`.st-teamtipp`, D-076), sanitised (§2.8, D-078) | `description_html`, `description_text`; also the source of brewing prose and „Zutaten: …" |
| Ingredients | Paragraph starting „Zutaten:" | `ingredients` |
| Flavoured | *Aroma* ≠ „naturbelassen" (e.g. „natürlich aromatisiert") **or** ingredients beyond tea (roasted rice and footnotes don't count, D-078), **or** breadcrumb `aromatisierter-gruentee` | `is_flavored` |
| Brewing grid | `div.st-zubereitung`: header `.st-zubereitung-header-col` (Menge, Temperatur, 1./2./3. Aufguss); rows `.st-zubereitung-value` with class `st-zubereitung-japan` (Kyusu icon), `st-zubereitung-westlich` (western icon) or `st-zubereitung-china` (Gongfu, D-089); strip `.st-zubereitung-mobilelabel`. Intro text is always „Unsere Angaben sind Richtwerte …" (ignored) | `brew_table_raw` (JSON per row) |
| Brewing prose | Description paragraphs that are brewing instructions: two of temperature (°C, also „80°"), time (Sek, Min, Std) and amount per volume (g, TL or tea bags per ml or l: „1 Beutel auf 0,3–0,5 Liter"), or one of them with a brewing word (Zubereitung, Aufguss, Ziehzeit, Dosierung, …); tasting notes, package sizes and storage hints are not (D-082), nor is any paragraph naming a video, whatever values it names (D-089) | `brew_text_raw` |
| Variants | `form[data-variant-switch]` → `.product-detail-configurator-option` labels („10g", „50g", „250g 3% gespart¹"); each variant is a separate URL/SKU; other sizes' prices are **not** on the parent page | `catalog_package` (grams, savings %) |
| Photo | `img.gallery-slider-image[data-full-image]` of the product's own gallery (original); microdata image as fallback; thumbnails via `data-srcset` | `photo_url` |
| Breadcrumb | `ol.breadcrumb` (e.g. `/tee/gruener-tee/japan/sencha/`); *Sorte* = title of the green type level (`…/japan/<type>/`, `/tee/japantee/<type>/`, `matcha-teepulver`); for a Japan-tree product without a type level (a tea family's or garden's page) the green type its name names („Bancha", „Houjicha"; „Fukamushi" → „Sencha"; D-089), else empty (D-077) | `breadcrumb` (deepest path), `shop_category` (titles), `tea_type`, section membership |
| Structured data | Microdata `schema.org/Product` (name, sku, gtin13, image, offers, aggregateRating) — **no JSON-LD**; used only as a fallback for name/price/image | — |

Buy-box fields (name, order number, price, unit price, stock) are read from
the product's own buy box (`.product-detail-buy`) only, never from the
cross-selling boxes of other products on the page (D-076).

Region is not a property; it is taken from the title or prose where
recognisable (e.g. „Uji", „Kirishima", „Yame": a fixed list of growing
regions, whole words, the title first; D-078), else left empty. Harvest year
comes from the *Ernte* badge and the title („2026 Shincha …") only, never from
the prose, whose years are travel reports, founding years and link texts
(D-089).

**Conversion into collection-tea fields** (CAT-12):

| Catalog data | Collection field | Rule |
|---|---|---|
| Ernte badge / title | `harvest_year` | First 4-digit year 2000–(current + 1) in the badge, else in the title; never from the prose (D-089); else empty |
| Ernte badge / prose | `harvest_season` | „first flush", „Frühlingsernte", „Erste Pflückung", „Ichibancha", „Shincha" → `FIRST_FLUSH`; „second flush", „Nibancha" → `SECOND_FLUSH`; „Sommerernte", „Sommer" → `SUMMER`; „Herbst" → `AUTUMN`; a badge naming none → `UNKNOWN`; without a badge only the prose's flush words count (not a bare „Sommer"/„Herbst"), else empty (D-085). Never taken from *Saison* (that is when to drink the tea). |
| Beschattung badge / prose | `shading_days` | Number + „Tage" → n; + „Wochen" → 7n; ranges → midpoint, rounded half up (e.g. prose „für 6 Tage beschattet", only a sentence about shading); badge without number or more than 60 days → empty |
| Dämpfung badge | `processing_notes` | Copied as text (e.g. „Chumushi") |
| Ingredients | `ingredients` | Copied |
| *Anbaugebiete* | `origin_country` | ISO 3166-1 code of the first country name the app knows (Japan → `JP`, China → `CN`, Taiwan → `TW`, (Süd-)Korea → `KR`, …), else empty (D-085) |

### 2.4 Discovery, variants and request budget

1. Fetch `robots.txt` and respect it; fetch `sitemap.xml` and its `.xml.gz`.
2. From product URLs (`/<slug>/<article number>`), derive base numbers;
   group variant URLs per base number. Skip `…SET`. Per base number the
   parent's own page is fetched, else its `M` page, else the smallest size
   (D-077). A sitemap that is missing, disallowed, broken or lists no product
   is a structural failure.
3. **First refresh:** fetch the parent page of every base number not yet
   classified, read the breadcrumb, and keep products under
   `/tee/gruener-tee/`, `/tee/japantee/` (green types only) or
   `/matcha/matcha-teepulver/`. A product of `/tee/japantee/` without a type
   level (a tea family's page `/tee/japantee/teefamilien/<family>/` or a tea
   garden's, live sample 2) is kept when its name names a green type (or
   tencha, aracha, „Grüntee", …) or it carries a steaming or shading badge,
   and neither its name nor its path names another tea (Koucha/Wakoucha,
   Oolong, Schwarz-, Weiß- or Kräutertee) or teaware (Kyusu, Kanne, Chawan,
   Set, …); otherwise it is skipped (D-089). Store non-green base numbers in a skip list
   (`catalog_skip`) so later refreshes don't fetch them again; the skip list
   changes only with a successful refresh (D-077). A page without a
   breadcrumb is neither kept nor skipped.
4. Exclude products whose ingredients contain no tea at all (e.g. „Ingwer
   Shot" in the flavoured section).
5. **Later refreshes:** fetch known green parents plus base numbers new in the
   sitemap; products missing from the sitemap → discontinued. The known green
   parents are the catalog's keys and the base numbers merged into them
   (item 7), read from their packages' article numbers; skip entries due
   again (90 days) are fetched too. A known tea whose page can't be read this
   time (robots.txt, 404, a page without product) but whose numbers are still
   in the sitemap is kept as it was; one that is now out of scope or missing
   from the sitemap is discontinued (CAT-07 AC4). Only new and changed photos
   are fetched (D-086).
6. **Package prices:** variant sizes come from the configurator labels and
   variant URLs. Sizes **larger** than the parent package are **estimated**
   without extra requests: `price(size) = parent price ÷ parent grams × size ×
   (1 − savings %)`, rounded to the cent, marked as estimate (verified: within
   ±0,05 € of the real prices). Sizes **smaller** than the parent (e.g. 10 g
   samples) carry a surcharge and are **not estimated** (Hisui 10 g = 2,80 € =
   280 €/kg vs. 179 €/kg) — shown as „Preis beim Hinzufügen". The exact price
   of any size is fetched from its variant URL when the user adds that package
   (CAT-12, network use (c) of GEN-01, online only; offline the estimate or
   an empty price is used and editable). The refresh never fetches a size's
   page (D-076): the sizes are the union of the configurator's options and
   the sitemap's `<base>-<grams>` URLs; the fetched page's own size carries
   its price and stock, the other sizes no stock (unknown). Each size keeps
   its own page as `variant_url` (the sitemap URL; the product page for the
   page's own size; empty for a size only the configurator names) (D-081).
7. Teas split into separate base numbers per size but with the same name are
   merged by normalised name (lower case, without sizes such as „30g" or
   „-100g" in the name or slug, without punctuation). The merged entry's `key`
   is deterministic: the **lowest** base number (string order, e.g. `AT0868`
   for `AT0868`/`AT0869`); its page gives the details. Each page's own size
   becomes a package at that page's price, which wins over another page's
   estimate for the same size; the tea is in stock when any of its pages is.
   Two pages with the same name **and** the same own size are two teas (e.g.
   two harvests) and are not merged (D-081).
8. New harvests usually get new article numbers; the old one becomes
   discontinued.

Request budget at the default rate limit (2 requests/s, all requests
including images):

| Refresh | Requests (approx.) | Duration |
|---|---|---|
| First | sitemap + ~1,560 parent pages for classification (all shop categories, counted live on 2026-10-25, D-076) + ~280 photos + guides (G = number of keyword-selected guide slugs, §2.7, ~10–40 and at most 40, plus their images, at most 4 each: ≤ 200 requests, ≈ 100 s, D-087) | ~15–18 min |
| Later | sitemap + ~280 green pages + new items + changed photos + G ≤ 40 guide pages and only new or changed guide images (≈ 0–40; an image already stored is not fetched again, D-087) | ~3–4 min |

### 2.5 Recipe derivation

`RecipeDeriver` turns the brewing grid and prose into timer recipes
(CAT-04).

**Step 1 — Rows to recipes.** Each grid row becomes one recipe:
`st-zubereitung-japan` → „Kyusu" (`KYUSU`), or for matcha „Usucha"
(`MATCHA_USUCHA`);
`st-zubereitung-westlich` → „Westlich" (`WESTERN`);
`st-zubereitung-china` → „Gongfu" (`GONGFU`, D-089). The **default recipe** is
the Kyusu row for Japanese teas when present, otherwise the row of the style's
default method (Gongfu for `longjing`, `bi_luo_chun`, `anji_bai_cha`, D-089),
otherwise the first row. A row whose temperature is „kalt" is no hot recipe
but the cold brew's row (step 6, D-089).

**Step 2 — Parse cells.** Patterns observed: amounts `~4g/100ml`,
`~4g/200ml`, `~10g/L`, `2-2,5g/50ml`; temperatures `50-55°C`, `~65°C`,
`~65-75°C`, `~80°` (the C missing); times `~90 Sek`, `60 Sek`, `~1 Min`,
`~20(30) Sek` (the first value counts); bags `1Stk./500ml`; `-` = not given.
A leading `~` is ignored. A bag weighs the package's grams over the bags the
name counts („– 20 Aufgussbeutel", 100 g → 5 g); without them a bag amount
gives only the water (D-089).

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

**Step 6 — Cold brew.** If the „Coldbrew geeignet" badge is present, the
grid has a row at „kalt" or the prose describes a Kaltaufguss, add a recipe
„Kaltaufguss" (`COLD_BREW`): values from the „kalt" row (amount, every given
infusion), else from the prose when given (e.g. „Ziehzeit … 20 - 30 Minuten …
Dosierung verdoppeln" → 25 min, double leaf), otherwise the style's
cold-brew default; styles without one use the sencha cold-brew default
(D-076); bags of unknown weight brew that ratio at their stated water,
flagged as style default. A powder (matcha, hōjicha powder) gets **no** cold
brew, badge or not: it is shaken cold, never steeped for hours, and §3's
matcha has none (D-089). Temperature 5 °C („kalt"). **„Dosierung verdoppeln"**
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

**Details fixed while building it (D-082):**
- A recipe's `source` is its least certain field: style default before text
  before table. The PO's second western infusion and the matcha whisk time
  don't count (they are rules, not data).
- Grid columns are read by their header label (Menge, Temperatur, n. Aufguss),
  else by position. A matcha's rows become one *Usucha* recipe.
- The prose is split into paragraphs: those describing a Kaltaufguss
  (Kaltaufguss, kaltes Wasser, Cold Brew, Kühlschrank) give only the cold
  brew's values; the others the hot recipes' (the first amount per volume,
  temperature below boiling, else boiling, and time; a time in hours is no hot
  infusion). Paragraphs are separated by blank lines. Teaspoons use the
  style's g/TL. Without a grid, the style's default recipe is derived with the
  prose's values and the style's other hot recipes follow unchanged.
- When the first infusion comes from the style, all steps do; follow-ups from
  the style keep the style's rise over its base temperature (sencha +5 / +10 °C).
  Temperatures above 100 °C are capped.
- A per-litre amount („10 g auf 1 Liter") in the cold-brew prose brews 1000 ml.
  Cold-brew times outside 1 min – 24 h fall back to the style's.
- Times snap to 5 s below 10 min and to whole minutes above (PRF-04); water to
  5 ml (10–3000 ml); leaf to 0,1 g, half up.
- A conflict needs the prose to differ from the default's grid value **and**
  from every other row's (prose matching the western row contradicts nothing).
  The amount is compared as g per 100 ml.
- One reason per tea, the first that applies: `IMPLAUSIBLE_VALUE`,
  `TABLE_TEXT_CONFLICT`, `FROM_STYLE_DEFAULT`, `FROM_TEXT`, `UNMAPPED_STYLE`.
- *Japanese* (Kyusu default): a Japanese style, a style named by a Japanese
  keyword in the name (a flavoured sencha), the Japan section, or *Anbaugebiete*
  Japan.

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

A seed that fails on the first start is retried at every later app start
until it succeeds (D-075); that retry never sends the photo download, so
those covers come with the first catalog refresh (CAT-15).

Recipes are scaled from the shop's ratios (4 g/200 ml, 10 g/L) to the PO's
seed vessels (WARE-06). Purchase date of the seed stock = first app start;
`opened_date` is set by the first session (INV-06).

### 2.6 Style mapping and umami level

Style from the breadcrumb's last segment, else name keywords (the name and the *Sorte*; the more specific first, e.g. „Gyokuro Karigane" → `karigane`, „Genmaicha mit Matcha" → `genmaicha`; D-082):

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
| `mizudashi-kaltaufgussbeutel`, or „Mizudashi" / „Kaltaufgussbeutel" in the name under any breadcrumb (D-089) | style from the breadcrumb or name tokens (e.g. Sencha) else `green_generic` (not flagged); form TEA_BAG; default recipe = cold brew |
| `aromatisierter-gruentee` | `flavored_green` |
| China/Taiwan/andere-regionen + name „Long Jing"/„Lung Ching" / „Bi Luo Chun", „Bi Lo Chun", „Pi Lo Chun" / „Anji" / a Korean grade („Ujeon", „Woojeon", „Sejak", „Jungjak", „Joongjak", „Daejak") | `longjing` / `bi_luo_chun` / `anji_bai_cha` / `korean_green` (D-089) |
| *Anbaugebiete* Korea, no keyword | `korean_green` (D-089) |
| tea bags („Aufgussbeutel", „Teebeutel") | the style as above, form TEA_BAG (D-089) |
| a flavoured tea no keyword names | `flavored_green` (not flagged, D-082) |
| anything else | `green_generic` + flag `UNMAPPED_STYLE` |

**Umami level** from the shop's Umami scale when present: 0 → `NONE`, 1 →
`LOW`, 2 → `MEDIUM`, 3 → `HIGH`, 4–5 → `VERY_HIGH`; otherwise the style's
level. All shop scales (Umami, Bitterstoffe, …) are shown on the catalog
detail page.

### 2.7 Tee-Info guides

The blog (`/tee-info/`, canonical `/blog`) shows only category tiles; articles
are discovered from the sitemap (`/tee-info/<slug>`, ~300). Articles are
selected by slug keywords (japantee, gruentee, matcha, sencha, gyokuro,
kabuse, shincha, houjicha, genmaicha, kaltaufguss, kyusu, umami, and since
live sample 2 aracha, mizudashi, mizucha, fukamushi, kultivar, bancha, kukicha,
tamaryokucha, kamairicha, tencha) and fetched individually. Promotions, events
and recipes are no guides: slugs starting with `sonstiges-`,
`teeverkostungen-`, `aktionstees-`, `blog-` or `teerezepte-`, and slugs with a
word starting with rezept, plaetzchen, kekse, kuchen, torte, muffin, cookie,
spritz, cocktail, smoothie, delight, dessert, kochbuch, verkostung, tasting,
event, aktion or gewinnspiel, are left out (D-089). Article body: `.pixel-blog-detail .cms-element-text` (h1
inside; gallery `.st-blog-gallery`); JSON-LD `BlogPosting` provides title,
date and image.

Confirmed relevant articles: `ein-guide-fuer-japantee`,
`matcha-verstehen-ein-guide-fuer-teefans`, `a-guide-to-sencha`,
`kabuse-beschattung-von-japanischem-gruentee`,
`teefibel-zubereitung-der-kaltaufguss-fahrplan-wie-geht-eigentlich-die-teezubereitung-als-kaltaufguss`.

Conversion (D-087): the app's own Markdown subset (headings, paragraphs
with line breaks, bold, italics, absolute http(s) links, lists, tables as
lists of rows, images), sanitised as in §2.8; a bold first line of a
paragraph becomes a heading; the body doesn't repeat the hero. Left out
as well (D-089): the comment section inside the article block
(`.pixel-blog-questions…`), the blog's date list (a list of dates only), a
paragraph introducing a video that isn't there (it names a video, ends with a
colon, has no link and no player link follows) and a closing tag line (two to
six plain words without punctuation). Title, date
and hero image from the JSON-LD `BlogPosting` (else the article's `h1`, no
date, no hero). At most 40 articles per refresh, the most relevant kept
(most keywords in the slug, then the shorter slug). An article that is gone is
left out, one that no longer reads keeps its stored version, and without any
selected article in the sitemap every stored guide is kept.

Images (the gallery's `data-full-image`, else `data-src`/`src`; lazyload
placeholders and tracking pixels skipped; each once, told apart by their
decoded path without the query, so the gallery's raw spaces and the body's
`%20` are one image, stored with spaces encoded, D-089) are downloaded through
the refresh's session only while SET-22 is on, at most 4 per article (the
hero first), and
stored as ≤ 1200 px WebP named after their URL's hash, so an unchanged image
is never fetched again. Links to product pages of the shop whose tea is in
the new catalog become in-app catalog links, links to stored guides in-app
guide links; every other link opens the browser. The guides are staged and
swapped with the teas (05 §4.5), so a failed refresh keeps the old ones.

### 2.8 Sanitising and robustness

- Strip scripts, styles, iframes, forms, tracking pixels, images and embeds
  (YouTube → plain link) and the staff tip blocks; keep basic formatting only:
  paragraphs, line breaks, bold, italics, lists and absolute http(s) links
  without attributes but `href`; headings become bold paragraphs (D-078).
  Control characters (C0 and C1 but tab and line breaks) become spaces in
  every text read from a page (D-082).
- **Cloudflare:** a response is a challenge if it has the header
  `cf-mitigated: challenge`, or (HTTP 403/503) its body contains challenge
  markers (`cdn-cgi/challenge-platform`, `_cf_chl_opt`, „Just a moment…").
  The refresh then stops immediately, keeps the old catalog, and shows „Der
  Shop hat den Abruf vorübergehend blockiert – bitte später erneut
  versuchen." No attempt is made to bypass challenges.
- **Rate signals:** HTTP 429 or any `Retry-After` header also stops the
  refresh (old catalog kept, same message); no automatic waiting and retry.
- **Refusals and outages:** a 401/403/5xx or a network error (offline; for
  the refresh after its retries, D-076) also stops the whole job; after any stop the job sends no further request. A
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
count as valid. Pages that gave no product or no breadcrumb count among the
products and as lacking name, price and article number; the brewing
threshold applies since the parser reads the brewing information: a green
tea lacks it when it has neither a grid row nor brewing prose (D-077, D-082).

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
take precedence for catalog teas. The green rows are code in `core:domain`
(`BuiltInStyles`, D-076, D-082); the rows of other categories follow with the
collection editor (M3).

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
