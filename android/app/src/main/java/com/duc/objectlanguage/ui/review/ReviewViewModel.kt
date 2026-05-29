package com.duc.objectlanguage.ui.review

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.duc.objectlanguage.ObjectLanguageApp
import com.duc.objectlanguage.R
import com.duc.objectlanguage.data.local.TokenManager
import com.duc.objectlanguage.data.model.ReviewCardResponse
import com.duc.objectlanguage.data.repository.CollectionRepository
import com.duc.objectlanguage.utils.AudioPlayerManager
import com.duc.objectlanguage.utils.LocaleHelper
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class ReviewSessionSummary(
    val total: Int = 0,
    val again: Int = 0,
    val hard: Int = 0,
    val good: Int = 0,
    val easy: Int = 0
) {
    val remembered: Int get() = good + easy
    val needsReview: Int get() = again + hard
    val rememberedPercent: Int
        get() = if (total == 0) 0 else (remembered * 100f / total).roundToInt()

    fun record(quality: Int): ReviewSessionSummary {
        return when {
            quality >= 5 -> copy(total = total + 1, easy = easy + 1)
            quality >= 4 -> copy(total = total + 1, good = good + 1)
            quality >= 3 -> copy(total = total + 1, hard = hard + 1)
            else -> copy(total = total + 1, again = again + 1)
        }
    }
}

class ReviewViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as ObjectLanguageApp).repository
    private val collectionRepo = CollectionRepository(TokenManager(application))

    private val _cards = MutableLiveData<List<ReviewCardResponse>>()
    val cards: LiveData<List<ReviewCardResponse>> = _cards

    private val _currentIndex = MutableLiveData(0)
    val currentIndex: LiveData<Int> = _currentIndex

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

    private val _finished = MutableLiveData(false)
    val finished: LiveData<Boolean> = _finished

    private val _finishedMessage = MutableLiveData<String>()
    val finishedMessage: LiveData<String> = _finishedMessage

    private val _sessionSummary = MutableLiveData(ReviewSessionSummary())
    val sessionSummary: LiveData<ReviewSessionSummary> = _sessionSummary

    private val _activeCollectionId = MutableLiveData(0)
    val activeCollectionId: LiveData<Int> = _activeCollectionId

    private val _activeCollectionName = MutableLiveData<String?>(null)
    val activeCollectionName: LiveData<String?> = _activeCollectionName

    private val audioPlayer = AudioPlayerManager(application.applicationContext)

    fun loadCards(collectionId: Int = 0, practice: Boolean = false, collectionName: String? = null) {
        _activeCollectionId.value = collectionId
        _activeCollectionName.value = collectionName
        viewModelScope.launch {
            _isLoading.value = true
            _finished.value = false
            _currentIndex.value = 0
            _sessionSummary.value = ReviewSessionSummary()
            val result = if (collectionId > 0) {
                if (practice) collectionRepo.getCollectionReviewCardsPractice(collectionId)
                else collectionRepo.getCollectionReviewCards(collectionId)
            } else {
                repo.getDueReviews()
            }
            result.fold(
                onSuccess = { cards ->
                    _cards.value = cards
                    if (cards.isEmpty()) {
                        _finishedMessage.value = if (collectionId > 0) {
                            "Bộ sưu tập này chưa có từ để ôn"
                        } else {
                            val stats = repo.getStats().getOrNull()
                            when {
                                stats == null -> localizedString(R.string.review_no_words_none)
                                stats.totalLearned == 0 -> localizedString(R.string.review_no_words_start)
                                else -> localizedString(R.string.review_finished_today)
                            }
                        }
                        _finished.value = true
                    }
                },
                onFailure = { _message.value = it.message }
            )
            _isLoading.value = false
        }
    }

    fun submitAnswer(quality: Int) {
        val cardList = _cards.value ?: return
        val idx = _currentIndex.value ?: 0
        if (idx >= cardList.size) return

        val card = cardList[idx]
        viewModelScope.launch {
            val result = repo.submitReview(card.progressId, quality)
            result.fold(
                onSuccess = {
                    _sessionSummary.value = (_sessionSummary.value ?: ReviewSessionSummary()).record(quality)
                    _message.value = if (quality >= 3) {
                        localizedString(R.string.review_msg_good)
                    } else {
                        localizedString(R.string.review_msg_again)
                    }
                    val nextIdx = idx + 1
                    if (nextIdx < cardList.size) {
                        _currentIndex.value = nextIdx
                    } else {
                        _finishedMessage.value = localizedString(R.string.review_finished)
                        _finished.value = true
                    }
                },
                onFailure = {
                    _message.value = localizedString(R.string.review_error_format, it.message.orEmpty())
                }
            )
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun playAudio(audioUrl: String?, word: String, lang: String = "en") {
        viewModelScope.launch {
            val bytes = audioUrl
                ?.takeIf { it.isNotBlank() }
                ?.let { repo.getAudioByUrl(it) }
                ?: repo.getTtsAudio(word, lang)
            if (bytes == null) {
                _message.value = "Không tải được audio"
                return@launch
            }

            audioPlayer.playMp3(bytes) {
                _message.value = "Không phát được audio"
            }
        }
    }

    private fun localizedString(@StringRes resId: Int, vararg args: Any): String {
        val context = LocaleHelper.applyLocale(getApplication<Application>())
        return if (args.isEmpty()) context.getString(resId) else context.getString(resId, *args)
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.release()
    }
}
