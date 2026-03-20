# STATE.md — Project Memory

> Last updated: 2026-03-12

## Last Session Summary

GSD project initialized.
- Codebase mapped: 6 routers, 6 services, 7 repositories, 14 models
- Refactored models: tách 7 file mới từ user.py và supporting.py
- Refactored repos: tạo language_repo.py, fix scan_router architecture
- SPEC.md finalized with 9 success criteria
- ROADMAP.md created with 6 phases

## Current Position

- **Phase**: Not started
- **Next action**: `/plan 1` — Plan Phase 1 (JWT Auth)
- **Parallel thesis track**: Phase 4B YOLOv8 Custom Training & Mobile Inference has been added to ROADMAP
- **YOLO note**: Current implementation is ML Kit + Gemini fallback; YOLO exists in architecture docs but not yet integrated in runtime code

## What Works

- FastAPI backend boots and serves 21 endpoints
- 14 database tables via Alembic migration
- Layered architecture: Router → Service → Repository → Model
- SM-2 algorithm implemented
- Seed data available

## What Doesn't Work Yet

- Auth is mocked (hardcoded user_id = 1)
- No JWT implementation
- No Gemini API integration
- No Text-to-Speech
- No mobile app
- No tests
- Password uses SHA256 instead of bcrypt

## Key Decisions Made

See DECISIONS.md
