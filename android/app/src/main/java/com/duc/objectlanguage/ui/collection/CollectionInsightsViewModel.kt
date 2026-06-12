package com.duc.objectlanguage.ui.collection

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.duc.objectlanguage.ObjectLanguageApp
import com.duc.objectlanguage.R
import com.duc.objectlanguage.data.model.Collection
import com.duc.objectlanguage.data.model.CollectionInsights
import com.duc.objectlanguage.ui.common.localizedString
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * ViewModel for collection analytics and insights.
 */
class CollectionInsightsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as ObjectLanguageApp).repository.collection

    private val _collections = MutableLiveData<List<Collection>>()
    val collections: LiveData<List<Collection>> = _collections

    private val _insights = MutableLiveData<CollectionInsights?>()
    val insights: LiveData<CollectionInsights?> = _insights

    private val _suggestions = MutableLiveData<List<String>>()
    val suggestions: LiveData<List<String>> = _suggestions

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _selectedCollectionId = MutableLiveData<Int?>()
    val selectedCollectionId: LiveData<Int?> = _selectedCollectionId

    private var initialCollectionId: Int = 0

    init {
        loadCollections()
    }

    fun setInitialCollection(collectionId: Int) {
        initialCollectionId = collectionId
    }

    private fun loadCollections() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repo.getCollections()

            result.fold(
                onSuccess = { collections ->
                    _collections.value = collections
                    if (collections.isNotEmpty() && _selectedCollectionId.value == null) {
                        val targetId = if (
                            initialCollectionId > 0 &&
                            collections.any { it.id == initialCollectionId }
                        ) {
                            initialCollectionId
                        } else {
                            collections[0].id
                        }
                        selectCollection(targetId)
                    }
                    _error.value = null
                },
                onFailure = { e ->
                    _error.value = localizedString(R.string.ci_collections_load_error_format, e.message.orEmpty())
                }
            )

            _isLoading.value = false
        }
    }

    fun selectCollection(collectionId: Int) {
        _selectedCollectionId.value = collectionId
        loadInsights(collectionId)
    }

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
                    _error.value = localizedString(R.string.ci_insights_load_error_format, e.message.orEmpty())
                }
            )

            _isLoading.value = false
        }
    }

    private fun generateSuggestions(insights: CollectionInsights) {
        val suggestions = mutableListOf<String>()

        val progressPercent = if (insights.totalItems > 0) {
            (insights.reviewedItems.toDouble() / insights.totalItems * 100).toInt()
        } else {
            0
        }

        val masteryPercent = if (insights.totalItems > 0) {
            (insights.masteredItems.toDouble() / insights.totalItems * 100).toInt()
        } else {
            0
        }

        when {
            insights.reviewedItems == 0 ->
                suggestions.add(localizedString(R.string.ci_suggestion_start_review))
            progressPercent < 50 ->
                suggestions.add(localizedString(R.string.ci_suggestion_progress_low, progressPercent))
            progressPercent < 100 ->
                suggestions.add(localizedString(R.string.ci_suggestion_progress_mid, 100 - progressPercent))
            else ->
                suggestions.add(localizedString(R.string.ci_suggestion_progress_done))
        }

        when {
            masteryPercent == 0 && insights.reviewedItems > 0 ->
                suggestions.add(localizedString(R.string.ci_suggestion_mastery_start))
            masteryPercent < 30 ->
                suggestions.add(localizedString(R.string.ci_suggestion_mastery_low, masteryPercent))
            masteryPercent < 70 ->
                suggestions.add(localizedString(R.string.ci_suggestion_mastery_mid, masteryPercent))
            masteryPercent >= 70 ->
                suggestions.add(localizedString(R.string.ci_suggestion_mastery_high, masteryPercent))
        }

        when {
            insights.successRate < 0.5 ->
                suggestions.add(localizedString(R.string.ci_suggestion_success_low))
            insights.successRate < 0.75 ->
                suggestions.add(localizedString(R.string.ci_suggestion_success_mid, (insights.successRate * 100).toInt()))
            insights.successRate >= 0.75 ->
                suggestions.add(localizedString(R.string.ci_suggestion_success_high, (insights.successRate * 100).toInt()))
        }

        if (insights.lastReviewDate != null) {
            val daysSinceReview = calculateDaysSince(insights.lastReviewDate)
            when {
                daysSinceReview == 0 ->
                    suggestions.add(localizedString(R.string.ci_suggestion_review_today))
                daysSinceReview == 1 ->
                    suggestions.add(localizedString(R.string.ci_suggestion_review_yesterday))
                daysSinceReview in 2..7 ->
                    suggestions.add(localizedString(R.string.ci_suggestion_review_days, daysSinceReview))
                daysSinceReview > 7 ->
                    suggestions.add(localizedString(R.string.ci_suggestion_review_many_days, daysSinceReview))
            }
        }

        when {
            insights.totalItems < 10 ->
                suggestions.add(localizedString(R.string.ci_suggestion_small_collection))
            insights.totalItems > 50 ->
                suggestions.add(localizedString(R.string.ci_suggestion_large_collection))
        }

        _suggestions.value = suggestions
    }

    private fun calculateDaysSince(dateStr: String): Int {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            sdf.isLenient = true
            val date = sdf.parse(dateStr) ?: return Int.MAX_VALUE
            val diff = System.currentTimeMillis() - date.time
            (diff / (1000 * 60 * 60 * 24)).toInt()
        } catch (e: Exception) {
            Int.MAX_VALUE
        }
    }

    fun refresh() {
        _selectedCollectionId.value?.let { collectionId ->
            loadInsights(collectionId)
        }
    }

    fun clearError() {
        _error.value = null
    }
}
