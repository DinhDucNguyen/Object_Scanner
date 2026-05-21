# Object Scanner — Context cho Claude

## Dự án là gì
Đồ án tốt nghiệp sinh viên. App Android học tiếng Anh qua nhận diện vật thể — chụp ảnh → nhận diện → hiển thị từ vựng (tên, dịch nghĩa, IPA, ví dụ) → lưu collection → ôn tập flashcard.

**Không phải production** — không cần giải pháp phức tạp như Redis, rate limiting nghiêm ngặt, v.v.

## Tech Stack
- **Android**: Kotlin, MVVM, Navigation Component, Retrofit, Room
- **Backend**: Python, FastAPI, SQLite
- **AI/ML**: YOLOv10 (train trên Google Colab), ML Kit on-device, Gemini API fallback
- **Auth**: JWT
- **Admin panel**: HTML/JS tĩnh trong `backend/static/admin/`

## Cấu trúc thư mục
```
Object_Scanner/
├── android/          # App Android (Kotlin)
│   └── app/src/main/java/com/duc/objectlanguage/
│       ├── ui/       # Fragments, ViewModels
│       ├── data/     # Repository, API, Room
│       └── ...
├── backend/          # FastAPI server
│   └── app/
│       ├── routers/  # API endpoints
│       ├── services/ # Business logic
│       ├── schemas/  # Pydantic models
│       └── ...
└── CLAUDE.md
```

## Tính năng đã làm
- Đăng ký / đăng nhập JWT
- Chụp ảnh + nhận diện vật thể (YOLO + ML Kit + Gemini fallback)
- Hiển thị chi tiết từ vựng
- Collection (bộ sưu tập từ vựng cá nhân)
- Review / flashcard (SM-2 spaced repetition)
- Admin panel quản lý dữ liệu
- Train model YOLO trên Google Colab

## Cách làm việc
- Trả lời **tiếng Việt**
- User là sinh viên — giải thích đơn giản, thực tế
- Ưu tiên giải pháp đơn giản, đúng việc, không over-engineer
