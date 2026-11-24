# v0.10.0 design review (`v0.10.0-rc.1`, #362)

These renders are Roborazzi goldens, scaled to 600 px wide and palette-optimised, in one folder per module:
- `brew/` from `feature:brew` (SCR-04, SCR-05, SCR-07 in English);
- `collection/` from `feature:collection` (SCR-29 edit sheets);
- `home/`, `journal/`, `stats/` from the modules of the same name (English screens);
- `onboarding/` from `feature:onboarding` (SCR-33 tour);
- `settings/` from `feature:settings` (SCR-25, SCR-26);
- `app/` from `app` (SCR-34 Vollbild sheet).

They use invented sample teas and the app's own placeholders only, with no Tee-Kontor content (D-016). Re-record the goldens with `./gradlew recordRoborazziDebug`.

Naming: `<Screen>_<state>_<light|dark>.png`; `_font2` / `_font2_0` is font scale 2.0 (`_font1_0` is 1.0); `En` in the name means the English UI (D-136 Q6), without it the German UI.

Decision: [D-136](../../../specs/10-open-questions-and-decisions.md#d-136) (Q1–Q9, items 10–16 and the build notes).

## Vorrat bearbeiten (SCR-29; INV-03, INV-08; D-136 Q2, Q3, item 11)

| Part | Renders (`collection/`) | What to look at |
|---|---|---|
| **Kauf bearbeiten** | `TeaDetail_editPurchase_{light,dark}_{font1_0,font2_0}` | The *Nachkauf erfassen* sheet filled in with *Kaufdatum*, Menge in g, Preis in €; *Speichern* (filled) and *Löschen* (text, error colour). At font 2.0 the buttons stay reachable. |
| **Korrektur bearbeiten** | `TeaDetail_editCorrection_{light,dark}_{font1_0,font2_0}` | The signed change („+5", „−3") and the day; the same two buttons. |

## Vollbild-Alarm wieder erlauben (SCR-34; TMR-31; D-136 Q4, item 13)

| Part | Renders (`app/`) | What to look at |
|---|---|---|
| **Sheet** | `FsiSheet_{light,dark}` | The title „Vollbild-Alarm wieder erlauben", the two-line explanation, *Erlauben* (filled) and *Später*. |

## Einführung (SCR-33, SET-36; ONB-07; D-136 Q7, Q9, item 16)

| Part | Renders (`onboarding/`) | What to look at |
|---|---|---|
| **German tour** | `Tour_page{1..6}_{light,dark}`, `Tour_page1_{light,dark}_font2_0` | The six pages in order (Start und Timer, Zwei Timer und Schnelltimer, Sammlung und Katalog, Journal und Geschmack, Statistik, Sicherung und Rezepte teilen): the illustration, a title and two lines; page dots, *Weiter*, *Überspringen* top right, *Fertig* on page 6. At font 2.0 the text scrolls and the buttons stay in view. |
| **English tour** | `TourEn_page{1..6}_{light,dark}`, `TourEn_page1_{light,dark}_font2_0` | The same pages with the English copy (Next, Skip, Done). |

## Sprache und Einstellungen (SCR-25, SET-35; GEN-13; D-136 Q6, item 15)

| Part | Renders (`settings/`) | What to look at |
|---|---|---|
| **Sprache** | `Settings_language_{light,dark}_{font1_0,font2_0}`, `SettingsEn_language_{light,dark}` | *Darstellung* with the *Sprache* row below *Design*, and the dialog with System · Deutsch · English; the two names in their own language. |
| **Other German sections** | `Settings_{theme,teaware,volume,alarmVolumeOff,preAlertSound,ringUnderDnd,missingCheck,allReady}_{light,dark}_{font1_0,font2_0}` (`ringUnderDnd` at font 1.0 only) | The settings as before; check that nothing moved or was clipped with the new *Sprache* row, and the *Hilfe* › *Einführung* row above *Über*. |
| **English settings** | `SettingsEn_{brewing,missingCheck}_{light,dark}` | Longer English labels in the same rows. |
| **Daten & Sicherung** | `DataBackupEn_{neverSaved,saved,restoreTyped,wipeDialog}_{light,dark}` | SCR-26 in English: the states, the typed confirmation word, the *Delete all data* dialog. |

## English screens (GEN-13; D-136 Q6)

| Part | Renders | What to look at |
|---|---|---|
| **Start** (`home/`) | `HomeEn_{empty,seeded,lowStock,activeSession}_{light,dark}` | English copy and „4.0 g" style numbers; nothing truncated in chips, cards and the timer bar. |
| **Journal** (`journal/`) | `JournalEn_{empty,filterChips,withSessions}_{light,dark}`, `SessionPageEn_{available,ratedWithNote,tasteUnfolded}_{light,dark}` | Dates as „Oct 21, 2026"; filter chips and the session page. The taste-slider names, descriptors and Sorte names stay German by decision. |
| **Statistik** (`stats/`) | `StatsEn_{overview,months}_{light,dark}` | Month labels, numbers and the cost per litre in English. |

## Timer in English (SCR-04, SCR-05, SCR-07; TMR-31, NFR-COMP-02; D-136 Q5, Q6, item 14)

| Part | Renders (`brew/`) | What to look at |
|---|---|---|
| **Setup** | `BrewSetupEn_{kyusu,coldBrew}_{light,dark}` | The setup titles and chips in English. |
| **Brew timer** | `TimerScreenEn_{Ready_lowStock,Ready_coldBrew,Running_quickRow,PreAlert,Alerting,Alerting_rinse,Paused,Finished}_{light,dark}` | Every state of SCR-05 in the portrait layout the timer now keeps; the pre-alert, alert and rinse wording, the second-timer row, the low-stock hint. |
| **Quick timer** | `QuickTimerEn_{chips,running,preAlertMenu,preAlertTooShort}_{light,dark}` | SCR-07 with the chips, the running state and the *Pre-alert* menu with its too-short message. |

## Open for the PO

- **Überspringen on the last tour page** (D-136 item 16, ONB-07 AC1 against SCR-33): as built it shows on all six pages, beside *Fertig* (see `Tour_page6_*`). Keep it, or hide it on the last page as SCR-33 says?
- **The English wording** (D-136 Q6, Q9): the tour copy, the settings, the timer states and the journal in `*En_*`. Is the tone right, and is anything too long?
- **Sprache closing at once** (SET-35): the dialog is shown in `Settings_language_*`; whether it closes on the tap and the screen switches without a flash is a device check.
- **The Vollbild sheet** (item 13): the wording of `FsiSheet_*`; it also shows on any later open while the permission is off and unanswered, not only the first.

Motion (the sheets, the tour pager, the language switch) and rotation (the timer staying portrait, also over the lock screen) are not visible in still renders; the PO's eye on the device is the check.
