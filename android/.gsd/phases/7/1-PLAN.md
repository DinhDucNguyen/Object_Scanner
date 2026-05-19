---
phase: 7
plan: 1
wave: 1
gap_closure: false
---

# Plan 7.1: Kiểm Kê Codebase Hiện Tại

## Mục Tiêu

Tạo một bức tranh tổng quan dựa trên code hiện tại của Android và backend, để các việc tiếp theo bắt đầu từ thực tế chứ không dựa vào ghi chú phase cũ.

## Ngữ Cảnh Cần Đọc

Đọc các file này để lấy ngữ cảnh:

- `.gsd/STATE.md`
- `.gsd/SPEC.md`
- `.gsd/ROADMAP.md`
- `PHASE6_COMPLETE.md`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/navigation/nav_graph.xml`
- `app/build.gradle.kts`
- Backend app entrypoint

Với các thư mục lớn, search trước rồi mới đọc file cụ thể:

- `android/app/src/main/java/com/duc/objectlanguage/ui`
- `android/app/src/main/java/com/duc/objectlanguage/data`
- `android/app/src/main/java/com/duc/objectlanguage/workers`
- `backend/app/routers`
- `backend/app/services`
- `backend/app/models`

## Công Việc

<task type="auto">
  <name>Kiểm kê các màn hình Android người dùng nhìn thấy</name>
  <files>
    app/src/main/java/com/duc/objectlanguage/ui/**
    app/src/main/res/layout/**
    app/src/main/res/navigation/nav_graph.xml
  </files>
  <action>
    Map từng screen, Fragment, ViewModel, adapter, và layout chính với chức năng mà người dùng thấy.

    Ghi lại:
    1. Nhóm feature
    2. File chính
    3. Entry trong navigation
    4. Data dependency
    5. Điểm chưa chắc chắn hoặc tài liệu đã cũ
  </action>
  <verify>
    `rg -n "class .*Fragment|class .*ViewModel|fragment" app/src/main/java app/src/main/res/navigation/nav_graph.xml`
  </verify>
  <done>
    Feature map của Android được ghi trong `.gsd/phases/7/CURRENT_STATE.md`.
  </done>
</task>

<task type="auto">
  <name>Kiểm kê data layer, ML, và background system của Android</name>
  <files>
    app/src/main/java/com/duc/objectlanguage/data/**
    app/src/main/java/com/duc/objectlanguage/utils/**
    app/src/main/java/com/duc/objectlanguage/workers/**
    app/src/main/assets/**
  </files>
  <action>
    Map API services, repositories, local storage, notification/streak workers, audio helpers, và ML model assets.

    Ghi chú các vấn đề như client bị duplicate, rủi ro sync state local/server, endpoint hardcoded, kích thước asset, và rủi ro runtime.
  </action>
  <verify>
    `rg -n "interface .*Api|class .*Repository|class .*Worker|DataStore|SharedPreferences|tflite|Retrofit" app/src/main/java app/src/main/assets`
  </verify>
  <done>
    Kiểm kê data, ML, và worker được ghi trong `.gsd/phases/7/CURRENT_STATE.md`.
  </done>
</task>

<task type="auto">
  <name>Kiểm kê API backend và domain system</name>
  <files>
    backend/app/routers/**
    backend/app/services/**
    backend/app/repositories/**
    backend/app/models/**
    backend/app/schemas/**
  </files>
  <action>
    Map routers với services, repositories, models, và Android consumers nếu thấy được.

    Ghi lại:
    1. Nhóm endpoint
    2. Trách nhiệm chính
    3. Yêu cầu authentication
    4. Android client đã cover chưa
    5. Điểm chưa chắc hoặc mismatch
  </action>
  <verify>
    `rg -n "@router\\.|APIRouter|class .*Service|class .*Repository|class .*Base" backend/app`
  </verify>
  <done>
    Backend API/domain map được ghi trong `.gsd/phases/7/CURRENT_STATE.md`.
  </done>
</task>

## Bắt Buộc Có

- [ ] Đã map Android screens và navigation.
- [ ] Đã map Android data/API/local/worker systems.
- [ ] Đã map backend endpoints và domain services.
- [ ] Kiểm kê feature hiện tại có dẫn chứng file path.

## Tiêu Chí Thành Công

- [ ] `.gsd/phases/7/CURRENT_STATE.md` exists.
- [ ] Mỗi feature lớn hiện có đều có code path tham chiếu.
- [ ] Các điểm chưa chắc được ghi rõ, không đoán mò.
