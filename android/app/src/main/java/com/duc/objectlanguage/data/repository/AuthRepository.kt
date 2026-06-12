package com.duc.objectlanguage.data.repository

import com.duc.objectlanguage.R
import com.duc.objectlanguage.data.api.RetrofitClient
import com.duc.objectlanguage.data.local.TokenManager
import com.duc.objectlanguage.data.model.*

class AuthRepository(private val tokenManager: TokenManager) {

    private val api get() = RetrofitClient.api

    suspend fun login(username: String, password: String): Result<TokenResponse> {
        return try {
            val response = api.login(LoginRequest(username, password))
            val body = response.body()
            if (response.isSuccessful && body != null) {
                tokenManager.accessToken = body.accessToken
                tokenManager.refreshToken = body.refreshToken
                tokenManager.username = body.user.username
                tokenManager.userId = body.user.id
                tokenManager.role = body.user.role
                tokenManager.isGoogleAccount = false
                Result.success(body)
            } else {
                Result.failure(Exception(ApiErrorParser.fromResponse(response, R.string.repo_auth_invalid_credentials)))
            }
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, R.string.repo_error_connection_server))
        }
    }

    suspend fun googleLogin(idToken: String): Result<TokenResponse> {
        return try {
            val response = api.googleLogin(GoogleLoginRequest(idToken))
            val body = response.body()
            if (response.isSuccessful && body != null) {
                tokenManager.accessToken = body.accessToken
                tokenManager.refreshToken = body.refreshToken
                tokenManager.username = body.user.username
                tokenManager.userId = body.user.id
                tokenManager.role = body.user.role
                tokenManager.isGoogleAccount = true
                Result.success(body)
            } else {
                Result.failure(Exception(ApiErrorParser.fromResponse(response, R.string.repo_auth_google_login_failed)))
            }
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, R.string.repo_error_connection_server))
        }
    }

    suspend fun register(username: String, email: String, password: String, fullName: String? = null): Result<String> {
        return try {
            val response = api.register(RegisterRequest(username, email, password, fullName))
            if (response.isSuccessful) Result.success(RepositoryText.get(R.string.repo_auth_register_success))
            else Result.failure(Exception(ApiErrorParser.fromResponse(response, R.string.repo_auth_register_failed)))
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, R.string.repo_error_connection_server))
        }
    }

    suspend fun resendRegistrationOtp(email: String): Result<String> {
        return try {
            val response = api.resendRegistrationOtp(ForgotPasswordRequest(email))
            if (response.isSuccessful) Result.success(RepositoryText.get(R.string.repo_auth_resend_otp_success))
            else Result.failure(Exception(ApiErrorParser.fromResponse(response, R.string.repo_auth_resend_otp_failed)))
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, R.string.repo_error_connection_server))
        }
    }

    suspend fun verifyRegistrationOtp(email: String, otpCode: String): Result<String> {
        return try {
            val response = api.verifyRegistrationOtp(VerifyOtpRequest(email, otpCode))
            if (response.isSuccessful) Result.success(RepositoryText.get(R.string.repo_auth_verify_email_success))
            else Result.failure(Exception(ApiErrorParser.fromResponse(response, R.string.repo_auth_otp_invalid_expired)))
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, R.string.repo_error_connection_server))
        }
    }

    suspend fun forgotPassword(email: String): Result<ForgotPasswordResponse> {
        return try {
            val response = api.forgotPassword(ForgotPasswordRequest(email))
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) Result.success(body.copy(message = RepositoryText.get(R.string.repo_auth_forgot_password_sent)))
                else Result.failure(Exception(RepositoryText.get(R.string.repo_auth_invalid_response)))
            } else {
                Result.failure(Exception(ApiErrorParser.fromResponse(response, R.string.repo_auth_send_otp_failed)))
            }
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, R.string.repo_error_connection_server))
        }
    }

    suspend fun verifyOtp(email: String, otpCode: String): Result<String> {
        return try {
            val response = api.verifyOtp(VerifyOtpRequest(email, otpCode))
            if (response.isSuccessful) Result.success(RepositoryText.get(R.string.repo_auth_otp_valid))
            else Result.failure(Exception(RepositoryText.get(R.string.repo_auth_otp_invalid_expired)))
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, R.string.repo_error_connection_server))
        }
    }

    suspend fun resetPassword(email: String, otpCode: String, newPassword: String): Result<String> {
        return try {
            val response = api.resetPassword(ResetPasswordRequest(email, otpCode, newPassword))
            if (response.isSuccessful) Result.success(RepositoryText.get(R.string.repo_auth_reset_password_success))
            else Result.failure(Exception(RepositoryText.get(R.string.repo_auth_reset_password_failed)))
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, R.string.repo_error_connection_server))
        }
    }

    suspend fun changePassword(currentPassword: String, newPassword: String): Result<String> {
        return try {
            val response = api.changePassword(ChangePasswordRequest(currentPassword, newPassword))
            if (response.isSuccessful) Result.success(RepositoryText.get(R.string.repo_auth_change_password_success))
            else Result.failure(Exception(ApiErrorParser.fromResponse(response, R.string.repo_auth_change_password_failed)))
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, R.string.repo_error_connection_server))
        }
    }

    suspend fun deleteAccount(password: String): Result<String> {
        return try {
            val response = api.deleteAccount(DeleteAccountRequest(password))
            if (response.isSuccessful) {
                tokenManager.clear()
                Result.success(RepositoryText.get(R.string.repo_auth_account_deleted))
            } else {
                Result.failure(Exception(ApiErrorParser.fromResponse(response, R.string.repo_auth_delete_account_failed)))
            }
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, R.string.repo_error_connection_server))
        }
    }

    suspend fun deleteAccountGoogle(idToken: String): Result<String> {
        return try {
            val response = api.deleteAccountGoogle(GoogleDeleteAccountRequest(idToken))
            if (response.isSuccessful) {
                tokenManager.clear()
                Result.success(RepositoryText.get(R.string.repo_auth_account_deleted))
            } else {
                Result.failure(Exception(ApiErrorParser.fromResponse(response, R.string.repo_auth_delete_account_failed)))
            }
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, R.string.repo_error_connection_server))
        }
    }

    fun logout() {
        tokenManager.clear()
    }
}
