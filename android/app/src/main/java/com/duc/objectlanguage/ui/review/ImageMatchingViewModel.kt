package com.duc.objectlanguage.ui.review

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.duc.objectlanguage.ObjectLanguageApp
import com.duc.objectlanguage.R
import com.duc.objectlanguage.data.model.ReviewCardResponse
import com.duc.objectlanguage.data.model.ReviewRequest
import com.duc.objectlanguage.ui.common.localizedString
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class CardType {
    IMAGE, TEXT
}

data class MatchingCard(
    val id: Int,
    val progressId: Int,
    val content: String, // word or image_url
    val type: CardType,
    val isFlipped: Boolean = false,
    val isMatched: Boolean = false
)

class ImageMatchingViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as ObjectLanguageApp).repository

    private val _cards = MutableLiveData<List<MatchingCard>>()
    val cards: LiveData<List<MatchingCard>> = _cards

    private val _currentRound = MutableLiveData(0)
    val currentRound: LiveData<Int> = _currentRound

    private val _score = MutableLiveData(0)
    val score: LiveData<Int> = _score

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _emptyMessage = MutableLiveData<String?>()
    val emptyMessage: LiveData<String?> = _emptyMessage

    private val _finished = MutableLiveData(false)
    val finished: LiveData<Boolean> = _finished

    private val _gameStarted = MutableLiveData(false)
    val gameStarted: LiveData<Boolean> = _gameStarted

    private val _roundComplete = MutableLiveData(false)
    val roundComplete: LiveData<Boolean> = _roundComplete

    private var allReviewCards = listOf<ReviewCardResponse>()
    private var currentCards = mutableListOf<MatchingCard>()
    private var flippedCards = mutableListOf<Int>()
    private val results = mutableMapOf<Int, Int>() // progressId -> quality
    private var currentRoundIndex = 0
    private var totalRounds = 0

    fun loadGame() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _emptyMessage.value = null
            _finished.value = false
            _score.value = 0
            currentRoundIndex = 0

            val result = repo.getDueReviews()
            result.fold(
                onSuccess = { cards ->
                    if (cards.isEmpty()) {
                        _emptyMessage.value = localizedString(R.string.test_no_cards_matching)
                        _isLoading.value = false
                        return@fold
                    }

                    allReviewCards = cards.filter { !it.imageUrl.isNullOrBlank() }
                    if (allReviewCards.isEmpty()) {
                        _emptyMessage.value = localizedString(R.string.test_no_image_cards_matching)
                        _isLoading.value = false
                        return@fold
                    }
                    totalRounds = (allReviewCards.size + 2) / 3 // 3 pairs per round

                    // Start first round
                    _currentRound.value = 1
                    startRound(0)
                    _gameStarted.value = true
                },
                onFailure = { throwable ->
                    _error.value = localizedString(R.string.test_load_game_error, throwable.message.orEmpty())
                }
            )
            _isLoading.value = false
        }
    }

    private fun startRound(roundIndex: Int) {
        val start = roundIndex * 3
        val end = minOf(start + 3, allReviewCards.size)
        val roundCards = allReviewCards.subList(start, end)

        currentCards.clear()
        flippedCards.clear()

        // Create pairs (image + text)
        var cardId = 0
        roundCards.forEach { reviewCard ->
            // Image card
            currentCards.add(
                MatchingCard(
                    id = cardId++,
                    progressId = reviewCard.progressId,
                    content = reviewCard.imageUrl ?: "",
                    type = CardType.IMAGE
                )
            )
            // Text card
            currentCards.add(
                MatchingCard(
                    id = cardId++,
                    progressId = reviewCard.progressId,
                    content = reviewCard.wordName,
                    type = CardType.TEXT
                )
            )
        }

        // Shuffle
        currentCards.shuffle()

        _cards.value = currentCards.toList()
        _roundComplete.value = false
    }

    fun onCardClicked(position: Int) {
        if (flippedCards.size >= 2) return // Wait for flip back
        if (currentCards[position].isMatched) return
        if (currentCards[position].isFlipped) return

        // Flip card
        currentCards[position] = currentCards[position].copy(isFlipped = true)
        flippedCards.add(position)
        _cards.value = currentCards.toList()

        if (flippedCards.size == 2) {
            checkMatch()
        }
    }

    private fun checkMatch() {
        viewModelScope.launch {
            delay(500) // Show both cards

            val pos1 = flippedCards[0]
            val pos2 = flippedCards[1]
            val card1 = currentCards[pos1]
            val card2 = currentCards[pos2]

            val isMatch = card1.progressId == card2.progressId && card1.type != card2.type

            if (isMatch) {
                // Match found!
                currentCards[pos1] = card1.copy(isMatched = true)
                currentCards[pos2] = card2.copy(isMatched = true)
                _score.value = (_score.value ?: 0) + 10

                // Record quality
                results[card1.progressId] = 5
            } else {
                // No match - flip back
                currentCards[pos1] = card1.copy(isFlipped = false)
                currentCards[pos2] = card2.copy(isFlipped = false)
            }

            flippedCards.clear()
            _cards.value = currentCards.toList()

            // Check if round complete
            if (currentCards.all { it.isMatched }) {
                currentRoundIndex++
                if (currentRoundIndex >= totalRounds) {
                    // Game finished
                    submitResults()
                } else {
                    _roundComplete.value = true
                }
            }
        }
    }

    fun startNextRound() {
        _currentRound.value = currentRoundIndex + 1
        startRound(currentRoundIndex)
    }

    fun onTimeUp() {
        // Submit current results
        submitResults()
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

            // Mark unmatched as wrong
            val unmatchedIds = allReviewCards.map { it.progressId }.filter { it !in results.keys }
            unmatchedIds.forEach { progressId ->
                val result = repo.submitReview(progressId, 2)
                result.fold(
                    onSuccess = { successCount++ },
                    onFailure = { failCount++ }
                )
            }

            _isLoading.value = false
            _finished.value = true
        }
    }

    fun getTotalRounds(): Int = totalRounds

    fun clearError() {
        _error.value = null
    }
}
