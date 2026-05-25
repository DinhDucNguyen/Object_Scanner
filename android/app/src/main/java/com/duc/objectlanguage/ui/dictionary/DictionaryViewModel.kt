package com.duc.objectlanguage.ui.dictionary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.duc.objectlanguage.ObjectLanguageApp
import com.duc.objectlanguage.data.model.DictionaryHistoryItem
import com.duc.objectlanguage.data.model.TranslateResponse
import com.duc.objectlanguage.utils.AudioPlayerManager
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

    private val _history = MutableLiveData<List<DictionaryHistoryItem>>(emptyList())
    val history: LiveData<List<DictionaryHistoryItem>> = _history

    private var translateJob: Job? = null
    private val audioPlayer = AudioPlayerManager(application.applicationContext)
    private var lastRequestKey: String? = null

    data class PronunciationTarget(val text: String, val lang: String)

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

    fun pronunciationTarget(result: TranslateResponse?): PronunciationTarget? {
        result ?: return null
        val candidates = listOf(
            result.translation to result.toLang,
            result.original to result.fromLang,
        )
        return candidates
            .map { (text, lang) -> text.trim() to lang.trim().lowercase() }
            .sortedBy { (_, lang) -> if (lang == "en") 0 else 1 }
            .firstOrNull { (text, _) -> isShortPronounceableText(text) }
            ?.let { (text, lang) -> PronunciationTarget(text, lang.ifBlank { "en" }) }
    }


    fun playAudio(target: PronunciationTarget?) {
        if (target == null) {
            _error.value = "Câu quá dài nên không tạo phát âm"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val bytes = repo.getTtsAudio(target.text, target.lang)
            if (bytes == null) {
                _error.postValue("Không tải được audio")
                return@launch
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                audioPlayer.playMp3(bytes) {
                    _error.postValue("Lỗi phát audio")
                }
            }
        }
    }

    private fun isShortPronounceableText(text: String): Boolean {
        if (text.isBlank() || text.length > MAX_PRONUNCIATION_CHARS) return false
        return text.split(Regex("\\s+")).size <= MAX_PRONUNCIATION_WORDS
    }

    fun loadHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = repo.getDictionaryHistory()
            if (result.isSuccess) {
                _history.postValue(result.getOrNull() ?: emptyList())
            }
        }
    }

    fun deleteHistoryItem(id: Int) {
        _history.value = (_history.value ?: emptyList()).filter { it.id != id }
        viewModelScope.launch(Dispatchers.IO) {
            val deleted = repo.deleteHistoryItem(id)
            if (!deleted.isSuccess) {
                val result = repo.getDictionaryHistory()
                if (result.isSuccess) {
                    _history.postValue(result.getOrNull() ?: emptyList())
                }
            }
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
        audioPlayer.release()
    }

    companion object {
        private const val MAX_PRONUNCIATION_CHARS = 60
        private const val MAX_PRONUNCIATION_WORDS = 6
    }
}
