package com.duc.objectlanguage.data.repository

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
                Result.failure(Exception(ApiErrorParser.fromResponse(response, "Sai tên đăng nhập hoặc mật khẩu")))
            }
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, "Không thể kết nối máy chủ"))
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
                Result.failure(Exception(ApiErrorParser.fromResponse(response, "Đăng nhập Google thất bại")))
            }
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, "Không thể kết nối máy chủ"))
        }
    }

    suspend fun register(username: String, email: String, password: String, fullName: String? = null): Result<String> {
        return try {
            val response = api.register(RegisterRequest(username, email, password, fullName))
            if (response.isSuccessful) Result.success(response.body()?.message ?: "Đăng ký thành công")
            else Result.failure(Exception(ApiErrorParser.fromResponse(response, "Đăng ký thất bại")))
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, "Không thể kết nối máy chủ"))
        }
    }

    suspend fun resendRegistrationOtp(email: String): Result<String> {
        return try {
            val response = api.resendRegistrationOtp(ForgotPasswordRequest(email))
            if (response.isSuccessful) Result.success(response.body()?.message ?: "Đã gửi lại mã OTP")
            else Result.failure(Exception(ApiErrorParser.fromResponse(response, "Gửi lại OTP thất bại")))
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, "Không thể kết nối máy chủ"))
        }
    }

    suspend fun verifyRegistrationOtp(email: String, otpCode: String): Result<String> {
        return try {
            val response = api.verifyRegistrationOtp(VerifyOtpRequest(email, otpCode))
            if (response.isSuccessful) Result.success(response.body()?.message ?: "Xác thực email thành công")
            else Result.failure(Exception(ApiErrorParser.fromResponse(response, "OTP không hợp lệ hoặc đã hết hạn")))
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, "Không thể kết nối máy chủ"))
        }
    }

    suspend fun forgotPassword(email: String): Result<ForgotPasswordResponse> {
        return try {
            val response = api.forgotPassword(ForgotPasswordRequest(email))
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) Result.success(body)
                else Result.failure(Exception("Phản hồi không hợp lệ"))
            } else {
                val msg = response.errorBody()?.string()?.let {
                    try { org.json.JSONObject(it).optString("detail", "Gửi OTP thất bại") } catch (_: Exception) { "Gửi OTP thất bại" }
                } ?: "Gửi OTP thất bại"
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, "Không thể kết nối máy chủ"))
        }
    }

    suspend fun verifyOtp(email: String, otpCode: String): Result<String> {
        return try {
            val response = api.verifyOtp(VerifyOtpRequest(email, otpCode))
            if (response.isSuccessful) Result.success(response.body()?.message ?: "OTP hợp lệ")
            else Result.failure(Exception("OTP không hợp lệ hoặc đã hết hạn"))
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, "Không thể kết nối máy chủ"))
        }
    }

    suspend fun resetPassword(email: String, otpCode: String, newPassword: String): Result<String> {
        return try {
            val response = api.resetPassword(ResetPasswordRequest(email, otpCode, newPassword))
            if (response.isSuccessful) Result.success(response.body()?.message ?: "Đặt lại mật khẩu thành công")
            else Result.failure(Exception("Đặt lại mật khẩu thất bại"))
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, "Không thể kết nối máy chủ"))
        }
    }

    suspend fun changePassword(currentPassword: String, newPassword: String): Result<String> {
        return try {
            val response = api.changePassword(ChangePasswordRequest(currentPassword, newPassword))
            if (response.isSuccessful) Result.success(response.body()?.message ?: "Đổi mật khẩu thành công")
            else Result.failure(Exception(ApiErrorParser.fromResponse(response, "Đổi mật khẩu thất bại")))
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, "Không thể kết nối máy chủ"))
        }
    }

    suspend fun deleteAccount(password: String): Result<String> {
        return try {
            val response = api.deleteAccount(DeleteAccountRequest(password))
            if (response.isSuccessful) {
                tokenManager.clear()
                Result.success(response.body()?.message ?: "Tài khoản đã được xóa")
            } else {
                Result.failure(Exception(ApiErrorParser.fromResponse(response, "Xóa tài khoản thất bại")))
            }
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, "Không thể kết nối máy chủ"))
        }
    }

    suspend fun deleteAccountGoogle(idToken: String): Result<String> {
        return try {
            val response = api.deleteAccountGoogle(GoogleDeleteAccountRequest(idToken))
            if (response.isSuccessful) {
                tokenManager.clear()
                Result.success(response.body()?.message ?: "Tài khoản đã được xóa")
            } else {
                Result.failure(Exception(ApiErrorParser.fromResponse(response, "Xóa tài khoản thất bại")))
            }
        } catch (e: Exception) {
            Result.failure(ApiErrorParser.userFacing(e, "Không thể kết nối máy chủ"))
        }
    }

    fun logout() {
        tokenManager.clear()
    }
}
