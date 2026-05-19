---
phase: 7
plan: 2
wave: 2
gap_closure: false
---

# Plan 7.2: Phân Tích Khoảng Trống & Reset Roadmap

## Mục Tiêu

So sánh implementation hiện tại với tài liệu GSD, tìm các giả định đã cũ, rồi reset roadmap để phase implement tiếp theo rõ ràng hơn.

## Ngữ Cảnh Cần Đọc

Đọc các file này để lấy ngữ cảnh:

- `.gsd/phases/7/CURRENT_STATE.md`
- `.gsd/SPEC.md`
- `.gsd/ROADMAP.md`
- `.gsd/ARCHITECTURE.md`
- `.gsd/STACK.md`
- `.gsd/TODO.md`
- `PHASE6_COMPLETE.md`

## Công Việc

<task type="auto">
  <name>Đối chiếu trạng thái Phase 6 với code thực tế</name>
  <files>
    PHASE6_COMPLETE.md
    .gsd/ROADMAP.md
    .gsd/phases/6/**
    .gsd/phases/7/CURRENT_STATE.md
  </files>
  <action>
    Tạo matrix so sánh các claim của Phase 6 với file hiện tại và trạng thái verify.

    Phân loại từng claim:
    - Đã implement và đã verify
    - Đã implement nhưng chưa verify
    - Implement một phần
    - Chưa có
    - Đã cũ hoặc bị thay thế
  </action>
  <verify>
    Cross-check thủ công với code paths đã liệt kê trong CURRENT_STATE.md.
  </verify>
  <done>
    Kết quả đối chiếu Phase 6 được ghi trong `.gsd/phases/7/GAP_ANALYSIS.md`.
  </done>
</task>

<task type="auto">
  <name>Xếp hạng gap và rủi ro hiện tại</name>
  <files>
    .gsd/phases/7/CURRENT_STATE.md
    app/src/main/**
    backend/app/**
  </files>
  <action>
    Tìm các gap có impact lớn nhất về UX, correctness, reliability, build health, API compatibility, và documentation drift.

    Mỗi item cần có:
    1. Mức độ nghiêm trọng
    2. Dẫn chứng
    3. Impact
    4. Bước tiếp theo nên làm
  </action>
  <verify>
    Mỗi item severity cao có ít nhất một file path hoặc command result làm dẫn chứng.
  </verify>
  <done>
    Danh sách gap/rủi ro đã xếp hạng nằm trong `.gsd/phases/7/GAP_ANALYSIS.md`.
  </done>
</task>

<task type="auto">
  <name>Reset hướng roadmap</name>
  <files>
    .gsd/ROADMAP.md
    .gsd/STATE.md
    .gsd/ARCHITECTURE.md
    .gsd/STACK.md
  </files>
  <action>
    Chỉ cập nhật GSD docs sau khi inventory và gap analysis đã xong.

    Việc reset cần:
    1. Ghi rõ phase nào là lịch sử.
    2. Ghi lại findings của Phase 7.
    3. Đề xuất phase implement tiếp theo theo priority.
    4. Không đánh dấu việc chưa verify là đã hoàn tất.
  </action>
  <verify>
    `powershell -ExecutionPolicy Bypass -File scripts/validate-all.ps1`
  </verify>
  <done>
    Roadmap và state trỏ tới phase tiếp theo có dẫn chứng rõ.
  </done>
</task>

## Bắt Buộc Có

- [ ] Các claim Phase 6 đã được đối chiếu với code.
- [ ] Gap hiện tại được xếp hạng và có dẫn chứng.
- [ ] Roadmap có đề xuất rõ cho phase tiếp theo.

## Tiêu Chí Thành Công

- [ ] `.gsd/phases/7/GAP_ANALYSIS.md` exists.
- [ ] `.gsd/ROADMAP.md` không còn coi planning note cũ là hiện trạng thật.
- [ ] `.gsd/STATE.md` nói rõ session sau cần làm gì.
