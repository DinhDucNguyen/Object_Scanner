package com.duc.objectlanguage.ui.review

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.duc.objectlanguage.ObjectLanguageApp
import com.duc.objectlanguage.data.model.ReviewCardResponse
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

    fun loadCards() {
        viewModelScope.launch {
            _isLoading.value = true
            _finished.value = false
            _currentIndex.value = 0
            val result = repo.getDueReviews()
            result.fold(
                onSuccess = {
                    _cards.value = it
                    if (it.isEmpty()) _finished.value = true
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
                    _message.value = if (quality >= 3) "✓ Tốt lắm!" else "Sẽ ôn lại sớm"
                    val nextIdx = idx + 1
                    if (nextIdx < cardList.size) {
                        _currentIndex.value = nextIdx
                    } else {
                        _finished.value = true
                    }
                },
                onFailure = { _message.value = "Lỗi: ${it.message}" }
            )
        }
    }

    fun clearMessage() { _message.value = null }
}
