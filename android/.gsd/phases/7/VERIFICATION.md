# Phase 7.3 - Verification & Release Readiness

> Ngay tao: 2026-05-19  
> Scope: verify build/test/runtime readiness va ghi evidence. Khong sua app/backend runtime code.  
> Ket luan ngan: **build debug pass, backend import/smoke pass, nhung chua release-ready** vi chua co runtime device test, asset model mac dinh dang untracked, va mot so advanced review modes chua thay entry point tu UI chinh.

## Executive Summary

| Khu vuc | Trang thai | Evidence |
|---|---|---|
| Android unit-test task | Pass co dieu kien | `testDebugUnitTest` success khi dung `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`; task `testDebugUnitTest` la `NO-SOURCE`. |
| Android debug build | Pass | `assembleDebug` success; tao `app/build/outputs/apk/debug/app-debug.apk` dung luong 111,061,628 bytes. |
| Android runtime manual | Chua verify | `adb devices` khong co device/emulator attached. |
| APK assets | Pass tren may hien tai | APK co `assets/best_float32.tflite` va `assets/yolov10n_int8.tflite`. |
| Clean checkout asset risk | Fail / blocker release | `git status --short -- app/src/main/assets/best_float32.tflite` tra ve `??`, trong khi model nay la default. |
| Backend import/smoke | Pass | `import main` OK, `route_count=91`, TestClient `/` = 200, `/admin` = 307, `/admin-panel/` = 200. |
| Backend tests | Chua co | Khong tim thay backend test files trong `backend/**`. |
| API route surface | Pass static | Android Retrofit endpoints co router tuong ung cho auth, scan, review, collection, history, data, dictionary, streak. |
| Review mode reachability | Risk | Nav graph co destinations, nhung static search khong thay code navigate toi `quizFragment`, `typingTestFragment`, `listeningTestFragment`, `imageMatchingFragment`, `pronunciationFragment` tu UI chinh. |

## Commands Da Chay

### Android Environment Check

```powershell
where.exe java
```

Ket qua:

- Co `C:\Program Files\Common Files\Oracle\Java\javapath\java.exe`
- Co `C:\Program Files\Java\jdk-21\bin\java.exe`

```powershell
$env:JAVA_HOME
```

Ket qua ban dau:

- `C:\Java`

Command Gradle chay truc tiep bi fail vi `JAVA_HOME=C:\Java` khong ton tai:

```text
ERROR: JAVA_HOME is set to an invalid directory: C:\Java
```

Kiem tra JDK kha dung:

```powershell
& 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe' -version
```

Ket qua:

- OpenJDK `17.0.11`

### Android Unit-Test Task

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --console=plain
```

Ket qua:

- Exit code: `0`
- `BUILD SUCCESSFUL in 3s`
- `:app:compileDebugUnitTestKotlin NO-SOURCE`
- `:app:compileDebugUnitTestJavaWithJavac NO-SOURCE`
- `:app:testDebugUnitTest NO-SOURCE`

Danh gia:

- Compile/config path pass.
- Khong co unit tests thuc su de danh gia behavior.
- Co manifest warnings tu TensorFlow Lite duplicate namespace, nhung khong block build.

### Android Debug Build

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat assembleDebug --console=plain
```

Ket qua:

- Exit code: `0`
- `BUILD SUCCESSFUL in 4s`
- APK: `app/build/outputs/apk/debug/app-debug.apk`
- APK size: `111,061,628` bytes

### APK Asset Check

```powershell
tar -tf android\app\build\outputs\apk\debug\app-debug.apk | Select-String -Pattern '\.tflite$'
```

Ket qua:

- `assets/best_float32.tflite`
- `assets/mlkit_odt_default_classifier/labeler_with_validation.tflite`
- `assets/mlkit_odt_localizer/localizer_with_validation.tflite`
- `assets/yolov10n_int8.tflite`

```powershell
git -C android status --short -- app/src/main/assets/best_float32.tflite app/src/main/assets/yolov10n_int8.tflite
```

Ket qua:

- `?? app/src/main/assets/best_float32.tflite`

Danh gia:

- APK local hien tai co model default.
- Clean checkout/release co nguy co thieu `best_float32.tflite` vi file dang untracked.

### Backend Import / Route Smoke

```powershell
@'
import main
print('APP_IMPORT_OK')
print('route_count=', len(main.app.routes))
'@ | python -
```

Ket qua:

- Exit code: `0`
- `APP_IMPORT_OK`
- `route_count= 91`

Warning:

```text
FutureWarning: All support for the `google.generativeai` package has ended...
```

```powershell
@'
from fastapi.testclient import TestClient
import main
client = TestClient(main.app)
for path in ['/', '/admin', '/admin-panel/']:
    r = client.get(path, follow_redirects=False)
    print(path, r.status_code, r.headers.get('location', '')[:80])
'@ | python -
```

Ket qua:

- `/ 200`
- `/admin 307 /admin-panel/index.html`
- `/admin-panel/ 200`

Danh gia:

- Backend app import va route/static smoke pass.
- Chua verify DB-backed endpoints vi se can DB state/credentials va test data.

### Runtime Device Check

```powershell
adb devices
```

Ket qua:

```text
List of devices attached
```

Danh gia:

- Khong co emulator/device attached.
- Chua the verify runtime UI, camera, permission, navigation click path, TFLite inference, speech recognition, notification scheduling.

## Static Runtime Surface Checks

### Navigation

Bang chung:

- `nav_graph.xml` co destinations: `quizFragment`, `typingTestFragment`, `listeningTestFragment`, `imageMatchingFragment`, `pronunciationFragment`, `analyticsFragment`, `collectionListFragment`, `collectionDetailFragment`, `collectionInsightsFragment`, `streakFragment`, `notificationSettingsFragment`.
- `ProfileFragment.kt` navigate toi `historyFragment`, `analyticsFragment`, `streakFragment`, `collectionListFragment`, `notificationSettingsFragment`.
- `DashboardFragment.kt` navigate toi `streakFragment`.
- `CollectionDetailFragment.kt` navigate toi `reviewFragment` voi args.
- Static search khong thay code navigate toi `quizFragment`, `typingTestFragment`, `listeningTestFragment`, `imageMatchingFragment`, `pronunciationFragment` tu UI chinh.

Danh gia:

- Analytics/collections/streak/settings co entry points tu Profile/Dashboard.
- Advanced review mode destinations compile duoc nhung co risk khong reachable voi user.

### API Contract

Static check cho thay Android Retrofit methods co backend router tuong ung:

- Auth: `/api/auth/login`, `/register`, `/refresh`, `/profile`, `/settings`, password reset/change.
- Scan: `/api/scan`, `/api/scan/image`, `/api/objects/{code}/examples`, `/api/tts/{word}`.
- Review/learning: `/api/learning/add`, `/api/review`, `/api/review/{progress_id}`, `/api/analytics`.
- Collections: `/api/collections`, detail, items, review, insights.
- History: `/api/history`, `/api/history/{scan_id}`, `/api/lich-su-quet`.
- Dictionary: `/api/dictionary/lookup`, `/api/dictionary/translate`.
- Streak: `/api/streak`, `/api/streak/record`, `/api/streak/sync`.

Danh gia:

- API surface match ve path.
- Chua verify schema runtime voi DB/test data.

### Model / ML

Bang chung:

- `ObjectDetectorHelper.kt` default `best_float32.tflite`.
- `ObjectDetectorHelper.CUSTOM_MODEL = "best_float32.tflite"`.
- `ObjectDetectorHelper.COCO_MODEL = "yolov10n_int8.tflite"`.
- `SCHOOL_SUPPLIES_LABELS = listOf("ruler")`.
- APK local co ca hai TFLite assets.

Danh gia:

- Local build co model assets.
- `best_float32.tflite` untracked la release blocker.
- Label map chi co `ruler`; neu custom model train nhieu class thi detection label mapping se sai.

## Blocking Issues

| Priority | Issue | Evidence | Impact | Recommended next action |
|---|---|---|---|---|
| P0 | Runtime UI chua verify duoc | `adb devices` khong co device/emulator | Khong biet app co crash o camera/nav/permission/TFLite/speech/notification khong | Phase 8 hoac buoc verify tiep: chay tren emulator/device that, ghi logcat va flow result. |
| P0 | Default custom model dang untracked | `?? app/src/main/assets/best_float32.tflite`; code default model la `best_float32.tflite` | Clean checkout co the build/run scan thieu asset | Quyet dinh track bang Git/Git LFS hoac add setup step tai model truoc release. |
| P1 | Advanced review modes co the khong reachable | Static search khong thay navigate toi `quizFragment`, `typingTestFragment`, `listeningTestFragment`, `imageMatchingFragment`, `pronunciationFragment` | User khong vao duoc cac mode Phase 6 du file co ton tai | Them/verify mode selector trong Phase 8 sau khi user cho phep sua code. |
| P1 | Custom model label map chi co `ruler` | `SCHOOL_SUPPLIES_LABELS = listOf("ruler")` | Detection label sai neu model co nhieu class | Doi chieu labels voi model training metadata/classes. |

## Non-Blocking Risks

| Risk | Evidence | Note |
|---|---|---|
| Default shell JAVA_HOME sai | `JAVA_HOME=C:\Java`, Gradle fail neu chay truc tiep | Dung JBR 17 tam thi build pass. Nen sua environment may/dev docs, khong phai code app. |
| Backend Gemini SDK deprecated | Import warning tu `google.generativeai` | Backend smoke pass, nhung nen migrate `google-genai` trong phase sau. |
| Backend khong co tests | `rg` khong tim thay backend test files | Can them smoke/unit tests sau khi on dinh. |
| Android test task NO-SOURCE | Gradle bao `testDebugUnitTest NO-SOURCE` | Build pass nhung behavior chua duoc test tu dong. |
| DB-backed backend endpoints chua smoke | Chi test `/`, `/admin`, `/admin-panel/` | Can test DB endpoints voi seed data/real DB neu release. |

## Release Readiness Snapshot

Trang thai hien tai: **build-ready local, chua release-ready**.

Co the tiep tuc demo noi bo neu:

- May dev dung JDK 17/JBR dung.
- File `best_float32.tflite` van co local trong `app/src/main/assets`.
- Backend `.env` va DB local dang dung voi IP `192.168.1.84:8000`.

Chua nen coi la release/user-test ready vi:

- Chua test UI tren device/emulator.
- Chua verify camera, speech recognition, notification permission/scheduling.
- Chua verify DB-backed API schema voi data that.
- Default model asset chua duoc track.
- Advanced review modes co risk khong co entry point tu UI.

## Proposed Next Phase

De xuat mo **Phase 8 - On Dinh & San Sang Demo** voi thu tu uu tien:

1. Fix release blockers: model asset tracking va review mode reachability.
2. Chay runtime manual tren emulator/device: scan, login, save, review, analytics, collections, streak, notification settings.
3. Verify backend DB-backed endpoints voi data that.
4. Them smoke tests toi thieu cho backend va Android ViewModel/API mapping neu kip.
5. Cleanup docs/SPEC theo scope that: admin panel, Phase 6 historical, model labels.

Neu user chua cho sua code, buoc tiep theo nen la:

```text
Theo GSD, tao Phase 8 docs dua tren VERIFICATION.md, chua sua code.
```

Neu user da muon fix:

```text
Theo GSD, execute Phase 8. Bat dau tu P0 blockers trong VERIFICATION.md.
```
