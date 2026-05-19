# Project TODOs

> Updated: 2026-05-19  
> Active phase: Phase 8 - On Dinh & San Sang Demo

## Phase 8 Active TODO

### Plan 8.1 - P0 Release Blockers

- [x] Create `.gsd/phases/8/P0_BLOCKERS.md`.
- [x] Decide asset strategy for `best_float32.tflite`.
- [x] Document Java/JDK setup issue around `JAVA_HOME=C:\Java`.
- [x] Prepare runtime device checklist for Plan 8.2.
- [x] With user approval, track `best_float32.tflite` using Git LFS.
- [ ] Commit/push staged LFS asset changes when requested.
- [x] With user action, attach emulator/device for runtime verification.

### Plan 8.2 - Runtime Device Verification

- [x] Attach emulator/device so `adb devices` shows a target.
- [x] Install and launch debug APK.
- [x] Verify runtime navigation and profile subflows.
- [x] Record scan partial pass, review data blocker, and permission gaps.
- [x] Create `.gsd/phases/8/RUNTIME_VERIFICATION.md`.

### Plan 8.3 - Learning Flow & Model Fix Plan

- [x] Decide/fix entry point for quiz, typing, listening, image matching, pronunciation.
- [x] Decide/fix custom model labels for `best_float32.tflite`.
- [x] Add History Detail -> Add to Collection recovery path using existing backend API.
- [x] Build current Android working tree after review/model evidence check.
- [x] Runtime verify normal review card flow with account that has 1 due word.
- [x] Runtime verify History Detail -> Add to Collection with existing collection API.
- [x] Runtime verify advanced review mode chips are visible and safe to tap from finished review state.
- [x] Runtime seed/cleanup `GSD_E2E` test data for account `viettran`.
- [x] Runtime verify seeded quiz, typing, listening submit path, and image matching.
- [ ] Runtime verify listening TTS playback and pronunciation scoring with real audio input.
- [ ] Decide whether to fix advanced mode counters showing `1/0` during load.
- [x] Create `.gsd/phases/8/LEARNING_MODEL_FIXES.md`.

### Plan 8.4 - Demo Hardening & Final Readiness

- [x] Smoke DB-backed backend/API endpoints with real test data.
- [x] Decide minimal automated smoke tests.
- [x] Cleanup GSD docs for demo scope.
- [x] Create `.gsd/phases/8/DEMO_READINESS.md`.

## Next Recommended TODO

- [ ] Commit/push staged Git LFS model asset changes when requested.
- [x] Run focused emulator scan rehearsal for Gallery -> COCO -> detection -> result -> save.
- [ ] Optional: run live-camera scan rehearsal with a known physical COCO object.
- [ ] Retest listening TTS playback on stable device/emulator.
- [ ] Retest pronunciation scoring with real speech input.
- [ ] Decide whether to fix advanced mode counters showing `1/0` during load.

## Historical Note

Phase 7 and Phase 8 evidence are complete. Do not use old Phase 6 checklist as active work. Use `.gsd/phases/8/DEMO_READINESS.md` for demo scope and residual risk decisions.
