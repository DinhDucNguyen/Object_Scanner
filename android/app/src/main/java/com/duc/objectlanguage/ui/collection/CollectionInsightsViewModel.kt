package com.duc.objectlanguage.ui.collection

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.duc.objectlanguage.data.local.TokenManager
import com.duc.objectlanguage.data.model.Collection
import com.duc.objectlanguage.data.model.CollectionInsights
import com.duc.objectlanguage.data.repository.CollectionRepository
import kotlinx.coroutines.launch

/**
 * ViewModel for collection analytics and insights
 * Provides per-collection statistics and smart suggestions
 */
class CollectionInsightsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val tokenManager = TokenManager(application)
    private val repo = CollectionRepository(tokenManager)
    
    // All collections for dropdown selection
    private val _collections = MutableLiveData<List<Collection>>()
    val collections: LiveData<List<Collection>> = _collections
    
    // Selected collection insights
    private val _insights = MutableLiveData<CollectionInsights?>()
    val insights: LiveData<CollectionInsights?> = _insights
    
    // Smart suggestions based on learning patterns
    private val _suggestions = MutableLiveData<List<String>>()
    val suggestions: LiveData<List<String>> = _suggestions
    
    // Loading state
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error
    
    // Selected collection ID
    private val _selectedCollectionId = MutableLiveData<Int?>()
    val selectedCollectionId: LiveData<Int?> = _selectedCollectionId

    // collectionId được truyền từ nav argument (ưu tiên hơn auto-select đầu tiên)
    private var initialCollectionId: Int = 0

    fun setInitialCollection(collectionId: Int) {
        initialCollectionId = collectionId
    }

    init {
        loadCollections()
    }
    
    /**
     * Load all collections for selection
     */
    private fun loadCollections() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repo.getCollections()
            
            result.fold(
                onSuccess = { collections ->
                    _collections.value = collections
                    // Ưu tiên initialCollectionId từ nav argument; fallback về collection đầu tiên
                    if (collections.isNotEmpty() && _selectedCollectionId.value == null) {
                        val targetId = if (initialCollectionId > 0 &&
                            collections.any { it.id == initialCollectionId })
                            initialCollectionId else collections[0].id
                        selectCollection(targetId)
                    }
                    _error.value = null
                },
                onFailure = { e ->
                    _error.value = "Failed to load collections: ${e.message}"
                }
            )
            
            _isLoading.value = false
        }
    }
    
    /**
     * Select collection and load insights
     */
    fun selectCollection(collectionId: Int) {
        _selectedCollectionId.value = collectionId
        loadInsights(collectionId)
    }
    
    /**
     * Load collection insights
     */
    private fun loadInsights(collectionId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repo.getCollectionInsights(collectionId)
            
            result.fold(
                onSuccess = { insights ->
                    _insights.value = insights
                    generateSuggestions(insights)
                    _error.value = null
                },
                onFailure = { e ->
                    _error.value = "Failed to load insights: ${e.message}"
                }
            )
            
            _isLoading.value = false
        }
    }
    
    /**
     * Generate smart suggestions based on insights
     */
    private fun generateSuggestions(insights: CollectionInsights) {
        val suggestions = mutableListOf<String>()
        
        // Calculate progress percentage
        val progressPercent = if (insights.totalItems > 0) {
            (insights.reviewedItems.toDouble() / insights.totalItems * 100).toInt()
        } else 0
        
        val masteryPercent = if (insights.totalItems > 0) {
            (insights.masteredItems.toDouble() / insights.totalItems * 100).toInt()
        } else 0
        
        // Gợi ý 1: Tiến độ ôn tập
        when {
            insights.reviewedItems == 0 ->
                suggestions.add("💡 Hãy bắt đầu ôn tập để xây dựng vốn từ vựng của bạn!")
            progressPercent < 50 ->
                suggestions.add("📚 Bạn đã ôn ${progressPercent}% từ trong bộ sưu tập. Tiếp tục nào!")
            progressPercent < 100 ->
                suggestions.add("🌟 Tiến độ tốt! Còn ${100 - progressPercent}% nữa là xong.")
            else ->
                suggestions.add("✅ Đã ôn hết tất cả! Duy trì để không quên nhé.")
        }

        // Gợi ý 2: Mức độ thành thạo
        when {
            masteryPercent == 0 && insights.reviewedItems > 0 ->
                suggestions.add("🎯 Tập trung cải thiện điểm chất lượng để thành thạo các từ này.")
            masteryPercent < 30 ->
                suggestions.add("📈 ${masteryPercent}% thành thạo. Ôn lại các từ khó thường xuyên hơn.")
            masteryPercent < 70 ->
                suggestions.add("🔥 ${masteryPercent}% thành thạo! Bạn đang tiến bộ rất tốt.")
            masteryPercent >= 70 ->
                suggestions.add("🏆 Xuất sắc! ${masteryPercent}% từ đã thành thạo.")
        }

        // Gợi ý 3: Tỉ lệ thành công
        when {
            insights.successRate < 0.5 ->
                suggestions.add("💪 Thử dùng các chế độ ôn khác nhau (Quiz, Gõ từ) để nhớ lâu hơn.")
            insights.successRate < 0.75 ->
                suggestions.add("📊 Tỉ lệ đúng ${(insights.successRate * 100).toInt()}%. Luyện phát âm sẽ giúp nhớ từ tốt hơn.")
            insights.successRate >= 0.75 ->
                suggestions.add("🌟 Tuyệt vời! Tỉ lệ đúng ${(insights.successRate * 100).toInt()}%!")
        }

        // Gợi ý 4: Tần suất ôn tập
        if (insights.lastReviewDate != null) {
            val daysSinceReview = calculateDaysSince(insights.lastReviewDate)
            when {
                daysSinceReview == 0 ->
                    suggestions.add("✨ Đã ôn hôm nay! Duy trì thói quen hàng ngày nhé.")
                daysSinceReview == 1 ->
                    suggestions.add("📅 Ôn lần cuối hôm qua. Ôn lại hôm nay để giữ tiến độ.")
                daysSinceReview in 2..7 ->
                    suggestions.add("⏰ ${daysSinceReview} ngày chưa ôn. Đã đến lúc ôn lại rồi!")
                daysSinceReview > 7 ->
                    suggestions.add("⚠️ Đã ${daysSinceReview} ngày chưa ôn. Một số từ có thể cần học lại từ đầu.")
            }
        }

        // Gợi ý 5: Kích thước bộ sưu tập
        when {
            insights.totalItems < 10 ->
                suggestions.add("➕ Bộ sưu tập còn nhỏ! Thêm từ liên quan để tăng hiệu quả học.")
            insights.totalItems > 50 ->
                suggestions.add("📦 Bộ sưu tập lớn! Hãy cân nhắc chia thành các nhóm chủ đề nhỏ hơn.")
        }
        
        _suggestions.value = suggestions
    }
    
    /**
     * Calculate days since a date
     */
    private fun calculateDaysSince(dateStr: String): Int {
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
            sdf.isLenient = true
            val date = sdf.parse(dateStr) ?: return Int.MAX_VALUE
            val diff = System.currentTimeMillis() - date.time
            (diff / (1000 * 60 * 60 * 24)).toInt()
        } catch (e: Exception) {
            Int.MAX_VALUE
        }
    }
    
    /**
     * Refresh insights
     */
    fun refresh() {
        _selectedCollectionId.value?.let { collectionId ->
            loadInsights(collectionId)
        }
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _error.value = null
    }
}
