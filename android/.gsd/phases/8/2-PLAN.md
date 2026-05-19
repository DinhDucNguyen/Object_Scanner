---
phase: 8
plan: 2
wave: 2
gap_closure: true
---

# Plan 8.2: Runtime Device Verification

## Muc Tieu

Chay app tren emulator/device that va ghi bang chung runtime. Plan nay khong sua code app/backend neu user chi yeu cau verify.

## Dieu Kien Bat Dau

- `adb devices` co it nhat mot device/emulator.
- Backend chay duoc tren dia chi khop `android/local.properties`.
- APK debug build duoc bang JDK 17/JBR.
- Neu can login/review, co account va data test.

## Ngu Canh Can Doc

- `.gsd/phases/7/VERIFICATION.md`
- `.gsd/phases/8/P0_BLOCKERS.md` neu da co
- `android/local.properties`
- `backend/.env`
- `app/src/main/res/navigation/nav_graph.xml`
- `app/src/main/java/com/duc/objectlanguage/ui/MainActivity.kt`

## Cong Viec

<task type="manual">
  <name>Install va launch app</name>
  <files>
    app/build/outputs/apk/debug/app-debug.apk
  </files>
  <action>
    Build APK, install vao device/emulator, launch app, va thu thap logcat neu crash.
  </action>
  <verify>
    `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat assembleDebug --console=plain`
    `adb install -r app/build/outputs/apk/debug/app-debug.apk`
    `adb shell monkey -p com.duc.objectlanguage 1`
  </verify>
  <done>
    App launch duoc hoac crash duoc ghi trong `RUNTIME_VERIFICATION.md`.
  </done>
</task>

<task type="manual">
  <name>Verify core demo flow</name>
  <files>
    app/src/main/java/com/duc/objectlanguage/ui/**
    backend/app/routers/**
  </files>
  <action>
    Verify theo thu tu:
    1. Start app va guest scan guard.
    2. Login/register neu co account.
    3. Scan flow: model toggle, camera/input, result detail, audio.
    4. Save/learn flow.
    5. Review card flow.
    6. Profile -> analytics, collections, streak, notification settings.
  </action>
  <verify>
    Manual checklist + logcat evidence.
  </verify>
  <done>
    Moi flow duoc danh dau Pass/Fail/Blocked trong `RUNTIME_VERIFICATION.md`.
  </done>
</task>

<task type="manual">
  <name>Verify permissions va device-only behavior</name>
  <files>
    app/src/main/AndroidManifest.xml
    app/src/main/java/com/duc/objectlanguage/ui/scan/**
    app/src/main/java/com/duc/objectlanguage/ui/review/PronunciationFragment.kt
    app/src/main/java/com/duc/objectlanguage/ui/settings/NotificationSettingsFragment.kt
  </files>
  <action>
    Verify camera, microphone, notification permission, TTS/SpeechRecognizer, va WorkManager-related behavior neu co the.
  </action>
  <verify>
    Manual checklist + logcat evidence.
  </verify>
  <done>
    Runtime-only risks duoc ghi ro.
  </done>
</task>

## Output

Tao `.gsd/phases/8/RUNTIME_VERIFICATION.md` voi:

- Device/emulator info.
- Backend/base URL info.
- Commands da chay.
- Flow checklist Pass/Fail/Blocked.
- Crash/logcat summary neu co.
- Priority fix list cho Plan 8.3/8.4.

## Bat Buoc Co

- [ ] Da thu launch app tren device/emulator hoac ghi ro blocker.
- [ ] Da verify core demo flow hoac ghi ro blocker.
- [ ] Da ghi permission/runtime behavior.
- [ ] `RUNTIME_VERIFICATION.md` exists.

## Tieu Chi Thanh Cong

- [ ] Co bang chung runtime thay vi chi static/build evidence.
- [ ] Biet ro app da demo-duoc hay crash/miss flow o dau.
