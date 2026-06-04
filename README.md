# LengoLens

Ứng dụng Android học tiếng Anh qua nhận diện vật thể — chụp ảnh bất kỳ đồ vật nào, AI nhận diện và hiển thị từ vựng (tên, phiên âm IPA, định nghĩa, câu ví dụ, phát âm), lưu vào bộ sưu tập cá nhân và ôn tập bằng flashcard spaced repetition.

> Đồ án tốt nghiệp — Khoa Công nghệ Số, 2026.

---

## Tính năng chính

| Tính năng | Mô tả |
|-----------|-------|
| **Quét vật thể** | Camera nhận diện qua YOLOv8 on-device → ML Kit ImageLabeling → Gemini API fallback |
| **Từ vựng chi tiết** | Tên tiếng Anh, phiên âm IPA, định nghĩa, câu ví dụ, phát âm TTS |
| **Bộ sưu tập** | Tạo và quản lý bộ từ vựng cá nhân theo chủ đề |
| **Ôn tập flashcard** | Spaced repetition SM-2, badge đếm từ cần ôn hôm nay |
| **Chuỗi ngày học** | Streak tracking, calendar lịch sử, milestone |
| **Thống kê học tập** | Biểu đồ tiến độ, tỉ lệ ghi nhớ, lịch sử ôn tập |
| **Tra từ điển** | Dịch và lưu từ không cần camera |
| **Khám phá chủ đề** | Từ vựng theo danh mục có sẵn |
| **Thông báo** | Nhắc nhở ôn tập hàng ngày, cài đặt thời gian thông báo |
| **Onboarding** | Hướng dẫn tính năng cho người dùng mới |
| **Đăng nhập** | Email/password + Google Sign-In, JWT auth |
| **Admin panel** | Duyệt ảnh training, quản lý object/category/translation, export dataset YOLO |

---

## Tech Stack

### Android
- **Kotlin** — MVVM, ViewBinding, Navigation Component, Coroutines
- **TensorFlow Lite** — chạy model YOLOv8 on-device
- **ML Kit** — Object Detection + ImageLabeling (fallback sau YOLO)
- **CameraX** — camera preview và chụp ảnh
- **Retrofit** + OkHttp — gọi REST API
- **Room** — local database
- **DataStore** — lưu preferences
- **WorkManager** — background workers
- **Glide** — load và cache ảnh
- **MPAndroidChart** — biểu đồ thống kê
- **Lottie** — animation
- **UCrop** — crop ảnh trước khi quét
- **Shimmer** — loading skeleton
- **Google Sign-In** — đăng nhập Google
- **Material Design 3**

### Backend
- **Python 3.11+** + **FastAPI**
- **SQLite** (dev) / **MySQL** (prod) qua SQLAlchemy + Alembic
- **JWT** — xác thực và phân quyền
- **Gemini API** — nhận diện vật thể và sinh từ vựng
- **Cloudinary** — lưu trữ ảnh
- **gTTS** — text-to-speech
- **slowapi** — rate limiting
- **google-auth** — xác thực Google token

---

## Cấu trúc thư mục

```
Object_Scanner/
├── android/                        # Android app
│   └── app/src/main/
│       ├── java/.../ui/            # Fragments, ViewModels
│       ├── java/.../data/          # Repository, API, Room, Models
│       ├── java/.../utils/         # Helper functions
│       ├── java/.../workers/       # Background workers
│       └── res/                    # Layouts, strings, drawables
├── backend/                        # FastAPI server
│   ├── app/
│   │   ├── routers/                # API endpoints
│   │   ├── services/               # Business logic
│   │   ├── repositories/           # Data access layer
│   │   ├── schemas/                # Pydantic models
│   │   ├── models/                 # SQLAlchemy models
│   │   ├── db/                     # Database session
│   │   ├── dependencies/           # FastAPI dependencies
│   │   ├── utils/                  # Helper functions
│   │   └── core/                   # Config, security
│   ├── alembic/                    # Database migrations
│   ├── main.py
│   └── requirements.txt
└── README.md
```

---

## Cài đặt & Chạy

### Yêu cầu hệ thống

- Python 3.11+
- Android Studio Hedgehog (2023.1.1) trở lên
- JDK 17+
- Android device hoặc emulator API 24+ (Android 7.0+)

---

### 1. Backend

```bash
cd backend

# Tạo virtual environment
python -m venv venv
source venv/bin/activate        # Linux/Mac
venv\Scripts\activate           # Windows

# Cài dependencies
pip install -r requirements.txt

# Tạo file .env từ template
cp .env.example .env
# Mở .env và điền các giá trị (xem bảng bên dưới)

# Chạy database migration
alembic upgrade head

# Khởi động server
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

Server chạy tại `http://0.0.0.0:8000`.  
Swagger API docs: `http://localhost:8000/docs`

#### Các biến môi trường cần thiết (`.env`)

| Biến | Bắt buộc | Mô tả |
|------|----------|-------|
| `DATABASE_URL` | ✅ | SQLite: `sqlite:///./objectscanner.db` hoặc MySQL |
| `SECRET_KEY` | ✅ | Key JWT — sinh bằng `python -c "import secrets; print(secrets.token_hex(32))"` |
| `GEMINI_API_KEY` | ✅ | Lấy tại [Google AI Studio](https://aistudio.google.com/app/apikey) |
| `CLOUDINARY_CLOUD_NAME` | ✅ | Lấy tại [Cloudinary Console](https://cloudinary.com/console) |
| `CLOUDINARY_API_KEY` | ✅ | Cloudinary API Key |
| `CLOUDINARY_API_SECRET` | ✅ | Cloudinary API Secret |
| `SMTP_HOST` | Tùy chọn | Server email — dùng `smtp.gmail.com` nếu dùng Gmail |
| `SMTP_USER` | Tùy chọn | Gmail address |
| `SMTP_PASSWORD` | Tùy chọn | Gmail App Password (không phải mật khẩu thường) |
| `CORS_ALLOW_ORIGINS` | Tùy chọn | Để trống nếu chỉ test local |

---

### 2. Android App

#### Bước 1 — Cấu hình địa chỉ server

Tạo hoặc mở file `android/local.properties` và thêm:

```properties
# IP của máy chạy backend — phải cùng mạng với điện thoại
SERVER_IP=192.168.x.x
SERVER_PORT=8000
SERVER_SCHEME=http

# Nếu dùng server public có HTTPS
# SERVER_IP=your-domain.com
# SERVER_PORT=443
# SERVER_SCHEME=https
```

> `local.properties` đã được gitignore — không bao giờ bị commit lên GitHub.

> **Quan trọng:** Mỗi lần đổi mạng (WiFi → hotspot → mạng trường), IP thay đổi → cập nhật `SERVER_IP` và build lại APK.

#### Bước 2 — Build và cài app

1. Mở thư mục `android/` bằng Android Studio
2. Chờ Gradle sync xong
3. Cắm điện thoại hoặc bật emulator
4. Nhấn **Run ▶** để build và cài trực tiếp

Hoặc build APK từ terminal:

```bash
cd android
./gradlew assembleDebug      # Debug APK — kết nối HTTP local
./gradlew assembleRelease    # Release APK — chặn cleartext, cần server HTTPS
```

APK output: `android/app/build/outputs/apk/`

---

## Lưu ý khi chạy

### Database migration
Bắt buộc chạy trước lần đầu tiên và sau mỗi lần pull code mới có thay đổi schema:

```bash
cd backend
alembic upgrade head    # Nâng lên version mới nhất
alembic heads           # Kiểm tra head hiện tại
alembic current         # Kiểm tra version đang chạy
```

Version hiện tại: `0016_user_email_verified`

### Model YOLO
Model YOLOv8 nhận diện vật thể đã được train sẵn trên Google Colab và đặt tại:
```
android/app/src/main/assets/best.tflite
```
Không cần làm gì thêm — app tự load model khi khởi động.

### Deploy lên server public
Để app chạy được mọi nơi mà không phụ thuộc mạng LAN:
1. Deploy backend lên Vercel / Railway / VPS
2. Cập nhật `SERVER_IP` = domain, `SERVER_SCHEME` = `https`
3. Build lại APK release

---

## API Endpoints chính

| Method | Endpoint | Mô tả |
|--------|----------|-------|
| `POST` | `/api/auth/register` | Đăng ký tài khoản |
| `POST` | `/api/auth/login` | Đăng nhập, trả về JWT |
| `POST` | `/api/scan` | Quét và nhận diện vật thể |
| `GET` | `/api/history` | Lịch sử quét |
| `GET` | `/api/review` | Lấy danh sách thẻ ôn tập hôm nay |
| `POST` | `/api/review/{progress_id}` | Nộp kết quả ôn tập |
| `GET` | `/api/review/count` | Số từ cần ôn hôm nay |
| `GET` | `/api/collections` | Danh sách bộ sưu tập |
| `GET` | `/api/streak` | Thông tin chuỗi ngày học |
| `GET` | `/api/dictionary` | Tra từ điển |

Xem đầy đủ tại `http://localhost:8000/docs` khi server đang chạy.

---

## Tác giả

**Đinh Đức Nguyên** — Đồ án tốt nghiệp 2026
