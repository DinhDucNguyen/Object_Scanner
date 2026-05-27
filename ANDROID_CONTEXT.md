# Android Context

## Project Snapshot

- Android module: `android/`
- App id / namespace: `com.duc.objectlanguage`
- App name: `LengoLens`
- Main application class: `ObjectLanguageApp`
- Main activity: `ui.MainActivity`
- Navigation graph: `android/app/src/main/res/navigation/nav_graph.xml`
- UI stack: native Android Kotlin, XML layouts, ViewBinding, Material Components, Android Navigation.
- Backend: FastAPI service configured through `ApiConfig` / `RetrofitClient`.

## Build Configuration

The Android app is configured in `android/app/build.gradle.kts`.

- `compileSdk`: 34
- `targetSdk`: 34
- `minSdk`: 24
- Java/Kotlin target: 17
- ViewBinding: enabled
- TFLite files are not compressed with `androidResources.noCompress.add("tflite")`.

Server values come from `android/local.properties`:

```properties
SERVER_IP=192.168.1.84
SERVER_PORT=8000
SERVER_SCHEME=http
```

Defaults are defined in Gradle if values are missing.

## Important Commands

Run Android commands from `android/`.

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat lintDebug --console=plain
.\gradlew.bat compileDebugSources --console=plain
.\gradlew.bat assembleDebug --console=plain
```

The default machine `JAVA_HOME` may point to an invalid `C:\Java`, so set Android Studio JBR before Gradle commands.

## Main Runtime Areas

- Auth: `ui/auth`
- Home/dashboard: `ui/dashboard`
- Scan: `ui/scan`
- Dictionary: `ui/dictionary`
- Explore/category flashcards: `ui/explore`
- Review: `ui/review`
- Collections: `ui/collection`
- History: `ui/history`
- Profile/settings: `ui/profile`
- Streak/notifications: `ui/streak`, `workers`

## Core App Services

- `ObjectLanguageApp` initializes:
  - `TokenManager`
  - `GuestSessionManager`
  - `RetrofitClient`
  - `AppRepository`
  - streak/reminder workers
  - `applicationScope` for app-level background sync that should survive Activity recreation
- `RetrofitClient` owns API client setup and auth handling.
- `ApiConfig.baseUrl` builds backend URLs from Gradle build config.

## Scan Flow Notes

The scan flow uses:

- CameraX for capture
- custom YOLO/TFLite models through `ObjectDetectorHelper`
- ML Kit fallback
- backend/Gemini fallback through repository scan APIs
- result UI in `fragment_scan.xml`

Keep the fallback chain conservative. Do not remove Gemini fallback or ML Kit fallback unless the product decision changes.

## Review Flow Notes

The current review experience is intentionally flashcard-first with four quality buttons.

Preserve this unless explicitly asked to redesign review modes:

- Forgot
- Hard
- Good
- Easy

Collection-specific review and normal due-review should continue to use the existing SM-2 style interaction.

## Collection Notes

Collections now support:

- rename
- privacy edit after creation
- owner-only add/remove/edit behavior
- community/public read-only detail behavior
- word detail bottom sheet with examples and audio

When touching collection detail, keep `canEdit` behavior intact so community collections do not expose owner actions.

## Profile Notes

Profile currently distinguishes:

- full name as primary centered display name
- `@username` as secondary handle
- small pencil affordance for editable full name
- dark-mode toggle restores scroll position after Activity recreation

Dark-mode server sync uses `ObjectLanguageApp.applicationScope`.

## Resource And Lint Cleanup Status

Recent cleanup made these lint groups clean:

- `HardcodedText`
- `SetTextI18n`
- `InflateParams`
- `ContentDescription`
- `UnusedAttribute`
- `StaticFieldLeak`
- `NotifyDataSetChanged`

Remaining lint warnings are mostly non-blocking categories:

- unused strings/resources, especially legacy/advanced review text
- plural suggestions
- typography/style suggestions
- dependency update suggestions
- target SDK / Android SDK policy updates
- overdraw/layout style warnings
- debug cleartext HTTP config

Do not mass-delete unused strings for old review/pronunciation/listening modes without checking reachability and product intent first.

## Verification Baseline

Latest local verification after cleanup:

```text
Android lintDebug: PASS
Backend py_compile: PASS
Backend import: PASS
```

Backend checks are run from `backend/`.

## Working Rules For Android Changes

- Preserve existing view IDs unless deliberately updating fragment code.
- Prefer XML/resource-only polish when the request is visual.
- Keep backend contract changes paired with Android model/API changes.
- Use shared color tokens and day/night resources instead of hardcoded colors.
- For image/icon-only UI elements, either add a real `contentDescription` or mark decorative icons with `@null`.
- Keep debug HTTP cleartext enabled only for debug/local backend usage; release uses HTTPS/cleartext disabled.
- Run `lintDebug` or at least `compileDebugSources` after Android edits.
