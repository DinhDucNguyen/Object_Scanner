package com.duc.objectlanguage.data.repository

import com.duc.objectlanguage.data.api.RetrofitClient
import com.duc.objectlanguage.data.local.TokenManager
import com.duc.objectlanguage.data.model.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class AppRepository(private val tokenManager: TokenManager) {

    private val api get() = RetrofitClient.api

    // ====== AUTH ======

    suspend fun login(username: String, password: String): Result<TokenResponse> {
        return try {
            val response = api.login(LoginRequest(username, password))
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                tokenManager.accessToken = body.accessToken
                tokenManager.refreshToken = body.refreshToken
                tokenManager.username = body.user.username
                tokenManager.userId = body.user.id
                Result.success(body)
            } else {
                Result.failure(Exception("Sai tên đăng nhập hoặc mật khẩu"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Không thể kết nối server: ${e.message}"))
        }
    }

    suspend fun register(username: String, email: String, password: String): Result<TokenResponse> {
        return try {
            val response = api.register(RegisterRequest(username, email, password))
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                tokenManager.accessToken = body.accessToken
                tokenManager.refreshToken = body.refreshToken
                tokenManager.username = body.user.username
                tokenManager.userId = body.user.id
                Result.success(body)
            } else {
                Result.failure(Exception("Đăng ký thất bại. Tài khoản có thể đã tồn tại."))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Không thể kết nối server: ${e.message}"))
        }
    }

    fun logout() {
        tokenManager.clear()
    }

    // ====== SCAN ======

    suspend fun scanByCode(objectCode: String, confidence: Float): Result<ScanResponse> {
        return try {
            val request = ScanRequest(objectCode, confidence)
            val response = api.scanByCode(request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorStr = response.errorBody()?.string() ?: "Unknown API error"
                Result.failure(Exception("Không tìm thấy vật thể: $errorStr (Code: ${response.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi scan: ${e.message}"))
        }
    }

    suspend fun scanByImage(imageBytes: ByteArray): Result<ScanResponse> {
        return try {
            val body = imageBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", "capture.jpg", body)
            val response = api.scanByImage(part)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorStr = response.errorBody()?.string() ?: "Unknown API error"
                Result.failure(Exception("Không nhận diện được vật thể: $errorStr (Code: ${response.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi scan: ${e.message}"))
        }
    }

    suspend fun getTtsAudio(word: String, lang: String = "en"): ByteArray? {
        return try {
            val response = api.getTts(word, lang)
            if (response.isSuccessful) response.body()?.bytes() else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getExamples(word: String, lang: String = "en"): List<String> {
        return try {
            val response = api.getExamples(word, lang)
            if (response.isSuccessful) response.body()?.sentences ?: emptyList()
            else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ====== LEARNING ======

    suspend fun addToLearning(translationId: Int): Result<String> {
        return try {
            val response = api.addToLearning(translationId)
            if (response.isSuccessful) Result.success("Đã thêm vào danh sách học!")
            else Result.failure(Exception("Thêm thất bại"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ====== REVIEW ======

    suspend fun getDueReviews(): Result<List<ReviewCardResponse>> {
        return try {
            val response = api.getDueReviews()
            if (response.isSuccessful) Result.success(response.body() ?: emptyList())
            else Result.failure(Exception("Lỗi tải review"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitReview(progressId: Int, quality: Int): Result<ReviewResult> {
        return try {
            val response = api.submitReview(progressId, ReviewRequest(quality))
            if (response.isSuccessful && response.body() != null) Result.success(response.body()!!)
            else Result.failure(Exception("Lỗi submit"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ====== STATS ======

    suspend fun getStats(): Result<StatsResponse> {
        return try {
            val response = api.getStats()
            if (response.isSuccessful && response.body() != null) Result.success(response.body()!!)
            else Result.failure(Exception("Lỗi tải stats"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ====== HISTORY ======

    suspend fun getHistory(limit: Int = 50): Result<List<HistoryItem>> {
        return try {
            val response = api.getHistory(limit)
            if (response.isSuccessful) Result.success(response.body() ?: emptyList())
            else Result.failure(Exception("Lỗi tải lịch sử"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ====== ANALYTICS ======
    suspend fun getProgressHistory(): Result<List<ProgressHistoryItem>> {
        return try {
            val response = api.getProgressHistory()
            if (response.isSuccessful) Result.success(response.body() ?: emptyList())
            else Result.failure(Exception("Lỗi tải tiến độ"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getReviewHistory(): Result<List<ReviewHistoryItem>> {
        return try {
            val response = api.getReviewHistory()
            if (response.isSuccessful) Result.success(response.body() ?: emptyList())
            else Result.failure(Exception("Lỗi tải lịch sử ôn tập"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
