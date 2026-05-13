package com.duc.objectlanguage.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.duc.objectlanguage.ObjectLanguageApp
import com.duc.objectlanguage.R
import com.duc.objectlanguage.data.model.ProfileData
import com.duc.objectlanguage.data.model.UserSettingsResponse
import kotlinx.coroutines.launch

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ObjectLanguageApp
    private val repo = app.repository

    private val _profile = MutableLiveData<ProfileData?>()
    val profile: LiveData<ProfileData?> = _profile

    private val _settings = MutableLiveData<UserSettingsResponse?>()
    val settings: LiveData<UserSettingsResponse?> = _settings

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    fun loadProfile() {
        viewModelScope.launch {
            repo.getProfile().fold(
                onSuccess = { _profile.value = it },
                onFailure = { _message.value = string(R.string.profile_error_format, it.message ?: "") }
            )
        }
    }

    fun updateBio(bio: String) {
        viewModelScope.launch {
            _isLoading.value = true
            repo.updateProfile(fullName = null, bio = bio).fold(
                onSuccess = {
                    _profile.value = it
                    _message.value = string(R.string.profile_bio_updated)
                },
                onFailure = { _message.value = string(R.string.profile_error_format, it.message ?: "") }
            )
            _isLoading.value = false
        }
    }

    fun uploadAvatar(imageBytes: ByteArray, filename: String) {
        viewModelScope.launch {
            _isLoading.value = true
            repo.uploadAvatar(imageBytes, filename).fold(
                onSuccess = {
                    loadProfile()
                    _message.value = string(R.string.profile_avatar_updated)
                },
                onFailure = { _message.value = string(R.string.profile_avatar_upload_error_format, it.message ?: "") }
            )
            _isLoading.value = false
        }
    }

    fun changePassword(current: String, new: String) {
        viewModelScope.launch {
            _isLoading.value = true
            repo.changePassword(current, new).fold(
                onSuccess = { _message.value = it },
                onFailure = { _message.value = it.message ?: string(R.string.profile_error_format, "") }
            )
            _isLoading.value = false
        }
    }

    fun loadSettings() {
        viewModelScope.launch {
            repo.getUserSettings().fold(
                onSuccess = { _settings.value = it },
                onFailure = { _message.value = it.message }
            )
        }
    }

    private fun string(resId: Int, vararg args: Any): String = app.getString(resId, *args)

    fun clearMessage() {
        _message.value = null
    }
}
