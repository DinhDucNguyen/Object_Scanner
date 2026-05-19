# Technology Stack

> Updated by Phase 8.1 P0 blocker review on 2026-05-19.  
> Versions below are observed from current project files and verification commands.

## Android Runtime

| Technology | Observed version / source | Purpose |
|---|---|---|
| Android SDK | App Gradle config | Native Android runtime. |
| Kotlin | App Gradle config | Primary Android language. |
| Java / JDK | Android Studio JBR 17.0.11 | Verified Gradle build runtime. Current shell `JAVA_HOME=C:\Java` is invalid and must be corrected before direct Gradle use. |
| AndroidX / Material | App Gradle dependencies | Fragments, lifecycle, navigation, UI components. |
| CameraX | App Gradle dependencies | Camera capture and preview. |
| Retrofit / OkHttp / Gson | App Gradle dependencies | Backend API client. |
| Coroutines | App Gradle dependencies | Async work in ViewModels/repositories. |

## Android Feature Libraries

| Package | Observed version | Purpose |
|---|---|---|
| `org.tensorflow:tensorflow-lite-task-vision` | `0.4.4` | TFLite object detection helper. |
| `org.tensorflow:tensorflow-lite-support` | `0.4.4` | TFLite support utilities. |
| `com.google.mlkit:object-detection` | `17.0.1` | On-device object detection support. |
| `com.github.PhilJay:MPAndroidChart` | `v3.1.0` | Analytics charts. |
| `androidx.datastore:datastore-preferences` | `1.0.0` | Streak and notification preferences. |
| `androidx.work:work-runtime-ktx` | `2.9.0` | Daily reminder and streak workers. |
| `nl.dionsegijn:konfetti-xml` | `2.0.4` | Celebration effects. |
| `com.github.yalantis:ucrop` | `2.2.8` | Image crop flow. |

## Android Permissions Observed

- `INTERNET`
- `ACCESS_NETWORK_STATE`
- `CAMERA`
- `RECORD_AUDIO`
- `POST_NOTIFICATIONS`
- `VIBRATE`
- `RECEIVE_BOOT_COMPLETED`

## Android Assets

| Asset | Observed size | Note |
|---|---:|---|
| `app/src/main/assets/best_float32.tflite` | 9,366,628 bytes | Default custom model in `ObjectDetectorHelper`. Added to git index as Git LFS object after user approval; pending commit/push. |
| `app/src/main/assets/yolov10n_int8.tflite` | 3,126,026 bytes | Alternate/COCO model path. |

## Backend Runtime

| Package | Version constraint | Purpose |
|---|---|---|
| `fastapi` | `>=0.115.0` | Backend web framework. |
| `uvicorn[standard]` | `>=0.34.0` | ASGI server. |
| `sqlalchemy` | `>=2.0.36` | ORM/database access. |
| `pydantic` | `>=2.10.0` | Schemas and validation. |
| `pydantic-settings` | `>=2.0.0` | Settings management. |
| `alembic` | `>=1.13.0` | Database migrations. |
| `pymysql` | `>=1.1.0` | MySQL driver. |
| `python-jose[cryptography]` | `>=3.3.0` | JWT/security. |
| `bcrypt` | `>=4.0.0` | Password hashing. |
| `google-generativeai` | `>=0.8.0` | Gemini integration; marked deprecated/TODO migrate. |
| `gTTS` | `>=2.5.0` | Text-to-speech audio generation. |
| `cloudinary` | `>=1.40.0` | Media storage integration. |
| `slowapi` | `>=0.1.9` | Rate limiting. |

## Verification Status

Android `assembleDebug` passed with:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat assembleDebug --console=plain
```

Backend import/smoke passed in Phase 7.3. Runtime UI remains unverified until an emulator/device appears in `adb devices`.
