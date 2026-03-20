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
- Chế độ offline (yêu cầu mạng để gọi API/Gemini)

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
- **Database**: MySQL (schema 14 bảng)

## Success Criteria (v1.0)

- [x] Người dùng có thể đăng ký/đăng nhập với JWT
- [x] Chụp ảnh → nhận diện vật thể → hiển thị từ vựng
- [x] Crop ảnh nếu nhiều vật thể
- [x] Nghe phát âm từ vựng
- [x] Xem 3 câu ví dụ
- [x] Lưu từ vựng vào bộ sưu tập yêu thích
- [x] Ôn tập hàng ngày với SM-2
- [x] Fallback qua Gemini API nếu vật thể chưa được train
- [x] UI/UX chất lượng production-ready

---

## Phase 6 Extension: Advanced Learning Experience (v1.5)

> **Status**: `PLANNING`
> **Focus**: Learning Effectiveness & Engagement
> **Timeline**: 2 tuần (11/03 - 25/03/2026)

### Vision for v1.5

Transform app from basic vocabulary tool → **Complete Language Learning Platform** với multiple learning modes, advanced analytics, và personalized learning paths.

### New Goals (v1.5)

6. **Multi-Modal Learning** — Multiple review modes (flashcard, quiz, typing, listening, image matching)
7. **Pronunciation Mastery** — Speech recognition với scoring & feedback
8. **Adaptive Learning** — AI-powered difficulty adjustment & personalized recommendations
9. **Deep Analytics** — Visual progress tracking với charts, heatmaps, insights
10. **Smart Collections** — Auto-categorization, difficulty-based grouping, custom study sets

### Success Criteria (v1.5)

**Learning Modes:**
- [ ] Flashcard mode với swipe gestures (Know/Don't Know)
- [ ] Multiple choice quiz (4 options, timed)
- [ ] Typing test (spell the word correctly)
- [ ] Listening test (hear audio → type word)
- [ ] Image matching game (match translation to image)

**Pronunciation:**
- [ ] Record user pronunciation
- [ ] Compare với native audio using speech recognition
- [ ] Scoring system (0-100%)
- [ ] Visual waveform comparison
- [ ] Retry until achieve 80%+ score

**Analytics:**
- [ ] Line chart: words learned over time (daily/weekly/monthly)
- [ ] Pie chart: mastery distribution (New/Learning/Mastered)
- [ ] Category breakdown chart
- [ ] Calendar heatmap: activity tracking (GitHub-style)
- [ ] Insights: "Best learning time", "Weakest category", "Streak record"

**Collections:**
- [ ] Auto-create collections by category
- [ ] Difficulty-based filters (Easy/Medium/Hard based on mastery)
- [ ] Custom study sets per collection
- [ ] Practice mode per collection
- [ ] Export collection to Anki format

**Personalization:**
- [ ] Daily learning goal (customizable: 10/20/50 words)
- [ ] Adaptive difficulty (show harder words more often)
- [ ] Recommendation engine: "Words you should review"
- [ ] Learning streak tracking (consecutive days)
- [ ] Smart notifications: "You have 5 words due"

### Technical Requirements

**New APIs (Backend - tất cả đã có sẵn):**
- ✅ `GET /api/learning/progress/{user_id}` - Learning stats
- ✅ `GET /api/learning/due` - Due words for review
- ✅ `POST /api/learning/{id}/review` - Submit review results
- ✅ `GET /api/collections` - User collections
- ✅ `GET /api/history` - Scan history

**New Android Components:**
- FlashcardFragment + ViewModel
- QuizFragment + ViewModel  
- TypingTestFragment + ViewModel
- ListeningTestFragment + ViewModel
- ImageMatchingFragment + ViewModel
- PronunciationFragment + SpeechRecognizer
- AnalyticsFragment + MPAndroidChart
- CollectionDetailFragment + RecyclerView adapter
- NotificationWorker (WorkManager for daily reminders)

**Libraries to Add:**
- MPAndroidChart (charts/graphs)
- Android Speech Recognition API
- Lottie (animations for achievements)
- WorkManager (background tasks)

### User Flows

#### Multi-Modal Review Flow
```
Dashboard → "Start Review" (5 words due)
  ↓
Choose Review Mode:
  1. 🎴 Flashcard (default, fastest)
  2. 🎯 Multiple Choice Quiz
  3. ⌨️ Typing Test  
  4. 🔊 Listening Test
  5. 🖼️ Image Matching
  ↓
Complete review → Submit results → Update SM-2
  ↓
Show results: "4/5 correct, +40 XP"
  ↓
Option: "Review incorrect words again"
```

#### Pronunciation Practice Flow
```
Word Detail Screen → "🎤 Practice Pronunciation"
  ↓
1. Play native audio (can replay)
2. Record button → User speaks
3. Auto-stop after 3 seconds
  ↓
Processing... (speech-to-text)
  ↓
Show Results:
  • Score: 85/100 ⭐⭐⭐⭐
  • Recognized: "apple" ✅
  • Waveform comparison (expected vs actual)
  • Feedback: "Great! Try emphasis on 'a'"
  ↓
Options:
  - "Try Again" (if < 80%)
  - "Next Word"
  - "Save Recording"
```

#### Analytics Dashboard Flow
```
Dashboard → "📊 Statistics" tab
  ↓
Overview Card:
  • Total words: 247
  • Mastered: 89 (36%)
  • Current streak: 12 days 🔥
  • This week: +28 words
  ↓
Charts Section:
  1. Line Chart: Progress over time
     - X: Last 30 days
     - Y: Cumulative words learned
     
  2. Pie Chart: Mastery breakdown
     - New (53%), Learning (25%), Mastered (22%)
     
  3. Bar Chart: Category performance
     - Animals (15), Food (23), Objects (31)...
     
  4. Heatmap: Daily activity
     - Green squares for active days (like GitHub)
     - Tap to see words learned that day
     ↓
Insights Section:
  • 💡 "You learn best in the evening (6-9 PM)"
  • 💡 "Your weakest category: Verbs (45% accuracy)"
  • 💡 "Words due tomorrow: 8"
  • 💡 "Streak record: 18 days (beat it!)"
```

### Non-Goals (v1.5)

- Social features (still out of scope)
- Offline mode (still requires network)
- iOS version
- Web admin dashboard
- Multiplayer games
- Live tutoring

### Metrics for Success

**Engagement:**
- Users complete 80%+ of due reviews
- Average 3+ review sessions per week
- Pronunciation practice used by 60%+ users

**Learning Effectiveness:**
- 70%+ words reach "Mastered" status within 30 days
- Quiz accuracy improves 15%+ over 2 weeks
- Pronunciation scores average 75%+

**Retention:**
- 7-day retention: 60%+
- Users maintain 7+ day streaks
- Daily active usage: 15+ minutes
