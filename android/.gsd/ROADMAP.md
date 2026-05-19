# Object Scanner GSD Roadmap

> Current phase: **Phase 8 - On Dinh & San Sang Demo**  
> Updated: 2026-05-19  
> Source of truth: `.gsd/phases/7/VERIFICATION.md` + `.gsd/phases/8/P0_BLOCKERS.md` + `.gsd/phases/8/RUNTIME_VERIFICATION.md` + `.gsd/phases/8/LEARNING_MODEL_FIXES.md` + `.gsd/phases/8/FOCUSED_SCAN_REHEARSAL.md` + `.gsd/phases/8/DEMO_READINESS.md`

## Nguyen Tac Sau Reset

- Phase 1-6 la historical context.
- Phase 7 la rebaseline complete: current state, gap analysis, verification.
- Phase 8 phai dua tren blockers/risks co evidence trong `VERIFICATION.md`.
- Neu user chi yeu cau docs/verify, khong sua source app/backend.
- Khi Codex/Gemini lam viec, uu tien doc: `AGENTS.md` -> `.gsd/README.md` -> `.gsd/STATE.md` -> phase plan hien tai.

## Phase Status

| Phase | Trang thai | Ghi chu |
|---|---|---|
| Phase 1 | Historical complete | Core foundation. |
| Phase 2 | Historical complete | Auth/data/API foundation. |
| Phase 3 | Historical complete | Scan/detail/review baseline. |
| Phase 4 | Historical complete | UI/UX polish va feature expansion. |
| Phase 5 | Historical complete | v1.0 milestone theo docs cu. |
| Phase 6 | Historical / stale | Nhieu feature co code, nhung docs cu co file names sai va da duoc Phase 7 phan loai. |
| Phase 7 | Complete | Rebaseline, gap analysis, build/backend verification. |
| Phase 8 | Current / evidence complete | Guided demo-ready; Gallery/COCO scan pass; residual risks documented for live camera/audio/LFS. |

## Phase 7 Summary

Phase 7 da tao:

- `.gsd/phases/7/CURRENT_STATE.md`
- `.gsd/phases/7/GAP_ANALYSIS.md`
- `.gsd/phases/7/VERIFICATION.md`

Ket qua quan trong:

- Android `testDebugUnitTest` pass voi JDK 17/JBR, nhung `NO-SOURCE`.
- Android `assembleDebug` pass va tao APK debug.
- Backend `import main` va FastAPI TestClient smoke pass.
- Runtime UI da verify tren `Pixel_8_Pro_API_35` / `emulator-5554`; install, launch, main tabs, va profile subflows pass.
- Scan capture partial pass; actual detection/result/save path chua verify.
- Review flow blocked by data vi current account khong co due words.
- `best_float32.tflite` la default model va da duoc stage bang Git LFS; pending commit/push.
- Current working tree da co advanced review entry points tu `ReviewFragment` finished state; runtime click-through can due data.
- Custom label map cua model giu `ruler` vi chua co training metadata/sidecar labels.
- Focused Gallery/COCO scan rehearsal pass: detection/result/save/history/learning path verified and cleaned up.

## Phase 8 - On Dinh & San Sang Demo

**Muc tieu:** dua project tu "build-ready local" sang "demo-ready / user-test ready" bang cach xu ly P0/P1 co evidence.

### Objectives

- [x] Chot strategy cho `best_float32.tflite` de clean checkout/release khong thieu asset.
- [x] Ghi ro Java/JDK setup de Gradle khong fail vi `JAVA_HOME=C:\Java`.
- [x] Verify runtime UI tren emulator/device.
- [x] Verify/fix entry point advanced review modes.
- [x] Chot custom model labels theo model scope/training classes.
- [x] Add History Detail -> Add to Collection recovery path.
- [x] Smoke DB-backed backend/API flows voi data that.
- [x] Tao demo readiness report.

### Plans

| Plan | Trang thai | Deliverable |
|---|---|---|
| 8.1 - P0 Release Blockers | Docs complete / approval pending | `.gsd/phases/8/P0_BLOCKERS.md` |
| 8.2 - Runtime Device Verification | Complete / partial runtime gaps documented | `.gsd/phases/8/RUNTIME_VERIFICATION.md` |
| 8.3 - Learning Flow & Model Fix Plan | Complete / decisions documented | `.gsd/phases/8/LEARNING_MODEL_FIXES.md` |
| 8.4 - Demo Hardening & Final Readiness | Complete / guided demo-ready | `.gsd/phases/8/DEMO_READINESS.md` |

## Next Step

Neu muon commit phan LFS asset da stage:

```text
Theo GSD, commit staged Phase 8 P0 model asset changes.
```

Neu muon rehearse live camera that truoc demo:

```text
Theo GSD, run optional live-camera scan rehearsal.
Verify physical camera capture -> detection -> result -> save, chi ghi evidence truoc neu can sua app/backend.
```
