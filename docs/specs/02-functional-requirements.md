# 02 · Functional Requirements

> Status: **Draft v0.2** · Last updated: 2026-10-21
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

**GEN-01 — Local-first, single network use** `Must`
All personal data is stored only on the device. The app declares
`INTERNET` **solely** for the catalog refresh (CAT-07).
- AC1: Network requests are made only to `www.tee-kontor-kiel.de` (and its
  image host, if different), enforced by a host allow-list in the HTTP client.
- AC2: Every feature except catalog refresh works in airplane mode.
- AC3: No analytics, ads, crash-reporting or other third-party network SDKs.

**GEN-02 — No accounts** `Must`

**GEN-03 — Single active timer** `Must`
At most one brew session or quick timer is active at any time.

**GEN-04 — German & metric** `Must`
UI language German (`de-DE`) only; units °C, g, ml; German number, date and
time formats (e.g. „21. Okt. 2026", „4,5 g").

**GEN-05 — Undo for destructive actions** `Should`
Deletions show a snackbar „Gelöscht · Rückgängig" for 5 s. Multi-object or
irreversible actions (restore, delete all) use a confirmation dialog.

**GEN-06 — Active brew bar** `Must`
While a session or quick timer is active, top-level screens show a compact bar
above the navigation bar with tea name, step („Aufguss 2/4") and live remaining
time; tapping it opens the timer (SCR-05).

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
Welcome page with logo, „UmamiTea", tagline, ink-wash illustration,
*Los geht's*.

**ONB-02 — Reliability setup** `Must`
One page „Damit der Timer zuverlässig klingelt" walks through:
1. Notification permission (`POST_NOTIFICATIONS`).
2. Battery: request exemption from battery optimisation
   (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) and explain Samsung's
   „Akku › Uneingeschränkt" and „Nie schlafende Apps" settings with a deep link
   to the app's battery settings.
3. Live notifications: verify that the app may show Live Updates; deep link to
   the setting if not.
Each item shows a ✓ when satisfied. *Weiter* is always allowed; missing items
are then shown as a banner on Home and the timer (TMR-31).

**ONB-03 — Load catalog** `Must`
Final page offers *Katalog jetzt laden* (runs CAT-07 with progress) or
*Später*.

**ONB-04 — Run once** `Must`
Shown until completed; reachable again via Einstellungen › Über ›
*Einrichtung erneut zeigen*.

---

## 3. Tee-Kontor catalog (CAT) — SCR-19 … SCR-23

The catalog is a local, read-only copy of all green teas from
[tee-kontor-kiel.de](https://www.tee-kontor-kiel.de/), refreshed on demand.
Source structure, field mapping and parsing rules: [08 §2](08-content.md#2-tee-kontor-catalog-import).

**CAT-01 — Scope** `Must`
All products in the shop's green tea sections: Japan, China, Taiwan, Korea and
other origins, *Bio*, *Klassischer Grüntee*, *Aromatisierter Grüntee*
(flavoured teas and blends), the current *Shincha* section and *Matcha &
Teepulver*. Excluded: tea sets/multipacks, accessories, gift cards.

**CAT-02 — One entry per tea** `Must`
Size variants, samples („M") and bulk packs of the same tea are merged into one
catalog entry with a list of package options (weight, price).

**CAT-03 — Extracted data** `Must`
Per tea: name, secondary name, article number, product URL, shop category and
tea type (*Sorte*: Sencha, Gyokuro, …), origin country & region, cultivar,
harvest/flush, shading, steaming, organic flag, flavoured flag and
ingredients, description and taste notes (German text), raw brewing table,
raw brewing text, package options with price, base price per 100 g,
availability, main photo. Unavailable fields stay empty.

**CAT-04 — Derived recipe** `Must`
For each tea, one or more timer-ready recipes are derived from the brewing
information using the rules in [08 §2.5](08-content.md#25-recipe-derivation):
table wins over text, then the built-in style default; ranges use the
midpoint; temperatures snap to 5 °C, times to 5 s; cold-brew instructions
produce an extra cold-brew recipe.

**CAT-05 — „Rezept prüfen" flag** `Must`
A tea is flagged „Rezept prüfen" (check recipe) with a reason when its recipe
came from the text or a style default instead of the table, when table and
text contradict, or when values were implausible. The flag is visible in the
catalog and carries over to collection teas created from it until the user
edits or confirms the recipe.

**CAT-06 — Style mapping** `Must`
Every catalog tea is mapped to a built-in style (STY) by its *Sorte* or name
(e.g. „Gyokuro" → `gyokuro`), providing umami level, freshness window and
fallback recipe. Unmapped teas use `green_generic` and are flagged.

**CAT-07 — Refresh** `Must`
*Katalog aktualisieren* (Sammlung › Katalog, overflow, and Settings) fetches
the shop, parses all products, and replaces the catalog atomically.
- AC1: Progress shows pages and products processed; the refresh can be
  cancelled; the phone may be locked during refresh (long-running WorkManager
  worker in a `dataSync` foreground service with a progress notification).
- AC2: Requests are polite: ≤ 1 request/second, identifying user agent,
  `robots.txt` respected, timeouts and ≤ 3 retries.
- AC3: Result summary: „12 neu · 5 geändert · 2 nicht mehr im Sortiment ·
  3 Rezepte prüfen".
- AC4: Teas no longer found are kept, marked „Nicht mehr im Sortiment".
- AC5: If parsing fails structurally (e.g. < 50 % of the previous product
  count, or required fields missing on most products), the existing catalog
  is kept unchanged and the error „Die Shop-Seite hat sich geändert – ein
  App-Update ist nötig" is shown with technical details for the developer.
- AC6: Date of the last successful refresh is shown in the catalog header.

**CAT-08 — Photos** `Must`
The main product photo of each tea is downloaded, resized (max 1600 px long
edge, JPEG/WebP) and stored locally; unchanged photos are not downloaded
again.

**CAT-09 — Tee-Info guides** `Should`
Tee-Kontor's guide articles relevant to green tea (e.g. „Ein Guide für
Japantee", matcha guides and recipes) are imported as readable articles and
listed in Katalog › *Ratgeber*. Links between guides and to catalog teas are
resolved locally where possible; other links open the browser.

**CAT-10 — Browse & search** `Must`
SCR-19 lists catalog teas with photo, name, secondary name, type, origin,
price per 100 g and flags (Bio, aromatisiert, im Besitz, Rezept prüfen).
Filters: type (multi), origin country (multi), organic, flavoured / pure,
umami level, „im Besitz" (owned), available. Sort: name, price per 100 g,
type. Search over name, secondary name, description, cultivar, region.

**CAT-11 — Catalog tea detail** `Must`
SCR-21 shows photo, names, type, origin details, organic/flavoured,
description, brewing guide (raw table and text as on the shop), the derived
recipe(s) with flag reason, package options and prices, and actions
*In Sammlung aufnehmen* and *Im Shop öffnen* (browser).

**CAT-12 — Add to collection** `Must`
*In Sammlung aufnehmen* opens SCR-23: choose package option (weight & price,
prefilled), purchase date (default today); creates a collection tea that is
an independent copy (TEA-01) with recipes, stock (INV-01) and price. If the
tea is already in the collection, the action becomes *Nachkauf erfassen*
(record repurchase), adding stock instead of a duplicate.

**CAT-13 — Collection teas stay independent** `Must`
A refresh never changes collection teas. Tea detail offers *Aus Katalog
zurücksetzen* (reset description/recipes from the current catalog entry, with
confirmation and a choice of what to reset).

**CAT-14 — Bundled snapshot** `Could`
An initial catalog snapshot (without photos) is bundled in the APK so the
catalog is available before the first refresh. Generated by the same parser
at build time; not committed to the repository (see D-016).

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
notes, details, *Im Shop öffnen* (catalog teas).

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
and an ordered list of steps (rinse, infusion, whisk).

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
| Pre-alert | „Wie in Einstellungen" (default) or Aus / 5 … 30 s in 5 s steps |
| Steps | 1–30 |
| Notes | 0–500 chars |

- AC1: Ratio shown live („4,0 g / 100 ml").

**PRF-04 — Steps editor** `Must`
Add, delete, reorder steps; per step: kind, duration 5 s – 24 h (5 s steps
below 10 min, 1 min steps above), optional temperature (5 °C steps), note.
At least one non-rinse step.

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

**WARE-05 — Default vessel per method** `Could`
E.g. KYUSU → „Kyusu 180 ml".

---

## 8. Brew setup (BRW) — SCR-03, SCR-04

**BRW-01 — Start a brew** `Must`
Entry points: Home *Aufguss starten* (tea picker), *Nochmal brühen* items on
Home, tea detail *Brühen*, journal *Nochmal brühen*. A known tea starts in ≤ 3
taps.

**BRW-02 — Setup screen** `Must`
Shows tea, recipe chips, vessel, water, leaf, ratio lock, base temperature,
step list, effective pre-alert, *Brühen starten*. Changes apply to this
session only unless *Im Rezept speichern* is tapped.

**BRW-03 — Ratio scaling** `Must`
With ratio lock (default on), changing water or vessel rescales leaf (0,1 g);
changing leaf rescales water (5 ml). Times and temperatures never scale.

**BRW-06 — Brew from a past session** `Should`
*Nochmal brühen* on a journal entry pre-fills setup with that session's
parameters.

**BRW-08 — Stock warning** `Should`
„Nur noch 3 g übrig" if stock is below the leaf amount.

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
label „Aufguss 2 von 4", step temperature.

**TMR-03 — Pause / resume** `Must`
A cream pill button *Pause* / *Weiter* below the ring (as in the mockup) is the
only primary control while running.

**TMR-05 — End step early** `Must`
Overflow ⋮ › *Aufguss jetzt beenden* ends the running step, records the actual
time and moves on (no alert).

**TMR-06 — Alert** `Must`
At zero: sound and vibration **following the phone's ringer mode** (normal →
sound + vibration; vibrate → vibration; silent → no sound/vibration, visual
only), repeated for up to ~15 s or until acknowledged; heads-up notification
and Live Update show „Jetzt abgießen!". Screen shows overtime („+0:07") and a
large *Abgegossen* button.

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
In FINISHED and via overflow: *Weiterer Aufguss* appends a step (last
temperature; last duration + 50 %, rounded per PRF-04: 5 s below 10 min,
1 min above), editable before start.

**TMR-11 — Edit remaining steps** `Should` (overflow; this session only).

**TMR-12 — Finish & auto-log** `Must`
*Sitzung beenden* (FINISHED state or overflow) completes the session; it is
saved to the journal automatically **without a summary screen**. A bottom
sheet (SCR-06) appears for 10 s and stays reachable from the journal:
„Gespeichert im Journal" with *Notizen hinzufügen* (opens SCR-17) and — only if
PRF-07 applies — *Zeiten ins Rezept übernehmen*. Ignoring it is fine.
- AC1: Sessions with no completed non-rinse step are discarded after
  confirmation.

**TMR-13 — Discard session** `Must` (overflow, with confirmation).

**TMR-14 — Auto-finish idle sessions** `Must`
60 min without interaction in READY/PAUSED/FINISHED → finished and logged
with notification „Sitzung gespeichert"; a session without any completed
non-rinse step is discarded silently instead.

**TMR-15 — Background & lock screen** `Must`
The timer continues when leaving the app or locking the phone.

**TMR-16 — Survive process death and reboot** `Must`
See [04 §7](04-brew-timer.md#7-persistence--recovery).

**TMR-17 — Keep screen on** `Must` while SCR-05 is visible (setting, default on).

**TMR-18 — Long steps (cold brew, ice brew)** `Must`
Steps ≥ 10 min (always including cold and ice brew) use exact alarms
without keeping a service running; no pre-alert; the Live Update shows
„Fertig um 14:30".

**TMR-19 — Whisk steps** `Must` („Zügig in M/W-Form aufschlagen").

**TMR-20 — Rinse steps** `Must` („Spülen – Wasser weggießen"; button
*Weggegossen*).

**TMR-21 — Second session** `Must`
Starting while another is active: „Erst die *Gyokuro*-Sitzung beenden?"
(*Beenden & neu starten* / *Abbrechen*).

**TMR-22 — Quick timer** `Should`
SCR-07: countdown without a tea: chips 0:30, 1:00, 1:30, 2:00, 3:00, 5:00 and
custom; same alert, pre-alert (global setting) and Live Update; not logged.

**TMR-23 — Pre-alert tick** `Must`
A soft tick sound (plus short haptic) **N seconds before the end** of each
step so the kyusu can be picked up.
- N comes from the recipe's pre-alert override, else the global setting
  (SET-10): Aus or 5–30 s in 5 s steps; default 5 s.
- Skipped if the step is shorter than N + 5 s, for RINSE steps shorter
  than 15 s, and for long steps (≥ 10 min, TMR-18).
- The tick follows the ringer mode like the alert (vibrate mode → short
  vibration only).
- The Live Update / timer caption switches to „Gleich fertig…" at the
  pre-alert.

**TMR-30 — Accuracy** `Must`
Alerts and pre-alerts within 1 s of target (typical ≤ 250 ms), screen on or
off, app foreground or background.

**TMR-31 — Reliability banner** `Must`
If notifications are blocked, Live Updates are disabled, the battery is
restricted for the app, or exact alarms are unavailable, the timer and Home
show „Der Timer ist eventuell unzuverlässig – *Beheben*" deep-linking to the
relevant setting.

**TMR-41 — Live Update** `Must`
On Android 16, the active timer is a promoted ongoing notification (Live
Update) with progress-style segments per step, visible as status bar chip, on
the lock screen and in Samsung's Now Bar. Details:
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
Row: thumbnail, tea name, „8. Juni · 3 Min · 80 °C", rating if present, a
subtle dot for sessions without notes. In-progress session pinned on top.

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

**INV-04 — Session edits** `Must` update the linked deduction.

**INV-05 — Stock status** `Must`
Leer (≤ 0 g), Knapp (≤ threshold, default 10 g), Vorrätig.

**INV-06 — Opened date & freshness** `Should`
*Geöffnet am* is set automatically (snackbar *Rückgängig*) on the first
session after a new package: i.e. when `opened_date` is empty, or when the
latest INITIAL/PURCHASE event is newer than `opened_date` and the stock had
reached 0 g before that purchase; freshness from the style's window: „Frisch",
„Bald genießen", „Über dem Höhepunkt".

**INV-07 — Low stock & freshness reminders** `Could`
Weekly digest notification (off by default).

**INV-08 — Cost** `Must`
Cost per gram from purchase events (weighted by grams of each purchase); cost
per session = leaf × cost per gram; shown on tea detail and in statistics.

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
Grams used per month (bar chart, 12 months), money spent on tea per month
(purchases), average cost per session, teas running out (Knapp/Leer).

**INS-07 — Time range** `Must`: 30 Tage, 90 Tage, 12 Monate, Gesamt (default 90 Tage).

Every chart has an accessible table alternative.

---

## 13. Settings (SET) — SCR-25

| ID | Setting (German label) | Values / default | Priority |
|---|---|---|---|
| SET-05 | Design (*Hell / Dunkel / System*) | System | Must |
| SET-06 | Hintergrundfarben verwenden (dynamic colour) | aus | Could |
| SET-09 | Alarmton | Bundled tones (Klangschale, Glocke, Holzblock, Glockenspiel, Sanft) — Klangschale | Must |
| SET-10 | Vorwarnung (pre-alert) | Aus, 5, 10, 15, 20, 25, 30 s — **5 s** | Must |
| SET-11 | Vibration | an | Must |
| SET-14 | Alarmdauer (alert length) | Kurz (1×) / Normal (bis 15 s) / Lang (bis Bestätigung, max. 2 Min) — Normal | Should |
| SET-15 | Verbrauch automatisch abziehen (auto-deduct) | an | Must |
| SET-16 | Schwelle „knapp" | 0–500 g — 10 g | Should |
| SET-17 | Bildschirm beim Brühen anlassen | an | Must |
| SET-18 | Geänderte Zeiten anbieten (PRF-07) | an | Should |
| SET-19 | Wöchentliche Vorrats-Erinnerung (INV-07) | aus | Could |
| SET-20 | Backup-Erinnerung (manual export) | Aus / Monatlich — Monatlich | Should |
| SET-22 | Katalog: Fotos laden | an | Should |
| SET-23 | Katalog: letzter Abruf, *Jetzt aktualisieren* | — | Must |
| SET-24 | Timer-Zuverlässigkeit (status of notification, Live Update, battery, exact alarm with *Beheben* links) | — | Must |

**SET-30 — Sections** `Must`: *Timer & Alarm*, *Brühen & Vorrat*, *Katalog*,
*Teegeschirr* (→ SCR-13), *Darstellung*, *Daten* (→ SCR-26, Alle Daten
löschen), *Über* (→ SCR-27).

**SET-31 — Test alert** `Must`: *Alarm testen* plays pre-alert tick + alert.

**SET-33 — Delete all data** `Must` (type „LÖSCHEN" to confirm).

---

## 14. Backup & export (BAK) — SCR-26

**BAK-05 — Android device backup** `Must`
Database and settings participate in Android Auto Backup / Samsung & Google
device backup and device-to-device transfer. Catalog photos and own photos are
excluded (size limit; catalog photos can be re-downloaded).

**BAK-01 — Manual export** `Should`
*Sicherung erstellen* writes a `.umamitea` file (data, settings and own photos;
catalog excluded) via the system file picker. Format:
[05 §8](05-data-model.md#8-backup-file-format).

**BAK-02 — Restore** `Should`
Validate, preview, confirm, safety backup, atomic replace.

**BAK-04 — CSV export** `Could`

**BAK-06 — Backup reminder** `Should`
Monthly low-importance reminder to create a manual export if there are ≥ 20
new sessions since the last export.

---

## 15. Notifications & Live Update (NOT)

**NOT-01 — Channels** `Must`: Brühtimer (live/ongoing, silent), Aufguss
fertig (high), Sitzungen (low), Erinnerungen (low), Katalog (low, refresh
progress/result).

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
| TMR-40 | Multiple concurrent timers | One at a time |
| SET-01…04, SET-07, SET-08 | Units, teaspoons, language, leaf precision | German/metric only |
| SET-12 | Start first step immediately | Always wait for the user |
| SET-13 | Alarm stream choice | Alert always follows ringer mode |
| SET-21 | Precise reminders permission | Exact alarms always available (`USE_EXACT_ALARM`, no store review) |
| BRW-04, BRW-05, BRW-07 | Teaspoons, cooling helper, checklist | Kettle with 5 °C steps |
| LRN-01…07 | Learn tab, own guides, calculators, tip of the day | Replaced by Tee-Kontor guides (CAT-09); Stats takes the tab |
| STY-02/03 (v0.1) | Browsable style reference | Styles are internal fallbacks only |
| JRN-01 (v0.1) | Session summary screen | Auto-logging (TMR-12) |
| JRN-06, JRN-15, JRN-16 | Water note, share, calendar | Not needed |
| TEA-12, TEA-15, TEA-16 | Grid view, bulk actions, label scanning | Not needed |
| BAK-03, BAK-07 | Merge restore, share backup | Not needed |
| NOT (store-related) | — | No store |
