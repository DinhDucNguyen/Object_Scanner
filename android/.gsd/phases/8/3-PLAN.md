---
phase: 8
plan: 3
wave: 3
gap_closure: true
---

# Plan 8.3: Learning Flow & Model Fix Plan

## Muc Tieu

Xu ly cac P1 blockers da co evidence:

1. Advanced review modes co destination nhung co the khong reachable tu UI chinh.
2. Custom model label map chi co `ruler`, can khop voi model scope/training classes.

Plan nay co the can sua app code, nhung chi lam khi user cho phep execute/fix.

## Ngu Canh Can Doc

- `.gsd/phases/7/VERIFICATION.md`
- `.gsd/phases/8/RUNTIME_VERIFICATION.md` neu da co
- `app/src/main/res/navigation/nav_graph.xml`
- `app/src/main/java/com/duc/objectlanguage/ui/review/**`
- `app/src/main/res/layout/fragment_review.xml`
- `app/src/main/java/com/duc/objectlanguage/ui/scan/ObjectDetectorHelper.kt`
- `app/src/main/assets/**`

## Cong Viec

<task type="auto">
  <name>Review mode reachability decision</name>
  <files>
    app/src/main/java/com/duc/objectlanguage/ui/review/**
    app/src/main/res/navigation/nav_graph.xml
    app/src/main/res/layout/**
  </files>
  <action>
    Xac dinh UI nen vao quiz, typing, listening, image matching, pronunciation tu dau.

    Lua chon:
    1. Them mode selector trong `ReviewFragment` hoac review entry screen.
    2. Them entry cards tren Dashboard/Profile/Review.
    3. Ghi cac mode ngoai demo scope neu khong can demo.

    Neu user cho phep sua code, implement path nho nhat va verify navigation.
  </action>
  <verify>
    `rg -n "quizFragment|typingTestFragment|listeningTestFragment|imageMatchingFragment|pronunciationFragment|navigate\\(" app/src/main/java app/src/main/res`
    Android build va runtime navigation check neu co device.
  </verify>
  <done>
    Advanced review modes co entry point ro, hoac docs ghi ro out-of-scope.
  </done>
</task>

<task type="auto">
  <name>Custom model label map decision</name>
  <files>
    app/src/main/java/com/duc/objectlanguage/ui/scan/ObjectDetectorHelper.kt
    app/src/main/assets/best_float32.tflite
  </files>
  <action>
    Xac dinh `best_float32.tflite` la model 1 class hay nhieu class.

    Neu chi co `ruler`, ghi ro scope "custom model ruler-only".
    Neu co nhieu class, cap nhat label list theo thu tu training classes va verify detection mapping.
  </action>
  <verify>
    Doi chieu training metadata/classes neu co.
    Build va runtime scan smoke neu co device.
  </verify>
  <done>
    Label map khop voi model scope.
  </done>
</task>

<task type="auto">
  <name>Update docs ket qua fixes</name>
  <files>
    .gsd/phases/8/LEARNING_MODEL_FIXES.md
    .gsd/STATE.md
    .gsd/TODO.md
  </files>
  <action>
    Ghi lai decisions, files changed neu co, commands da chay, va residual risk.
  </action>
  <verify>
    `powershell -ExecutionPolicy Bypass -File scripts/validate-all.ps1`
  </verify>
  <done>
    `LEARNING_MODEL_FIXES.md` exists.
  </done>
</task>

## Bat Buoc Co

- [ ] Co decision ve entry point advanced review modes.
- [ ] Co decision ve custom model labels.
- [ ] Neu sua code, Android build pass.
- [ ] `LEARNING_MODEL_FIXES.md` exists.

## Tieu Chi Thanh Cong

- [ ] User co the vao cac mode can demo tu UI ro rang.
- [ ] Detection label mapping khong con mo ho.
- [ ] Docs ghi ro cac mode/labels nao chua nam trong demo scope.
