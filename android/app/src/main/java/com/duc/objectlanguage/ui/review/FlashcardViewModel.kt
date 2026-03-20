package com.duc.objectlanguage.ui.review

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.duc.objectlanguage.ObjectLanguageApp
import com.duc.objectlanguage.data.model.ReviewCardResponse
import com.duc.objectlanguage.data.model.ReviewRequest
import kotlinx.coroutines.launch

class FlashcardViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as ObjectLanguageApp).repository

    private val _cards = MutableLiveData<List<ReviewCardResponse>>()
    val cards: LiveData<List<ReviewCardResponse>> = _cards

    private val _currentIndex = MutableLiveData(0)
    val currentIndex: LiveData<Int> = _currentIndex

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _finished = MutableLiveData(false)
    val finished: LiveData<Boolean> = _finished

    private val results = mutableMapOf<Int, Int>() // progressId -> quality

    fun loadDueCards() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _finished.value = false
            _currentIndex.value = 0

            val result = repo.getDueReviews()
            result.fold(
                onSuccess = { cards ->
                    _cards.value = cards
                    if (cards.isEmpty()) {
                        _finished.value = true
                        _error.value = "No cards due for review!"
                    }
                },
                onFailure = { throwable ->
                    _error.value = "Failed to load cards: ${throwable.message}"
                    _finished.value = true
                }
            )
            _isLoading.value = false
        }
    }

    fun markKnown() {
        recordAnswer(5) // Perfect recall
    }

    fun markUnknown() {
        recordAnswer(0) // Complete blackout
    }

    fun markDifficult() {
        recordAnswer(2) // Incorrect but remembered
    }

    private fun recordAnswer(quality: Int) {
        val cardList = _cards.value ?: return
        val index = _currentIndex.value ?: return

        if (index >= cardList.size) return

        val card = cardList[index]
        results[card.progressId] = quality

        // Move to next card
        moveToNext()
    }

    private fun moveToNext() {
        val current = _currentIndex.value ?: 0
        val total = _cards.value?.size ?: 0

        if (current + 1 >= total) {
            // All cards reviewed, submit results
            submitResults()
        } else {
            _currentIndex.value = current + 1
        }
    }

    private fun submitResults() {
        viewModelScope.launch {
            _isLoading.value = true

            var successCount = 0
            var failCount = 0

            results.forEach { (progressId, quality) ->
                val result = repo.submitReview(progressId, quality)
                result.fold(
                    onSuccess = { successCount++ },
                    onFailure = { failCount++ }
                )
            }

            _isLoading.value = false
            _finished.value = true

            if (failCount > 0) {
                _error.value = "Some reviews failed to submit ($failCount failed)"
            }
        }
    }

    fun getScore(): Int {
        // Count quality >= 3 as correct
        return results.values.count { it >= 3 }
    }

    fun clearError() {
        _error.value = null
    }
}
