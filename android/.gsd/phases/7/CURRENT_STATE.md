# Phase 7.1 - Current State

> **Ngày tạo**: 2026-05-19  
> **Phạm vi**: Kiểm kê hiện trạng Android app và FastAPI backend từ code hiện tại.  
> **Giới hạn**: Chỉ audit và tạo tài liệu; không sửa code app/backend; chưa chạy build/test/runtime.

## Tóm Tắt Nhanh

Project hiện tại là app Android native bằng Kotlin kết nối FastAPI backend. App đã vượt xa roadmap Phase 1-5 ban đầu và trong code có nhiều phần thuộc Phase 6: review modes, pronunciation, analytics, collections, streaks, notifications, admin/backend moderation.

Điểm quan trọng: tài liệu đang bị lệch trạng thái. `PHASE6_COMPLETE.md` nói Phase 6 đã complete, nhưng một số claim trong file đó không khớp 100% với code hiện tại. Ví dụ: không tìm thấy `FlashcardFragment`, `FlashcardViewModel`, `ReviewModeSelector`, `ReviewSessionManager`, `FuzzyMatcher`, `AudioPlayer.kt`, `RetrofitInstance.kt`; thay vào đó code hiện tại dùng `ReviewFragment`, `ReviewViewModel`, `AudioPlayerManager`, và `RetrofitClient`.

## Nguồn Đã Kiểm Tra

### GSD / Docs

- `.gsd/STATE.md`
- `.gsd/SPEC.md`
- `.gsd/ROADMAP.md`
- `PHASE6_COMPLETE.md`
- `.gsd/phases/7/1-PLAN.md`

### Android

- `app/src/main/AndroidManifest.xml`
- `app/build.gradle.kts`
- `app/src/main/res/navigation/nav_graph.xml`
- `app/src/main/res/menu/bottom_nav_menu.xml`
- `app/src/main/java/com/duc/objectlanguage/ObjectLanguageApp.kt`
- `app/src/main/java/com/duc/objectlanguage/ui/MainActivity.kt`
- `app/src/main/java/com/duc/objectlanguage/ui/**`
- `app/src/main/java/com/duc/objectlanguage/data/**`
- `app/src/main/java/com/duc/objectlanguage/utils/**`
- `app/src/main/java/com/duc/objectlanguage/workers/**`
- `app/src/main/assets/**`

### Backend

- `backend/main.py`
- `backend/requirements.txt`
- `backend/app/routers/**`
- `backend/app/services/**`
- `backend/app/repositories/**`
- `backend/app/models/**`
- `backend/app/schemas/**`
- `backend/alembic/versions/**`

## Snapshot Theo Số Lượng

| Khu vực | Số lượng quan sát được | Ghi chú |
|---|---:|---|
| Android UI Kotlin files | 54 | Trong `app/src/main/java/com/duc/objectlanguage/ui` |
| Android data Kotlin files | 12 | API, local storage, models, repositories |
| Android layout files | 44 | Trong `app/src/main/res/layout` |
| Android TFLite assets | 2 | `best_float32.tflite`, `yolov10n_int8.tflite` |
| Backend routers | 10 | Bao gồm `__init__.py` |
| Backend services | 13 | Auth, scan, learning, dictionary, admin, streak... |
| Backend repositories | 7 | User, object, translation, learning, collection, history, language |
| Backend models | 21 | SQLAlchemy models |
| Alembic migrations | 11 | `0001` đến `0011` |

## Android App Overview

### App Shell

| Thành phần | File chính | Hiện trạng |
|---|---|---|
| Application | `ObjectLanguageApp.kt` | Khởi tạo `TokenManager`, `GuestSessionManager`, `RetrofitClient`, `AppRepository`; schedule `StreakResetWorker` và `DailyReminderWorker`. |
| Main Activity | `ui/MainActivity.kt` | Setup Navigation + bottom nav; guest guard chỉ cho guest dùng Scan; user đã login được chuyển sang Dashboard. |
| Navigation graph | `res/navigation/nav_graph.xml` | `startDestination="@id/scanFragment"`; nhiều feature Phase 6 đã có destination. |
| Bottom navigation | `res/menu/bottom_nav_menu.xml` | 5 tab chính: Dashboard, Scan, Dictionary, Review, Profile. |
| Permissions | `AndroidManifest.xml` | Camera, Internet, Record Audio, Post Notifications, Vibrate, Receive Boot Completed. |

### Navigation Hiện Tại

Start mặc định là `scanFragment`. Nếu đã login và activity tạo mới, `MainActivity` đổi start destination sang `dashboardFragment`.

| Nhóm | Fragment destinations |
|---|---|
| Auth | `loginFragment`, `registerFragment`, `forgotPasswordFragment`, `verifyOtpFragment`, `resetPasswordFragment` |
| Main tabs | `dashboardFragment`, `scanFragment`, `dictionaryFragment`, `reviewFragment`, `profileFragment` |
| Explore | `exploreFragment`, `categoryDetailFragment` |
| History | `historyFragment`, `historyDetailFragment` |
| Review modes | `quizFragment`, `typingTestFragment`, `listeningTestFragment`, `imageMatchingFragment`, `pronunciationFragment` |
| Analytics / collection / streak | `analyticsFragment`, `collectionListFragment`, `collectionDetailFragment`, `collectionInsightsFragment`, `streakFragment`, `notificationSettingsFragment` |

### Android Feature Map

| Feature | UI files | ViewModel / logic | Data dependency | Ghi chú |
|---|---|---|---|---|
| Auth | `ui/auth/LoginFragment.kt`, `RegisterFragment.kt`, `ForgotPasswordFragment.kt`, `VerifyOtpFragment.kt`, `ResetPasswordFragment.kt` | Fragment trực tiếp gọi repository | `AppRepository.login/register/forgotPassword/verifyOtp/resetPassword/changePassword` | Login lưu token qua `TokenManager`. |
| Dashboard | `ui/dashboard/DashboardFragment.kt` | `DashboardViewModel.kt` | `getStats`, `getAllObjects`, `getStreak`, `StreakDataStore` | Có daily suggestions và streak summary. |
| Scan / object detection | `ui/scan/ScanFragment.kt` | `ScanViewModel.kt`, `ObjectDetectorHelper.kt` | CameraX, TFLite, ML Kit, `scanByCode`, `scanByImage`, `saveLichSuQue`, `getExamples`, `getTtsAudio` | Có guest scan limit, YOLO -> DB lookup -> ML Kit -> Gemini fallback. |
| Explore | `ui/explore/ExploreFragment.kt`, `CategoryDetailFragment.kt` | `ExploreViewModel.kt`, `CategoryDetailViewModel.kt` | `getCategories`, `getObjectsByCategory` | Dashboard có thể điều hướng sang explore/category. |
| Dictionary / translate | `ui/dictionary/DictionaryFragment.kt`, `DictionaryAdapter.kt` | `DictionaryViewModel.kt` | `translate`, `lookupWord`, `addToLearning`, `getTtsAudio` | Có debounce/job cancel và audio playback qua `AudioPlayerManager`. |
| Review flashcard cơ bản | `ui/review/ReviewFragment.kt` | `ReviewViewModel.kt` | `getDueReviews`, `getCollectionReviewCards`, `submitReview`, TTS/audio URL | Đây là review card flip/reveal hiện tại; không thấy `FlashcardFragment`. |
| Quiz | `ui/review/QuizFragment.kt` | `QuizViewModel.kt` | `getDueReviews`, `submitReview` | Multiple choice có timer 10s trong Fragment. |
| Typing test | `ui/review/TypingTestFragment.kt` | `TypingTestViewModel.kt` | `getDueReviews`, `submitReview` | Có kiểm tra câu trả lời text; chi tiết scoring cần Plan 7.2 rà sâu. |
| Listening test | `ui/review/ListeningTestFragment.kt` | `ListeningTestViewModel.kt` | `getDueReviews`, `submitReview`, Android `TextToSpeech` | TTS client-side cho listening mode. |
| Image matching | `ui/review/ImageMatchingFragment.kt`, `MatchingCardAdapter.kt` | `ImageMatchingViewModel.kt` | `getDueReviews`, `submitReview` | Có matching cards và timer. |
| Pronunciation | `ui/review/PronunciationFragment.kt` | `PronunciationViewModel.kt` | `getDueReviews`, `submitReview`, `SpeechRecognizer`, `TextToSpeech` | Cần `RECORD_AUDIO`; score do client tính. |
| Analytics | `ui/analytics/AnalyticsFragment.kt` | `AnalyticsViewModel.kt` | `getStats`, `getAnalytics`, `getStreak` | Dùng MPAndroidChart theo dependency. |
| Collections | `ui/collection/CollectionListFragment.kt`, `CollectionDetailFragment.kt`, `CollectionInsightsFragment.kt`, adapters, bottom sheet | `CollectionViewModel.kt`, `CollectionInsightsViewModel.kt`, `SaveToCollectionBottomSheet.kt` | `CollectionRepository`, `CollectionApiService` | CRUD collection, item management, collection review, insights. |
| History | `ui/history/HistoryFragment.kt`, `HistoryDetailFragment.kt`, `HistoryAdapter.kt` | `HistoryViewModel.kt`, `HistoryDetailViewModel.kt` | `getHistory`, `getHistoryDetail`, `deleteHistory`, `getTranslationsByCode`, audio | Có filter/search/pagination theo code. |
| Profile / settings | `ui/profile/ProfileFragment.kt` | `ProfileViewModel.kt` | `getProfile`, `updateProfile`, `uploadAvatar`, `getUserSettings`, `updateSettings`, `changePassword` | Profile cũng là hub vào History, Analytics, Streak, Collections, Notification Settings. |
| Streak | `ui/streak/StreakFragment.kt` | `StreakViewModel.kt` | `StreakDataStore`, `getStreak`, `recordStreak`, `syncStreak`, `AppNotificationHelper` | Có local/server sync và milestone notification. |
| Notification settings | `ui/settings/NotificationSettingsFragment.kt` | Fragment + `NotificationPreferences` | DataStore + `DailyReminderWorker` + `AppNotificationHelper` | Android 13+ permission handled in fragment. |
| Guest guard | `ui/common/GuestUpsellDialog.kt` | MainActivity + ScanViewModel | `GuestSessionManager`, `TokenManager` | Guest chỉ được dùng scan; các tab khác hiện upsell login/register. |

## Android Data / ML / Background Systems

### API & Repository

| File | Vai trò | Ghi chú |
|---|---|---|
| `data/api/RetrofitClient.kt` | Singleton Retrofit/OkHttp client | Gắn bearer token, HTTP logging debug, auto refresh token bằng authenticator. |
| `data/api/ApiService.kt` | Main API surface | Auth, scan, TTS, examples, review, stats, explore, history, dictionary, profile/settings, analytics, scan history, streak. |
| `data/api/CollectionApiService.kt` | Collection API surface | Collection CRUD, add/remove item, review cards theo collection, insights. |
| `data/repository/AppRepository.kt` | Main repository | Wrap `Response<T>` thành `Result<T>`, lưu token, normalize audio URL, upload avatar/image. |
| `data/repository/CollectionRepository.kt` | Collection repository | Wrap collection endpoints thành `Result`. |

### Local State

| File | Storage | Vai trò |
|---|---|---|
| `data/local/TokenManager.kt` | `EncryptedSharedPreferences` | Access/refresh token, username, user id, login state. |
| `data/local/GuestSessionManager.kt` | `SharedPreferences` | Guest scan quota 5 lượt/ngày. |
| `data/local/StreakDataStore.kt` | DataStore Preferences | Current/longest streak, total reviews, reviews today, last milestone. |
| `data/local/NotificationPreferences.kt` | DataStore Preferences | Daily reminder, time, streak alert, milestone celebration. |
| `data/local/ApiConfig.kt` | BuildConfig from `local.properties` | Base URL: `SERVER_SCHEME://SERVER_IP:SERVER_PORT/`; default IP trong Gradle là `192.168.1.100`. |

### ML / Camera / Audio

| Thành phần | File | Hiện trạng |
|---|---|---|
| Custom TFLite | `app/src/main/assets/best_float32.tflite` | 9,366,628 bytes; hiện untracked trong git status. |
| COCO TFLite | `app/src/main/assets/yolov10n_int8.tflite` | 3,126,026 bytes; tracked/available asset. |
| Detector helper | `ui/scan/ObjectDetectorHelper.kt` | TFLite Interpreter, input 640, output `[1, 300, 6]`; labels hiện custom chỉ có `ruler`, COCO labels đầy đủ. |
| ML Kit fallback | `ui/scan/ScanViewModel.kt` | Dùng `ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)` sau YOLO nếu cần. |
| Gemini fallback | `ScanViewModel.scanWithDetection/runGemini` | Gửi ảnh qua backend `scanByImage` khi DB lookup/ML Kit không đủ. |
| Audio playback | `utils/AudioPlayerManager.kt` | Dùng bởi Scan, Review, Dictionary, History Detail. |
| Pronunciation/listening | `PronunciationFragment.kt`, `ListeningTestFragment.kt` | Dùng Android `TextToSpeech`; pronunciation thêm `SpeechRecognizer`. |

### Background Workers / Notifications

| File | Vai trò |
|---|---|
| `workers/DailyReminderWorker.kt` | One-time WorkManager job tự reschedule hằng ngày theo preference; không notify nếu user đã review hôm nay hoặc không có due words. |
| `workers/StreakResetWorker.kt` | Periodic WorkManager mỗi 6 giờ, đọc `StreakDataStore.currentStreak` để trigger validation. |
| `utils/AppNotificationHelper.kt` | Notification channel, daily reminder, milestone notification, permission guard. |

## Backend Overview

### App Entry

`backend/main.py` tạo FastAPI app, cấu hình CORS, rate limit bằng SlowAPI, mount `/uploads` và `/admin-panel`, include routers:

- `auth_router`
- `scan_router`
- `review_router`
- `collection_router`
- `history_router`
- `data_router`
- `dictionary_router`
- `admin_router` với prefix `/api`
- `streak_router`

### Backend Router Map

| Router | Prefix | Auth | Service chính | Android client coverage |
|---|---|---|---|---|
| `auth_router.py` | `/api/auth` | Mixed; profile/settings/change password cần JWT | `UserService` | Covered bởi Auth/Profile flows. |
| `scan_router.py` | `/api` | Optional user cho scan; TTS/examples public-ish | `ScanService`, `GeminiService`, `TTSService` | Covered bởi Scan, TTS/audio, examples, history save flow. |
| `review_router.py` | `/api` | JWT required | `LearningService` | Covered bởi review modes, analytics endpoint, add learning. |
| `collection_router.py` | `/api/collections` | JWT required | `CollectionService`, `LearningService` | Covered bởi collection UI and collection review. |
| `history_router.py` | `/api` | JWT required | `HistoryFeedbackService` | Covered bởi history list/detail/delete and scan save. |
| `data_router.py` | `/api` | Categories/objects public; stats JWT | `DataService` | Covered by dashboard/explore/stats. |
| `dictionary_router.py` | `/api/dictionary` | Optional user | `DictionaryService` | Covered by Dictionary screen. |
| `streak_router.py` | `/api/streak` | JWT required | `StreakService` | Covered by dashboard/streak/analytics. |
| `admin_router.py` | `/api/admin` | Admin role required | `AdminService`, `object_media_service` | Not used by Android app; used by static admin panel. |

### Backend Service / Repository Map

| Domain | Services | Repositories | Models liên quan |
|---|---|---|---|
| User/auth/profile/settings | `UserService`, `EmailService` | `UserRepository` | `User`, `Profile`, `UserSettings`, `VaiTro`, `TrangThaiNguoiDung`, `PasswordResetOTP` |
| Scan/object vocab | `ScanService`, `GeminiService`, `TTSService`, `DataService` | `ObjectRepository`, `TranslationRepository`, `LanguageRepository` | `Object`, `ObjectAlias`, `Translation`, `ViDu`, `Language`, `Category`, `ObjectMedia`, `ScanHistory`, `AIPrediction` |
| Learning/review/analytics | `LearningService`, `StreakService` | `LearningProgressRepository`, `TranslationRepository` | `LearningProgress`, `ReviewLog`, `Translation`, `User` |
| Collections | `CollectionService` | `CollectionRepository` | `UserCollection`, `CollectionItem`, `Translation` |
| History/feedback | `HistoryFeedbackService` | `HistoryRepository` | `ScanHistory`, `AIPrediction`, `Object`, `Translation` |
| Admin moderation | `AdminService`, `object_media_service` | Direct SQLAlchemy queries inside service/router | Object/media/user/category/translation/prediction models |
| Dictionary | `DictionaryService` | Direct SQLAlchemy queries + Gemini | `DictionaryLookup`, `Object`, `Translation`, `Language`, `AIPrediction` |

### Backend Data Model Surface

Các model chính theo bảng:

- User/auth: `NguoiDung`, `VaiTro`, `TrangThaiNguoiDung`, `HoSo`, `CaiDatNguoiDung`, `OTPDatLaiMatKhau`
- Vocabulary/object: `DanhMuc`, `DoiTuong`, `BiDanhDoiTuong`, `AnhDoiTuong`, `NgonNgu`, `BanDich`, `ViDu`
- Learning: `TienDoHoc`, `LichSuOnTap`
- Collections: `BoSuuTap`, `ChiTietBoSuuTap`
- Scan/moderation: `LichSuQuet`, `DuDoanAI`
- Dictionary logging/cache: `TraTuDien`

### Backend Dependencies

`backend/requirements.txt` hiện có FastAPI, Uvicorn, SQLAlchemy, Pydantic settings, Alembic, PyMySQL, python-jose, bcrypt, Gemini SDK cũ `google-generativeai`, gTTS, Pillow, Cloudinary, SlowAPI, python-multipart.

Ghi chú: requirement có comment `google-generativeai` deprecated, "Will migrate to google-genai package".

## Android - Backend Endpoint Coverage

| Android API method | Backend endpoint | Status quan sát |
|---|---|---|
| `login/register/refresh` | `/api/auth/login`, `/register`, `/refresh` | Có match. |
| `forgotPassword/verifyOtp/resetPassword/changePassword` | `/api/auth/forgot-password`, `/verify-otp`, `/reset-password`, `/change-password` | Có match. |
| `getProfile/updateProfile/uploadAvatar` | `/api/auth/profile`, `/profile/avatar` | Có match. |
| `getSettings/updateSettings` | `/api/auth/settings` | Có match. |
| `scanByCode` | `/api/scan` | Có match; backend optional auth. |
| `scanByImage` | `/api/scan/image` | Có match; backend optional auth; image compression server-side. |
| `getTts/getAudioByUrl` | `/api/tts/{word}` hoặc absolute URL | Có match cho TTS endpoint. |
| `getExamples` | `/api/objects/{code}/examples` | Có match. |
| `addToLearning` | `/api/learning/add?translation_id=` | Có match. |
| `getDueReviews/submitReview` | `/api/review`, `/api/review/{progress_id}` | Có match. |
| `getAnalytics/getStats` | `/api/analytics`, `/api/stats` | Có match. |
| `getCategories/getObjectsByCategory` | `/api/categories`, `/api/objects` | Có match. |
| `getHistory/getHistoryDetail/deleteHistory` | `/api/history`, `/api/history/{scan_id}` | Có match. |
| `saveLichSuQue` | `/api/lich-su-quet` | Có match. |
| `CollectionApiService` | `/api/collections...` | Có match. |
| `getStreak/recordStreak/syncStreak` | `/api/streak`, `/record`, `/sync` | Có match. |

## Điểm Chưa Chắc / Cần Plan 7.2 Rà Sâu

1. `PHASE6_COMPLETE.md` không khớp hoàn toàn với code hiện tại:
   - Không thấy `FlashcardFragment.kt` / `FlashcardViewModel.kt`; hiện review card chính là `ReviewFragment.kt` / `ReviewViewModel.kt`.
   - Không thấy `ReviewModeSelector.kt`, `ReviewSessionManager.kt`, `FuzzyMatcher.kt`, `AudioPlayer.kt`, `RetrofitInstance.kt`.
   - Có `Quiz`, `Typing`, `Listening`, `ImageMatching`, `Pronunciation`, `Analytics`, `Collection`, `Streak`, `NotificationSettings` trong code.
2. Navigation có destination cho nhiều màn Phase 6, nhưng bottom nav chỉ expose 5 tab chính. Cần kiểm tra từ UI có đường vào tất cả màn review mode/analytics/streak/settings không.
3. `best_float32.tflite` hiện xuất hiện trong worktree như untracked file; nhưng `ObjectDetectorHelper` dùng nó làm default/custom model. Cần quyết định tracking asset hoặc setup asset delivery.
4. `ObjectDetectorHelper.SCHOOL_SUPPLIES_LABELS` hiện chỉ có `ruler`; nếu custom model có nhiều class thì label map đang thiếu.
5. Android và backend cùng có local/server streak logic. Cần kiểm tra nguồn sự thật cuối cùng là server `LichSuOnTap` hay local DataStore trong từng flow.
6. Backend admin panel tồn tại trong `backend/static/admin`, nhưng GSD SPEC từng ghi "Admin dashboard web" là non-goal. Đây là documentation drift cần Plan 7.2 phân loại.
7. Chưa chạy build/test/runtime trong Plan 7.1. Độ đúng compile/API chỉ là inventory từ code; Plan 7.3 mới verify.

## Worktree Note

Trước khi tạo file này, worktree đã có nhiều thay đổi/untracked ở `android/app/**` và `backend/app/**`. Plan 7.1 không sửa các file đó. File mới được tạo trong scope GSD:

- `.gsd/phases/7/CURRENT_STATE.md`

## Kết Luận Plan 7.1

Hiện trạng code cho thấy project đã có nền tảng sản phẩm khá rộng:

- Core v1.0: auth, scan, translate/learn, collection, SM-2 review, history.
- Advanced learning: nhiều review modes, pronunciation, analytics, streak/notification.
- Backend: API đầy đủ cho mobile app, có thêm admin moderation/data management.

Việc tiếp theo hợp lý là chạy Plan 7.2 để biến inventory này thành `GAP_ANALYSIS.md`: đối chiếu claim Phase 6, xếp hạng rủi ro, và reset roadmap theo code thật.
