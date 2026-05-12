package com.duc.objectlanguage.ui.dictionary

import android.app.Application
import android.media.MediaPlayer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.duc.objectlanguage.ObjectLanguageApp
import com.duc.objectlanguage.data.model.TranslateResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DictionaryViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as ObjectLanguageApp).repository

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _result = MutableLiveData<TranslateResponse?>()
    val result: LiveData<TranslateResponse?> = _result

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

    private val _fromLang = MutableLiveData("en")
    val fromLang: LiveData<String> = _fromLang

    private val _toLang = MutableLiveData("vi")
    val toLang: LiveData<String> = _toLang

    private var translateJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null
    private var lastRequestKey: String? = null

    fun loadDefaultLangs() {
        // App chỉ hỗ trợ EN ⇌ VI
        _fromLang.value = "en"
        _toLang.value = "vi"
    }

    fun swapLanguages(currentText: String) {
        val tmp = _fromLang.value ?: "en"
        _fromLang.value = _toLang.value ?: "vi"
        _toLang.value = tmp
        if (currentText.isNotBlank()) translate(currentText)
    }

    fun translate(text: String) {
        if (text.isBlank()) {
            _result.value = null
            return
        }
        translateJob?.cancel()
        translateJob = viewModelScope.launch(Dispatchers.IO) {
            val from = _fromLang.value ?: "en"
            val to = _toLang.value ?: "vi"
            val normalized = text.trim()
            val requestKey = "${from}:${to}:${normalized.lowercase()}"
            delay(800)
            if (requestKey == lastRequestKey && _result.value != null) return@launch
            lastRequestKey = requestKey
            _isLoading.postValue(true)
            _error.postValue(null)
            val response = repo.translate(normalized, from, to)
            if (response.isSuccess) {
                _result.postValue(response.getOrNull())
            } else {
                _result.postValue(null)
                _error.postValue(response.exceptionOrNull()?.message ?: "Lỗi dịch")
            }
            _isLoading.postValue(false)
        }
    }

    fun saveCurrentWord() {
        val translationId = _result.value?.translationId
        if (translationId == null || translationId <= 0) {
            _error.value = "Tu nay chua the luu"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val response = repo.addToLearning(translationId)
            if (response.isSuccess) {
                _message.postValue(response.getOrNull() ?: "Da luu tu vung")
            } else {
                _error.postValue(response.exceptionOrNull()?.message ?: "Loi luu tu vung")
            }
        }
    }

    fun playAudio(audioUrl: String?) {
        if (audioUrl.isNullOrEmpty()) {
            // Fallback: use TTS via the word in result
            _error.value = "Không có audio cho từ này"
            return
        }
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioUrl)
                prepareAsync()
                setOnPreparedListener { start() }
                setOnErrorListener { _, _, _ ->
                    _error.postValue("Lỗi phát audio")
                    true
                }
            }
        } catch (e: Exception) {
            _error.value = "Lỗi phát audio: ${e.message}"
        }
    }

    fun clearResult() {
        translateJob?.cancel()
        _result.value = null
        _error.value = null
        _isLoading.value = false
    }

    fun clearError() { _error.value = null }
    fun clearMessage() { _message.value = null }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
    }
}
