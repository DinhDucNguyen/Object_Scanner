package com.duc.objectlanguage.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.duc.objectlanguage.ObjectLanguageApp
import com.duc.objectlanguage.R
import com.duc.objectlanguage.data.model.HistoryDetail
import com.duc.objectlanguage.data.model.TranslationResponse
import com.duc.objectlanguage.utils.AudioPlayerManager
import kotlinx.coroutines.launch

class HistoryDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as ObjectLanguageApp).repository
    private val audioPlayer = AudioPlayerManager(application.applicationContext)

    private val _translations = MutableLiveData<List<TranslationResponse>>()
    val translations: LiveData<List<TranslationResponse>> = _translations

    private val _detail = MutableLiveData<HistoryDetail?>()
    val detail: LiveData<HistoryDetail?> = _detail

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun loadTranslations(objectCode: String) {
        viewModelScope.launch {
            _isLoading.value = true
            repo.getTranslationsByCode(objectCode).fold(
                onSuccess = { _translations.value = it },
                onFailure = { _translations.value = emptyList() }
            )
            _isLoading.value = false
        }
    }

    fun loadDetail(scanId: Int, fallbackObjectCode: String) {
        if (scanId <= 0) {
            loadTranslations(fallbackObjectCode)
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            repo.getHistoryDetail(scanId).fold(
                onSuccess = {
                    _detail.value = it
                    _translations.value = it.translations
                },
                onFailure = {
                    _error.value = it.message ?: "Khong tai duoc chi tiet lich su"
                    if (fallbackObjectCode.isNotBlank()) loadTranslations(fallbackObjectCode)
                }
            )
            _isLoading.value = false
        }
    }

    fun playAudio(audioUrl: String?, word: String?, lang: String = "en") {
        if (audioUrl.isNullOrBlank() && word.isNullOrBlank()) {
            _error.value = getApplication<Application>().getString(R.string.history_audio_load_error)
            return
        }

        viewModelScope.launch {
            val bytes = audioUrl
                ?.takeIf { it.isNotBlank() }
                ?.let { repo.getAudioByUrl(it) }
                ?: word
                    ?.takeIf { it.isNotBlank() }
                    ?.let { repo.getTtsAudio(it, lang) }

            if (bytes == null) {
                _error.value = getApplication<Application>().getString(R.string.history_audio_load_error)
                return@launch
            }

            audioPlayer.playMp3(bytes) {
                _error.value = getApplication<Application>().getString(R.string.history_audio_play_error)
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.release()
    }
}
