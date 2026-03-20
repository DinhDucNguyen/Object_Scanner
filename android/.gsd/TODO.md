# Project TODOs

## Phase 6: Advanced Learning Experience (v1.5)

> **Status**: Planning Complete, Ready for Implementation
> **Timeline**: 2 tuần (11/03 - 25/03/2026)

---

### Wave 1: Multi-Modal Review System (4 ngày)

**Task 1.1: Flashcard Mode**
- [ ] Create FlashcardFragment + ViewModel
- [ ] Implement ViewPager2 với swipe gestures
- [ ] Card flip animation (front/back)
- [ ] Progress indicator (3/10)
- [ ] Submit results to API

**Task 1.2: Multiple Choice Quiz**
- [ ] Create QuizFragment + ViewModel
- [ ] Generate 4 options (1 correct + 3 distractors)
- [ ] Timer implementation (10s per question)
- [ ] Answer highlighting (green/red)
- [ ] Score summary dialog

**Task 1.3: Typing & Listening Tests**
- [ ] TypingTestFragment (show Vietnamese → type English)
- [ ] Spell checking với typo tolerance
- [ ] ListeningTestFragment (play audio → type)
- [ ] Hint system (first letter, length)

**Task 1.4: Image Matching Game**
- [ ] ImageMatchingFragment với grid layout
- [ ] Match detection logic
- [ ] Flip animations
- [ ] Time tracking

---

### Wave 2: Pronunciation Practice (3 ngày)

**Task 2.1: Speech Recognition Setup**
- [ ] Request RECORD_AUDIO permission
- [ ] SpeechRecognizer initialization
- [ ] Error handling for recognition failures

**Task 2.2: Pronunciation UI**
- [ ] PronunciationFragment layout
- [ ] Native audio playback (normal + slow)
- [ ] Recording button với Lottie animation
- [ ] Waveform visualization (optional)

**Task 2.3: Scoring System**
- [ ] Levenshtein distance algorithm
- [ ] Similarity scoring (0-100%)
- [ ] Feedback message generation
- [ ] Best score persistence

---

### Wave 3: Visual Analytics (3 ngày)

**Task 3.1: MPAndroidChart Setup**
- [ ] Add library dependency
- [ ] AnalyticsFragment + ViewModel
- [ ] Chart styling configuration

**Task 3.2: Line Chart**
- [ ] Fetch learning history from API
- [ ] LineChart: words learned over time
- [ ] Gradient fill, markers, tap interaction

**Task 3.3: Pie & Bar Charts**
- [ ] PieChart: mastery distribution
- [ ] BarChart: category breakdown
- [ ] Custom colors, legends

**Task 3.4: Activity Heatmap**
- [ ] Calendar grid (7x5 = 35 days)
- [ ] Color intensity coding
- [ ] Tap to show daily details
- [ ] Scroll for past months

---

### Wave 4: Collections & Insights (2 ngày)

**Task 4.1: Enhanced Collections**
- [ ] Auto-create category collections
- [ ] CollectionDetailFragment + RecyclerView
- [ ] Filter implementation (mastery, difficulty, date)
- [ ] Sort options (alphabetical, progress, due)
- [ ] Bulk actions ("Practice all")

**Task 4.2: Insights Engine**
- [ ] Calculate best learning time
- [ ] Identify weakest category
- [ ] Streak statistics
- [ ] Daily goal tracker widget
- [ ] Recommendation engine

---

### Wave 5: Streaks & Notifications (2 ngày)

**Task 5.1: Streak System**
- [ ] Streak calculation logic
- [ ] Dashboard streak widget với animation
- [ ] Milestone achievements (3/7/30/100 days)
- [ ] Streak history graph

**Task 5.2: Smart Notifications**
- [ ] WorkManager setup
- [ ] DailyReminderWorker implementation
- [ ] Notification channel creation
- [ ] Settings UI (enable/disable, time picker)
- [ ] Smart logic (skip if reviewed today)

---

## Testing & Verification

**Per Wave Testing:**
- [ ] Wave 1: Test all 4 review modes với 10 words each
- [ ] Wave 2: Test pronunciation với 20 words on 3 devices
- [ ] Wave 3: Verify all charts với 100+ data points
- [ ] Wave 4: Test filters/sorts với 50+ words
- [ ] Wave 5: Verify notifications over 3 consecutive days

**Integration Testing:**
- [ ] End-to-end flow: Scan → Learn → Review → Analytics
- [ ] Test with real users (5+ người)
- [ ] Performance testing (memory, battery, network)

**Quality Assurance:**
- [ ] No crashes during extended use (1 hour session)
- [ ] Animations smooth (60 FPS)
- [ ] API error handling graceful
- [ ] Data persistence across app restarts

---

## Success Criteria

**Phase 6 Complete When:**
- [ ] All 5 waves implemented & verified
- [ ] All 18 tasks completed
- [ ] 0 critical bugs
- [ ] Performance benchmarks met
- [ ] User testing positive feedback (4/5 rating)

**Milestone v1.5 Achieved:**
- App chuyển từ MVP → Complete Learning Platform
- Ready for deployment & graduation submission
- Demo-ready for thesis defense
