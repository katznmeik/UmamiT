# 01 · Product Vision & Scope

> Status: **Draft v0.1** · Owner: product · Last updated: 2026-10-21

## 1. Elevator pitch

**UmamiTea** is an offline-first Android companion for people who want to brew
tea *well*, with a special love for umami-rich teas such as gyokuro, kabusecha,
sencha and matcha. It combines:

1. a **personal tea collection** (what you own, how much is left, how fresh it is),
2. **brewing guidance** with sensible starting parameters for 25+ tea styles,
3. a **multi-infusion steep timer** that keeps working when the phone is locked, and
4. a **tasting journal** that helps you learn what you like and brew it again.

Tagline (from the brand mockup): **"Better tea. Deeper moments."**
Japanese tagline: **「お茶で、いい時間を。」** ("A good moment, with tea.")

## 2. Problem statement

| Problem | Who feels it | Today's workaround |
|---|---|---|
| Delicate green teas (especially gyokuro) turn bitter and astringent when brewed with water that is too hot or for too long; the "umami" that people paid for never shows up. | Beginners and intermediate drinkers | Package instructions (often vague or only in Japanese), blog posts, guessing |
| Multi-infusion brewing (2–8 short steeps with changing temperatures) is hard to track with a generic kitchen timer. | Anyone brewing kyusu or gongfu style | Phone clock app, mental arithmetic, sticky notes |
| Tea drinkers forget which parameters produced a great cup and cannot reproduce it. | Enthusiasts | Paper notebooks, spreadsheets, notes apps |
| Green tea goes stale weeks after opening; people forget what they own and how old it is. | Enthusiasts with many teas | Nothing / memory |

## 3. Vision

> *Every cup of tea you brew with UmamiTea is a little better than the last,
> and the app itself feels as calm as the tea.*

The product should feel like a **quiet, beautiful tea notebook** rather than a
productivity tool: warm paper tones, ink-wash landscapes, generous whitespace,
and no nagging. It is opinionated about how umami teas should be brewed, but
never stands in the way of the user's own preferences.

## 4. Product principles

1. **Calm over clever.** Few, large, obvious controls; no gamification pressure,
   no streak-shaming, no ads, no pop-ups. Notifications only when they are useful.
2. **Brew first.** From launch to a running timer must take **≤ 3 taps** for a
   tea the user has brewed before.
3. **Hands are wet.** The timer must be usable with one thumb, at arm's length,
   with big touch targets, and must alert reliably while the phone is locked.
4. **Your data stays on your phone.** No account, no server, no tracking, no
   `INTERNET` permission. Export is always possible.
5. **Teach, don't lecture.** Guidance appears in context ("Water at 50 °C is
   about 5 transfers from boiling") and the deeper "why" lives in the Learn tab.
6. **Starting points, not rules.** Built-in parameters are defaults; every value
   can be changed, and the app learns the user's adjusted times.
7. **Accessible and inclusive.** Works with TalkBack, large fonts, metric and
   imperial units, light and dark themes.

## 5. Target users & personas

The personas are fictional composites used to make design decisions.

### P1 — "The Curious Beginner" (Mira, 28)

- Recently bought a first bag of gyokuro after tasting it in a tea shop.
- Owns a kettle without temperature control and a mug with an infuser.
- **Goals:** not to "ruin" an expensive tea; understand why temperature matters.
- **Frustrations:** contradictory instructions online; no idea how to get 50 °C water.
- **Needs from UmamiTea:** guided setup, cooling helper, simple timer, gentle explanations.

### P2 — "The Enthusiast" (Kenji, 41)

- Owns 30–60 teas, several kyusu, a houhin, a gaiwan and a scale with 0.1 g precision.
- Brews 2–4 sessions per day, mostly Japanese greens and some oolong.
- **Goals:** reproduce great sessions, compare harvests/cultivars, track what is running low.
- **Frustrations:** generic timers do not handle 4 infusions at different temperatures; spreadsheets are tedious.
- **Needs:** per-tea brewing profiles, precise multi-step timer, rich journal, inventory & freshness.

### P3 — "The Daily Ritualist" (Sam, 35)

- Drinks the same sencha every morning and a matcha in the afternoon.
- **Goals:** a quick, reliable timer with saved presets; a calm moment.
- **Needs:** one-tap "brew again", lock-screen alerts, optional quick rating.

## 6. Goals and non-goals

### 6.1 Goals for 1.0

| ID | Goal |
|---|---|
| G1 | Help users brew umami-rich teas correctly through per-style default parameters and contextual guidance. |
| G2 | Provide the most pleasant, reliable multi-infusion steep timer on Android (works locked, survives process death). |
| G3 | Let users build a personal tea collection with brewing profiles, stock and freshness. |
| G4 | Let users journal sessions and see which teas and parameters they enjoy most. |
| G5 | Be fully functional offline, with no account and with complete user-controlled backup/export. |
| G6 | Deliver the calm, premium aesthetic defined in the brand mockup (see [03 · UX & UI](03-ux-ui.md)). |

### 6.2 Non-goals for 1.x

| ID | Non-goal | Rationale |
|---|---|---|
| NG1 | User accounts, cloud sync, multi-device sync | Explicit product decision: offline only (see [10 · Decisions](10-open-questions-and-decisions.md#d-001)). |
| NG2 | Social features (following, sharing feeds, comments) | Requires backend; conflicts with calm/privacy principles. |
| NG3 | Buying tea / e-commerce / vendor integrations | Requires network and partnerships. |
| NG4 | Caffeine or health tracking, health claims | Unreliable estimates; health claims are legally and ethically sensitive. |
| NG5 | Bluetooth scales / smart kettles | Hardware integration cost; possible future. |
| NG6 | iOS, web, Wear OS app | Android phones & tablets first. Notifications bridge to watches automatically. |
| NG7 | Ads, trackers, analytics SDKs | Privacy principle; no `INTERNET` permission. |

## 7. Scope overview

Feature areas (detailed in [02 · Functional Requirements](02-functional-requirements.md)):

| Area | Summary | 1.0? |
|---|---|---|
| Onboarding | Welcome, units, notification permission, optional sample teas | Yes |
| Collection (teas) | CRUD, photos, styles, tags, search/filter/sort, archive | Yes |
| Tea style catalog | 25+ built-in styles with descriptions, umami level and default brew profiles | Yes |
| Brew profiles | Per-tea multi-step recipes (method, leaf, water, temperatures, steps) | Yes |
| Teaware | Vessels with capacities, used to scale recipes | Yes |
| Brew setup & calculator | Ratio scaling, unit display, cooling helper | Yes |
| Steep timer | Multi-infusion, background-safe, notifications, quick timer, long cold brews | Yes |
| Journal | Sessions with actual times, rating, taste profile, descriptors, notes, photos | Yes |
| Inventory & freshness | Stock tracking with auto-deduction, opened date, freshness hints | Yes |
| Insights ("Your Tea Journey") | Simple personal statistics and charts | Yes |
| Learn | Guides on umami & brewing, style reference, tools | Yes |
| Settings | Units, timer & alerts, appearance, data | Yes |
| Backup / restore / CSV export | Full local backup file, CSV export, OS auto-backup | Yes |
| Widgets, QS tile, Live Updates, brew coach | Post-1.0 candidates | Later |

## 8. Success metrics

Because the app ships **without analytics**, success is measured only through
signals that do not require in-app tracking:

| Metric | Source | Target (6 months after 1.0) |
|---|---|---|
| Store rating | Google Play Console | ≥ 4.6 average |
| Crash-free users | Play Console › Android vitals | ≥ 99.5 % |
| ANR rate | Play Console › Android vitals | < 0.2 % |
| 30-day retained installers | Play Console › Statistics | ≥ 35 % |
| Uninstall rate within 7 days | Play Console | < 25 % |
| Qualitative feedback | Reviews, feedback e-mails | Timer reliability praised; no "alarm didn't ring" reports |

Pre-launch qualitative goals (closed testing):

- 10 of 12+ testers complete a full session (setup → timer → journal) unaided.
- Zero reports of a missed steep alert with the screen locked.
- System Usability Scale (SUS) questionnaire score ≥ 80 from testers.

## 9. Key assumptions & constraints

- **A1** Users own a kettle; many cannot set a temperature → cooling helper is valuable.
- **A2** Most sessions are short multi-infusion steeps (< 10 min per step); some are long cold brews (1–12 h).
- **A3** Users are willing to manually enter tea details (no barcode database exists for artisan teas).
- **C1** No network access: all content (styles, articles, images, fonts) is bundled.
- **C2** Single developer or small team; scope must fit roughly 17–18 weeks to 1.0, including the closed beta (see [09 · Release Plan](09-release-plan.md)).
- **C3** Google Play policies (target API level, foreground-service types, data safety) apply.

## 10. Related documents

- [02 · Functional Requirements](02-functional-requirements.md)
- [03 · UX & UI Specification](03-ux-ui.md)
- [04 · Brew Timer Specification](04-brew-timer.md)
- [05 · Data Model & Backup Format](05-data-model.md)
- [06 · Technical Architecture](06-architecture.md)
- [07 · Non-Functional Requirements](07-non-functional.md)
- [08 · Content Specification](08-content.md)
- [09 · Release Plan & Roadmap](09-release-plan.md)
- [10 · Open Questions & Decision Log](10-open-questions-and-decisions.md)
- [11 · Glossary](11-glossary.md)
