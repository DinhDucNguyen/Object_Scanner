# ROADMAP.md

> **Current Phase**: Not started
> **Milestone**: v1.0 — Đồ án tốt nghiệp
> **Deadline**: 10/04/2026

## Must-Haves (from SPEC)

- [ ] JWT Authentication (thay mock dependency hiện tại)
- [ ] Gemini Vision API integration (fallback object recognition)
- [ ] Text-to-Speech API cho phát âm
- [ ] Kotlin Android app với camera + crop
- [ ] SM-2 review flow hoàn chỉnh trên mobile

## Phases

### Phase 1: Backend — Auth & Security
**Status**: ⬜ Not Started
**Objective**: Implement JWT authentication thật, thay thế mock dependency
**Timeline**: 10/03 → 13/03 (3 ngày)
**Tasks**:
- Implement JWT token (login, register, refresh)
- Password hashing với bcrypt (thay SHA256)
- Auth middleware bảo vệ các endpoint
- Unit tests cho auth flow

---

### Phase 2: Backend — Gemini API & Audio
**Status**: ⬜ Not Started
**Objective**: Tích hợp Gemini Vision API cho fallback recognition, Text-to-Speech
**Timeline**: 13/03 → 17/03 (4 ngày)
**Tasks**:
- Gemini Vision API: nhận ảnh → trả object name + translations 
- Auto-save kết quả Gemini vào MySQL (objects + translations)
- Text-to-Speech endpoint (Google TTS hoặc tương đương)
- Example sentences endpoint (3 câu ví dụ cho mỗi từ)
- Tách schemas/common.py thành file riêng theo domain

---

### Phase 3: Mobile — Project Setup & Auth Screens
**Status**: ⬜ Not Started
**Objective**: Khởi tạo Kotlin app, implement đăng ký/đăng nhập
**Timeline**: 17/03 → 21/03 (4 ngày)
**Tasks**:
- Khởi tạo Kotlin project (MVVM + Retrofit + Hilt)
- Splash screen, onboarding
- Login & Register screens
- JWT token storage (DataStore)
- Profile & Settings screens

---

### Phase 4: Mobile — Camera & Object Scanning
**Status**: ⬜ Not Started
**Objective**: Core feature — chụp ảnh, crop, nhận diện vật thể
**Timeline**: 21/03 → 27/03 (6 ngày)
**Tasks**:
- Camera capture với CameraX
- Image crop UI (nếu nhiều vật thể)
- ML Kit on-device object detection
- Gọi backend API `/api/scan`
- Hiển thị kết quả: tên + dịch + phiên âm + audio + ví dụ
- Nút lưu vào bộ sưu tập

---

### Phase 4B: AI Track — YOLOv8 Custom Training & Mobile Inference (GSD)
**Status**: ⬜ Planned (Priority for thesis demo)
**Objective**: Tự train model nhận diện vật thể và tích hợp inference on-device để giảm phụ thuộc API
**Timeline**: 27/03 → 31/03 (4 ngày)
**GSD Flow**:
- SPEC: chốt danh sách classes, metric mục tiêu (mAP50, precision, recall), latency mục tiêu trên thiết bị
- PLAN: chốt pipeline data → train → evaluate → export → integrate Android
- EXECUTE: train YOLOv8n/s, chọn best checkpoint, export model mobile
- VERIFY: benchmark accuracy + FPS/latency trên ảnh test và thiết bị thật
**Tasks**:
- Tạo dataset theo format YOLO (train/val/test) từ dữ liệu đồ án
- Train baseline YOLOv8n (epochs/imgsz/augment cố định) và log kết quả
- Train YOLOv8s để so sánh accuracy/latency
- Đánh giá: mAP50, mAP50-95, precision, recall, confusion matrix
- Export model (ưu tiên TFLite/ONNX tùy runtime Android)
- Tích hợp inference vào luồng scan Android trước ML Kit fallback
- Mapping class_id → object_code hiện có trong backend DB
- Chuẩn bị bảng so sánh: ML Kit vs YOLOv8 vs Gemini fallback
- Demo script bảo vệ: luồng offline (YOLO) + luồng fallback (API)

---

### Phase 5: Mobile — Review & Collections
**Status**: ⬜ Not Started
**Objective**: Ôn tập SM-2 + quản lý bộ sưu tập trên mobile
**Timeline**: 27/03 → 01/04 (5 ngày)
**Tasks**:
- Review flashcard UI (swipe cards)
- SM-2 review flow (get due → answer → update)
- Collections list + detail screens
- Scan history screen
- Notifications nhắc ôn tập

---

### Phase 6: Polish & Testing
**Status**: ⬜ Not Started
**Objective**: UI polish, bug fix, testing, documentation
**Timeline**: 01/04 → 10/04 (9 ngày)
**Tasks**:
- UI/UX polish (animations, loading states, error handling)
- Backend unit tests + integration tests
- Mobile UI tests
- Performance optimization
- Documentation (API docs, user guide)
- Demo preparation
