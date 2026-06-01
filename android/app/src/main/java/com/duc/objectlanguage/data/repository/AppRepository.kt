package com.duc.objectlanguage.data.repository



import android.util.Log

import com.google.gson.JsonParser

import com.duc.objectlanguage.data.api.RetrofitClient

import com.duc.objectlanguage.data.local.TokenManager

import com.duc.objectlanguage.data.model.*

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.withContext

import okhttp3.MediaType.Companion.toMediaTypeOrNull

import okhttp3.MultipartBody

import okhttp3.RequestBody.Companion.toRequestBody

import retrofit2.Response



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

                Result.failure(Exception(apiError(response, "Sai tên đăng nhập hoặc mật khẩu")))

            }

        } catch (e: Exception) {

            Result.failure(Exception("Không thể kết nối server: ${e.message}"))

        }

    }



    suspend fun googleLogin(idToken: String): Result<TokenResponse> {

        return try {

            val response = api.googleLogin(GoogleLoginRequest(idToken))

            if (response.isSuccessful && response.body() != null) {

                val body = response.body()!!

                tokenManager.accessToken = body.accessToken

                tokenManager.refreshToken = body.refreshToken

                tokenManager.username = body.user.username

                tokenManager.userId = body.user.id

                Result.success(body)

            } else {

                Result.failure(Exception(apiError(response, "Đăng nhập Google thất bại")))

            }

        } catch (e: Exception) {

            Result.failure(Exception("Không thể kết nối server: ${e.message}"))

        }

    }



    suspend fun register(username: String, email: String, password: String, fullName: String? = null): Result<String> {

        return try {

            val response = api.register(RegisterRequest(username, email, password, fullName))

            if (response.isSuccessful) {

                Result.success(response.body()?.message ?: "Đăng ký thành công")

            } else {

                Result.failure(Exception(apiError(response, "Đăng ký thất bại")))

            }

        } catch (e: Exception) {

            Result.failure(Exception("Không thể kết nối server: ${e.message}"))

        }

    }



    suspend fun resendRegistrationOtp(email: String): Result<String> {
        return try {
            val response = api.resendRegistrationOtp(ForgotPasswordRequest(email))
            if (response.isSuccessful) Result.success(response.body()?.message ?: "Đã gửi lại mã OTP")
            else Result.failure(Exception(apiError(response, "Gửi lại OTP thất bại")))
        } catch (e: Exception) {
            Result.failure(Exception("KhÃ´ng thá»ƒ káº¿t ná»‘i server: ${e.message}"))
        }
    }

    suspend fun verifyRegistrationOtp(email: String, otpCode: String): Result<String> {
        return try {
            val response = api.verifyRegistrationOtp(VerifyOtpRequest(email, otpCode))
            if (response.isSuccessful) Result.success(response.body()?.message ?: "Xác thực email thành công")
            else Result.failure(Exception(apiError(response, "OTP khÃ´ng há»£p lá»‡ hoáº·c Ä‘Ã£ háº¿t háº¡n")))
        } catch (e: Exception) {
            Result.failure(Exception("KhÃ´ng thá»ƒ káº¿t ná»‘i server: ${e.message}"))
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

            Result.failure(Exception("Không thể kết nối server: ${e.message}"))

        }

    }



    suspend fun verifyOtp(email: String, otpCode: String): Result<String> {

        return try {

            val response = api.verifyOtp(VerifyOtpRequest(email, otpCode))

            if (response.isSuccessful) Result.success(response.body()?.message ?: "OTP hợp lệ")

            else Result.failure(Exception("OTP không hợp lệ hoặc đã hết hạn"))

        } catch (e: Exception) {

            Result.failure(Exception("Không thể kết nối server: ${e.message}"))

        }

    }



    suspend fun resetPassword(email: String, otpCode: String, newPassword: String): Result<String> {

        return try {

            val response = api.resetPassword(ResetPasswordRequest(email, otpCode, newPassword))

            if (response.isSuccessful) Result.success(response.body()?.message ?: "Đặt lại mật khẩu thành công")

            else Result.failure(Exception("Đặt lại mật khẩu thất bại"))

        } catch (e: Exception) {

            Result.failure(Exception("Không thể kết nối server: ${e.message}"))

        }

    }



    suspend fun changePassword(currentPassword: String, newPassword: String): Result<String> {

        return try {

            val response = api.changePassword(ChangePasswordRequest(currentPassword, newPassword))

            if (response.isSuccessful) Result.success(response.body()?.message ?: "Đổi mật khẩu thành công")

            else Result.failure(Exception(apiError(response, "Đổi mật khẩu thất bại")))

        } catch (e: Exception) {

            Result.failure(Exception("Không thể kết nối server: ${e.message}"))

        }

    }



    suspend fun deleteAccount(password: String): Result<String> {
        return try {
            val response = api.deleteAccount(DeleteAccountRequest(password))
            if (response.isSuccessful) {
                tokenManager.clear()
                Result.success(response.body()?.message ?: "Tài khoản đã được xóa")
            } else {
                Result.failure(Exception(apiError(response, "Xóa tài khoản thất bại")))
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

            if (response.isSuccessful) withContext(Dispatchers.IO) { response.body()?.bytes() } else null

        } catch (e: Exception) {

            null

        }

    }



    suspend fun getAudioByUrl(audioUrl: String): ByteArray? {

        return try {

            val response = api.getAudioByUrl(normalizeAudioUrl(audioUrl))

            if (response.isSuccessful) withContext(Dispatchers.IO) { response.body()?.bytes() } else null

        } catch (e: Exception) {

            null

        }

    }



    suspend fun getExamples(word: String, lang: String = "en"): List<String> {

        return try {

            val response = api.getExamples(word, lang)

            if (response.isSuccessful) response.body()?.sentences?.take(3) ?: emptyList()

            else emptyList()

        } catch (e: Exception) {

            emptyList()

        }

    }



    // ====== LEARNING ======



    suspend fun addToLearning(translationId: Int): Result<String> {

        return try {

            val response = api.addToLearning(translationId)

            response.body()?.close()

            if (response.isSuccessful) Result.success("Đã thêm vào danh sách học!")

            else Result.failure(Exception("Lỗi ${response.code()}: ${response.message()}"))

        } catch (e: Exception) {

            Result.failure(Exception("Không kết nối được server: ${e.message}"))

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



    // ====== EXPLORE ======



    suspend fun getCategories(): Result<List<CategoryData>> {

        return try {

            val response = api.getCategories()

            if (response.isSuccessful) Result.success(response.body() ?: emptyList())

            else Result.failure(Exception("Loi tai chu de: ${response.code()}"))

        } catch (e: Exception) {

            Result.failure(Exception("Loi tai chu de: ${e.message}"))

        }

    }



    suspend fun getObjectsByCategory(categoryId: Int): Result<List<ObjectData>> {

        return try {

            val response = api.getObjectsByCategory(categoryId)

            if (response.isSuccessful) Result.success(response.body() ?: emptyList())

            else Result.failure(Exception("Loi tai tu vung: ${response.code()}"))

        } catch (e: Exception) {

            Result.failure(Exception("Loi tai tu vung: ${e.message}"))

        }

    }



    suspend fun getAllObjects(): Result<List<ObjectData>> {

        return try {

            val response = api.getObjectsByCategory()

            if (response.isSuccessful) Result.success(response.body() ?: emptyList())

            else Result.failure(Exception("Loi tai danh sach object: ${response.code()}"))

        } catch (e: Exception) {

            Result.failure(Exception("Loi tai danh sach object: ${e.message}"))

        }

    }



    // ====== HISTORY ======



    suspend fun getHistory(

        limit: Int = 50,

        offset: Int = 0,

        keyword: String? = null,

        period: String? = "all",

        fromDate: String? = null,

        toDate: String? = null

    ): Result<List<HistoryItem>> {

        return try {

            val response = api.getHistory(limit, offset, keyword, period, fromDate, toDate)

            if (response.isSuccessful) Result.success(response.body() ?: emptyList())

            else Result.failure(Exception("Lỗi tải lịch sử"))

        } catch (e: Exception) {

            Result.failure(e)

        }

    }



    suspend fun getTranslationsByCode(objectCode: String): Result<List<TranslationResponse>> {

        return try {

            val response = api.getTranslations(objectCode)

            if (response.isSuccessful) Result.success(response.body() ?: emptyList())

            else Result.failure(Exception("Không tìm thấy từ vựng"))

        } catch (e: Exception) {

            Result.failure(e)

        }

    }



    suspend fun getHistoryDetail(scanId: Int): Result<HistoryDetail> {

        return try {

            val response = api.getHistoryDetail(scanId)

            if (response.isSuccessful && response.body() != null) Result.success(response.body()!!)

            else Result.failure(Exception("Khong tim thay lich su"))

        } catch (e: Exception) {

            Result.failure(e)

        }

    }



    suspend fun deleteHistory(scanId: Int): Result<String> {

        return try {

            val response = api.deleteHistory(scanId)

            response.body()?.close()

            if (response.isSuccessful) Result.success("Da xoa lich su quet")

            else Result.failure(Exception("Xoa lich su that bai"))

        } catch (e: Exception) {

            Result.failure(e)

        }

    }



    // ====== DICTIONARY / TRANSLATE ======



    suspend fun lookupWord(word: String, fromLang: String = "en", toLang: String = "en"): Result<DictionaryResponse> {

        return try {

            val response = api.lookupWord(word, fromLang, toLang)

            if (response.isSuccessful && response.body() != null) Result.success(response.body()!!)

            else Result.failure(Exception(response.errorBody()?.string() ?: "Không tìm thấy từ"))

        } catch (e: Exception) {

            Result.failure(Exception("Lỗi tra từ: ${e.message}"))

        }

    }



    suspend fun translate(text: String, fromLang: String, toLang: String): Result<TranslateResponse> {

        return try {

            val response = api.translate(TranslateRequest(text, fromLang, toLang))

            if (response.isSuccessful && response.body() != null) {

                Result.success(response.body()!!)

            } else {

                val rawError = response.errorBody()?.string()

                val message = runCatching {

                    org.json.JSONObject(rawError ?: "").optString("detail")

                        .takeIf { it.isNotBlank() }

                }.getOrNull() ?: rawError ?: "Không thể dịch lúc này"

                Result.failure(Exception(message))

            }

        } catch (e: Exception) {

            Result.failure(Exception("Không thể kết nối tới server"))

        }

    }



    suspend fun getDictionaryHistory(limit: Int = 30): Result<List<DictionaryHistoryItem>> {

        return try {

            val response = api.getDictionaryHistory(limit)

            if (response.isSuccessful && response.body() != null) Result.success(response.body()!!)

            else Result.failure(Exception(response.errorBody()?.string() ?: "Lỗi tải lịch sử"))

        } catch (e: Exception) {

            Result.failure(Exception("Lỗi tải lịch sử: ${e.message}"))

        }

    }



    suspend fun deleteHistoryItem(id: Int): Result<Unit> {

        return try {

            val response = api.deleteHistoryItem(id)

            if (response.isSuccessful) Result.success(Unit)

            else Result.failure(Exception("Lỗi xóa lịch sử"))

        } catch (e: Exception) {

            Result.failure(Exception("Lỗi xóa: ${e.message}"))

        }

    }



    // ====== USER PROFILE / SETTINGS ======



    suspend fun getProfile(): Result<ProfileData> {

        return try {

            val response = api.getProfile()

            if (response.isSuccessful && response.body() != null) Result.success(response.body()!!)

            else Result.failure(Exception(apiError(response, "Lỗi tải profile")))

        } catch (e: Exception) {

            Result.failure(e)

        }

    }



    suspend fun updateProfile(username: String? = null, fullName: String? = null, bio: String? = null): Result<ProfileData> {

        return try {

            val response = api.updateProfile(ProfileUpdateRequest(username, fullName, bio))

            if (response.isSuccessful && response.body() != null) {

                val profile = response.body()!!

                profile.username?.takeIf { it.isNotBlank() }?.let { tokenManager.username = it }

                Result.success(profile)

            }

            else Result.failure(Exception(apiError(response, "Cập nhật profile thất bại")))

        } catch (e: Exception) {

            Result.failure(e)

        }

    }



    suspend fun uploadAvatar(imageBytes: ByteArray, filename: String): Result<String> {

        return try {

            val body = imageBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())

            val part = okhttp3.MultipartBody.Part.createFormData("file", filename, body)

            val response = api.uploadAvatar(part)

            if (response.isSuccessful && response.body() != null) Result.success(response.body()!!.avatarUrl)

            else Result.failure(Exception(apiError(response, "Upload avatar thất bại")))

        } catch (e: Exception) {

            Result.failure(e)

        }

    }



    suspend fun getUserSettings(): Result<UserSettingsResponse> {

        return try {

            val response = api.getSettings()

            if (response.isSuccessful && response.body() != null) Result.success(response.body()!!)

            else Result.failure(Exception("Lỗi tải cài đặt"))

        } catch (e: Exception) {

            Result.failure(e)

        }

    }



    suspend fun updateUserSettings(displayLanguage: String): Result<UserSettingsResponse> {

        return try {

            val response = api.updateSettings(UserSettingsUpdate(displayLanguage = displayLanguage))

            if (response.isSuccessful && response.body() != null) Result.success(response.body()!!)

            else Result.failure(Exception("Cập nhật thất bại"))

        } catch (e: Exception) {

            Result.failure(e)

        }

    }



    suspend fun updateDarkMode(darkMode: Boolean): Result<UserSettingsResponse> {

        return try {

            val response = api.updateSettings(UserSettingsUpdate(darkMode = darkMode))

            if (response.isSuccessful && response.body() != null) Result.success(response.body()!!)

            else Result.failure(Exception("Cập nhật thất bại"))

        } catch (e: Exception) {

            Result.failure(e)

        }

    }



    // ====== ANALYTICS ======



    suspend fun getAnalytics(): Result<AnalyticsResponse> {

        return try {

            val response = api.getAnalytics()

            if (response.isSuccessful && response.body() != null) Result.success(response.body()!!)

            else Result.failure(Exception("Lỗi tải analytics"))

        } catch (e: Exception) {

            Result.failure(e)

        }

    }



    // ====== LICH SU QUET ======



    suspend fun saveLichSuQue(

        objectCode: String,

        confidence: Float,

        imageBytes: ByteArray?,

        trainingSource: String = "yolo",

    ): Result<LichSuQuetResponse> {

        return try {

            val codePart = objectCode.toRequestBody("text/plain".toMediaTypeOrNull())

            val confidencePart = confidence.toString().toRequestBody("text/plain".toMediaTypeOrNull())

            val trainingSourcePart = trainingSource.toRequestBody("text/plain".toMediaTypeOrNull())

            val imagePart = imageBytes?.let {

                val body = it.toRequestBody("image/jpeg".toMediaTypeOrNull())

                MultipartBody.Part.createFormData("image", "scan.jpg", body)

            }

            val response = api.saveLichSuQue(codePart, confidencePart, trainingSourcePart, imagePart)

            if (response.isSuccessful && response.body() != null) {

                Result.success(response.body()!!)

            } else {

                Result.failure(Exception("Lưu lịch sử thất bại: ${response.code()}"))

            }

        } catch (e: Exception) {

            Result.failure(Exception("Lỗi lưu lịch sử: ${e.message}"))

        }

    }



    // ====== STREAK ======



    suspend fun getStreak(): Result<StreakResponse> {

        return try {

            val response = api.getStreak()

            if (response.isSuccessful && response.body() != null) Result.success(response.body()!!)

            else Result.failure(Exception("Lỗi tải streak"))

        } catch (e: Exception) {

            Result.failure(e)

        }

    }



    /** Gọi sau mỗi lần submit review — server tự tính streak theo ngày */

    suspend fun recordStreak(): Result<StreakResponse> {

        return try {

            val response = api.recordStreak()

            if (response.isSuccessful && response.body() != null) Result.success(response.body()!!)

            else Result.failure(Exception("Lỗi ghi streak"))

        } catch (e: Exception) {

            Result.failure(e)

        }

    }



    suspend fun getStreakCalendar(days: Int = 30): Result<StreakCalendarResponse> {

        return try {

            val response = api.getStreakCalendar(days)

            if (response.isSuccessful && response.body() != null) Result.success(response.body()!!)

            else Result.failure(Exception("Lỗi tải lịch streak"))

        } catch (e: Exception) {

            Result.failure(e)

        }

    }



    /** Đồng bộ streak local lên server khi có mạng */

    suspend fun syncStreak(req: StreakSyncRequest): Result<StreakResponse> {

        return try {

            val response = api.syncStreak(req)

            if (response.isSuccessful && response.body() != null) Result.success(response.body()!!)

            else Result.failure(Exception("Lỗi đồng bộ streak"))

        } catch (e: Exception) {

            Result.failure(e)

        }

    }



    private fun apiError(response: Response<*>, fallback: String): String {

        val raw = response.errorBody()?.string()

        val detail = parseApiError(raw)

        return if (detail.isNullOrBlank()) "$fallback (${response.code()})" else detail

    }



    private fun parseApiError(raw: String?): String? {

        if (raw.isNullOrBlank()) return null

        return try {

            val json = JsonParser().parse(raw)

            if (!json.isJsonObject) return raw

            val obj = json.asJsonObject

            val detail = obj.get("detail")

            when {

                detail == null -> obj.get("message")?.asString ?: raw

                detail.isJsonPrimitive -> detail.asString

                detail.isJsonArray && detail.asJsonArray.size() > 0 -> {

                    val first = detail.asJsonArray[0]

                    if (first.isJsonObject) first.asJsonObject.get("msg")?.asString ?: first.toString()

                    else first.toString()

                }

                else -> detail.toString()

            }

        } catch (_: Exception) {

            raw

        }

    }



    private fun normalizeAudioUrl(audioUrl: String): String {

        val trimmed = audioUrl.trim()

        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {

            trimmed

        } else {

            trimmed.trimStart('/')

        }

    }

}

