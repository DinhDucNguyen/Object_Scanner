---
phase: 3
plan: 1
wave: 1
---

# Plan 3.1: Vocabulary Presentation (IPA, Audio, Examples)

## Objective
Trong Phase này, ứng dụng cần hiển thị chi tiết từ vựng được nhận diện từ Camera. Cụ thể là bản dịch tiếng Việt, phiên âm (IPA), nút phát âm (Audio TTS) và các câu ví dụ.

## Context
- .gsd/SPEC.md
- app/src/main/java/com/duc/objectlanguage/ui/scan/ScanFragment.kt
- app/src/main/java/com/duc/objectlanguage/data/model/Responses.kt

## Tasks

<task type="auto">
  <name>Kiểm tra và hoàn thiện hiển thị từ vựng trên ScanFragment</name>
  <files>
    - app/src/main/java/com/duc/objectlanguage/ui/scan/ScanFragment.kt
  </files>
  <action>
    - Kiểm tra logic trong `viewModel.scanResult.observe` của `ScanFragment`. Đảm bảo UI đã trích xuất đúng `phonetic` và `definition` từ danh sách `translations`.
    - Kiểm tra nút phát âm `btnPlayAudio`: Đảm bảo nó gọi đúng `viewModel.playAudio()` và truyền đúng ngôn ngữ. Hiện tại audio đang được lấy qua API TTS của backend, cần đảm bảo không có cảnh báo lỗi UI khi đang tải.
    - Kiểm tra hiển thị ví dụ `tvExamples`: Đảm bảo `viewModel.examples.observe` nối chuỗi danh sách các câu ví dụ đúng định dạng (có đánh số thứ tự).
  </action>
  <verify>grep -rn "tvTranslations.text =" app/src/main/java/com/duc/objectlanguage/ui/scan/ScanFragment.kt</verify>
  <done>Giao diện ScanFragment hiển thị đầy đủ thông tin từ vựng, phiên âm, định nghĩa và Audio phát bình thường.</done>
</task>

## Success Criteria
- [ ] Thông tin từ vựng (tên, bản dịch, IPA, ví dụ) hiện lên mượt mà sau khi AI nhận diện.
- [ ] Tính năng Audio TTS hoạt động không crash.
