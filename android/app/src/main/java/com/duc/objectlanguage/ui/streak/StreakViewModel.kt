package com.duc.objectlanguage.ui.streak

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.duc.objectlanguage.ObjectLanguageApp
import com.duc.objectlanguage.R
import com.duc.objectlanguage.data.local.StreakDataStore
import com.duc.objectlanguage.data.model.StreakSyncRequest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

class StreakViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx          = application.applicationContext
    private val streakStore  = StreakDataStore(application)
    private val repository   = (application as ObjectLanguageApp).repository

    // ── UI data: combine từ local DataStore ──────────────────────────────────
    val streakData: LiveData<StreakData> = combine(
        streakStore.currentStreak,
        streakStore.longestStreak,
        streakStore.totalReviews,
        streakStore.reviewsToday,
    ) { current, longest, total, today ->
        StreakData(
            currentStreak     = current,
            longestStreak     = longest,
            totalReviews      = total,
            reviewsToday      = today,
            motivationMessage = buildMotivationMessage(current, today),
            nextMilestone     = getNextMilestone(current),
            daysToMilestone   = getDaysToMilestone(current),
        )
    }.asLiveData()

    // ── Milestone celebration event ──────────────────────────────────────────
    private val _celebrateMilestone = MutableLiveData<Int?>()
    val celebrateMilestone: LiveData<Int?> = _celebrateMilestone

    fun clearCelebration() { _celebrateMilestone.value = null }

    // ── recordReview: local + server ─────────────────────────────────────────
    fun recordReview() {
        viewModelScope.launch {
            val previousStreak = streakStore.currentStreak.first()

            // 1. Cập nhật local DataStore trước (offline-first)
            streakStore.recordReview()
            val newStreak = streakStore.currentStreak.first()

            // 2. Kiểm tra milestone
            checkMilestones(previousStreak, newStreak)

            // 3. Gọi server để đồng bộ (fire-and-forget, lỗi mạng không sao)
            repository.recordStreak()
        }
    }

    /** Gọi khi mở StreakFragment hoặc sau khi có mạng trở lại */
    fun syncToServer() {
        viewModelScope.launch {
            val current  = streakStore.currentStreak.first()
            val longest  = streakStore.longestStreak.first()
            val total    = streakStore.totalReviews.first()
            val today    = LocalDate.now().toString()   // "yyyy-MM-dd"

            repository.syncStreak(StreakSyncRequest(current, longest, total, today))
        }
    }

    /** Tải streak từ server về (dùng khi login lần đầu / đổi thiết bị) */
    fun loadFromServer() {
        viewModelScope.launch {
            repository.getStreak().onSuccess { remote ->
                // Nếu server có streak lớn hơn local → cập nhật local
                val localStreak  = streakStore.currentStreak.first()
                val localLongest = streakStore.longestStreak.first()
                val localTotal   = streakStore.totalReviews.first()

                if (remote.streakHienTai > localStreak ||
                    remote.streakDaiNhat > localLongest ||
                    remote.tongLuotOn    > localTotal) {
                    // Ghi server data vào local (qua sync ngược)
                    // Đơn giản: chỉ override nếu server cao hơn
                    streakStore.overrideIfHigher(
                        remote.streakHienTai,
                        remote.streakDaiNhat,
                        remote.tongLuotOn
                    )
                }
            }
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private suspend fun checkMilestones(previousStreak: Int, newStreak: Int) {
        val milestones    = listOf(3, 7, 14, 30, 50, 100, 365)
        val lastMilestone = streakStore.lastMilestone.first()
        val achieved = milestones.firstOrNull { m ->
            newStreak >= m && previousStreak < m && m > lastMilestone
        }
        if (achieved != null) {
            streakStore.updateMilestone(achieved)
            _celebrateMilestone.postValue(achieved)
        }
    }

    private fun buildMotivationMessage(streak: Int, reviewsToday: Int): String {
        return when {
            reviewsToday == 0 && streak == 0 -> ctx.getString(R.string.streak_msg_start)
            reviewsToday == 0 && streak > 0  -> ctx.getString(R.string.streak_msg_dont_break, streak)
            reviewsToday > 0  && streak == 0 -> ctx.getString(R.string.streak_msg_great_start)
            streak == 1                      -> ctx.getString(R.string.streak_msg_keep_going)
            streak in 2..6                   -> ctx.getString(R.string.streak_msg_momentum, streak)
            streak in 7..13                  -> ctx.getString(R.string.streak_msg_one_week)
            streak in 14..29                 -> ctx.getString(R.string.streak_msg_two_weeks)
            streak in 30..99                 -> ctx.getString(R.string.streak_msg_champion, streak)
            streak >= 100                    -> ctx.getString(R.string.streak_msg_legend, streak)
            else                             -> ctx.getString(R.string.streak_msg_default)
        }
    }

    private fun getNextMilestone(current: Int): Int =
        listOf(3, 7, 14, 30, 50, 100, 365).firstOrNull { it > current } ?: 365

    private fun getDaysToMilestone(current: Int): Int =
        getNextMilestone(current) - current
}

data class StreakData(
    val currentStreak: Int,
    val longestStreak: Int,
    val totalReviews: Int,
    val reviewsToday: Int,
    val motivationMessage: String,
    val nextMilestone: Int,
    val daysToMilestone: Int,
)
