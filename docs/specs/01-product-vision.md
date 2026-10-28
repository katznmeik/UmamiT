# 01 · Product Vision & Scope

> Status: **Draft v0.2** · Last updated: 2026-10-21
> v0.2 reflects the product-owner interview of 2026-10-21 (personal use, German,
> Tee-Kontor catalog, timer details). See [10 · Decisions](10-open-questions-and-decisions.md).

## 1. Elevator pitch

**UmamiT** is a **personal** Android app for its owner's daily tea ritual,
with a love for umami-rich Japanese green teas (gyokuro, kabusecha, sencha,
matcha). It combines:

1. a **multi-infusion steep timer** that is reliable on a locked phone,
   with a pre-alert tick and a Live Update on the lock screen,
2. a **catalog of all green teas from Tee-Kontor Kiel**
   ([tee-kontor-kiel.de](https://www.tee-kontor-kiel.de/)) with their
   descriptions, photos and brewing guides, refreshable from inside the app,
3. a **personal collection** of the teas actually owned, with stock and cost,
4. an **automatic journal** of every session, and
5. **statistics** about brewing activity, ratings, tea types and consumption.

Tagline (from the brand mockup): **"Better tea. Deeper moments."**
German UI tagline: **„Besserer Tee. Tiefere Momente."**
Japanese tagline: **「お茶で、いい時間を。」**

## 2. Audience

**One user: the product owner (PO).** The app is for personal use only and is
**not published** on Google Play or any other store.

| Fact | Consequence for the spec |
|---|---|
| Single user, single device | No multi-user, accounts or sync. Requirements are tuned to the PO's habits. |
| Samsung Galaxy, Android 17 / One UI 9 (Android 16 / One UI 8 until 2026-10-28) | Minimum Android 15 (API 35). Samsung battery management must be handled. Android 16 Live Updates and the Samsung Now Bar are used. |
| Brews in mixed styles (kyusu, mug, gongfu, cold brew) | Timer is step-based and style-agnostic. |
| Has a kettle adjustable in 5 °C steps | All temperatures are expressed and snapped to 5 °C. No water-cooling helper. |
| German speaker; buys from Tee-Kontor Kiel | German UI and content; metric units only; Tee-Kontor catalog as the primary data source. |
| Not a developer of this app; Claude builds it | Installation via signed APK from GitHub Releases; work delivered as one PR per milestone. |

## 3. Problem statement

| Problem | Today's workaround |
|---|---|
| Multi-infusion brewing (3–5 short steeps at changing temperatures) is hard to follow with a generic timer. | Phone clock app, memory |
| Picking up the kyusu in time for the pour needs a warning *before* the end. | Watching the screen |
| Brewing parameters for each Tee-Kontor tea are scattered over product pages. | Opening the shop website each time |
| No overview of which teas are owned, how much is left, and what a cup costs. | Nothing |
| No record of what was brewed, how often, and how it tasted. | Nothing |

## 4. Product principles

1. **The timer is sacred.** It must never miss an alert. Reliability beats
   features.
2. **Calm over clever.** The brand mockup's quiet, premium aesthetic; few
   controls; no gamification pressure.
3. **Zero-effort logging.** Sessions log themselves; notes are optional.
4. **Brew first.** From launch to a running timer in ≤ 3 taps for a known tea.
5. **Local-first.** All personal data stays on the phone. Network access goes
   only to tee-kontor-kiel.de: the user-triggered catalog refresh, a one-time
   seed photo download and exact prices when adding a package (D-036).
6. **Starting points, not rules.** Imported recipes are defaults; every value
   can be changed, and the app offers to save adjusted times.

## 5. Goals and non-goals

### 5.1 Goals

| ID | Goal |
|---|---|
| G1 | A multi-infusion timer the PO trusts completely on a locked Samsung phone, with a configurable pre-alert and a Live Update. |
| G2 | Every Tee-Kontor green tea available offline with its brewing guide, converted into a ready-to-run timer recipe. |
| G3 | A personal collection with stock, cost per cup and per-tea recipes. |
| G4 | Automatic journaling and meaningful statistics with no manual effort. |
| G5 | The calm, premium look of the brand mockup, in German. |

### 5.2 Non-goals

| ID | Non-goal |
|---|---|
| NG1 | Publishing on Google Play or any store; store listings, policies, beta tests. |
| NG2 | Accounts, cloud sync, multiple users or devices. |
| NG3 | Social features. |
| NG4 | Ordering or buying from Tee-Kontor inside the app (the app links to the product page at most). |
| NG5 | Caffeine/health tracking or health claims. |
| NG6 | Non-green teas from Tee-Kontor (black, oolong, …) in the first catalog import. Teas of any kind can still be added manually. |
| NG7 | Multiple concurrent timers. |
| NG8 | Languages other than German; imperial units. |

## 6. Scope overview

Detailed in [02 · Functional Requirements](02-functional-requirements.md).

| Area | Summary |
|---|---|
| Steep timer | Multi-step recipes, pause, pre-alert tick, alert that follows silent mode, Live Update, exact alarms, recovery |
| Tee-Kontor catalog | Import/refresh all green teas (incl. flavoured), photos, brewing guides, *Tee-Info* guides |
| Collection | Teas owned (from catalog or manual), package size → stock and price, recipes, pre-alert override |
| Teaware | Vessels with volume; leaf amount scales to the chosen vessel |
| Journal | Auto-logged sessions; optional rating, taste profile, notes, photos |
| Statistics | Activity, ratings & taste, tea types, consumption & cost |
| Settings & backup | Timer & alerts, appearance, catalog, Android device backup, manual export |

## 7. Success criteria

There is no analytics. Success means:

- The PO uses UmamiT for every hot-tea session for 4 weeks after the first
  full release.
- Zero missed or late alerts reported by the PO.
- The catalog refresh works without manual fixes for at least 3 months, or
  breaks visibly (with a clear error) when the shop's site changes.

## 8. Constraints

- **C1** Built by Claude in cloud sessions; the PO tests on the phone. Every
  milestone needs an installable APK.
- **C2** Development starts **only after the PO's explicit go**; until then
  only the specifications are worked on.
- **C3** The catalog depends on the structure of tee-kontor-kiel.de, which can
  change at any time without notice.
- **C4** ✅ The cloud environment allows `www.tee-kontor-kiel.de` (verified
  2026-10-21); the shop uses Cloudflare, so refreshes must stay polite.

## 9. Related documents

[02 Functional Requirements](02-functional-requirements.md) ·
[03 UX & UI](03-ux-ui.md) · [04 Brew Timer](04-brew-timer.md) ·
[05 Data Model](05-data-model.md) · [06 Architecture](06-architecture.md) ·
[07 Non-Functional](07-non-functional.md) · [08 Content & Catalog Import](08-content.md) ·
[09 Delivery Plan](09-release-plan.md) · [10 Decisions](10-open-questions-and-decisions.md) ·
[11 Glossary](11-glossary.md)
