# AGENT.md — Object Language Backend Context

> **Purpose:** Cung cấp context nhanh cho AI agent hiểu dự án mà không cần đọc toàn bộ codebase.
>
> **Last updated:** 2026-03-20

---

## 🎯 Project Overview

**Object Language API** — Backend cho ứng dụng nhận diện vật thể đa ngôn ngữ (multilingual object recognition learning app).

**Luồng chính:**
1. Người dùng **quét vật thể** (qua camera hoặc ML Kit/YOLOv8 on-device)
2. Backend **tra cứu/tạo bản dịch** đa ngôn ngữ (DB sẵn hoặc gọi Gemini Vision API)
3. Người dùng **ôn tập từ vựng** qua thuật toán **SM-2 (Spaced Repetition)**
4. Hỗ trợ **bộ sưu tập**, **lịch sử**, **phản hồi AI**, **text-to-speech**

**Clients:**
- Android app (Kotlin, Gradle) — nằm trong `../android/`
- Mobile app (Expo/React Native) — phát triển riêng, gọi API này

---

## 🏗️ Architecture

**Pattern:** Layered Architecture — `Router → Service → Repository → Model`

```
e:\PythonFolder\Object_Scanner\backend\
├── main.py                      # FastAPI entry point (uvicorn, port 8000)
├── .env                         # Environment variables
├── requirements.txt             # Python dependencies
├── alembic/                     # Database migrations
├── alembic.ini                  # Alembic config
├── model_capabilities.yaml      # AI model selection guide (optional)
│
├── app/
│   ├── core/
│   │   └── config.py            # Settings (Pydantic BaseSettings, .env)
│   │
│   ├── db/
│   │   ├── session.py           # SQLAlchemy engine, SessionLocal, Base, get_db()
│   │   └── seed.py              # Database seeding script
│   │
│   ├── models/                  # SQLAlchemy ORM models (14 tables, 1 file = 1 table)
│   │   ├── user.py              # User (username, email, password_hash, role, status)
│   │   ├── profile.py           # Profile (1:1 with User)
│   │   ├── user_settings.py     # UserSettings (1:1 with User)
│   │   ├── language.py          # Language (code, name, flag_icon, is_active)
│   │   ├── category.py          # Category (name, parent_id — self-ref)
│   │   ├── object.py            # Object (object_code UK, difficulty_level 1-5)
│   │   ├── translation.py       # Translation (object_id FK, language_id FK, word_name, phonetic, definition)
│   │   ├── object_media.py      # ObjectMedia (media files for objects)
│   │   ├── learning_progress.py # LearningProgress (SM-2: easiness_factor, interval, next_review_date)
│   │   ├── user_collection.py   # UserCollection (name, is_public)
│   │   ├── collection_item.py   # CollectionItem (collection_id, translation_id)
│   │   ├── scan_history.py      # ScanHistory (user_id, object_id, confidence_score)
│   │   ├── ai_feedback_report.py# AIFeedbackReport (error_type, correct_label)
│   │   └── data_version.py      # DataVersion (version tracking)
│   │
│   ├── schemas/                 # Pydantic DTOs (request/response)
│   │   ├── common.py            # ScanRequest/Response, TranslationResponse, ReviewCard, Collection, Stats, etc.
│   │   └── user.py              # UserCreate, UserLogin, TokenResponse, ProfileResponse, etc.
│   │
│   ├── routers/                 # API endpoints (thin layer, chỉ gọi service)
│   │   ├── auth_router.py       # /api/auth — register, login, refresh, profile, settings
│   │   ├── scan_router.py       # /api — scan (code+image), translations, examples, TTS
│   │   ├── review_router.py     # /api — learning/add, review (SM-2)
│   │   ├── collection_router.py # /api/collections — CRUD collections + items + insights
│   │   ├── history_router.py    # /api — history, feedback
│   │   └── data_router.py       # /api — languages, categories, objects, stats, data-versions
│   │
│   ├── services/                # Business logic
│   │   ├── user_service.py      # Auth (register/login/JWT), profile, settings
│   │   ├── scan_service.py      # Object scanning (DB lookup → Gemini fallback → auto-save)
│   │   ├── gemini_service.py    # Gemini Vision API integration (identify_object, example_sentences)
│   │   ├── learning_service.py  # SM-2 spaced repetition logic
│   │   ├── collection_service.py# Collection management + insights
│   │   ├── history_service.py   # Scan history & AI feedback reports
│   │   ├── data_service.py      # Languages, categories, stats queries
│   │   └── tts_service.py       # Text-to-speech (gTTS)
│   │
│   ├── repositories/            # Data access layer (SQLAlchemy queries only)
│   │   ├── user_repo.py
│   │   ├── object_repo.py
│   │   ├── translation_repo.py
│   │   ├── language_repo.py
│   │   ├── learning_repo.py
│   │   ├── collection_repo.py
│   │   └── history_repo.py
│   │
│   ├── dependencies/
│   │   └── get_current_user.py  # JWT auth dependency (⚠️ hiện mock, cần implement)
│   │
│   └── utils/
│       ├── security.py          # Password hashing, JWT token creation/verification
│       └── sm2.py               # SM-2 algorithm implementation
│
├── docs/                        # Operational documentation
├── scripts/                     # Utility scripts (validate, search)
├── adapters/                    # Model-specific AI agent configs (Claude, Gemini, GPT)
└── .gsd/                        # GSD methodology state files
    ├── ARCHITECTURE.md          # Full architecture diagram
    ├── STACK.md                 # Technology stack details
    ├── SPEC.md                  # Requirements specification
    ├── ROADMAP.md               # Development roadmap
    └── STATE.md                 # Session memory
```

---

## 🛠️ Tech Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| **Language** | Python | 3.13.0 |
| **Framework** | FastAPI | ≥0.115.0 |
| **Server** | Uvicorn | ≥0.34.0 |
| **ORM** | SQLAlchemy | ≥2.0.36 |
| **Validation** | Pydantic | ≥2.10.0 |
| **Database** | MySQL (PyMySQL) | Port 3307 |
| **Migration** | Alembic | ≥1.13.0 |
| **Auth** | python-jose (JWT) + bcrypt | — |
| **AI/Vision** | google-generativeai (Gemini) | ≥0.8.0 |
| **TTS** | gTTS | ≥2.5.0 |
| **Image** | Pillow | ≥10.0.0 |
| **HTTP Client** | httpx | ≥0.28.0 |

---

## 🔌 API Endpoints Summary

### Auth (`/api/auth`)
| Method | Endpoint | Auth? | Description |
|--------|----------|-------|-------------|
| `POST` | `/register` | ❌ | Đăng ký (username, email, password) |
| `POST` | `/login` | ❌ | Đăng nhập → JWT tokens |
| `POST` | `/refresh` | ❌ | Refresh access token |
| `GET` | `/profile` | ✅ | Lấy user profile |
| `GET` | `/settings` | ✅ | Lấy user settings |
| `PUT` | `/settings` | ✅ | Cập nhật settings |

### Scan (`/api`)
| Method | Endpoint | Auth? | Description |
|--------|----------|-------|-------------|
| `GET` | `/scan/test` | ❌ | Health check |
| `POST` | `/scan` | ❌ | Scan bằng object_code (ML Kit/YOLO) |
| `POST` | `/scan/image` | ❌ | Scan bằng ảnh (Gemini Vision) |
| `GET` | `/objects/{code}/translations` | ❌ | Lấy bản dịch |
| `GET` | `/objects/{code}/examples` | ❌ | Sinh câu ví dụ (Gemini) |
| `GET` | `/tts/{word}` | ❌ | Text-to-Speech MP3 |

### Review (`/api`)
| Method | Endpoint | Auth? | Description |
|--------|----------|-------|-------------|
| `POST` | `/learning/add` | ✅ | Thêm từ vào danh sách ôn tập |
| `GET` | `/review` | ✅ | Lấy flashcards cần ôn hôm nay |
| `POST` | `/review/{id}` | ✅ | Submit kết quả ôn tập (SM-2) |

### Collections (`/api/collections`)
| Method | Endpoint | Auth? | Description |
|--------|----------|-------|-------------|
| `GET` | `/` | ✅ | Danh sách bộ sưu tập |
| `POST` | `/` | ✅ | Tạo bộ sưu tập |
| `GET` | `/{id}` | ✅ | Chi tiết BST + items |
| `DELETE` | `/{id}` | ✅ | Xóa BST |
| `POST` | `/{id}/items` | ✅ | Thêm item vào BST |
| `DELETE` | `/{id}/items/{item_id}` | ✅ | Xóa item khỏi BST |
| `GET` | `/{id}/insights` | ✅ | Analytics BST |

### History & Feedback (`/api`)
| Method | Endpoint | Auth? | Description |
|--------|----------|-------|-------------|
| `GET` | `/history` | ✅ | Lịch sử quét |
| `POST` | `/feedback` | ✅ | Phản hồi lỗi AI |
| `GET` | `/feedback` | ❌ | Danh sách phản hồi |

### Data (`/api`)
| Method | Endpoint | Auth? | Description |
|--------|----------|-------|-------------|
| `GET` | `/languages` | ❌ | Danh sách ngôn ngữ |
| `GET` | `/categories` | ❌ | Danh sách danh mục |
| `GET` | `/objects` | ❌ | Danh sách vật thể |
| `GET` | `/stats` | ✅ | Thống kê tổng quan |
| `GET` | `/data-versions` | ❌ | Phiên bản dữ liệu |

---

## 🗄️ Database Schema (14 Tables)

**Core entities:**
- `users` → `profiles` (1:1), `user_settings` (1:1)
- `categories` (self-referencing parent_id) → `objects`
- `objects` (object_code unique) → `translations`, `object_media`
- `languages` → `translations`
- `translations` → `learning_progress`, `collection_items`
- `user_collections` → `collection_items`
- `scan_history` → `ai_feedback_reports`
- `data_versions` (standalone)

**Key relationships:**
- `User` 1:N `ScanHistory`, `LearningProgress`, `UserCollection`, `AIFeedbackReport`
- `Object` 1:N `Translation`, `ObjectMedia`, `ScanHistory`
- `Translation` 1:N `LearningProgress`, `CollectionItem`

---

## 🔑 Key Business Logic

### Scan Flow (`scan_service.py`)
1. **Code scan** (`/api/scan`): Client gửi `object_code` → lookup DB → có thì trả translation, chưa có thì tạo object mới (trống)
2. **Image scan** (`/api/scan/image`): Upload ảnh → compress (800x800 JPEG, Pillow) → Gemini Vision API nhận diện → check DB → auto-save object + translations nếu mới

### Gemini Integration (`gemini_service.py`)
- Model fallback chain: `gemini-2.5-flash` → `gemini-2.0-flash` → `gemini-flash-latest`
- Handles quota exhaustion with automatic model fallback
- JSON response parsing with robust fallback (regex extraction)
- Returns structured: `{ object_code, category, translations[{ lang_code, word_name, phonetic, definition, example_sentences }] }`

### SM-2 Spaced Repetition (`sm2.py` + `learning_service.py`)
- Thuật toán SM-2 chuẩn: easiness_factor, interval, repetitions
- User nhận quality score (0-5) → tính next_review_date
- Flashcard system cho ôn tập hàng ngày

### Auth (`user_service.py` + `security.py`)
- JWT tokens (access + refresh) via `python-jose`
- Password hashing via `bcrypt` (requirement) — ⚠️ code hiện dùng SHA-256, cần migrate

---

## ⚠️ Known Technical Debt

| Issue | Location | Priority |
|-------|----------|----------|
| Mock auth dependency | `dependencies/get_current_user.py` | 🔴 High |
| SHA-256 password (not bcrypt) | `user_service.py` | 🔴 High |
| SECRET_KEY hardcoded | `core/config.py` | 🟡 Medium |
| All schemas in `common.py` | `schemas/common.py` | 🟡 Medium |
| No test suite | Project-wide | 🟡 Medium |
| No logging framework | Project-wide | 🟡 Medium |
| Deprecated `google-generativeai` | `gemini_service.py` | 🟢 Low |
| CORS allow all origins | `main.py` | 🟢 Low (dev only) |

---

## 🚀 How to Run

```bash
# Install dependencies
pip install -r requirements.txt

# Set environment variables
cp .env.example .env  # Edit DATABASE_URL, GEMINI_API_KEY

# Run database migrations
alembic upgrade head

# Seed database (optional)
python -m app.db.seed

# Start development server
python main.py
# or
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

**Access:**
- API: http://localhost:8000
- Docs: http://localhost:8000/docs (Swagger)
- ReDoc: http://localhost:8000/redoc

---

## 📐 Conventions

| Aspect | Convention |
|--------|-----------|
| **Models** | PascalCase (`UserCollection`), 1 file = 1 table |
| **Tables** | snake_case plural (`user_collections`) |
| **Files** | snake_case (`user_collection.py`, `scan_service.py`) |
| **Routers** | `{domain}_router.py` → prefix `/api/{domain}` |
| **Architecture** | Router → Service → Repository → Model (strict) |
| **Router** | Chỉ gọi Service, không gọi Repository trực tiếp |
| **Service** | Chứa business logic, gọi Repository |
| **Repository** | Chỉ chứa DB queries, không có business logic |
| **Commits** | `type(scope): description` (feat, fix, docs, refactor, test, chore) |

---

## 🔗 Related Components

- **Android App:** `e:\PythonFolder\Object_Scanner\android\` — Kotlin/Gradle native app
- **Mobile App (Expo):** Separate repo — React Native with i18n, AdMob, health monitoring
- **NestJS Server:** Separate project — Being refactored (controllers → smaller modules)
- **Database:** MySQL at `192.168.5.248:3307`, database `language_learning_db`

---

## 📚 Reference

- [ARCHITECTURE.md](.gsd/ARCHITECTURE.md) — Full architecture diagram with Mermaid
- [STACK.md](.gsd/STACK.md) — Detailed technology stack
- [ROADMAP.md](.gsd/ROADMAP.md) — Development roadmap and phases
- [PROJECT_RULES.md](PROJECT_RULES.md) — GSD methodology rules
- [GSD-STYLE.md](GSD-STYLE.md) — Code style and conventions
