# Codex Adapter

> Everything in this file is optional.
> For canonical rules, see [PROJECT_RULES.md](../PROJECT_RULES.md).

This adapter maps the GSD methodology to Codex working in this repository.

## Entry Point

When starting work in this workspace:

1. Read the root [AGENTS.md](../../AGENTS.md) if present.
2. Read [.gsd/README.md](../.gsd/README.md) for the project guide.
3. Read [.gsd/STATE.md](../.gsd/STATE.md) for the current phase and next action.
4. For implementation work, read only the relevant plan under `.gsd/phases/`.

## Tool Mapping

| GSD Need | Codex Practice |
|----------|----------------|
| Search-first exploration | Use `rg` or `rg --files` before broad reads |
| Targeted context | Read specific files or line ranges only when needed |
| File edits | Use `apply_patch` for manual edits |
| Verification | Run the smallest command that proves the change |
| Long work | Send short progress updates and keep state in `.gsd/STATE.md` |

## Git Policy

GSD encourages atomic commits. In Codex chat sessions for this project:

- Do not create commits unless the user explicitly asks.
- Keep working-tree changes atomic by task.
- After verification, provide a suggested commit message when useful.
- Never revert unrelated dirty files or user changes.

## State Policy

Update `.gsd/STATE.md` when work changes project direction, phase progress, or multiple files.

Do not update state for tiny Q&A, one-off command output, or exploratory reads that do not change the project.

## Verification Defaults

For Android work from `android/`:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

For backend work from `backend/`, inspect the backend config first and run the narrowest relevant test or import check.

## Conflict Resolution

If docs and code disagree:

1. Trust compiling code, tests, and current runtime behavior first.
2. Treat `.gsd` as planning memory that may need refreshing.
3. Update `.gsd` only when the mismatch matters for future work.
