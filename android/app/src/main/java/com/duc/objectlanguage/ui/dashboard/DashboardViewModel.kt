package com.duc.objectlanguage.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.duc.objectlanguage.ObjectLanguageApp
import com.duc.objectlanguage.data.local.StreakDataStore
import com.duc.objectlanguage.data.model.StatsResponse
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as ObjectLanguageApp).repository
    private val streakStore = StreakDataStore(application)

    private val _stats = MutableLiveData<StatsResponse?>()
    val stats: LiveData<StatsResponse?> = _stats

    val streakSummary: LiveData<DashboardStreakSummary> = combine(
        streakStore.currentStreak,
        streakStore.reviewsToday,
    ) { current, today ->
        buildStreakSummary(current, today)
    }.asLiveData()

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun loadStats() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repo.getStats()
            result.fold(
                onSuccess = { _stats.value = it },
                onFailure = { _error.value = it.message }
            )
            _isLoading.value = false
        }
    }

    fun loadStreak() {
        viewModelScope.launch {
            repo.getStreak().onSuccess { remote ->
                streakStore.replaceWithServer(
                    remote.streakHienTai,
                    remote.streakDaiNhat,
                    remote.tongLuotOn,
                    remote.luotOnHomNay,
                    remote.ngayOnCuoi
                )
            }
        }
    }

    private fun buildStreakSummary(current: Int, reviewsToday: Int): DashboardStreakSummary {
        val nextMilestone = STREAK_MILESTONES.firstOrNull { milestone -> milestone > current }
        return if (nextMilestone == null) {
            DashboardStreakSummary(
                currentStreak = current,
                reviewsToday = reviewsToday,
                nextMilestone = current.coerceAtLeast(1),
                daysToMilestone = 0,
                hasReachedTopMilestone = true,
            )
        } else {
            DashboardStreakSummary(
                currentStreak = current,
                reviewsToday = reviewsToday,
                nextMilestone = nextMilestone,
                daysToMilestone = nextMilestone - current,
                hasReachedTopMilestone = false,
            )
        }
    }

    companion object {
        private val STREAK_MILESTONES = listOf(3, 7, 14, 30, 50, 100, 365)
    }
}

data class DashboardStreakSummary(
    val currentStreak: Int,
    val reviewsToday: Int,
    val nextMilestone: Int,
    val daysToMilestone: Int,
    val hasReachedTopMilestone: Boolean,
)
