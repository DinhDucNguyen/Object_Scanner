---
phase: 1
plan: 1
wave: 1
---

# Plan 1.1: Cốt lõi Xác thực & API Client

## Mục tiêu
Thiết lập các thành phần nền tảng cho mạng và xác thực người dùng. Công việc bao gồm tinh chỉnh Retrofit client, điều chỉnh `TokenManager` (nếu cần) và đảm bảo `AppRepository` xử lý đúng trạng thái xác thực cũng như việc cấp mới token (refresh token).

## Bối cảnh
- .gsd/SPEC.md
- .gsd/ARCHITECTURE.md
- app/src/main/java/com/duc/objectlanguage/data/api/ApiService.kt
- app/src/main/java/com/duc/objectlanguage/data/api/RetrofitClient.kt
- app/src/main/java/com/duc/objectlanguage/data/local/TokenManager.kt
- app/src/main/java/com/duc/objectlanguage/data/repository/AppRepository.kt

## Các nhiệm vụ

<task type="auto">
  <name>Tinh chỉnh Network & Repository Layer</name>
  <files>
    - app/src/main/java/com/duc/objectlanguage/data/api/RetrofitClient.kt
    - app/src/main/java/com/duc/objectlanguage/data/local/TokenManager.kt
    - app/src/main/java/com/duc/objectlanguage/data/repository/AppRepository.kt
  </files>
  <action>
    - Xem xét phần cài đặt `RetrofitClient` hiện tại. Đảm bảo `AuthInterceptor` gắn đúng Bearer token được lấy từ `TokenManager`.
    - Đảm bảo `TokenManager` đọc/ghi `accessToken` và `refreshToken` một cách an toàn.
    - Sắp xếp để `AppRepository` hỗ trợ xử lý đúng luồng login và register, cũng như lưu token vào `TokenManager` khi thành công.
    - Tránh thay đổi tên của các phương thức có sẵn trong `ApiService` vì UI có thể đang gọi đến chúng.
  </action>
  <verify>grep -rn "AuthInterceptor" app/src/main/java/com/duc/objectlanguage/data/api/RetrofitClient.kt</verify>
  <done>Retrofit client gắn token thành công và Repository lưu được token sau khi đăng nhập.</done>
</task>

## Tiêu chí thành công
- [ ] Tầng Network được cấu hình đúng với interceptors.
- [ ] Cơ chế lưu trữ Token hoạt động ổn định (SharedPreferences hoặc DataStore).
- [ ] Hàm login/register của `AppRepository` trả về wrapper Result thay vì throw Exception.
