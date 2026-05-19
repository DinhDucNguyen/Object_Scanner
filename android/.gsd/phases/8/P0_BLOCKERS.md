# Phase 8.1 - P0 Release Blockers

> Ngay tao: 2026-05-19  
> Scope: xu ly P0 theo docs/evidence, khong sua app/backend source.  
> Ket luan ngan: P0 model asset blocker da duoc dong trong git index bang Git LFS. Chua commit/push, chua sua source app/backend, chua sua environment may.

## Executive Summary

| P0 item | Trang thai sau Plan 8.1 | Decision / next action |
|---|---|---|
| `best_float32.tflite` untracked | Closed in git index / pending commit-push | Added `.gitattributes` LFS rule and staged `best_float32.tflite` as an LFS object. |
| Java/JDK setup | Build pass voi JBR 17, shell default van sai | Recommended: set `JAVA_HOME` user/system toi JDK 17/JBR hoac dung command tam trong docs. Khong sua environment trong repo. |
| Runtime device verification | Van blocked | `adb devices` chua co device/emulator. Can attach device/emulator truoc Plan 8.2. |
| Backend reachability | Ready tu may dev | `192.168.1.84:8000` TCP reachable tu may dev. Can verify tu emulator/device trong Plan 8.2. |

## Evidence

### Closure Action

Commands executed after user approval:

```powershell
cd android
git lfs track "app/src/main/assets/*.tflite"
git add .gitattributes app/src/main/assets/best_float32.tflite
```

Verification:

```powershell
git show :android/app/src/main/assets/best_float32.tflite
```

Index content:

```text
version https://git-lfs.github.com/spec/v1
oid sha256:bce11b3748a16e29d099b121f51144383952b595ec85780159ae5b36e971dee6
size 9366628
```

`git lfs status` reports:

```text
android/.gitattributes (Git: 11d6dd8)
android/app/src/main/assets/best_float32.tflite (LFS: bce11b3)
```

### Model Asset

Commands:

```powershell
git -C android status --short -- app/src/main/assets/best_float32.tflite app/src/main/assets/yolov10n_int8.tflite
```

Original result before closure:

```text
?? app/src/main/assets/best_float32.tflite
```

Commands:

```powershell
Get-Item android\app\src\main\assets\best_float32.tflite, android\app\src\main\assets\yolov10n_int8.tflite | Select-Object Name,Length,LastWriteTime
```

Result:

| Asset | Size |
|---|---:|
| `best_float32.tflite` | 9,366,628 bytes |
| `yolov10n_int8.tflite` | 3,126,026 bytes |

Commands:

```powershell
rg -n "best_float32|yolov10n_int8|CUSTOM_MODEL|COCO_MODEL|SCHOOL_SUPPLIES_LABELS|labelsForModel" app/src/main/java/com/duc/objectlanguage/ui/scan/ObjectDetectorHelper.kt
```

Relevant evidence:

- Default constructor uses `best_float32.tflite`.
- `CUSTOM_MODEL = "best_float32.tflite"`.
- `COCO_MODEL = "yolov10n_int8.tflite"`.
- `SCHOOL_SUPPLIES_LABELS = listOf("ruler")`.

Commands:

```powershell
tar -tf android\app\build\outputs\apk\debug\app-debug.apk | Select-String -Pattern '\.tflite$'
```

Result:

```text
assets/best_float32.tflite
assets/mlkit_odt_default_classifier/labeler_with_validation.tflite
assets/mlkit_odt_localizer/localizer_with_validation.tflite
assets/yolov10n_int8.tflite
```

Interpretation before closure:

- Local APK currently includes `best_float32.tflite` because the file exists locally.
- A clean checkout would not include it until the file is tracked or a documented download/setup step exists.
- `.gitignore` does not ignore `*.tflite`, so the current issue is not ignore rules; the file simply has not been added/tracked.

### Git LFS Availability

Command:

```powershell
git -C android lfs version
```

Result:

```text
git-lfs/3.4.1
```

Interpretation:

- Git LFS is available in this environment.
- Recommended asset strategy can use Git LFS without installing extra local tooling.

### Java / Gradle Environment

Commands:

```powershell
$env:JAVA_HOME
where.exe java
& 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe' -version
& 'C:\Program Files\Java\jdk-21\bin\java.exe' -version
```

Results:

- Current shell `JAVA_HOME`: `C:\Java`
- `where java` includes Oracle javapath and `C:\Program Files\Java\jdk-21\bin\java.exe`
- Android Studio JBR: OpenJDK `17.0.11`
- Installed standalone JDK: Java `21.0.1`

Build verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat assembleDebug --console=plain
```

Result:

```text
BUILD SUCCESSFUL in 1s
```

Interpretation:

- Project builds with Android Studio JBR 17.
- Running Gradle directly without correcting `JAVA_HOME` can fail because `C:\Java` is invalid.
- This is a developer environment/setup issue, not an app source issue.

### Runtime Device / Backend

Command:

```powershell
adb devices
```

Result:

```text
List of devices attached
```

Command:

```powershell
Test-NetConnection -ComputerName 192.168.1.84 -Port 8000
```

Result:

| ComputerName | RemotePort | TcpTestSucceeded |
|---|---:|---|
| `192.168.1.84` | 8000 | True |

`android/local.properties` currently uses:

```properties
SERVER_IP=192.168.1.84
SERVER_PORT=8000
```

Interpretation:

- Backend is reachable from the dev machine at the configured LAN IP/port.
- Runtime app verification is still blocked until a device/emulator is attached.
- If using Android emulator, confirm whether it can reach `192.168.1.84`; if not, use the correct emulator host route such as `10.0.2.2` after confirming backend binding.

## Decisions

### Decision 1 - Model Asset Strategy

Executed decision: **track `best_float32.tflite` with Git LFS**.

Reason:

- It is the default model path in `ObjectDetectorHelper`.
- The file is binary and already 9.37MB.
- Git LFS is installed.
- Leaving it untracked makes clean checkout/release unsafe.

Implementation executed after user approval:

```powershell
cd android
git lfs track "app/src/main/assets/*.tflite"
git add .gitattributes app/src/main/assets/best_float32.tflite
```

Alternative if repo policy avoids LFS:

```powershell
cd android
git add app/src/main/assets/best_float32.tflite
```

Not recommended:

- Keep `best_float32.tflite` untracked without an automated download/setup step.

### Decision 2 - Java / JDK Setup

Recommended decision: **use Android Studio JBR 17 for Gradle builds**.

Suggested command for current shell:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug --console=plain
```

Suggested machine-level fix, to be done by the developer outside repo if desired:

```powershell
[Environment]::SetEnvironmentVariable('JAVA_HOME', 'C:\Program Files\Android\Android Studio\jbr', 'User')
```

No project source change is required.

### Decision 3 - Runtime Device Checklist

Before Plan 8.2:

- [ ] `adb devices` shows a real target.
- [ ] Backend is running and reachable from the device/emulator.
- [ ] `android/local.properties` IP/port matches the runtime network path.
- [ ] Test account exists for login/profile/review flows.
- [ ] Database has at least a few reviewable words/cards.
- [ ] Camera strategy is clear: physical camera, emulator virtual scene, or test image flow.
- [ ] Android permissions can be tested: camera, microphone, notifications.
- [ ] Logcat capture is ready before launch.

Recommended Plan 8.2 first commands:

```powershell
adb devices
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat assembleDebug --console=plain
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell monkey -p com.duc.objectlanguage 1
```

## Remaining P0 Ownership

| Item | Owner decision needed? | Why |
|---|---|---|
| Commit/push staged LFS asset | Yes | `.gitattributes` and `best_float32.tflite` are staged, but no commit was made. |
| Change user/system `JAVA_HOME` | Yes | Environment change outside repo. |
| Runtime UI verification | Yes | Needs attached emulator/device and possibly test account/data. |

## Plan 8.1 Status

- [x] `P0_BLOCKERS.md` exists.
- [x] Recommended decision for `best_float32.tflite` recorded.
- [x] JDK/JAVA_HOME note recorded.
- [x] Runtime device checklist recorded.
- [x] Android build re-verified with JBR 17.
- [x] P0 model blocker closed in git index with Git LFS.
- [ ] P0 model blocker committed/pushed. Pending user request to commit/push.
- [ ] Runtime blocker actually closed. Pending attached device/emulator in Plan 8.2.
