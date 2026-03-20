package com.duc.objectlanguage.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.duc.objectlanguage.ObjectLanguageApp
import com.duc.objectlanguage.data.model.HistoryItem
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as ObjectLanguageApp).repository

    private val _items = MutableLiveData<List<HistoryItem>>()
    val items: LiveData<List<HistoryItem>> = _items

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repo.getHistory()
            result.fold(
                onSuccess = { _items.value = it },
                onFailure = { _items.value = emptyList() }
            )
            _isLoading.value = false
        }
    }
}
