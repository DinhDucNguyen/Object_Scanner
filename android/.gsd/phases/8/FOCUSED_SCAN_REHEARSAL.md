# Focused Scan Rehearsal

> Date: 2026-05-19  
> Scope: verify actual detection -> result -> save path on emulator/device.  
> Guardrail: no app/backend source code was changed.

## Executive Summary

| Area | Result | Evidence |
|---|---|---|
| Build/install | Pass | `assembleDebug` -> `BUILD SUCCESSFUL`; `adb install -r` -> `Success`. |
| Emulator target | Pass | `emulator-5554`, `1344x2992`, density `480`, app focused on `MainActivity`. |
| Backend reachability | Pass | Host `192.168.1.84:8000` reachable before rehearsal. |
| Input path | Pass | Gallery picker selected `/sdcard/Pictures/gsd_bus.jpg`. |
| Model path | Pass | COCO model selected; log: `Model loaded OK: yolov10n_int8.tflite`. |
| Detection | Pass | Log: `YOLO on captured: bus score=0.9169145`. |
| Result UI | Pass | Result card rendered `Bus`, `Phuong tien - COCO YOLOv10n - 91%`, translation, examples, audio, save action. |
| Backend result lookup | Pass | `POST /api/scan` -> `200 OK`, `source=internal_db`, `object_code=bus`, `translations=1`. |
| Save/history/learning | Pass | `POST /api/lich-su-quet` -> `200 OK`, response `id=236`, `learning_added=true`, `translation_id=157`. |
| Cleanup | Pass | Exact DB cleanup removed `scan_id=236` and `progress_id=71`; Cloudinary destroy returned `result=ok`; emulator image removed. |
| Crash signal | Pass | App-focused logcat showed no app `FATAL EXCEPTION`; only `uiautomator` process lifecycle noise appeared. |

## Target And Input

| Item | Value |
|---|---|
| Device | `emulator-5554` |
| App package | `com.duc.objectlanguage` |
| App activity | `com.duc.objectlanguage/.ui.MainActivity` |
| Test image | `https://ultralytics.com/images/bus.jpg` |
| Emulator image path | `/sdcard/Pictures/gsd_bus.jpg` |
| Selected model | `COCO YOLOv10n` / `yolov10n_int8.tflite` |
| Detected object | `bus` |
| Account side effect user | `user_id=6` |

Note: this rehearsal verifies the actual detector/result/save path through the Gallery image input. Live camera capture with a physical object was not separately tested in this pass.

## Commands And Runtime Evidence

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug --console=plain
adb install -r android\app\build\outputs\apk\debug\app-debug.apk
```

Result:

```text
BUILD SUCCESSFUL in 5s
adb install -r -> Success
```

```powershell
Invoke-WebRequest -Uri 'https://ultralytics.com/images/bus.jpg' -OutFile $env:TEMP\gsd_scan_rehearsal\bus.jpg
adb push $env:TEMP\gsd_scan_rehearsal\bus.jpg /sdcard/Pictures/gsd_bus.jpg
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Pictures/gsd_bus.jpg
```

Runtime UI path:

```text
Dashboard -> Scan tab -> COCO -> Gallery -> select newest image -> preview dialog -> QUET NGAY
```

Relevant UI evidence from `uiautomator`:

```text
tvObjectName = Bus
tvCategory = Phuong tien - COCO YOLOv10n - 91%
tvTranslations = bus (noun) /bɐs/ xe buyt...
Examples rendered: 3
Actions rendered: Nghe phat am, LUU, QUET LAI
```

Relevant logcat evidence:

```text
ObjectDetector: Model loaded OK: yolov10n_int8.tflite. Output shape: [1, 300, 6]
ObjectDetector: YOLO on captured: bus score=0.9169145
ScanViewModel: YOLO result: bus (0.9169145) model=yolov10n_int8.tflite
POST http://192.168.1.84:8000/api/scan -> 200 OK (1451ms)
ScanViewModel: YOLO DB result: source=internal_db translations=1
POST http://192.168.1.84:8000/api/lich-su-quet -> 200 OK (6185ms)
```

Relevant API response evidence:

```text
/api/scan:
source=internal_db
object_id=114
object_code=bus
category_name=Phuong tien
translation_id=157
pending_review=false

/api/lich-su-quet:
id=236
message=Da luu lich su quet
image_url=https://res.cloudinary.com/.../object_scanner/scans/enrdhaskvqepcrkzjjy0.jpg
learning_added=true
learning_status=added
translation_id=157
```

## Cleanup Evidence

The rehearsal created real data and then cleaned it up by exact IDs:

```text
scan_id=236
progress_id=71
user_id=6
translation_id=157
```

Cleanup result:

```text
db_cleanup = deleted scan_history and learning_progress by exact ids
scan_236_exists = false
progress_71_exists = false
cloudinary_public_id = object_scanner/scans/enrdhaskvqepcrkzjjy0
cloudinary_cleanup.result = ok
/sdcard/Pictures/gsd_bus.jpg removed
```

## Decision

The scan demo is now safe for a **guided Gallery -> COCO -> result -> save** rehearsal path.

Remaining scan-specific risk:

- Live camera capture with a physical object has not been separately rehearsed after this pass.
- If the final demo will use the physical camera instead of Gallery, run one short live-camera pass with a known COCO object.
