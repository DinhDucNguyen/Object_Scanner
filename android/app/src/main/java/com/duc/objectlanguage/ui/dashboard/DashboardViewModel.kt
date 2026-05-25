package com.duc.objectlanguage.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.duc.objectlanguage.ObjectLanguageApp
import com.duc.objectlanguage.data.local.StreakDataStore
import com.duc.objectlanguage.data.model.StatsResponse
import com.duc.objectlanguage.data.repository.CollectionRepository
import java.util.Calendar
import kotlin.random.Random
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as ObjectLanguageApp).repository
    private val collectionRepo = CollectionRepository((application as ObjectLanguageApp).tokenManager)
    private val streakStore = StreakDataStore(application)

    private val _stats = MutableLiveData<StatsResponse?>()
    val stats: LiveData<StatsResponse?> = _stats

    val streakSummary: LiveData<DashboardStreakSummary> = combine(
        streakStore.currentStreak,
        streakStore.reviewsToday,
    ) { current, today ->
        buildStreakSummary(current, today)
    }.asLiveData()

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _dailySuggestions = MutableLiveData<List<DashboardSuggestion>>(emptyList())
    val dailySuggestions: LiveData<List<DashboardSuggestion>> = _dailySuggestions

    private val _topCollections = MutableLiveData<List<CollectionHighlight>>(emptyList())
    val topCollections: LiveData<List<CollectionHighlight>> = _topCollections

    private val _avatarUrl = MutableLiveData<String?>()
    val avatarUrl: LiveData<String?> = _avatarUrl

    fun loadStats() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repo.getStats()
            result.fold(
                onSuccess = { _stats.value = it },
                onFailure = { _error.value = it.message }
            )
            _isLoading.value = false
        }
    }

    fun loadAvatarUrl() {
        viewModelScope.launch {
            repo.getProfile().getOrNull()?.avatarUrl?.let { url ->
                if (url.isNotBlank() && url != "default_avatar.png") _avatarUrl.value = url
            }
        }
    }

    fun loadDailySuggestions() {
        viewModelScope.launch {
            val objects = repo.getAllObjects().getOrNull().orEmpty()
                .filter { it.objectCode.isNotBlank() }
                .distinctBy { it.objectCode }

            val suggestions = if (objects.isNotEmpty()) {
                objects
                    .shuffled(Random(todaySeed()))
                    .take(SUGGESTION_COUNT)
                    .map { obj ->
                        DashboardSuggestion(
                            objectCode = obj.objectCode,
                            displayName = obj.wordName?.takeIf { it.isNotBlank() }
                                ?: obj.objectCode.replace("_", " ")
                        )
                    }
            } else {
                FALLBACK_SUGGESTIONS
            }

            _dailySuggestions.value = suggestions
        }
    }

    fun loadTopCollections() {
        viewModelScope.launch {
            collectionRepo.getCollections().getOrNull()
                ?.sortedByDescending { it.itemCount }
                ?.take(3)
                ?.map { CollectionHighlight(it.id, it.name, it.itemCount) }
                ?.let { _topCollections.value = it }
        }
    }

    fun loadStreak() {
        viewModelScope.launch {
            repo.getStreak().onSuccess { remote ->
                streakStore.replaceWithServer(
                    remote.streakHienTai,
                    remote.streakDaiNhat,
                    remote.tongLuotOn,
                    remote.luotOnHomNay,
                    remote.ngayOnCuoi
                )
            }
        }
    }

    private fun buildStreakSummary(current: Int, reviewsToday: Int): DashboardStreakSummary {
        val nextMilestone = STREAK_MILESTONES.firstOrNull { milestone -> milestone > current }
        return if (nextMilestone == null) {
            DashboardStreakSummary(
                currentStreak = current,
                reviewsToday = reviewsToday,
                nextMilestone = current.coerceAtLeast(1),
                daysToMilestone = 0,
                hasReachedTopMilestone = true,
            )
        } else {
            DashboardStreakSummary(
                currentStreak = current,
                reviewsToday = reviewsToday,
                nextMilestone = nextMilestone,
                daysToMilestone = nextMilestone - current,
                hasReachedTopMilestone = false,
            )
        }
    }

    companion object {
        private val STREAK_MILESTONES = listOf(3, 7, 14, 30, 50, 100, 365)
        private const val SUGGESTION_COUNT = 4
        private val FALLBACK_SUGGESTIONS = listOf(
            DashboardSuggestion("ruler", "ruler"),
            DashboardSuggestion("laptop", "laptop"),
            DashboardSuggestion("book", "book"),
            DashboardSuggestion("bottle", "bottle"),
        )

        private fun todaySeed(): Int {
            val calendar = Calendar.getInstance()
            return calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR)
        }
    }
}

data class DashboardStreakSummary(
    val currentStreak: Int,
    val reviewsToday: Int,
    val nextMilestone: Int,
    val daysToMilestone: Int,
    val hasReachedTopMilestone: Boolean,
)

data class DashboardSuggestion(
    val objectCode: String,
    val displayName: String,
)

data class CollectionHighlight(
    val id: Int,
    val name: String,
    val itemCount: Int,
)
