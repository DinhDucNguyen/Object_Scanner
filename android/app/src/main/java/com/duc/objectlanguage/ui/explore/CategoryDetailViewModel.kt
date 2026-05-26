package com.duc.objectlanguage.ui.explore

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.duc.objectlanguage.ObjectLanguageApp
import com.duc.objectlanguage.data.local.ApiConfig
import com.duc.objectlanguage.data.model.ObjectData
import java.net.URLEncoder
import com.duc.objectlanguage.utils.AudioPlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CategoryDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as ObjectLanguageApp).repository
    private val audioPlayer = AudioPlayerManager(application)

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

    fun playAudio(word: String) {
        val encoded = URLEncoder.encode(word, "UTF-8").replace("+", "%20")
        val url = "${ApiConfig.baseUrl}api/tts/${encoded}?lang=en"
        audioPlayer.playUrl(url) {}
    }
}
