package com.duc.objectlanguage.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.duc.objectlanguage.ObjectLanguageApp
import com.duc.objectlanguage.data.model.TranslationResponse
import kotlinx.coroutines.launch

class HistoryDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as ObjectLanguageApp).repository

    private val _translations = MutableLiveData<List<TranslationResponse>>()
    val translations: LiveData<List<TranslationResponse>> = _translations

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

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
}
