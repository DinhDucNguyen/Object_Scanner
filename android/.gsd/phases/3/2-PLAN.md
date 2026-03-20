---
phase: 3
plan: 2
wave: 2
---

# Plan 3.2: Vocabulary Collection Integration

## Objective
Cho phép người dùng lưu (favorite) các từ vựng họ vừa quét được vào bộ sưu tập cá nhân (để phục vụ cho tính năng Spaced Repetition ở Phase sau).

## Context
- .gsd/SPEC.md
- app/src/main/java/com/duc/objectlanguage/ui/scan/ScanFragment.kt

## Tasks

<task type="auto">
  <name>Kiểm tra và hoàn thiện chức năng Thêm vào học tập (Add to Learning)</name>
  <files>
    - app/src/main/java/com/duc/objectlanguage/ui/scan/ScanFragment.kt
    - app/src/main/java/com/duc/objectlanguage/ui/scan/ScanViewModel.kt
  </files>
  <action>
    - Xác nhận `btnAddLearn` trong `ScanFragment` đã được gán sự kiện click gọi đến `viewModel.addToLearning(first.id)`.
    - Đảm bảo `ScanViewModel.addToLearning` gọi `repo.addToLearning(translationId)` thành công và phản hồi lại UI (ví dụ thông qua Toast qua biến `addedMsg`).
    - Tránh việc thay đổi kiến trúc kết nối API, chỉ verify luồng hoạt động ổn định.
  </action>
  <verify>grep -rn "viewModel.addToLearning" app/src/main/java/com/duc/objectlanguage/ui/scan/ScanFragment.kt</verify>
  <done>Khi bấm nút lưu, từ vựng được gửi lên API để thêm vào danh sách học, user nhận được thông báo phản hồi.</done>
</task>

## Success Criteria
- [ ] Nút "Thêm vào danh sách học" hoạt động trơn tru.
- [ ] Ứng dụng hiển thị Toast thông báo khi lưu thành công hoặc thất bại.
