# 11 · Glossary

> Status: **Draft v0.2** · Last updated: 2026-10-31 (Archivieren / Löschen / Wiederherstellen, step kinds, v0.4.0 grilling; 2026-10-30: Merkliste, Knapp, D-097; quick timer, active timer, long step from v0.3.0)

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
| **Pre-alert (Vorwarnung)** | A short sound (*Vorwarnton*) a few seconds before a step ends, so the kyusu can be picked up; per step *Automatisch*, *Aus* or 5–30 s (TMR-23). Long steps have none. |
| **Merkliste (Merken, Gemerkt)** | Catalog teas marked for later without adding them to the collection (CAT-18, v0.2.2). Not *Favoriten*, which are the user's own teas. |
| **Knapp** | Stock status of a tea at or below the threshold (SET-16, 15 g from v0.2.2); *Leer* at 0 g (INV-05). |
| **Low-stock card / reminder** | The card on Start for one *Knapp* or *Leer* tea (INV-10), which *Ausblenden* hides for that tea until its next purchase; and, from v0.3.0, the daily notification listing every *Knapp* or *Leer* tea, which ignores *Ausblenden*. An archived tea appears in neither. |
| **Archivieren / Wiederherstellen** | *Archivieren* puts a tea or a vessel away: it leaves the lists and pickers but keeps its recipes, stock and journal entries; *Wiederherstellen* brings it back. The one word for it everywhere (formerly „Aus Sammlung entfernen" in the tea picker). Not *Ausblenden*, which only hides a low-stock card. |
| **Löschen** | Deleting a tea for good: it and its recipes, stock and photos are gone; its journal entries stay with its name. Vessels are never deleted, only archived. |
| **Pre-alert glow** | Signature motion: the pre-alert marker on the timer ring glows gently in sync with the pre-alert tick (03 §3.6, D-041). |
| **Liquor fill** | Signature motion: while a step runs, the inside of the timer ring fills in the tea's liquor colour (03 §3.6, D-041). |
| **Live Update** | Android 16 promoted ongoing notification shown as status-bar chip and on the lock screen; on One UI also in Samsung's Now Bar (best effort). |
| **Now Bar** | Samsung One UI area on the lock screen for live activities. |
| **Sideloading** | Installing an APK directly (here from GitHub Releases) instead of from a store. |
| **snap5** | Rounding a temperature to the nearest 5 °C (half up). |
| **Collection** | The user's own teas (UI name: Sammlung › Meine Tees). |
| **Kategorie** | The broad class of a tea: Grüntee, Weißer Tee, Oolong, Schwarzer Tee, Pu-Erh, Kräutertee. |
| **Sorte** | The kind of a tea as the user sees it (Gyokuro, Sencha, Bancha …): the shop's *Sorte* for a catalog tea, or the one picked in *Sorte wählen* for a tea added without the catalog. Each Sorte belongs to one Kategorie. |
| **Style** | The built-in reference entry behind a Sorte, with its default recipe and freshness period. The user sees a style only by its Sorte name, never its values directly. |
| **Brew profile** | A per-tea recipe: method, leaf, water, temperature, steps. |
| **Step** | One timed part of a profile/session. Its kind is *Aufguss* (infusion), *Spülen* (rinse: poured away, not a brewed step) or *Aufschlagen* (whisking matcha). |
| **Set time / measured time** | A step's set time is the duration it ran with (*geplant*, including Schritte-Sheet changes); its measured time runs until *Abgegossen* (*tatsächlich*, including any overtime). Brewing again uses set times. |
| **Session** | One brewing occasion recorded in the journal; its detail page (SCR-16) opens from a journal entry. |
| **Nochmal brühen / So nochmal brühen** | *Nochmal brühen* (Start, BRW-11) starts one of the last three teas at once in READY with its last session's values. *So nochmal brühen* (on a session's detail page, from v0.4.0) opens the brew setup with that session's values as brewed, to check before *Start*. |
| **Quick timer (Schnelltimer)** | A countdown without a tea; nothing is recorded (TMR-22). Not a session. |
| **Active timer** | A brew session or a quick timer that is running, paused, waiting or alerting. At most one active brew session and at most one active quick timer at the same time (from v0.3.0; until then one active timer in total). |
| **Teaware / vessel** | A brewing vessel with capacity (kyusu, gaiwan, …). |
| **Ratio** | Leaf grams per 100 ml of water. |
| **Active brew bar** | The persistent mini-timer shown above the navigation bar while an active timer exists; with a brew session and a quick timer it shows both. |
| **Short / long step** | A long step is every step of a cold or ice brew, and any step of at least 60 minutes (from v0.3.0; until then 10 minutes). Long steps have no pre-alert and show „Fertig um …“; all other steps are short steps. |
| **Full-screen intent** | A notification that turns the screen on and opens an activity over the lock screen; used for the step alert (`USE_FULL_SCREEN_INTENT`). |
| **Schritte-Sheet** | Session-only step editor (SCR-31). |
| **Release candidate (rc)** | Pre-release APK `vX.Y.Z-rc.N` built from a milestone PR branch for the PO's testing. |
| **FGS** | Foreground service — an Android service with a visible notification that keeps the timer alive. |
| **Doze** | Android power-saving mode that defers background work when the device is idle. |
| **Exact alarm** | An `AlarmManager` alarm delivered at a precise time; needs special permission on Android 12+. |
| **SAF** | Storage Access Framework — system file picker used for backups. |
| **UDF** | Unidirectional data flow: state flows down, events flow up. |
| **MoSCoW** | Must / Should / Could / Won't prioritisation. |
| **TTID / TTFD** | Time to initial / full display (startup metrics). |
| **PO** | Product owner. |
