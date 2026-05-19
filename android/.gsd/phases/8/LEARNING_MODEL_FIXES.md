# Phase 8.3 - Learning Flow & Model Fix Plan

> Date: 2026-05-19  
> Scope: evidence review and fix plan for review modes + model labels, plus P1 learning UX recovery.  
> Guardrail: backend API was not changed; Android-only UI/ViewModel changes were made for History Detail collection recovery.

## Executive Summary

| Area | Decision | Evidence |
|---|---|---|
| Advanced review mode entry point | Present and runtime verified from Review finished-state chips. | `nav_graph.xml` has 5 actions; `fragment_review.xml` has 5 chips; seeded runtime pass opened quiz, typing, listening, image matching, and pronunciation. |
| Advanced review mode data | Seeded runtime verification completed for quiz, typing, listening submit path, and image matching; pronunciation remains blocked by recognized speech input. | `.gsd/phases/8/ADVANCED_REVIEW_SEED_VERIFICATION.md` records `GSD_E2E` seed/cleanup and POST results for progress `61-67`. |
| Pronunciation | Reachable and mic opens after permission grant, but scoring/submit was not verified. | Seeded runtime pass rendered the word and SpeechRecognizer opened, then returned `NO_SPEECH_DETECTED`; no `POST /api/review/68` occurred. |
| Custom model labels | Keep `SCHOOL_SUPPLIES_LABELS = listOf("ruler")` unless training labels/metadata prove more classes. | Repo has no sidecar label file or training class metadata for `best_float32.tflite`; code maps custom model to one label. |
| Demo model guidance | Use COCO model for broad object demo; treat custom model as ruler-only until verified otherwise. | Scan UI has Custom YOLO/COCO toggle; custom label map is one-class, COCO label map is full COCO list. |
| History -> Collection recovery | Implemented Android-only and runtime verified. | History Detail exposed `Add to collection`; collection picker loaded `hello`; `POST /api/collections/6/items` returned `200 OK`. |
| Build verification | Pass. | `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat assembleDebug --console=plain` -> `BUILD SUCCESSFUL in 30s`. |

## Inputs

- `.gsd/phases/7/GAP_ANALYSIS.md`
- `.gsd/phases/8/RUNTIME_VERIFICATION.md`
- `app/src/main/res/navigation/nav_graph.xml`
- `app/src/main/res/layout/fragment_review.xml`
- `app/src/main/java/com/duc/objectlanguage/ui/review/**`
- `app/src/main/java/com/duc/objectlanguage/ui/scan/ObjectDetectorHelper.kt`
- `app/src/main/assets/**`
- `app/src/main/java/com/duc/objectlanguage/ui/history/HistoryDetailFragment.kt`
- `app/src/main/java/com/duc/objectlanguage/ui/history/HistoryDetailViewModel.kt`
- `app/src/main/res/layout/fragment_history_detail.xml`

## Review Mode Reachability

### Gap From Phase 7 / Runtime Plan 8.2

Phase 7 flagged that advanced review fragments existed, but entry point reachability from the main UI needed confirmation.

Runtime Plan 8.2 confirmed the Review tab was reachable, but current data had no due words:

```text
Hom nay da on xong / no due words
```

So card review and advanced mode sessions were not exercised end-to-end in that first pass.

Runtime follow-up with account `quytran` created one due word and verified the normal review flow:

| Step | Result | Evidence |
|---|---|---|
| Open Review | Pass | Card `1 / 1` rendered for `Monkey plush toy`. |
| Reveal answer | Pass | Definition, example, and quality buttons rendered. |
| Submit answer | Pass | `POST /api/review/60` returned `200 OK`. |
| Finished-state mode entry | Partial pass | Five advanced mode chips rendered. Tapping quiz was safe and triggered `GET /api/review` -> `200 OK`, but the app returned to the no-due state because the only due card had already been consumed. |

Seeded runtime follow-up with account `viettran` / `user_id=6` verified advanced modes with cleanup:

| Mode | Result | Evidence |
|---|---|---|
| Quiz | Pass | 4 seeded cards; `POST /api/review/61`, `62`, `63`, `64` -> `200 OK`. |
| Typing | Pass | 1 seeded card; result `1/1 (100%)`; `POST /api/review/65` -> `200 OK`. |
| Listening | Partial pass | Submit path passed; result `1/1 (100%)`; `POST /api/review/66` -> `200 OK`; TTS audio was not ready. |
| Image matching | Pass | 1 image-backed card; result `Final Score: 10`; `POST /api/review/67` -> `200 OK`. |
| Pronunciation | Partial / blocked | Word rendered and mic opened; SpeechRecognizer returned no speech; no submit for `progress_id=68`. |
| Cleanup | Pass | Final DB check: `objects_prefix=0`, `translations_prefix=0`, `progress_user=0`, `logs_user=0`, `media_prefix=0`. |

### Current Working Tree Evidence

The current working tree already contains review mode entry code:

| File | Evidence |
|---|---|
| `app/src/main/res/navigation/nav_graph.xml` | `reviewFragment` has `action_review_to_quiz`, `action_review_to_typing`, `action_review_to_listening`, `action_review_to_imageMatching`, `action_review_to_pronunciation`. |
| `app/src/main/res/layout/fragment_review.xml` | Finished state uses `layoutFinished` with `ChipGroup` and chips: `chipQuiz`, `chipTyping`, `chipListening`, `chipImageMatching`, `chipPronunciation`. |
| `app/src/main/java/com/duc/objectlanguage/ui/review/ReviewFragment.kt` | Chip click listeners call `findNavController().navigate(...)` for the 5 advanced destinations. |
| `app/src/main/java/com/duc/objectlanguage/ui/MainActivity.kt` | Up-navigation maps advanced review destinations back to `reviewFragment`. |

No extra app code change is recommended before deciding whether these existing working-tree edits are the desired final fix.

### Remaining Runtime Risk

The entry UI is present, but demo-readiness still needs a runtime pass with seeded due cards:

| Mode | Entry status | Data/runtime risk |
|---|---|---|
| Quiz | Present | `QuizViewModel.loadQuiz()` uses `repo.getDueReviews()`; empty due list ends the mode. |
| Typing | Present | `TypingTestViewModel.loadTest()` uses `repo.getDueReviews()`; empty due list ends the mode. |
| Listening | Present | `ListeningTestViewModel.loadTest()` uses `repo.getDueReviews()`; also needs TTS/audio check. |
| Image matching | Present | `ImageMatchingViewModel.loadGame()` uses due reviews and requires cards with `imageUrl`. |
| Pronunciation | Present | `PronunciationViewModel` needs due words; `PronunciationFragment` also needs `RECORD_AUDIO`. |

### Decision

- Keep the current entry point shape if the goal is the smallest fix: show mode chips from the Review finished state.
- Before demo, retest listening/pronunciation on a runtime with stable TTS and real microphone input.
- If advanced modes are a headline demo feature, consider a stronger entry point later: a dedicated mode selector visible even before the normal review session ends.

## Custom Model Label Map

### Evidence

| Item | Value |
|---|---|
| Custom asset | `app/src/main/assets/best_float32.tflite`, 9,366,628 bytes |
| COCO asset | `app/src/main/assets/yolov10n_int8.tflite`, 3,126,026 bytes |
| Current custom label map | `SCHOOL_SUPPLIES_LABELS = listOf("ruler")` |
| Current COCO label map | `COCO_LABELS` list in `ObjectDetectorHelper.kt` |
| Model switch | `ScanFragment` defaults to `ObjectDetectorHelper.CUSTOM_MODEL`; UI toggle can choose COCO. |
| Missing evidence | No sidecar labels file or training class order for `best_float32.tflite` found in repo assets. |
| Runtime evidence | Plan 8.2 reached scan capture confirmation, but did not execute model detection/result/save. |

### Decision

Do not invent more labels for `best_float32.tflite`.

The safe project decision is:

- Treat `best_float32.tflite` as custom/ruler-only unless training metadata proves otherwise.
- Keep `SCHOOL_SUPPLIES_LABELS = listOf("ruler")` for now.
- Use the COCO model path for broad object demo.
- If custom model should recognize more than `ruler`, require the exact training class order first, then update `SCHOOL_SUPPLIES_LABELS` and run a scan smoke test.

## P1 Learning UX: Add History Item To Collection

### User Problem

After scan/save, a user may forget to add the word to a collection. Before this follow-up, the recovery path was awkward: Scan result had an add-to-collection action, but History Detail only displayed the saved scan and vocabulary/audio.

### Backend/API Evidence

No backend change was required.

| Layer | Evidence |
|---|---|
| Android repository | `CollectionRepository.addToCollection(collectionId, translationId)` already exists. |
| Android API | `CollectionApiService.addToCollection()` already calls `POST api/collections/{id}/items`. |
| Backend router | `collection_router.py` already exposes `POST /api/collections/{collection_id}/items`. |
| Backend service | `CollectionService.add_to_collection()` already checks ownership and duplicate items. |
| History data | `HistoryDetail` and `TranslationResponse` expose translation ids needed by the collection API. |

### Android Change

| File | Change |
|---|---|
| `app/src/main/res/layout/fragment_history_detail.xml` | Added `btnAddHistoryToCollection` below the translation card content. |
| `app/src/main/java/com/duc/objectlanguage/ui/history/HistoryDetailFragment.kt` | Tracks the active translation id, shows the button only when a translation exists, and forwards clicks to the ViewModel. |
| `app/src/main/java/com/duc/objectlanguage/ui/history/HistoryDetailViewModel.kt` | Added collection loading, collection picker dialog, add-to-collection call, and success/error message LiveData. |

### Behavior

- If History Detail has a translation, show `Add to collection`.
- Tapping opens the existing collection picker behavior.
- If no collections exist, show the existing `collection_no_collections` message.
- On success, show the existing `collection_added_to` message.
- If the history item has no vocabulary translation, keep the action hidden.

### Verification

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug --console=plain
```

Result: `BUILD SUCCESSFUL in 30s`.

Runtime click-through was verified on the new account:

| Step | Result | Evidence |
|---|---|---|
| History list | Pass | Profile -> History showed `Monkey plush toy` with `100%` confidence and timestamp `2026-05-19 22:34:07`. |
| History detail | Pass | Detail rendered the saved translation, definition, example, and `Them vao bo suu tap` button. |
| Collection picker | Pass | `GET /api/collections` returned `200 OK`; picker showed collection `hello`. |
| Add to collection | Pass | Selecting `hello` sent `POST /api/collections/6/items` and returned `200 OK (31ms)`. |

## Evidence Required Before App Code Changes

If the next step modifies app code, collect or decide these first:

| Needed evidence | Why |
|---|---|
| Runtime with stable TextToSpeech | Required before considering listening/pronunciation audio demo-ready. |
| Real microphone input or speech injection path | Required to verify pronunciation scoring and submit. |
| Confirm whether advanced modes should be visible before review completion | Decides whether current finished-state chips are enough or a dedicated selector is needed. |
| Training class order for `best_float32.tflite` | Required before adding labels beyond `ruler`. |
| Runtime scan detection result for custom and COCO | Required to confirm label mapping and result/save flow. |

## Commands Run

```powershell
rg -n "quizFragment|typingTestFragment|listeningTestFragment|imageMatchingFragment|pronunciationFragment|action_review|navigate\(" android/app/src/main/java android/app/src/main/res
```

Result: found 5 review actions and 5 chip navigation handlers in current working tree.

```powershell
rg -n "SCHOOL_SUPPLIES_LABELS|COCO_LABELS|best_float32|yolov10n|labelsForModel" android/app/src/main/java android/app/src/main/assets android/.gsd
```

Result: custom model maps to one label (`ruler`); COCO model has its own label list; no sidecar label file was found in assets.

```powershell
Get-ChildItem -Force -Path android/app/src/main/assets | Select-Object Mode,Length,Name
```

Result:

```text
best_float32.tflite   9366628
yolov10n_int8.tflite  3126026
```

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug --console=plain
```

Result after the P1 History Detail change: `BUILD SUCCESSFUL in 30s`.

```powershell
adb logcat -d | Select-String -Pattern 'api/collections|api/review|FATAL EXCEPTION|AndroidRuntime' | Select-Object -Last 120
```

Runtime follow-up result:

```text
POST http://192.168.1.84:8000/api/review/60 -> 200 OK (488ms)
GET  http://192.168.1.84:8000/api/collections -> 200 OK (15ms)
POST http://192.168.1.84:8000/api/collections/6/items -> 200 OK (31ms)
```

```powershell
adb logcat -d | Select-String -Pattern 'api/review|NO_SPEECH_DETECTED|Text-to-Speech' | Select-Object -Last 120
```

Seeded advanced review follow-up result:

```text
POST /api/review/61 -> 200 OK
POST /api/review/62 -> 200 OK
POST /api/review/63 -> 200 OK
POST /api/review/64 -> 200 OK
POST /api/review/65 -> 200 OK
POST /api/review/66 -> 200 OK
POST /api/review/67 -> 200 OK
SpeechRecognizer -> NO_SPEECH_DETECTED for pronunciation
```

## Residual Risks

| Risk | Severity | Next action |
|---|---|---|
| Current review entry fix is in working tree, not committed. | Medium | User decides whether to keep/commit these app changes in a separate step. |
| Listening TTS was not ready on emulator. | Medium | Retest on a device/emulator with stable TextToSpeech before demo. |
| Pronunciation scoring/submit was not completed. | Medium | Verify with real recognized speech input. |
| Advanced mode counters can show `Question 1/0` or `Word 1/0` during load. | Low/Medium | Fix if it appears in demo rehearsals. |
| Custom model class scope is not independently proven from training metadata. | Medium | Keep ruler-only scope, or provide exact class list before code change. |
| Scan detection/result/save path remains unverified. | High | Plan 8.4 should run a focused scan smoke with both model toggles if possible. |

## Done Criteria

- [x] Decision on advanced review mode entry point documented.
- [x] Decision on custom model labels documented.
- [x] Android build pass recorded.
- [x] `LEARNING_MODEL_FIXES.md` exists.
- [x] P1 History Detail add-to-collection recovery implemented with Android-only changes.
- [x] P1 History Detail add-to-collection recovery runtime verified on emulator.
- [x] Seeded advanced review runtime verification documented.
- [x] No backend source code was changed by this plan.
