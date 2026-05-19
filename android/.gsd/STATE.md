# GSD State

> Updated: 2026-05-19  
> Current source of truth: `.gsd/phases/7/VERIFICATION.md` + `.gsd/phases/8/P0_BLOCKERS.md` + `.gsd/phases/8/RUNTIME_VERIFICATION.md` + `.gsd/phases/8/LEARNING_MODEL_FIXES.md` + `.gsd/phases/8/ADVANCED_REVIEW_SEED_VERIFICATION.md` + `.gsd/phases/8/FOCUSED_SCAN_REHEARSAL.md` + `.gsd/phases/8/DEMO_READINESS.md`

## Current Position

- **Phase**: 8 - On Dinh & San Sang Demo
- **Current task**: Phase 8 evidence complete; ready for LFS commit, optional live-camera rehearsal, or final demo polish.
- **Status**: Plan 8.4 complete plus focused Gallery/COCO scan rehearsal. Backend/API smoke passed with real `GSD_E2E` DB seed and cleanup. Chua commit/push; app/backend source khong bi sua trong scan rehearsal.

## Completed Rebaseline

- [x] Phase 7.1: `.gsd/phases/7/CURRENT_STATE.md`
- [x] Phase 7.2: `.gsd/phases/7/GAP_ANALYSIS.md`
- [x] Phase 7.3: `.gsd/phases/7/VERIFICATION.md`

## Phase 8 Docs

| Plan | Trang thai | Deliverable |
|---|---|---|
| 8.1 - P0 Release Blockers | Docs complete / approval pending | `.gsd/phases/8/P0_BLOCKERS.md` |
| 8.2 - Runtime Device Verification | Complete / partial runtime gaps documented | `.gsd/phases/8/RUNTIME_VERIFICATION.md` |
| 8.3 - Learning Flow & Model Fix Plan | Complete / decisions documented | `.gsd/phases/8/LEARNING_MODEL_FIXES.md`, `.gsd/phases/8/ADVANCED_REVIEW_SEED_VERIFICATION.md` |
| 8.4 - Demo Hardening & Final Readiness | Complete / guided demo-ready | `.gsd/phases/8/DEMO_READINESS.md` |

## Important Findings From Verification

- Android debug build pass local khi dung JDK 17/JBR.
- Backend import/smoke pass voi FastAPI TestClient.
- Runtime UI da verify tren `Pixel_8_Pro_API_35` / `emulator-5554`: install, launch, main tabs, va profile subflows pass.
- Backend `192.168.1.84:8000` reachable tu emulator bang `toybox nc` (`EXIT:0`).
- Scan capture partial pass: live camera da vao confirmation screen, nhung physical-camera detection/save chua verify.
- Focused scan rehearsal pass qua Gallery + COCO: `bus` detected `91%`, result UI rendered, `POST /api/scan` -> `200 OK`, `POST /api/lich-su-quet` -> `200 OK`; cleanup deleted `scan_id=236`, `progress_id=71`, and Cloudinary asset.
- Review card flow da pass trong runtime follow-up voi account `quytran`: card `Monkey plush toy`, reveal answer, va `POST /api/review/60` -> `200 OK`.
- `RECORD_AUDIO` chua granted nen pronunciation/audio recording chua verify runtime.
- `best_float32.tflite` la model mac dinh va da duoc stage bang Git LFS cung `android/.gitattributes`; pending commit/push.
- Current shell `JAVA_HOME=C:\Java` invalid; Gradle build pass voi Android Studio JBR 17.
- Current working tree da co review mode entry points: 5 actions tu `reviewFragment`, 5 chips trong finished state, va 5 navigate handlers.
- Advanced review seeded runtime pass voi account `viettran` da pass quiz, typing, image matching; listening submit path pass nhung TTS chua ready; pronunciation render/mic pass nhung scoring bi block vi khong co speech input.
- Seed/cleanup da sach: final DB check cho prefix `GSD_E2E` tra ve `objects=0`, `translations=0`, `progress_user=0`, `logs_user=0`, `media=0`.
- Advanced review counters co UX risk: mot so man hinh hien `Question 1/0` hoac `Word 1/0` luc load data.
- Custom model labels giu `ruler` only vi repo khong co sidecar labels/training class metadata cho `best_float32.tflite`.
- History Detail now supports adding an existing scan translation to a collection using the existing collection API; runtime verified voi collection `hello` va `POST /api/collections/6/items` -> `200 OK`.
- Plan 8.3/P1 Android `assembleDebug` pass voi Android Studio JBR 17.
- Backend DB-backed demo endpoints da smoke pass voi FastAPI TestClient + real DB seed: `34 pass / 0 fail / 0 blocked`.
- Final cleanup cho prefix `GSD_E2E` tra ve 0 cho users, objects, translations, progress, logs, collections, scans, lookups, va media.
- Demo readiness decision: guided demo/user-test ready cho Gallery/COCO scan, learning/review/collections/backend; chua full release-ready vi live physical-camera pass, listening TTS, pronunciation scoring, va staged LFS commit con residual risk.

## Next Command

Neu muon commit phan LFS asset da stage:

```text
Theo GSD, commit staged Phase 8 P0 model asset changes.
```

Neu muon rehearse live camera that truoc demo:

```text
Theo GSD, run optional live-camera scan rehearsal.
Verify physical camera capture -> detection -> result -> save, chi ghi evidence truoc neu can sua app/backend.
```

## Guardrails

- Khong sua app/backend neu user chi yeu cau tao docs hoac verify-only.
- Phase 8 khong them feature lon; chi on dinh blocker/risk da co evidence.
- Neu sua code, cap nhat output doc cua plan va chay verify phu hop.
- Khong commit/push neu user chua yeu cau.
