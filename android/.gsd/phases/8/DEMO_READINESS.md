# Phase 8.4 - Demo Hardening & Final Readiness

> Date: 2026-05-19  
> Scope: backend/demo smoke readiness using Phase 8 runtime and learning evidence.  
> Guardrail: no app/backend source code was changed for this plan.

## Executive Summary

| Area | Result | Evidence |
|---|---|---|
| Backend DB-backed demo APIs | Pass | FastAPI TestClient smoke with real MySQL seed returned `34 pass / 0 fail / 0 blocked`. |
| Temporary data cleanup | Pass | Final cleanup counts for `GSD_E2E` were all `0`: users, objects, translations, progress, logs, collections, scans, lookups, media. |
| Android runtime shell | Pass | Prior Plan 8.2 installed/launched on `Pixel_8_Pro_API_35`; main tabs and profile subflows rendered. |
| Normal review flow | Pass | Runtime account follow-up submitted `POST /api/review/60` -> `200 OK`. |
| Advanced review modes | Partial pass | Seeded quiz, typing, listening submit path, and image matching passed; pronunciation scoring and listening TTS playback remain runtime risks. |
| History Detail -> Add to Collection | Pass | Runtime verified `GET /api/collections` -> `200 OK` and `POST /api/collections/6/items` -> `200 OK`. |
| Scan demo | Pass with scope | Gallery -> COCO -> detection -> result -> save passed on emulator; live camera capture still needs a short rehearsal if used in final demo. |
| Admin panel | Out of demo scope | Normal user `GET /api/admin/dashboard` returned expected `403 Admin permission required`. |

## Decision

**Demo-ready for a guided demo and focused user test of learning/review/collection flows.**

Not yet full release-ready. The remaining release/demo risks are live physical-camera rehearsal, listening TTS playback, pronunciation scoring with real speech, and committing/pushing the staged Git LFS model asset changes.

## Final Command Results

### Backend API Smoke

Command shape:

```powershell
$env:PYTHONIOENCODING='utf-8'
# Inline FastAPI TestClient smoke from backend/
```

Result:

```text
strategy = FastAPI TestClient + real DB temporary GSD_E2E seed + cleanup
timestamp = 2026-05-19T23:21:43
totals = pass 34, fail 0, blocked 0
```

Seed context:

```text
seed_prefix = GSD_E2E
user_id = 8
object_id = 217
translation_id = 256
progress_id = 70
saved_scan_id = 235
collection_id = 8
```

Cleanup evidence:

```text
users_prefix = 0
objects_prefix = 0
translations_prefix = 0
progress_prefix_user_or_translation = 0
review_logs_prefix_user_or_translation = 0
collections_prefix = 0
scan_history_prefix = 0
dictionary_lookups_prefix = 0
media_prefix = 0
```

Note: an earlier dry-run smoke attempt left one `GSD_E2E` user/object before the final smoke. The corrected cleanup removed it before the final run, and the final post-run cleanup also returned all prefix counts to `0`.

### Backend/API Smoke Result

| Flow | Endpoint evidence | Result |
|---|---|---|
| App import/root | `GET /` -> `200` | Pass |
| Scan health | `GET /api/scan/test` -> `200` | Pass |
| Auth register/login/refresh | `/api/auth/register`, `/api/auth/login`, `/api/auth/refresh` -> `200` | Pass |
| Profile/settings | `GET/PUT /api/auth/profile`, `GET/PUT /api/auth/settings` -> `200` | Pass |
| Reference data | `GET /api/languages`, `/api/categories`, `/api/objects` -> `200` | Pass |
| Stats | `GET /api/stats` -> `200` | Pass |
| Scan by code | `POST /api/scan` -> `source=internal_db`, `200` | Pass |
| Save scan/history/learning | `POST /api/lich-su-quet` -> `learning_status=already_in_learning`, `200` | Pass |
| History | `GET /api/history`, `/api/history/235`, `/api/predictions/235` -> `200` | Pass |
| Review | `GET /api/review` returned 1 due card; `POST /api/review/70` -> `success=true`, `200` | Pass |
| Analytics/streak | `/api/analytics`, `/api/streak`, `/api/streak/record`, `/api/streak/sync` -> `200` | Pass |
| Collections | Create, add item, list, detail, review, insights, delete -> `200` | Pass |
| Dictionary DB-first | `POST /api/dictionary/translate` -> `source=internal_db`, `can_save=true`, `200` | Pass |
| Dictionary external lookup | `GET /api/dictionary/lookup?word=apple...` -> `source=free_dictionary_api`, `200` | Pass |
| Admin normal-user guard | `GET /api/admin/dashboard` -> expected `403` | Pass |

## Demo Flow Checklist

| Flow | Demo decision | Evidence / script note |
|---|---|---|
| Launch app and login | Show | Runtime launch/login state already stable; backend auth smoke passed. |
| Dashboard and bottom tabs | Show | Plan 8.2 runtime pass. |
| Scan via Gallery + COCO | Show | Focused scan rehearsal passed: `bus` detected at `91%`, result card rendered, `/api/scan` and `/api/lich-su-quet` returned `200`. |
| Scan via live camera | Show only if rehearsed first | Earlier camera capture reached confirmation screen, but physical live-camera detection/save was not separately tested. |
| Dictionary lookup/translate | Show | DB-first translate and external lookup passed. |
| History detail | Show | Runtime history detail rendered existing translation. |
| History Detail -> Add to Collection | Show | Runtime `POST /api/collections/6/items` -> `200 OK`; backend collection smoke also passed. |
| Normal review card | Show | Runtime `Monkey plush toy` review and backend seeded review both passed. |
| Quiz / typing / image matching | Show if seeded due words exist | Seeded runtime pass completed these modes. |
| Listening | Limited demo | Submit path passed, but TTS playback was not ready on emulator. |
| Pronunciation | Do not headline | Entry/mic opened, but scoring submit was blocked by no recognized speech. |
| Analytics/streak | Show | Runtime profile subflows and backend analytics/streak smoke passed. |
| Admin panel | Out of demo | Treat as internal; normal user guard returned expected `403`. |

## Minimal Automated Smoke Tests Decision

No persistent test files were added in Plan 8.4.

Reason:

- User requested evidence/docs first and no app/backend code changes unless evidence required it.
- The repo currently has no active `backend/tests`, `android/app/src/test`, or `android/app/src/androidTest` suite to extend cheaply.
- The inline TestClient smoke already covers the demo-critical backend path with real DB seed and cleanup.

Recommended next hardening step, if regression coverage is desired:

- Add `backend/tests/test_demo_smoke.py` backed by an isolated test database or transactional fixture.
- Add Android ViewModel/API mapping tests only after current working-tree feature changes are accepted/committed.

## Known Residual Risks

| Priority | Risk | Evidence | Next action |
|---|---|---|---|
| P1 | Live physical-camera scan has not been separately rehearsed. | Focused rehearsal passed through Gallery -> COCO -> result -> save; earlier live camera reached preview confirmation only. | If demo uses camera instead of Gallery, run one live-camera pass with a known COCO object. |
| P1 | Listening TTS playback unreliable on emulator. | Seeded runtime pass showed submit path OK but `Text-to-Speech not ready yet`. | Retest on physical device or stable emulator TTS. |
| P1 | Pronunciation scoring not verified. | SpeechRecognizer opened but returned no speech; no submit for pronunciation card. | Verify with real microphone input or move pronunciation out of demo. |
| P1 | Staged model asset still needs commit/push. | `best_float32.tflite` tracked with Git LFS but pending commit/push. | Commit staged LFS asset when user requests. |
| P2 | Advanced mode counters can show `Question 1/0` or `Word 1/0`. | Seeded runtime evidence. | Fix later if it appears during rehearsal. |
| P2 | Custom model is ruler-only by evidence. | No sidecar labels/training class metadata found. | Use COCO for broad object demo or provide training class order. |

## Next Phase

Phase 8 is complete from a GSD evidence standpoint. Suggested next work:

1. Commit staged LFS model asset changes.
2. Run one optional live-camera scan rehearsal if the final demo will not use Gallery.
3. Decide whether to fix listening/pronunciation/counter polish before the final demo.
