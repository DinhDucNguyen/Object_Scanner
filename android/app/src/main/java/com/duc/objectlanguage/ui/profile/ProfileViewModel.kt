package com.duc.objectlanguage.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.duc.objectlanguage.ObjectLanguageApp
import com.duc.objectlanguage.data.model.UserSettingsResponse
import kotlinx.coroutines.launch

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as ObjectLanguageApp).repository

    private val _settings = MutableLiveData<UserSettingsResponse?>()
    val settings: LiveData<UserSettingsResponse?> = _settings

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _updateSuccess = MutableLiveData<String?>()
    val updateSuccess: LiveData<String?> = _updateSuccess

    fun loadSettings() {
        viewModelScope.launch {
            repo.getUserSettings().fold(
                onSuccess = { _settings.value = it },
                onFailure = { _error.value = it.message }
            )
        }
    }

    fun updateTargetLang(langId: Int) {
        viewModelScope.launch {
            val nativeId = _settings.value?.nativeLangId
            repo.updateUserSettings(nativeId, langId).fold(
                onSuccess = {
                    _settings.value = it
                    _updateSuccess.value = "Đã cập nhật ngôn ngữ học"
                },
                onFailure = { _error.value = it.message }
            )
        }
    }

    fun clearMessages() {
        _error.value = null
        _updateSuccess.value = null
    }
}
