# Domain Docs

How the engineering skills should consume this repo's domain documentation when exploring the codebase.

UmamiT is a **single-context** repo. Its domain docs already live in the
specification, so the skills use these files instead of a `CONTEXT.md` and
`docs/adr/`:

| Skill convention | UmamiT file |
|---|---|
| `CONTEXT.md` (glossary) | [`docs/specs/11-glossary.md`](../specs/11-glossary.md) |
| `docs/adr/` (decisions) | the `D-xxx` entries in [`docs/specs/10-open-questions-and-decisions.md`](../specs/10-open-questions-and-decisions.md) |

Do **not** create `CONTEXT.md`, `CONTEXT-MAP.md` or `docs/adr/`. When
`/domain-modeling` (or `/grill-with-docs`, `/improve-codebase-architecture`)
resolves a term, add it to the glossary; when it records a decision, add a
`D-xxx` entry (and a change-log line) to the decision log.

## Before exploring, read these

- **`docs/specs/11-glossary.md`**: the project's vocabulary.
- **`docs/specs/10-open-questions-and-decisions.md`**: read the decisions that touch the area you're about to work in.
- **`docs/specs/README.md`**: the index of the spec, which is the source of truth for behaviour.

## Use the glossary's vocabulary

When your output names a domain concept (in an issue title, a refactor proposal, a hypothesis, a test name), use the term as defined in the glossary. Don't drift to synonyms the glossary explicitly avoids. UI copy is German; code, docs and issues are English, using the glossary's English terms.

If the concept you need isn't in the glossary yet, that's a signal: either you're inventing language the project doesn't use (reconsider) or there's a real gap (note it for `/domain-modeling`).

## Flag decision conflicts

If your output contradicts an existing decision, surface it explicitly rather than silently overriding:

> _Contradicts D-019 (timer behaviour), but worth reopening because…_
