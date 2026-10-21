# 09 · Release Plan & Roadmap

> Status: **Draft v0.1** · Last updated: 2026-10-21
>
> Estimates assume **one experienced Android developer** working full time,
> with design and content support in parallel. They are rough and will be
> refined after milestone M0.

## 1. Release phases

| Phase | Contents | Audience | Exit criteria |
|---|---|---|---|
| **MVP / internal** | All `Must` requirements | Developer + internal testing track | All Must ACs pass; core journey works end-to-end |
| **Closed beta** | MVP + most `Should` | ≥ 12 opted-in testers for ≥ 14 days (Play requirement for new personal developer accounts) | No P0/P1 bugs; no missed-alert reports; SUS ≥ 80 |
| **1.0 public** | All `Must` + `Should` | Google Play production (staged rollout 10 % → 50 % → 100 %) | Vitals within targets for 7 days at each stage |
| **1.x** | `Could` backlog, feedback-driven | Production | — |

## 2. MVP definition (`Must` scope)

1. Onboarding (welcome, units, notifications).
2. Collection: add/edit/delete/archive teas from styles; search; sort; favourites.
3. Style catalog (data + browse).
4. Brew profiles with multi-step editor.
5. Brew setup with ratio scaling.
6. **Steep timer**: full state machine, FGS, notifications, alerts, persistence &
   recovery, long-step reminder mode, matcha whisk and rinse steps.
7. Journal: summary with rating and notes, list, detail, edit.
8. Settings: units, theme, alert sound, vibration, keep screen on, delete all.
9. Backup & restore (full file).
10. Design system (washi/steeping themes, serif font, core components).

## 3. Milestones

| # | Milestone | Duration | Deliverables | Requirements |
|---|---|---|---|---|
| M0 | **Foundations** | 1.5 wk | Gradle setup, build-logic, version catalog, module skeleton, CI (lint, detekt, spotless, unit tests, manifest check), Hilt, Room & DataStore skeleton, design system v1 (tokens, typography, core components), navigation shell with 4 tabs, splash & icon placeholder | GEN-01, GEN-02, NFR-MAINT |
| M1 | **Collection** | 2.5 wk | Data model v1 + schema export, style catalog loader with all style data, tea CRUD, style picker, profile & steps editor, search/sort/favourites, teaware CRUD, photos | TEA, STY, PRF, WARE |
| M2 | **Brewing** | 3.5 wk | Timer engine (with exhaustive unit tests), controller, service, notifications, alert player, recovery, long steps, brew setup & scaling, timer UI (all states), quick timer, active brew bar, accuracy benchmark | BRW, TMR, NOT |
| M3 | **Journal & stock** | 2 wk | Session summary, rating, taste sliders, radar, descriptors, journal list/detail/editor, manual entry, inventory events & stock UI, freshness | JRN, INV |
| M4 | **Learn, insights, content** | 2 wk | Guides renderer + 12 guides, style descriptions, tools (ratio, cooling, converter), Your Tea Journey screen, final illustrations/photos integrated | LRN, INS, content |
| M5 | **Data & polish** | 2 wk | Backup/restore, CSV export, auto-backup rules, settings complete, onboarding final, accessibility pass, adaptive layouts, baseline profiles, performance budgets, localisation readiness | BAK, SET, NFR-* |
| M6 | **Closed beta** | 2–3 wk | Play internal → closed track, 12+ testers × 14 days, feedback triage, bug fixing, store listing assets, privacy policy page, FGS declaration video | — |
| M7 | **Launch 1.0** | 1 wk | Production release with staged rollout, monitoring | — |

**Total:** ≈ 17–18 weeks to production, including beta.

Critical path: M2 (timer) — start its engine and a spike of the service/alert
behaviour on real Samsung/Xiaomi devices during M0 to retire the biggest risk
early.

## 4. Beta plan

- **Recruiting:** tea community (forums, local tea shop, friends) — mix of the
  three personas; at least 3 beginners, 3 enthusiasts, and testers on Samsung
  and Xiaomi devices.
- **Feedback channel:** e-mail form link in About + short survey (SUS + 5 open
  questions) sent after 7 and 14 days.
- **Test script:** add two teas, brew a 4-infusion gyokuro session with the
  phone locked, log tasting notes, brew again from Home, back up and restore.
- **Tracking:** GitHub issues with labels `beta`, `timer`, `ux`, `content`,
  severity P0–P3.

## 5. Launch checklist

- [ ] Application ID final and registered (Q-01).
- [ ] App name "UmamiTea" checked for trademark conflicts and Play availability.
- [ ] Store listing: title (≤ 30 chars), short description (≤ 80 chars), full
      description (≤ 4,000 chars), icon 512 × 512, feature graphic 1024 × 500,
      ≥ 4 phone screenshots, tablet screenshots, optional promo video.
- [ ] Category: Food & Drink. Tags: tea, timer, journal.
- [ ] Content rating questionnaire completed.
- [ ] Data safety: no data collected / shared.
- [ ] Privacy policy URL (e.g. GitHub Pages).
- [ ] Foreground service (`specialUse`) declaration with video.
- [ ] Exact alarm permission usage reviewed (SCHEDULE_EXACT_ALARM only).
- [ ] Target audience and ads declaration ("no ads").
- [ ] Release notes, `CHANGELOG.md`, git tag `v1.0.0`.
- [ ] Vitals monitoring plan for staged rollout.
- [ ] Content review sign-off (Q-06), asset licences recorded.

Draft store copy:
- **Short description:** "Brew umami-rich tea beautifully: steep timer, tea collection & tasting journal."
- **Full description opening:** "UmamiTea is a calm companion for brewing better
  tea — especially Japanese greens like gyokuro, sencha and matcha. Get
  suggested temperatures and steep times for 25 tea styles, follow a
  multi-infusion timer that rings even when your phone is locked, and keep a
  beautiful journal of every cup. No account. No ads. No internet access. Your
  tea notes stay on your phone."

## 6. Post-1.0 roadmap

Ordered by expected value; to be re-prioritised with user feedback.

| Horizon | Item | Ref |
|---|---|---|
| Next (1.1) | Home-screen widget, app shortcuts, Quick Settings tile | NOT-06…08 |
| Next | Brew coach tips | JRN-14 |
| Next | Live Updates progress notification on supported Android versions | TMR-41 |
| Next | Additional languages (Japanese, German) | NFR-I18N-01 |
| Next | Calendar view, share session card image | JRN-15/16 |
| Later | Multiple concurrent timers | TMR-40 |
| Later | Merge-restore of backups; encrypted backups | BAK-03, NFR-PRIV-06 |
| Later | On-device label OCR (bundled ML model, no network) to pre-fill tea details | TEA-16 |
| Later | Wear OS companion (timer on the wrist) | NG6 |
| Later | Tabletop posture layout; spoken countdown | TMR-24/25 |
| Exploratory | Optional cloud sync (would revisit D-001 and require accounts & a backend) | NG1 |
| Exploratory | Bluetooth scale / smart kettle integration | NG5 |

## 7. Risks

| # | Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| R1 | Timer alerts delayed/killed by OEM battery managers | Medium | High | FGS + wake lock + exact backstop; device matrix testing; in-app help page; beta on Samsung/Xiaomi |
| R2 | Play rejects `specialUse` FGS justification | Low–Medium | High | Clear declaration + video; fallback design: exact alarms with `SCHEDULE_EXACT_ALARM` permission request flow (or `USE_EXACT_ALARM` if the app qualifies as a timer app); decision D-004 documents alternatives |
| R3 | Inaccurate brewing content damages trust | Medium | Medium | Expert review (Q-06); present as starting points; feedback channel |
| R4 | Image/font licensing issues | Low | Medium | Only OFL fonts; commissioned or properly licensed imagery; credits list |
| R5 | Scope creep (social, sync) | Medium | Medium | Non-goals documented; MoSCoW discipline |
| R6 | Users lose data (offline only, phone lost) | Medium | High | Auto Backup of DB, backup reminders, easy export |
| R7 | APK size grows with imagery | Medium | Low | WebP, budgets in NFR-SIZE, vector illustrations where possible |
| R8 | Closed-testing requirement (12 testers / 14 days) delays launch | Medium | Medium | Recruit testers during M4–M5; plan M6 with buffer |
