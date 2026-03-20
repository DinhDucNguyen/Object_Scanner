package com.duc.objectlanguage.ui.streak

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.duc.objectlanguage.data.local.StreakDataStore
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * ViewModel for streak tracking and display
 */
class StreakViewModel(application: Application) : AndroidViewModel(application) {
    
    private val streakDataStore = StreakDataStore(application)
    
    // Streak statistics
    val currentStreak: LiveData<Int> = streakDataStore.currentStreak.asLiveData()
    val longestStreak: LiveData<Int> = streakDataStore.longestStreak.asLiveData()
    val totalReviews: LiveData<Int> = streakDataStore.totalReviews.asLiveData()
    val reviewsToday: LiveData<Int> = streakDataStore.reviewsToday.asLiveData()
    
    // Combined streak data for UI
    val streakData: LiveData<StreakData> = combine(
        streakDataStore.currentStreak,
        streakDataStore.longestStreak,
        streakDataStore.totalReviews,
        streakDataStore.reviewsToday
    ) { current, longest, total, today ->
        StreakData(
            currentStreak = current,
            longestStreak = longest,
            totalReviews = total,
            reviewsToday = today,
            motivationMessage = getMotivationMessage(current, today),
            nextMilestone = getNextMilestone(current),
            daysToMilestone = getDaysToMilestone(current)
        )
    }.asLiveData()
    
    /**
     * Record a review completion
     */
    fun recordReview() {
        viewModelScope.launch {
            val previousStreak = currentStreak.value ?: 0
            streakDataStore.recordReview()
            val newStreak = currentStreak.value ?: 0
            
            // Check for milestone achievements
            checkMilestones(previousStreak, newStreak)
        }
    }
    
    /**
     * Check if milestone reached and show celebration
     */
    private suspend fun checkMilestones(oldStreak: Int, newStreak: Int) {
        val milestones = listOf(3, 7, 14, 30, 50, 100, 365)
        val lastMilestone = streakDataStore.lastMilestone
        
        lastMilestone.collect { last ->
            val achievedMilestone = milestones.firstOrNull { milestone ->
                newStreak >= milestone && oldStreak < milestone && milestone > last
            }
            
            if (achievedMilestone != null) {
                streakDataStore.updateMilestone(achievedMilestone)
                // Trigger celebration UI (handled in Fragment)
            }
        }
    }
    
    /**
     * Get motivation message based on streak
     */
    private fun getMotivationMessage(streak: Int, reviewsToday: Int): String {
        return when {
            reviewsToday == 0 && streak == 0 -> "🌟 Start your learning journey today!"
            reviewsToday == 0 && streak > 0 -> "🔥 Don't break your ${streak}-day streak!"
            reviewsToday > 0 && streak == 0 -> "✨ Great start! Come back tomorrow!"
            streak == 1 -> "💪 Keep going! Build your daily habit."
            streak in 2..6 -> "🚀 ${streak} days strong! You're building momentum."
            streak in 7..13 -> "⭐ One week streak! You're on fire!"
            streak in 14..29 -> "🔥 Two weeks! You're unstoppable!"
            streak in 30..99 -> "🏆 ${streak} days! You're a learning champion!"
            streak >= 100 -> "👑 ${streak} days! Legendary dedication!"
            else -> "Keep learning every day!"
        }
    }
    
    /**
     * Get next milestone to reach
     */
    private fun getNextMilestone(currentStreak: Int): Int {
        val milestones = listOf(3, 7, 14, 30, 50, 100, 365)
        return milestones.firstOrNull { it > currentStreak } ?: 365
    }
    
    /**
     * Calculate days until next milestone
     */
    private fun getDaysToMilestone(currentStreak: Int): Int {
        return getNextMilestone(currentStreak) - currentStreak
    }
    
    /**
     * Reset streak (for testing)
     */
    fun resetStreak() {
        viewModelScope.launch {
            streakDataStore.resetAll()
        }
    }
}

/**
 * Combined streak data for UI
 */
data class StreakData(
    val currentStreak: Int,
    val longestStreak: Int,
    val totalReviews: Int,
    val reviewsToday: Int,
    val motivationMessage: String,
    val nextMilestone: Int,
    val daysToMilestone: Int
)
