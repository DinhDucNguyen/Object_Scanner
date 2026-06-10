package com.duc.objectlanguage.data.model

import com.google.gson.annotations.SerializedName

// ====== AUTH ======

data class LoginRequest(val username: String, val password: String)
data class GoogleLoginRequest(@SerializedName("id_token") val idToken: String)
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
    @SerializedName("full_name") val fullName: String? = null
)
data class RefreshRequest(@SerializedName("refresh_token") val refreshToken: String)

data class ForgotPasswordRequest(val email: String)
data class ForgotPasswordResponse(
    val message: String,
    val email: String,
    @SerializedName("masked_email") val maskedEmail: String
)
data class VerifyOtpRequest(val email: String, @SerializedName("otp_code") val otpCode: String)
data class ResetPasswordRequest(
    val email: String,
    @SerializedName("otp_code") val otpCode: String,
    @SerializedName("new_password") val newPassword: String
)
data class ChangePasswordRequest(
    @SerializedName("current_password") val currentPassword: String,
    @SerializedName("new_password") val newPassword: String
)

data class DeleteAccountRequest(
    @SerializedName("password") val password: String
)

data class GoogleDeleteAccountRequest(
    @SerializedName("id_token") val idToken: String
)

data class MessageResponse(val message: String)

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

// ====== SETTINGS ======

data class UserSettingsResponse(
    @SerializedName("nguoi_dung_id") val userId: Int,
    @SerializedName("display_language") val displayLanguage: String = "vi",
    @SerializedName("dark_mode") val darkMode: Boolean = false
)

data class UserSettingsUpdate(
    @SerializedName("display_language") val displayLanguage: String? = null,
    @SerializedName("dark_mode") val darkMode: Boolean? = null
)

// ====== SCAN ======

data class ScanRequest(
    @SerializedName("object_code") val objectCode: String,
    val confidence: Float = 0f
)

data class ScanResponse(
    val source: String,
    @SerializedName("object_id") val objectId: Int,
    @SerializedName("object_code") val objectCode: String,
    @SerializedName("category_name") val categoryName: String?,
    val translations: List<TranslationResponse>,
    @SerializedName("lich_su_quet_id") val scanId: Int? = null,
    @SerializedName("prediction_id") val predictionId: Int? = null,
    @SerializedName("pending_review") val pendingReview: Boolean = false,
    val aliases: List<String> = emptyList()
)

data class ViDuResponse(
    val id: Int,
    @SerializedName("cau_vi_du") val cauViDu: String?,
    @SerializedName("dich_nghia") val dichNghia: String?,
    @SerializedName("nguon_du_lieu") val nguonDuLieu: String?
)

data class TranslationResponse(
    val id: Int,
    @SerializedName("object_id") val objectId: Int,
    @SerializedName("language_id") val languageId: Int,
    @SerializedName("language_code") val languageCode: String?,
    @SerializedName("language_name") val languageName: String?,
    @SerializedName("word_name") val wordName: String,
    val phonetic: String?,
    @SerializedName("part_of_speech") val partOfSpeech: String?,
    val definition: String?,
    val examples: List<ViDuResponse> = emptyList(),
    @SerializedName("audio_url") val audioUrl: String?,
    @SerializedName("data_source") val dataSource: String?
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
    val examples: List<ViDuResponse> = emptyList(),
    @SerializedName("language_code") val languageCode: String,
    @SerializedName("language_name") val languageName: String,
    @SerializedName("easiness_factor") val easinessFactor: Float,
    val interval: Int,
    val repetitions: Int,
    @SerializedName("image_url") val imageUrl: String? = null,
    @SerializedName("audio_url") val audioUrl: String? = null,
    @SerializedName("last_reviewed_at") val lastReviewedAt: String? = null
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
    @SerializedName("total_scans") val totalScans: Int,
    @SerializedName("current_streak") val currentStreak: Int = 0,
    @SerializedName("longest_streak") val longestStreak: Int = 0,
    @SerializedName("total_reviews") val totalReviews: Int = 0
)

// ====== HISTORY ======

data class HistoryItem(
    val id: Int,
    @SerializedName("object_code") val objectCode: String?,
    @SerializedName("object_name") val objectName: String?,
    @SerializedName("word_name") val wordName: String?,
    val phonetic: String?,
    val definition: String?,
    @SerializedName("translation_id") val translationId: Int?,
    @SerializedName("category_name") val categoryName: String?,
    @SerializedName("confidence_score") val confidenceScore: Float?,
    @SerializedName("scanned_at") val scanDate: String?,
    @SerializedName("image_url") val imageUrl: String?,
    val status: String? = null,
    @SerializedName("review_status") val reviewStatus: String? = null,
    @SerializedName("prediction_id") val predictionId: Int? = null
)

data class HistoryDetail(
    val id: Int,
    @SerializedName("object_code") val objectCode: String?,
    @SerializedName("object_name") val objectName: String?,
    @SerializedName("word_name") val wordName: String?,
    val phonetic: String?,
    val definition: String?,
    @SerializedName("translation_id") val translationId: Int?,
    @SerializedName("category_name") val categoryName: String?,
    @SerializedName("confidence_score") val confidenceScore: Float?,
    @SerializedName("scanned_at") val scanDate: String?,
    @SerializedName("image_url") val imageUrl: String?,
    val status: String? = null,
    @SerializedName("review_status") val reviewStatus: String? = null,
    @SerializedName("prediction_id") val predictionId: Int? = null,
    val translations: List<TranslationResponse> = emptyList()
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

// ====== DICTIONARY ======

data class DictionaryResponse(
    val word: String,
    val phonetic: String?,
    @SerializedName("audio_url") val audioUrl: String?,
    val meanings: List<DictionaryMeaning>,
    val source: String
)

data class DictionaryMeaning(
    @SerializedName("part_of_speech") val partOfSpeech: String,
    val definition: String,
    val example: String?
)

// ====== TRANSLATE ======

data class TranslateRequest(
    val text: String,
    @SerializedName("from_lang") val fromLang: String = "en",
    @SerializedName("to_lang") val toLang: String = "vi"
)

data class TranslateResponse(
    val original: String,
    val translation: String,
    val phonetic: String?,
    val definitions: List<String> = emptyList(),
    @SerializedName("from_lang") val fromLang: String,
    @SerializedName("to_lang") val toLang: String,
    val source: String? = null,
    @SerializedName("is_physical_object") val isPhysicalObject: Boolean = false,
    @SerializedName("object_id") val objectId: Int? = null,
    @SerializedName("object_code") val objectCode: String? = null,
    @SerializedName("translation_id") val translationId: Int? = null,
    @SerializedName("can_save") val canSave: Boolean = false,
    @SerializedName("pending_review") val pendingReview: Boolean = false
)

data class DictionaryHistoryItem(
    val id: Int,
    @SerializedName("tu_tra") val tuTra: String,
    @SerializedName("ket_qua_dich") val ketQuaDich: String?,
    @SerializedName("phien_am") val phienAm: String?,
    @SerializedName("lan_tra_cuoi") val lanTraCuoi: String?,
    @SerializedName("doi_tuong_id") val doiTuongId: Int?,
    @SerializedName("object_code") val objectCode: String?,
    @SerializedName("image_url") val imageUrl: String?,
    @SerializedName("den_ngon_ngu") val denNgonNgu: String = "vi"
)

// ====== EXPLORE / CATEGORY ======

data class CategoryData(
    val id: Int,
    val name: String,
    @SerializedName("parent_id") val parentId: Int?,
    val description: String?,
    @SerializedName("object_count") val objectCount: Int = 0
)

data class ObjectData(
    val id: Int,
    @SerializedName("object_code") val objectCode: String,
    @SerializedName("category_id") val categoryId: Int?,
    @SerializedName("category_name") val categoryName: String?,
    @SerializedName("translation_count") val translationCount: Int,
    @SerializedName("word_name") val wordName: String?,
    val phonetic: String?,
    val definition: String?,
    @SerializedName("translation_id") val translationId: Int?,
    @SerializedName("image_url") val imageUrl: String?
)

// ====== PROFILE ======

data class ProfileData(
    @SerializedName("nguoi_dung_id") val userId: Int,
    val username: String?,
    @SerializedName("full_name") val fullName: String?,
    @SerializedName("avatar_url") val avatarUrl: String?,
    val bio: String?
)

data class ProfileUpdateRequest(
    val username: String? = null,
    @SerializedName("full_name") val fullName: String? = null,
    val bio: String? = null
)

data class AvatarUploadResponse(
    @SerializedName("avatar_url") val avatarUrl: String
)

// ====== STREAK ======

data class StreakResponse(
    @SerializedName("streak_hien_tai") val streakHienTai: Int,
    @SerializedName("streak_dai_nhat") val streakDaiNhat: Int,
    @SerializedName("tong_luot_on")    val tongLuotOn: Int,
    @SerializedName("luot_on_hom_nay") val luotOnHomNay: Int = 0,
    @SerializedName("ngay_on_cuoi")    val ngayOnCuoi: String?
)

// ====== ANALYTICS ======

data class DailyReview(
    @SerializedName("date") val date: String,
    @SerializedName("count") val count: Int
)

data class MasteryDist(
    @SerializedName("new") val newCount: Int,
    @SerializedName("learning") val learning: Int,
    @SerializedName("mastered") val mastered: Int
)

data class AnalyticsResponse(
    @SerializedName("weekly_reviews") val weeklyReviews: List<DailyReview>,
    @SerializedName("mastery") val mastery: MasteryDist
)

data class StreakCalendarDay(
    @SerializedName("date")  val date: String,
    @SerializedName("count") val count: Int
)

data class StreakCalendarResponse(
    @SerializedName("days") val days: List<StreakCalendarDay>
)

data class StreakSyncRequest(
    @SerializedName("streak_hien_tai") val streakHienTai: Int,
    @SerializedName("streak_dai_nhat") val streakDaiNhat: Int,
    @SerializedName("tong_luot_on")    val tongLuotOn: Int,
    @SerializedName("luot_on_hom_nay") val luotOnHomNay: Int,
    @SerializedName("ngay_on_cuoi")    val ngayOnCuoi: String?   // "yyyy-MM-dd"
)

// ====== LICH SU QUET (TFLite → save history + image) ======

data class LichSuQuetResponse(
    val id: Int,
    val message: String,
    @SerializedName("image_url") val imageUrl: String?,
    @SerializedName("learning_added") val learningAdded: Boolean = false,
    @SerializedName("learning_status") val learningStatus: String? = null,
    @SerializedName("translation_id") val translationId: Int? = null
)

data class DueCountResponse(val count: Int)
