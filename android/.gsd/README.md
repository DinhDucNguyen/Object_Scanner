# GSD Project Guide

This folder is the operational guide for the Object Scanner / Object Language App project.

GSD means "Get Shit Done": a spec-driven workflow that keeps project intent, plans, progress, and verification evidence in files an AI agent can reload across sessions.

## What Lives Here

| Path | Purpose |
|------|---------|
| `SPEC.md` | Product vision, goals, scope, and success criteria |
| `ROADMAP.md` | Phase and wave breakdown |
| `STATE.md` | Current position and session memory |
| `ARCHITECTURE.md` | Codebase map and system structure |
| `STACK.md` | Technology stack and dependencies |
| `TODO.md` | Loose follow-up items |
| `phases/` | Detailed plans, summaries, and verification by phase |
| `templates/` | Reusable document templates |
| `examples/` | Reference workflows and command examples |

## How To Use It

1. Start with `STATE.md` to see the current phase and next action.
2. Check `SPEC.md` before implementation; phase work should align with a finalized spec.
3. Read the relevant section of `ROADMAP.md` and the exact plan under `phases/`.
4. Implement against the real codebase.
5. Verify with commands, screenshots, or API responses.
6. For substantial work, update `STATE.md` and any relevant phase summary.

## Current Fit For This Project

The app already has completed v1.0 planning and execution notes. Phase 6 historical docs cover advanced learning features:

- Multi-modal review modes
- Pronunciation practice
- Analytics
- Collections and insights
- Streaks and notifications

Phase 7 hiện là phase cập nhật hiện trạng chính. Dùng phase này để dựng lại tổng quan từ code thật trước khi planning thêm implementation.

## Agent Entrypoints

- Codex: see `../../AGENTS.md` and `../adapters/CODEX.md`.
- Gemini: see `../.gemini/GEMINI.md` and `../adapters/GEMINI.md`.
- Canonical GSD rules: see `../PROJECT_RULES.md`.

## Important Boundary

`.gsd` is not runtime app code. Deleting it should not directly break Android builds, but it would remove project memory, phase plans, and resume context.
