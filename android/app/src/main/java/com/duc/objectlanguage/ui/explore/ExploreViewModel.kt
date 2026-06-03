package com.duc.objectlanguage.ui.explore

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.duc.objectlanguage.ObjectLanguageApp
import com.duc.objectlanguage.R
import com.duc.objectlanguage.data.model.CategoryData
import com.duc.objectlanguage.ui.common.localizedString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ExploreViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as ObjectLanguageApp).repository

    private val _categories = MutableLiveData<List<CategoryData>>(emptyList())
    val categories: LiveData<List<CategoryData>> = _categories

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun loadCategories() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)
            val result = repo.getCategories()
            if (result.isSuccess) {
                _categories.postValue(result.getOrNull().orEmpty())
            } else {
                _error.postValue(result.exceptionOrNull()?.message ?: localizedString(R.string.explore_load_error))
            }
            _isLoading.postValue(false)
        }
    }

    fun clearError() {
        _error.value = null
    }
}
