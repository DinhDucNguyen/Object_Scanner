---
phase: 5
plan: 1
wave: 1
---

# Plan 5.1: UI/UX Polish & Error Handling

## Objective
Hoàn thiện giao diện người dùng (UI), bổ sung các trạng thái loading, error toast và đảm bảo điều hướng gián đoạn (Navigation) hoạt động mượt mà.

## Context
- app/src/main/java/com/duc/objectlanguage/ui/MainActivity.kt
- app/src/main/res/layout/activity_main.xml

## Tasks

<task type="auto">
  <name>Kiểm tra UI toàn cục và xử lý lỗi</name>
  <files>
    - app/src/main/java/com/duc/objectlanguage/ui/MainActivity.kt
  </files>
  <action>
    - Xác minh Bottom Navigation bar tự động ẩn/hiện chính xác khi chuyển qua lại giữa các màn hình Auth (Login/Register) và các màn hình chính (Dashboard, Scan, Review).
    - Đảm bảo `MainActivity` kiểm tra `tokenManager.isLoggedIn` chính xác để chặn truy cập trái phép.
    - Đề xuất: Đảm bảo các Repository/ViewModel đều catch Exception và đẩy message lỗi ra LiveData dể UI hiển thị `Toast` mạch lạc. (Được verify gián tiếp qua code structure).
  </action>
  <verify>grep -rn "bottomNavigation.visibility" app/src/main/java/com/duc/objectlanguage/ui/MainActivity.kt</verify>
  <done>Bottom Navigation ẩn hiện đúng. Auth Guard trong MainActivity hoạt động tốt.</done>
</task>

## Success Criteria
- [ ] Màn hình điều hướng (Bottom Nav) hoạt động đúng lúc có/không có Auth.
- [ ] Không có màn hình nào bị crash lặng lẽ khi mất mạng (dựa trên Result wrappers).
