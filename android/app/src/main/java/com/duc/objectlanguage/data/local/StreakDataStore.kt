package com.duc.objectlanguage.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*

/**
 * Local storage for streak tracking using DataStore
 */
class StreakDataStore(private val context: Context) {
    
    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("streak_prefs")
        
        private val CURRENT_STREAK = intPreferencesKey("current_streak")
        private val LONGEST_STREAK = intPreferencesKey("longest_streak")
        private val LAST_REVIEW_DATE = longPreferencesKey("last_review_date")
        private val TOTAL_REVIEWS = intPreferencesKey("total_reviews")
        private val REVIEWS_TODAY = intPreferencesKey("reviews_today")
        private val LAST_MILESTONE = intPreferencesKey("last_milestone")
    }
    
    /**
     * Get current streak count
     */
    val currentStreak: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[CURRENT_STREAK] ?: 0
    }
    
    /**
     * Get longest streak record
     */
    val longestStreak: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[LONGEST_STREAK] ?: 0
    }
    
    /**
     * Get total reviews done
     */
    val totalReviews: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[TOTAL_REVIEWS] ?: 0
    }
    
    /**
     * Get reviews completed today
     */
    val reviewsToday: Flow<Int> = context.dataStore.data.map { prefs ->
        val lastReviewDate = prefs[LAST_REVIEW_DATE] ?: 0L
        val today = getTodayStartTimestamp()
        
        if (lastReviewDate >= today) {
            prefs[REVIEWS_TODAY] ?: 0
        } else {
            0 // Reset if last review was not today
        }
    }
    
    /**
     * Record a review completion
     */
    suspend fun recordReview() {
        context.dataStore.edit { prefs ->
            val lastReviewDate = prefs[LAST_REVIEW_DATE] ?: 0L
            val currentTime = System.currentTimeMillis()
            val today = getTodayStartTimestamp()
            val yesterday = today - 24 * 60 * 60 * 1000
            
            // Increment total reviews
            val totalReviews = (prefs[TOTAL_REVIEWS] ?: 0) + 1
            prefs[TOTAL_REVIEWS] = totalReviews
            
            // Update reviews today
            val reviewsToday = if (lastReviewDate >= today) {
                (prefs[REVIEWS_TODAY] ?: 0) + 1
            } else {
                1 // First review of the day
            }
            prefs[REVIEWS_TODAY] = reviewsToday
            
            // Update streak
            val currentStreak = prefs[CURRENT_STREAK] ?: 0
            val newStreak = when {
                lastReviewDate >= today -> currentStreak // Same day, keep streak
                lastReviewDate >= yesterday -> currentStreak + 1 // Yesterday, increment
                else -> 1 // Streak broken, restart
            }
            prefs[CURRENT_STREAK] = newStreak
            
            // Update longest streak
            val longestStreak = prefs[LONGEST_STREAK] ?: 0
            if (newStreak > longestStreak) {
                prefs[LONGEST_STREAK] = newStreak
            }
            
            // Update last review date
            prefs[LAST_REVIEW_DATE] = currentTime
        }
    }
    
    /**
     * Check if user reviewed today
     */
    suspend fun hasReviewedToday(): Boolean {
        var result = false
        context.dataStore.data.collect { prefs ->
            val lastReviewDate = prefs[LAST_REVIEW_DATE] ?: 0L
            val today = getTodayStartTimestamp()
            result = lastReviewDate >= today
        }
        return result
    }
    
    /**
     * Get last milestone reached
     */
    val lastMilestone: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[LAST_MILESTONE] ?: 0
    }
    
    /**
     * Update last milestone
     */
    suspend fun updateMilestone(milestone: Int) {
        context.dataStore.edit { prefs ->
            prefs[LAST_MILESTONE] = milestone
        }
    }
    
    /**
     * Reset all streak data (for testing)
     */
    suspend fun resetAll() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }
    
    /**
     * Get timestamp for start of today (00:00:00)
     */
    private fun getTodayStartTimestamp(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
