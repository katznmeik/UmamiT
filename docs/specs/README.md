# UmamiTea — Specifications

> Status: **Draft v0.1 — for review** · Last updated: 2026-10-21

This folder holds the complete product and technical specification of
**UmamiTea**, an offline Android companion for brewing umami-rich tea:
a tea collection, brewing guidance, a multi-infusion steep timer and a
tasting journal. Development starts only after these documents are reviewed
and the open questions marked *Needed by M0* are answered.

## Documents

| # | Document | What it answers |
|---|---|---|
| 01 | [Product Vision & Scope](01-product-vision.md) | Why, for whom, goals, non-goals, success metrics |
| 02 | [Functional Requirements](02-functional-requirements.md) | What the app does — every feature with ID, priority and acceptance criteria |
| 03 | [UX & UI Specification](03-ux-ui.md) | Navigation, design system (from the brand mockup), every screen |
| 04 | [Brew Timer Specification](04-brew-timer.md) | State machine, background execution, notifications, alerts, recovery |
| 05 | [Data Model & Backup Format](05-data-model.md) | Entities, enums, settings, units, backup/CSV formats, migrations |
| 06 | [Technical Architecture](06-architecture.md) | Stack, modules, libraries, testing, CI, build |
| 07 | [Non-Functional Requirements](07-non-functional.md) | Performance, reliability, accessibility, privacy, security, compatibility |
| 08 | [Content Specification](08-content.md) | Tea style catalog & brewing defaults, descriptors, guides, sample data |
| 09 | [Release Plan & Roadmap](09-release-plan.md) | Milestones, MVP, beta, launch checklist, post-1.0 roadmap, risks |
| 10 | [Open Questions & Decision Log](10-open-questions-and-decisions.md) | What still needs deciding, and what has been decided and why |
| 11 | [Glossary](11-glossary.md) | Tea and technical terms |

## Key decisions at a glance

- **Concept:** brewing companion (collection + guidance + timer + journal),
  umami-focused, works for all teas. — D-002
- **Offline only:** no accounts, no backend, no `INTERNET` permission, no
  analytics. — D-001, D-006
- **Stack:** Kotlin, Jetpack Compose, Material 3, Room, DataStore, Hilt; min
  SDK 26. — D-003, D-005
- **Timer:** foreground service + wake lock for short steps, alarms for long
  cold brews; survives process death and reboot. — D-004
- **Look & feel:** calm washi-paper neutrals, deep matcha green, serif display
  type, ink-wash illustrations, dark timer screen. — [03 §1](03-ux-ui.md#1-design-reference--brand-mockup)
- **Navigation:** Home · Journal · Collection · Learn (stats inside Journal) —
  pending Q-07.

## How to read and maintain these specs

- Requirement IDs (`TMR-06`, `NFR-PERF-07`, `SCR-05`, `D-004`, `Q-07`) are
  stable; reference them in issues, PRs and commits.
- Priorities use MoSCoW: **Must** = MVP/beta, **Should** = 1.0, **Could** =
  backlog, **Won't** = out of scope for 1.x.
- Specs are living documents: behaviour changes update the relevant document in
  the same PR, and decisions are added to the decision log.
