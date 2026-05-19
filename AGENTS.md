# AGENTS.md

This workspace uses the Android GSD system as the persistent project guide.

## Start Here

1. Read `android/.gsd/README.md` to understand how the project guidance is organized.
2. Read `android/.gsd/STATE.md` for the current position.
3. Read `android/.gsd/SPEC.md` and `android/.gsd/ROADMAP.md` before phase work.
4. Read `android/PROJECT_RULES.md` for canonical GSD rules.
5. Read `android/adapters/CODEX.md` for Codex-specific workflow notes.

## Project Scope

- `android/`: Native Android app in Kotlin. This is where the GSD methodology currently lives.
- `backend/`: FastAPI backend used by the mobile app.
- `static/` and `uploads/`: Runtime/static assets used by the backend.

The `.gsd` folder is planning memory and workflow guidance. It is not runtime source code.

## Working Rules

- Prefer the real codebase and test output over stale documentation.
- Use `rg` for search-first exploration, then targeted file reads.
- Keep edits scoped to the user request and preserve existing user changes.
- Do not print secrets or API keys. Report only file paths and variable names if secrets are found.
- Do not create git commits unless the user explicitly asks for a commit.
- For substantial phase or multi-file work, update `android/.gsd/STATE.md` with the outcome and next step.

## Verification Hints

- Android commands run from `android/`.
- Prefer `.\gradlew.bat testDebugUnitTest` for JVM/unit test checks.
- Use `.\gradlew.bat assembleDebug` when build verification is needed.
- Backend commands run from `backend/`; inspect local docs/config before choosing the exact test command.
