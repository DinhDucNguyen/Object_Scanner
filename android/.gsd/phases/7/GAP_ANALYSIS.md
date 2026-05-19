# Phase 7.2 - Gap Analysis & Roadmap Reset

> Ngay tao: 2026-05-19  
> Scope: chi doi chieu tai lieu voi code hien tai va reset GSD docs. Khong sua app/backend runtime code.  
> Dau vao chinh: `.gsd/phases/7/CURRENT_STATE.md`, `.gsd/ROADMAP.md`, `.gsd/SPEC.md`, `.gsd/TODO.md`, `.gsd/ARCHITECTURE.md`, `.gsd/STACK.md`, `PHASE6_COMPLETE.md`.

## Tom Tat Dieu Hanh

Project hien tai da vuot xa roadmap Phase 1-5 va co nhieu phan Phase 6 trong code: review modes, pronunciation, analytics, collections, streaks, notifications, backend admin/moderation. Tuy nhien, tai lieu GSD cu khong con la source of truth an toan:

- `PHASE6_COMPLETE.md` ghi Phase 6 da complete, nhung mot so ten file/abstraction trong claim khong ton tai trong code hien tai.
- `.gsd/ROADMAP.md`, `.gsd/TODO.md`, `.gsd/SPEC.md` van co nhieu noi dung Phase 6 dang o trang thai planning.
- Code hien tai co admin panel/backend moderation, trong khi SPEC cu ghi "Admin dashboard web" la non-goal.
- Chua co pass build/test/runtime trong Phase 7.1, nen cac feature co file hien dien chi nen danh dau la "da implement nhung chua verify".

Ket luan: reset roadmap theo huong **Phase 7 la rebaseline**, Plan 7.3 la buoc verify tiep theo, sau do moi mo Phase 8 de sua loi/lam demo-ready dua tren bang chung.

## Matrix Doi Chieu Phase 6

| Claim trong Phase 6 | Bang chung code hien tai | Trang thai | Gap / next action |
|---|---|---|---|
| Wave 1 - Flashcard mode bang `FlashcardFragment.kt` va `FlashcardViewModel.kt` | Khong thay 2 file nay. Code hien tai dung `ui/review/ReviewFragment.kt` va `ReviewViewModel.kt` cho card review co ban. | Da cu / bi thay the mot phan | Cap nhat docs goi day la `ReviewFragment` hien tai. Plan 7.3 can verify flow flip/reveal va submit review. |
| Wave 1 - Quiz mode | Co `QuizFragment.kt` va `QuizViewModel.kt`. | Da implement nhung chua verify | Plan 7.3 chay build va test manual flow multiple choice. |
| Wave 1 - Typing test | Co `TypingTestFragment.kt` va `TypingTestViewModel.kt`. | Da implement nhung chua verify | Verify typo tolerance, submit review, empty/error states. |
| Wave 1 - Listening test | Co `ListeningTestFragment.kt` va `ListeningTestViewModel.kt`; co TTS/audio support qua code review layer. | Da implement nhung chua verify | Verify audio playback/TTS va answer submission tren thiet bi/emulator. |
| Wave 1 - Image matching | Co `ImageMatchingFragment.kt`, `ImageMatchingViewModel.kt`, `MatchingCardAdapter.kt`. | Da implement nhung chua verify | Verify layout, timer, matching logic, API submit. |
| Wave 1 - Shared helpers `ReviewSessionManager.kt`, `AudioPlayer.kt`, `FuzzyMatcher.kt`, `ReviewModeSelector.kt` | Khong tim thay cac file nay. Code hien tai co `AudioPlayerManager` va review fragments rieng. | Da cu / khong khop code | Khong coi cac helper nay la bat buoc neu code hien tai da thay bang path khac. Neu thieu UX chon mode, ghi vao Phase 8 sau verify. |
| Wave 2 - Pronunciation Practice | Co `PronunciationFragment.kt`, `PronunciationViewModel.kt`; manifest co `RECORD_AUDIO`. | Da implement nhung chua verify | Verify permission, SpeechRecognizer, TTS, scoring, retry/error states. |
| Wave 3 - Visual Analytics | Co `AnalyticsFragment.kt`, `AnalyticsViewModel.kt`; dependency `MPAndroidChart:v3.1.0`. | Da implement nhung chua verify | Verify chart render, empty data, API mapping, navigation reachability. |
| Wave 4 - Collections & Insights | Co `CollectionListFragment`, `CollectionDetailFragment`, `CollectionInsightsFragment`, `CollectionRepository`, `CollectionApiService`; backend co `collection_router.py`. | Da implement nhung chua verify | Verify CRUD, practice per collection, insights accuracy, backend compatibility. |
| Wave 4 - `RetrofitInstance.kt` moi | Khong thay `RetrofitInstance.kt`; code dung `RetrofitClient`. | Da cu / bi thay the | Cap nhat docs theo `RetrofitClient`, khong yeu cau tao lai `RetrofitInstance`. |
| Wave 5 - Streaks | Co `StreakDataStore.kt`, `StreakFragment.kt`, `StreakViewModel.kt`, API `getStreak/recordStreak/syncStreak`, backend `streak_router.py`. | Da implement nhung chua verify | Can xac dinh source of truth local/server va verify sync edge cases. |
| Wave 5 - Notifications | Co `NotificationPreferences.kt`, `NotificationSettingsFragment.kt`, `DailyReminderWorker.kt`, `StreakResetWorker.kt`, `AppNotificationHelper.kt`; manifest co `POST_NOTIFICATIONS`, `VIBRATE`, `RECEIVE_BOOT_COMPLETED`. | Da implement nhung chua verify | Verify Android 13 permission, scheduling, skip-if-reviewed logic, boot behavior. |
| Phase 6 dependencies | Gradle co MPAndroidChart, DataStore, WorkManager, Konfetti, UCrop, TensorFlow Lite, ML Kit. | Da implement nhung chua verify | Plan 7.3 build se xac nhan version/compatibility. |
| Phase 6 testing checklist | `PHASE6_COMPLETE.md` van de nhieu checklist chua tick; Phase 7.1 chua chay build/test. | Chua verify | Plan 7.3 phai tao `VERIFICATION.md` va khong danh dau release-ready neu build/test fail. |
| Phase 6 statistics / file counts | Mot so file claim khong ton tai, nen line counts/statistics khong con dang tin. | Da cu | Dung `CURRENT_STATE.md` va ket qua verify moi lam source of truth. |

## Gaps Va Rui Ro Uu Tien

| Severity | Gap / risk | Bang chung | Impact | Next action |
|---|---|---|---|---|
| High | Phase 6 docs noi "complete" nhung khong khop code hien tai | `PHASE6_COMPLETE.md` claim `FlashcardFragment`, `FlashcardViewModel`, `ReviewModeSelector`, `ReviewSessionManager`, `FuzzyMatcher`, `AudioPlayer.kt`, `RetrofitInstance.kt`; `CURRENT_STATE.md` khong tim thay cac file nay | Planning sai, de yeu cau Codex/Gemini sua theo abstraction khong ton tai | Reset docs: Phase 6 la historical/stale. Plan 7.3 verify code that su, Phase 8 chi sua theo bang chung moi. |
| High | Model mac dinh `best_float32.tflite` la asset quan trong nhung dang co dau hieu chua duoc track trong audit | `ObjectDetectorHelper.kt` default `best_float32.tflite`; asset co trong `app/src/main/assets` dung luong 9,366,628 bytes; `CURRENT_STATE.md` ghi file nay untracked | Clean checkout/build co the thieu model, scan runtime co the fail hoac fallback sai | Quyet dinh track asset, dung Git LFS, hoac co quy trinh tai model ro rang truoc release. |
| High | Label map custom model chi co `ruler` | `ObjectDetectorHelper.SCHOOL_SUPPLIES_LABELS = listOf("ruler")` | Neu model detect nhieu class, UI/API se map sai object; neu model chi co ruler thi scope can duoc ghi ro | Doi chieu metadata/training classes cua model, cap nhat labels hoac ghi model scope. |
| High | Build/test/runtime chua duoc verify trong Phase 7 | Plan 7.1 chi inventory, khong build/test; Plan 7.2 chi docs | Khong biet app/backend co compile, API contract co match, nav flow co crash khong | Plan 7.3 chay Android build/test, backend import/smoke, va ghi `VERIFICATION.md`. |
| Medium | Navigation reachability cua cac mode Phase 6 can confirm | Nav graph co nhieu destination, bottom nav chi co 5 tab; `CURRENT_STATE.md` ghi can verify UI paths | Feature co file nhung user co the khong vao duoc tu UI chinh | Plan 7.3 test manual navigation paths: review modes, analytics, collection, streak, notification settings. |
| Medium | Streak co logic ca local va server | Android co `StreakDataStore`, backend co `streak_router.py`/`StreakService`, Android co `syncStreak` | Race/overwrite co the lam sai streak, analytics va notification | Xac dinh source of truth sau Plan 7.3; them tests/sync rules neu can. |
| Medium | Admin panel ton tai du SPEC cu ghi non-goal | `backend/main.py` mount `/admin-panel`; `backend/static/admin`; `admin_router.py`; SPEC ghi "Admin dashboard web" non-goal | Docs khong khop san pham, co the anh huong scope bao ve/bao cao | Cap nhat SPEC trong phase rieng hoac ghi admin la internal tooling hien co. |
| Medium | Backend Gemini package dang co TODO migrate | `backend/requirements.txt` dung `google-generativeai>=0.8.0`; `gemini_service.py` co TODO deprecated | Dependency co the gay warning/compat issue ve sau | Dua vao backlog Phase 8 neu Plan 7.3 backend smoke pass nhung package can migrate. |
| Medium | Deadline va milestone cu khong con y nghia van hanh | SPEC/ROADMAP cu co deadline 10/04/2026 va timeline Phase 6 thang 03/2026 | Nguoi doc moi de danh gia sai tien do hien tai | ROADMAP reset ngay 2026-05-19, de deadline cu o khu vuc historical. |
| Low | Mot so docs/todo cu con o dang planning checklist | `.gsd/TODO.md` van noi Phase 6 ready for implementation | Lam planning lech | Gan nhan stale va point ve Phase 7/Plan 7.3. |

## Roadmap Reset Direction

### Source of truth moi

1. `.gsd/phases/7/CURRENT_STATE.md` la ban do hien trang code sau inventory.
2. `.gsd/phases/7/GAP_ANALYSIS.md` la ban doi chieu docs/code va risk ranking.
3. `.gsd/phases/7/VERIFICATION.md` se la source of truth ve build/test/runtime sau Plan 7.3.

### Trang thai phase

| Phase | Trang thai sau reset | Ghi chu |
|---|---|---|
| Phase 1-5 | Historical complete | Khong dung de suy luan code hien tai neu trai voi Phase 7 docs. |
| Phase 6 | Historical / stale | Code co nhieu feature Phase 6, nhung claim "complete" chua duoc verify va co file names sai. |
| Phase 7 | Current rebaseline | Plan 7.1 complete, Plan 7.2 complete sau file nay, Plan 7.3 la next. |
| Phase 8 | Proposed, chua start | "Stabilization & Demo Readiness" sau khi co `VERIFICATION.md`. |

### Priority cho buoc tiep theo

1. Execute Plan 7.3: build/test/smoke va tao `VERIFICATION.md`.
2. Sau Plan 7.3, neu co blocker: mo Phase 8 de fix build/runtime/navigation/model asset labels.
3. Neu Plan 7.3 pass: mo Phase 8 demo hardening, docs cleanup, va chuan bi release/demo checklist.

## De Xuat Phase 8

Ten de xuat: **Phase 8 - On Dinh & San Sang Demo**

Muc tieu:

- Fix cac blocker tu `VERIFICATION.md`.
- Chot asset/model strategy cho `best_float32.tflite` va labels.
- Verify cac flow hoc tap chinh tu UI: scan, save, review modes, analytics, collections, streak/notifications.
- Dong bo docs GSD voi code that va loai bo checklist/stats cu gay nhieu.

Phase 8 **khong nen start** truoc khi Plan 7.3 tao xong `VERIFICATION.md`.
