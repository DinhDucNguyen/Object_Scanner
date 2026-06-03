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
import kotlinx.coroutines.launch
import kotlin.math.min

data class TypingQuestion(
    val progressId: Int,
    val questionWord: String, // Vietnamese
    val correctAnswer: String // English
)

data class TypingAnswerResult(
    val isCorrect: Boolean,
    val userAnswer: String,
    val correctAnswer: String
)

class TypingTestViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as ObjectLanguageApp).repository

    private val _currentQuestion = MutableLiveData<TypingQuestion?>()
    val currentQuestion: LiveData<TypingQuestion?> = _currentQuestion

    private val _answerResult = MutableLiveData<TypingAnswerResult?>()
    val answerResult: LiveData<TypingAnswerResult?> = _answerResult

    private val _currentIndex = MutableLiveData(0)
    val currentIndex: LiveData<Int> = _currentIndex

    private val _score = MutableLiveData(0)
    val score: LiveData<Int> = _score

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _finished = MutableLiveData(false)
    val finished: LiveData<Boolean> = _finished

    private val questions = mutableListOf<TypingQuestion>()
    private val results = mutableMapOf<Int, Int>() // progressId -> quality

    fun loadTest() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _finished.value = false
            _currentIndex.value = 0
            _score.value = 0

            val result = repo.getDueReviews()
            result.fold(
                onSuccess = { cards ->
                    if (cards.isEmpty()) {
                        _error.value = localizedString(R.string.test_no_cards_typing)
                        _finished.value = true
                        _isLoading.value = false
                        return@fold
                    }

                    // Generate questions
                    questions.clear()
                    cards.forEach { card ->
                        questions.add(
                            TypingQuestion(
                                progressId = card.progressId,
                                questionWord = card.definition ?: card.objectCode.replace("_", " "),
                                correctAnswer = card.wordName
                            )
                        )
                    }

                    // Show first question
                    if (questions.isNotEmpty()) {
                        _currentQuestion.value = questions[0]
                    }
                },
                onFailure = { throwable ->
                    _error.value = localizedString(R.string.test_load_practice_error, throwable.message.orEmpty())
                    _finished.value = true
                }
            )
            _isLoading.value = false
        }
    }

    fun submitAnswer(userAnswer: String) {
        val question = _currentQuestion.value ?: return

        // Use Levenshtein distance to check if answer is close enough
        val distance = levenshteinDistance(userAnswer.lowercase(), question.correctAnswer.lowercase())
        val tolerance = if (question.correctAnswer.length <= 4) 1 else 2
        val isCorrect = distance <= tolerance

        // Update score
        if (isCorrect) {
            _score.value = (_score.value ?: 0) + 1
        }

        // Record quality for SM-2
        results[question.progressId] = when {
            distance == 0 -> 5 // Perfect
            isCorrect -> 4     // Close enough
            else -> 2          // Wrong
        }

        // Show answer result
        _answerResult.value = TypingAnswerResult(
            isCorrect = isCorrect,
            userAnswer = userAnswer,
            correctAnswer = question.correctAnswer
        )
    }

    fun skipQuestion() {
        val question = _currentQuestion.value ?: return
        
        // Mark as wrong
        results[question.progressId] = 0

        // Show answer result
        _answerResult.value = TypingAnswerResult(
            isCorrect = false,
            userAnswer = "",
            correctAnswer = question.correctAnswer
        )
    }

    fun moveToNext() {
        _answerResult.value = null
        val current = _currentIndex.value ?: 0

        if (current + 1 >= questions.size) {
            // Test finished
            submitResults()
        } else {
            _currentIndex.value = current + 1
            _currentQuestion.value = questions[current + 1]
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
        }
    }

    fun getTotalQuestions(): Int = questions.size

    fun getFirstLetterHint(): String {
        return _currentQuestion.value?.correctAnswer?.first()?.toString() ?: ""
    }

    fun getWordLengthHint(): Int {
        return _currentQuestion.value?.correctAnswer?.length ?: 0
    }

    fun clearError() {
        _error.value = null
    }

    /**
     * Levenshtein Distance algorithm to measure typo tolerance
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length

        val dp = Array(len1 + 1) { IntArray(len2 + 1) }

        for (i in 0..len1) {
            dp[i][0] = i
        }
        for (j in 0..len2) {
            dp[0][j] = j
        }

        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,     // deletion
                    dp[i][j - 1] + 1,     // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }

        return dp[len1][len2]
    }
}
