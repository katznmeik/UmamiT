# 11 · Glossary

> Status: **Draft v0.2** · Last updated: 2026-10-21

## Tea terms

| Term | Meaning |
|---|---|
| **Umami** | The savoury "fifth taste" (alongside sweet, sour, salty, bitter), associated with glutamate. In tea it comes from free amino acids such as glutamic acid and L-theanine. |
| **L-theanine** | The most abundant amino acid in tea leaves; contributes savoury-sweet taste. Shading preserves it. |
| **Catechins** | Polyphenols that give tea bitterness and astringency; extracted more at higher temperatures. |
| **Gyokuro** (玉露) | Japanese green tea shaded for about three weeks before harvest; very high umami. |
| **Kabusecha** (かぶせ茶) | Japanese green tea shaded for about one to two weeks. |
| **Kabuse** | Covering/shading tea bushes before harvest. **Tana**: a trellis-style shading structure. |
| **Sencha** (煎茶) | The most common Japanese green tea: steamed, rolled, usually unshaded. |
| **Asamushi / Chūmushi / Fukamushi** | Light / medium / deep steaming of sencha. |
| **Shincha** (新茶) | "New tea": the first harvest of the year, sold fresh in spring. |
| **Ichibancha / Nibancha** | First / second harvest (flush) of the year. |
| **Matcha** (抹茶) | Stone-ground powder of shaded tencha leaves, whisked rather than steeped. |
| **Tencha** (碾茶) | Shaded, steamed, dried (not rolled) leaf that is ground into matcha. |
| **Usucha / Koicha** | "Thin" (whisked, foamy) and "thick" (kneaded, syrupy) matcha. |
| **Chasen / Chashaku / Chawan** | Bamboo whisk / bamboo scoop / matcha bowl. |
| **Hōjicha** (ほうじ茶) | Roasted Japanese green tea; toasty, low in bitterness. |
| **Genmaicha** (玄米茶) | Green tea blended with toasted rice. |
| **Kukicha / Karigane** | Stem teas; *karigane* often refers to stems from gyokuro or high-grade sencha. |
| **Tamaryokucha** (玉緑茶) | Curly-leaf green tea from Kyushu. |
| **Bancha** (番茶) | Later-harvest Japanese green tea; lighter, low in umami. |
| **Kamairicha** (釜炒り茶) | Pan-fired (not steamed) Japanese green tea. |
| **Aufguss** | German for infusion/steep. **Ziehzeit**: steep time. **Kaltaufguss**: cold brew. |
| **Kyusu** (急須) | Japanese teapot, typically with a side handle. |
| **Houhin** (宝瓶) | Small handleless teapot, often used for gyokuro. |
| **Shiboridashi** (絞り出し) | Handleless brewing vessel with a notched lid. |
| **Mizudashi** (水出し) | Cold brewing in cold water over hours. |
| **Kōridashi** (氷出し) | Ice brewing: ice melts slowly over the leaves. |
| **Gongfu** (功夫) | Chinese brewing style: high leaf-to-water ratio, many short infusions, often in a gaiwan. |
| **Gaiwan** (盖碗) | Lidded bowl used for gongfu brewing. |
| **Western style** | Few long infusions with a low leaf-to-water ratio in a teapot or mug. |
| **Infusion / steep** | One round of water on the leaves. |
| **Rinse** | A very short first pour that is discarded (common for oolong and pu-erh). |
| **Liquor** | The brewed tea liquid. |
| **Flush** | A harvest period (first flush = spring). |
| **Cultivar** | A cultivated tea plant variety (e.g. Yabukita, Saemidori). |

## Product & technical terms

| Term | Meaning |
|---|---|
| **Tee-Kontor Kiel** | German tea shop (tee-kontor-kiel.de) whose green tea range forms the in-app catalog. |
| **Katalog / Sammlung** | Catalog = read-only copy of the shop's green teas; Sammlung (collection) = the teas the user owns. |
| **Rezept** | UI name for a brew profile (recipe). |
| **Rezept prüfen** | Flag on a tea whose recipe was not taken directly from the shop's brewing table. |
| **Pre-alert (Vorwarnung)** | Soft tick a configurable number of seconds before a steep ends. |
| **Live Update** | Android 16 promoted ongoing notification shown as status-bar chip, on the lock screen and in Samsung's Now Bar. |
| **Now Bar** | Samsung One UI area on the lock screen for live activities. |
| **Sideloading** | Installing an APK directly (here from GitHub Releases) instead of from a store. |
| **snap5** | Rounding a temperature to the nearest 5 °C (half up). |
| **Collection** | The user's own teas (UI name: Sammlung › Meine Tees). |
| **Style** | A built-in internal reference entry (e.g. "Gyokuro") with fallback parameters; not browsable. |
| **Brew profile** | A per-tea recipe: method, leaf, water, temperature, steps. |
| **Step** | One timed part of a profile/session: rinse, infusion or whisk. |
| **Session** | One brewing occasion recorded in the journal. |
| **Teaware / vessel** | A brewing vessel with capacity (kyusu, gaiwan, …). |
| **Ratio** | Leaf grams per 100 ml of water. |
| **Active brew bar** | The persistent mini-timer shown above the navigation bar while a session runs. |
| **Short / long step** | Steps under / at least 30 minutes; handled differently in the background. |
| **FGS** | Foreground service — an Android service with a visible notification that keeps the timer alive. |
| **Doze** | Android power-saving mode that defers background work when the device is idle. |
| **Exact alarm** | An `AlarmManager` alarm delivered at a precise time; needs special permission on Android 12+. |
| **SAF** | Storage Access Framework — system file picker used for backups. |
| **UDF** | Unidirectional data flow: state flows down, events flow up. |
| **MoSCoW** | Must / Should / Could / Won't prioritisation. |
| **TTID / TTFD** | Time to initial / full display (startup metrics). |
| **PO** | Product owner. |
