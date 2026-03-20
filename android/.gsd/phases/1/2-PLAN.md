---
phase: 1
plan: 2
wave: 1
---

# Plan 1.2: Cốt lõi UI Đăng nhập & Điều hướng

## Mục tiêu
Đảm bảo các thành phần UI cho xác thực người dùng (Login và Register Fragments) hoạt động đầy đủ, quản lý trạng thái chính xác và điều hướng đúng cách dựa trên kết quả đăng nhập.

## Bối cảnh
- .gsd/SPEC.md
- app/src/main/java/com/duc/objectlanguage/ui/auth/LoginFragment.kt
- app/src/main/java/com/duc/objectlanguage/ui/auth/RegisterFragment.kt
- app/src/main/java/com/duc/objectlanguage/ui/MainActivity.kt
- app/src/main/res/navigation/nav_graph.xml

## Các nhiệm vụ

<task type="auto">
  <name>Hoàn thiện luồng giao diện Đăng nhập và Đăng ký</name>
  <files>
    - app/src/main/java/com/duc/objectlanguage/ui/auth/LoginFragment.kt
    - app/src/main/java/com/duc/objectlanguage/ui/auth/RegisterFragment.kt
    - app/src/main/java/com/duc/objectlanguage/ui/MainActivity.kt
  </files>
  <action>
    - Xem xét lại `LoginFragment` và `RegisterFragment` để đảm bảo các trường hợp biên (nhập rỗng, lỗi mạng) được xử lý mượt mà (ví dụ: hiển thị Toast hoặc SnackBar).
    - Đảm bảo `MainActivity` xác định đúng màn hình bắt đầu. Nếu token đã có trong `TokenManager`, bỏ qua luồng Auth và chuyển thẳng đến Dashboard.
    - Tránh thay đổi ID của file layout nếu không cần thiết, để phòng tránh lỗi biên dịch với ViewBinding.
  </action>
  <verify>grep -rn "TokenManager" app/src/main/java/com/duc/objectlanguage/ui/MainActivity.kt</verify>
  <done>Ứng dụng bỏ qua Đăng nhập nếu đã xác thực, và Đăng nhập/Đăng ký xử lý tốt input và trạng thái mạng.</done>
</task>

## Tiêu chí thành công
- [ ] `MainActivity` có logic bỏ qua màn hình đăng nhập nếu token tồn tại.
- [ ] `LoginFragment` hiển thị đúng trạng thái loading và lỗi.
- [ ] `RegisterFragment` hiển thị đúng trạng thái loading và lỗi.
