---
phase: 8
plan: 1
wave: 1
gap_closure: true
---

# Plan 8.1: P0 Release Blockers

## Muc Tieu

Xu ly hoac chot cach xu ly cac P0 blocker trong `.gsd/phases/7/VERIFICATION.md` truoc khi test runtime rong hon:

1. `best_float32.tflite` la model mac dinh nhung dang untracked.
2. Runtime UI chua verify vi chua co emulator/device.
3. Default shell `JAVA_HOME=C:\Java` sai, Gradle chi pass khi dung JBR 17 tam.

Plan nay nen bat dau bang docs/checklist. Chi sua app/backend neu user cho phep ro.

## Ngu Canh Can Doc

- `.gsd/phases/7/VERIFICATION.md`
- `.gsd/phases/7/GAP_ANALYSIS.md`
- `app/src/main/java/com/duc/objectlanguage/ui/scan/ObjectDetectorHelper.kt`
- `app/src/main/assets/**`
- `android/local.properties.example`
- `android/app/build.gradle.kts`
- `android/.gitignore`

## Cong Viec

<task type="auto">
  <name>Chot chien luoc asset cho `best_float32.tflite`</name>
  <files>
    app/src/main/assets/best_float32.tflite
    app/src/main/assets/yolov10n_int8.tflite
    app/src/main/java/com/duc/objectlanguage/ui/scan/ObjectDetectorHelper.kt
    .gitignore
  </files>
  <action>
    Xac dinh cach dam bao clean checkout/release khong thieu model mac dinh.

    Lua chon chap nhan duoc:
    1. Track `best_float32.tflite` trong git neu size/repo policy cho phep.
    2. Dung Git LFS neu repo policy can.
    3. Giu untracked nhung them setup docs/script tai model va fallback ro.

    Ghi quyet dinh, evidence size, va action can lam vao `.gsd/phases/8/P0_BLOCKERS.md`.
  </action>
  <verify>
    `git -C android status --short -- app/src/main/assets/best_float32.tflite app/src/main/assets/yolov10n_int8.tflite`
    `tar -tf app/build/outputs/apk/debug/app-debug.apk | Select-String -Pattern '\.tflite$'`
  </verify>
  <done>
    Asset strategy ro rang, co next action cu the, khong con mo ho ve clean checkout.
  </done>
</task>

<task type="auto">
  <name>Chot Java/Gradle environment note</name>
  <files>
    .gsd/STACK.md
    .gsd/phases/8/P0_BLOCKERS.md
    local.properties.example
  </files>
  <action>
    Ghi lai rang Gradle fail neu `JAVA_HOME=C:\Java`, nhung pass voi Android Studio JBR 17.

    Neu chi duoc sua docs, cap nhat docs setup.
    Neu duoc sua environment/project helper, de xuat cach lam rieng va xin phep truoc.
  </action>
  <verify>
    `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat assembleDebug --console=plain`
  </verify>
  <done>
    Developer moi biet dung JDK nao de build.
  </done>
</task>

<task type="manual">
  <name>Chuan bi runtime device checklist</name>
  <files>
    .gsd/phases/8/P0_BLOCKERS.md
    .gsd/phases/8/2-PLAN.md
  </files>
  <action>
    Ghi checklist can co truoc Plan 8.2:
    - Co emulator/device hien trong `adb devices`.
    - Backend dang chay dung IP/port trong `local.properties`.
    - Co account/test data neu can login/review.
    - Camera/emulator image input strategy.
  </action>
  <verify>
    `adb devices`
  </verify>
  <done>
    Plan 8.2 co dieu kien bat dau ro.
  </done>
</task>

## Bat Buoc Co

- [ ] `P0_BLOCKERS.md` exists.
- [ ] Co quyet dinh hoac recommended decision cho `best_float32.tflite`.
- [ ] Co note environment ve JDK/JAVA_HOME.
- [ ] Co checklist de chay runtime device verification.

## Tieu Chi Thanh Cong

- [ ] P0 blockers khong con mo ho ve ownership/next action.
- [ ] Neu co code changes, Android build pass lai.
- [ ] Neu khong sua code, docs ghi ro viec nao can user cho phep.
