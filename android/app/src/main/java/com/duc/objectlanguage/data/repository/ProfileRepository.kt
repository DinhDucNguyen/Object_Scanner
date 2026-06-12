package com.duc.objectlanguage.data.repository

import com.duc.objectlanguage.R
import com.duc.objectlanguage.data.api.RetrofitClient
import com.duc.objectlanguage.data.local.TokenManager
import com.duc.objectlanguage.data.model.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class ProfileRepository(private val tokenManager: TokenManager) {

    private val api get() = RetrofitClient.api

    suspend fun getProfile(): Result<ProfileData> {
        return try {
            val response = api.getProfile()
            ApiErrorParser.bodyResult(response, R.string.repo_profile_load_error)
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, R.string.repo_profile_load_error))
        }
    }

    suspend fun updateProfile(username: String? = null, fullName: String? = null, bio: String? = null): Result<ProfileData> {
        return try {
            val response = api.updateProfile(ProfileUpdateRequest(username, fullName, bio))
            val profile = response.body()
            if (response.isSuccessful && profile != null) {
                profile.username?.takeIf { it.isNotBlank() }?.let { tokenManager.username = it }
                Result.success(profile)
            } else Result.failure(Exception(ApiErrorParser.fromResponse(response, R.string.repo_profile_update_failed)))
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, R.string.repo_profile_update_error))
        }
    }

    suspend fun uploadAvatar(imageBytes: ByteArray, filename: String): Result<String> {
        return try {
            val body = imageBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
            val part = okhttp3.MultipartBody.Part.createFormData("file", filename, body)
            val response = api.uploadAvatar(part)
            val avatar = response.body()?.avatarUrl
            if (response.isSuccessful && avatar != null) Result.success(avatar)
            else Result.failure(Exception(ApiErrorParser.fromResponse(response, R.string.repo_profile_avatar_load_error)))
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, R.string.repo_profile_avatar_load_error))
        }
    }

    suspend fun getUserSettings(): Result<UserSettingsResponse> {
        return try {
            val response = api.getSettings()
            ApiErrorParser.bodyResult(response, R.string.repo_profile_settings_load_error)
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, R.string.repo_profile_settings_load_error))
        }
    }

    suspend fun updateUserSettings(displayLanguage: String): Result<UserSettingsResponse> {
        return try {
            val response = api.updateSettings(UserSettingsUpdate(displayLanguage = displayLanguage))
            ApiErrorParser.bodyResult(response, R.string.repo_profile_settings_update_error)
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, R.string.repo_profile_settings_update_error))
        }
    }

    suspend fun updateDarkMode(darkMode: Boolean): Result<UserSettingsResponse> {
        return try {
            val response = api.updateSettings(UserSettingsUpdate(darkMode = darkMode))
            ApiErrorParser.bodyResult(response, R.string.repo_profile_settings_update_error)
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, R.string.repo_profile_settings_update_error))
        }
    }
}
