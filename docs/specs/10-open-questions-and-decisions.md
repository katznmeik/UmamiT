# 10 · Open Questions & Decision Log

> Status: **Living document** · Last updated: 2026-10-21

## Open questions

Questions that need an answer from the product owner (PO) or a specialist
before or during development. "Needed by" refers to milestones in
[09](09-release-plan.md#3-milestones).

| ID | Question | Proposed default | Owner | Needed by |
|---|---|---|---|---|
| Q-01 | What is the final **application ID** (package name)? It can never change after the first Play upload. | `com.umamitea.app` (owning the `umamitea.com` domain is not required, but recommended) | PO | M0 |
| Q-02 | **Monetisation:** free forever, one-time "supporter" unlock, or paid app? (In-app purchases would need Play Billing and a review of the no-network decision.) | Free, no ads, no IAP in 1.0 | PO | M5 |
| Q-03 | Which **languages** at launch besides English? | English only at 1.0; Japanese and German in 1.1 | PO | M5 |
| Q-04 | Who produces the **final brand assets** (logo, colour palette, ink-wash illustrations, style photos)? Are the mockup images licensed for use? | Designer commissioned; mockup used as reference only | PO | M1 (palette), M4 (imagery) |
| Q-05 | Which **serif display font**? | Cormorant Garamond (OFL) — closest to the mockup; alternatives EB Garamond, Noto Serif Display | Designer | M0 |
| Q-06 | Who **reviews tea content** (parameters, guides) for accuracy? | An experienced tea professional / educator, paid review | PO | M4 |
| Q-07 | Bottom navigation: mockup shows **Home · Journal · Collection · Stats**; spec proposes **Home · Journal · Collection · Learn** with stats inside Journal. Which? | Spec proposal (Learn top-level) | PO | M0 |
| Q-08 | Are **tablet / foldable-optimised layouts** required for 1.0, or is "works well, not optimised" acceptable? | List-detail for Collection & Journal in 1.0; other screens centred with max width | PO | M5 |
| Q-09 | Is the Play developer account **personal or organisation**? Personal accounts created after Nov 2023 must run a closed test (≥ 12 testers, ≥ 14 days) before production. | Assume personal → plan M6 accordingly | PO | M0 |
| Q-10 | Confirm **minimum Android version** 8.0 (API 26). | Yes | PO | M0 |
| Q-11 | Will the project be **open source**? Which licence? | Private repo; decide before 1.0 | PO | M6 |
| Q-12 | Should onboarding default to **sample teas**? | Yes (ONB-04), removable anytime | PO | M5 |
| Q-13 | Add the **brand mockup** image to `docs/design/brand-mockup.png`? | Yes | PO | M0 |
| Q-14 | **Support e-mail** and **privacy policy hosting** (e.g. GitHub Pages)? | Dedicated address + GitHub Pages | PO | M6 |
| Q-15 | Default **alert sound**: is a singing-bowl tone right for the brand, and should the default alert ring in silent mode (SET-13)? | Singing bowl; ring in silent mode | PO | M2 |

## Decision log

Lightweight architecture/product decision records. Status: *Accepted*,
*Proposed* (default unless PO objects), *Superseded*.

<a id="d-001"></a>
### D-001 — Offline only, no accounts · *Accepted* (PO, 2026-10-21)

- **Context:** Options were offline-only, or accounts + cloud sync.
- **Decision:** 1.x is fully offline with no accounts and no backend.
- **Consequences:** No server costs or GDPR processing; strong privacy story;
  no `INTERNET` permission. Data safety relies on user backups and Android
  Auto Backup. Multi-device sync is out of scope (NG1).

<a id="d-002"></a>
### D-002 — Product concept: tea brewing companion · *Accepted* (PO, 2026-10-21)

- **Context:** Candidate concepts: brewing companion, tea-shop ordering app,
  discovery/community, e-commerce.
- **Decision:** Personal tea library + brewing guidance + steep timer + tasting
  journal, focused on umami-rich teas but supporting all teas.

<a id="d-003"></a>
### D-003 — Kotlin, Jetpack Compose, Material 3, min SDK 26 · *Proposed*

- **Alternatives:** Flutter / Kotlin Multiplatform (no need for iOS now);
  XML views (legacy).
- **Rationale:** Modern, officially recommended stack; best access to
  platform features needed by the timer (FGS, notifications, alarms).
  API 26 covers the vast majority of active devices and removes legacy
  notification/channel workarounds.

<a id="d-004"></a>
### D-004 — Timer background strategy · *Proposed*

- **Context:** Alerts must be reliable with the screen off. Options:
  1. Foreground service type `specialUse` + partial wake lock during short
     steps, exact-alarm backstop when permitted (chosen).
  2. `shortService` FGS — rejected: ~3 min timeout is shorter than western
     steeps.
  3. Exact alarms only via `USE_EXACT_ALARM` — Play restricts this permission to
     apps whose core function is an alarm clock or timer; acceptance for a
     brewing companion is uncertain.
  4. Exact alarms via `SCHEDULE_EXACT_ALARM` only — denied by default on
     Android 14+, so every user would need to grant it manually.
  5. WorkManager — rejected: not precise.
- **Decision:** Option 1, with long steps (≥ 30 min) using alarms (exact if
  permitted, else a 10-minute window). See [04 §6](04-brew-timer.md#6-background-execution).
- **Consequences:** Play Console FGS declaration and video required. If Play
  rejects `specialUse`, fall back to option 4 with an in-app permission flow
  (and evaluate option 3).

<a id="d-005"></a>
### D-005 — Persistence: Room + typed DataStore, UUIDs, metric storage · *Proposed*

- **Rationale:** Relational data with FTS search and migrations; UUIDs enable
  future merge/sync; metric storage avoids rounding drift.

<a id="d-006"></a>
### D-006 — No network permission, no analytics · *Proposed*

- **Rationale:** Privacy principle, simpler Data safety, fewer dependencies.
- **Consequences:** Usage insights only from Play Console; content updates only
  with app updates; feedback via e-mail.

<a id="d-007"></a>
### D-007 — Navigation library · *Proposed*

- **Decision:** Navigation Compose with type-safe `@Serializable` routes.
  Re-evaluate Navigation 3 at M0 if it is stable and offers better adaptive
  (list-detail) support.

<a id="d-008"></a>
### D-008 — Guides authored in Markdown · *Proposed*

- **Rationale:** Easy to write, review and translate; rendered by a Compose
  Markdown renderer limited to headings, paragraphs, emphasis, lists, tables
  and images. If the library adds unwanted weight, build a minimal renderer.

<a id="d-009"></a>
### D-009 — Brand theme by default, dynamic colour optional · *Proposed*

- **Rationale:** The calm washi/matcha palette is central to the brand
  (mockup). Material You colours would dilute it; offered as an opt-in setting.

<a id="d-010"></a>
### D-010 — Statistics inside Journal; Learn as top-level tab · *Proposed* (pending Q-07)

<a id="d-011"></a>
### D-011 — Photo handling · *Proposed*

- **Decision:** Photo Picker + camera intent (no storage/camera permissions),
  resized to 2048 px JPEG, EXIF removed, stored in app-private storage;
  included in manual backups; excluded from Auto Backup (25 MB limit).

<a id="d-012"></a>
### D-012 — Timer screen always dark · *Proposed*

- **Rationale:** Matches the mockup; reduces glare in dim rooms during a calm
  ritual; dark backgrounds use less power on OLED when the screen is kept on.

## Change log

| Date | Change |
|---|---|
| 2026-10-21 | Initial specification set (v0.1); D-001 and D-002 accepted by PO; brand mockup received and incorporated into [03](03-ux-ui.md). |
