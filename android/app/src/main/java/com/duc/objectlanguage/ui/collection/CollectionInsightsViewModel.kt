package com.duc.objectlanguage.ui.collection

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.duc.objectlanguage.data.local.TokenManager
import com.duc.objectlanguage.data.model.Collection
import com.duc.objectlanguage.data.model.CollectionInsights
import com.duc.objectlanguage.data.repository.CollectionRepository
import kotlinx.coroutines.launch

/**
 * ViewModel for collection analytics and insights
 * Provides per-collection statistics and smart suggestions
 */
class CollectionInsightsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val tokenManager = TokenManager(application)
    private val repo = CollectionRepository(tokenManager)
    
    // All collections for dropdown selection
    private val _collections = MutableLiveData<List<Collection>>()
    val collections: LiveData<List<Collection>> = _collections
    
    // Selected collection insights
    private val _insights = MutableLiveData<CollectionInsights?>()
    val insights: LiveData<CollectionInsights?> = _insights
    
    // Smart suggestions based on learning patterns
    private val _suggestions = MutableLiveData<List<String>>()
    val suggestions: LiveData<List<String>> = _suggestions
    
    // Loading state
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error
    
    // Selected collection ID
    private val _selectedCollectionId = MutableLiveData<Int?>()
    val selectedCollectionId: LiveData<Int?> = _selectedCollectionId
    
    init {
        loadCollections()
    }
    
    /**
     * Load all collections for selection
     */
    private fun loadCollections() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repo.getCollections()
            
            result.fold(
                onSuccess = { collections ->
                    _collections.value = collections
                    // Auto-select first collection if available
                    if (collections.isNotEmpty() && _selectedCollectionId.value == null) {
                        selectCollection(collections[0].id)
                    }
                    _error.value = null
                },
                onFailure = { e ->
                    _error.value = "Failed to load collections: ${e.message}"
                }
            )
            
            _isLoading.value = false
        }
    }
    
    /**
     * Select collection and load insights
     */
    fun selectCollection(collectionId: Int) {
        _selectedCollectionId.value = collectionId
        loadInsights(collectionId)
    }
    
    /**
     * Load collection insights
     */
    private fun loadInsights(collectionId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repo.getCollectionInsights(collectionId)
            
            result.fold(
                onSuccess = { insights ->
                    _insights.value = insights
                    generateSuggestions(insights)
                    _error.value = null
                },
                onFailure = { e ->
                    _error.value = "Failed to load insights: ${e.message}"
                }
            )
            
            _isLoading.value = false
        }
    }
    
    /**
     * Generate smart suggestions based on insights
     */
    private fun generateSuggestions(insights: CollectionInsights) {
        val suggestions = mutableListOf<String>()
        
        // Calculate progress percentage
        val progressPercent = if (insights.totalItems > 0) {
            (insights.reviewedItems.toDouble() / insights.totalItems * 100).toInt()
        } else 0
        
        val masteryPercent = if (insights.totalItems > 0) {
            (insights.masteredItems.toDouble() / insights.totalItems * 100).toInt()
        } else 0
        
        // Suggestion 1: Review frequency
        when {
            insights.reviewedItems == 0 -> {
                suggestions.add("💡 Start reviewing items in this collection to build your vocabulary!")
            }
            progressPercent < 50 -> {
                suggestions.add("📚 You've reviewed ${progressPercent}% of items. Keep going!")
            }
            progressPercent < 100 -> {
                suggestions.add("🌟 Great progress! ${100 - progressPercent}% more to review.")
            }
            else -> {
                suggestions.add("✅ All items reviewed! Focus on maintaining mastery.")
            }
        }
        
        // Suggestion 2: Mastery level
        when {
            masteryPercent == 0 && insights.reviewedItems > 0 -> {
                suggestions.add("🎯 Focus on improving quality scores to master these items.")
            }
            masteryPercent < 30 -> {
                suggestions.add("📈 ${masteryPercent}% mastered. Review difficult items more frequently.")
            }
            masteryPercent < 70 -> {
                suggestions.add("🔥 ${masteryPercent}% mastered! You're making excellent progress.")
            }
            masteryPercent >= 70 -> {
                suggestions.add("🏆 Outstanding! ${masteryPercent}% of items mastered.")
            }
        }
        
        // Suggestion 3: Success rate
        when {
            insights.successRate < 0.5 -> {
                suggestions.add("💪 Try using different review modes (Flashcards, Quiz, Typing) to improve retention.")
            }
            insights.successRate < 0.75 -> {
                suggestions.add("📊 ${(insights.successRate * 100).toInt()}% success rate. Practice pronunciation for better memory.")
            }
            insights.successRate >= 0.75 -> {
                suggestions.add("🌟 Excellent ${(insights.successRate * 100).toInt()}% success rate!")
            }
        }
        
        // Suggestion 4: Review consistency
        if (insights.lastReviewDate != null) {
            val daysSinceReview = calculateDaysSince(insights.lastReviewDate)
            when {
                daysSinceReview == 0 -> {
                    suggestions.add("✨ Reviewed today! Keep up the daily habit.")
                }
                daysSinceReview == 1 -> {
                    suggestions.add("📅 Last reviewed yesterday. Review again to maintain progress.")
                }
                daysSinceReview in 2..7 -> {
                    suggestions.add("⏰ Last reviewed ${daysSinceReview} days ago. Time for a refresh!")
                }
                daysSinceReview > 7 -> {
                    suggestions.add("⚠️ No review in ${daysSinceReview} days. Items may need re-learning.")
                }
            }
        }
        
        // Suggestion 5: Collection size
        when {
            insights.totalItems < 10 -> {
                suggestions.add("➕ Small collection! Add more related items to build a stronger foundation.")
            }
            insights.totalItems > 50 -> {
                suggestions.add("📦 Large collection! Consider splitting into smaller themed collections.")
            }
        }
        
        _suggestions.value = suggestions
    }
    
    /**
     * Calculate days since a date
     */
    private fun calculateDaysSince(date: java.util.Date): Int {
        val now = System.currentTimeMillis()
        val diff = now - date.time
        return (diff / (1000 * 60 * 60 * 24)).toInt()
    }
    
    /**
     * Refresh insights
     */
    fun refresh() {
        _selectedCollectionId.value?.let { collectionId ->
            loadInsights(collectionId)
        }
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _error.value = null
    }
}
