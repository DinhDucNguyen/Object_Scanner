# Wave 5: Learning Streaks & Notifications

> **Duration**: 2 ngày  
> **Dependencies**: All waves complete  
> **Focus**: User engagement & retention

---

## Task 5.1: Streak Tracking System (1 ngày)

### Streak Calculation Logic
```kotlin
// Consecutive days with >0 reviews completed
// Reset if miss a day (no reviews)
// Store in SharedPreferences + sync to backend
```

### Streak Display
**Dashboard Widget:**
```
🔥 12 Day Streak!
━━━━━━━━━━━━━━━━
Current: 12 days
Record: 18 days (beat it!)
[Mini line chart of last 7 days]
```

### Streak Milestones
Achievement badges:
- 3 days: "Getting Started 🌱"
- 7 days: "Week Warrior 🏆"
- 14 days: "Consistency King 👑"
- 30 days: "Monthly Master 🌟"
- 100 days: "Century Club 💯"

### Streak Protection
- **Streak Freeze** (optional future feature)
- Show warning: "Don't break your 12-day streak! Review now"

---

## Task 5.2: Smart Notifications (1 ngày)

### WorkManager Setup
```kotlin
// NotificationWorker.kt
class DailyReminderWorker : Worker() {
    override fun doWork(): Result {
        // Check if user has due words
        val dueCount = repository.getDueCount()
        
        if (dueCount > 0 && !reviewedToday()) {
            showNotification(dueCount)
        }
        
        return Result.success()
    }
}
```

### Notification Logic
**Schedule:**
- Default: Daily at 8:00 PM
- User customizable in Settings

**Smart Conditions:**
- Only notify if dueCount > 0
- Skip if user already reviewed today
- Respect Do Not Disturb mode

**Notification Content:**
```
📚 Time to Learn!
━━━━━━━━━━━━━━━━
You have 5 words to review
Keep your 12-day streak alive! 🔥

[Review Now] [Later]
```

### Notification Channel
```kotlin
val channel = NotificationChannel(
    "learning_reminders",
    "Learning Reminders",
    NotificationManager.IMPORTANCE_DEFAULT
).apply {
    description = "Daily reminders to review vocabulary"
}
```

### Settings UI
```
⚙️ Notification Settings
━━━━━━━━━━━━━━━━━━━━
☑ Enable daily reminders
⏰ Reminder time: 8:00 PM
☑ Streak warnings
☐ Achievement notifications
```

---

## Verification
```bash
# Streak System
✅ Streak increments on daily review
✅ Streak resets if miss a day
✅ Record streak persists
✅ Milestones trigger at correct days
✅ Streak display animates (Lottie flames)

# Notifications
✅ Worker scheduled correctly (daily)
✅ Notification appears at set time
✅ Due count accurate in notification
✅ Tap notification → opens ReviewFragment
✅ Notification respects user settings
✅ No notification if already reviewed
✅ No notification if dueCount = 0

# Cross-Device Testing
✅ Test on Android 11, 12, 13, 14
✅ Test with battery optimization enabled
✅ Test in Doze mode (background restrictions)
✅ Verify WorkManager persistence dopo reboot
```

---

## Phase 6 Complete! 🎉

All 5 waves implemented:
- ✅ Multi-modal review (4 modes)
- ✅ Pronunciation practice
- ✅ Visual analytics (4 charts)
- ✅ Smart collections & insights
- ✅ Streaks & notifications

**App is now a complete learning platform!**
