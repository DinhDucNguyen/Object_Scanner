---
phase: 2
plan: 1
wave: 1
---

# Plan 2.1: CameraX Integration & Image Capture

## Objective
Hoàn thiện tính năng luồng quét thông qua CameraX. Đảm bảo CameraX được khởi tạo chính xác, hiển thị luồng live preview tốt, và tính năng chụp ảnh (ImageCapture) tạo ra byte array chuẩn xác từ khung hình hiện tại để chuẩn bị gửi lên server hoặc ML Kit.

## Context
- .gsd/SPEC.md
- app/src/main/java/com/duc/objectlanguage/ui/scan/ScanFragment.kt

## Tasks

<task type="auto">
  <name>Hoàn thiện CameraX Preview và ImageCapture</name>
  <files>
    - app/src/main/java/com/duc/objectlanguage/ui/scan/ScanFragment.kt
  </files>
  <action>
    - Kiểm tra và đảm bảo hàm `startCamera()` khởi tạo `ProcessCameraProvider` và bind đúng Lifecycle.
    - Đảm bảo `Preview.Builder()` kết nối với `binding.previewView.surfaceProvider`.
    - Đảm bảo `ImageCapture` được cấu hình để chụp ảnh với độ trễ thấp (MINIMIZE_LATENCY).
    - Trong hàm `captureImage()`, xác nhận rằng mảng byte thu được từ `ImageProxy` được luân chuyển an toàn sang `viewModel.scanImage(bytes)`. Xử lý giải phóng (close) `image` sau khi dùng xong để tránh memory leak.
  </action>
  <verify>grep -rn "captureImage" app/src/main/java/com/duc/objectlanguage/ui/scan/ScanFragment.kt</verify>
  <done>Camera preview hiển thị mượt mà và nút chụp gửi thành công mảng byte ảnh tới ViewModel mà không gây crash hay leak.</done>
</task>

## Success Criteria
- [ ] Màn hình quét (Scan) yêu cầu quyền Camera thành công.
- [ ] Camera preview hiển thị đúng trên UI.
- [ ] Tính năng chụp ảnh không gây lỗi memory leak.
