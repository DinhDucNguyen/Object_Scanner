package com.duc.objectlanguage.ui.explore

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.duc.objectlanguage.ObjectLanguageApp
import com.duc.objectlanguage.data.model.ObjectData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CategoryDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as ObjectLanguageApp).repository

    private val _objects = MutableLiveData<List<ObjectData>>(emptyList())
    val objects: LiveData<List<ObjectData>> = _objects

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun loadObjects(categoryId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)
            val result = repo.getObjectsByCategory(categoryId)
            if (result.isSuccess) {
                _objects.postValue(result.getOrNull().orEmpty())
            } else {
                _error.postValue(result.exceptionOrNull()?.message ?: "Loi tai tu vung")
            }
            _isLoading.postValue(false)
        }
    }

    fun clearError() {
        _error.value = null
    }
}
