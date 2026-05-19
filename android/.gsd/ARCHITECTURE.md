# Architecture

> Updated by Phase 7.2 rebaseline on 2026-05-19.  
> This file is a high-level map only. For detailed evidence, read `.gsd/phases/7/CURRENT_STATE.md`.

## Overview

Object Scanner / Object Language App is a native Android app in Kotlin connected to a FastAPI backend. The Android app follows an MVVM style with Fragments, ViewModels, repositories, Retrofit APIs, local preferences/DataStore, CameraX, TensorFlow Lite/ML Kit object detection, and Gemini fallback through backend flows.

The project currently includes more than the original v1.0 scope: multi-modal review, pronunciation, analytics, collections, streaks, notifications, and backend admin moderation.

## Android Architecture

| Layer | Main paths | Responsibility |
|---|---|---|
| Application | `ObjectLanguageApp.kt` | Initialize token/session managers, Retrofit, repository, workers. |
| UI | `app/src/main/java/com/duc/objectlanguage/ui/**` | Fragments, adapters, navigation entry points, user flows. |
| ViewModel | `ui/**/**ViewModel.kt` | Screen state, coroutine calls, repository orchestration. |
| Repository | `data/repository/**` | App API access, collection API access, result wrapping. |
| API | `data/api/**` | Retrofit services and client configuration. |
| Local state | `data/local/**` | Token/session, notification preferences, streak DataStore. |
| Model | `data/model/**` | Android request/response DTOs. |
| Workers | `workers/**` | Daily reminders and streak validation scheduling. |
| ML/Scan | `ui/scan/**`, `app/src/main/assets/**` | Camera/scan flow, TFLite/ML Kit helpers, model assets. |

## Main Android Feature Areas

- Auth and guest session guard.
- Dashboard with stats, suggestions, and streak summary.
- Scan/detail/history flows.
- Dictionary and explore flows.
- Review flows: current card review, quiz, typing, listening, image matching, pronunciation.
- Analytics with MPAndroidChart dependency.
- Collections and collection insights.
- Profile/settings, notification settings, streak screen.

## Backend Architecture

| Layer | Main paths | Responsibility |
|---|---|---|
| FastAPI entry | `backend/main.py` | App setup, CORS, rate limiting, static mounts, router includes. |
| Routers | `backend/app/routers/**` | Auth, scan, review, collection, history, data, dictionary, admin, streak APIs. |
| Services | `backend/app/services/**` | Business logic for scan, learning, collections, dictionary, admin, streak, etc. |
| Repositories | `backend/app/repositories/**` | SQLAlchemy data access helpers. |
| Models | `backend/app/models/**` | SQLAlchemy database models. |
| Schemas | `backend/app/schemas/**` | Pydantic request/response schemas. |
| Migrations | `backend/alembic/**` | Database migration history. |
| Static admin | `backend/static/admin/**` | Existing internal admin panel mounted at `/admin-panel`. |

## Integration Points

| Integration | Current note |
|---|---|
| Android -> Backend | Retrofit services call `/api/...` routers for auth, scan, review, collections, history, data, dictionary, streak. |
| Android ML | Default custom model is `best_float32.tflite`; fallback/alternate model `yolov10n_int8.tflite` also exists. |
| Backend AI | Gemini service exists; dependency `google-generativeai` is marked deprecated/TODO migrate. |
| Notifications | Android WorkManager + DataStore + NotificationHelper; needs runtime verification. |
| Admin | Backend has admin router/static panel even though older SPEC marked web admin as non-goal. |

## Known Architecture Risks After Phase 7.2

- Phase 6 docs mention abstractions that are not present in code now: `FlashcardFragment`, `FlashcardViewModel`, `ReviewModeSelector`, `ReviewSessionManager`, `FuzzyMatcher`, `AudioPlayer.kt`, `RetrofitInstance.kt`.
- Review modes and advanced screens are implemented as files but not yet verified by build/runtime in Phase 7.
- Streak state exists both locally and on backend; source of truth needs verification.
- Custom model label list currently needs confirmation against the trained model.

## Next Architecture Action

Plan 7.3 should verify build/test/runtime and write `.gsd/phases/7/VERIFICATION.md`. A deeper architecture rewrite should happen only after that evidence exists.
