package com.duc.objectlanguage.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.duc.objectlanguage.ObjectLanguageApp
import com.duc.objectlanguage.data.model.HistoryDetail
import com.duc.objectlanguage.data.model.TranslationResponse
import kotlinx.coroutines.launch

class HistoryDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as ObjectLanguageApp).repository

    private val _translations = MutableLiveData<List<TranslationResponse>>()
    val translations: LiveData<List<TranslationResponse>> = _translations

    private val _detail = MutableLiveData<HistoryDetail?>()
    val detail: LiveData<HistoryDetail?> = _detail

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

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

    fun addToLearning(translationId: Int?) {
        if (translationId == null || translationId <= 0) {
            _error.value = "Tu nay chua the luu"
            return
        }

        viewModelScope.launch {
            repo.addToLearning(translationId).fold(
                onSuccess = { _message.value = it },
                onFailure = { _error.value = it.message ?: "Khong luu duoc tu" }
            )
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun clearError() {
        _error.value = null
    }
}
