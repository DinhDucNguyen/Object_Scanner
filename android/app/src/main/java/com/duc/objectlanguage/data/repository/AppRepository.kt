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

            val body = response.body()

            if (response.isSuccessful && body != null) {

                tokenManager.accessToken = body.accessToken

                tokenManager.refreshToken = body.refreshToken

                tokenManager.username = body.user.username

                tokenManager.userId = body.user.id

                Result.success(body)

            } else {

                Result.failure(Exception(apiError(response, "Sai tên đăng nhập hoặc mật khẩu")))

            }

        } catch (e: Exception) {

            Result.failure(userFacingException(e, "Không thể kết nối máy chủ"))

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

                Result.success(body)

            } else {

                Result.failure(Exception(apiError(response, "Đăng nhập Google thất bại")))

            }

        } catch (e: Exception) {

            Result.failure(userFacingException(e, "Không thể kết nối máy chủ"))

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

            Result.failure(userFacingException(e, "Không thể kết nối máy chủ"))

        }

    }



    suspend fun resendRegistrationOtp(email: String): Result<String> {
        return try {
            val response = api.resendRegistrationOtp(ForgotPasswordRequest(email))
            if (response.isSuccessful) Result.success(response.body()?.message ?: "Đã gửi lại mã OTP")
            else Result.failure(Exception(apiError(response, "Gửi lại OTP thất bại")))
        } catch (e: Exception) {
            Result.failure(userFacingException(e, "Không thể kết nối máy chủ"))
        }
    }

    suspend fun verifyRegistrationOtp(email: String, otpCode: String): Result<String> {
        return try {
            val response = api.verifyRegistrationOtp(VerifyOtpRequest(email, otpCode))
            if (response.isSuccessful) Result.success(response.body()?.message ?: "Xác thực email thành công")
            else Result.failure(Exception(apiError(response, "OTP không hợp lệ hoặc đã hết hạn")))
        } catch (e: Exception) {
            Result.failure(userFacingException(e, "Không thể kết nối máy chủ"))
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

            Result.failure(userFacingException(e, "Không thể kết nối máy chủ"))

        }

    }



    suspend fun verifyOtp(email: String, otpCode: String): Result<String> {

        return try {

            val response = api.verifyOtp(VerifyOtpRequest(email, otpCode))

            if (response.isSuccessful) Result.success(response.body()?.message ?: "OTP hợp lệ")

            else Result.failure(Exception("OTP không hợp lệ hoặc đã hết hạn"))

        } catch (e: Exception) {

            Result.failure(userFacingException(e, "Không thể kết nối máy chủ"))

        }

    }



    suspend fun resetPassword(email: String, otpCode: String, newPassword: String): Result<String> {

        return try {

            val response = api.resetPassword(ResetPasswordRequest(email, otpCode, newPassword))

            if (response.isSuccessful) Result.success(response.body()?.message ?: "Đặt lại mật khẩu thành công")

            else Result.failure(Exception("Đặt lại mật khẩu thất bại"))

        } catch (e: Exception) {

            Result.failure(userFacingException(e, "Không thể kết nối máy chủ"))

        }

    }



    suspend fun changePassword(currentPassword: String, newPassword: String): Result<String> {

        return try {

            val response = api.changePassword(ChangePasswordRequest(currentPassword, newPassword))

            if (response.isSuccessful) Result.success(response.body()?.message ?: "Đổi mật khẩu thành công")

            else Result.failure(Exception(apiError(response, "Đổi mật khẩu thất bại")))

        } catch (e: Exception) {

            Result.failure(userFacingException(e, "Không thể kết nối máy chủ"))

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
            Result.failure(userFacingException(e, "Không thể kết nối máy chủ"))
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

            val body = response.body()

            if (response.isSuccessful && body != null) {

                Result.success(body)

            } else {

                val errorStr = parseApiError(response.errorBody()?.string()) ?: "Máy chủ chưa trả thông tin lỗi"

                Result.failure(Exception("Không tìm thấy vật thể: $errorStr (Code: ${response.code()})"))

            }

        } catch (e: Exception) {

            Result.failure(userFacingException(e, "Không thể quét lúc này"))

        }

    }



    suspend fun scanByImage(imageBytes: ByteArray): Result<ScanResponse> {

        return try {

            val body = imageBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())

            val part = MultipartBody.Part.createFormData("file", "capture.jpg", body)

            val response = api.scanByImage(part)

            val responseBody = response.body()

            if (response.isSuccessful && responseBody != null) {

                Result.success(responseBody)

            } else {

                val errorStr = parseApiError(response.errorBody()?.string()) ?: "Máy chủ chưa trả thông tin lỗi"

                Result.failure(Exception("Không nhận diện được vật thể: $errorStr (Code: ${response.code()})"))

            }

        } catch (e: Exception) {

            Result.failure(userFacingException(e, "Không thể quét lúc này"))

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

            Result.failure(userFacingException(e, "Không thể kết nối máy chủ"))

        }

    }



    // ====== REVIEW ======



    suspend fun getDueCount(): Result<Int> {
        return try {
            val response = api.getDueCount()
            if (response.isSuccessful) Result.success(response.body()?.count ?: 0)
            else Result.success(0)
        } catch (e: Exception) {
            Result.success(0)
        }
    }

    suspend fun getDueReviews(): Result<List<ReviewCardResponse>> {

        return try {

            val response = api.getDueReviews()

            if (response.isSuccessful) Result.success(response.body() ?: emptyList())

            else Result.failure(Exception("Không tải được dữ liệu ôn tập"))

        } catch (e: Exception) {

            Result.failure(userFacingException(e, "Không tải được dữ liệu ôn tập"))

        }

    }



    suspend fun submitReview(progressId: Int, quality: Int): Result<ReviewResult> {

        return try {

            val response = api.submitReview(progressId, ReviewRequest(quality))

            bodyResult(response, "Không gửi được kết quả ôn tập")

        } catch (e: Exception) {

            Result.failure(userFacingException(e, "Không gửi được kết quả ôn tập"))

        }

    }



    // ====== STATS ======



    suspend fun getStats(): Result<StatsResponse> {

        return try {

            val response = api.getStats()

            bodyResult(response, "Không tải được thống kê")

        } catch (e: Exception) {

            Result.failure(userFacingException(e, "Không tải được thống kê"))

        }

    }



    // ====== EXPLORE ======



    suspend fun getCategories(): Result<List<CategoryData>> {

        return try {

            val response = api.getCategories()

            if (response.isSuccessful) Result.success(response.body() ?: emptyList())

            else Result.failure(Exception("Không tải được chủ đề (${response.code()})"))

        } catch (e: Exception) {

            Result.failure(userFacingException(e, "Không tải được chủ đề"))

        }

    }



    suspend fun getObjectsByCategory(categoryId: Int): Result<List<ObjectData>> {

        return try {

            val response = api.getObjectsByCategory(categoryId)

            if (response.isSuccessful) Result.success(response.body() ?: emptyList())

            else Result.failure(Exception("Không tải được từ vựng (${response.code()})"))

        } catch (e: Exception) {

            Result.failure(userFacingException(e, "Không tải được từ vựng"))

        }

    }



    suspend fun getAllObjects(): Result<List<ObjectData>> {

        return try {

            val response = api.getObjectsByCategory()

            if (response.isSuccessful) Result.success(response.body() ?: emptyList())

            else Result.failure(Exception("Không tải được danh sách vật thể (${response.code()})"))

        } catch (e: Exception) {

            Result.failure(userFacingException(e, "Không tải được danh sách vật thể"))

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

            Result.failure(userFacingException(e, "Không tải được lịch sử"))

        }

    }



    suspend fun getTranslationsByCode(objectCode: String): Result<List<TranslationResponse>> {

        return try {

            val response = api.getTranslations(objectCode)

            if (response.isSuccessful) Result.success(response.body() ?: emptyList())

            else Result.failure(Exception("Không tìm thấy từ vựng"))

        } catch (e: Exception) {

            Result.failure(userFacingException(e, "Không tải được từ vựng"))

        }

    }



    suspend fun getHistoryDetail(scanId: Int): Result<HistoryDetail> {

        return try {

            val response = api.getHistoryDetail(scanId)

            bodyResult(response, "Không tìm thấy lịch sử")

        } catch (e: Exception) {

            Result.failure(userFacingException(e, "Không tải được chi tiết lịch sử"))

        }

    }



    suspend fun deleteHistory(scanId: Int): Result<String> {

        return try {

            val response = api.deleteHistory(scanId)

            response.body()?.close()

            if (response.isSuccessful) Result.success("Đã xoá lịch sử quét")

            else Result.failure(Exception("Xoá lịch sử thất bại"))

        } catch (e: Exception) {

            Result.failure(userFacingException(e, "Không xoá được lịch sử"))

        }

    }



    // ====== DICTIONARY / TRANSLATE ======



    suspend fun lookupWord(word: String, fromLang: String = "en", toLang: String = "en"): Result<DictionaryResponse> {

        return try {

            val response = api.lookupWord(word, fromLang, toLang)

            bodyResult(response, "Không tìm thấy từ")

        } catch (e: Exception) {

            Result.failure(userFacingException(e, "Không tra được từ lúc này"))

        }

    }



    suspend fun translate(text: String, fromLang: String, toLang: String): Result<TranslateResponse> {

        return try {

            val response = api.translate(TranslateRequest(text, fromLang, toLang))

            val body = response.body()

            if (response.isSuccessful && body != null) {

                Result.success(body)

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

            bodyResult(response, "Không tải được lịch sử")

        } catch (e: Exception) {

            Result.failure(userFacingException(e, "Không tải được lịch sử"))

        }

    }



    suspend fun deleteHistoryItem(id: Int): Result<Unit> {

        return try {

            val response = api.deleteHistoryItem(id)

            if (response.isSuccessful) Result.success(Unit)

            else Result.failure(Exception("Lỗi xóa lịch sử"))

        } catch (e: Exception) {

            Result.failure(userFacingException(e, "Không xoá được lịch sử"))

        }

    }



    // ====== USER PROFILE / SETTINGS ======



    suspend fun getProfile(): Result<ProfileData> {

        return try {

            val response = api.getProfile()

            bodyResult(response, "Không tải được hồ sơ")

        } catch (e: Exception) {

            Result.failure(userFacingException(e, "Không tải được hồ sơ"))

        }

    }



    suspend fun updateProfile(username: String? = null, fullName: String? = null, bio: String? = null): Result<ProfileData> {

        return try {

            val response = api.updateProfile(ProfileUpdateRequest(username, fullName, bio))

            val profile = response.body()

            if (response.isSuccessful && profile != null) {

                profile.username?.takeIf { it.isNotBlank() }?.let { tokenManager.username = it }

                Result.success(profile)

            }

            else Result.failure(Exception(apiError(response, "Cập nhật profile thất bại")))

        } catch (e: Exception) {

            Result.failure(userFacingException(e, "Không cập nhật được hồ sơ"))

        }

    }



    suspend fun uploadAvatar(imageBytes: ByteArray, filename: String): Result<String> {

        return try {

            val body = imageBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())

            val part = okhttp3.MultipartBody.Part.createFormData("file", filename, body)

            val response = api.uploadAvatar(part)

            val avatar = response.body()?.avatarUrl

            if (response.isSuccessful && avatar != null) Result.success(avatar)

            else Result.failure(Exception(apiError(response, "Không tải được ảnh đại diện")))

        } catch (e: Exception) {

            Result.failure(userFacingException(e, "Không tải được ảnh đại diện"))

        }

    }



    suspend fun getUserSettings(): Result<UserSettingsResponse> {

        return try {

            val response = api.getSettings()

            bodyResult(response, "Không tải được cài đặt")

        } catch (e: Exception) {

            Result.failure(userFacingException(e, "Không tải được cài đặt"))

        }

    }



    suspend fun updateUserSettings(displayLanguage: String): Result<UserSettingsResponse> {

        return try {

            val response = api.updateSettings(UserSettingsUpdate(displayLanguage = displayLanguage))

            bodyResult(response, "Không cập nhật được cài đặt")

        } catch (e: Exception) {

            Result.failure(userFacingException(e, "Không cập nhật được cài đặt"))

        }

    }



    suspend fun updateDarkMode(darkMode: Boolean): Result<UserSettingsResponse> {

        return try {

            val response = api.updateSettings(UserSettingsUpdate(darkMode = darkMode))

            bodyResult(response, "Không cập nhật được cài đặt")

        } catch (e: Exception) {

            Result.failure(userFacingException(e, "Không cập nhật được cài đặt"))

        }

    }



    // ====== ANALYTICS ======



    suspend fun getAnalytics(): Result<AnalyticsResponse> {

        return try {

            val response = api.getAnalytics()

            bodyResult(response, "Không tải được thống kê")

        } catch (e: Exception) {

            Result.failure(userFacingException(e, "Không tải được thống kê"))

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

            val body = response.body()

            if (response.isSuccessful && body != null) {

                Result.success(body)

            } else {

                Result.failure(Exception("Lưu lịch sử thất bại: ${response.code()}"))

            }

        } catch (e: Exception) {

            Result.failure(userFacingException(e, "Không lưu được lịch sử quét"))

        }

    }



    // ====== STREAK ======



    suspend fun getStreak(): Result<StreakResponse> {

        return try {

            val response = api.getStreak()

            bodyResult(response, "Không tải được chuỗi ngày học")

        } catch (e: Exception) {

            Result.failure(userFacingException(e, "Không tải được streak"))

        }

    }



    /** Gọi sau mỗi lần submit review — server tự tính streak theo ngày */

    suspend fun recordStreak(): Result<StreakResponse> {

        return try {

            val response = api.recordStreak()

            bodyResult(response, "Không ghi được chuỗi ngày học")

        } catch (e: Exception) {

            Result.failure(userFacingException(e, "Không ghi được streak"))

        }

    }



    suspend fun getStreakCalendar(days: Int = 30): Result<StreakCalendarResponse> {

        return try {

            val response = api.getStreakCalendar(days)

            bodyResult(response, "Không tải được lịch chuỗi ngày học")

        } catch (e: Exception) {

            Result.failure(userFacingException(e, "Không tải được lịch streak"))

        }

    }



    /** Đồng bộ streak local lên server khi có mạng */

    suspend fun syncStreak(req: StreakSyncRequest): Result<StreakResponse> {

        return try {

            val response = api.syncStreak(req)

            bodyResult(response, "Không đồng bộ được chuỗi ngày học")

        } catch (e: Exception) {

            Result.failure(userFacingException(e, "Không đồng bộ được streak"))

        }

    }



    private fun apiError(response: Response<*>, fallback: String): String {

        val raw = response.errorBody()?.string()

        val detail = parseApiError(raw)

        return if (detail.isNullOrBlank()) "$fallback (${response.code()})" else detail

    }



    private fun <T> bodyResult(response: Response<T>, fallback: String): Result<T> {

        val body = response.body()

        return if (response.isSuccessful && body != null) {

            Result.success(body)

        } else {

            Result.failure(Exception(apiError(response, fallback)))

        }

    }



    private fun parseApiError(raw: String?): String? {

        if (raw.isNullOrBlank()) return null

        return try {

            val json = JsonParser.parseString(raw)

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
