---
phase: 2
plan: 2
wave: 2
---

# Plan 2.2: ML Kit & Gemini Fallback Integration Mocks

## Objective
Hiện tại `ScanViewModel` đang gọi trực tiếp `repo.scanByImage(imageBytes)` (API Backend). Theo REQUIREMENT-02, chúng ta cần cơ chế ưu tiên: ML Kit Object Detection on-device -> nếu fail/không nhận diện được -> fallback sang Gemini API (hoặc backend API). Cần điều chỉnh ViewModel / Repository để chuẩn bị luồng này. 

*(Ghi chú: Việc tích hợp MLKit local model và Gemini API đầy đủ có thể cần cài thêm thư viện/key. Plan này tập trung vào kiến trúc luồng xử lý (logic flow) trong ViewModel/Repository trước).*

## Context
- .gsd/REQUIREMENTS.md
- app/src/main/java/com/duc/objectlanguage/ui/scan/ScanViewModel.kt
- app/src/main/java/com/duc/objectlanguage/data/repository/AppRepository.kt

## Tasks

<task type="auto">
  <name>Điều chỉnh luồng nhận diện ưu tiên ML Kit</name>
  <files>
    - app/src/main/java/com/duc/objectlanguage/ui/scan/ScanViewModel.kt
  </files>
  <action>
    - Trong `scanImage(imageBytes)`, thêm TODO comment hoạch định logic: 
       1. Khởi chạy ML Kit Object Detection (local).
       2. Nếu local model nhận diện thành công -> Mapping nhãn -> Gửi kết quả lên UI.
       3. Nếu thất bại / không chắc chắn -> Gọi `repo.scanByImage(imageBytes)` để backend xử lý (nơi tích hợp Gemini hoặc YOLOv8 chính).
    - Hiện tại, vì backend API đã có sẵn endpoint này, đảm bảo ViewModel vẫn gọi API như một luồng fallback hoàn chỉnh trong khi chờ tích hợp ML Kit thực tế.
  </action>
  <verify>grep -rn "TODO: ML Kit Object Detection" app/src/main/java/com/duc/objectlanguage/ui/scan/ScanViewModel.kt</verify>
  <done>ViewModel được thiết kế để hỗ trợ luồng nhận diện đa tầng (Local -> Cloud) thay vì chỉ gọi API trực tiếp ngay bước đầu.</done>
</task>

## Success Criteria
- [ ] `ScanViewModel` có comments định tuyến rõ ràng cho ML Kit và Fallback API.
- [ ] Luồng loading UI được cập nhật đúng để hỗ trợ trạng thái quét dài hơn (nếu cả 2 luồng đều kích hoạt).
