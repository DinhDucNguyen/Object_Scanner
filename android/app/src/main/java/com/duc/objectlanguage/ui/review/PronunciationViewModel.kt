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

data class PronunciationWord(
    val progressId: Int,
    val wordName: String,
    val phonetic: String,
    val definition: String
)

data class PronunciationResult(
    val userSpeech: String,
    val expectedWord: String,
    val score: Int, // 0-100
    val isCorrect: Boolean
)

class PronunciationViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as ObjectLanguageApp).repository

    private val _currentWord = MutableLiveData<PronunciationWord?>()
    val currentWord: LiveData<PronunciationWord?> = _currentWord

    private val _pronunciationResult = MutableLiveData<PronunciationResult?>()
    val pronunciationResult: LiveData<PronunciationResult?> = _pronunciationResult

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

    private val words = mutableListOf<PronunciationWord>()
    private val results = mutableMapOf<Int, Int>() // progressId -> quality

    fun loadPractice() {
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
                        _error.value = localizedString(R.string.test_no_cards_pronunciation)
                        _finished.value = true
                        _isLoading.value = false
                        return@fold
                    }

                    // Generate words
                    words.clear()
                    cards.forEach { card ->
                        words.add(
                            PronunciationWord(
                                progressId = card.progressId,
                                wordName = card.wordName,
                                phonetic = card.phonetic ?: "",
                                definition = card.definition ?: card.objectCode.replace("_", " ")
                            )
                        )
                    }

                    // Show first word
                    if (words.isNotEmpty()) {
                        _currentWord.value = words[0]
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

    fun evaluatePronunciation(userSpeech: String) {
        val word = _currentWord.value ?: return
        val expectedWord = word.wordName.lowercase()
        val spokenWord = userSpeech.lowercase()

        // Calculate similarity score using multiple metrics
        val score = calculatePronunciationScore(spokenWord, expectedWord)
        val isCorrect = score >= 70 // 70% threshold for "correct"

        // Update score
        if (isCorrect) {
            _score.value = (_score.value ?: 0) + 1
        }

        // Record quality for SM-2
        results[word.progressId] = when {
            score >= 90 -> 5 // Perfect
            score >= 70 -> 4 // Good
            score >= 50 -> 3 // Okay
            else -> 2         // Needs practice
        }

        // Show result
        _pronunciationResult.value = PronunciationResult(
            userSpeech = userSpeech,
            expectedWord = word.wordName,
            score = score,
            isCorrect = isCorrect
        )
    }

    fun moveToNext() {
        _pronunciationResult.value = null
        val current = _currentIndex.value ?: 0

        if (current + 1 >= words.size) {
            // Practice finished
            submitResults()
        } else {
            _currentIndex.value = current + 1
            _currentWord.value = words[current + 1]
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

    fun getTotalWords(): Int = words.size

    fun clearError() {
        _error.value = null
    }

    /**
     * Calculate pronunciation score (0-100) using multiple similarity metrics
     */
    private fun calculatePronunciationScore(spoken: String, expected: String): Int {
        // Exact match
        if (spoken == expected) return 100

        // Normalize strings (remove punctuation, extra spaces)
        val normalizedSpoken = normalizeForComparison(spoken)
        val normalizedExpected = normalizeForComparison(expected)

        if (normalizedSpoken == normalizedExpected) return 100

        // Calculate multiple similarity metrics
        val levenshteinScore = calculateLevenshteinSimilarity(normalizedSpoken, normalizedExpected)
        val jaroScore = calculateJaroSimilarity(normalizedSpoken, normalizedExpected)
        val longestCommonSubsequenceScore = calculateLCSSimilarity(normalizedSpoken, normalizedExpected)

        // Weighted average (Jaro-Winkler is best for pronunciation)
        val finalScore = (levenshteinScore * 0.3 + jaroScore * 0.5 + longestCommonSubsequenceScore * 0.2).toInt()

        return finalScore.coerceIn(0, 100)
    }

    private fun normalizeForComparison(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "") // Remove punctuation
            .trim()
            .replace(Regex("\\s+"), " ") // Normalize spaces
    }

    /**
     * Levenshtein distance converted to similarity percentage
     */
    private fun calculateLevenshteinSimilarity(s1: String, s2: String): Int {
        val distance = levenshteinDistance(s1, s2)
        val maxLength = maxOf(s1.length, s2.length)
        if (maxLength == 0) return 100
        return ((1.0 - distance.toDouble() / maxLength) * 100).toInt()
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length

        val dp = Array(len1 + 1) { IntArray(len2 + 1) }

        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j

        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }

        return dp[len1][len2]
    }

    /**
     * Jaro similarity (0-100) - good for pronunciation similarity
     */
    private fun calculateJaroSimilarity(s1: String, s2: String): Int {
        if (s1 == s2) return 100
        if (s1.isEmpty() || s2.isEmpty()) return 0

        val matchDistance = maxOf(s1.length, s2.length) / 2 - 1
        val s1Matches = BooleanArray(s1.length)
        val s2Matches = BooleanArray(s2.length)

        var matches = 0
        for (i in s1.indices) {
            val start = maxOf(0, i - matchDistance)
            val end = minOf(i + matchDistance + 1, s2.length)
            for (j in start until end) {
                if (s2Matches[j] || s1[i] != s2[j]) continue
                s1Matches[i] = true
                s2Matches[j] = true
                matches++
                break
            }
        }

        if (matches == 0) return 0

        var transpositions = 0
        var k = 0
        for (i in s1.indices) {
            if (!s1Matches[i]) continue
            while (!s2Matches[k]) k++
            if (s1[i] != s2[k]) transpositions++
            k++
        }

        val jaro = (matches.toDouble() / s1.length +
                matches.toDouble() / s2.length +
                (matches - transpositions / 2.0) / matches) / 3.0

        return (jaro * 100).toInt()
    }

    /**
     * Longest Common Subsequence similarity (0-100)
     */
    private fun calculateLCSSimilarity(s1: String, s2: String): Int {
        val lcsLength = longestCommonSubsequence(s1, s2)
        val maxLength = maxOf(s1.length, s2.length)
        if (maxLength == 0) return 100
        return (lcsLength.toDouble() / maxLength * 100).toInt()
    }

    private fun longestCommonSubsequence(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 1..m) {
            for (j in 1..n) {
                if (s1[i - 1] == s2[j - 1]) {
                    dp[i][j] = dp[i - 1][j - 1] + 1
                } else {
                    dp[i][j] = maxOf(dp[i - 1][j], dp[i][j - 1])
                }
            }
        }

        return dp[m][n]
    }
}
