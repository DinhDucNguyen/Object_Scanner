package com.duc.objectlanguage.data.api

import com.duc.objectlanguage.data.model.*
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ====== AUTH ======
    @POST("api/auth/login")
    suspend fun login(@Body req: LoginRequest): Response<TokenResponse>

    @POST("api/auth/register")
    suspend fun register(@Body req: RegisterRequest): Response<TokenResponse>

    @POST("api/auth/refresh")
    suspend fun refreshToken(@Body req: RefreshRequest): Response<TokenResponse>

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

    // ====== EXAMPLES ======
    @GET("api/objects/{code}/examples")
    suspend fun getExamples(
        @Path("code") code: String,
        @Query("lang") lang: String = "en",
        @Query("count") count: Int = 3
    ): Response<ExamplesResponse>

    // ====== LEARNING ======
    @POST("api/learning/add")
    suspend fun addToLearning(@Query("translation_id") id: Int): Response<Map<String, Any>>

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

    // ====== HISTORY ======
    @GET("api/history")
    suspend fun getHistory(@Query("limit") limit: Int = 50): Response<List<HistoryItem>>

    // ====== ANALYTICS ======
    @GET("api/analytics/progress")
    suspend fun getProgressHistory(): Response<List<ProgressHistoryItem>>

    @GET("api/analytics/reviews")
    suspend fun getReviewHistory(): Response<List<ReviewHistoryItem>>
}
