---
phase: 7
plan: 3
wave: 3
gap_closure: false
---

# Plan 7.3: Verify & Đánh Giá Độ Sẵn Sàng Release

## Mục Tiêu

Ghi lại mức độ tin cậy hiện tại của build/test/runtime để phase tiếp theo ưu tiên fix dựa trên dẫn chứng thật.

## Ngữ Cảnh Cần Đọc

Đọc các file này để lấy ngữ cảnh:

- `.gsd/phases/7/CURRENT_STATE.md`
- `.gsd/phases/7/GAP_ANALYSIS.md`
- `android/build.gradle.kts`
- `android/app/build.gradle.kts`
- `backend/README.md` hoặc tài liệu setup backend nếu có
- Các file dependency/config của backend

## Công Việc

<task type="auto">
  <name>Chạy verification cho Android</name>
  <files>
    android/build.gradle.kts
    android/app/build.gradle.kts
    android/app/src/main/**
  </files>
  <action>
    Chạy các command verify Android nhỏ nhất nhưng có ích, rồi ghi lại kết quả.

    Ưu tiên:
    1. Unit tests nếu đã cấu hình.
    2. Debug build nếu test không có hoặc chưa đủ.
    3. Lint/build check hẹp hơn nếu build fail.
  </action>
  <verify>
    From `android/`: `.\gradlew.bat testDebugUnitTest` and/or `.\gradlew.bat assembleDebug`
  </verify>
  <done>
    Kết quả verify Android và summary lỗi nếu có được ghi trong `.gsd/phases/7/VERIFICATION.md`.
  </done>
</task>

<task type="auto">
  <name>Chạy verification cho backend</name>
  <files>
    backend/**
  </files>
  <action>
    Kiểm tra setup backend rồi chạy command verify hẹp nhất có thể.

    Ví dụ:
    1. Unit tests nếu có.
    2. Python import check cho app startup.
    3. FastAPI route import check.
  </action>
  <verify>
    Dùng dependency manager và command được document trong backend nếu có.
  </verify>
  <done>
    Kết quả verify backend và summary lỗi nếu có được ghi trong `.gsd/phases/7/VERIFICATION.md`.
  </done>
</task>

<task type="auto">
  <name>Tạo snapshot độ sẵn sàng release</name>
  <files>
    .gsd/phases/7/CURRENT_STATE.md
    .gsd/phases/7/GAP_ANALYSIS.md
    .gsd/phases/7/VERIFICATION.md
  </files>
  <action>
    Tóm tắt project hiện sẵn sàng cho demo, user testing, hay cần một phase fix nữa.

    Bao gồm:
    1. Trạng thái verification
    2. Blocking issues
    3. Rủi ro không block
    4. Phase tiếp theo được đề xuất
  </action>
  <verify>
    Snapshot có tham chiếu command result thật và các entry trong gap analysis.
  </verify>
  <done>
    Phase 7 có đủ dẫn chứng để quyết định phase implement tiếp theo.
  </done>
</task>

## Bắt Buộc Có

- [ ] Đã thử verify Android và ghi kết quả.
- [ ] Đã thử verify backend và ghi kết quả.
- [ ] Đề xuất release-readiness có dẫn chứng.

## Tiêu Chí Thành Công

- [ ] `.gsd/phases/7/VERIFICATION.md` exists.
- [ ] Build/test blockers được liệt kê kèm summary command output.
- [ ] Phase tiếp theo được đề xuất theo priority, không đoán mò.
