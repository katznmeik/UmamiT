# 02 · Functional Requirements

> Status: **Draft v0.1** · Last updated: 2026-10-21

## 0. Conventions

### 0.1 Requirement IDs

Every requirement has a stable ID `AREA-NN`. IDs are never reused; removed
requirements are marked ~~struck through~~ with a note.

| Prefix | Area | Section |
|---|---|---|
| GEN | Cross-cutting / general | §1 |
| ONB | Onboarding | §2 |
| TEA | Collection (user's teas) | §3 |
| STY | Tea style catalog | §4 |
| PRF | Brew profiles | §5 |
| WARE | Teaware (vessels) | §6 |
| BRW | Brew setup & calculator | §7 |
| TMR | Steep timer & brew sessions | §8 |
| JRN | Journal & tasting notes | §9 |
| INV | Inventory & freshness | §10 |
| INS | Insights ("Your Tea Journey") | §11 |
| LRN | Learn & tools | §12 |
| SET | Settings | §13 |
| BAK | Backup, restore & export | §14 |
| NOT | Notifications | §15 |

### 0.2 Priorities (MoSCoW)

| Priority | Meaning |
|---|---|
| **Must** | Required for the MVP / closed beta. The app is not usable without it. |
| **Should** | Required for public release 1.0. |
| **Could** | Desirable; post-1.0 backlog candidate. Build only if time allows. |
| **Won't** | Explicitly out of scope for 1.x (documented to prevent scope creep). |

### 0.3 Format

Each requirement contains a short statement and, where useful, acceptance
criteria (AC) in *Given / When / Then* style. Screen references (`SCR-xx`)
point to [03 · UX & UI](03-ux-ui.md); entity names point to
[05 · Data Model](05-data-model.md). Timer behaviour is specified in depth in
[04 · Brew Timer](04-brew-timer.md).

Units in this document are metric; the app stores metric values and converts
for display (see GEN-04).

---

## 1. General (GEN)

**GEN-01 — Fully offline** `Must`
The app works without any network connection and declares **no `INTERNET`
permission**. All content (styles, articles, images, fonts, sounds) is bundled.
- AC1: The merged release manifest contains no `android.permission.INTERNET`
  and no `ACCESS_NETWORK_STATE` permission (verified by a CI check).
- AC2: Every feature in this document works in airplane mode.

**GEN-02 — No accounts** `Must`
There is no sign-in, registration or user identifier of any kind.

**GEN-03 — Single active timer** `Must`
At most one brew session or quick timer is active at any time (multiple
concurrent timers are TMR-40, `Could`).

**GEN-04 — Unit handling** `Must`
Values are stored in metric (°C, grams, millilitres) and displayed in the user's
chosen units (°C/°F, g/oz, ml/fl oz US). Input fields accept the displayed unit
and convert on save. Conversion and rounding rules: [05 §6](05-data-model.md#6-units-precision-and-rounding).

**GEN-05 — Undo for destructive actions** `Should`
Deleting a tea, profile, vessel, session or photo shows a snackbar with **Undo**
for 5 seconds. Destructive actions affecting more than one object (e.g. deleting
a tea that has sessions, restoring a backup, deleting all data) use a
confirmation dialog instead and are not undoable.

**GEN-06 — Persistent "active brew" bar** `Must`
While a session or quick timer is active, every top-level screen shows a compact
bar above the navigation bar with tea name, step (e.g. "Infusion 2/4") and live
remaining time; tapping it opens the timer (SCR-05).

**GEN-07 — Configuration changes & process death** `Must`
No user input or timer state is lost on rotation, theme change, multi-window
resize, or when the OS kills the process in the background. Unsaved editor
forms are restored after process death.

**GEN-08 — Unsaved changes guard** `Must`
Leaving an editor with unsaved changes (back, close, predictive back) shows
"Discard changes?" with *Keep editing* / *Discard*.

**GEN-09 — Empty, loading and error states** `Must`
Every list and detail screen defines an empty state (illustration + explanation
+ primary action), a loading state (only if > 300 ms; no spinners for local
reads under that) and an error state (message + retry where applicable).

**GEN-10 — Search is local and instant** `Must`
Search results update as the user types (debounced 150 ms), are
case-insensitive, accent-insensitive where supported, and match prefixes of
words.

**GEN-11 — Japanese names** `Should`
Teas and styles can show an optional secondary name (e.g. 「煎茶 浅蒸し」) under
the main name, as in the brand mockup.

---

## 2. Onboarding (ONB) — SCR-01

**User stories**
- As a new user, I want to understand what the app does in a few seconds, so that I can start brewing quickly.
- As a new user, I want sensible units preselected, so that I do not have to configure anything.

**ONB-01 — Welcome** `Must`
First launch shows a welcome page with logo, tagline ("Better tea. Deeper
moments."), an ink-wash illustration, and *Get started*. At most 3 onboarding
pages in total; every page except the first has *Skip*.

**ONB-02 — Units** `Must`
The units page preselects units from the device locale (US → °F, oz, fl oz;
everywhere else → °C, g, ml) and lets the user change temperature, weight and
volume units independently.

**ONB-03 — Notification permission** `Must`
On Android 13+ a page explains *why* notifications are needed ("so your steep
alert rings when the phone is locked") before requesting `POST_NOTIFICATIONS`.
- AC1: If the user denies, onboarding continues; the timer shows a
  non-blocking warning banner (TMR-31).
- AC2: The system dialog is never shown without the explanation first.

**ONB-04 — Starting content** `Should`
The final page offers *Start with sample teas* (adds the sample teas defined in
[08 §5](08-content.md#5-sample-teas), flagged `isSample`) or *Start with an
empty collection*. Default selection: sample teas.

**ONB-05 — Remove samples** `Should`
While sample teas exist, the Collection shows a dismissible card "These are
sample teas. Remove them?"; Settings › Data also offers *Remove sample teas*.
Removing samples also deletes their sessions.

**ONB-06 — Run once** `Must`
Onboarding is shown only until completed or skipped; it can be replayed from
Settings › About › *Show introduction*.

---

## 3. Collection — the user's teas (TEA) — SCR-08 … SCR-11

**User stories**
- As an enthusiast, I want to record every tea I own with origin, cultivar and harvest, so that I can compare them.
- As a beginner, I want to add a tea by picking its style, so that brewing parameters are filled in for me.

**TEA-01 — Add a tea** `Must`
The user adds a tea from the Collection FAB, the Home screen, a style detail
page (*Add to my collection*), or the tea picker.
- AC1: Step 1 is the style picker (SCR-11) with search; *Other / not sure* is
  always available.
- AC2: Choosing a style pre-fills category, form, secondary name suggestion,
  umami level and **copies the style's default brew profiles** into the new tea
  (PRF-02).
- AC3: Only **Name** is mandatory (1–80 characters after trimming). Saving
  with an empty name shows an inline error and does not save.
- AC4: After save, the app navigates to the new tea's detail page (SCR-09).

**TEA-02 — Tea fields** `Must`
The editor (SCR-10) supports all fields of the `Tea` entity
([05 §3.1](05-data-model.md#31-tea)), grouped in sections: *Basics* (photo, name,
secondary name, style, category, form), *Origin* (country, region, cultivar,
harvest year, harvest season, shading days, processing notes), *Purchase*
(vendor, purchase date, price & currency, package size), *Stock & freshness*
(see INV), *Notes & tags*.
- AC1: Optional sections are collapsed by default when empty.
- AC2: Country is chosen from a localized, searchable list (ISO 3166-1);
  Japan, China, Taiwan, Korea, India, Sri Lanka appear first.
- AC3: Region and cultivar fields offer autocomplete from the suggestion lists in
  [08 §6](08-content.md#6-autocomplete-suggestions) plus values the user entered
  before.

**TEA-03 — Edit a tea** `Must`
All fields are editable later. Changing the style does **not** overwrite
existing brew profiles; instead the user is asked "Also add the default profiles
of *<style>*?" (*Add* / *Keep current*).

**TEA-04 — Delete or archive a tea** `Must`
- AC1: *Archive* hides the tea from the default Collection view, pickers and
  Home; its sessions remain fully visible in the Journal.
- AC2: *Delete* asks for confirmation showing how many journal sessions refer to
  the tea. Sessions are **kept** (they store a snapshot of the tea name, see
  [05 §3.6](05-data-model.md#36-brewsession)) and display "Tea removed from
  collection". Profiles, photos, tags links and inventory events of the tea are
  deleted.
- AC3: The confirmation dialog offers *Archive instead* as secondary action.

**TEA-05 — Photos** `Should`
Up to 10 photos per tea, from the system photo picker or the camera; the first
photo is the cover (reorderable by drag). Processing rules: [07 §6](07-non-functional.md#6-privacy).

**TEA-06 — Favourites** `Must`
A tea can be marked favourite from list, detail and timer-summary screens.
Favourites appear on Home and can be filtered.

**TEA-07 — Tags** `Should`
Free-form tags (e.g. "gift", "Yame", "competition grade"); 1–30 characters,
case-insensitive unique; up to 20 tags per tea; autocomplete from existing tags;
tags can be renamed or deleted in Settings › Data › *Manage tags*.

**TEA-08 — Collection list** `Must`
The list (SCR-08) shows: cover photo or category illustration, name, secondary
name, style · vendor, average rating with count, stock chip and freshness chip
(if tracked), favourite indicator.

**TEA-09 — Search** `Must`
Searches name, secondary name, vendor, region, cultivar, notes and tags (FTS).

**TEA-10 — Filters** `Should`
Combinable filter chips: *Favourites*, *Category* (multi-select), *Umami level*
(multi-select), *Stock* (in stock / low / out / not tracked), *Tags*
(multi-select, any-of), *Show archived*. Active filter count is shown; *Clear
all* resets.

**TEA-11 — Sorting** `Must`
Sort by: Name (A–Z, default), Recently brewed, Recently added, Rating (high →
low), Stock remaining (low → high), Opened date (oldest first). The choice is
remembered.

**TEA-12 — List / grid view** `Could`
Toggle between list and 2-column photo grid; remembered.

**TEA-13 — Tea detail** `Must`
SCR-09 shows hero photo, names, rating, category chip, the **default profile
summary** (temperature · steep time · infusions, as in the mockup), *Brew*
primary action, profiles, stock & freshness, tasting summary (average rating,
average taste radar, top 5 descriptors), recent sessions (last 5 + *See all*),
notes, tags, origin and purchase details.

**TEA-14 — Duplicate a tea** `Could`
Creates a copy (name + " (copy)") with profiles and tags, without photos,
sessions or inventory — useful for a new harvest of the same tea.

**TEA-15 — Bulk actions** `Could`
Long-press enters multi-select: archive, delete, add tag, mark favourite.

**TEA-16 — Barcode / label scanning** `Won't`
No reliable product database exists offline. (Label OCR is a post-1.0 idea, see
[09 §6](09-release-plan.md#6-post-10-roadmap).)

---

## 4. Tea style catalog (STY) — SCR-11, SCR-21

**STY-01 — Bundled catalog** `Must`
The app ships the read-only catalog in [08 §2](08-content.md#2-tea-style-catalog)
(≥ 25 styles). Each style has: key, name, secondary name, category, typical
origin, umami level, short description, brewing notes, freshness window,
grams-per-teaspoon factor, and 1–3 default brew profiles.

**STY-02 — Browse & search styles** `Must`
Learn › *Tea styles* lists styles grouped by category with search and an umami
level filter.

**STY-03 — Style detail** `Should`
Shows description, umami meter, default profiles in readable form, storage /
freshness advice, links to related guides, and *Add to my collection*.

**STY-04 — Catalog updates** `Must`
The catalog is versioned with the app. Updating the app never modifies a user's
existing teas or profiles; new styles simply become available.

**STY-05 — Custom styles** `Won't`
Users cannot create styles; *Other* + custom profiles covers this.

---

## 5. Brew profiles (PRF) — SCR-12

A **brew profile** is a recipe: method, leaf amount, water volume, base
temperature, optional vessel, and an ordered list of **steps** (rinse,
infusion, whisk) each with a duration and optional temperature override.

**PRF-01 — Profiles per tea** `Must`
A tea has 1–10 profiles; exactly one is the **default**. Profiles are listed on
the tea detail page with the default first.

**PRF-02 — Defaults from style** `Must`
New teas receive copies of their style's default profiles. Teas with style
*Other* receive one generic profile (WESTERN, 3 g, 250 ml, 85 °C, one 3:00
infusion).

**PRF-03 — Edit profile** `Must`
Fields and limits:

| Field | Limits | Notes |
|---|---|---|
| Name | 1–40 chars | e.g. "Houhin", "Ice brew" |
| Method | enum, see [05 §4](05-data-model.md#4-enumerations) | Changing method offers to load the style's defaults for that method |
| Vessel | optional | Selecting a vessel sets water to its capacity (editable) |
| Leaf | 0.1–100 g | step 0.1 g (or 0.5 g if SET-08) |
| Water | 10–3000 ml | step 5 ml |
| Base temperature | 0–100 °C | step 1 °C / 1 °F; "Cold" label for ≤ 10 °C |
| Steps | 1–30 | see PRF-04 |
| Notes | 0–500 chars | |

- AC1: The ratio is shown live as "x.x g / 100 ml" and "1 : n".
- AC2: Water greater than the selected vessel's capacity shows a warning (not an error).

**PRF-04 — Steps editor** `Must`
- Add, remove (swipe or delete icon), reorder (drag handle) steps.
- Each step: kind (Rinse / Infusion / Whisk), duration 1 s – 24 h (picker with
  h:mm:ss; quick chips ±5 s, ±10 s), optional temperature override, optional
  note (≤ 120 chars).
- Rinse steps are numbered separately ("Rinse", then "Infusion 1, 2 …").
- AC1: A profile must contain at least one non-rinse step.

**PRF-05 — Generate a series** `Should`
"Generate infusions": first duration, increment (e.g. +5 s) or multiplier, count
(2–20), optional temperature increment per step → replaces or appends steps.
Typical gongfu use: 10 s + 5 s × 8.

**PRF-06 — Reset to style defaults** `Should`
Replaces a profile's values with the style default for the same method (confirm).

**PRF-07 — Learn from sessions** `Should`
After a session whose actual step durations differ from the plan by more than
max(5 s, 10 %) on any step, the summary (SCR-06) offers "Save today's times to
this profile". Accepting updates the durations (rounded to 5 s, or to 1 min for
steps ≥ 10 min) and the leaf/water used.

**PRF-08 — Duplicate profile** `Should`
Creates "<name> (copy)" on the same tea.

**PRF-09 — Share profile between teas** `Won't`
Profiles belong to one tea. Duplicate the tea or re-enter values instead.

---

## 6. Teaware (WARE) — SCR-13, SCR-14

**WARE-01 — Manage vessels** `Should`
Create, edit, archive, delete vessels: name (1–40), type, capacity (10–3000 ml),
material, notes, up to 3 photos, favourite. Reached from Settings › *My teaware*
and from vessel pickers (*Manage teaware*).

**WARE-02 — Vessel picker** `Should`
Brew setup and the profile editor list favourite vessels first, then others
alphabetically, then *No vessel*.

**WARE-03 — Deleting a vessel** `Should`
Profiles that referenced it keep their water volume and lose the vessel link;
sessions keep the vessel name snapshot.

**WARE-04 — Usable vs. nominal capacity** `Could`
Optional "usable brewing volume" in addition to nominal capacity (a 200 ml
kyusu often brews 150 ml with leaves).

---

## 7. Brew setup & calculator (BRW) — SCR-03, SCR-04

**BRW-01 — Start a brew** `Must`
Entry points: Home *Start steeping* card (opens tea picker SCR-03), *Brew again*
items on Home, tea detail *Brew*, journal session *Brew this again*,
notification/shortcut (Could).
- AC1: From Home, a tea brewed before can be started in ≤ 3 taps
  (Brew again item → *Start brewing* → *Start*).

**BRW-02 — Setup screen** `Must`
SCR-04 shows the tea, profile chips, vessel, water, leaf, ratio lock, base
temperature, step list and *Start brewing*. Changes made here apply **to this
session only** unless the user taps *Save to profile*.

**BRW-03 — Ratio scaling** `Must`
With the ratio lock on (default), changing water or vessel rescales leaf:
`leaf = round(ratio × water / 100, precision)`; changing leaf rescales water
(rounded to 5 ml). With the lock off, values change independently and the ratio
updates. Steep times and temperatures are never scaled.

**BRW-04 — Leaf display in teaspoons** `Should`
If the leaf unit setting is *teaspoons (approx.)*, leaf amounts additionally
show "≈ n tsp" using the tea's (or style's) grams-per-teaspoon factor, rounded
to ¼ tsp. Grams remain the stored value.

**BRW-05 — Cooling helper inline** `Should`
When the base temperature is ≤ 85 °C, setup shows a hint "From boiling: about
*n* transfers between room-temperature vessels" (formula in LRN-06) linking to
the full cooling helper.

**BRW-06 — Brew from a past session** `Should`
*Brew this again* on a journal session pre-fills setup with that session's
leaf, water, temperatures and step durations (actual if PRF-07 rounding
applied, else planned).

**BRW-07 — Pre-brew checklist** `Could`
Collapsible "Before you start" list (warm the vessel, weigh leaf, prepare water
at X °C). Checkbox state is not persisted.

**BRW-08 — Stock warning** `Should`
If inventory is tracked and remaining stock is below the leaf amount, setup
shows "Only *x* g left" (non-blocking).

**BRW-09 — Freshness hint** `Could`
If the tea is past its freshness window, setup shows a subtle hint linking to
the storage guide.

---

## 8. Steep timer & brew sessions (TMR) — SCR-05, SCR-06, SCR-07

Detailed behaviour, states and background execution: [04 · Brew Timer](04-brew-timer.md).

**User stories**
- As a kyusu brewer, I want each infusion to have its own time and temperature, so that I do not have to remember the sequence.
- As anyone, I want the alert to ring even when my phone is locked and in another room.
- As an enthusiast, I want the journal to record how long I actually steeped, including when I poured late.

**TMR-01 — Start session** `Must`
*Start brewing* creates a `BrewSession` (status IN_PROGRESS) and opens the timer
in the READY state for the first step. If SET-12 "Start first step immediately"
is on, the first step starts running right away.

**TMR-02 — Step countdown** `Must`
In RUNNING, the screen shows the remaining time (mm:ss, or h:mm:ss ≥ 1 h) in a
large tabular font inside a thin circular progress ring, the step label
("Infusion 2 of 4"), and the step temperature.

**TMR-03 — Pause / resume** `Must`
Pause freezes remaining time; resume continues from it.

**TMR-04 — Adjust while running** `Must`
Buttons −10 s / +10 s adjust the remaining time. Reducing below zero ends the
step immediately (→ ALERTING).

**TMR-05 — Pour early** `Must`
"Pour now" ends the running step immediately, records the actual time and moves
to READY for the next step (no alert).

**TMR-06 — Alert** `Must`
When a step reaches zero, the app alerts with sound, vibration and a heads-up
notification as configured (SET-09…SET-11), in foreground and background,
including with the screen locked. The screen shows "Pour now", the overtime
counting up ("+0:07"), and a large *Poured* button.

**TMR-07 — Acknowledge ("Poured")** `Must`
*Poured* (in-app or notification action) stops the alert, records the step's
actual duration (running time including overtime) and moves to READY for the
next step, or to FINISHED after the last step.

**TMR-08 — Next-step guidance** `Must`
READY shows the upcoming step's duration and temperature and, if the
temperature differs from the previous step, a hint ("Slightly hotter: 55 °C").

**TMR-09 — Skip step** `Should`
Overflow › *Skip this step* moves to the next step without recording an actual
time (stored as skipped).

**TMR-10 — Add infusion** `Must`
In FINISHED (and via overflow at any time) *Add another infusion* appends a step
copying the last infusion's temperature with duration = last duration + 50 %
(rounded to 5 s); editable before starting.

**TMR-11 — Edit remaining steps** `Should`
Overflow › *Edit steps* opens the steps editor for steps not yet started.
Changes affect this session only.

**TMR-12 — Finish session** `Must`
*Finish & log* (FINISHED state or overflow) sets status COMPLETED, end time, and
opens the session summary (SCR-06).
- AC1: Sessions with zero completed non-rinse steps are discarded instead,
  after confirmation ("Nothing brewed yet — discard session?").

**TMR-13 — Discard session** `Must`
Overflow › *Discard session* (confirm) deletes the session and any inventory
deduction.

**TMR-14 — Auto-finish idle sessions** `Must`
A session left in READY, PAUSED or FINISHED for 60 minutes without interaction
is finished automatically (COMPLETED if ≥ 1 non-rinse step was completed, else
discarded) and a low-priority notification informs the user.

**TMR-15 — Background & lock screen** `Must`
The timer continues when the user leaves the screen, switches app, or locks the
device. An ongoing notification shows the countdown with actions (see NOT-02).

**TMR-16 — Survive process death and reboot** `Must`
Timer state is persisted on every transition; after the process is killed or
the device reboots, opening the app restores the session in the correct state
(see [04 §7](04-brew-timer.md#7-persistence--recovery)).

**TMR-17 — Keep screen on** `Must`
While SCR-05 is visible and the setting is on (default on), the screen does not
time out.

**TMR-18 — Long steeps (cold brew, ice brew)** `Must`
Steps of 30 min or more run in "reminder mode": the user can leave the app; an
ongoing notification shows "Ready at ~14:30"; an alert notification fires at
the end. Precision may be lower (≤ 10 min late) unless exact alarms are
permitted ([04 §6.3](04-brew-timer.md#63-long-steps-reminder-mode)).

**TMR-19 — Matcha whisk steps** `Must`
WHISK steps show a whisk illustration and the text "Whisk briskly in an M/W
motion" (usucha) or "Knead slowly" (koicha); otherwise behave like infusions.

**TMR-20 — Rinse steps** `Must`
RINSE steps are labelled "Rinse — discard the water"; their *Poured* button
reads *Discarded*. They are recorded but excluded from infusion counts and
tasting.

**TMR-21 — Starting a second session** `Must`
Starting a brew while another session/timer is active shows "Finish *Gyokuro*
session first?" with *Finish & start new* / *Cancel*.

**TMR-22 — Quick timer** `Should`
SCR-07: a single countdown without a tea: preset chips 0:30, 1:00, 1:30, 2:00,
3:00, 5:00 and custom duration; same alert behaviour; not logged in the journal.

**TMR-23 — Pre-alert tick** `Could`
Optional soft tick 5 seconds before the end of a step (SET-10).

**TMR-24 — Spoken countdown** `Could`
Optional text-to-speech announcements ("10 seconds", "Pour now") using the
on-device TTS engine.

**TMR-25 — Tabletop posture layout** `Could`
On foldables in tabletop posture, the time shows on the upper half and controls
on the lower half.

**TMR-30 — Timer accuracy** `Must`
Alert fires within 1 s of the target (typical ≤ 250 ms) for steps < 30 min,
with the app in foreground or background and the screen on or off. See
[07 §2](07-non-functional.md#2-performance).

**TMR-31 — Missing permissions banner** `Must`
If notifications are disabled (permission or channel), the timer and the
active-brew bar show "Alerts won't show while the app is closed — *Fix*",
deep-linking to the relevant system settings.

**TMR-40 — Multiple concurrent timers** `Could`
Run up to 3 independent sessions (e.g. a cold brew in the fridge plus a hot
session). Data model already supports it; UI and service do not in 1.0.

**TMR-41 — Live Updates / progress notification** `Could`
On Android versions that support promoted ongoing notifications with progress
("Live Updates"), show the session as a progress-style live update.

---

## 9. Journal & tasting notes (JRN) — SCR-06, SCR-15 … SCR-17, SCR-30

**JRN-01 — Session summary** `Must`
After finishing, SCR-06 shows tea, date/time, method, leaf, water, a table of
steps (planned vs actual, temperature), and tasting inputs. *Done* saves;
*Skip* keeps the session without tasting notes. The session is already
persisted before this screen opens, so leaving it by any means loses nothing.

**JRN-02 — Rating** `Must`
Overall rating 0.5–5.0 in half-star steps; optional. Displayed as "★ 4.5" in
lists (as in the mockup).

**JRN-03 — Taste profile** `Should`
Six optional 0–5 sliders: **Umami, Sweetness, Bitterness, Astringency, Body,
Aroma**. Visualised as a hexagonal radar chart on session and tea detail
(average).

**JRN-04 — Descriptors** `Should`
Multi-select chips from the bundled vocabulary ([08 §3](08-content.md#3-tasting-descriptor-vocabulary)),
grouped (Marine, Vegetal, Sweet, Roasted & nutty, Floral, Fruity, Earthy &
woody, Mouthfeel, Off-notes), searchable; users can add custom descriptors
(1–30 chars). Max 20 per session.

**JRN-05 — Notes** `Must`
Free-text notes up to 5,000 characters, with optional per-step notes (≤ 300
chars each).

**JRN-06 — Water note** `Could`
Free text "Water" (e.g. "Volvic", "filtered tap") with autocomplete from history.

**JRN-07 — Session photos** `Should`
Up to 6 photos per session.

**JRN-08 — Journal list** `Must`
SCR-15 lists sessions newest first, grouped by day with headers ("Today",
"Yesterday", "Monday, 12 October"). Item: thumbnail (session photo → tea cover
→ category illustration), tea name, date · total steep time · base temperature,
rating (as in the mockup).

**JRN-09 — Journal search & filters** `Should`
Search tea names, notes and descriptors. Filters: tea, category, minimum rating,
date range, method, "has notes".

**JRN-10 — Session detail** `Must`
SCR-16 shows everything recorded, with *Edit*, *Delete*, *Brew this again*.

**JRN-11 — Edit session** `Must`
All tasting fields, date/time, leaf, water, notes and step actual times are
editable. Editing leaf amount updates the linked inventory event (INV-04).

**JRN-12 — Manual entry** `Should`
"Log a session" (Journal FAB) creates a MANUAL session without the timer: tea
(or free text name), date/time (default now), method, leaf, water, temperature,
number of infusions + optional durations, and tasting fields.

**JRN-13 — In-progress session in journal** `Must`
The IN_PROGRESS session is shown at the top of the journal with a live badge and
opens the timer.

**JRN-14 — Brew coach suggestions** `Could`
Rule-based tips on the summary screen derived from tasting input (rules in
[08 §7](08-content.md#7-brew-coach-rules)), e.g. "Quite bitter? Try 5 °C cooler next time."

**JRN-15 — Share session as text/image** `Could`
Share sheet with a formatted text summary (and later a styled card image).

**JRN-16 — Calendar view** `Could`
Month calendar with dots on days with sessions.

---

## 10. Inventory & freshness (INV)

**INV-01 — Track stock** `Should`
Per tea, the user can enable stock tracking by entering the current amount
(g). Remaining stock = sum of inventory events ([05 §3.10](05-data-model.md#310-inventoryevent)).

**INV-02 — Auto-deduct on brew** `Should`
When a session is finished (COMPLETED) for a tracked tea and SET-15 is on
(default), an inventory event of −leaf grams is created and linked to the
session. Discarding the session removes it.

**INV-03 — Manual adjustments** `Should`
On tea detail: *Add purchase* (+g, optional price/date), *Set remaining*
(creates a correcting event), and a history list of events (SCR-29).

**INV-04 — Editing / deleting sessions** `Should`
Editing a session's leaf amount updates its deduction; deleting a session
deletes its deduction.

**INV-05 — Stock status** `Should`
Status: *Out* (≤ 0 g), *Low* (≤ threshold; default 10 g, per-tea override),
*In stock*. Negative values are displayed as 0 g with status *Out*.

**INV-06 — Opened date & freshness** `Should`
Fields *Opened on* and *Best before*. Freshness status from the tea's (or
style's) freshness window: *Fresh* (< 75 % elapsed), *Enjoy soon* (75–100 %),
*Past its best* (> 100 %), or *Past best-before*. Shown as a subtle chip; never
red/alarming.
- AC1: When the first session of a tea is finished and *Opened on* is empty,
  the app sets it to today (with a snackbar *Undo*). `Could`

**INV-07 — Low stock & freshness reminders** `Could`
Optional weekly digest notification (off by default): "2 teas are running low;
your shincha is best enjoyed soon."

**INV-08 — Price per cup** `Could`
If price and package size are known: cost per gram and per session.

---

## 11. Insights — "Your Tea Journey" (INS) — SCR-18

**INS-01 — Overview** `Should`
Cards: total teas in collection (+ added this month), sessions this week /
month, current and longest daily streak (consecutive local days with ≥ 1
completed or manual session; shown neutrally, no "streak lost" messaging),
grams brewed this month (if inventory used).

**INS-02 — Ratings overview** `Should`
Average rating and distribution bars by star (1–5, half stars rounded down), as
in the mockup.

**INS-03 — Tea types** `Should`
Share of sessions by category (and by style when filtered to one category),
shown as a donut with legend and percentages, plus an accessible table
alternative.

**INS-04 — Activity over time** `Should`
Sessions per week for the last 12 weeks (bar chart).

**INS-05 — Top teas** `Should`
Top 5 teas by sessions and top 5 by average rating (min. 3 rated sessions).

**INS-06 — Taste preferences** `Could`
Average taste radar across all sessions rated ≥ 4 ("what you love"), and the
most frequent descriptors.

**INS-07 — Time range** `Should`
Range selector: 30 days, 90 days, 12 months, all time (default 90 days).

**INS-08 — Caffeine tracking** `Won't` — see NG4.

---

## 12. Learn & tools (LRN) — SCR-19 … SCR-24

**LRN-01 — Guides** `Should`
Bundled articles ([08 §4](08-content.md#4-learn-guides)) with title, ink-wash
hero illustration, reading time, and body in rich text (headings, paragraphs,
lists, tables, figures). Available offline; no external links required to
understand them.

**LRN-02 — Contextual links** `Should`
Style detail, brew setup and brew coach tips link to relevant guides.

**LRN-03 — Tea styles** `Must` — see STY-02/03.

**LRN-04 — Ratio calculator** `Should`
Inputs: water (or vessel) and ratio (g/100 ml, or pick a style/profile), or
leaf and ratio → computes the missing value. Shows teaspoons if enabled.

**LRN-05 — Unit converter** `Could`
°C↔°F, g↔oz, ml↔fl oz, g↔tsp (per style factor).

**LRN-06 — Cooling helper** `Should`
Inputs: start temperature (default 100 °C), target temperature, drop per
transfer (default 10 °C, adjustable 5–15 °C, with explanation).
Output: `transfers = ceil((start − target) / drop)` with a visual of pouring
between vessels and the temperature after each transfer. Disclaimer: "An
estimate — vessels, room temperature and volume all matter. A thermometer or
variable-temperature kettle is most accurate."

**LRN-07 — Tip of the day** `Could`
Home shows one short tip from the guides, rotating daily; dismissible.

---

## 13. Settings (SET) — SCR-25

| ID | Setting | Values / default | Priority |
|---|---|---|---|
| SET-01 | Temperature unit | °C / °F — from locale | Must |
| SET-02 | Weight unit | g / oz — from locale | Must |
| SET-03 | Volume unit | ml / fl oz (US) — from locale | Must |
| SET-04 | Show leaf in teaspoons too | on / off — off | Should |
| SET-05 | Theme | System / Light / Dark — System | Must |
| SET-06 | Use wallpaper colours (dynamic colour, Android 12+) | on / off — **off** (brand palette) | Could |
| SET-07 | App language | Android 13+ per-app language (system settings link) | Should |
| SET-08 | Leaf precision | 0.1 g / 0.5 g — 0.1 g | Should |
| SET-09 | Alert sound | Bundled tones (Bell, Singing bowl, Wood block, Chime, Soft) / Silent — Singing bowl | Must |
| SET-10 | Pre-alert tick 5 s before end | on / off — off | Could |
| SET-11 | Vibration | on / off — on | Must |
| SET-12 | Start first step immediately | on / off — off | Should |
| SET-13 | Alert loudness mode | "Ring even in silent mode" (alarm stream) / "Follow ringer mode" (notification stream) — Ring | Should |
| SET-14 | Alert length | Short (1×) / Normal (up to 15 s) / Long (until acknowledged, max 2 min) — Normal | Should |
| SET-15 | Deduct leaf from stock after brewing | on / off — on | Should |
| SET-16 | Default low-stock threshold | 0–500 g — 10 g | Should |
| SET-17 | Keep screen on during brewing | on / off — on | Must |
| SET-18 | Offer to save adjusted times (PRF-07) | on / off — on | Should |
| SET-19 | Weekly stock & freshness digest (INV-07) | on / off — off | Could |
| SET-20 | Backup reminder | Off / Monthly / Weekly — Monthly | Should |
| SET-21 | Precise reminders for long steeps (exact alarm access) | Link to system "Alarms & reminders" setting, shows status | Could |

**SET-30 — Sections** `Must`
Settings are grouped: *Units*, *Timer & alerts*, *Brewing*, *Stock*,
*Appearance*, *Teaware* (→ SCR-13), *Data* (→ SCR-26, tags, remove samples,
delete all), *About* (→ SCR-27).

**SET-31 — Test alert** `Should`
*Timer & alerts* has *Play test alert* using current sound/vibration/stream.

**SET-32 — Notification channel shortcuts** `Should`
Links to the system notification settings of each channel.

**SET-33 — Delete all data** `Must`
Two-step confirmation (type "DELETE") wipes database, photos, settings, and
returns to onboarding.

---

## 14. Backup, restore & export (BAK) — SCR-26

Format: [05 §8](05-data-model.md#8-backup-file-format).

**BAK-01 — Create backup** `Must`
*Back up now* writes a single `.umamitea` file (ZIP) containing all data,
settings and photos, to a location chosen with the system file picker
(Storage Access Framework). Progress is shown; the app records the last backup
date.

**BAK-02 — Restore backup** `Must`
*Restore* picks a file, validates it, shows a preview (date, app version,
counts of teas/sessions/photos) and replaces **all** current data after
confirmation.
- AC1: Before replacing, an automatic safety backup of current data is written
  to app-private storage (last one kept) and can be restored from the same
  screen.
- AC2: Invalid, corrupt, or newer-format files are rejected with a clear
  message; existing data is untouched.
- AC3: Restore is atomic: on any failure, the previous state remains.

**BAK-03 — Merge restore** `Could`
Import a backup and merge by ID (newer `updatedAt` wins).

**BAK-04 — CSV export** `Should`
Exports `teas.csv`, `sessions.csv`, `steps.csv` (zipped) for spreadsheets.

**BAK-05 — OS backup** `Should`
The database and settings participate in Android Auto Backup and
device-to-device transfer; photos are excluded (25 MB limit). Explained on
SCR-26.

**BAK-06 — Backup reminder** `Should`
If SET-20 is on and the last backup is older than the interval and there are
≥ 5 sessions since, a low-priority notification suggests backing up.

**BAK-07 — Share backup** `Could`
*Back up & share* sends the backup file via the share sheet (e.g. to a cloud
drive app) — the app itself never uploads anything.

---

## 15. Notifications (NOT)

Channels and content: [04 §8](04-brew-timer.md#8-notifications).

**NOT-01 — Channels** `Must`
Separate channels: *Brew timer* (ongoing, silent), *Steep finished* (high
importance), *Cold brew & long steeps*, *Reminders*, *Session updates*. Created
on first launch with localized names and descriptions.

**NOT-02 — Ongoing timer notification** `Must`
Shows tea name, step, countdown (system chronometer), and actions depending on
state: RUNNING → *Pause*, *+10 s*; PAUSED → *Resume*; READY → *Start infusion n*;
ALERTING → *Poured*. Tapping opens SCR-05.

**NOT-03 — Lock screen** `Must`
Timer notifications are public on the lock screen (tea names are not
sensitive); the user can change this via system settings.

**NOT-04 — Wearables** `Should`
Alerts bridge to paired watches with the *Poured* action available.

**NOT-05 — No marketing notifications** `Must`
The app never sends promotional notifications.

**NOT-06 — App shortcuts** `Could`
Launcher long-press shortcuts: *Quick timer*, and dynamic shortcuts for the 3
most recently brewed teas.

**NOT-07 — Quick Settings tile** `Could`
Tile to start the quick timer / show the active session.

**NOT-08 — Home screen widget** `Could`
Glance widget: active session countdown or "Brew again" for 2–3 recent teas.
