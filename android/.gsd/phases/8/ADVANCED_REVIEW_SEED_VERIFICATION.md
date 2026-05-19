# Phase 8 Runtime Seed Verification - Advanced Review Modes

> Date: 2026-05-19  
> Scope: seed minimal runtime data with prefix `GSD_E2E`, verify advanced review modes on emulator, then clean up all seeded rows.  
> Guardrail: no app/backend source code was changed.

## Target

| Item | Value |
|---|---|
| Emulator | `emulator-5554` / `sdk_gphone64_x86_64` |
| App package | `com.duc.objectlanguage` |
| Runtime account | `viettran` |
| Backend user id | `6` |
| Initial account data | `progress=0`, `review_logs=0` |
| Seed prefix | `GSD_E2E` |

## Seed Strategy

Seed was done directly through the existing backend SQLAlchemy `SessionLocal`, without editing backend source files.

For each mode, a small batch was inserted, the mode was opened from the Review finished-state chips, evidence was captured, then the batch was deleted before the next mode.

Seeded tables:

- `DoiTuong`
- `BanDich`
- `ViDu`
- `TienDoHoc`
- `AnhDoiTuong` for image matching only

Cleanup removed seed rows by `DoiTuong.ma_doi_tuong LIKE 'GSD_E2E%'`, including related review logs, collection items, examples, media, translations, progress, and objects.

## Results

| Mode | Result | Evidence |
|---|---|---|
| Quiz | Pass | Seeded 4 due cards. Quiz loaded `GSD_E2E QUIZ definition 1`, completed the session, and submitted `POST /api/review/61`, `62`, `63`, `64` -> `200 OK`. |
| Typing | Pass | Seeded 1 due card. Typed `gsd_e2e_typing_1`, result dialog showed `You scored 1 out of 1 (100%)`, and `POST /api/review/65` -> `200 OK`. |
| Listening | Partial pass | Seeded 1 due card. Listening screen loaded and submit path worked: result dialog showed `You scored 1 out of 1 (100%)`, `POST /api/review/66` -> `200 OK`. Audio playback was not ready: dialog showed `Text-to-Speech not ready yet`, and play buttons were disabled. |
| Image matching | Pass | Seeded 1 image-backed due card with non-empty `image_url`. Game showed `Round 1`, two matching cards, completed with `Final Score: 10`, and `POST /api/review/67` -> `200 OK`. |
| Pronunciation | Partial / blocked by runtime audio input | Seeded 1 due card and temporarily granted `RECORD_AUDIO`. Screen rendered `gsd_e2e_speak_1`; recording opened SpeechRecognizer and mic, but emulator had no speech input, so UI showed `No speech detected. Try again!`. No review submit occurred for `progress_id=68`. |

## Runtime Notes

- Advanced mode entry works only from the Review finished state. To test modes with due data, the screen was first kept on the finished-state chip UI, then each seed batch was inserted before tapping the target chip.
- Quiz, typing, listening, image matching, and pronunciation screens showed transient counters such as `Question 1/0` or `Word 1/0` while seeded data was already rendered. This did not block submit for quiz/typing/listening/image matching, but it is a visible UX defect.
- Listening and pronunciation both depend on Android TTS readiness. In this emulator pass, TTS was not ready and `com.google.android.tts` logged a native crash during the audio attempt.
- Pronunciation depends on real audio input. With no spoken audio in the emulator, SpeechRecognizer returned `NO_SPEECH_DETECTED`, so the scoring/submit path was not completed.
- `RECORD_AUDIO` started as `granted=false` and `appops` uid mode `ignore`. It was temporarily granted for pronunciation and then restored to `granted=false` / uid mode `ignore`.
- Android killed `com.duc.objectlanguage` after the microphone permission was revoked. This was caused by permission restoration, not by an in-app advanced review crash.

## Cleanup Evidence

Final cleanup result:

```text
objects_prefix=0
translations_prefix=0
progress_user=0
logs_user=0
collection_items_prefix=0
media_prefix=0
```

Final `RECORD_AUDIO` state:

```text
android.permission.RECORD_AUDIO: granted=false
Uid mode: RECORD_AUDIO: ignore
```

## Remaining Risks

| Risk | Severity | Next action |
|---|---|---|
| Listening TTS playback is not demo-safe on this emulator. | Medium | Retry on a device/emulator with stable TextToSpeech engine before demo. |
| Pronunciation scoring was not completed because no speech was recognized. | Medium | Verify with real microphone input or a device that can provide speech input. |
| Advanced mode counters can show `1/0` during load. | Low/Medium | Fix UI count timing if this is visible in demo. |
| Revoking microphone permission kills/restarts the app. | Low | Expected Android behavior during permission cleanup; avoid changing permission during live demo. |
