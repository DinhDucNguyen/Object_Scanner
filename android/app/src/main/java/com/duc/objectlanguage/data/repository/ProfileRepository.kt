package com.duc.objectlanguage.data.repository

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
            ApiErrorParser.bodyResult(response, "Không tải được hồ sơ")
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, "Không tải được hồ sơ"))
        }
    }

    suspend fun updateProfile(username: String? = null, fullName: String? = null, bio: String? = null): Result<ProfileData> {
        return try {
            val response = api.updateProfile(ProfileUpdateRequest(username, fullName, bio))
            val profile = response.body()
            if (response.isSuccessful && profile != null) {
                profile.username?.takeIf { it.isNotBlank() }?.let { tokenManager.username = it }
                Result.success(profile)
            } else Result.failure(Exception(ApiErrorParser.fromResponse(response, "Cập nhật profile thất bại")))
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, "Không cập nhật được hồ sơ"))
        }
    }

    suspend fun uploadAvatar(imageBytes: ByteArray, filename: String): Result<String> {
        return try {
            val body = imageBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
            val part = okhttp3.MultipartBody.Part.createFormData("file", filename, body)
            val response = api.uploadAvatar(part)
            val avatar = response.body()?.avatarUrl
            if (response.isSuccessful && avatar != null) Result.success(avatar)
            else Result.failure(Exception(ApiErrorParser.fromResponse(response, "Không tải được ảnh đại diện")))
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, "Không tải được ảnh đại diện"))
        }
    }

    suspend fun getUserSettings(): Result<UserSettingsResponse> {
        return try {
            val response = api.getSettings()
            ApiErrorParser.bodyResult(response, "Không tải được cài đặt")
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, "Không tải được cài đặt"))
        }
    }

    suspend fun updateUserSettings(displayLanguage: String): Result<UserSettingsResponse> {
        return try {
            val response = api.updateSettings(UserSettingsUpdate(displayLanguage = displayLanguage))
            ApiErrorParser.bodyResult(response, "Không cập nhật được cài đặt")
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, "Không cập nhật được cài đặt"))
        }
    }

    suspend fun updateDarkMode(darkMode: Boolean): Result<UserSettingsResponse> {
        return try {
            val response = api.updateSettings(UserSettingsUpdate(darkMode = darkMode))
            ApiErrorParser.bodyResult(response, "Không cập nhật được cài đặt")
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, "Không cập nhật được cài đặt"))
        }
    }
}
