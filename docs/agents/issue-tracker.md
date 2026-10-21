# Issue tracker: GitHub

Issues for this repo live as GitHub issues in `katznmeik/UmamiT`.

## UmamiT rules

- **Issues are work items, not specs.** The specs in `docs/specs/` are the
  source of truth (CLAUDE.md rule 2). Every issue cites the requirement IDs
  (e.g. `TMR-23`, `CAT-07`) and decision IDs (e.g. `D-036`) it covers. A
  behaviour change still updates the spec in the same PR.
- **Public repository:** never put Tee-Kontor texts, photos or HTML into an
  issue, and never secrets.
- **Language:** issues are written in English (like docs and code).
- **Development gate:** `ready-for-agent` does not override the PO's go. No
  implementation starts before the go recorded in `PROJECT_STATE.md`.

## Tools

Cloud sessions have **no `gh` CLI**; use the GitHub MCP tools there. Local
sessions may use `gh`.

| Operation | Cloud session (GitHub MCP) | Local (`gh`) |
|---|---|---|
| Create an issue | `issue_write` (method `create`) | `gh issue create --title "..." --body "..."` (heredoc for multi-line bodies) |
| Read an issue | `issue_read` (issue, comments, labels) | `gh issue view <number> --comments` |
| List / search issues | `list_issues` (state, labels) / `search_issues` | `gh issue list --state open --json number,title,body,labels,comments` with `--label` / `--state` filters |
| Comment | `add_issue_comment` | `gh issue comment <number> --body "..."` |
| Apply / remove labels | `issue_write` (method `update`, full `labels` list) | `gh issue edit <number> --add-label "..."` / `--remove-label "..."` |
| Close | `issue_write` (`state: closed`, always with `state_reason`) plus a closing comment | `gh issue close <number> --comment "..."` |
| Sub-issues | `sub_issue_write` | `gh api` on the sub-issues endpoint |

## Pull requests as a triage surface

**PRs as a request surface: no.** _(Set to `yes` if this repo treats external PRs as feature requests; `/triage` reads this flag.)_

When set to `yes`, PRs run through the same labels and states as issues
(`pull_request_read` / `gh pr view`, `gh pr diff`; keep only PRs whose
`authorAssociation` is `CONTRIBUTOR`, `FIRST_TIME_CONTRIBUTOR` or `NONE`).

GitHub shares one number space across issues and PRs, so a bare number may
be either: try the PR first, then fall back to the issue.

## When a skill says "publish to the issue tracker"

Create a GitHub issue.

## When a skill says "fetch the relevant ticket"

Read the issue with its comments and labels.

## Wayfinding operations

Used by `/wayfinder`. The **map** is a single issue with **child** issues as tickets.

- **Map**: a single issue labelled `wayfinder:map`, holding the Notes / Decisions-so-far / Fog body.
- **Child ticket**: an issue linked to the map as a GitHub sub-issue. Where sub-issues aren't available, add the child to a task list in the map body and put `Part of #<map>` at the top of the child body. Labels: `wayfinder:<type>` (`research`/`prototype`/`grilling`/`task`). Once claimed, the ticket is assigned to the driving dev.
- **Blocking**: GitHub's native issue dependencies where the tooling supports them (`gh api --method POST repos/<owner>/<repo>/issues/<child>/dependencies/blocked_by -F issue_id=<blocker-db-id>`, using the blocker's numeric database id). The GitHub MCP tools have no dependency operation, so in cloud sessions put a `Blocked by: #<n>, #<n>` line at the top of the child body. A ticket is unblocked when every blocker is closed.
- **Frontier query**: list the map's open children, drop any with an open blocker or an assignee; first in map order wins.
- **Claim**: assign the issue to yourself, the session's first write.
- **Resolve**: comment the answer, close the issue, then append a context pointer (gist + link) to the map's Decisions-so-far.
