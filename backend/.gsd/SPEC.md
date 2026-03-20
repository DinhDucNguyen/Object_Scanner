# SPEC.md — Project Specification

> **Status**: `FINALIZED`
> **Created**: 2026-03-10

## Vision

Object Language App — ứng dụng học ngôn ngữ thông qua nhận diện vật thể bằng camera. Người dùng chụp ảnh một đối tượng bất kỳ, ứng dụng nhận diện và hiển thị từ vựng đa ngôn ngữ kèm phiên âm, phát âm, ví dụ. Hỗ trợ ôn tập từ vựng qua thuật toán SM-2 (Spaced Repetition). Đây là đồ án tốt nghiệp, yêu cầu chất lượng gần sản phẩm thật nhất có thể.

## Goals

1. **Scan & Recognize** — Chụp ảnh vật thể, nhận diện qua YOLOv8/ML Kit, fallback Gemini API
2. **Translate & Learn** — Hiển thị tên + bản dịch + phiên âm + audio + ví dụ
3. **Smart Review** — Ôn tập từ vựng qua SM-2 spaced repetition
4. **Collection** — Lưu từ vựng yêu thích vào bộ sưu tập
5. **User Management** — Đăng ký, đăng nhập, profile, cài đặt

## Non-Goals (Out of Scope)

- Hỗ trợ đa ngôn ngữ ngoài Việt-Anh (mở rộng sau)
- Model 3D cho vật thể
- Social features (chia sẻ, bảng xếp hạng)
- Chạy trên iOS (chỉ Android)
- Admin dashboard web

## Users

Sinh viên và người học tiếng Anh nói chung. Ngôn ngữ gốc: Tiếng Việt. Ngôn ngữ đích: Tiếng Anh (ưu tiên). Sử dụng trên thiết bị Android.

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Mobile App | Kotlin (Android Native) |
| Object Detection | YOLOv8 (pre-trained) + ML Kit |
| Fallback AI | Gemini Vision API |
| Backend | FastAPI + SQLAlchemy + Alembic |
| Database | MySQL |
| Auth | JWT |
| Audio | Text-to-Speech API |

## Core User Flow

```
Chụp ảnh → Nhận diện vật thể
  ├─ Đúng? → Xác nhận
  └─ Sai?  → Crop lại ảnh → Nhận diện lại
       ↓
Hiển thị kết quả:
  • Tên vật thể (English)
  • Bản dịch (Vietnamese)
  • Phiên âm (IPA)
  • 🔊 Nút phát âm
  • "Xem ví dụ" → 3 câu ví dụ
  • ❤️ Lưu vào bộ sưu tập
       ↓
Ôn tập SM-2 hàng ngày
```

## Constraints

- **Deadline**: 10/04/2026 (~1 tháng)
- **Platform**: Android only (Kotlin)
- **Backend**: FastAPI (đã có codebase, cần hoàn thiện)
- **AI Model**: YOLOv8 custom + ML Kit on-device + Gemini API fallback
- **Database**: MySQL (đã có schema 14 bảng)

## Success Criteria

- [ ] Người dùng có thể đăng ký/đăng nhập với JWT
- [ ] Chụp ảnh → nhận diện vật thể → hiển thị từ vựng
- [ ] Crop ảnh nếu nhiều vật thể
- [ ] Nghe phát âm từ vựng
- [ ] Xem 3 câu ví dụ
- [ ] Lưu từ vựng vào bộ sưu tập yêu thích
- [ ] Ôn tập hàng ngày với SM-2
- [ ] Fallback qua Gemini API nếu vật thể chưa được train
- [ ] UI/UX chất lượng production-ready
