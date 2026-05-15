package com.duc.objectlanguage.ui.analytics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.duc.objectlanguage.ObjectLanguageApp
import com.duc.objectlanguage.data.model.AnalyticsResponse
import com.duc.objectlanguage.data.model.StatsResponse
import com.duc.objectlanguage.data.model.StreakResponse
import kotlinx.coroutines.launch

class AnalyticsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as ObjectLanguageApp).repository

    private val _stats = MutableLiveData<StatsResponse?>()
    val stats: LiveData<StatsResponse?> = _stats

    private val _analytics = MutableLiveData<AnalyticsResponse?>()
    val analytics: LiveData<AnalyticsResponse?> = _analytics

    private val _streak = MutableLiveData<StreakResponse?>()
    val streak: LiveData<StreakResponse?> = _streak

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun loadAnalytics() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            repo.getStats().fold(
                onSuccess = { _stats.value = it },
                onFailure = { _error.value = it.message ?: "Không tải được thống kê" }
            )

            repo.getAnalytics().fold(
                onSuccess = { _analytics.value = it },
                onFailure = { _error.value = it.message ?: "Không tải được biểu đồ" }
            )

            repo.getStreak().fold(
                onSuccess = { _streak.value = it },
                onFailure = { _error.value = it.message ?: "Không tải được chuỗi học" }
            )

            _isLoading.value = false
        }
    }

    fun clearError() {
        _error.value = null
    }
}
