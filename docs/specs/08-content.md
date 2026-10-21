# 08 · Content Specification

> Status: **Draft v0.1** · Last updated: 2026-10-21
>
> ⚠️ **Content review required.** All brewing parameters, freshness windows and
> guide outlines below are well-established *starting points* compiled for this
> spec. Before release they must be reviewed by an experienced tea professional
> (open question Q-06). The app always presents them as suggestions.

## 1. Content principles

- **Starting points, not rules.** Every parameter is editable; guides explain
  *why* so users can adjust confidently.
- **Accurate and sourced.** Each guide keeps a list of sources in the content
  repository (not necessarily shown in-app).
- **No health claims.** Do not claim effects of caffeine, L-theanine,
  catechins, etc. on health, mood or focus (NFR-POL-03). Chemistry is described
  only in terms of *taste* and *brewing*.
- **Respectful use of Japanese and Chinese terms** with romanisation, native
  script, and a one-line explanation the first time.
- **Calm tone** consistent with [03 §3.8](03-ux-ui.md#38-voice--tone).

## 2. Tea style catalog

### 2.1 Style record schema

`assets/content/styles.json`:

```json
{
  "contentCatalogVersion": 1,
  "styles": [
    {
      "key": "gyokuro",
      "name": "Gyokuro",
      "secondaryName": "玉露",
      "category": "GREEN",
      "form": "LOOSE_LEAF",
      "origin": "JP",
      "umamiLevel": "VERY_HIGH",
      "freshnessDays": 60,
      "gramsPerTsp": 3.0,
      "image": "style_gyokuro",
      "descriptionRes": "style_gyokuro_description",
      "brewingNotesRes": "style_gyokuro_notes",
      "relatedGuides": ["umami", "shade-grown", "temperature"],
      "profiles": [
        {
          "name": "Houhin / kyusu",
          "method": "KYUSU",
          "leafGrams": 6.0,
          "waterMl": 60,
          "baseTempC": 50,
          "steps": [
            { "kind": "INFUSION", "seconds": 120 },
            { "kind": "INFUSION", "seconds": 20, "tempC": 55 },
            { "kind": "INFUSION", "seconds": 60, "tempC": 65 },
            { "kind": "INFUSION", "seconds": 90, "tempC": 75 }
          ]
        }
      ]
    }
  ]
}
```

Localised texts are string resources referenced by key; names of styles are
proper nouns and usually not translated.

### 2.2 Styles and default profiles

Notation for steps: `2:00` = 2 min; `@55` = temperature override in °C;
`R` = rinse; `W` = whisk. First profile listed is the default.
Freshness = suggested days after opening. g/tsp = approximate grams per level
teaspoon.

#### Japanese green teas (category GREEN, origin JP)

| Key | Name | Umami | Fresh. | g/tsp | Profiles |
|---|---|---|---|---|---|
| `gyokuro` | Gyokuro 玉露 — shaded ~3 weeks; intensely savoury, sweet, brothy | VERY_HIGH | 60 | 3.0 | **Houhin/kyusu** KYUSU 6 g / 60 ml / 50 °C: 2:00, 0:20 @55, 1:00 @65, 1:30 @75 · **Ice brew** ICE_BREW 6 g / 100 ml ice / 0 °C: 2:00:00 (note: cover leaves with ice; ready when melted, typically 1.5–3 h) · **Cold brew** COLD_BREW 10 g / 500 ml / 5 °C: 3:00:00 |
| `kabusecha` | Kabusecha かぶせ茶 — shaded ~1–2 weeks; between sencha and gyokuro | HIGH | 45 | 2.5 | **Kyusu** KYUSU 5 g / 120 ml / 65 °C: 1:30, 0:20 @70, 1:00 @80 · **Cold brew** 10 g / 500 ml / 5 °C: 3:00:00 |
| `sencha` | Sencha 煎茶 — steamed, unshaded; fresh, vegetal, balanced | MEDIUM | 45 | 2.5 | **Kyusu** KYUSU 5 g / 150 ml / 75 °C: 1:00, 0:15 @80, 0:45 @85, 1:00 @90 · **Mug** WESTERN 3 g / 250 ml / 75 °C: 1:30, 2:00 @80 · **Cold brew** 10 g / 1000 ml / 5 °C: 4:00:00 |
| `fukamushi_sencha` | Fukamushi sencha 深蒸し煎茶 — deep-steamed; rich, cloudy, sweet | MEDIUM | 45 | 3.0 | **Kyusu** KYUSU 5 g / 150 ml / 70 °C: 0:45, 0:05 @75, 0:30 @80 (note: fine-mesh kyusu recommended) |
| `shincha` | Shincha 新茶 — first spring harvest, sold fresh | HIGH | 30 | 2.5 | **Kyusu** KYUSU 5 g / 150 ml / 70 °C: 1:00, 0:15 @75, 0:45 @80 |
| `matcha` | Matcha 抹茶 — stone-ground shaded tencha | HIGH | 30 | 2.0 | **Usucha** MATCHA_USUCHA 2 g / 70 ml / 75 °C: W 0:20 (note: sift first; whisk in M/W motion) · **Koicha** MATCHA_KOICHA 4 g / 35 ml / 75 °C: W 0:40 (note: knead slowly, no froth) — form POWDER |
| `karigane` | Kukicha / Karigane 茎茶・雁ヶ音 — stems; sweet, light, savoury (gyokuro karigane especially) | MEDIUM | 45 | 1.5 | **Kyusu** KYUSU 5 g / 150 ml / 70 °C: 1:00, 0:20 @75, 1:00 @85 |
| `tamaryokucha` | Tamaryokucha 玉緑茶 — curly leaf (Kyushu); mellow, sweet | MEDIUM | 45 | 2.5 | **Kyusu** KYUSU 5 g / 150 ml / 75 °C: 1:00, 0:15 @80, 0:45 @85 |
| `genmaicha` | Genmaicha 玄米茶 — green tea with toasted rice | LOW | 45 | 3.0 | **Kyusu** KYUSU 5 g / 200 ml / 85 °C: 0:45, 0:15 @90, 1:00 @95 · **Mug** WESTERN 3 g / 250 ml / 85 °C: 2:00 |
| `hojicha` | Hōjicha ほうじ茶 — roasted; toasty, low bitterness | LOW | 60 | 1.5 | **Kyusu** KYUSU 4 g / 200 ml / 95 °C: 0:30, 0:30, 1:00 · **Mug** WESTERN 3 g / 250 ml / 95 °C: 2:00 |

#### Other green teas

| Key | Name | Umami | Fresh. | g/tsp | Profiles |
|---|---|---|---|---|---|
| `longjing` | Longjing (Dragon Well) 龙井 — CN pan-fired; chestnut, savoury | MEDIUM | 90 | 1.5 | **Gongfu** GONGFU 5 g / 100 ml / 80 °C: 0:20, 0:25, 0:30, 0:40, 1:00 · **Western** 3 g / 250 ml / 80 °C: 2:00, 2:30, 3:00 |
| `anji_bai_cha` | Anji Bai Cha 安吉白茶 — CN green from a pale-leaf cultivar; notably high in amino acids, brothy-sweet | HIGH | 90 | 1.0 | **Gongfu** 4 g / 100 ml / 80 °C: 0:20, 0:25, 0:30, 0:40, 1:00 · **Western** 3 g / 250 ml / 80 °C: 2:00, 2:30 |
| `bi_luo_chun` | Bi Luo Chun 碧螺春 — CN; fruity-floral, delicate | MEDIUM | 90 | 2.0 | **Gongfu** 4 g / 100 ml / 75 °C: 0:15, 0:20, 0:25, 0:35, 0:50 · **Western** 2.5 g / 250 ml / 75 °C: 2:00 |
| `korean_green` | Korean green (Ujeon / Sejak) 우전·세작 — KR; nutty, savoury, sweet | HIGH | 60 | 2.0 | **Kyusu** KYUSU 4 g / 120 ml / 70 °C: 1:00, 0:30 @75, 1:00 @80 |
| `green_generic` | Green tea (other) | MEDIUM | 90 | 2.5 | **Western** 3 g / 250 ml / 80 °C: 2:00, 2:30 · **Gongfu** 5 g / 100 ml / 80 °C: 0:15, 0:20, 0:25, 0:35, 0:50 |

#### White, yellow, oolong, black, dark, herbal

| Key | Name | Cat. | Umami | Fresh. | g/tsp | Profiles |
|---|---|---|---|---|---|---|
| `silver_needle` | Silver Needle 白毫银针 | WHITE | LOW | 365 | 1.0 | **Western** 3 g / 250 ml / 85 °C: 4:00, 5:00 · **Gongfu** 5 g / 100 ml / 85 °C: 0:30, 0:30, 0:40, 0:50, 1:00, 1:30 |
| `white_peony` | White Peony (Bai Mu Dan) 白牡丹 | WHITE | LOW | 365 | 0.8 | **Western** 3 g / 250 ml / 90 °C: 3:00, 4:00 · **Gongfu** 5 g / 100 ml / 90 °C: 0:20, 0:25, 0:30, 0:40, 1:00 |
| `yellow_generic` | Yellow tea 黄茶 | YELLOW | LOW | 180 | 2.0 | **Western** 3 g / 250 ml / 80 °C: 2:30, 3:00 · **Gongfu** 5 g / 100 ml / 80 °C: 0:20, 0:25, 0:30, 0:40 |
| `rolled_oolong` | Rolled oolong (e.g. high-mountain, Tieguanyin) | OOLONG | LOW | 180 | 3.5 | **Gongfu** 6 g / 100 ml / 95 °C: R 0:05, 0:45, 0:30, 0:40, 0:50, 1:00, 1:30 · **Western** 3 g / 250 ml / 95 °C: 3:00, 4:00 |
| `strip_oolong` | Strip oolong (e.g. Wuyi yancha, Dancong) | OOLONG | NONE | 365 | 1.5 | **Gongfu** 6 g / 100 ml / 98 °C: R 0:05, 0:10, 0:15, 0:20, 0:25, 0:35, 0:50, 1:10 · **Western** 3 g / 250 ml / 95 °C: 3:00 |
| `black_generic` | Black tea (e.g. Assam, Ceylon, Darjeeling, wakōcha) | BLACK | LOW | 365 | 2.5 | **Western** 2.5 g / 250 ml / 95 °C: 4:00 · **Gongfu** 5 g / 100 ml / 95 °C: 0:10, 0:15, 0:20, 0:25, 0:35, 0:50 (note: Darjeeling first flush 85–90 °C) |
| `sheng_puerh` | Sheng (raw) pu-erh 生普洱 | DARK | LOW | — | 2.5 | **Gongfu** 6 g / 100 ml / 95 °C: R 0:05, 0:10, 0:10, 0:15, 0:20, 0:25, 0:35, 0:50, 1:10 |
| `shou_puerh` | Shou (ripe) pu-erh 熟普洱 | DARK | NONE | — | 2.5 | **Gongfu** 6 g / 100 ml / 100 °C: R 0:10, 0:10, 0:10, 0:15, 0:20, 0:25, 0:35, 0:50 · **Western** 2.5 g / 250 ml / 100 °C: 4:00 |
| `herbal` | Herbal infusion (tisane) | HERBAL | NONE | 180 | 1.5 | **Western** 2 g / 250 ml / 100 °C: 5:00 |
| `other` | Other / not sure | OTHER | — | — | 2.5 | **Western** 3 g / 250 ml / 85 °C: 3:00 (PRF-02) |

`—` freshness = no freshness indicator (e.g. pu-erh is often aged on purpose).

### 2.3 Style descriptions (draft copy, en)

Each style needs ~40–70 words of description and ~30–60 words of brewing notes.
Example:

> **Gyokuro** — Japan's most prized leaf tea. For about three weeks before
> harvest the bushes are shaded from the sun, which keeps the leaves rich in
> the amino acids responsible for its savoury-sweet, brothy taste. Brewed
> concentrated and cool, the first cup is small, thick and intensely umami.
>
> *Brewing notes:* Use plenty of leaf, little water and low temperature
> (around 50 °C). Pour out every last drop so the leaves don't keep steeping.
> Later infusions can be hotter and shorter.

Writing the remaining 24 descriptions is a content task in milestone M4
([09](09-release-plan.md)).

## 3. Tasting descriptor vocabulary

Built-in keys (`snake_case`) grouped by `DescriptorGroup`. Labels shown in
English; translations via string resources.

| Group | Descriptors |
|---|---|
| MARINE | `seaweed` Seaweed, `nori` Nori, `kombu` Kombu, `marine` Marine / ocean breeze, `dashi` Dashi / brothy |
| VEGETAL | `spinach` Spinach, `snap_pea` Snap pea, `edamame` Edamame, `asparagus` Asparagus, `steamed_greens` Steamed greens, `fresh_grass` Fresh-cut grass, `hay` Hay, `artichoke` Artichoke |
| SWEET | `sweet_corn` Sweet corn, `chestnut` Chestnut, `honey` Honey, `caramel` Caramel, `cane_sugar` Cane sugar, `sweet_potato` Sweet potato, `vanilla` Vanilla |
| ROASTED_NUTTY | `toasted_rice` Toasted rice, `roasted_nuts` Roasted nuts, `toast` Toast, `cocoa` Cocoa, `coffee` Coffee, `smoke` Smoke |
| FLORAL | `orchid` Orchid, `jasmine` Jasmine, `osmanthus` Osmanthus, `rose` Rose, `honeysuckle` Honeysuckle, `sakura` Cherry blossom |
| FRUITY | `citrus` Citrus, `yuzu` Yuzu, `stone_fruit` Stone fruit, `muscatel` Muscatel grape, `melon` Melon, `dried_fruit` Dried fruit, `berry` Berry |
| EARTHY_WOODY | `forest_floor` Forest floor, `mushroom` Mushroom, `wood` Wood, `leather` Leather, `camphor` Camphor, `mineral` Mineral / wet stone |
| SPICE_HERB | `mint` Mint, `pine` Pine, `cinnamon` Cinnamon, `herbal` Herbal |
| MOUTHFEEL | `creamy` Creamy, `silky` Silky, `thick` Thick / viscous, `brisk` Brisk, `drying` Drying, `coating` Coating, `clean_finish` Clean finish, `lingering_sweet` Lingering sweetness |
| OFF_NOTE | `stale` Stale, `flat` Flat / weak, `harsh_bitter` Harshly bitter, `metallic` Metallic, `musty` Musty, `sour` Sour |

The umami taste itself is captured by the *Umami* slider, not a descriptor.

## 4. Learn guides

Format: one Markdown file per guide in `assets/content/guides/<slug>.md` with
front matter (`slug`, `title`, `readingMinutes`, `heroImage`, `relatedStyles`,
`relatedGuides`, `version`). Target length 400–900 words. Outlines:

| # | Slug | Title | Key points |
|---|---|---|---|
| 1 | `umami` | What is umami — and why is it in my tea? | Fifth basic taste (savoury), associated with glutamate; in tea it comes from free amino acids — glutamic acid and L-theanine (the most abundant amino acid in tea, with a milder savoury-sweet taste) among them; why umami teas taste brothy/sweet; how to taste it (coats the tongue, lingers). |
| 2 | `shade-grown` | Shade-grown teas: gyokuro, kabusecha, matcha | Shading before harvest (kabuse); sunlight converts theanine into catechins (bitter/astringent), so shading keeps more theanine and chlorophyll; ~1–2 weeks kabusecha vs ~3 weeks gyokuro/tencha; tana vs direct covering. |
| 3 | `temperature` | Temperature: the umami dial | Amino acids dissolve well even in cool water; catechins and caffeine extract more at higher temperatures → cooler water emphasises umami and sweetness; examples per style; yuzamashi technique (≈ 10 °C drop per transfer); link to cooling helper. |
| 4 | `ratios` | Leaf, water, time | Ratio concept (g / 100 ml); why Japanese and gongfu use more leaf and shorter steeps; adjusting one variable at a time; using the ratio calculator. |
| 5 | `water` | Water matters | Soft water with low mineral content suits Japanese greens; avoid heavily chlorinated water; filtered water; don't re-boil repeatedly; mention TDS without health claims. |
| 6 | `japanese-teaware` | Kyusu, houhin and shiboridashi | Side-handle kyusu, handleless houhin for gyokuro, shiboridashi; filters (ceramic, mesh); clay types (Tokoname, Banko); warming cups; pouring the last drops ("golden drops"). |
| 7 | `matcha` | Matcha: usucha and koicha | Grades (avoid "ceremonial" marketing confusion — describe by taste and use); sifting; water temperature; whisking (chasen, M/W motion); koicha kneading; caring for the chasen. |
| 8 | `cold-brew` | Cold brew and ice brew | Mizudashi (cold water, hours in fridge) and kōridashi (ice melting over leaves); why it's sweeter and less bitter; food-safety basics (fridge, consume within ~24 h). |
| 9 | `freshness` | Keeping tea fresh | Enemies: oxygen, light, heat, moisture, odours; airtight, opaque containers; unopened in fridge OK — let it reach room temperature before opening to avoid condensation; opened green tea best within weeks; how the app's freshness hints work. |
| 10 | `labels` | Reading a Japanese tea label | Region (Uji, Yame, Shizuoka, Kagoshima…), harvest (ichibancha, shincha), cultivar (Yabukita, Saemidori, Okumidori, Gokō…), steaming level (asamushi, chūmushi, fukamushi), shading. |
| 11 | `tasting` | Tasting like a pro | Look, smell (dry leaf, wet leaf, liquor), sip, slurp, finish; using the taste sliders and descriptors; comparing infusions; brew coach. |
| 12 | `gongfu-vs-western` | Gongfu, western and kyusu brewing | Differences in ratio, time, number of infusions; when to use which; how the timer handles multi-infusion. |

All guides must be fact-checked against reputable sources (tea research
institutes, established tea educators) before release.

## 5. Sample teas

Added if the user chooses *Start with sample teas* (ONB-04), flagged
`isSample = true`, with no vendor, no stock, and the style's profiles:

| Name | Secondary | Style | Notes field |
|---|---|---|---|
| Sample Gyokuro | 玉露 | `gyokuro` | "A sample tea — edit or remove it anytime." |
| Everyday Sencha | 煎茶 | `sencha` | same |
| Matcha for Usucha | 抹茶 | `matcha` | same |
| Roasted Hōjicha | ほうじ茶 | `hojicha` | same |

No sample sessions are created (the journal starts empty and honest).

## 6. Autocomplete suggestions

**Regions** (with country): Uji (Kyoto, JP), Yame (Fukuoka, JP), Hoshino
(Fukuoka, JP), Shizuoka (JP), Kakegawa (Shizuoka, JP), Kawane (Shizuoka, JP),
Okabe (Shizuoka, JP), Kagoshima (JP), Chiran (Kagoshima, JP), Nishio (Aichi,
JP), Sayama (Saitama, JP), Ureshino (Saga, JP), Wazuka (Kyoto, JP), Hangzhou
(Zhejiang, CN), Anji (Zhejiang, CN), Wuyi Shan (Fujian, CN), Anxi (Fujian, CN),
Fuding (Fujian, CN), Yunnan (CN), Alishan (TW), Lishan (TW), Nantou (TW),
Boseong (KR), Hadong (KR), Jeju (KR), Darjeeling (IN), Assam (IN), Nilgiri
(IN), Uva (LK), Nuwara Eliya (LK).

**Cultivars:** Yabukita, Saemidori, Okumidori, Asatsuyu, Gokō, Yamakai,
Tsuyuhikari, Kanaya Midori, Sae Akari, Samidori, Asanoka, Benifūki, Ujihikari,
Okuyutaka, Meiryoku, Sōfū, Fushun, Jin Xuan, Qing Xin, Da Hong Pao,
Tieguanyin, Longjing #43.

## 7. Brew coach rules

Evaluated in order on the session summary (JRN-14); show at most two tips.
Thresholds use the 0–5 taste scales.

| # | Condition | Tip |
|---|---|---|
| 1 | bitterness ≥ 4 **or** astringency ≥ 4, and base temp > style default − 5 °C | "Quite bitter or drying? Try about 5 °C cooler, or shorten the first infusion by ~20 %." |
| 2 | style umami ≥ HIGH and umami ≤ 2 and base temp ≥ style default + 5 °C | "Umami teas shine with cooler water. Try the suggested temperature next time." |
| 3 | body ≤ 1 **or** descriptor `flat` | "A bit thin? Try 10–20 % more leaf or a slightly longer steep." |
| 4 | descriptor `stale` and tea past freshness window | "Opened a while ago? Green teas fade after a few weeks — see *Keeping tea fresh*." |
| 5 | any actual step ≥ planned + 50 % (overtime) | "Your infusions ran long today — the alert sound can be made longer in settings." |
| 6 | rating ≥ 4.5 and the session differed from the profile | "Loved this one? Save today's parameters to the profile." (pairs with PRF-07) |

## 8. Micro-copy

- Home greetings (rotating): "Good tea brings a moment of calm." · "A quiet cup
  awaits." · "Take a breath. Warm the pot." · Morning: "Good morning — fresh
  leaves, fresh start." · Evening: "Something gentle for the evening?"
- Tips of the day (LRN-07, ~30): short facts drawn from the guides, e.g.
  "Pouring water between two cups cools it by roughly 10 °C each time."
- Empty states: Collection "Your collection is empty. Add the teas you own to
  get brewing suggestions." · Journal "Your first cup is waiting." · Search "No
  teas match — try fewer filters."
