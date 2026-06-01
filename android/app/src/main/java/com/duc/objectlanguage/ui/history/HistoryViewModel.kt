package com.duc.objectlanguage.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.asLiveData
import com.duc.objectlanguage.ObjectLanguageApp
import com.duc.objectlanguage.R
import com.duc.objectlanguage.data.local.StreakDataStore
import com.duc.objectlanguage.data.model.HistoryItem
import com.duc.objectlanguage.ui.common.localizedString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as ObjectLanguageApp).repository
    private val streakStore = StreakDataStore(application)
    private val pageSize = 20

    val streakValue: LiveData<Int> = streakStore.currentStreak.asLiveData()

    private val _avatarUrl = MutableLiveData<String?>()
    val avatarUrl: LiveData<String?> = _avatarUrl

    fun loadAvatarUrl() {
        viewModelScope.launch {
            repo.getProfile().getOrNull()?.avatarUrl?.let { url ->
                if (url.isNotBlank() && url != "default_avatar.png") _avatarUrl.value = url
            }
        }
    }

    private val _items = MutableLiveData<List<HistoryItem>>()
    val items: LiveData<List<HistoryItem>> = _items

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private var keyword: String? = null
    private var period: String = "all"
    private var fromDate: String? = null
    private var toDate: String? = null
    private var offset = 0
    private var canLoadMore = true
    private var loadJob: Job? = null
    private var searchJob: Job? = null

    fun load(reset: Boolean = true) {
        if (reset) {
            // Cancel any in-flight load so rapid filter/scroll changes don't stack up
            loadJob?.cancel()
            offset = 0
            canLoadMore = true
        }
        if (!canLoadMore) return

        loadJob = viewModelScope.launch {
            _isLoading.value = true
            val result = repo.getHistory(
                limit = pageSize,
                offset = offset,
                keyword = keyword,
                period = period,
                fromDate = fromDate,
                toDate = toDate,
            )
            result.fold(
                onSuccess = { newItems ->
                    val current = if (reset) emptyList() else (_items.value ?: emptyList())
                    _items.value = current + newItems
                    offset += newItems.size
                    canLoadMore = newItems.size == pageSize
                },
                onFailure = {
                    if (it is CancellationException) return@launch
                    if (reset) _items.value = emptyList()
                    _error.value = it.message ?: localizedString(R.string.history_load_error)
                }
            )
            _isLoading.value = false
        }
    }

    fun loadMore() {
        // Don't load more while a reset load is in progress
        if (_isLoading.value == true) return
        load(reset = false)
    }

    fun setKeyword(value: String) {
        keyword = value.trim().ifEmpty { null }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(350)
            load(reset = true)
        }
    }

    fun setPeriod(value: String) {
        period = value
        fromDate = null
        toDate = null
        searchJob?.cancel()
        load(reset = true)
    }

    fun setDateRange(from: String?, to: String?) {
        period = "custom"
        fromDate = from
        toDate = to
        searchJob?.cancel()
        load(reset = true)
    }

    fun clearDateRange() {
        period = "all"
        fromDate = null
        toDate = null
        searchJob?.cancel()
        load(reset = true)
    }

    fun deleteHistory(item: HistoryItem) {
        viewModelScope.launch {
            val result = repo.deleteHistory(item.id)
            result.fold(
                onSuccess = { message ->
                    _items.value = (_items.value ?: emptyList()).filterNot { it.id == item.id }
                    _message.value = message
                },
                onFailure = { _error.value = it.message ?: localizedString(R.string.history_delete_error) }
            )
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun clearError() {
        _error.value = null
    }
}
