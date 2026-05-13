package com.duc.objectlanguage.ui.review

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.duc.objectlanguage.ObjectLanguageApp
import com.duc.objectlanguage.R
import com.duc.objectlanguage.data.model.ReviewCardResponse
import com.duc.objectlanguage.utils.LocaleHelper
import kotlinx.coroutines.launch

class ReviewViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as ObjectLanguageApp).repository

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

    fun loadCards() {
        viewModelScope.launch {
            _isLoading.value = true
            _finished.value = false
            _currentIndex.value = 0
            val result = repo.getDueReviews()
            result.fold(
                onSuccess = { cards ->
                    _cards.value = cards
                    if (cards.isEmpty()) {
                        val stats = repo.getStats().getOrNull()
                        _finishedMessage.value = when {
                            stats == null -> localizedString(R.string.review_no_words_none)
                            stats.totalLearned == 0 -> localizedString(R.string.review_no_words_start)
                            else -> localizedString(R.string.review_finished_today)
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

    private fun localizedString(@StringRes resId: Int, vararg args: Any): String {
        val context = LocaleHelper.applyLocale(getApplication<Application>())
        return if (args.isEmpty()) context.getString(resId) else context.getString(resId, *args)
    }
}
