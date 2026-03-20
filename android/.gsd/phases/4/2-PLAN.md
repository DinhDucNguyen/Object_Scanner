---
phase: 4
plan: 2
wave: 2
---

# Plan 4.2: Review UI Edge Cases & Details

## Objective
Cải thiện trải nghiệm UI/UX cho màn hình Ôn tập (Review). Xử lý các trạng thái biên như: không có thẻ nào để ôn, hiển thị chi tiết thẻ (IPA, ví dụ), nút lật thẻ.

## Context
- app/src/main/java/com/duc/objectlanguage/ui/review/ReviewFragment.kt

## Tasks

<task type="auto">
  <name>Kiểm tra UI và trạng thái biên của màn hình Review</name>
  <files>
    - app/src/main/java/com/duc/objectlanguage/ui/review/ReviewFragment.kt
  </files>
  <action>
    - Đảm bảo thẻ chỉ hiển thị câu hỏi (Object/Word Name) và ẩn câu trả lời, IPA, Definition cho tới khi người dùng ấn nút "Xem" (`btnReveal`).
    - Khi ấn nút "Xem", hiển thị row chứa 4 nút đánh giá chất lượng (Again, Hard, Good, Easy) và hiện câu trả lời đầy đủ.
    - Khi đánh giá xong hoặc không có thẻ nào từ đầu, hiển thị UI "Bạn đã hoàn thành" (`tvFinished`) thay vì màn hình trống.
  </action>
  <verify>grep -rn "btnReveal" app/src/main/java/com/duc/objectlanguage/ui/review/ReviewFragment.kt</verify>
  <done>Giao diện review ẩn/hiện đáp án chính xác và trang thái hoàn thành (Zero inbox) hiển thị tốt.</done>
</task>

## Success Criteria
- [ ] Cơ chế lật thẻ hoạt động đúng (chỉ thấy câu hỏi trước).
- [ ] Màn hình "Hoàn thành" hiện ra khi học xong tất cả thẻ.
