# CLAUDE.md — UmamiT

UmamiT is a **personal Android app** (German UI) for brewing umami-rich green
tea. It has four parts:
- a multi-infusion steep timer;
- a catalog of Tee-Kontor Kiel's green teas;
- the user's own tea collection;
- an automatic journal with statistics.

There is one user, the product owner (PO). It runs on one Samsung Galaxy
(Android 16 / One UI 8) and is installed from GitHub Releases, not a store.

## Start of every session

1. Read **`PROJECT_STATE.md`**: current phase, milestone, next steps and
   blockers.
2. Check `git status`, the current branch and open PRs.
3. Read the spec sections for the task (`docs/specs/README.md` is the index).

At the end of every session (or milestone), update `PROJECT_STATE.md`,
commit and push.

## Hard rules

1. **No development without the PO's explicit go.** `PROJECT_STATE.md`
   records whether the go was given. Until then, only specs, plans and docs
   are changed.
2. **The specs are the source of truth** (`docs/specs/`). When behaviour
   changes, update the spec in the same PR and record decisions in
   `docs/specs/10-open-questions-and-decisions.md`. Reference requirement IDs
   (e.g. `TMR-23`, `CAT-07`) in commits and PRs.
3. **Public repository:**
   - never commit Tee-Kontor texts, photos or HTML pages; parser tests use
     **synthetic** fixtures (D-016);
   - never commit secrets or keystores (GitHub Actions secrets only).
4. **App language is German** (informal „du"):
   - metric units, temperatures in **5 °C steps**;
   - German number and date formats („4,0 g", „21. Okt. 2026");
   - every UI string goes in `strings.xml`.
   Docs and code are in English.
5. **Delegation** follows `docs/specs/12-agent-plan.md`:

   | Tier | Model | Effort | Use for |
   |---|---|---|---|
   | T1 | **Sonnet** | `low` | Simple tasks |
   | T2 | Opus | `medium` | Standard tasks |
   | T3 | Opus | `high` | Complex tasks |
   | T4 | Opus | `xhigh` | Critical tasks |

   Never use `max`. Timer, backup and migration code is never below T3.
   Escalate one tier after a failure, never downgrade. Verify every subagent
   result (build, tests, diff) yourself.
6. **One PR per milestone** into `main`. A milestone is done when:
   - CI is green;
   - a signed APK is on a GitHub Release. Claude dispatches `release.yml`
     (`workflow_dispatch`, inputs `ref` + `version`), which creates the tag
     and the release. Release candidates `vX.Y.Z-rc.N` are pre-releases
     from the PR branch for the PO's testing; the final `vX.Y.Z` is built
     from `main` after the merge. The version lives only in
     `gradle.properties` (versionCode scheme: `docs/specs/06-architecture.md` §6);
   - the on-device checklist passes (`docs/specs/09-release-plan.md` §4);
   - the design review passes. Rendered screenshots (light and dark) go in
     `docs/design/review/<milestone>/`, using placeholder images only.
7. **The app uses the network for exactly three things**, all only to
   `www.tee-kontor-kiel.de` (D-036): the user-triggered catalog refresh, a
   one-time download of the two seed teas' photos at first start, and the
   exact price fetch when the user adds a package. Rules:
   - host allow-list;
   - at most 2 requests per second, one connection;
   - respect `robots.txt` and discover pages through the sitemap;
   - stop on a Cloudflare challenge, HTTP 429 or `Retry-After`, and never
     bypass a challenge.
8. **Timer reliability beats features.** Any change to the timer needs the
   unit tests in `docs/specs/04-brew-timer.md` §12 to pass.
9. **Claude merges every PR; the PO never merges** (D-037).
   - Documentation/housekeeping PRs: merge as soon as CI (if any) is green
     and you have checked the diff.
   - Milestone PRs: merge only when all done criteria of rule 6 are met,
     including the PO's explicit approval of the on-device checklist and the
     design review (given in the session or as a PR comment). Then dispatch
     the final release from `main`.
   - Use merge commits. After merging, restart the working branch from
     `main` for follow-up work.

## Key facts

| Item | Value |
|---|---|
| Application ID | `com.umamit.app` (never change after the first release) |
| SDK | min 35, target/compile 36 |
| Stack | Kotlin 2, Compose, Material 3, Room, DataStore, Hilt, WorkManager, OkHttp, Jsoup, Coil 3 |
| Navigation | Start · Journal · Sammlung (Meine Tees / Katalog) · Statistik |
| Design | `docs/design/mockup-v2-*.png` (current), serif Cormorant Garamond, washi/matcha palette (`docs/specs/03-ux-ui.md`) |
| Images to produce | `docs/design/assets.md` |
| Milestones | M0 specs → M1 timer → M2 catalog → M3 collection → M4 journal & stats → M5 1.0 |
| Current plan | `docs/plans/` (e.g. `M1-timer.md`) |

## Environment (cloud sessions)

- JDK 21 and Gradle 8.14 are preinstalled. **The Android SDK is not**: it is
  installed by the session-start hook created in task M1-00.
- These hosts are reachable:
  - `dl.google.com`, `maven.google.com`, `repo.maven.apache.org`,
    `plugins.gradle.org`, `services.gradle.org` (build);
  - `raw.githubusercontent.com` (fonts);
  - `www.tee-kontor-kiel.de` (catalog).
- The container has **no KVM, so no Android emulator** runs here.
  Instrumented tests run in the **CI emulator job** on GitHub Actions
  (`reactivecircus/android-emulator-runner`, API 35 and 36).
- There is no device for the PO's phone. Behaviour on the device is verified
  by the PO from release APKs (probe, release candidates).
- `ffmpeg` is available for synthesising the alert sounds.

## Commands

Filled in once the project skeleton exists (M1-01):

```bash
./gradlew spotlessCheck detekt lint testDebugUnitTest   # quality + unit tests
./gradlew verifyRoborazziDebug                          # screenshot tests
./gradlew assembleDebug                                 # debug APK
```

## Conventions

- Conventional Commits (`feat:`, `fix:`, `docs:` …) with requirement IDs.
- Kotlin official style (Spotless/ktlint), Detekt and Lint with no baseline.
- Prefer fakes over mocks; pure Kotlin modules for logic (`core:timer-engine`,
  `core:catalog-parser`, `core:domain`).

## Agent skills

### Issue tracker

GitHub Issues in `katznmeik/UmamiT` (GitHub MCP tools in cloud sessions, `gh` locally); specs in `docs/specs/` stay the source of truth. See `docs/agents/issue-tracker.md`.

### Triage labels

The five default labels: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: glossary `docs/specs/11-glossary.md`, decisions (ADRs) `docs/specs/10-open-questions-and-decisions.md`. See `docs/agents/domain.md`.

### Project skills

`/implement-spec` (`.claude/skills/implement-spec/`): beta skill vendored from mattpocock/skills (MIT) with UmamiT rules (gate, tiers, verification, milestone PR) that take precedence. It works a spec's tickets as a task graph on one PR.

Design and motion skills from emilkowalski/skills (MIT), installed with
`npx skills add` and pinned in `skills-lock.json` (update with
`npx skills update -p`). Most of them are written for the web (CSS, React,
Framer Motion); for UmamiT, apply their principles to Compose and the
washi/matcha design in `docs/specs/03-ux-ui.md`. The most relevant are
`/emil-design-eng`, `/apple-design`, `/review-animations`,
`/improve-animations` and `/find-animation-opportunities`.
