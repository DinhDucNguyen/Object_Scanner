---
phase: 5
plan: 2
wave: 2
---

# Plan 5.2: End-to-End Testing

## Objective
Kiểm tra luồng ứng dụng End-to-End (E2E) từ khi Đăng ký -> Đăng nhập -> Quét vật thể -> Xem từ vựng -> Lưu từ vựng -> Ôn tập.

## Context
- Toàn bộ app.
- .gsd/ROADMAP.md

## Tasks

<task type="auto">
  <name>Verify the Complete Developer Flow (E2E Logic Check)</name>
  <files>
    - Tự động check chéo các Modules.
  </files>
  <action>
    - Đảm bảo tất cả các fragment (`DashboardFragment`, `ScanFragment`, `ReviewFragment`, `LoginFragment`, `RegisterFragment`) đã được kết nối vào `nav_graph.xml` và ViewModel đều xử lý đúng LiveData.
    - Đảm bảo không có TODO block quan trọng nào (như `// TODO: Implement...`) bị sót lại trong pipeline cốt lõi (Camera -> API -> DB).
  </action>
  <verify>grep -rn "TODO" app/src/main/java/com/duc/objectlanguage</verify>
  <done>Toàn bộ luồng ViewModel - API Repositories liền mạch, không còn logic giả lập nào sót.</done>
</task>

## Success Criteria
- [ ] Quét codebase không thấy logic giả (Mock) thay vì API call (trừ ML Kit dự phòng).
- [ ] Đạt Milestone v1.0 (Graduation Project version).
