package com.duc.objectlanguage.ui.auth

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.duc.objectlanguage.ObjectLanguageApp
import com.duc.objectlanguage.R
import com.duc.objectlanguage.ui.common.localizedString
import kotlinx.coroutines.launch

data class LoginUiState(
    val loginLoading: Boolean = false,
    val googleLoading: Boolean = false,
) {
    val isLoading: Boolean get() = loginLoading || googleLoading
}

sealed class LoginEvent {
    data object Success : LoginEvent()
    data class Error(val message: String) : LoginEvent()
}

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ObjectLanguageApp
    private val repo = app.repository

    private val _state = MutableLiveData(LoginUiState())
    val state: LiveData<LoginUiState> = _state

    private val _event = MutableLiveData<LoginEvent?>()
    val event: LiveData<LoginEvent?> = _event

    fun login(username: String, password: String) {
        if (_state.value?.isLoading == true) return
        _state.value = LoginUiState(loginLoading = true)
        viewModelScope.launch {
            val result = repo.login(username, password)
            result.fold(
                onSuccess = {
                    syncAccountDarkMode()
                    _event.value = LoginEvent.Success
                },
                onFailure = {
                    _event.value = LoginEvent.Error(it.message ?: localizedString(R.string.login_error_failed))
                }
            )
            _state.value = LoginUiState()
        }
    }

    fun googleLogin(idToken: String) {
        if (_state.value?.isLoading == true) return
        _state.value = LoginUiState(googleLoading = true)
        viewModelScope.launch {
            val result = repo.googleLogin(idToken)
            result.fold(
                onSuccess = {
                    syncAccountDarkMode()
                    _event.value = LoginEvent.Success
                },
                onFailure = {
                    _event.value = LoginEvent.Error(it.message ?: localizedString(R.string.login_error_google_failed))
                }
            )
            _state.value = LoginUiState()
        }
    }

    fun consumeEvent() {
        _event.value = null
    }

    private suspend fun syncAccountDarkMode() {
        val prefs = getApplication<Application>().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        repo.getUserSettings().onSuccess { settings ->
            val isDark = settings.darkMode
            prefs.edit().putBoolean("dark_mode", isDark).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }
    }
}
