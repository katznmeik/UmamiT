# UmamiT — Specifications

> Status: **Draft v0.2 — for review** · Last updated: 2026-10-21

Product and technical specification of **UmamiT**, a personal Android app
for brewing tea, focused on umami-rich Japanese green teas. It provides a
multi-infusion steep timer, a catalog of Tee-Kontor Kiel's green teas, a
personal collection with stock and cost, an automatic journal and
statistics.

**Development has not started.** It begins only after the product owner's
explicit go ([D-024](10-open-questions-and-decisions.md#d-024)).

## Documents

| # | Document | What it answers |
|---|---|---|
| 01 | [Product Vision & Scope](01-product-vision.md) | Why, for whom, goals, non-goals, constraints |
| 02 | [Functional Requirements](02-functional-requirements.md) | Every feature with ID, priority and acceptance criteria |
| 03 | [UX & UI Specification](03-ux-ui.md) | Navigation, design system (from the brand mockup), every screen, German copy |
| 04 | [Brew Timer Specification](04-brew-timer.md) | State machine, pre-alert, alerts, Live Update, background execution, recovery |
| 05 | [Data Model & Backup Format](05-data-model.md) | Personal and catalog tables, enums, settings, backup |
| 06 | [Technical Architecture](06-architecture.md) | Stack, modules, catalog refresh, testing, CI, GitHub Releases |
| 07 | [Non-Functional Requirements](07-non-functional.md) | Performance, reliability, accessibility, privacy, security |
| 08 | [Content & Catalog Import](08-content.md) | Tee-Kontor import rules, recipe derivation, built-in styles, descriptors |
| 09 | [Delivery Plan & Roadmap](09-release-plan.md) | Milestones, prerequisites, acceptance checklists, risks |
| 10 | [Open Questions & Decision Log](10-open-questions-and-decisions.md) | What is still open, what was decided and why |
| 11 | [Glossary](11-glossary.md) | Tea and technical terms |
| 12 | [Subagent Distribution Plan](12-agent-plan.md) | Which model and effort Claude's subagents use for which tasks |

Design reference: [`docs/design/mockup-v2-board.png`](../design/mockup-v2-board.png)
and [`mockup-v2-start.png`](../design/mockup-v2-start.png) (current), and
[`brand-mockup.png`](../design/brand-mockup.png) (original mood).
Prompt for new mockups: [`docs/design/mockup-prompt.md`](../design/mockup-prompt.md).
Image assets to create, with prompts: [`docs/design/assets.md`](../design/assets.md).

Working documents: [`CLAUDE.md`](../../CLAUDE.md) (rules for every Claude
session), [`PROJECT_STATE.md`](../../PROJECT_STATE.md) (current status and
handover), [`docs/plans/`](../plans/) (milestone implementation plans, e.g.
[M1](../plans/M1-timer.md)).

## Key decisions at a glance

- **Personal app:** one user, Samsung Galaxy on Android 16. Sideloaded from
  GitHub Releases, no store. — D-013
- **German only,** metric units, temperatures in 5 °C steps. — D-017, D-025
- **Timer:**
  - waits for your tap between infusions
  - Pause is the only control while running
  - pre-alert tick: global default 10 s, overridable per recipe
  - alert follows the ringer mode; full-screen alert turns the screen on
  - Live Update on lock screen (Now Bar best effort)
  - exact alarms plus a foreground service
  - one timer at a time
  - — D-019, D-036
- **Catalog:** all green teas from tee-kontor-kiel.de, refreshed in the app.
  Recipes are derived from the shop's brewing guides; teas whose recipe needs
  checking are flagged. — D-014, D-015, D-021
- **Network:** only to tee-kontor-kiel.de — catalog refresh, one-time seed
  photo download, exact prices when adding a package. — D-014, D-036
- **Journal:** sessions are logged automatically; notes are optional. — D-020
- **Backup:** Android device backup plus a simple manual export; no in-app
  restore in 1.x. — D-022, D-036
- **Navigation:** Start · Journal · Sammlung · Statistik. — D-018
- **Stack:** Kotlin, Jetpack Compose, Material 3, Room, Hilt, OkHttp and
  Jsoup; min SDK 35. — D-003, D-023
- **Delivery:** timer first, starting with a probe release; one PR per
  milestone; release candidates for testing. — D-024, D-036
- **Subagents:** simple tasks use Sonnet at low effort; complex ones use Opus
  at medium, high or extra-high effort, never max. — D-026

## Conventions

- Stable IDs: requirements (`TMR-23`), non-functional (`NFR-PERF-07`), screens
  (`SCR-05`), decisions (`D-019`), questions (`Q-01`).
- Priorities: **Must**, **Should**, **Could**, **Won't**.
- Specs are living documents. Behaviour changes update them in the same PR.
