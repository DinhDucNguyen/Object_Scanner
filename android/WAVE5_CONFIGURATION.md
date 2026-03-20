# Wave 5: Streaks & Notifications - Configuration Guide

## 📦 Dependencies to Add (build.gradle)

Add these dependencies to your `app/build.gradle`:

```gradle
dependencies {
    // Existing dependencies...
    
    // DataStore Preferences (for streak persistence)
    implementation "androidx.datastore:datastore-preferences:1.0.0"
    
    // WorkManager (for background notifications)
    implementation "androidx.work:work-runtime-ktx:2.8.1"
    
    // Konfetti (for celebration animations)
    implementation "nl.dionsegijn:konfetti-xml:2.0.3"
    
    // Material TimePicker (already included in Material3)
    // implementation "com.google.android.material:material:1.9.0"
}
```

## 🔐 Permissions to Add (AndroidManifest.xml)

Add these permissions to your `AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    
    <!-- Notification permissions -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.VIBRATE" />
    
    <!-- Background work permissions (optional but recommended) -->
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    
    <application
        android:name=".ObjectLanguageApp"
        android:allowBackup="true"
        ...>
        
        <!-- Activities -->
        <activity android:name=".MainActivity" ... />
        
        <!-- WorkManager Workers -->
        <receiver
            android:name="androidx.work.impl.background.systemalarm.ConstraintProxy$BatteryNotLowProxy"
            android:enabled="false"
            android:exported="false"
            tools:replace="android:enabled" />
            
    </application>
    
</manifest>
```

**Important:** Add `android:name=".ObjectLanguageApp"` to the `<application>` tag to register the custom Application class.

## 🔗 Integration with Existing Code

### 1. Call `recordReview()` after successful review submission

In **ALL 6 ViewModels** (FlashcardViewModel, QuizViewModel, TypingTestViewModel, ListeningTestViewModel, ImageMatchingViewModel, PronunciationViewModel), add this code AFTER the successful review submission loop:

```kotlin
// In submitResults() method, AFTER the forEach loop:
results.forEach { (progressId, quality) ->
    val result = repo.submitReview(progressId, quality)
    result.fold(
        onSuccess = { successCount++ },
        onFailure = { failCount++ }
    )
}

// ADD THIS:
if (successCount > 0) {
    viewModelScope.launch {
        val dataStore = StreakDataStore(context)
        dataStore.recordReview()
    }
}
```

**IMPORTANT:** You need to pass `context: Context` to each ViewModel constructor or get it from AndroidViewModel.

### 2. Initialize StreakResetWorker in Application class

Already done! The `ObjectLanguageApp.kt` file automatically schedules the streak validation worker on app startup.

### 3. Add StreakFragment to Navigation Graph

In your `nav_graph.xml`:

```xml
<fragment
    android:id="@+id/streakFragment"
    android:name="com.duc.objectlanguage.ui.streak.StreakFragment"
    android:label="Streaks"
    tools:layout="@layout/fragment_streak" />
```

### 4. Add Navigation Menu Item

In your bottom navigation menu or drawer menu:

```xml
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- Existing items -->
    
    <item
        android:id="@+id/streakFragment"
        android:icon="@drawable/ic_notification"
        android:title="Streaks" />
</menu>
```

## 🧪 Testing Checklist

- [ ] **Notification permission**: Request POST_NOTIFICATIONS permission at runtime (Android 13+)
- [ ] **Daily reminder**: Set reminder time and verify notification appears at configured time
- [ ] **Streak increment**: Complete a review and verify streak increases
- [ ] **Same-day protection**: Complete multiple reviews on same day, streak should not double-increment
- [ ] **Streak continuation**: Review on consecutive days, streak should increase
- [ ] **Streak break**: Skip a day, verify streak resets to 0
- [ ] **Milestone celebration**: Reach 3, 7, 14 day milestones and verify confetti animation
- [ ] **Longest streak**: Break and rebuild streak, verify "Best" stat shows longest streak ever
- [ ] **Motivation messages**: Verify different messages appear based on streak length (0, 1-2, 3-6, 7-13, 14-29, 30-49, 50-99, 100+ days)
- [ ] **Flame intensity**: Verify flame icons change (💤, 🔥, 🔥🔥, 🔥🔥🔥, 🔥🔥🔥🔥) based on streak
- [ ] **Today status**: Verify "Review pending" changes to "✅ Review complete!" after first review of the day
- [ ] **Progress bar**: Verify progress to next milestone updates correctly
- [ ] **Notification settings**: Toggle switches and verify behavior changes

## 🎯 Features Summary

Wave 5 implements a complete Duolingo-style streak and notification system:

### ✅ Core Files Created (10 files):

1. **StreakDataStore.kt** (145 lines)
   - DataStore preferences for local persistence
   - Automatic streak validation (same day/yesterday/broken)
   - Milestone tracking (3, 7, 14, 30, 50, 100, 365 days)

2. **StreakViewModel.kt** (125 lines)
   - Combined StreakData flow for UI
   - 9 motivation messages based on streak length
   - Next milestone calculation

3. **DailyReminderWorker.kt** (180 lines)
   - Configurable daily notification time
   - 7 dynamic notification content variants
   - NotificationChannel setup for Android 8.0+

4. **StreakResetWorker.kt** (45 lines)
   - 6-hour periodic validation cycle
   - Automatic streak reset if day skipped

5. **StreakFragment.kt** (195 lines)
   - Main streak display with flame intensity
   - TimePickerDialog for notification setup
   - Konfetti celebration animations
   - Milestone info dialog

6. **fragment_streak.xml** (450 lines)
   - Scrollable layout with KonfettiView
   - Main streak card with flame icon
   - Today status card
   - Motivation message card
   - Statistics grid (Best/Total/Today)
   - Milestone progress card
   - Notification settings card

7. **NotificationSettingsFragment.kt** (90 lines)
   - Toggle switches for notifications
   - Time picker integration
   - Status updates

8. **fragment_notification_settings.xml** (200 lines)
   - Daily reminder toggle + time selector
   - Streak alert toggle
   - Milestone celebration toggle
   - Status text display

9. **ic_notification.xml** (10 lines)
   - Notification icon vector drawable

10. **ObjectLanguageApp.kt** (15 lines)
    - Application class for worker initialization

### 🎨 Visual Features:

- **Flame Intensity**: 💤 (0 days) → 🔥 (1-6) → 🔥🔥 (7-29) → 🔥🔥🔥 (30-99) → 🔥🔥🔥🔥 (100+)
- **Konfetti Animation**: Triggered at milestones (3, 7, 14, 30, 50, 100, 365 days)
- **Progress Bar**: Shows progress to next milestone
- **Color Coding**: Purple for streak, Blue for total, Green for today

### 🔔 Notification Features:

- **Daily Reminder**: Configurable time (default 19:00)
- **Streak Alert**: Warn if streak is at risk
- **Milestone Celebration**: Notification on milestone achievements
- **Dynamic Content**: 7 notification variants based on streak length

### 📊 Data Tracking:

- **Current Streak**: Days of consecutive reviews
- **Longest Streak**: Best streak ever achieved
- **Total Reviews**: All-time review count
- **Reviews Today**: Daily review count

### 🎮 Gamification:

- **Motivation Messages**: 9 contextual messages (New/Getting Started/Building Habit/Week Warrior/Strong/Dedicated/Legendary/Master/Unstoppable)
- **Milestones**: 3, 7, 14, 30, 50, 100, 365 days
- **Visual Feedback**: Flame intensity, progress bars, confetti
- **Social Proof**: Stats display to encourage competition with self

## 🚀 Next Steps

1. **Add Dependencies**: Update `app/build.gradle` with the 3 new dependencies
2. **Add Permissions**: Update `AndroidManifest.xml` with POST_NOTIFICATIONS and VIBRATE
3. **Register Application**: Add `android:name=".ObjectLanguageApp"` to `<application>` tag
4. **Integrate recordReview()**: Add streak recording to all 6 ViewModels after successful submissions
5. **Add to Navigation**: Add StreakFragment to nav_graph and navigation menu
6. **Request Permissions**: Add runtime permission request for POST_NOTIFICATIONS (Android 13+)
7. **Test**: Follow the testing checklist above

## 📝 Permission Request Code (Android 13+)

Add this to MainActivity or StreakFragment:

```kotlin
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

// In Activity or Fragment:
private val notificationPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
) { isGranted ->
    if (isGranted) {
        // Permission granted, can show notifications
    } else {
        // Permission denied, show explanation
    }
}

private fun requestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Already have permission
            }
            else -> {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
```

## 🎉 Wave 5 Complete!

All core files for Streaks & Notifications are now created. Follow the integration steps above to complete the implementation.
