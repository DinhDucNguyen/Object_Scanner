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

data class QuizQuestion(
    val progressId: Int,
    val questionWord: String, // Vietnamese
    val correctAnswer: String, // English
    val options: List<String>, // 4 options shuffled
    val correctIndex: Int
)

data class AnswerResult(
    val isCorrect: Boolean,
    val selectedIndex: Int,
    val correctIndex: Int
)

class QuizViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as ObjectLanguageApp).repository

    private val _currentQuestion = MutableLiveData<QuizQuestion?>()
    val currentQuestion: LiveData<QuizQuestion?> = _currentQuestion

    private val _answerResult = MutableLiveData<AnswerResult?>()
    val answerResult: LiveData<AnswerResult?> = _answerResult

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

    private val questions = mutableListOf<QuizQuestion>()
    private val results = mutableMapOf<Int, Int>() // progressId -> quality
    private var allCards = listOf<ReviewCardResponse>()

    fun loadQuiz() {
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
                        _error.value = localizedString(R.string.test_no_cards_quiz)
                        _finished.value = true
                        _isLoading.value = false
                        return@fold
                    }

                    allCards = cards

                    // Generate questions
                    questions.clear()
                    cards.forEach { card ->
                        val question = generateQuestion(card, cards)
                        questions.add(question)
                    }

                    // Show first question
                    if (questions.isNotEmpty()) {
                        _currentQuestion.value = questions[0]
                    }
                },
                onFailure = { throwable ->
                    _error.value = localizedString(R.string.test_load_quiz_error, throwable.message.orEmpty())
                    _finished.value = true
                }
            )
            _isLoading.value = false
        }
    }

    private fun generateQuestion(
        card: ReviewCardResponse,
        allCards: List<ReviewCardResponse>
    ): QuizQuestion {
        // Get 3 random wrong answers (distractors)
        val distractors = allCards
            .filter { it.progressId != card.progressId && it.wordName != card.wordName }
            .shuffled()
            .take(3)
            .map { it.wordName }

        // If not enough distractors, use generic ones
        val finalDistractors = if (distractors.size < 3) {
            val generic = listOf("House", "Water", "Book", "Computer", "Phone", "Cat", "Dog")
                .filter { it != card.wordName }
                .shuffled()
                .take(3 - distractors.size)
            distractors + generic
        } else {
            distractors
        }

        // Combine with correct answer and shuffle
        val options = (finalDistractors + card.wordName).shuffled()
        val correctIndex = options.indexOf(card.wordName)

        return QuizQuestion(
            progressId = card.progressId,
            questionWord = card.definition ?: card.objectCode.replace("_", " "),
            correctAnswer = card.wordName,
            options = options,
            correctIndex = correctIndex
        )
    }

    fun submitAnswer(selectedIndex: Int) {
        val question = _currentQuestion.value ?: return
        val isCorrect = (selectedIndex == question.correctIndex)

        // Update score
        if (isCorrect) {
            _score.value = (_score.value ?: 0) + 1
        }

        // Record quality for SM-2
        results[question.progressId] = if (isCorrect) 5 else 2

        // Show answer result
        _answerResult.value = AnswerResult(
            isCorrect = isCorrect,
            selectedIndex = selectedIndex,
            correctIndex = question.correctIndex
        )
    }

    fun moveToNext() {
        _answerResult.value = null
        val current = _currentIndex.value ?: 0

        if (current + 1 >= questions.size) {
            // Quiz finished
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

    fun clearError() {
        _error.value = null
    }
}
