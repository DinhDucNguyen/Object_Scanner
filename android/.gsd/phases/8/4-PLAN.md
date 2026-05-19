---
phase: 8
plan: 4
wave: 4
gap_closure: true
---

# Plan 8.4: Demo Hardening & Final Readiness

## Muc Tieu

Dong goi project thanh trang thai demo-ready/user-test-ready sau khi P0/P1 da duoc xu ly hoac co decision ro.

## Ngu Canh Can Doc

- `.gsd/phases/7/VERIFICATION.md`
- `.gsd/phases/8/P0_BLOCKERS.md`
- `.gsd/phases/8/RUNTIME_VERIFICATION.md`
- `.gsd/phases/8/LEARNING_MODEL_FIXES.md`
- `.gsd/SPEC.md`
- `.gsd/TODO.md`
- Backend `.env` va seed/test data docs neu co

## Cong Viec

<task type="auto">
  <name>Backend DB-backed smoke verification</name>
  <files>
    backend/app/routers/**
    backend/app/services/**
    backend/requirements.txt
  </files>
  <action>
    Verify cac endpoint can cho demo voi data that:
    - Auth/profile/settings
    - Scan by code/image neu co input
    - Review due + submit
    - Collections CRUD/review/insights
    - Analytics/stats/streak
    - Dictionary lookup/translate

    Neu khong co DB/test data, ghi blocker ro.
  </action>
  <verify>
    FastAPI TestClient hoac HTTP smoke against local backend.
  </verify>
  <done>
    Backend demo endpoints co Pass/Fail/Blocked trong `DEMO_READINESS.md`.
  </done>
</task>

<task type="auto">
  <name>Minimal automated smoke tests decision</name>
  <files>
    backend/**
    app/src/test/**
    app/src/androidTest/**
  </files>
  <action>
    Quyet dinh co them tests toi thieu khong:
    - Backend import/routes smoke.
    - Android ViewModel/API mapping test neu setup nhanh.
    - Khong them test neu lam cham demo va da co manual checklist du.
  </action>
  <verify>
    Test command tuong ung neu co.
  </verify>
  <done>
    Test coverage decision va command result duoc ghi.
  </done>
</task>

<task type="auto">
  <name>Docs cleanup cho demo scope</name>
  <files>
    .gsd/SPEC.md
    .gsd/ROADMAP.md
    .gsd/STATE.md
    .gsd/TODO.md
    .gsd/phases/8/DEMO_READINESS.md
  </files>
  <action>
    Cleanup docs chi trong GSD:
    - Admin panel: internal tool hay out-of-scope?
    - Phase 6 historical note.
    - Demo scope: flows nao se show, flows nao out-of-demo.
    - Known risks con lai.
  </action>
  <verify>
    `powershell -ExecutionPolicy Bypass -File scripts/validate-all.ps1`
  </verify>
  <done>
    Docs khong con keo nguoi doc ve roadmap cu.
  </done>
</task>

## Output

Tao `.gsd/phases/8/DEMO_READINESS.md` voi:

- Final command results.
- Runtime/demo flow checklist.
- Backend/API smoke result.
- Known residual risks.
- Decision: Demo-ready / user-test-ready / still blocked.
- Next phase neu can.

## Bat Buoc Co

- [ ] `DEMO_READINESS.md` exists.
- [ ] Backend/API demo endpoints co Pass/Fail/Blocked.
- [ ] Demo flow checklist ro.
- [ ] Roadmap/state duoc cap nhat theo ket luan.

## Tieu Chi Thanh Cong

- [ ] Co the noi ro project da demo-ready hay chua, dua tren evidence.
- [ ] Neu chua ready, blocker con lai duoc xep priority.
