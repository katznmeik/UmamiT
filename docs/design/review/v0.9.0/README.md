# v0.9.0 design review (`v0.9.0-rc.1`, #343)

These renders are Roborazzi goldens, scaled to 600 px wide and palette-optimised:
- `backup/` comes from `feature:settings` (SCR-26 and its dialogs);
- `recipe/` from `feature:collection` (SCR-08 ⋮, SCR-12 ⋮, SCR-32) and `feature:catalog` (SCR-32's package sheet).

They use invented sample teas and the app's own placeholders only, with no Tee-Kontor content (D-016). Re-record the goldens with `./gradlew recordRoborazziDebug`.

Naming: `<Screen>_<state>_<light|dark>.png`; `_font2` is font scale 2.0.

Decisions: [D-130](../../../specs/10-open-questions-and-decisions.md#d-130), [D-131](../../../specs/10-open-questions-and-decisions.md) (*Alle Daten löschen*), [D-133](../../../specs/10-open-questions-and-decisions.md#d-133) (the rc fix round).

## Daten & Sicherung (SCR-26; BAK-01, BAK-02, BAK-04, BAK-06, SET-20, SET-33)

| Part | Renders | What to look at |
|---|---|---|
| **States** | `DataBackup_neverSaved`, `_saved`, `_timerActive`, `_undoNone` | „Noch keine Sicherung" vs. „Letzte Sicherung: … · N neue Sessions"; greyed out with „Während ein Timer läuft, nicht möglich". |
| **Export** | `DataBackup_sizeNote`, `_progress` | The size note before the picker; the progress with the photo count (it now eases per photo, D-133 Q11). |
| **Import dialogs** | `DataBackup_preview`, `_previewTyped` (also `_font2`), `_importing` | The preview with its date and counts; the hint line „Tippe ERSETZEN zur Bestätigung" now **above** a field without a label (D-133 Q1). |
| **Refusals** | `DataBackup_refused_{notABackup,damaged,newerVersion,timerActive,catalogRefresh,safetyCopy,replaceFailed}` | One dialog each; „Deine Daten sind unverändert". |
| **Undo** | `DataBackup_undoCounts` | „Seit dem Import: 3 neue Sessions, 1 geänderter Tee …". |
| **Alle Daten löschen** (#346) | `DataBackup_wipeRow`, `_wipeTimerActive`, `_wipeExportRunning`, `_wipeDialog`, `_wipeDialogTyped` (also `_font2`), `_wiping` (also `_font2`) | The row, its greyed-out states, the dialog with the safety-copy paragraph, and the ~1 s notice, now in the same window as the dialog (D-133 Q7). At font 2.0 the hint no longer crowds the body. |

## Recipes (PRF-08; SCR-08, SCR-12, SCR-32)

| Part | Renders | What to look at |
|---|---|---|
| **SCR-12 ⋮** | `RecipeEditor_shareMenu`, `_shareMenu_unsaved` | *Rezept teilen*; unsaved changes show „Erst speichern". |
| **SCR-08 ⋮** | `Collection_myTeasMenu` | *Rezept importieren*. |
| **SCR-32** | `ReceiveRecipe_{match,noMatch,notInCatalog,withoutCatalog,refused}` | The matching tea preselected; *Anderen Tee wählen*, *Neuen Tee anlegen*, *Tee über Shop-Link laden* (only on a tap); a refused file. |
| **Package sheet** | `CatalogPackageSheet_open` (also `_font2`), `_failed` | *In Sammlung aufnehmen* from SCR-32 (it now slides out after *Aufnehmen*, A-13). |

Motion (D-133 Q6, Q8, Q11, A-12, A-13) is not visible in still renders; the PO's eye on the device is the check.
