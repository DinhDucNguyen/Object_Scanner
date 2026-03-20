---
phase: 4
plan: 1
wave: 1
---

# Plan 4.1: SM-2 Review Data Fetching & Result Submission

## Objective
Kiểm tra và hoàn thiện chức năng ôn tập từ vựng dựa trên thuật toán SM-2. Đảm bảo ứng dụng có thể kết nối với Backend API để tải danh sách các thẻ cần ôn (Due Reviews) và gửi kết quả đánh giá (Quality) thành công.

## Context
- .gsd/SPEC.md
- app/src/main/java/com/duc/objectlanguage/ui/review/ReviewFragment.kt
- app/src/main/java/com/duc/objectlanguage/ui/review/ReviewViewModel.kt

## Tasks

<task type="auto">
  <name>Hoàn thiện luồng API Ôn tập</name>
  <files>
    - app/src/main/java/com/duc/objectlanguage/ui/review/ReviewFragment.kt
    - app/src/main/java/com/duc/objectlanguage/ui/review/ReviewViewModel.kt
  </files>
  <action>
    - Trong `ReviewViewModel.loadCards()`, đảm bảo trạng thái Loading được cập nhật chuẩn xác. Xử lý trường hợp API trả về mảng rỗng (không có thẻ nào cần học) bằng cách set `_finished.value = true`.
    - Trong `ReviewViewModel.submitAnswer(quality)`, đảm bảo gọi `repo.submitReview`. Khi submit thành công, hệ thống chuyển sang thẻ tiếp theo (`currentIndex + 1`) hoặc hiển thị màn hình hoàn thành.
    - Cập nhật hiển thị Toast thông báo khi có lỗi mạng để user biết quá trình submit thất bại thay vì kẹt lại.
  </action>
  <verify>grep -rn "submitReview" app/src/main/java/com/duc/objectlanguage/ui/review/ReviewViewModel.kt</verify>
  <done>ViewModel tải thẻ và gửi kết quả review đúng cách qua Repo. Chuyển thẻ mượt mà.</done>
</task>

## Success Criteria
- [ ] Màn hình danh sách ôn tập hiển thị thẻ từ hợp lệ.
- [ ] Chọn chất lượng (Again, Hard, Good, Easy) gửi request thành công và chuyển ngay qua thẻ kế tiếp.
