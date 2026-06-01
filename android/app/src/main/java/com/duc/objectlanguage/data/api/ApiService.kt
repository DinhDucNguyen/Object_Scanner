package com.duc.objectlanguage.data.api

import com.duc.objectlanguage.data.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ====== AUTH ======
    @POST("api/auth/login")
    suspend fun login(@Body req: LoginRequest): Response<TokenResponse>

    @POST("api/auth/register")
    suspend fun register(@Body req: RegisterRequest): Response<MessageResponse>

    @POST("api/auth/register/resend-otp")
    suspend fun resendRegistrationOtp(@Body req: ForgotPasswordRequest): Response<MessageResponse>

    @POST("api/auth/register/verify-otp")
    suspend fun verifyRegistrationOtp(@Body req: VerifyOtpRequest): Response<MessageResponse>

    @POST("api/auth/refresh")
    suspend fun refreshToken(@Body req: RefreshRequest): Response<TokenResponse>

    @POST("api/auth/refresh")
    fun refreshTokenSync(@Body req: RefreshRequest): Call<TokenResponse>

    @POST("api/auth/google")
    suspend fun googleLogin(@Body req: GoogleLoginRequest): Response<TokenResponse>

    @POST("api/auth/forgot-password")
    suspend fun forgotPassword(@Body req: ForgotPasswordRequest): Response<ForgotPasswordResponse>

    @POST("api/auth/verify-otp")
    suspend fun verifyOtp(@Body req: VerifyOtpRequest): Response<MessageResponse>

    @POST("api/auth/reset-password")
    suspend fun resetPassword(@Body req: ResetPasswordRequest): Response<MessageResponse>

    @PUT("api/auth/change-password")
    suspend fun changePassword(@Body req: ChangePasswordRequest): Response<MessageResponse>

    @DELETE("api/auth/account")
    suspend fun deleteAccount(@Body req: DeleteAccountRequest): Response<MessageResponse>

    // ====== SCAN ======
    @POST("api/scan")
    suspend fun scanByCode(@Body req: ScanRequest): Response<ScanResponse>

    @Multipart
    @POST("api/scan/image")
    suspend fun scanByImage(@Part file: MultipartBody.Part): Response<ScanResponse>

    // ====== TTS ======
    @GET("api/tts/{word}")
    @Streaming
    suspend fun getTts(
        @Path("word") word: String,
        @Query("lang") lang: String = "en"
    ): Response<ResponseBody>

    @GET
    @Streaming
    suspend fun getAudioByUrl(@Url url: String): Response<ResponseBody>

    // ====== EXAMPLES ======
    @GET("api/objects/{code}/examples")
    suspend fun getExamples(
        @Path("code") code: String,
        @Query("lang") lang: String = "en",
        @Query("count") count: Int = 3
    ): Response<ExamplesResponse>

    // ====== LEARNING ======
    @POST("api/learning/add")
    suspend fun addToLearning(@Query("translation_id") id: Int): Response<ResponseBody>

    // ====== REVIEW ======
    @GET("api/review")
    suspend fun getDueReviews(): Response<List<ReviewCardResponse>>

    @POST("api/review/{progressId}")
    suspend fun submitReview(
        @Path("progressId") id: Int,
        @Body req: ReviewRequest
    ): Response<ReviewResult>

    // ====== STATS ======
    @GET("api/stats")
    suspend fun getStats(): Response<StatsResponse>

    // ====== EXPLORE ======
    @GET("api/categories")
    suspend fun getCategories(): Response<List<CategoryData>>

    @GET("api/objects")
    suspend fun getObjectsByCategory(@Query("category_id") categoryId: Int? = null): Response<List<ObjectData>>

    // ====== HISTORY ======
    @GET("api/history")
    suspend fun getHistory(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
        @Query("keyword") keyword: String? = null,
        @Query("period") period: String? = "all",
        @Query("from_date") fromDate: String? = null,
        @Query("to_date") toDate: String? = null
    ): Response<List<HistoryItem>>

    @GET("api/history/{scanId}")
    suspend fun getHistoryDetail(@Path("scanId") scanId: Int): Response<HistoryDetail>

    @DELETE("api/history/{scanId}")
    suspend fun deleteHistory(@Path("scanId") scanId: Int): Response<ResponseBody>

    @GET("api/objects/{code}/translations")
    suspend fun getTranslations(@Path("code") code: String): Response<List<TranslationResponse>>

    // ====== DICTIONARY / TRANSLATE ======
    @GET("api/dictionary/lookup")
    suspend fun lookupWord(
        @Query("word") word: String,
        @Query("from_lang") fromLang: String = "en",
        @Query("to_lang") toLang: String = "en"
    ): Response<DictionaryResponse>

    @POST("api/dictionary/translate")
    suspend fun translate(@Body req: TranslateRequest): Response<TranslateResponse>

    @GET("api/dictionary/history")
    suspend fun getDictionaryHistory(
        @Query("limit") limit: Int = 30
    ): Response<List<DictionaryHistoryItem>>

    @DELETE("api/dictionary/history/{id}")
    suspend fun deleteHistoryItem(@Path("id") id: Int): Response<Void>

    // ====== USER PROFILE / SETTINGS ======
    @GET("api/auth/profile")
    suspend fun getProfile(): Response<ProfileData>

    @PUT("api/auth/profile")
    suspend fun updateProfile(@Body req: ProfileUpdateRequest): Response<ProfileData>

    @Multipart
    @POST("api/auth/profile/avatar")
    suspend fun uploadAvatar(@Part image: MultipartBody.Part): Response<AvatarUploadResponse>

    @GET("api/auth/settings")
    suspend fun getSettings(): Response<UserSettingsResponse>

    @PUT("api/auth/settings")
    suspend fun updateSettings(@Body req: UserSettingsUpdate): Response<UserSettingsResponse>

    // ====== ANALYTICS ======
    @GET("api/analytics")
    suspend fun getAnalytics(): Response<AnalyticsResponse>

    // ====== LICH SU QUET ======

    @Multipart
    @POST("api/lich-su-quet")
    suspend fun saveLichSuQue(
        @Part("object_code") objectCode: RequestBody,
        @Part("confidence") confidence: RequestBody,
        @Part("training_source") trainingSource: RequestBody,
        @Part image: MultipartBody.Part?,
    ): Response<LichSuQuetResponse>

    // ====== STREAK ======
    @GET("api/streak")
    suspend fun getStreak(): Response<StreakResponse>

    @GET("api/streak/calendar")
    suspend fun getStreakCalendar(@Query("days") days: Int = 30): Response<StreakCalendarResponse>

    @POST("api/streak/record")
    suspend fun recordStreak(): Response<StreakResponse>

    @POST("api/streak/sync")
    suspend fun syncStreak(@Body req: StreakSyncRequest): Response<StreakResponse>
}
