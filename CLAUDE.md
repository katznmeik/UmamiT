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
   - a signed APK is on a GitHub Release;
   - the on-device checklist passes (`docs/specs/09-release-plan.md` §4);
   - the design review passes. Rendered screenshots (light and dark) go in
     `docs/design/review/<milestone>/`, using placeholder images only.
7. **The app's only network use** is the user-triggered catalog refresh to
   `www.tee-kontor-kiel.de`. Its rules:
   - host allow-list;
   - at most 2 requests per second;
   - respect `robots.txt` and discover pages through the sitemap;
   - stop on a Cloudflare challenge and never bypass it.
8. **Timer reliability beats features.** Any change to the timer needs the
   unit tests in `docs/specs/04-brew-timer.md` §12 to pass.

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
- There is no device or emulator for the PO's phone. Behaviour on the device
  is verified by the PO from the release APK.

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
