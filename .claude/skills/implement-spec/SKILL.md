---
name: implement-spec
description: "Implement a specification in code: work its tickets as a task graph with parallel implementer subagents and land them on one PR. UmamiT copy of the beta skill from mattpocock/skills, with project rules."
disable-model-invocation: true
---

<!--
Vendored from mattpocock/skills, skills/in-progress/implement-spec (plugin
version 1.2.3, beta), MIT License, Copyright (c) 2026 Matt Pocock.
https://github.com/mattpocock/skills
The upstream text is kept verbatim down to "UmamiT rules"; that section is
UmamiT's own addition and takes precedence where they differ.
-->

You have been provided a spec. This spec should have tickets associated with it, describing how to implement the spec.

The goal is a PR which implements the entire spec on a single branch.

The tickets are not a list of steps. They are a **task graph** with blocking relationships between them. This means there is always a **frontier** of tickets which are ready to be grabbed.

Communication to and from subagents should be sparse. Communicate primarily through **context pointers**: to the spec, tickets, research notes, and previous commits. Don't duplicate information already available via pointers.

**Implementer subagents** should be run in the background where possible for **maximum concurrency**.

## Steps

1. Read the spec and tickets. Read enough to understand the task graph.

2. (optional) Use an **exploration subagent** to conduct any exploration required by the tickets - relevant codebase files or external documentation. Ensure the exploration subagent can save files - it should save its markdown notes in a directory outside the repo, accessible by all future subagents. This lets **implementer subagents** focus on implementation rather than exploration.

3. Create a branch, and a draft PR. The PR should be marked as 'closing' the spec issue and tickets.

4. Use **implementer subagents** to implement each ticket. Each implementer subagent should work in its own worktree, on its own branch.

5. Once an **implementer subagent** completes, merge its work to the PR branch with a **merger subagent**.

6. If this changes the **frontier** of available tickets, kick off more **implementer subagents** to work on the new tickets. This allows for maximum concurrency.

7. Once all tickets are complete, run /code-review on the PR branch. Fix all issues raised by the code review in a single **implementer subagent**.

8. Mark the PR as ready for review.

9. Clean up all **implementer subagent** worktrees.

## UmamiT rules (take precedence over the steps above)

Read `CLAUDE.md`, `PROJECT_STATE.md` and `docs/agents/issue-tracker.md` first.

- **Development gate:** do nothing unless `PROJECT_STATE.md` records that the PO's go is in effect. A `ready-for-agent` label does not override it.
- **Branch and PR:** work on the session's designated branch. The PR is the milestone PR (CLAUDE.md rule 6, "one PR per milestone") using `.github/pull_request_template.md`. It closes the spec issue and only the tickets it actually completes. The exception is `release.yml`, which reaches `main` through its own housekeeping PR (D-039).
- **Tiers:** set `model` and `effort` on every subagent call per `docs/specs/12-agent-plan.md`:
  - each ticket's tier comes from the plan (`docs/plans/<milestone>.md`);
  - timer, backup and migration code is never below T3;
  - never use `max`;
  - after a failure, escalate one tier and never downgrade.
- **Implementer subagents:** run them with `isolation: "worktree"`. Exploration notes go to the session scratchpad, not the repo.
- **Merging into the PR branch:** the orchestrator verifies each result itself (build, `spotlessCheck detekt lint testDebugUnitTest`, screenshot tests where they exist, and the diff) before merging it. A subagent's report is not proof.
- **Human tickets (`ready-for-human`):** no implementer subagent. Work that the specs assign to the orchestrator (e.g. generating and delivering the signing key, 06 §6.1) is done by the orchestrator itself, so key material passes through no other context. Then wait for the PO's part. Release dispatches and the PO's on-device checks gate the tickets that depend on them.
- **Public repository:** no Tee-Kontor texts, photos or HTML, and no secrets or keystores (CLAUDE.md rule 3).
- **Code review (step 7):** use a reviewer at the milestone's review tier (`docs/specs/12-agent-plan.md` §3.2; the M1 milestone review is T4). Fix the findings or answer them.
- **Ready for review and merge (steps 8–9):** keep the PR in draft while the milestone has open tickets. Mark it ready only when the milestone's done criteria can be checked. Claude merges a milestone PR only after the PO's explicit approval (CLAUDE.md rule 9). Never close the spec issue by hand.
- **Bookkeeping:** update the ticket checkboxes and `PROJECT_STATE.md` as tickets complete. Reference requirement IDs in commits (Conventional Commits).
