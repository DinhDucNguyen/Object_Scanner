# ROADMAP.md

> **Current Phase**: Phase 6
> **Milestone**: v1.5 (Advanced Learning Experience)
> **Timeline**: 11/03 - 25/03/2026
> **Deadline**: 10/04/2026 (full project)

## Must-Haves (from SPEC)
- [x] User authentication (JWT)
- [x] Object detection via Camera (YOLOv8 + ML Kit + Gemini API fallback)
- [x] Vocabulary display (English name, Vietnamese translation, IPA, Audio, Examples)
- [x] Vocabulary collection (Favorites)
- [x] Spaced Repetition (SM-2) review system
- [x] Production-ready UI/UX

## Phases

### Phase 1: Foundation & Auth
**Status**: ✅ Complete
**Objective**: Setup project architecture, API client, local storage, and basic Authentication flow.
**Requirements**: REQ-01

### Phase 2: Core Scanning & Inference Flow
**Status**: ✅ Complete
**Objective**: Implement CameraX, ML Kit on-device inference, image cropping, and Gemini API fallback integration.
**Requirements**: REQ-02

### Phase 3: Vocabulary Presentation & Collection
**Status**: ✅ Complete
**Objective**: Display translation, IPA, audio TTS, examples, and integrate "Add to Collection" feature with backend.
**Requirements**: REQ-03, REQ-04

### Phase 4: Smart Review (SM-2)
**Status**: ✅ Complete
**Objective**: Build the daily review UI and integrate SM-2 algorithm logic to schedule learning items.
**Requirements**: REQ-05

### Phase 5: Polish & Final Testing
**Status**: ✅ Complete
**Objective**: Refine UI/UX (animations, error handling), conduct end-to-end testing, bug fixing.
**Requirements**: REQ-06

---
**Milestone v1.0 Achieved**: All Must-Haves implemented successfully. READY FOR DEPLOYMENT.

---

## Phase 6: Advanced Learning Experience (v1.5)

> **Status**: 🚧 In Planning
> **Duration**: 2 tuần (12 ngày làm việc)
> **Focus**: Transform app thành complete learning platform

### Must-Haves for v1.5
- [ ] Multi-modal review system (5 learning modes)
- [ ] Pronunciation practice với speech recognition
- [ ] Visual analytics dashboard
- [ ] Smart collections management
- [ ] Learning streak & daily goals

### Phase 6 Breakdown

#### **Wave 1: Multi-Modal Review System** (4 ngày)
**Dependencies**: None (all APIs exist)

##### Task 1.1: Flashcard Mode (1 ngày)
**Objective**: Tinder-style swipe interface cho review nhanh
- [ ] FlashcardFragment + ViewModel
- [ ] ViewPager2 với custom animation
- [ ] Swipe gestures: Right = Know, Left = Don't know, Up = Mark difficult
- [ ] Card flip animation (front: English, back: Vietnamese)
- [ ] Progress indicator (3/10 cards)
- [ ] Submit results → Update SM-2 via API

##### Task 1.2: Multiple Choice Quiz (1 ngày)
**Objective**: Quiz với 4 đáp án, timed mode
- [ ] QuizFragment + ViewModel
- [ ] Random 4 options (1 correct + 3 distractors)
- [ ] Timer: 10 seconds per question
- [ ] Highlight correct/wrong answers
- [ ] Score calculation: "8/10 correct (80%)"
- [ ] Retry incorrect words option

##### Task 1.3: Typing & Listening Tests (1 ngày)
**Objective**: Active recall modes
- [ ] TypingTestFragment: Show Vietnamese → type English
- [ ] Spell checking với Levenshtein distance (allow 1-2 typos)
- [ ] ListeningTestFragment: Play audio → type word
- [ ] Audio playback controls (play, replay, slow speed)
- [ ] Hint system: "Show first letter", "Show length"

##### Task 1.4: Image Matching Game (1 ngày)
**Objective**: Visual memory reinforcement
- [ ] ImageMatchingFragment với grid layout
- [ ] 6 cards: 3 images + 3 translations
- [ ] Flip animation khi tap
- [ ] Match detection với fade-out animation
- [ ] Time tracking: complete in < 60 seconds
- [ ] Shuffle cards mỗi round

**Wave 1 Verification:**
```bash
# Manual test checklist
- ✅ Swipe flashcards smoothly
- ✅ Quiz shows 4 unique options
- ✅ Typing accepts minor typos
- ✅ Listening plays audio correctly
- ✅ Image matching detects pairs
- ✅ All modes update SM-2 intervals
```

---

#### **Wave 2: Pronunciation Practice** (3 ngày)
**Dependencies**: Wave 1 complete

##### Task 2.1: Speech Recognition Setup (1 ngày)
**Objective**: Integrate Android Speech Recognition API
- [ ] PronunciationFragment + ViewModel
- [ ] Request RECORD_AUDIO permission
- [ ] SpeechRecognizer setup với RecognizerIntent
- [ ] Handle recognition results & errors
- [ ] Confidence score extraction
- [ ] Noise cancellation settings

##### Task 2.2: Pronunciation UI & Recording (1 ngày)
**Objective**: User-friendly recording interface
- [ ] Native audio player với waveform visualization
- [ ] Record button với microphone animation (Lottie)
- [ ] Recording status: "Listening..." indicator
- [ ] Auto-stop sau 3 seconds
- [ ] Playback recorded audio
- [ ] Visual feedback: green mic = good volume, red = too quiet

##### Task 2.3: Scoring & Feedback System (1 ngày)
**Objective**: Accurate pronunciation assessment
- [ ] Compare recognized text với expected word
- [ ] Calculate similarity score (0-100%)
- [ ] Phoneme-level analysis (optional: use IPA)
- [ ] Feedback messages:
  - 90-100%: "Perfect! 🎉"
  - 70-89%: "Great job! ⭐⭐⭐"
  - 50-69%: "Good try, try again 👍"
  - < 50%: "Listen carefully and retry 👂"
- [ ] Save best score per word
- [ ] Unlock "Pronunciation Master" badge at 80%+ average

**Wave 2 Verification:**
```bash
# Test với 10 words
- ✅ Microphone records clearly
- ✅ Recognition accuracy > 80% for clear speech
- ✅ Scoring reflects pronunciation quality
- ✅ Feedback messages display correctly
- ✅ User can retry unlimited times
```

---

#### **Wave 3: Visual Analytics Dashboard** (3 ngày)
**Dependencies**: Wave 1 complete (need learning data)

##### Task 3.1: MPAndroidChart Integration (0.5 ngày)
**Objective**: Setup charting library
- [ ] Add `com.github.PhilJay:MPAndroidChart:v3.1.0` to build.gradle
- [ ] Create AnalyticsFragment + ViewModel
- [ ] Setup chart styling (colors, fonts, animations)
- [ ] Custom value formatter cho dates/numbers

##### Task 3.2: Progress Line Chart (0.5 ngày)
**Objective**: Words learned over time
- [ ] Fetch learning history từ backend (GET /api/learning/progress)
- [ ] LineChart: X = dates (last 30 days), Y = cumulative words
- [ ] Data points với circular markers
- [ ] Fill area under line với gradient
- [ ] Zoom & pan gestures
- [ ] Tap data point → show details "March 10: +5 words"

##### Task 3.3: Mastery & Category Charts (1 ngày)
**Objective**: Distribution visualization
- [ ] PieChart: Mastery levels (New/Learning/Mastered)
  - Custom colors: Gray/Yellow/Green
  - Center text: "36% Mastered"
  - Legend với percentages
- [ ] BarChart: Words per category
  - Horizontal bars: Animals (15), Food (23), Objects (31)...
  - Sort by count descending
  - Color-coded by category

##### Task 3.4: Activity Heatmap (1 ngày)
**Objective**: GitHub-style contribution calendar
- [ ] Custom CalendarView hoặc RecyclerView grid
- [ ] 7x5 grid (35 days visible)
- [ ] Color intensity: 0 words (gray) → 10+ words (dark green)
- [ ] Tap cell → show popup "March 10: 5 words learned"
- [ ] Scroll to view past months
- [ ] Current day highlight

**Wave 3 Verification:**
```bash
# Visual inspection
- ✅ Line chart shows smooth growth trend
- ✅ Pie chart percentages sum to 100%
- ✅ Bar chart sorted correctly
- ✅ Heatmap shows accurate dates
- ✅ All charts animate smoothly
```

---

#### **Wave 4: Smart Collections & Insights** (2 ngày)
**Dependencies**: Wave 3 complete

##### Task 4.1: Enhanced Collections UI (1 ngày)
**Objective**: Better organization & filtering
- [ ] Auto-create collections by category (Animals, Food, etc.)
- [ ] CollectionDetailFragment với RecyclerView
- [ ] Filter options:
  - By mastery: New / Learning / Mastered
  - By difficulty: Easy (90%+) / Medium (70-89%) / Hard (<70%)
  - By date added: Recent / Oldest
- [ ] Sort options: Alphabetical / Progress / Due date
- [ ] Bulk actions: "Practice all in this collection"
- [ ] Collection stats: "15 words, 60% mastered"

##### Task 4.2: Insights & Recommendations (1 ngày)
**Objective**: AI-powered learning tips
- [ ] Calculate insights từ learning data:
  - Best learning time (most reviews completed)
  - Weakest category (lowest accuracy)
  - Average reviews per day
  - Streak record (longest consecutive days)
  - Predicted mastery date for new words
- [ ] InsightsCard component với icon + text
- [ ] Recommendations:
  - "Review Animals category (45% accuracy)"
  - "You have 8 words due tomorrow"
  - "Try practicing in the evening (your best time)"
- [ ] Daily goal tracking widget: "Daily Goal: 7/10 words ⭐"

**Wave 4 Verification:**
```bash
# Test insights accuracy
- ✅ Best learning time matches user activity
- ✅ Weakest category calculation correct
- ✅ Daily goal progress updates real-time
- ✅ Recommendations actionable & relevant
```

---

#### **Wave 5: Learning Streaks & Notifications** (2 ngày)
**Dependencies**: All waves complete

##### Task 5.1: Streak Tracking System (1 ngày)
**Objective**: Encourage daily usage
- [ ] Streak calculation logic:
  - Consecutive days with > 0 reviews completed
  - Reset if miss a day
  - Store in SharedPreferences (local tracking)
- [ ] Streak display in Dashboard:
  - 🔥 icon với animated flames (Lottie)
  - "12 day streak!" text
  - Streak history graph (mini line chart)
- [ ] Streak milestones:
  - 7 days: "Week Warrior 🏆"
  - 30 days: "Monthly Master 🌟"
  - 100 days: "Century Club 💯"

##### Task 5.2: Smart Notifications (1 ngày)
**Objective**: Daily reminders với WorkManager
- [ ] Setup WorkManager periodic task (daily at 8 PM)
- [ ] NotificationWorker class:
  - Check if user has due words
  - Fetch due count từ API
  - Show notification: "You have 5 words to review 📚"
- [ ] Notification channel: "Learning Reminders"
- [ ] Tap notification → open ReviewFragment
- [ ] Settings option: Enable/disable notifications, set reminder time
- [ ] Smart logic: Don't notify if user already reviewed today

**Wave 5 Verification:**
```bash
# Test notification flow
- ✅ Notification appears at scheduled time
- ✅ Notification shows correct due count
- ✅ Tapping notification opens app to review
- ✅ Notification respects user settings
- ✅ Streak updates correctly after review
```

---

### Phase 6 Timeline Summary

| Wave | Duration | Tasks | Focus |
|------|----------|-------|-------|
| 1 | 4 ngày | 4 | Multi-modal review modes |
| 2 | 3 ngày | 3 | Pronunciation practice |
| 3 | 3 ngày | 4 | Visual analytics |
| 4 | 2 ngày | 2 | Collections & insights |
| 5 | 2 ngày | 2 | Streaks & notifications |

**Total**: 14 ngày = 2 tuần (buffer: 2 ngày)

### Success Metrics (Phase 6)

**Completion Criteria:**
- [ ] All 5 review modes functional
- [ ] Pronunciation scoring accurate (tested với 20 words)
- [ ] 4 charts rendering correctly
- [ ] Activity heatmap shows 30+ days
- [ ] Collections filterable by 3 criteria
- [ ] 3 insights displaying accurately
- [ ] Streak tracking working
- [ ] Daily notifications firing at correct time

**Quality Metrics:**
- [ ] No crashes during review sessions
- [ ] Charts load in < 2 seconds
- [ ] Speech recognition latency < 3 seconds
- [ ] UI animations smooth (60 FPS)
- [ ] Memory usage < 200 MB during analytics
- [ ] Battery drain < 5% per review session

### Risk Mitigation

**Risk 1**: Speech recognition accuracy low trên devices khác nhau
- Mitigation: Test trên 3+ devices (Samsung, Xiaomi, Google Pixel)
- Fallback: Allow manual skip nếu recognition fails 3 times

**Risk 2**: Charts lag với large datasets (500+ words)
- Mitigation: Implement pagination (show last 30 days only)
- Optimization: Cache chart data in ViewModel

**Risk 3**: Notifications không reliable trên Android 12+
- Mitigation: Use WorkManager (battery-optimized)
- Test: Verify on Android 12, 13, 14

**Risk 4**: Time constraint (2 tuần tight)
- Mitigation: Wave 4-5 optional if needed
- Critical path: Waves 1-3 (core learning features)
