# 12 · Subagent Distribution Plan

> Status: **Draft v0.2** · Last updated: 2026-10-31 (M3 in two releases, v0.4.0 and v0.4.1, D-100)
> Decision: [D-026](10-open-questions-and-decisions.md#d-026)

UmamiT is built by Claude ([D-024](10-open-questions-and-decisions.md#d-024)).
A main **orchestrating session** plans each milestone, splits it into tasks,
and delegates tasks to **subagents**. This document defines which **model**
and which **reasoning effort** each kind of task gets, so that simple work is
cheap and fast and complex work gets the depth it needs.

## 1. Principles

1. **Match effort to difficulty.** Simple, well-specified tasks use low
   effort; complex or risky tasks use high effort.
2. **Simple tasks use Sonnet.** Moderate and harder tasks use Opus.
3. **The ceiling is `xhigh` (extra high).** `max` is not used.
4. **Risk raises the tier, size does not.** A small change to the timer's
   alarm handling is riskier than a large but mechanical screen, so it gets the
   higher tier.
5. **Verify everything.** The orchestrator builds, runs tests and reviews
   every subagent result before it goes into a PR. A subagent's own report is
   not proof that the work is correct.
6. **Escalate, never silently downgrade.** If a task fails or turns out to be
   harder than expected, re-run it one tier up (§4).

## 2. Tiers

| Tier | Model | Effort | Use for |
|---|---|---|---|
| **T1 Simple** | **Sonnet** | `low` | Mechanical, fully specified, low-risk changes with an obvious correct result |
| **T2 Standard** | Opus | `medium` | Normal feature work following established patterns in the codebase |
| **T3 Complex** | Opus | `high` | Non-trivial logic, platform integration, data integrity, anything with many edge cases |
| **T4 Critical** | Opus | `xhigh` | Highest risk and hardest reasoning: timer reliability, architecture, hard bugs, final reviews |

The orchestrating session itself runs on Opus at the session's configured
effort.

### 2.1 How to choose a tier

Answer the questions in order and take the first tier that matches:

1. Could a mistake cause a **missed or late timer alert, data loss, or
   corrupted data**? → **T4** for design and debugging, at least **T3** for
   implementation.
2. Does it involve **non-trivial algorithms, Android platform behaviour
   (alarms, services, notifications, backup), concurrency, parsing untrusted
   input, or migrations**? → **T3**.
3. Is it **regular feature code** (screen + ViewModel + repository) following an
   existing pattern? → **T2**.
4. Is it **mechanical**, with the exact result described (rename, strings,
   formatting, boilerplate, docs, simple tests)? → **T1** (Sonnet, low).

## 3. Task catalogue

### 3.1 Cross-cutting

| Task | Tier |
|---|---|
| Fix formatting, lint and detekt findings with no logic change | T1 |
| Add or translate string resources; copy text changes | T1 |
| Import icons and vector assets; resource renames | T1 |
| Update specs or docs after a decision that has been made | T1 |
| Dependency version bumps with green CI | T1 |
| Test data builders and fakes | T1 |
| Straightforward unit tests for simple functions | T1 |
| Standard Compose screen from spec using existing components | T2 |
| ViewModel + repository wiring for a CRUD feature | T2 |
| Room entity and DAO for simple tables | T2 |
| Settings screen and DataStore fields | T2 |
| Compose UI tests and screenshot tests for a feature | T2 |
| CI and release workflows (GitHub Actions, signing) | T3 |
| Room migrations and migration tests | T3 |
| Accessibility pass (semantics, TalkBack, font scale) | T2 |
| Performance work (Baseline Profile, jank analysis) | T3 |
| Code review of a T1/T2 change | T2 |
| Code review of a T3 change | T3 |
| Security review (backup import, HTML sanitising, network allow-list) | T4 |
| Root-cause analysis of a bug the PO reports from the phone | T3, or T4 if timer-related or not reproducible |

### 3.2 By milestone

**M1 — Timer first**

| Task | Tier |
|---|---|
| Gradle project skeleton, version catalog, convention plugins, **all** module stubs (before parallel work) | T2 |
| Probe release `v0.0.1-probe` (exact alarm, FGS, Live Update, full-screen alert, synthesised sound) | T3 |
| CI emulator job (GitHub Actions, API 35/36) | T3 |
| Design system: colour tokens, typography (Cormorant Garamond), shapes, core components | T2 |
| Navigation shell with placeholder tabs | T1 |
| `BrewTimerEngine` state machine + exhaustive unit tests | T3 (design reviewed at T4) |
| Background strategy implementation: exact alarms, FGS, wake lock, receivers, boot recovery | T4 |
| Live Update notification (ProgressStyle, promotion, fallback) | T3 |
| `AlertPlayer` (ringer-mode matrix, audio focus, pre-alert tick) | T3 |
| Persistence & recovery of `active_brew` | T4 |
| Timer screen UI (all states, overflow per state) | T2 |
| Schritte-Sheet (SCR-31, session-only step editing) | T2 |
| App icon (adaptive + monochrome) and status-bar small icon | T1 |
| Diagnostics log (timer events with scheduled vs actual times) | T3 |
| Reliability setup page and banner (battery, notifications, Live Update checks) | T2 |
| Quick timer | T2 |
| Minimal manual tea and recipe creation for testing | T2 |
| Synthesised sounds (ffmpeg generator script, OGG) and integration | T2 |
| Milestone review before the PR | T4 |

**M2 — Tee-Kontor catalog**

| Task | Tier |
|---|---|
| Re-check parser selectors against the live site (structure verified in [08 §2](08-content.md#2-tee-kontor-catalog-import)) | T2 |
| HTTP client: host allow-list, rate limiter, robots.txt, retries | T3 |
| `TeeKontorParser` (selectors, properties, variants, images) | T3 |
| `RecipeDeriver` (extraction, normalisation, flags, cold brew) | T4 (complex rules and the most visible risk to recipe quality) |
| Style mapping table | T1 |
| Synthetic HTML fixtures and parser tests | T2 |
| Refresh service (own `dataSync` FGS), staging tables, atomic swap, sanity checks | T3 |
| Photo download and caching | T2 |
| Tee-Info guide conversion and Markdown rendering | T3 (sanitising untrusted HTML) |
| Catalog list and detail screens, add-to-collection sheet | T2 |
| Milestone review | T3 |

**M3 — Collection** (two releases, [D-100](10-open-questions-and-decisions.md#d-100); tickets in
[`docs/plans/v0.4.0.md`](../plans/v0.4.0.md))

*v0.4.0 — brewing*

| Task | Tier |
|---|---|
| Data semantics: delete a tea with its photo files, restore, the recipe default invariant (one default, ≤ 10, promotion on delete), INITIAL event on manual add, TEA-17 clearing | T3 |
| Built-in style rows of the other categories; list search, filters and sort as pure functions in `core:domain` | T2 |
| *Meine Tees* (SCR-08), tea detail (SCR-09), tea editor and *Sorte wählen* (SCR-10, SCR-11) | T2 |
| Recipe editor (SCR-12: drag reorder, step kinds, several recipes; no series generator) | T2 (T3 where it changes the setup's save path) |
| Teaware list and editor (SCR-13, SCR-14) and the settings entry | T2 |
| Setup changes: ⋮ *Tee ansehen*, *Neues Gefäß …*, the archived vessel's label, BRW-08 | T3 |
| Session page (SCR-16, read-only) and *So nochmal brühen* (BRW-06) | T3 |
| Wording (*Archivieren*, step kinds) and the SET-15 row | T2 |
| Accuracy test waits (timer test code) | T3 |
| Milestone review | T3 |

*v0.4.1 — stock, freshness, cost, photos* (provisional; its spec sets the tickets and tiers)

| Task | Tier |
|---|---|
| Stock history (SCR-29), *Nachkauf erfassen* for teas not from the catalog | T3 |
| Freshness labels, the opened-date snackbar, dates in the editor | T2 |
| Price per gram and cost per cup | T3 |
| Own photos (photo picker, cover, EXIF rotation) | T3 |
| *Aus Katalog zurücksetzen* | T2 |
| Milestone review | T3 |

**M4 — Journal & statistics**

| Task | Tier |
|---|---|
| Journal list, session detail, notes editor | T2 |
| Rating, taste sliders, radar chart, descriptor picker | T2 |
| „Gespeichert" sheet and recipe-update logic (PRF-07) | T3 |
| Statistics computations (streaks, buckets, cost, consumption) + tests | T3 |
| Charts (Vico bar charts, custom donut) | T2 |
| German descriptor vocabulary and copy | T1 |
| Milestone review | T3 |

**M5 — Data & polish → 1.0**

| Task | Tier |
|---|---|
| Device backup verification and refinements (rules and `active_brew` clearing exist since M1) | T3 |
| Simple manual export (ZIP: data JSON + own photos) | T2 |
| Orphan sweeper and reminders | T2 |
| Remaining settings screens | T1 |
| Diagnostics log extended to the catalog | T2 |
| Accessibility and dark-theme pass | T2 |
| Baseline Profile and performance budgets | T3 |
| Final 1.0 review (whole app, timer and data safety focus) | T4 |

## 4. Escalation and fallback rules

| Situation | Action |
|---|---|
| A T1 (Sonnet) result fails to build, fails tests, or is wrong | Re-run the task once at T2 (Opus, medium) with the failure details |
| A T2 task fails twice, or turns out to touch timer, data integrity or parsing | Escalate to T3 |
| A T3 task fails twice, or the problem is not understood | Escalate to T4 |
| T4 cannot resolve it | Orchestrator stops and asks the PO, with a summary of the findings |
| A task touches `core:timer`, `core:timer-engine`, `core:backup` or migrations | Never below T3, whatever its size |

A task is never moved to a *lower* tier after it failed.

## 5. Orchestration workflow

1. **Plan:** the orchestrator breaks the milestone into tasks with the tier
   from §3, written to `docs/plans/<milestone>.md` (e.g.
   [M1-timer.md](../plans/M1-timer.md)). It writes each task prompt as self-contained: spec references
   (requirement IDs), files involved, acceptance criteria, and tests to add.
2. **Parallelise** independent tasks (different modules). Code-writing
   subagents that run in parallel use isolated git worktrees, and the
   orchestrator merges their results. The module graph and version catalog
   are created up front (M1-01), so parallel tasks never edit them.
3. **Verify:** for every result, the orchestrator runs the build,
   lint/detekt, unit tests and screenshot tests, and reads the diff. Failures
   follow §4.
4. **Review:** before the milestone PR, a review subagent at the milestone's
   review tier (§3.2) checks the full diff against the spec. Its findings are
   fixed (or answered) before opening the PR.
5. **PR:** one PR per milestone, with the requirement IDs implemented, the
   tiers used for critical parts, and the on-device acceptance checklist from
   [09 §4](09-release-plan.md#4-acceptance-checklists-on-the-pos-galaxy).

## 6. Prompt template for subagents

```text
Task: <one sentence>
Milestone / requirements: <M1 · TMR-23, 04 §5>
Context: <relevant files and modules; patterns to follow>
Do: <exact steps / expected result>
Don't: <out of scope; files not to touch>
Acceptance: <tests that must pass, behaviour to demonstrate>
Report: <what to return: changed files, test results, open questions>
```

## 7. Notes

- **Setting model and effort:** the Agent tool accepts `model` and `effort`
  per subagent call, so the tier is set directly on each delegation; no
  `.claude/agents/` definitions are needed.
- Tiers are a default. The orchestrator may *raise* a tier when it judges the
  risk to be higher. It lowers a tier only when §2.1 clearly allows it, and
  never after a failure.
- Read-only research (searching the codebase, checking the website) can use
  T1 or T2 depending on how much judgement the conclusion needs.
- This plan applies from M1 onwards. It is revised after M1 based on how well
  the tiers worked.
