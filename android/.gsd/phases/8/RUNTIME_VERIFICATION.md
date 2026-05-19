# Phase 8.2 Runtime Device Verification

> Date: 2026-05-19  
> Scope: verify-only runtime pass on emulator/device.  
> Guardrail: no app/backend source code was changed for this plan.

## Executive Summary

| Area | Result | Evidence |
|---|---|---|
| Emulator/device | Pass | `Pixel_8_Pro_API_35` booted as `emulator-5554`; `sys.boot_completed=1`. |
| Backend reachability | Pass | Dev machine and emulator can reach `192.168.1.84:8000`; emulator `toybox nc` returned `EXIT:0`. |
| Build/install/launch | Pass | `assembleDebug` success, `adb install -r` success, `MainActivity` focused after `monkey` launch. |
| Main navigation tabs | Pass | Dashboard, Scan, Dictionary, Review, and Profile rendered without app fatal crash. |
| Profile subflows | Pass | Analytics, Streak, Collections, and Notification Settings screens rendered. |
| Scan camera/capture | Partial pass | Scan screen opened and capture reached confirmation screen: `Is the object clear?`; actual detection was not executed. |
| Review session | Blocked by data | Review tab rendered `Hom nay da on xong` / no due words, so active card answer flow was not available. |
| Permissions | Partial pass | `CAMERA` and `POST_NOTIFICATIONS` granted; `RECORD_AUDIO` not granted, so pronunciation/audio recording was not verified. |
| App crash signal | Pass | No app fatal exception observed in recent app-focused runtime logcat. `uiautomator` VM shutdown lines were tool-process noise. |

## Runtime Target

| Item | Value |
|---|---|
| AVD | `Pixel_8_Pro_API_35` |
| Device id | `emulator-5554` |
| Device product/model | `sdk_gphone64_x86_64` / `sdk_gphone64_x86_64` |
| App package | `com.duc.objectlanguage` |
| Main activity | `com.duc.objectlanguage.ui.MainActivity` |
| Installed APK | `app/build/outputs/apk/debug/app-debug.apk` |
| Backend base from `local.properties` | `SERVER_IP=192.168.1.84`, `SERVER_PORT=8000` |

## Commands And Results

```powershell
adb devices -l
```

Result: no device initially, then emulator started and reported:

```text
emulator-5554 device product:sdk_gphone64_x86_64 model:sdk_gphone64_x86_64 device:emu64xa transport_id:3
```

```powershell
& 'C:\Users\MSI\AppData\Local\Android\Sdk\emulator\emulator.exe' -list-avds
```

Result: `Pixel_8_Pro_API_35`.

```powershell
Start-Process -FilePath 'C:\Users\MSI\AppData\Local\Android\Sdk\emulator\emulator.exe' -ArgumentList @('-avd','Pixel_8_Pro_API_35','-no-window','-no-audio','-no-boot-anim') -WindowStyle Hidden -PassThru
adb shell getprop sys.boot_completed
```

Result: emulator booted; `sys.boot_completed` returned `1`.

```powershell
Test-NetConnection -ComputerName 192.168.1.84 -Port 8000
adb shell 'toybox nc -z -w 3 192.168.1.84 8000; echo EXIT:$?'
```

Result: host `TcpTestSucceeded=True`; emulator returned `EXIT:0`.

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug --console=plain
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb logcat -c
adb shell monkey -p com.duc.objectlanguage 1
```

Result: `BUILD SUCCESSFUL`, install `Success`, launch injected 1 event, and `MainActivity` became focused.

## Flow Checklist

| Flow | Result | Evidence / Note |
|---|---|---|
| App launch | Pass | Focused activity: `com.duc.objectlanguage/.ui.MainActivity`. Existing runtime state was logged in as user `duc`. |
| Dashboard | Pass | Rendered `LengoLens`, search prompt, daily goal, scan CTA, streak/review stats, and bottom navigation. |
| Guest scan guard | Not verified | Existing app data was already authenticated, so guest-only behavior was not reachable without clearing runtime data. |
| Login/register | Not verified | Existing session was present; fresh auth was intentionally not forced during verify-only pass. |
| Scan screen | Pass | Rendered camera preview/overlay, `Custom YOLO` and `COCO` toggles, gallery, and capture controls. |
| Scan capture | Partial pass | Capture reached image confirmation with `CROP AGAIN`, `RETAKE`, and `SCAN NOW`. Actual model detection/result detail was not executed. |
| Dictionary | Pass | Rendered source language, text input, swap action, and empty translation state. |
| Save/learn | Not verified | No new dictionary/scan result was saved in this verify-only pass. |
| Review tab | Blocked by data | Rendered done state: no due words available; card answer flow could not be exercised. |
| Profile | Pass | Rendered account info, language controls, history, analytics, streak, collections, logout. |
| Analytics | Pass | Rendered total words, due count, mastered count, current streak, reviews today, best streak, and 7-day charts. |
| Streak | Pass | Rendered current streak, reviewed-today state, stats, and next milestone progress. |
| Collections | Pass | Rendered empty state and collection search. |
| Notification settings | Pass | Rendered daily reminder, time, permission state, test notification action, and reminder options. |
| Notification permission | Pass | App permission dump showed `POST_NOTIFICATIONS: granted=true`; settings screen said notifications are allowed. |
| Camera permission | Pass | App permission dump showed `CAMERA: granted=true`; scan camera UI opened. |
| Microphone / pronunciation | Blocked | App permission dump showed `RECORD_AUDIO: granted=false`; pronunciation runtime behavior was not verified. |
| WorkManager reminder behavior | Not verified | Settings screen rendered, but scheduling/delivery was not validated in this pass. |

## Runtime Findings

1. Runtime launch is now verified on an Android emulator. This closes the Phase 7 runtime evidence gap where no device/emulator was attached.
2. The app can reach the configured backend `192.168.1.84:8000` from both the dev machine and emulator network.
3. Core shell navigation is stable enough for demo exploration: Dashboard, Scan, Dictionary, Review, Profile, and several Profile subflows render without fatal app crash.
4. Scan is only partially verified. Camera capture reached the confirmation step, but model detection/result detail/audio/save behavior still needs a focused pass.
5. Review is blocked by current data state: the account has no due words, so the normal card review flow could not be tested.
6. Pronunciation/audio remains blocked at runtime because `RECORD_AUDIO` is not granted and the advanced pronunciation flow has not been reached from the main UI.
7. During coordinate-based UI testing, the runtime language control was tapped once and may have changed the installed app state to English. This changed emulator app data only, not repository files.

## Runtime Follow-Up: New Account Due Word

> Date: 2026-05-19  
> Scope: verify-only follow-up after the user switched to a new account with 1 review word.  
> Guardrail: no app/backend source code was changed during this follow-up.

| Flow | Result | Evidence / Note |
|---|---|---|
| Account state | Pass | Dashboard showed account `quytran` and `On lai 1 tu sap quen` / one due review word. |
| Review card entry | Pass | Review tab opened card `1 / 1` for `Monkey plush toy` with phonetic `/ˈmʌŋ.ki plʌʃ tɔɪ/` and answer reveal action. |
| Review answer reveal | Pass | Revealed definition, example sentence, and quality actions `Quen`, `Kho`, `On`, `De`. |
| Review submit | Pass | Submitted the card with the lowest quality action; logcat showed `POST http://192.168.1.84:8000/api/review/60` -> `200 OK (488ms)`. |
| Advanced review mode entry | Partial pass | Finished review state showed 5 mode chips: quiz, typing, listening, image matching, pronunciation. Tapping quiz was safe and triggered `GET /api/review` -> `200 OK`; full mode session was blocked because the only due card had already been consumed. |
| History list/detail | Pass | Profile -> History listed `Monkey plush toy` with `100%` confidence and date `2026-05-19 22:34:07`; History Detail rendered the translation, definition, example, and audio controls. |
| History Detail -> Add to Collection | Pass | `Them vao bo suu tap` button appeared on History Detail, collection picker loaded collection `hello`, `GET /api/collections` -> `200 OK`, and selecting `hello` sent `POST /api/collections/6/items` -> `200 OK (31ms)`. |
| Crash signal | Pass | No app fatal exception was observed in the app-focused logcat evidence for the reviewed flows. |

### Follow-Up Commands / Log Evidence

```powershell
adb install -r android\app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.duc.objectlanguage/com.duc.objectlanguage.ui.MainActivity
adb logcat -d | Select-String -Pattern 'api/collections|api/review|FATAL EXCEPTION|AndroidRuntime' | Select-Object -Last 120
```

Relevant runtime evidence:

```text
GET  http://192.168.1.84:8000/api/review -> 200 OK
POST http://192.168.1.84:8000/api/review/60 -> 200 OK (488ms)
GET  http://192.168.1.84:8000/api/review -> 200 OK
GET  http://192.168.1.84:8000/api/collections -> 200 OK (15ms)
POST http://192.168.1.84:8000/api/collections/6/items -> 200 OK (31ms)
```

### Runtime Data Side Effects

- The single due review card for `Monkey plush toy` was submitted with the lowest quality action during verification.
- The `Monkey plush toy` translation from History Detail was added to collection `hello`.
- Because the only due card was consumed first, advanced review modes still need a fresh due-card dataset for full per-mode verification.

## Runtime Follow-Up: Seeded Advanced Review Modes

> Date: 2026-05-19  
> Scope: seed minimal `GSD_E2E` data for account `viettran` / `user_id=6`, verify advanced review modes, then clean up seeded rows.  
> Guardrail: no app/backend source code was changed.

Detailed evidence: `.gsd/phases/8/ADVANCED_REVIEW_SEED_VERIFICATION.md`.

| Flow | Result | Evidence / Note |
|---|---|---|
| Seed safety | Pass | Account started with `progress=0`, `review_logs=0`; seed used prefix `GSD_E2E` and per-mode cleanup. |
| Quiz | Pass | 4 seeded due cards loaded; session completed; `POST /api/review/61`, `62`, `63`, `64` all returned `200 OK`. |
| Typing | Pass | 1 seeded due card loaded; exact answer submitted; result `1/1 (100%)`; `POST /api/review/65` -> `200 OK`. |
| Listening | Partial pass | Data/form submit path passed; result `1/1 (100%)`; `POST /api/review/66` -> `200 OK`. Audio playback was blocked by `Text-to-Speech not ready yet`, and play controls were disabled. |
| Image matching | Pass | 1 image-backed seeded card loaded; matching pair completed; result `Final Score: 10`; `POST /api/review/67` -> `200 OK`. |
| Pronunciation | Partial / blocked | Seeded word rendered and mic/SpeechRecognizer opened after temporary `RECORD_AUDIO` grant. Emulator returned `No speech detected. Try again!`; no review submit occurred for `progress_id=68`. |
| Cleanup | Pass | Final DB check returned `objects_prefix=0`, `translations_prefix=0`, `progress_user=0`, `logs_user=0`, `media_prefix=0`. |
| Permission restore | Pass with note | `RECORD_AUDIO` restored to `granted=false` and appops uid mode `ignore`. Android killed the app during permission revoke, which is expected permission-change behavior. |

### Seeded Follow-Up Findings

1. Advanced review mode entry is usable from the Review finished-state chips.
2. Quiz, typing, and image matching are runtime-verified end-to-end with seeded due cards.
3. Listening can complete the answer/submit path, but TTS playback is not reliable on this emulator.
4. Pronunciation entry and mic opening are verified, but scoring/submit is still blocked without real recognized speech.
5. Advanced mode counters can briefly show `Question 1/0` or `Word 1/0` while data is rendered. This did not block the tested submit paths, but it is visible UX evidence.

## Priority Follow-Up

| Priority | Item | Suggested Plan |
|---|---|---|
| P0/P1 | Exercise actual scan detection/result/save path with known input and backend reachable. | Plan 8.3 or 8.4 |
| P1 | Verify listening TTS playback on a runtime where TextToSpeech is ready. | Plan 8.4 |
| P1 | Verify pronunciation scoring/submit with real microphone input. | Plan 8.4 |
| P2 | Fix advanced mode transient counters showing `1/0` if this appears during demo. | Later hardening |
| P1 | Smoke DB-backed backend/API flows with real test data. | Plan 8.4 |
| P2 | Test notification scheduling/delivery, not only settings UI. | Plan 8.4 |

## Runtime Follow-Up: Focused Scan Rehearsal

> Date: 2026-05-19  
> Scope: verify actual detection/result/save path using emulator Gallery input and COCO model.  
> Guardrail: no app/backend source code was changed.

Detailed evidence: `.gsd/phases/8/FOCUSED_SCAN_REHEARSAL.md`.

| Flow | Result | Evidence / Note |
|---|---|---|
| Build/install | Pass | `assembleDebug` -> `BUILD SUCCESSFUL`; `adb install -r` -> `Success`. |
| Gallery input | Pass | Test image selected from `/sdcard/Pictures/gsd_bus.jpg`. |
| COCO detection | Pass | `YOLO on captured: bus score=0.9169145`; model `yolov10n_int8.tflite`. |
| Result UI | Pass | Rendered `Bus`, `Phuong tien - COCO YOLOv10n - 91%`, translation, examples, audio, save action. |
| Backend lookup | Pass | `POST /api/scan` -> `200 OK`; `source=internal_db`, `object_code=bus`, `translation_id=157`. |
| Save/history/learning | Pass | `POST /api/lich-su-quet` -> `200 OK`; response `id=236`, `learning_added=true`, `translation_id=157`. |
| Cleanup | Pass | Deleted exact DB side effects `scan_id=236` and `progress_id=71`; Cloudinary asset destroy returned `result=ok`; emulator image removed. |
| Remaining scan risk | Partial | Live camera capture with physical object was not separately tested in this pass. |

## Done Criteria

- [x] App launch was tested on emulator/device.
- [x] Core demo flow was checked and each item was marked Pass, Partial, Blocked, or Not verified.
- [x] Permission/runtime behavior was recorded.
- [x] Runtime-only risks were documented with evidence.
- [x] `RUNTIME_VERIFICATION.md` exists.
- [x] Follow-up account with 1 due word verified normal review card flow.
- [x] Follow-up verified History Detail -> Add to Collection against the existing collection API.
- [x] Follow-up seeded `GSD_E2E` data and cleaned it up after advanced review mode verification.
- [x] Follow-up verified Gallery -> COCO detection -> result -> save/history/learning path and cleaned up data side effects.
