package com.duc.objectlanguage.ui.analytics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.duc.objectlanguage.ObjectLanguageApp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class ProgressDataPoint(
    val date: String,
    val wordsLearned: Int
)

data class MasteryDistribution(
    val mastered: Int,      // EF >= 2.5, interval > 7 days
    val learning: Int,      // EF >= 2.0, interval 1-7 days
    val newWords: Int,      // repetitions == 0
    val difficult: Int      // EF < 2.0
)

data class ActivityDay(
    val dayName: String,
    val reviewCount: Int
)

data class StatsData(
    val totalWords: Int,
    val masteredWords: Int,
    val reviewsToday: Int,
    val currentStreak: Int
)

class AnalyticsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as ObjectLanguageApp).repository

    private val _progressData = MutableLiveData<List<ProgressDataPoint>>()
    val progressData: LiveData<List<ProgressDataPoint>> = _progressData

    private val _masteryData = MutableLiveData<MasteryDistribution>()
    val masteryData: LiveData<MasteryDistribution> = _masteryData

    private val _activityData = MutableLiveData<List<ActivityDay>>()
    val activityData: LiveData<List<ActivityDay>> = _activityData

    private val _statsData = MutableLiveData<StatsData>()
    val statsData: LiveData<StatsData> = _statsData

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun loadAnalytics() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                // Load progress data (last 7 days)
                loadProgressData()

                // Load mastery distribution
                loadMasteryData()

                // Load activity data (last 7 days)
                loadActivityData()

                // Load overall stats
                loadStatsData()

            } catch (e: Exception) {
                _error.value = "Failed to load analytics: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadProgressData() {
        val result = repo.getProgressHistory()
        result.fold(
            onSuccess = { history ->
                // Group by date and count words learned
                val dateFormat = SimpleDateFormat("MM/dd", Locale.getDefault())
                val calendar = Calendar.getInstance()
                
                val last7Days = (0..6).map { daysAgo ->
                    calendar.time = Date()
                    calendar.add(Calendar.DAY_OF_YEAR, -daysAgo)
                    dateFormat.format(calendar.time)
                }.reversed()

                val dataPoints = last7Days.map { date ->
                    ProgressDataPoint(
                        date = date,
                        wordsLearned = history.count { 
                            dateFormat.format(Date(it.createdAt)) == date && it.repetitions > 0
                        }
                    )
                }

                _progressData.value = dataPoints
            },
            onFailure = {
                _progressData.value = emptyList()
            }
        )
    }

    private suspend fun loadMasteryData() {
        val result = repo.getProgressHistory()
        result.fold(
            onSuccess = { history ->
                val mastered = history.count { it.easinessFactor >= 2.5 && it.interval > 7 }
                val learning = history.count { it.easinessFactor >= 2.0 && it.interval in 1..7 }
                val newWords = history.count { it.repetitions == 0 }
                val difficult = history.count { it.easinessFactor < 2.0 && it.repetitions > 0 }

                _masteryData.value = MasteryDistribution(
                    mastered = mastered,
                    learning = learning,
                    newWords = newWords,
                    difficult = difficult
                )
            },
            onFailure = {
                _masteryData.value = MasteryDistribution(0, 0, 0, 0)
            }
        )
    }

    private suspend fun loadActivityData() {
        val result = repo.getReviewHistory()
        result.fold(
            onSuccess = { reviews ->
                val dateFormat = SimpleDateFormat("EEE", Locale.getDefault())
                val calendar = Calendar.getInstance()
                
                val last7Days = (0..6).map { daysAgo ->
                    calendar.time = Date()
                    calendar.add(Calendar.DAY_OF_YEAR, -daysAgo)
                    dateFormat.format(calendar.time)
                }.reversed()

                val activityDays = last7Days.map { day ->
                    ActivityDay(
                        dayName = day,
                        reviewCount = reviews.count {
                            dateFormat.format(Date(it.reviewedAt)) == day
                        }
                    )
                }

                _activityData.value = activityDays
            },
            onFailure = {
                _activityData.value = emptyList()
            }
        )
    }

    private suspend fun loadStatsData() {
        val progressResult = repo.getProgressHistory()
        val reviewResult = repo.getReviewHistory()

        progressResult.fold(
            onSuccess = { history ->
                val total = history.size
                val mastered = history.count { it.easinessFactor >= 2.5 && it.interval > 7 }
                
                reviewResult.fold(
                    onSuccess = { reviews ->
                        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                        val reviewsToday = reviews.count {
                            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.reviewedAt)) == today
                        }

                        // Calculate streak
                        val streak = calculateStreak(reviews.map { it.reviewedAt }.sorted())

                        _statsData.value = StatsData(
                            totalWords = total,
                            masteredWords = mastered,
                            reviewsToday = reviewsToday,
                            currentStreak = streak
                        )
                    },
                    onFailure = {
                        _statsData.value = StatsData(total, mastered, 0, 0)
                    }
                )
            },
            onFailure = {
                _statsData.value = StatsData(0, 0, 0, 0)
            }
        )
    }

    private fun calculateStreak(reviewDates: List<Long>): Int {
        if (reviewDates.isEmpty()) return 0

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = dateFormat.format(Date())
        
        // Get unique dates
        val uniqueDates = reviewDates.map { dateFormat.format(Date(it)) }.toSet().sorted().reversed()
        
        if (uniqueDates.isEmpty() || uniqueDates[0] != today) {
            // No review today, check if yesterday
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            val yesterday = dateFormat.format(calendar.time)
            if (uniqueDates.isEmpty() || uniqueDates[0] != yesterday) {
                return 0
            }
        }

        var streak = 0
        val calendar = Calendar.getInstance()

        for (i in 0 until uniqueDates.size) {
            val expectedDate = dateFormat.format(calendar.time)
            if (uniqueDates[i] == expectedDate) {
                streak++
                calendar.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }

        return streak
    }

    fun clearError() {
        _error.value = null
    }
}
