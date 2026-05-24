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
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * ViewModel for collection analytics and insights.
 */
class CollectionInsightsViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenManager = TokenManager(application)
    private val repo = CollectionRepository(tokenManager)

    private val _collections = MutableLiveData<List<Collection>>()
    val collections: LiveData<List<Collection>> = _collections

    private val _insights = MutableLiveData<CollectionInsights?>()
    val insights: LiveData<CollectionInsights?> = _insights

    private val _suggestions = MutableLiveData<List<String>>()
    val suggestions: LiveData<List<String>> = _suggestions

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _selectedCollectionId = MutableLiveData<Int?>()
    val selectedCollectionId: LiveData<Int?> = _selectedCollectionId

    private var initialCollectionId: Int = 0

    init {
        loadCollections()
    }

    fun setInitialCollection(collectionId: Int) {
        initialCollectionId = collectionId
    }

    private fun loadCollections() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repo.getCollections()

            result.fold(
                onSuccess = { collections ->
                    _collections.value = collections
                    if (collections.isNotEmpty() && _selectedCollectionId.value == null) {
                        val targetId = if (
                            initialCollectionId > 0 &&
                            collections.any { it.id == initialCollectionId }
                        ) {
                            initialCollectionId
                        } else {
                            collections[0].id
                        }
                        selectCollection(targetId)
                    }
                    _error.value = null
                },
                onFailure = { e ->
                    _error.value = "Không tải được bộ sưu tập: ${e.message}"
                }
            )

            _isLoading.value = false
        }
    }

    fun selectCollection(collectionId: Int) {
        _selectedCollectionId.value = collectionId
        loadInsights(collectionId)
    }

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
                    _error.value = "Không tải được thống kê: ${e.message}"
                }
            )

            _isLoading.value = false
        }
    }

    private fun generateSuggestions(insights: CollectionInsights) {
        val suggestions = mutableListOf<String>()

        val progressPercent = if (insights.totalItems > 0) {
            (insights.reviewedItems.toDouble() / insights.totalItems * 100).toInt()
        } else {
            0
        }

        val masteryPercent = if (insights.totalItems > 0) {
            (insights.masteredItems.toDouble() / insights.totalItems * 100).toInt()
        } else {
            0
        }

        when {
            insights.reviewedItems == 0 ->
                suggestions.add("Hãy bắt đầu ôn tập để xây dựng vốn từ vựng của bạn.")
            progressPercent < 50 ->
                suggestions.add("Bạn đã ôn ${progressPercent}% từ trong bộ sưu tập. Tiếp tục giữ nhịp nhé.")
            progressPercent < 100 ->
                suggestions.add("Tiến độ tốt. Còn ${100 - progressPercent}% nữa là hoàn tất vòng ôn đầu.")
            else ->
                suggestions.add("Đã ôn hết tất cả từ. Hãy duy trì để không quên.")
        }

        when {
            masteryPercent == 0 && insights.reviewedItems > 0 ->
                suggestions.add("Tập trung cải thiện điểm chất lượng để đưa các từ vào mức thành thạo.")
            masteryPercent < 30 ->
                suggestions.add("${masteryPercent}% từ đã thành thạo. Nên ôn lại các từ khó thường xuyên hơn.")
            masteryPercent < 70 ->
                suggestions.add("${masteryPercent}% từ đã thành thạo. Bạn đang tiến bộ rất ổn.")
            masteryPercent >= 70 ->
                suggestions.add("Xuất sắc. ${masteryPercent}% từ trong bộ sưu tập đã thành thạo.")
        }

        when {
            insights.successRate < 0.5 ->
                suggestions.add("Thử dùng các chế độ ôn khác nhau như Quiz hoặc Gõ từ để nhớ lâu hơn.")
            insights.successRate < 0.75 ->
                suggestions.add("Tỉ lệ đúng ${(insights.successRate * 100).toInt()}%. Luyện phát âm có thể giúp ghi nhớ tốt hơn.")
            insights.successRate >= 0.75 ->
                suggestions.add("Tỉ lệ đúng ${(insights.successRate * 100).toInt()}%. Đây là mức rất tốt.")
        }

        if (insights.lastReviewDate != null) {
            val daysSinceReview = calculateDaysSince(insights.lastReviewDate)
            when {
                daysSinceReview == 0 ->
                    suggestions.add("Bạn đã ôn hôm nay. Duy trì thói quen này là đẹp.")
                daysSinceReview == 1 ->
                    suggestions.add("Lần ôn gần nhất là hôm qua. Ôn lại hôm nay để giữ tiến độ.")
                daysSinceReview in 2..7 ->
                    suggestions.add("${daysSinceReview} ngày chưa ôn. Đã đến lúc quay lại bộ sưu tập này.")
                daysSinceReview > 7 ->
                    suggestions.add("Đã ${daysSinceReview} ngày chưa ôn. Một số từ có thể cần học lại từ đầu.")
            }
        }

        when {
            insights.totalItems < 10 ->
                suggestions.add("Bộ sưu tập còn nhỏ. Thêm vài từ liên quan sẽ giúp buổi ôn đầy đủ hơn.")
            insights.totalItems > 50 ->
                suggestions.add("Bộ sưu tập khá lớn. Có thể chia thành nhóm chủ đề nhỏ để dễ quản lý.")
        }

        _suggestions.value = suggestions
    }

    private fun calculateDaysSince(dateStr: String): Int {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            sdf.isLenient = true
            val date = sdf.parse(dateStr) ?: return Int.MAX_VALUE
            val diff = System.currentTimeMillis() - date.time
            (diff / (1000 * 60 * 60 * 24)).toInt()
        } catch (e: Exception) {
            Int.MAX_VALUE
        }
    }

    fun refresh() {
        _selectedCollectionId.value?.let { collectionId ->
            loadInsights(collectionId)
        }
    }

    fun clearError() {
        _error.value = null
    }
}
