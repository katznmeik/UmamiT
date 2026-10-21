# 08 · Content & Catalog Import

> Status: **Draft v0.2** · Last updated: 2026-10-21
> v0.2: Tee-Kontor Kiel is the primary content source; built-in styles become
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

The structure below comes from a desk survey via search-engine results on
2026-10-21. The shop could not be opened directly because the cloud
environment's network policy blocks `www.tee-kontor-kiel.de`. **Before the
parser is implemented, the domain must be allowed** and this section must be
verified against the live pages (task V-01 in [09](09-release-plan.md)).

### 2.2 Source structure

| Aspect | Observation (to verify) |
|---|---|
| Shop platform | Most likely Shopware 5 (product URLs `/<slug>/<articleNumber>`, legacy `/detail/index/sArticle/<id>`) |
| Green tea root | `/tee/gruener-tee/` |
| Sub-sections | `japan/` (with `alle-japantees-nach-sorten/<sorte>/` for Matcha, Shincha, Sencha, Kabusecha, Gyokuro, Kukicha, Bancha, Houjicha, Kamairicha, Tamaryokucha, Genmaicha), `china/`, `china-taiwan/`, Korea/other, `aromatisierter-gruentee/`, `bio/`, `klassischer-gruentee/`, Shincha (seasonal) |
| Matcha | `/matcha/matcha-teepulver/` (also a top-level `/matcha/` section) |
| Excluded | `/tee/multipacks-sets-zum-probieren/…` (sets), accessories |
| Product IDs | Article numbers like `TG0557`, `AT0634`; variants with suffixes (`TG0028-10`, `TG0028-50`, `TG0557M` for sample) |
| Product page data | Name (sometimes with Japanese gloss), properties list (*Anbauland*, *Region*, *Kultivar*, *Ernte/Pflückung*, *Beschattung*, *Dämpfung*, *Bio*, *Hersteller*), description prose, a „Zubereitungsempfehlung" table (Menge, Temperatur, 1./2./3. Aufguss) plus prose, variants with price, base price per kg/100 g, images |
| Discovery | Crawl section listings with pagination (and/or `sitemap.xml` filtered to the sections above); de-duplicate by base article number |

Survey examples (paraphrased; to verify):

| Tea | Brewing info found |
|---|---|
| Bio Uji Gyokuro Gokō (`AT0634`) | ~4 g / 100 ml; 50–55 °C; 1st ~90 s, 2nd ~30 s, 3rd ~20 s; cold brew 20–30 min, double dose |
| Bio Sencha Hisui (`TG0557`) | ~4 g / 200 ml; 60–65 °C; 1st ~60 s, 2nd/3rd ~10 s; cold brew 20–30 min, double dose |
| Westlake Xi Hu Long Jing (`TG0566`) | ~3 g / 150 ml; ~80 °C; 1st ~60 s, then ~20 s; gaiwan |
| Bio Premium Matcha Horai (`AT0220a`) | Text: 1–2 g with 80–100 ml at ~80 °C, whisk; table: ~1 g / 100 ml at ~70 °C (conflict) |

### 2.3 Field mapping

| Catalog field ([05 §4.1](05-data-model.md#41-catalogtea)) | Source |
|---|---|
| `key` | Base article number (strip `-<n>`, trailing `M`, bulk suffixes) |
| `name` / `secondary_name` | Product title; a Japanese/Chinese term or gloss in the title/properties becomes the secondary name where identifiable (e.g. „玉露" added via style if absent) |
| `tea_type` | *Sorte* from the category path (`…/nach-sorten/<sorte>/`) or properties; else from name keywords |
| `origin_*`, `cultivar`, `harvest`, `shading`, `steaming` | Properties list |
| `is_organic` | „Bio" in title/properties/category |
| `is_flavored`, `ingredients` | Category *Aromatisierter Grüntee* or an ingredients list („Zutaten") |
| `description_html` | Description section, sanitised (§2.8) |
| `brew_table_raw`, `brew_text_raw` | „Zubereitungsempfehlung" table and accompanying prose |
| `catalog_package` rows | Variant selector: label, grams, price, availability |
| `base_price_cents_per_100g` | Shop base price, else computed from the cheapest regular package |
| `photo_url` | Main product image (largest reasonable size) |

### 2.4 Variant merging

All article numbers sharing a base number form one tea. Package rows
include samples („Probe") and bulk sizes. If two variants have different
names (e.g. a different harvest year), the newest name wins and the year is
kept in `harvest`.

### 2.5 Recipe derivation

`RecipeDeriver` converts brewing info to timer recipes (CAT-04).

**Step 1 — Extract** from the table and, separately, from the prose:
- Amount: patterns like „4 g / 100 ml", „2–2,5 g auf 50 ml", „10–12 g/l",
  „1 TL" (teaspoons converted with the style's internal grams-per-teaspoon),
  „1–2 Bambuslöffel" (≈ 0,5 g each, matcha).
- Temperature: „50–55 °C", „ca. 80 °C", „abgekocht und auf etwa 80 °C
  abgekühlt".
- Infusions: „1. Aufguss 90 Sek.", „2. Aufguss 30 s", „1–2 Min", optional
  per-infusion temperatures („2. und 3. Aufguss 60 °C").
- Cold brew: „Kaltaufguss", „kalt", „Eis" with duration and dose hint
  („Dosierung verdoppeln").
- Vessel hints: „Kyusu", „Gaiwan", „Kanne", „Glas", „Chasen".

**Step 2 — Choose per field:** table value → else prose value → else
built-in style default. Then flag:
- any field from prose → `FROM_TEXT`,
- any field from style default → `FROM_STYLE_DEFAULT` (except matcha whisk
  duration, which the shop never states),
- table and prose both present but differing (temperature > 5 °C, time >
  max(10 s, 20 %), amount > 20 %) → `TABLE_TEXT_CONFLICT` (table still wins).

**Step 3 — Normalise:**
- Ranges → midpoint.
- Temperature → `snap5` (half up; 52,5 → 55 °C).
- Times → nearest 5 s (half up).
- Amount → recipe `water_ml` = the stated volume (e.g. 100 ml); per-litre
  statements use the style's default water volume and scale the leaf; leaf
  rounded to 0,1 g.

**Step 4 — Build steps:**
- One INFUSION per stated infusion with its temperature (or base
  temperature).
- If only the first infusion is stated and the style is multi-infusion
  (KYUSU/GONGFU), append the style's follow-up infusions (flag
  `FROM_STYLE_DEFAULT`).
- Matcha: one WHISK step (20 s usucha); koicha only if stated.

**Step 5 — Method:** Matcha → `MATCHA_USUCHA`; „Gaiwan" → `GONGFU`; Japanese
types or „Kyusu" → `KYUSU`; ratio ≥ 3 g/100 ml with first infusion ≤ 90 s →
`GONGFU` for non-Japanese teas; else `WESTERN`.

**Step 6 — Cold brew:** if prose mentions cold brewing, add a second recipe
„Kaltaufguss" (`COLD_BREW`): leaf = hot ratio × (2 if „verdoppeln", else 1),
same water, 5 °C, one INFUSION with the midpoint duration (e.g. 20–30 min →
25 min).

**Step 7 — Plausibility:** hot temperature 40–100 °C; first hot infusion
5 s–10 min; ratio 0,5–15 g/100 ml. Out-of-range values are replaced by style
defaults and flagged `IMPLAUSIBLE_VALUE`.

**Worked examples (from survey data):**

| Tea | Derived recipe | Flag |
|---|---|---|
| Bio Uji Gyokuro Gokō | KYUSU 4,0 g / 100 ml / 55 °C: 1:30, 0:30, 0:20 · Kaltaufguss 8,0 g / 100 ml / 5 °C: 25:00 | `FROM_TEXT` (cold-brew recipe from prose) |
| Bio Sencha Hisui | KYUSU 4,0 g / 200 ml / 65 °C: 1:00, 0:10, 0:10 · Kaltaufguss 8,0 g / 200 ml: 25:00 | `FROM_TEXT` (cold brew) |
| Xi Hu Long Jing | GONGFU 3,0 g / 150 ml / 80 °C: 1:00, 0:20, 0:20 | depends on table |
| Matcha Horai | MATCHA_USUCHA 1,0 g / 100 ml / 70 °C: whisk 0:20 | `TABLE_TEXT_CONFLICT` |

### 2.6 Style mapping

| Shop *Sorte* / keyword | Style key |
|---|---|
| Gyokuro | `gyokuro` |
| Kabusecha | `kabusecha` |
| Sencha + „Fukamushi" | `fukamushi_sencha` |
| Sencha | `sencha` |
| Shincha | `shincha` |
| Matcha | `matcha` |
| Kukicha, Karigane | `karigane` |
| Bancha | `bancha` |
| Houjicha, Hōjicha, Hojicha | `hojicha` |
| Genmaicha | `genmaicha` |
| Kamairicha | `kamairicha` |
| Tamaryokucha | `tamaryokucha` |
| Long Jing, Lung Ching | `longjing` |
| Bi Luo Chun, Pi Lo Chun | `bi_luo_chun` |
| Anji Bai Cha | `anji_bai_cha` |
| Sejak, Ujeon, Korea | `korean_green` |
| Aromatisierter Grüntee / flavoured | `flavored_green` |
| Teepulver other than matcha | `green_generic` (form POWDER) |
| anything else | `green_generic` + flag `UNMAPPED_STYLE` |

### 2.7 Tee-Info guides

Discover articles under `/tee-info/` whose title or body matches green-tea
keywords (Grüntee, Japantee, Matcha, Sencha, Gyokuro, Kyusu, Umami,
Kaltaufguss). Known examples: „Ein Guide für Japantee", „Matcha-Eistee".
Convert to Markdown (headings, paragraphs, lists, tables, images
downloaded locally); rewrite links to catalog products as in-app links.

### 2.8 Sanitising

Strip scripts, styles, iframes, forms, tracking pixels and remote embeds
(e.g. YouTube → plain link). Keep basic formatting only.

### 2.9 Sanity thresholds (CAT-07 AC5)

Abort the refresh without changes if: fewer than 50 % of the previous
product count (or < 30 on first import) are parsed; or > 30 % of products
lack a name, price or article number; or every product lacks a brewing table
**and** brewing text (selector broke).

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

- Greetings: „Guter Tee schenkt einen Moment der Ruhe." · „Eine stille
  Tasse wartet." · „Atme durch. Wärm die Kanne vor." · Morgen: „Guten
  Morgen – frische Blätter, frischer Start." · Abend: „Etwas Sanftes für den
  Abend?"
- Empty states: Meine Tees „Noch keine Tees – stöbere im Katalog." ·
  Journal „Deine erste Tasse wartet." · Katalog „Der Katalog ist noch leer." ·
  Suche „Nichts gefunden – weniger Filter probieren."
