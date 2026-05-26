package com.duc.objectlanguage.ui.streak

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.duc.objectlanguage.ObjectLanguageApp
import com.duc.objectlanguage.R
import com.duc.objectlanguage.data.local.NotificationPreferences
import com.duc.objectlanguage.data.local.StreakDataStore
import com.duc.objectlanguage.data.model.StreakResponse
import com.duc.objectlanguage.data.model.StreakSyncRequest
import com.duc.objectlanguage.utils.AppNotificationHelper
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class StreakViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx = application.applicationContext
    private val streakStore = StreakDataStore(application)
    private val repository = (application as ObjectLanguageApp).repository

    val streakData: LiveData<StreakData> = combine(
        streakStore.currentStreak,
        streakStore.longestStreak,
        streakStore.totalReviews,
        streakStore.reviewsToday,
    ) { current, longest, total, today ->
        StreakData(
            currentStreak = current,
            longestStreak = longest,
            totalReviews = total,
            reviewsToday = today,
            nextMilestone = getNextMilestone(current),
            daysToMilestone = getDaysToMilestone(current),
        )
    }.asLiveData()

    private val _calendarDays = MutableLiveData<List<com.duc.objectlanguage.data.model.StreakCalendarDay>>()
    val calendarDays: LiveData<List<com.duc.objectlanguage.data.model.StreakCalendarDay>> = _calendarDays

    private val _celebrateMilestone = MutableLiveData<Int?>()
    val celebrateMilestone: LiveData<Int?> = _celebrateMilestone

    fun clearCelebration() {
        _celebrateMilestone.value = null
    }

    fun recordReview() {
        viewModelScope.launch {
            val previousStreak = streakStore.currentStreak.first()

            repository.recordStreak().fold(
                onSuccess = { remote ->
                    applyServerStreak(remote)
                    checkMilestones(previousStreak, remote.streakHienTai)
                },
                onFailure = {
                    streakStore.recordReview()
                    val newStreak = streakStore.currentStreak.first()
                    checkMilestones(previousStreak, newStreak)
                }
            )
        }
    }

    fun syncToServer() {
        viewModelScope.launch {
            val current = streakStore.currentStreak.first()
            val longest = streakStore.longestStreak.first()
            val total = streakStore.totalReviews.first()
            val today = streakStore.reviewsToday.first()
            val lastDate = streakStore.getLastReviewDateString()

            repository.syncStreak(StreakSyncRequest(current, longest, total, today, lastDate))
                .onSuccess { remote -> applyServerStreak(remote) }
        }
    }

    fun loadFromServer() {
        viewModelScope.launch {
            repository.getStreak().onSuccess { remote ->
                applyServerStreak(remote)
            }
        }
        loadCalendar()
    }

    fun loadCalendar() {
        viewModelScope.launch {
            repository.getStreakCalendar(30).onSuccess { response ->
                _calendarDays.postValue(response.days)
            }
        }
    }

    private suspend fun applyServerStreak(remote: StreakResponse) {
        streakStore.replaceWithServer(
            remote.streakHienTai,
            remote.streakDaiNhat,
            remote.tongLuotOn,
            remote.luotOnHomNay,
            remote.ngayOnCuoi
        )
    }

    private suspend fun checkMilestones(previousStreak: Int, newStreak: Int) {
        val milestones = listOf(3, 7, 14, 30, 50, 100, 365)
        val lastMilestone = streakStore.lastMilestone.first()
        val achieved = milestones.firstOrNull { milestone ->
            newStreak >= milestone && previousStreak < milestone && milestone > lastMilestone
        }
        if (achieved != null) {
            streakStore.updateMilestone(achieved)
            val settings = NotificationPreferences(ctx).currentSettings()
            if (settings.milestoneCelebrationEnabled) {
                _celebrateMilestone.postValue(achieved)
                AppNotificationHelper.showMilestone(ctx, achieved)
            }
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
    val nextMilestone: Int,
    val daysToMilestone: Int,
)
