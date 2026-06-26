package com.duc.objectlanguage.data.repository

import com.duc.objectlanguage.data.local.TokenManager
import com.duc.objectlanguage.data.model.*

class AppRepository(tokenManager: TokenManager) {

    val auth = AuthRepository(tokenManager)
    val scan = ScanRepository()
    val learning = LearningRepository()
    val review = ReviewRepository()
    val stats = StatsRepository()
    val explore = ExploreRepository()
    val history = HistoryRepository()
    val dictionary = DictionaryRepository()
    val profile = ProfileRepository(tokenManager)
    val streak = StreakRepository()
    val collection = CollectionRepository()

    // ====== AUTH ======

    suspend fun login(username: String, password: String) = auth.login(username, password)
    suspend fun googleLogin(idToken: String) = auth.googleLogin(idToken)
    suspend fun register(username: String, email: String, password: String, fullName: String? = null) =
        auth.register(username, email, password, fullName)
    suspend fun resendRegistrationOtp(email: String) = auth.resendRegistrationOtp(email)
    suspend fun verifyRegistrationOtp(email: String, otpCode: String) = auth.verifyRegistrationOtp(email, otpCode)
    suspend fun forgotPassword(email: String) = auth.forgotPassword(email)
    suspend fun verifyOtp(email: String, otpCode: String) = auth.verifyOtp(email, otpCode)
    suspend fun resetPassword(email: String, otpCode: String, newPassword: String) =
        auth.resetPassword(email, otpCode, newPassword)
    suspend fun changePassword(currentPassword: String, newPassword: String) =
        auth.changePassword(currentPassword, newPassword)
    suspend fun deleteAccount(password: String) = auth.deleteAccount(password)
    suspend fun deleteAccountGoogle(idToken: String) = auth.deleteAccountGoogle(idToken)
    fun logout() = auth.logout()

    // ====== SCAN ======

    suspend fun scanByCode(objectCode: String, confidence: Float) = scan.scanByCode(objectCode, confidence)
    suspend fun scanByImage(imageBytes: ByteArray) = scan.scanByImage(imageBytes)
    suspend fun saveLichSuQuet(objectCode: String, confidence: Float, imageBytes: ByteArray?, trainingSource: String = "yolo") =
        scan.saveLichSuQuet(objectCode, confidence, imageBytes, trainingSource)
    suspend fun getTtsAudio(word: String, lang: String = "en") = scan.getTtsAudio(word, lang)
    suspend fun getAudioByUrl(audioUrl: String) = scan.getAudioByUrl(audioUrl)
    suspend fun getExamples(word: String, lang: String = "en") = scan.getExamples(word, lang)

    // ====== LEARNING ======

    suspend fun addToLearning(translationId: Int) = learning.addToLearning(translationId)

    // ====== REVIEW ======

    suspend fun getDueCount() = review.getDueCount()
    suspend fun getDueReviews() = review.getDueReviews()
    suspend fun submitReview(progressId: Int, quality: Int) = review.submitReview(progressId, quality)
    suspend fun getMasteredWords() = review.getMasteredWords()

    // ====== STATS ======

    suspend fun getStats() = stats.getStats()

    // ====== EXPLORE ======

    suspend fun getCategories() = explore.getCategories()
    suspend fun getObjectsByCategory(categoryId: Int) = explore.getObjectsByCategory(categoryId)
    suspend fun getAllObjects() = explore.getAllObjects()

    // ====== HISTORY ======

    suspend fun getHistory(
        limit: Int = 50,
        offset: Int = 0,
        keyword: String? = null,
        period: String = "all",
        fromDate: String? = null,
        toDate: String? = null
    ) = history.getHistory(limit, offset, keyword, period, fromDate, toDate)
    suspend fun getHistoryDetail(scanId: Int) = history.getHistoryDetail(scanId)
    suspend fun deleteHistory(scanId: Int) = history.deleteHistory(scanId)
    suspend fun getTranslationsByCode(objectCode: String) = history.getTranslationsByCode(objectCode)

    // ====== DICTIONARY ======

    suspend fun lookupWord(word: String, fromLang: String = "en", toLang: String = "en") =
        dictionary.lookupWord(word, fromLang, toLang)
    suspend fun translate(text: String, fromLang: String, toLang: String) =
        dictionary.translate(text, fromLang, toLang)
    suspend fun getDictionaryHistory(limit: Int = 30) = dictionary.getDictionaryHistory(limit)
    suspend fun deleteHistoryItem(id: Int) = dictionary.deleteHistoryItem(id)

    // ====== USER PROFILE / SETTINGS ======

    suspend fun getProfile() = profile.getProfile()
    suspend fun updateProfile(username: String? = null, fullName: String? = null, bio: String? = null) =
        profile.updateProfile(username, fullName, bio)
    suspend fun uploadAvatar(imageBytes: ByteArray, filename: String) = profile.uploadAvatar(imageBytes, filename)
    suspend fun getUserSettings() = profile.getUserSettings()
    suspend fun updateUserSettings(displayLanguage: String) = profile.updateUserSettings(displayLanguage)
    suspend fun updateDarkMode(darkMode: Boolean) = profile.updateDarkMode(darkMode)

    // ====== ANALYTICS ======

    suspend fun getAnalytics() = stats.getAnalytics()

    // ====== STREAK ======

    suspend fun getStreak() = streak.getStreak()
    suspend fun recordStreak() = streak.recordStreak()
    suspend fun getStreakCalendar(days: Int = 30) = streak.getStreakCalendar(days)
    suspend fun syncStreak(req: StreakSyncRequest) = streak.syncStreak(req)
}
