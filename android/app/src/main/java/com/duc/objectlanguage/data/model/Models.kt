package com.duc.objectlanguage.data.model

import com.google.gson.annotations.SerializedName

// ====== AUTH ======

data class LoginRequest(val username: String, val password: String)
data class RegisterRequest(val username: String, val email: String, val password: String)
data class RefreshRequest(@SerializedName("refresh_token") val refreshToken: String)

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("token_type") val tokenType: String,
    val user: UserResponse
)

data class UserResponse(
    val id: Int,
    val username: String,
    val email: String,
    val status: String,
    val role: String
)

// ====== SCAN ======

data class ScanRequest(
    @SerializedName("object_code") val objectCode: String,
    val confidence: Float = 0f,
    @SerializedName("device_model") val deviceModel: String = android.os.Build.MODEL
)

data class ScanResponse(
    val source: String,
    @SerializedName("object_id") val objectId: Int,
    @SerializedName("object_code") val objectCode: String,
    @SerializedName("category_name") val categoryName: String?,
    @SerializedName("difficulty_level") val difficultyLevel: Int,
    val translations: List<TranslationResponse>
)

data class TranslationResponse(
    val id: Int,
    @SerializedName("object_id") val objectId: Int,
    @SerializedName("language_id") val languageId: Int,
    @SerializedName("language_code") val languageCode: String?,
    @SerializedName("language_name") val languageName: String?,
    @SerializedName("word_name") val wordName: String,
    val phonetic: String?,
    val definition: String?,
    @SerializedName("example_sentence") val exampleSentence: String?
)

data class ExamplesResponse(
    val word: String,
    val lang: String,
    val sentences: List<String>
)

// ====== REVIEW ======

data class ReviewRequest(val quality: Int)

data class ReviewCardResponse(
    @SerializedName("progress_id") val progressId: Int,
    @SerializedName("translation_id") val translationId: Int,
    @SerializedName("object_code") val objectCode: String,
    @SerializedName("word_name") val wordName: String,
    val phonetic: String?,
    val definition: String?,
    @SerializedName("example_sentence") val exampleSentence: String?,
    @SerializedName("language_code") val languageCode: String,
    @SerializedName("language_name") val languageName: String,
    @SerializedName("easiness_factor") val easinessFactor: Float,
    val interval: Int,
    val repetitions: Int,
    @SerializedName("image_url") val imageUrl: String? = null
)

data class ReviewResult(
    val success: Boolean,
    @SerializedName("new_interval") val newInterval: Int,
    @SerializedName("new_ef") val newEf: Float,
    @SerializedName("next_review_date") val nextReviewDate: String
)

// ====== STATS ======

data class StatsResponse(
    @SerializedName("total_objects") val totalObjects: Int,
    @SerializedName("total_translations") val totalTranslations: Int,
    @SerializedName("total_languages") val totalLanguages: Int,
    @SerializedName("total_learned") val totalLearned: Int,
    @SerializedName("due_today") val dueToday: Int,
    val mastered: Int,
    @SerializedName("total_scans") val totalScans: Int
)

// ====== HISTORY ======

data class HistoryItem(
    val id: Int,
    @SerializedName("object_code") val objectCode: String?,
    @SerializedName("confidence_score") val confidenceScore: Float?,
    @SerializedName("scan_date") val scanDate: String?,
    @SerializedName("device_model") val deviceModel: String?
)

data class ProgressHistoryItem(
    val id: Int,
    @SerializedName("created_at") val createdAt: Long,
    val repetitions: Int,
    @SerializedName("easiness_factor") val easinessFactor: Float,
    val interval: Int
)

data class ReviewHistoryItem(
    val id: Int,
    @SerializedName("reviewed_at") val reviewedAt: Long
)
