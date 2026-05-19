package com.duc.objectlanguage.ui.history

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.duc.objectlanguage.ObjectLanguageApp
import com.duc.objectlanguage.R
import com.duc.objectlanguage.data.model.HistoryDetail
import com.duc.objectlanguage.data.model.TranslationResponse
import com.duc.objectlanguage.data.repository.CollectionRepository
import com.duc.objectlanguage.utils.AudioPlayerManager
import kotlinx.coroutines.launch

class HistoryDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ObjectLanguageApp
    private val repo = app.repository
    private val collectionRepo = CollectionRepository(app.tokenManager)
    private val audioPlayer = AudioPlayerManager(application.applicationContext)

    private val _translations = MutableLiveData<List<TranslationResponse>>()
    val translations: LiveData<List<TranslationResponse>> = _translations

    private val _detail = MutableLiveData<HistoryDetail?>()
    val detail: LiveData<HistoryDetail?> = _detail

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _addedMsg = MutableLiveData<String?>()
    val addedMsg: LiveData<String?> = _addedMsg

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

    fun showAddToCollectionDialog(translationId: Int, context: Context) {
        viewModelScope.launch {
            val result = collectionRepo.getCollections()
            val collections = result.getOrNull()
            if (collections.isNullOrEmpty()) {
                _addedMsg.value = context.getString(R.string.collection_no_collections)
                return@launch
            }

            val labels = collections.map { it.name }.toTypedArray()
            AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.collection_add_to_title))
                .setItems(labels) { _, which ->
                    val collection = collections[which]
                    viewModelScope.launch {
                        val addResult = collectionRepo.addToCollection(collection.id, translationId)
                        _addedMsg.value = if (addResult.isSuccess) {
                            context.getString(R.string.collection_added_to, collection.name)
                        } else {
                            context.getString(
                                R.string.review_error_format,
                                addResult.exceptionOrNull()?.message.orEmpty(),
                            )
                        }
                    }
                }
                .setNegativeButton(context.getString(R.string.btn_cancel), null)
                .show()
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun clearAddedMsg() {
        _addedMsg.value = null
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.release()
    }
}
