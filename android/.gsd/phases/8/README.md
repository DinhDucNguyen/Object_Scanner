# Phase 8: On Dinh & San Sang Demo

> **Trang thai**: Plans 8.1-8.4 executed/verified; Gallery/COCO scan pass; guided demo-ready with residual risks  
> **Ngay tao**: 2026-05-19  
> **Nguon**: `.gsd/phases/7/VERIFICATION.md`  
> **Muc dich**: dua project tu "build-ready local" sang "demo-ready / user-test ready" bang cach xu ly P0/P1 co evidence.

## Boi Canh

Phase 7 da rebaseline project va tao verification snapshot. Ket qua tot la Android debug build pass va backend import/smoke pass. Diem chua tot la project chua release-ready:

- Khong co emulator/device attached nen runtime UI chua verify.
- `best_float32.tflite` la model mac dinh nhung dang untracked.
- Advanced review modes co destination trong nav graph nhung static search chua thay entry point tu UI chinh.
- Custom model label map hien chi co `ruler`.
- Backend DB-backed endpoints da duoc smoke voi real `GSD_E2E` test data va cleanup.
- Gallery -> COCO -> detection -> result -> save was verified on emulator and cleaned up.

Phase 8 khong nen them feature lon. Phase nay la on dinh, chot asset/model, verify runtime, va lam demo flow dung duoc.

## Nguyen Tac

- Uu tien P0/P1 trong `VERIFICATION.md`.
- Moi thay doi app/backend phai co ly do gan voi blocker/risk da ghi.
- Khong refactor rong khi chua can.
- Sau moi plan co code changes, phai chay verify phu hop va cap nhat docs ket qua.
- Neu user chi yeu cau docs/verify, khong sua source app/backend.

## Cac Plan

| Plan | Ten | Muc tieu | Output |
|---|---|---|---|
| 8.1 | P0 Release Blockers | Chot model asset strategy, dev Java env note, va checklist runtime device | `P0_BLOCKERS.md` |
| 8.2 | Runtime Device Verification | Chay app tren emulator/device va verify main flows | `RUNTIME_VERIFICATION.md` |
| 8.3 | Learning Flow & Model Fix Plan | Xu ly review mode reachability, custom model labels, va seeded advanced review verification | `LEARNING_MODEL_FIXES.md`, `ADVANCED_REVIEW_SEED_VERIFICATION.md` |
| 8.4 | Demo Hardening & Final Readiness | Verify DB-backed APIs, smoke tests, docs cleanup, demo checklist | `DEMO_READINESS.md` |

## Uu Tien

1. **Plan 8.1** truoc: khong co asset strategy va environment ro thi clean checkout/release van rui ro.
2. **Plan 8.2** tiep theo: runtime UI la bang chung quan trong nhat cho demo.
3. **Plan 8.3** sau khi biet app chay duoc tren device: sua entry point review modes va model labels neu can.
4. **Plan 8.4** cuoi: dong goi demo readiness, test toi thieu, va cleanup docs.

## Dieu Kien Hoan Thanh Phase 8

- Debug build pass tu clean setup co model strategy ro.
- Runtime UI smoke pass tren it nhat mot emulator/device.
- Demo flow pass for guided Gallery/COCO scan -> result -> save/learn -> review -> analytics/streak.
- Review modes nang cao co entry point ro hoac duoc ghi la out-of-demo scope.
- Model labels khop voi custom model scope.
- Backend DB-backed endpoints chinh duoc smoke voi data that.
- `DEMO_READINESS.md` ket luan ro: guided demo-ready, nhung chua full release-ready.

## Prompt Dieu Khien

Bat dau bang docs/verify nhe:

```text
Theo GSD, execute Phase 8 Plan 8.1.
Chi xu ly P0 release blockers theo docs, neu can sua app/backend thi bao evidence truoc.
```

Neu muon chi verify runtime:

```text
Theo GSD, execute Phase 8 Plan 8.2.
Chay app tren emulator/device va tao RUNTIME_VERIFICATION.md, chua sua code app/backend.
```
